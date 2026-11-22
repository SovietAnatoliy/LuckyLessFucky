package com.luckylessfucky.app

import com.luckylessfucky.app.domain.model.PokemonVariant
import com.luckylessfucky.app.domain.model.ScanResult
import com.luckylessfucky.app.domain.model.TradeProfile
import com.luckylessfucky.app.overlay.AddButtonVisualState
import com.luckylessfucky.app.overlay.IndicatorBaseColor
import com.luckylessfucky.app.overlay.OverlayStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayStateHolderTest {

    private val shinyPikachu = PokemonVariant(
        variantId = "0025_pikachu_base",
        pokedexNumber = 25,
        speciesName = "Pikachu",
        isShinyAvailableInGame = true
    )

    private val shinyLapras = PokemonVariant(
        variantId = "0131_lapras_base",
        pokedexNumber = 131,
        speciesName = "Lapras",
        isShinyAvailableInGame = true
    )

    @Test
    fun `button 1 scan and add shows visual success on conclusive scan and failure on inconclusive scan`() = runTest {
        val addedIds = mutableListOf<String>()
        var nextScanResult: ScanResult = ScanResult.Conclusive(
            variant = shinyPikachu,
            confidence = 0.99f,
            matchedText = "Pikachu"
        )

        val holder = OverlayStateHolder(
            scope = this,
            performScan = { nextScanResult },
            addVariantToUserPokedex = { addedIds.add(it) }
        )

        holder.onScanAndAddToUserPokedexClicked()
        runCurrent()

        assertEquals(listOf("0025_pikachu_base"), addedIds)
        assertTrue(holder.uiState.value.addButtonState is AddButtonVisualState.Success)

        // Reset to Idle after feedback duration
        advanceTimeBy(OverlayStateHolder.ADD_FEEDBACK_DURATION_MS + 50L)
        runCurrent()
        assertEquals(AddButtonVisualState.Idle, holder.uiState.value.addButtonState)

        // Inconclusive scan shows visual failure indication
        nextScanResult = ScanResult.Inconclusive("No Pokémon detected")
        holder.onScanAndAddToUserPokedexClicked()
        runCurrent()
        assertTrue(holder.uiState.value.addButtonState is AddButtonVisualState.Failure)
    }

    @Test
    fun `manual mode indicator button turns green when Needed, holds 5s, and fades to gray over 1s`() = runTest {
        var nextScanResult: ScanResult = ScanResult.Conclusive(
            variant = shinyLapras,
            confidence = 0.95f,
            matchedText = "Lapras"
        )

        val holder = OverlayStateHolder(
            scope = this,
            performScan = { nextScanResult },
            addVariantToUserPokedex = {}
        )

        // Target profile has Pikachu (Not Needed), does NOT have Lapras (Needed)
        val target = TradeProfile(
            profileId = "p1",
            username = "Ash",
            updatedAtEpochMs = 100L,
            recordedVariantIds = setOf("0025_pikachu_base")
        )
        holder.updateActiveTargetProfile(target)

        // Default is Gray
        assertEquals(IndicatorBaseColor.GRAY, holder.uiState.value.manualIndicatorState.baseColor)

        // Click Button 2a to check Lapras (Needed by Ash)
        holder.onManualTargetCheckClicked()
        runCurrent()

        // Immediately turns solid GREEN
        assertEquals(IndicatorBaseColor.GREEN, holder.uiState.value.manualIndicatorState.baseColor)
        assertFalse(holder.uiState.value.manualIndicatorState.isFadingToGray)
        assertEquals(0.0f, holder.uiState.value.manualIndicatorState.fadeFractionToGray, 0.001f)

        // At 4.9 seconds, still solid GREEN (not fading yet)
        advanceTimeBy(4900L)
        runCurrent()
        assertEquals(IndicatorBaseColor.GREEN, holder.uiState.value.manualIndicatorState.baseColor)
        assertFalse(holder.uiState.value.manualIndicatorState.isFadingToGray)
        assertEquals(0.0f, holder.uiState.value.manualIndicatorState.fadeFractionToGray, 0.001f)

        // At 5.5 seconds (halfway through the 1-second fade), it is fading to Gray (~0.5 fraction)
        advanceTimeBy(600L)
        runCurrent()
        assertEquals(IndicatorBaseColor.GREEN, holder.uiState.value.manualIndicatorState.baseColor)
        assertTrue(holder.uiState.value.manualIndicatorState.isFadingToGray)
        assertEquals(0.5f, holder.uiState.value.manualIndicatorState.fadeFractionToGray, 0.1f)

        // At 6.0 seconds (5s hold + 1s fade complete), back to solid GRAY
        advanceTimeBy(550L)
        runCurrent()
        assertEquals(IndicatorBaseColor.GRAY, holder.uiState.value.manualIndicatorState.baseColor)
        assertFalse(holder.uiState.value.manualIndicatorState.isFadingToGray)
        assertEquals(1.0f, holder.uiState.value.manualIndicatorState.fadeFractionToGray, 0.001f)
    }

    @Test
    fun `manual mode turns red when Not Needed and informs user when inconclusive`() = runTest {
        var nextScanResult: ScanResult = ScanResult.Conclusive(
            variant = shinyPikachu,
            confidence = 0.96f,
            matchedText = "Pikachu"
        )

        val holder = OverlayStateHolder(
            scope = this,
            performScan = { nextScanResult },
            addVariantToUserPokedex = {}
        )

        val target = TradeProfile(
            profileId = "p1",
            username = "Ash",
            updatedAtEpochMs = 100L,
            recordedVariantIds = setOf("0025_pikachu_base")
        )
        holder.updateActiveTargetProfile(target)

        // Scan Pikachu -> Not Needed by Ash -> turns RED
        holder.onManualTargetCheckClicked()
        runCurrent()
        assertEquals(IndicatorBaseColor.RED, holder.uiState.value.manualIndicatorState.baseColor)

        // After 5s hold + 1s fade -> back to GRAY
        advanceTimeBy(6050L)
        runCurrent()
        assertEquals(IndicatorBaseColor.GRAY, holder.uiState.value.manualIndicatorState.baseColor)

        // Inconclusive scan -> user is informed and indicator is GRAY
        nextScanResult = ScanResult.Inconclusive("Screen obscured")
        holder.onManualTargetCheckClicked()
        runCurrent()
        assertTrue(holder.uiState.value.isInconclusiveNoticeVisible)
        assertTrue(holder.uiState.value.targetFeedbackMessage.orEmpty().contains("inconclusive", ignoreCase = true))
        assertEquals(IndicatorBaseColor.GRAY, holder.uiState.value.manualIndicatorState.baseColor)
    }

    @Test
    fun `auto scan mode scans every 2 seconds and turns single button green, red, or gray`() = runTest {
        var nextScanResult: ScanResult = ScanResult.Conclusive(
            variant = shinyLapras,
            confidence = 0.95f,
            matchedText = "Lapras"
        )

        val holder = OverlayStateHolder(
            scope = this,
            performScan = { nextScanResult },
            addVariantToUserPokedex = {}
        )

        val target = TradeProfile(
            profileId = "p1",
            username = "Ash",
            updatedAtEpochMs = 100L,
            recordedVariantIds = setOf("0025_pikachu_base")
        )
        holder.updateActiveTargetProfile(target)

        // Enable 2-second auto scan mode
        holder.updateAutoScanEnabled(true)
        assertTrue(holder.uiState.value.isAutoScanEnabled)
        assertEquals(IndicatorBaseColor.GRAY, holder.uiState.value.autoSingleButtonColor)

        // Tick 1 at 2,000 ms: Lapras is Needed -> GREEN
        advanceTimeBy(2000L)
        runCurrent()
        assertEquals(IndicatorBaseColor.GREEN, holder.uiState.value.autoSingleButtonColor)

        // Tick 2 at 4,000 ms: Pikachu is Not Needed -> RED
        nextScanResult = ScanResult.Conclusive(
            variant = shinyPikachu,
            confidence = 0.95f,
            matchedText = "Pikachu"
        )
        advanceTimeBy(2000L)
        runCurrent()
        assertEquals(IndicatorBaseColor.RED, holder.uiState.value.autoSingleButtonColor)

        // Tick 3 at 6,000 ms: Inconclusive -> GRAY
        nextScanResult = ScanResult.Inconclusive("Map view")
        advanceTimeBy(2000L)
        runCurrent()
        assertEquals(IndicatorBaseColor.GRAY, holder.uiState.value.autoSingleButtonColor)

        holder.stopAll()
    }
}
