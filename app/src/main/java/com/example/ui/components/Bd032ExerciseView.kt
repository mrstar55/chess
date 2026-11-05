package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExerciseDefinition
import com.example.model.ExercisePhase
import com.example.model.ExerciseUiState
import com.example.ui.theme.AppIcons

private fun defaultExerciseTitleFa(fen: String): String {
    val activeColor = fen.trim().split(Regex("\\s+")).getOrNull(1) ?: "w"
    val sideLabel = if (activeColor.equals("b", ignoreCase = true)) "سیاه" else "سفید"
    return "تمرین تعاملی: مات در یک حرکت برای $sideLabel"
}

private fun defaultExercisePromptFa(fen: String): String {
    val activeColor = fen.trim().split(Regex("\\s+")).getOrNull(1) ?: "w"
    val isBlackTurn = activeColor.equals("b", ignoreCase = true)
    val sideLabel = if (isBlackTurn) "سیاه" else "سفید"
    val opponentKingLabel = if (isBlackTurn) "شاه سفید" else "شاه سیاه"
    return "نوبت $sideLabel است. با لمس مهره مورد نظر روی صفحه شطرنج و سپس لمس خانه مقصد، $opponentKingLabel را در یک حرکت مات کنید:"
}

@Composable
fun InteractiveExerciseView(
    exerciseId: String,
    state: ExerciseUiState,
    onSquareClick: (file: Int, rank: Int) -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit = onReset,
    onToggleLegalMovesHint: () -> Unit = {},
    onTransitionFinished: ((String) -> Unit)? = null,
    showMoveTrail: Boolean = true,
    onMoveTrailFinished: ((String) -> Unit)? = null,
    definition: ExerciseDefinition? = null,
    maxBoardHeight: Dp = 380.dp,
    modifier: Modifier = Modifier
) {
    val resolvedTitleFa = definition?.titleFa?.takeIf { it.isNotBlank() }
        ?: defaultExerciseTitleFa(state.currentFen)
    val resolvedPromptFa = definition?.promptFa?.takeIf { it.isNotBlank() }
        ?: defaultExercisePromptFa(state.currentFen)
    val allowLegalMoveHints = definition?.allowLegalMoveHints ?: true

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_exercise_card_$exerciseId"),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = resolvedTitleFa,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("exercise_title_$exerciseId")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = resolvedPromptFa,
                    fontSize = 11.sp,
                    lineHeight = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("exercise_prompt_$exerciseId")
                )
            }

            // Interactive ChessBoard:
            // - By default (showLegalMovesHint == false), possibleMoves is empty so destination dots never give away the answer
            // - Locked once solved, while animating, or while in WRONG_LEGAL_MOVE awaiting Retry
            // - Strictly isolated from dynamic feedback/button height changes so board dimensions remain 100% constant
            ChessBoardView(
                fen = state.currentFen,
                transition = state.activeTransition,
                onTransitionFinished = onTransitionFinished,
                moveTrail = state.activeMoveTrail,
                showMoveTrail = showMoveTrail,
                onMoveTrailFinished = onMoveTrailFinished,
                illegalFlashSquares = state.illegalFlashSquares,
                selectedSquare = state.selectedSquare,
                possibleMoves = state.visibleTargetSquares,
                onSquareClick = if (state.isBoardLocked) null else onSquareClick,
                modifier = Modifier
                    .padding(vertical = 1.dp)
                    .heightIn(max = maxBoardHeight)
            )

            // Fixed-height Persistent Move Description Bar directly below the board (36.dp permanently reserved)
            PersistentMoveDescriptionBar(
                descriptionFa = state.lastMoveDescriptionFa
            )

            // Permanently reserved 34.dp slot for real-time exercise feedback so board never resizes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(34.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.feedbackMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (state.feedbackIsSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("exercise_feedback_$exerciseId")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("exercise_feedback"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.feedbackIsSuccess) AppIcons.Check else AppIcons.Close,
                                contentDescription = null,
                                tint = if (state.feedbackIsSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.feedbackMessage,
                                fontSize = 10.5.sp,
                                color = if (state.feedbackIsSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                lineHeight = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Fixed-height (34.dp) Action Controls Row: Legal Moves Hint toggle, Retry ("تلاش دوباره"), and Reset ("شروع مجدد وضعیت")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(34.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (allowLegalMoveHints) {
                    Box(modifier = Modifier.testTag("exercise_legal_hint_btn")) {
                        OutlinedButton(
                            onClick = onToggleLegalMovesHint,
                            enabled = !state.isBoardLocked,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = if (state.showLegalMovesHint) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("exercise_legal_hint_btn_$exerciseId")
                        ) {
                            Icon(
                                imageVector = AppIcons.Visibility,
                                contentDescription = "نمایش حرکت‌های قانونی",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "نمایش حرکت‌های قانونی",
                                fontSize = 10.5.sp,
                                fontWeight = if (state.showLegalMovesHint) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Retry Button ("تلاش دوباره") shown after a wrong legal move
                if (state.canRetry || state.phase == ExercisePhase.WRONG_LEGAL_MOVE) {
                    Box(modifier = Modifier.testTag("exercise_retry_btn")) {
                        FilledTonalButton(
                            onClick = onRetry,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("exercise_retry_btn_$exerciseId")
                        ) {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = "تلاش دوباره",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تلاش دوباره",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Reset Button ("شروع مجدد وضعیت")
                Box(modifier = Modifier.testTag("exercise_reset_btn")) {
                    Button(
                        onClick = onReset,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("exercise_reset_btn_$exerciseId")
                    ) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "شروع مجدد تمرین",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "شروع مجدد وضعیت",
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Deprecated(
    message = "Use InteractiveExerciseView instead.",
    replaceWith = ReplaceWith("InteractiveExerciseView(exerciseId, state, onSquareClick, onReset, onRetry, onToggleLegalMovesHint, onTransitionFinished, showMoveTrail, onMoveTrailFinished, definition, maxBoardHeight, modifier)")
)
@Composable
fun Bd032ExerciseView(
    state: ExerciseUiState,
    onSquareClick: (file: Int, rank: Int) -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit = onReset,
    onToggleLegalMovesHint: () -> Unit = {},
    onTransitionFinished: ((String) -> Unit)? = null,
    showMoveTrail: Boolean = true,
    onMoveTrailFinished: ((String) -> Unit)? = null,
    exerciseId: String = "EX_DEMO",
    definition: ExerciseDefinition? = null,
    maxBoardHeight: Dp = 380.dp,
    modifier: Modifier = Modifier
) {
    InteractiveExerciseView(
        exerciseId = exerciseId,
        state = state,
        onSquareClick = onSquareClick,
        onReset = onReset,
        onRetry = onRetry,
        onToggleLegalMovesHint = onToggleLegalMovesHint,
        onTransitionFinished = onTransitionFinished,
        showMoveTrail = showMoveTrail,
        onMoveTrailFinished = onMoveTrailFinished,
        definition = definition,
        maxBoardHeight = maxBoardHeight,
        modifier = modifier
    )
}
