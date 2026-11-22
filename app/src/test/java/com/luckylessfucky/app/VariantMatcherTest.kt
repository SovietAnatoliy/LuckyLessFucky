package com.luckylessfucky.app

import com.luckylessfucky.app.domain.model.PokemonVariant
import com.luckylessfucky.app.domain.model.ScanResult
import com.luckylessfucky.app.scanner.VariantMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VariantMatcherTest {

    private val sampleCatalog = listOf(
        PokemonVariant(
            variantId = "0025_pikachu_base",
            pokedexNumber = 25,
            speciesName = "Pikachu",
            formOrEventName = null,
            isEventVariant = false,
            isShinyAvailableInGame = true,
            ocrKeywords = listOf("pikachu")
        ),
        PokemonVariant(
            variantId = "0025_pikachu_party_hat",
            pokedexNumber = 25,
            speciesName = "Pikachu",
            formOrEventName = "Party Hat",
            isEventVariant = true,
            isShinyAvailableInGame = true,
            ocrKeywords = listOf("pikachu", "party hat"),
            spriteSignatureHex = "FF3B30_PARTY"
        ),
        PokemonVariant(
            variantId = "0025_pikachu_witch_hat",
            pokedexNumber = 25,
            speciesName = "Pikachu",
            formOrEventName = "Witch Hat",
            isEventVariant = true,
            isShinyAvailableInGame = true,
            ocrKeywords = listOf("pikachu", "witch hat"),
            spriteSignatureHex = "5856D6_WITCH"
        ),
        PokemonVariant(
            variantId = "0150_mewtwo_base",
            pokedexNumber = 150,
            speciesName = "Mewtwo",
            formOrEventName = null,
            isEventVariant = false,
            isShinyAvailableInGame = true,
            ocrKeywords = listOf("mewtwo")
        ),
        PokemonVariant(
            variantId = "0150_mewtwo_armored",
            pokedexNumber = 150,
            speciesName = "Mewtwo",
            formOrEventName = "Armored",
            isEventVariant = true,
            isShinyAvailableInGame = false,
            ocrKeywords = listOf("mewtwo", "armored")
        )
    )

    @Test
    fun `matches base species conclusively from standard encounter or storage screen`() {
        val ocrLines = listOf("CP 942", "Pikachu", "78 / 78 HP")
        val result = VariantMatcher.matchScreenText(ocrLines, sampleCatalog)
        assertTrue(result is ScanResult.Conclusive)
        assertEquals("0025_pikachu_base", (result as ScanResult.Conclusive).variant.variantId)
    }

    @Test
    fun `matches nicknamed pokemon conclusively via species candy label`() {
        val ocrLines = listOf("CP 4178", "MyBestBuddy", "180 / 180 HP", "MEWTWO CANDY", "POWER UP")
        val result = VariantMatcher.matchScreenText(ocrLines, sampleCatalog)
        assertTrue(result is ScanResult.Conclusive)
        assertEquals("0150_mewtwo_base", (result as ScanResult.Conclusive).variant.variantId)
    }

    @Test
    fun `matches specific event variant when event keyword or sprite signature is present`() {
        val ocrLines = listOf("CP 512", "Pikachu", "Caught with a Party Hat", "PIKACHU CANDY")
        val result = VariantMatcher.matchScreenText(ocrLines, sampleCatalog)
        assertTrue(result is ScanResult.Conclusive)
        assertEquals("0025_pikachu_party_hat", (result as ScanResult.Conclusive).variant.variantId)

        // Sprite signature disambiguation
        val spriteDisambiguated = VariantMatcher.matchScreenText(
            ocrLines = listOf("CP 600", "Pikachu", "Special Event"),
            catalog = sampleCatalog,
            detectedSpriteSignatureHex = "5856D6_WITCH"
        )
        assertTrue(spriteDisambiguated is ScanResult.Conclusive)
        assertEquals(
            "0025_pikachu_witch_hat",
            (spriteDisambiguated as ScanResult.Conclusive).variant.variantId
        )
    }

    @Test
    fun `returns Inconclusive when screen does not contain a known pokemon or costume is ambiguous`() {
        val nonPokemonScreen = listOf("SETTINGS", "Music", "Sound Effects", "Battery Saver")
        val res1 = VariantMatcher.matchScreenText(nonPokemonScreen, sampleCatalog)
        assertTrue(res1 is ScanResult.Inconclusive)

        val ambiguousCostumeScreen = listOf("CP 450", "Pikachu", "Caught during a special event")
        val res2 = VariantMatcher.matchScreenText(ambiguousCostumeScreen, sampleCatalog)
        assertTrue(res2 is ScanResult.Inconclusive)
    }
}
