package com.luckylessfucky.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luckylessfucky.app.data.local.entity.UserPokedexEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPokedexDao {
    @Query("SELECT variantId FROM user_pokedex_entries")
    fun observeRecordedVariantIds(): Flow<List<String>>

    @Query("SELECT variantId FROM user_pokedex_entries")
    suspend fun getRecordedVariantIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: UserPokedexEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<UserPokedexEntryEntity>)

    @Query("DELETE FROM user_pokedex_entries WHERE variantId = :variantId")
    suspend fun deleteEntry(variantId: String)

    @Query("DELETE FROM user_pokedex_entries")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAllEntries(entries: List<UserPokedexEntryEntity>) {
        clearAll()
        insertEntries(entries)
    }
}
