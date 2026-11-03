package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PgnReplayerState

@Composable
fun PgnControlsView(
    state: PgnReplayerState,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReset: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Do not render replay controls if there are no moves in the sequence
    if (state.totalSteps <= 0) return

    val canGoPrev = state.currentStep > 0
    val canGoNext = state.currentStep < state.totalSteps

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("pgn_controls_bar_${state.pgnRef}"),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status row: Current move SAN & step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.currentMoveSan != null) "آخرین حرکت: ${state.currentMoveSan}" else "موقعیت آغازین بازی",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "حرکت ${state.currentStep} از ${state.totalSteps}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Navigation buttons row (RTL: Right side = Previous Move with ChevronRight; Left side = Next Move with ChevronLeft)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset / Jump to start (only shown for multi-move sequences; in RTL sits on the right, so uses right-pointing FastForward ⏭)
                    if (state.totalSteps > 1) {
                        IconButton(
                            onClick = onReset,
                            enabled = canGoPrev,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("pgn_reset_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.FastForward,
                                contentDescription = "بازگشت به شروع",
                                modifier = Modifier.testTag("pgn_reset_icon_fast_forward")
                            )
                        }
                    }

                    // Previous move («حرکت قبلی» with Right-pointing ChevronRight in RTL)
                    FilledTonalButton(
                        onClick = onPrev,
                        enabled = canGoPrev,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("pgn_prev_btn")
                    ) {
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = "حرکت قبلی",
                            modifier = Modifier.testTag("pgn_prev_icon_chevron_right")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حرکت قبلی", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Next move («حرکت بعدی» with Left-pointing ChevronLeft in RTL)
                    Button(
                        onClick = onNext,
                        enabled = canGoNext,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("pgn_next_btn")
                    ) {
                        Text("حرکت بعدی", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = AppIcons.ChevronLeft,
                            contentDescription = "حرکت بعدی",
                            modifier = Modifier.testTag("pgn_next_icon_chevron_left")
                        )
                    }

                    // Jump to end (only shown for multi-move sequences; in RTL sits on the left, so uses left-pointing FastRewind ⏮)
                    if (state.totalSteps > 1) {
                        IconButton(
                            onClick = onEnd,
                            enabled = canGoNext,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("pgn_end_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.FastRewind,
                                contentDescription = "پرش به پایان بازی",
                                modifier = Modifier.testTag("pgn_end_icon_fast_rewind")
                            )
                        }
                    }
                }
            }
        }
    }
}
