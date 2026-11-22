package com.luckylessfucky.app.data.repository

import com.luckylessfucky.app.data.local.dao.PokemonVariantDao
import com.luckylessfucky.app.data.local.dao.UserPokedexDao
import com.luckylessfucky.app.data.local.entity.UserPokedexEntryEntity
import com.luckylessfucky.app.domain.model.PokedexItem
import com.luckylessfucky.app.domain.model.PokemonVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class PokedexRepository(
    private val pokemonVariantDao: PokemonVariantDao,
    private val userPokedexDao: UserPokedexDao
) {
    val allVariantsFlow: Flow<List<PokemonVariant>> =
        pokemonVariantDao.observeAllVariants().map { list -> list.map { it.toDomain() } }

    val userRecordedVariantIdsFlow: Flow<Set<String>> =
        userPokedexDao.observeRecordedVariantIds().map { it.toSet() }

    val userPokedexItemsFlow: Flow<List<PokedexItem>> =
        combine(allVariantsFlow, userRecordedVariantIdsFlow) { variants, recordedSet ->
            variants.map { variant ->
                PokedexItem(
                    variant = variant,
                    isRecorded = recordedSet.contains(variant.variantId)
                )
            }
        }

    suspend fun getAllVariants(): List<PokemonVariant> {
        return pokemonVariantDao.getAllVariants().map { it.toDomain() }
    }

    suspend fun getUserRecordedVariantIds(): Set<String> {
        return userPokedexDao.getRecordedVariantIds().toSet()
    }

    suspend fun addPokemonToUserPokedex(variantId: String) {
        userPokedexDao.insertEntry(
            UserPokedexEntryEntity(
                variantId = variantId,
                addedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun removePokemonFromUserPokedex(variantId: String) {
        userPokedexDao.deleteEntry(variantId)
    }

    suspend fun togglePokemonInUserPokedex(variantId: String, currentlyRecorded: Boolean) {
        if (currentlyRecorded) {
            removePokemonFromUserPokedex(variantId)
        } else {
            addPokemonToUserPokedex(variantId)
        }
    }

    suspend fun replaceUserPokedexEntries(variantIds: Collection<String>) {
        val now = System.currentTimeMillis()
        val entries = variantIds.distinct().map { id ->
            UserPokedexEntryEntity(variantId = id, addedAtEpochMs = now)
        }
        userPokedexDao.replaceAllEntries(entries)
    }
}
