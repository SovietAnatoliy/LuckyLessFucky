package com.luckylessfucky.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luckylessfucky.app.data.preferences.AppPreferencesRepository
import com.luckylessfucky.app.data.preferences.UserSettingsState
import com.luckylessfucky.app.data.repository.PokedexRepository
import com.luckylessfucky.app.data.repository.ProfileRepository
import com.luckylessfucky.app.domain.model.ProfileExportPayload
import com.luckylessfucky.app.sync.FileImportExportHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettingsState = UserSettingsState(),
    val userRecordedPokedexCount: Int = 0,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val pokedexRepository: PokedexRepository,
    private val profileRepository: ProfileRepository,
    private val fileHelper: FileImportExportHelper
) : ViewModel() {

    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private var pendingUserExportPayload: ProfileExportPayload? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.settingsFlow,
        pokedexRepository.userRecordedVariantIdsFlow,
        statusMessageFlow
    ) { settings, recordedSet, statusMsg ->
        SettingsUiState(
            settings = settings,
            userRecordedPokedexCount = recordedSet.size,
            statusMessage = statusMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun saveUsername(newUsername: String) {
        viewModelScope.launch {
            preferencesRepository.setUsername(newUsername)
            statusMessageFlow.value = "Username saved as '${newUsername.trim().ifEmpty { "Trainer" }}'."
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOverlayEnabled(enabled)
        }
    }

    fun setAutoScanTwoSecondIntervalEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoScanEnabled(enabled)
            statusMessageFlow.value = if (enabled) {
                "Auto-scan (every 2 seconds) enabled. Target button pair replaced by single button."
            } else {
                "Auto-scan disabled. Manual target check button pair active."
            }
        }
    }

    fun prepareExportUserProfile(launchCreateDocument: (String) -> Unit) {
        viewModelScope.launch {
            val payload = profileRepository.buildUserExportPayload()
            pendingUserExportPayload = payload
            launchCreateDocument(FileImportExportHelper.suggestedFileName(payload.username))
        }
    }

    fun completeExportUserProfileToUri(uri: Uri) {
        val payload = pendingUserExportPayload ?: return
        pendingUserExportPayload = null
        viewModelScope.launch {
            fileHelper.exportPayloadToUri(uri, payload)
                .onSuccess {
                    statusMessageFlow.value =
                        "Exported profile '${payload.username}' alongside ${payload.recordedVariantIds.size} Lucky Pokédex entries."
                }
                .onFailure { err ->
                    statusMessageFlow.value =
                        "Export failed: ${err.localizedMessage ?: "Write error"}"
                }
        }
    }

    fun importUserProfileFromUri(uri: Uri) {
        viewModelScope.launch {
            fileHelper.importPayloadFromUri(uri)
                .onSuccess { payload ->
                    profileRepository.importIntoUserProfile(payload)
                    statusMessageFlow.value =
                        "Imported profile '${payload.username}' and ${payload.recordedVariantIds.size} Lucky Pokédex entries."
                }
                .onFailure { err ->
                    statusMessageFlow.value =
                        "Import failed: ${err.localizedMessage ?: "Invalid file"}"
                }
        }
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    companion object {
        fun factory(
            preferencesRepository: AppPreferencesRepository,
            pokedexRepository: PokedexRepository,
            profileRepository: ProfileRepository,
            fileHelper: FileImportExportHelper
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    preferencesRepository = preferencesRepository,
                    pokedexRepository = pokedexRepository,
                    profileRepository = profileRepository,
                    fileHelper = fileHelper
                ) as T
            }
        }
    }
}
