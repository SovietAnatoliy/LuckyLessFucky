package com.luckylessfucky.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "trade_profile_entries",
    primaryKeys = ["profileId", "variantId"],
    foreignKeys = [
        ForeignKey(
            entity = TradeProfileEntity::class,
            parentColumns = ["profileId"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class TradeProfileEntryEntity(
    val profileId: String,
    val variantId: String
)
