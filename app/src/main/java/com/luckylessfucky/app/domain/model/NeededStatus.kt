package com.luckylessfucky.app.domain.model

/**
 * Any Pokémon that a user has recorded in their Pokédex is labeled as "Not Needed".
 * All other Pokémon are labeled as "Needed".
 */
enum class NeededStatus(val label: String) {
    NEEDED("Needed"),
    NOT_NEEDED("Not Needed");

    companion object {
        fun fromIsRecorded(isRecorded: Boolean): NeededStatus {
            return if (isRecorded) NOT_NEEDED else NEEDED
        }
    }
}
