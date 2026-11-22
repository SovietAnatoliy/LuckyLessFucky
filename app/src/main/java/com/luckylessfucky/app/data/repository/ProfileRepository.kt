package com.luckylessfucky.app.data.repository

import com.luckylessfucky.app.data.local.dao.TradeProfileDao
import com.luckylessfucky.app.data.local.entity.TradeProfileEntity
import com.luckylessfucky.app.data.preferences.AppPreferencesRepository
import com.luckylessfucky.app.domain.model.ProfileExportPayload
import com.luckylessfucky.app.domain.model.TradeProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ProfileRepository(
    private val tradeProfileDao: TradeProfileDao,
    private val pokedexRepository: PokedexRepository,
    private val preferencesRepository: AppPreferencesRepository
) {
    val tradeProfilesFlow: Flow<List<TradeProfile>> = combine(
        tradeProfileDao.observeAllProfiles(),
        tradeProfileDao.observeAllProfileEntries()
    ) { profiles, entries ->
        val entriesByProfile = entries.groupBy({ it.profileId }, { it.variantId })
        profiles.map { entity ->
            TradeProfile(
                profileId = entity.profileId,
                username = entity.username,
                updatedAtEpochMs = entity.updatedAtEpochMs,
                recordedVariantIds = entriesByProfile[entity.profileId]?.toSet() ?: emptySet()
            )
        }
    }

    val activeTargetProfileFlow: Flow<TradeProfile?> = combine(
        tradeProfilesFlow,
        preferencesRepository.settingsFlow
    ) { profiles, settings ->
        val activeId = settings.activeTargetProfileId ?: return@combine null
        profiles.firstOrNull { it.profileId == activeId }
    }

    /**
     * Exports the current user's own profile and Lucky Pokédex info.
     */
    suspend fun buildUserExportPayload(): ProfileExportPayload {
        val settings = preferencesRepository.settingsFlow.first()
        val profileId = preferencesRepository.getOrCreateUserProfileId()
        val recordedIds = pokedexRepository.getUserRecordedVariantIds().sorted()
        return ProfileExportPayload(
            schemaVersion = 1,
            profileId = profileId,
            username = settings.username,
            exportedAtEpochMs = System.currentTimeMillis(),
            recordedVariantIds = recordedIds
        )
    }

    /**
     * Imports a profile payload into the user's own local profile and Lucky Pokédex.
     */
    suspend fun importIntoUserProfile(payload: ProfileExportPayload) {
        preferencesRepository.setUserProfileIdentity(
            profileId = payload.profileId,
            username = payload.username
        )
        pokedexRepository.replaceUserPokedexEntries(payload.recordedVariantIds)
    }

    /**
     * Exports a specific Trade Target profile.
     */
    suspend fun buildTargetExportPayload(profileId: String): ProfileExportPayload? {
        val entity = tradeProfileDao.getProfileById(profileId) ?: return null
        val recordedIds = tradeProfileDao.getVariantIdsForProfile(profileId).sorted()
        return ProfileExportPayload(
            schemaVersion = 1,
            profileId = entity.profileId,
            username = entity.username,
            exportedAtEpochMs = entity.updatedAtEpochMs,
            recordedVariantIds = recordedIds
        )
    }

    /**
     * Imports or updates a Trade Target profile from a ProfileExportPayload
     * (whether from manual file import or Bluetooth download/update).
     * If a profile with the same profileId or username already exists, updates it in place
     * so any active Lucky Trade Target assignment remains intact.
     */
    suspend fun importOrUpdateTradeTargetProfile(payload: ProfileExportPayload): TradeProfile {
        val existingById = tradeProfileDao.getProfileById(payload.profileId)
        val existingByUsername = if (existingById == null) {
            tradeProfileDao.getProfileByUsername(payload.username)
        } else {
            null
        }
        val targetProfileId = existingById?.profileId
            ?: existingByUsername?.profileId
            ?: payload.profileId

        val updatedAt = System.currentTimeMillis()
        val entity = TradeProfileEntity(
            profileId = targetProfileId,
            username = payload.username.trim().ifEmpty { "Unknown Trainer" },
            updatedAtEpochMs = updatedAt
        )
        val distinctVariants = payload.recordedVariantIds.distinct()
        tradeProfileDao.upsertProfileWithEntries(entity, distinctVariants)

        return TradeProfile(
            profileId = targetProfileId,
            username = entity.username,
            updatedAtEpochMs = updatedAt,
            recordedVariantIds = distinctVariants.toSet()
        )
    }

    suspend fun deleteTradeTargetProfile(profileId: String) {
        val settings = preferencesRepository.settingsFlow.first()
        if (settings.activeTargetProfileId == profileId) {
            preferencesRepository.setActiveTargetProfileId(null)
        }
        tradeProfileDao.deleteProfile(profileId)
    }

    suspend fun setActiveLuckyTradeTarget(profileId: String?) {
        preferencesRepository.setActiveTargetProfileId(profileId)
    }
}
