package com.luckylessfucky.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luckylessfucky.app.domain.model.PokemonVariant

@Entity(tableName = "pokemon_variants")
data class PokemonVariantEntity(
    @PrimaryKey
    val variantId: String,
    val pokedexNumber: Int,
    val speciesName: String,
    val formOrEventName: String?,
    val isEventVariant: Boolean,
    val isShinyAvailableInGame: Boolean,
    val ocrKeywordsJoined: String,
    val spriteSignatureHex: String?
) {
    fun toDomain(): PokemonVariant {
        val keywords = if (ocrKeywordsJoined.isBlank()) {
            emptyList()
        } else {
            ocrKeywordsJoined.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return PokemonVariant(
            variantId = variantId,
            pokedexNumber = pokedexNumber,
            speciesName = speciesName,
            formOrEventName = formOrEventName,
            isEventVariant = isEventVariant,
            isShinyAvailableInGame = isShinyAvailableInGame,
            ocrKeywords = keywords,
            spriteSignatureHex = spriteSignatureHex
        )
    }

    companion object {
        fun fromDomain(variant: PokemonVariant): PokemonVariantEntity {
            return PokemonVariantEntity(
                variantId = variant.variantId,
                pokedexNumber = variant.pokedexNumber,
                speciesName = variant.speciesName,
                formOrEventName = variant.formOrEventName,
                isEventVariant = variant.isEventVariant,
                isShinyAvailableInGame = variant.isShinyAvailableInGame,
                ocrKeywordsJoined = variant.ocrKeywords.joinToString("|"),
                spriteSignatureHex = variant.spriteSignatureHex
            )
        }
    }
}
