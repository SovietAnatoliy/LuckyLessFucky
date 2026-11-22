package com.luckylessfucky.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PokemonVariant(
    val variantId: String,
    val pokedexNumber: Int,
    val speciesName: String,
    val formOrEventName: String? = null,
    val isEventVariant: Boolean = false,
    val isShinyAvailableInGame: Boolean = true,
    val ocrKeywords: List<String> = emptyList(),
    val spriteSignatureHex: String? = null
) {
    val displayName: String
        get() = if (formOrEventName.isNullOrBlank()) {
            speciesName
        } else {
            "$speciesName ($formOrEventName)"
        }

    val formattedDexNumber: String
        get() = "#%04d".format(pokedexNumber)

    val shinyAvailabilityLabel: String
        get() = if (isShinyAvailableInGame) {
            "Shiny Available"
        } else {
            "Cannot Be Shiny Yet in Game"
        }
}

data class PokedexItem(
    val variant: PokemonVariant,
    val isRecorded: Boolean
) {
    val neededStatus: NeededStatus
        get() = NeededStatus.fromIsRecorded(isRecorded)

    val neededLabel: String
        get() = neededStatus.label
}
