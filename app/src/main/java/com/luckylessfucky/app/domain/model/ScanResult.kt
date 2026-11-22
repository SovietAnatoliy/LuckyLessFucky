package com.luckylessfucky.app.domain.model

sealed interface ScanResult {
    data class Conclusive(
        val variant: PokemonVariant,
        val confidence: Float,
        val matchedText: String
    ) : ScanResult

    data class Inconclusive(
        val reason: String,
        val rawText: String = ""
    ) : ScanResult
}
