package com.luckylessfucky.app.ui.pokedex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckylessfucky.app.data.repository.PokedexRepository
import com.luckylessfucky.app.data.repository.ProfileRepository
import com.luckylessfucky.app.domain.model.NeededStatus
import com.luckylessfucky.app.domain.model.PokedexItem
import com.luckylessfucky.app.domain.model.TradeProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PokedexFilter(val label: String) {
    ALL("All"),
    NEEDED("Needed"),
    NOT_NEEDED("Not Needed"),
    EVENT_ONLY("Event & Costume"),
    SHINY_AVAILABLE("Shiny Available"),
    SHINY_UNAVAILABLE("Cannot Be Shiny Yet")
}

data class PokedexUiState(
    val searchQuery: String = "",
    val selectedFilter: PokedexFilter = PokedexFilter.ALL,
    val items: List<PokedexItem> = emptyList(),
    val totalCount: Int = 0,
    val notNeededCount: Int = 0,
    val neededCount: Int = 0,
    val activeTargetProfile: TradeProfile? = null
)

class PokedexViewModel(
    private val pokedexRepository: PokedexRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedFilterFlow = MutableStateFlow(PokedexFilter.ALL)

    val uiState: StateFlow<PokedexUiState> = combine(
        pokedexRepository.userPokedexItemsFlow,
        profileRepository.activeTargetProfileFlow,
        searchQueryFlow,
        selectedFilterFlow
    ) { allItems, activeTarget, query, filter ->
        val trimmedQuery = query.trim().lowercase()
        val filtered = allItems.filter { item ->
            val matchesQuery = if (trimmedQuery.isEmpty()) {
                true
            } else {
                item.variant.displayName.lowercase().contains(trimmedQuery) ||
                    item.variant.pokedexNumber.toString().contains(trimmedQuery) ||
                    item.variant.formattedDexNumber.lowercase().contains(trimmedQuery)
            }
            val matchesFilter = when (filter) {
                PokedexFilter.ALL -> true
                PokedexFilter.NEEDED -> item.neededStatus == NeededStatus.NEEDED
                PokedexFilter.NOT_NEEDED -> item.neededStatus == NeededStatus.NOT_NEEDED
                PokedexFilter.EVENT_ONLY -> item.variant.isEventVariant
                PokedexFilter.SHINY_AVAILABLE -> item.variant.isShinyAvailableInGame
                PokedexFilter.SHINY_UNAVAILABLE -> !item.variant.isShinyAvailableInGame
            }
            matchesQuery && matchesFilter
        }

        val recordedCount = allItems.count { it.isRecorded }
        PokedexUiState(
            searchQuery = query,
            selectedFilter = filter,
            items = filtered,
            totalCount = allItems.size,
            notNeededCount = recordedCount,
            neededCount = (allItems.size - recordedCount).coerceAtLeast(0),
            activeTargetProfile = activeTarget
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PokedexUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
    }

    fun onFilterSelected(filter: PokedexFilter) {
        selectedFilterFlow.value = filter
    }

    fun onTogglePokemon(item: PokedexItem) {
        viewModelScope.launch {
            pokedexRepository.togglePokemonInUserPokedex(
                variantId = item.variant.variantId,
                currentlyRecorded = item.isRecorded
            )
        }
    }

    companion object {
        fun factory(
            pokedexRepository: PokedexRepository,
            profileRepository: ProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PokedexViewModel(pokedexRepository, profileRepository) as T
            }
        }
    }
}
