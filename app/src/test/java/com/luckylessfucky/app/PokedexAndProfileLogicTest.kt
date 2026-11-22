package com.luckylessfucky.app

import com.luckylessfucky.app.domain.model.NeededStatus
import com.luckylessfucky.app.domain.model.PokedexItem
import com.luckylessfucky.app.domain.model.PokemonVariant
import com.luckylessfucky.app.domain.model.ProfileExportPayload
import com.luckylessfucky.app.domain.model.TradeProfile
import com.luckylessfucky.app.sync.ProfileJsonCodec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class PokedexAndProfileLogicTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `recorded pokemon is labeled Not Needed and unrecorded is labeled Needed`() {
        val pikachu = PokemonVariant(
            variantId = "0025_pikachu_base",
            pokedexNumber = 25,
            speciesName = "Pikachu",
            isShinyAvailableInGame = true
        )

        val recordedItem = PokedexItem(variant = pikachu, isRecorded = true)
        assertEquals(NeededStatus.NOT_NEEDED, recordedItem.neededStatus)
        assertEquals("Not Needed", recordedItem.neededLabel)

        val unrecordedItem = PokedexItem(variant = pikachu, isRecorded = false)
        assertEquals(NeededStatus.NEEDED, unrecordedItem.neededStatus)
        assertEquals("Needed", unrecordedItem.neededLabel)

        val targetProfile = TradeProfile(
            profileId = "target-1",
            username = "Misty",
            updatedAtEpochMs = 1000L,
            recordedVariantIds = setOf("0025_pikachu_base")
        )
        assertFalse(targetProfile.needsPokemon("0025_pikachu_base"))
        assertEquals(NeededStatus.NOT_NEEDED, targetProfile.getNeededStatus("0025_pikachu_base"))
        assertTrue(targetProfile.needsPokemon("0131_lapras_base"))
        assertEquals(NeededStatus.NEEDED, targetProfile.getNeededStatus("0131_lapras_base"))
    }

    @Test
    fun `offline catalog JSON contains shiny available, event variants, and shiny-unavailable entries`() {
        val assetFile = File("src/main/assets/pokemon_go_catalog.json").takeIf { it.exists() }
            ?: File("app/src/main/assets/pokemon_go_catalog.json")
        assertTrue("Catalog JSON file must exist", assetFile.exists())

        val catalog = json.decodeFromString<List<PokemonVariant>>(assetFile.readText())
        assertTrue("Catalog should have at least 200 variants", catalog.size >= 200)

        // Check standard shiny-available
        val basePikachu = catalog.firstOrNull { it.variantId == "0025_pikachu_base" }
        assertTrue(basePikachu != null && basePikachu.isShinyAvailableInGame)

        // Check event variant WITH shiny available
        val partyHatPikachu = catalog.firstOrNull { it.variantId == "0025_pikachu_party_hat" }
        assertTrue(
            partyHatPikachu != null &&
                partyHatPikachu.isEventVariant &&
                partyHatPikachu.isShinyAvailableInGame
        )

        // Check event variant WITHOUT shiny available
        val armoredMewtwo = catalog.firstOrNull { it.variantId == "0150_mewtwo_armored" }
        assertTrue(
            armoredMewtwo != null &&
                armoredMewtwo.isEventVariant &&
                !armoredMewtwo.isShinyAvailableInGame
        )
        assertEquals("Cannot Be Shiny Yet in Game", armoredMewtwo?.shinyAvailabilityLabel)

        // Check standard mythical/pokemon WITHOUT shiny available
        val victini = catalog.firstOrNull { it.variantId == "0494_victini_base" }
        assertTrue(victini != null && !victini.isShinyAvailableInGame)
    }

    @Test
    fun `ProfileJsonCodec encodes and decodes JSON and Bluetooth framed stream with SHA256 verification`() {
        val payload = ProfileExportPayload(
            schemaVersion = 1,
            profileId = "uuid-1234",
            username = "TrainerBrock",
            exportedAtEpochMs = 1763769600000L,
            recordedVariantIds = listOf("0095_onix_base", "0074_geodude_base")
        )

        // JSON file round-trip
        val encodedJson = ProfileJsonCodec.encodeToJson(payload)
        val decodedJson = ProfileJsonCodec.decodeFromJson(encodedJson).getOrThrow()
        assertEquals(payload, decodedJson)

        // Bluetooth framed stream round-trip
        val byteOut = ByteArrayOutputStream()
        ProfileJsonCodec.writeFramedPayload(payload, byteOut)
        val framedBytes = byteOut.toByteArray()

        val decodedStream = ProfileJsonCodec.readFramedPayload(ByteArrayInputStream(framedBytes)).getOrThrow()
        assertEquals(payload, decodedStream)

        // Corrupt a byte in payload and verify checksum rejects it
        val corruptedBytes = framedBytes.copyOf()
        corruptedBytes[corruptedBytes.lastIndex] = (corruptedBytes.last().toInt() xor 0xFF).toByte()
        val corruptedResult = ProfileJsonCodec.readFramedPayload(ByteArrayInputStream(corruptedBytes))
        assertTrue("Corrupted Bluetooth frame must fail checksum validation", corruptedResult.isFailure)
    }
}
