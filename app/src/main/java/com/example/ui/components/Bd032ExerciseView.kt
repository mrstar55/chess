package com.example.ui.components

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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.Bd032UiState

@Composable
fun Bd032ExerciseView(
    state: Bd032UiState,
    onSquareClick: (file: Int, rank: Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "تمرین تعاملی: مات در یک حرکت برای سیاه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "نوبت سیاه است. با لمس مهره مورد نظر روی صفحه شطرنج و سپس لمس خانه مقصد، شاه سفید را در یک حرکت مات کنید:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Interactive ChessBoard: disabled once solved until Reset is pressed
            ChessBoardView(
                fen = state.currentFen,
                selectedSquare = state.selectedSquare,
                possibleMoves = state.legalTargetSquares,
                onSquareClick = if (state.isSolved) null else onSquareClick,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Real-time Feedback result from ChessEngine
            if (state.feedbackMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (state.feedbackIsSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("exercise_feedback")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.feedbackIsSuccess) AppIcons.Check else AppIcons.Close,
                            contentDescription = null,
                            tint = if (state.feedbackIsSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.feedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.feedbackIsSuccess) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Reset Button
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .testTag("exercise_reset_btn")
            ) {
                Icon(AppIcons.Refresh, contentDescription = "شروع مجدد تمرین")
                Spacer(modifier = Modifier.width(6.dp))
                Text("شروع مجدد وضعیت")
            }
        }
    }
}
