package com.luckylessfucky.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileExportPayload(
    val schemaVersion: Int = 1,
    val profileId: String,
    val username: String,
    val exportedAtEpochMs: Long,
    val recordedVariantIds: List<String>
)

data class TradeProfile(
    val profileId: String,
    val username: String,
    val updatedAtEpochMs: Long,
    val recordedVariantIds: Set<String>
) {
    fun getNeededStatus(variantId: String): NeededStatus {
        return NeededStatus.fromIsRecorded(recordedVariantIds.contains(variantId))
    }

    fun needsPokemon(variantId: String): Boolean {
        return getNeededStatus(variantId) == NeededStatus.NEEDED
    }
}
