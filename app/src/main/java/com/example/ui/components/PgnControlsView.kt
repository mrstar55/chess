package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "حرکت ${state.currentStep} از ${state.totalSteps}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Navigation buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset / Jump to start
                IconButton(
                    onClick = onReset,
                    enabled = !state.isAtStart,
                    modifier = Modifier.testTag("pgn_reset_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.FastRewind,
                        contentDescription = "بازگشت به شروع"
                    )
                }

                // Previous move
                FilledTonalButton(
                    onClick = onPrev,
                    enabled = !state.isAtStart,
                    modifier = Modifier.testTag("pgn_prev_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "حرکت قبلی"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("قبلی", fontSize = 13.sp)
                }

                // Next move
                Button(
                    onClick = onNext,
                    enabled = !state.isAtEnd,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("pgn_next_btn")
                ) {
                    Text("بعدی", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = AppIcons.ArrowForward,
                        contentDescription = "حرکت بعدی"
                    )
                }

                // Jump to end
                IconButton(
                    onClick = onEnd,
                    enabled = !state.isAtEnd,
                    modifier = Modifier.testTag("pgn_end_btn")
                ) {
                    Icon(
                        imageVector = AppIcons.FastForward,
                        contentDescription = "پرش به پایان بازی"
                    )
                }
            }
        }
    }
}
