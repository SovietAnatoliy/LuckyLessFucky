package com.luckylessfucky.app.overlay

import com.luckylessfucky.app.domain.model.ScanResult
import com.luckylessfucky.app.domain.model.TradeProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class IndicatorBaseColor {
    GRAY,
    GREEN,
    RED
}

sealed interface AddButtonVisualState {
    data object Idle : AddButtonVisualState
    data object Scanning : AddButtonVisualState
    data class Success(val pokemonName: String, val message: String) : AddButtonVisualState
    data class Failure(val reason: String) : AddButtonVisualState
}

data class ManualTargetIndicatorState(
    val baseColor: IndicatorBaseColor = IndicatorBaseColor.GRAY,
    val isFadingToGray: Boolean = false,
    /**
     * 0.0f = pure baseColor (GREEN or RED),
     * 1.0f = pure GRAY.
     */
    val fadeFractionToGray: Float = 1.0f
)

data class OverlayUiState(
    val isAutoScanEnabled: Boolean = false,
    val activeTargetProfile: TradeProfile? = null,
    val addButtonState: AddButtonVisualState = AddButtonVisualState.Idle,
    val isTargetScanning: Boolean = false,
    val manualIndicatorState: ManualTargetIndicatorState = ManualTargetIndicatorState(),
    val autoSingleButtonColor: IndicatorBaseColor = IndicatorBaseColor.GRAY,
    val targetFeedbackMessage: String? = null,
    val isInconclusiveNoticeVisible: Boolean = false
)

