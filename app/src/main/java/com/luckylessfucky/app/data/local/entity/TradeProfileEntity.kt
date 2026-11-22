package com.luckylessfucky.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trade_profiles")
data class TradeProfileEntity(
    @PrimaryKey
    val profileId: String,
    val username: String,
    val updatedAtEpochMs: Long
)
