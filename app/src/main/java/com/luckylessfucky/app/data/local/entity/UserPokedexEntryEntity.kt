package com.luckylessfucky.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_pokedex_entries")
data class UserPokedexEntryEntity(
    @PrimaryKey
    val variantId: String,
    val addedAtEpochMs: Long
)