class OverlayStateHolder(
    private val scope: CoroutineScope,
    private val performScan: suspend () -> ScanResult,
    private val addVariantToUserPokedex: suspend (String) -> Unit
) {
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private var addFeedbackJob: Job? = null
    private var manualHoldAndFadeJob: Job? = null
    private var autoScanLoopJob: Job? = null

    fun updateActiveTargetProfile(profile: TradeProfile?) {
        _uiState.update { current ->
            current.copy(activeTargetProfile = profile)
        }
        if (profile == null) {
            manualHoldAndFadeJob?.cancel()
            _uiState.update { current ->
                current.copy(
                    manualIndicatorState = ManualTargetIndicatorState(
                        baseColor = IndicatorBaseColor.GRAY,
                        isFadingToGray = false,
                        fadeFractionToGray = 1.0f
                    ),
                    autoSingleButtonColor = IndicatorBaseColor.GRAY
                )
            }
        }
    }

    fun updateAutoScanEnabled(enabled: Boolean) {
        val wasEnabled = _uiState.value.isAutoScanEnabled
        if (wasEnabled == enabled && (enabled == (autoScanLoopJob?.isActive == true))) {
            return
        }

        manualHoldAndFadeJob?.cancel()
        autoScanLoopJob?.cancel()

        _uiState.update { current ->
            current.copy(
                isAutoScanEnabled = enabled,
                manualIndicatorState = ManualTargetIndicatorState(
                    baseColor = IndicatorBaseColor.GRAY,
                    isFadingToGray = false,
                    fadeFractionToGray = 1.0f
                ),
                autoSingleButtonColor = IndicatorBaseColor.GRAY,
                isInconclusiveNoticeVisible = false,
                targetFeedbackMessage = null
            )
        }

        if (enabled) {
            startAutoScanLoop()
        }
    }

    /**
     * Button 1: Scans the screen and attempts to add the current Pokémon to the user's Pokédex.
     * Shows visual indication upon success or failure.
     */
    fun onScanAndAddToUserPokedexClicked() {
        addFeedbackJob?.cancel()
        addFeedbackJob = scope.launch {
            _uiState.update { it.copy(addButtonState = AddButtonVisualState.Scanning) }

            when (val result = performScan()) {
                is ScanResult.Conclusive -> {
                    runCatching {
                        addVariantToUserPokedex(result.variant.variantId)
                    }.onSuccess {
                        _uiState.update {
                            it.copy(
                                addButtonState = AddButtonVisualState.Success(
                                    pokemonName = result.variant.displayName,
                                    message = "Added ${result.variant.displayName} to Pokédex"
                                )
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                addButtonState = AddButtonVisualState.Failure(
                                    reason = "Database error: ${err.localizedMessage ?: "Failed"}"
                                )
                            )
                        }
                    }
                }
                is ScanResult.Inconclusive -> {
                    _uiState.update {
                        it.copy(
                            addButtonState = AddButtonVisualState.Failure(
                                reason = "Inconclusive: ${result.reason}"
                            )
                        )
                    }
                }
            }

            delay(ADD_FEEDBACK_DURATION_MS)
            _uiState.update { it.copy(addButtonState = AddButtonVisualState.Idle) }
        }
    }

    /**
     * Manual Mode Button 2a (first button of the second pair):
     * Checks if the Pokémon on screen is "Needed" by the target trade profile.
     * - Informs user if scan is inconclusive.
     * - If conclusive & needed -> Button 2b turns GREEN.
     * - If conclusive & not needed -> Button 2b turns RED.
     * - Holds GREEN or RED for 5 seconds, then fades back to GRAY over 1 second.
     */
    fun onManualTargetCheckClicked() {
        if (_uiState.value.isAutoScanEnabled) return

        manualHoldAndFadeJob?.cancel()
        manualHoldAndFadeJob = scope.launch {
            val targetProfile = _uiState.value.activeTargetProfile
            if (targetProfile == null) {
                _uiState.update {
                    it.copy(
                        isTargetScanning = false,
                        isInconclusiveNoticeVisible = true,
                        targetFeedbackMessage = "Inconclusive: No Lucky Trade Target profile set",
                        manualIndicatorState = ManualTargetIndicatorState(
                            baseColor = IndicatorBaseColor.GRAY,
                            isFadingToGray = false,
                            fadeFractionToGray = 1.0f
                        )
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isTargetScanning = true,
                    isInconclusiveNoticeVisible = false
                )
            }

            when (val result = performScan()) {
                is ScanResult.Inconclusive -> {
                    _uiState.update {
                        it.copy(
                            isTargetScanning = false,
                            isInconclusiveNoticeVisible = true,
                            targetFeedbackMessage = "Scan inconclusive: ${result.reason}",
                            manualIndicatorState = ManualTargetIndicatorState(
                                baseColor = IndicatorBaseColor.GRAY,
                                isFadingToGray = false,
                                fadeFractionToGray = 1.0f
                            )
                        )
                    }
                }
                is ScanResult.Conclusive -> {
                    val isNeeded = targetProfile.needsPokemon(result.variant.variantId)
                    val targetColor = if (isNeeded) {
                        IndicatorBaseColor.GREEN
                    } else {
                        IndicatorBaseColor.RED
                    }
                    val statusWord = if (isNeeded) "Needed" else "Not Needed"

                    _uiState.update {
                        it.copy(
                            isTargetScanning = false,
                            isInconclusiveNoticeVisible = false,
                            targetFeedbackMessage = "${result.variant.displayName}: $statusWord by ${targetProfile.username}",
                            manualIndicatorState = ManualTargetIndicatorState(
                                baseColor = targetColor,
                                isFadingToGray = false,
                                fadeFractionToGray = 0.0f
                            )
                        )
                    }

                    // Hold Green or Red for 5 seconds (5,000 ms)
                    delay(MANUAL_HOLD_DURATION_MS)

                    // Fade back to Gray over the course of 1 second (1,000 ms)
                    val stepDelayMs = MANUAL_FADE_DURATION_MS / FADE_STEPS
                    for (step in 1..FADE_STEPS) {
                        delay(stepDelayMs)
                        val fraction = step.toFloat() / FADE_STEPS.toFloat()
                        val isComplete = step == FADE_STEPS
                        _uiState.update { current ->
                            current.copy(
                                manualIndicatorState = if (isComplete) {
                                    ManualTargetIndicatorState(
                                        baseColor = IndicatorBaseColor.GRAY,
                                        isFadingToGray = false,
                                        fadeFractionToGray = 1.0f
                                    )
                                } else {
                                    ManualTargetIndicatorState(
                                        baseColor = targetColor,
                                        isFadingToGray = true,
                                        fadeFractionToGray = fraction
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Automatic Mode (2-second interval scan):
     * Single button turns GREEN if conclusively Needed, RED if conclusively Not Needed,
     * and GRAY if the scan does not conclusively return a Pokémon (or no target profile is set).
     */
    private fun startAutoScanLoop() {
        autoScanLoopJob?.cancel()
        autoScanLoopJob = scope.launch {
            while (isActive && _uiState.value.isAutoScanEnabled) {
                delay(AUTO_SCAN_INTERVAL_MS)
                if (!_uiState.value.isAutoScanEnabled) break
                executeAutoScanTick()
            }
        }
    }

    suspend fun executeAutoScanTick() {
        val targetProfile = _uiState.value.activeTargetProfile
        if (targetProfile == null) {
            _uiState.update {
                it.copy(
                    autoSingleButtonColor = IndicatorBaseColor.GRAY,
                    targetFeedbackMessage = "Auto-Scan: No Lucky Trade Target set"
                )
            }
            return
        }

        when (val result = performScan()) {
            is ScanResult.Conclusive -> {
                val isNeeded = targetProfile.needsPokemon(result.variant.variantId)
                val color = if (isNeeded) IndicatorBaseColor.GREEN else IndicatorBaseColor.RED
                val statusLabel = if (isNeeded) "Needed" else "Not Needed"
                _uiState.update {
                    it.copy(
                        autoSingleButtonColor = color,
                        targetFeedbackMessage = "${result.variant.displayName}: $statusLabel"
                    )
                }
            }
            is ScanResult.Inconclusive -> {
                _uiState.update {
                    it.copy(
                        autoSingleButtonColor = IndicatorBaseColor.GRAY,
                        targetFeedbackMessage = "Auto-Scan: Inconclusive"
                    )
                }
            }
        }
    }

    fun stopAll() {
        addFeedbackJob?.cancel()
        manualHoldAndFadeJob?.cancel()
        autoScanLoopJob?.cancel()
    }

    companion object {
        const val ADD_FEEDBACK_DURATION_MS = 2500L
        const val MANUAL_HOLD_DURATION_MS = 5000L
        const val MANUAL_FADE_DURATION_MS = 1000L
        const val FADE_STEPS = 20
        const val AUTO_SCAN_INTERVAL_MS = 2000L
    }
}
