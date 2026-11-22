package com.luckylessfucky.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luckylessfucky.app.data.local.entity.TradeProfileEntity
import com.luckylessfucky.app.data.local.entity.TradeProfileEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeProfileDao {
    @Query("SELECT * FROM trade_profiles ORDER BY username COLLATE NOCASE ASC")
    fun observeAllProfiles(): Flow<List<TradeProfileEntity>>

    @Query("SELECT * FROM trade_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: String): TradeProfileEntity?

    @Query("SELECT * FROM trade_profiles WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getProfileByUsername(username: String): TradeProfileEntity?

    @Query("SELECT * FROM trade_profile_entries")
    fun observeAllProfileEntries(): Flow<List<TradeProfileEntryEntity>>

    @Query("SELECT variantId FROM trade_profile_entries WHERE profileId = :profileId")
    suspend fun getVariantIdsForProfile(profileId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: TradeProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<TradeProfileEntryEntity>)

    @Query("DELETE FROM trade_profile_entries WHERE profileId = :profileId")
    suspend fun deleteEntriesForProfile(profileId: String)

    @Query("DELETE FROM trade_profiles WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Transaction
    suspend fun upsertProfileWithEntries(
        profile: TradeProfileEntity,
        variantIds: List<String>
    ) {
        insertProfile(profile)
        deleteEntriesForProfile(profile.profileId)
        val entries = variantIds.distinct().map { variantId ->
            TradeProfileEntryEntity(profileId = profile.profileId, variantId = variantId)
        }
        insertEntries(entries)
    }
}
