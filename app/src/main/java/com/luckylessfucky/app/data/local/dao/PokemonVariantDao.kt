package com.luckylessfucky.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luckylessfucky.app.data.local.entity.PokemonVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonVariantDao {
    @Query("SELECT * FROM pokemon_variants ORDER BY pokedexNumber ASC, CASE WHEN formOrEventName IS NULL THEN 0 ELSE 1 END ASC, variantId ASC")
    fun observeAllVariants(): Flow<List<PokemonVariantEntity>>

    @Query("SELECT * FROM pokemon_variants ORDER BY pokedexNumber ASC, CASE WHEN formOrEventName IS NULL THEN 0 ELSE 1 END ASC, variantId ASC")
    suspend fun getAllVariants(): List<PokemonVariantEntity>

    @Query("SELECT COUNT(*) FROM pokemon_variants")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variants: List<PokemonVariantEntity>)
}
