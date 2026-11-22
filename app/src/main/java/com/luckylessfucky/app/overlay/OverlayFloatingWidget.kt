package com.luckylessfucky.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OverlayGray = Color(0xFF757575)
private val OverlayGreen = Color(0xFF2E7D32)
private val OverlayRed = Color(0xFFC62828)
private val OverlayAmber = Color(0xFFEF6C00)
private val OverlayDarkBg = Color(0xEB0F172A)

@Composable
fun OverlayFloatingWidget(
    uiState: OverlayUiState,
    onScanAndAddClick: () -> Unit,
    onManualTargetCheckClick: () -> Unit,
    onCloseOverlayClick: () -> Unit,
    onDragOverlay: (dx: Float, dy: Float) -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(OverlayDarkBg)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Bar with Drag Handle + Target Label + Close Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragOverlay(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag Overlay",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = uiState.activeTargetProfile?.let { "Target: ${it.username}" }
                    ?: "Target: None",
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onCloseOverlayClick,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Turn Off Overlay",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Action Buttons Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // BUTTON 1: Scan screen and attempt to add current Pokémon to user's Pokédex
            val addBtnColor = when (uiState.addButtonState) {
                is AddButtonVisualState.Idle -> Color(0xFF2563EB)
                is AddButtonVisualState.Scanning -> Color(0xFF1D4ED8)
                is AddButtonVisualState.Success -> OverlayGreen
                is AddButtonVisualState.Failure -> OverlayAmber
            }

            Button(
                onClick = onScanAndAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = addBtnColor,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                when (uiState.addButtonState) {
                    is AddButtonVisualState.Scanning -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    is AddButtonVisualState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Add Success",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    is AddButtonVisualState.Failure -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Add Failed",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    is AddButtonVisualState.Idle -> {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Scan & Add",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (uiState.addButtonState) {
                        is AddButtonVisualState.Success -> "Added!"
                        is AddButtonVisualState.Failure -> "Failed"
                        else -> "+Dex"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // TARGET PROFILE SECTION:
            // - If Auto-Scan (2s interval) is OFF (default): Show the pair of buttons (Trigger + Status Indicator)
            // - If Auto-Scan (2s interval) is ON: Replace the pair of buttons with a single button
            if (!uiState.isAutoScanEnabled) {
                // Button 2a (First button of the pair): Click to check if Pokémon on screen is Needed by target
                Button(
                    onClick = onManualTargetCheckClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isTargetScanning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = "Check Target Needed",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Check",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Button 2b (Second button of the pair):
                // Turns Green if Needed, Red if Not Needed, Gray by default.
                // After 5 seconds of being Green or Red, fades back to Gray over 1 second.
                val computedIndicatorColor = resolveManualIndicatorColor(uiState.manualIndicatorState)
                val indicatorLabel = when (uiState.manualIndicatorState.baseColor) {
                    IndicatorBaseColor.GREEN -> "NEEDED"
                    IndicatorBaseColor.RED -> "NO"
                    IndicatorBaseColor.GRAY -> "IDLE"
                }

                Button(
                    onClick = { /* Indicator button of the pair */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = computedIndicatorColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.85f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = indicatorLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                // Auto-Scan ON (2s interval): Single button replacing the second pair of buttons
                val singleBtnColor = when (uiState.autoSingleButtonColor) {
                    IndicatorBaseColor.GREEN -> OverlayGreen
                    IndicatorBaseColor.RED -> OverlayRed
                    IndicatorBaseColor.GRAY -> OverlayGray
                }
                val singleBtnText = when (uiState.autoSingleButtonColor) {
                    IndicatorBaseColor.GREEN -> "AUTO: NEEDED"
                    IndicatorBaseColor.RED -> "AUTO: NOT NEEDED"
                    IndicatorBaseColor.GRAY -> "AUTO: SCANNING"
                }

                Button(
                    onClick = { /* Automatic 2s status button */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = singleBtnColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = "Auto Scan Active",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = singleBtnText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Visual feedback banner for Button 1 (Add to Pokédex success or failure)
        val addFeedbackText = when (val state = uiState.addButtonState) {
            is AddButtonVisualState.Success -> "✓ ${state.message}"
            is AddButtonVisualState.Failure -> "⚠ ${state.reason}"
            else -> null
        }

        AnimatedVisibility(visible = addFeedbackText != null) {
            Surface(
                color = when (uiState.addButtonState) {
                    is AddButtonVisualState.Success -> OverlayGreen.copy(alpha = 0.25f)
                    else -> OverlayAmber.copy(alpha = 0.25f)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = addFeedbackText.orEmpty(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Visual notification when Target scan is inconclusive or reports status
        AnimatedVisibility(visible = !uiState.targetFeedbackMessage.isNullOrBlank()) {
            val isWarn = uiState.isInconclusiveNoticeVisible
            Surface(
                color = if (isWarn) Color(0xFF475569) else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = uiState.targetFeedbackMessage.orEmpty(),
                    color = if (isWarn) Color(0xFFFDE047) else Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = if (isWarn) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

fun resolveManualIndicatorColor(state: ManualTargetIndicatorState): Color {
    val activeColor = when (state.baseColor) {
        IndicatorBaseColor.GREEN -> OverlayGreen
        IndicatorBaseColor.RED -> OverlayRed
        IndicatorBaseColor.GRAY -> OverlayGray
    }
    return lerp(activeColor, OverlayGray, state.fadeFractionToGray.coerceIn(0f, 1f))
}
