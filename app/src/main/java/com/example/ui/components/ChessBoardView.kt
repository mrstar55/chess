package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.FenParser
import com.example.model.ChessPiece

// Standard elegant chess board colors (Wood / Classic)
val BoardLightSquare = Color(0xFFF0D9B5)
val BoardDarkSquare = Color(0xFFB58863)
val BoardBorderColor = Color(0xFF5D4037)
val HighlightRed = Color(0x99EF4444)
val HighlightYellow = Color(0x99FBBF24)
val SelectedBlue = Color(0x993B82F6)

@Composable
fun ChessBoardView(
    fen: String,
    modifier: Modifier = Modifier,
    highlightSquares: List<String> = emptyList(),
    selectedSquare: String? = null,
    possibleMoves: List<String> = emptyList(),
    onSquareClick: ((file: Int, rank: Int) -> Unit)? = null,
    showCoordinates: Boolean = true
) {
    val position = remember(fen) { FenParser.parse(fen) }

    // Enforce LTR for chessboard so files a-h and ranks 8-1 are never reversed in RTL mode!
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val totalWidth = maxWidth
            val boardSize = totalWidth.coerceAtMost(380.dp)

            Column(
                modifier = Modifier
                    .size(boardSize)
                    .shadow(6.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(BoardBorderColor)
                    .padding(if (showCoordinates) 6.dp else 2.dp)
                    .testTag("chessboard_container"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 8 ranks (from 8 down to 1)
                for (rank in 7 downTo 0) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Rank label (8..1)
                        if (showCoordinates) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.CenterVertically),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${rank + 1}",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // 8 files (a to h)
                        for (file in 0..7) {
                            val squareNotation = "${('a' + file)}${rank + 1}"
                            val isLight = (file + rank) % 2 != 0
                            val baseColor = if (isLight) BoardLightSquare else BoardDarkSquare
                            val piece = position.board[rank][file]

                            val isHighlighted = highlightSquares.contains(squareNotation)
                            val isSelected = selectedSquare == squareNotation
                            val isPossible = possibleMoves.contains(squareNotation)

                            val squareColor = when {
                                isSelected -> SelectedBlue
                                isHighlighted -> HighlightRed
                                isPossible -> HighlightYellow
                                else -> baseColor
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(squareColor)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, Color(0xFF1D4ED8))
                                        } else if (isHighlighted) {
                                            Modifier.border(2.dp, Color(0xFFDC2626))
                                        } else Modifier
                                    )
                                    .clickable(enabled = onSquareClick != null) {
                                        onSquareClick?.invoke(file, rank)
                                    }
                                    .testTag("square_$squareNotation"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (piece != null) {
                                    PieceDisplay(piece = piece)
                                } else if (isPossible) {
                                    // Small target circle
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF047857))
                                    )
                                }
                            }
                        }
                    }
                }

                // File labels (a..h) at the bottom
                if (showCoordinates) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Offset for rank label
                        Box(modifier = Modifier.size(14.dp))

                        for (file in 0..7) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${('a' + file)}",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PieceDisplay(piece: ChessPiece) {
    // High-contrast, clear piece glyph
    val pieceColor = if (piece.isWhite) Color(0xFFFFFBEB) else Color(0xFF0F172A)
    val strokeShadow = if (piece.isWhite) Color(0xFF451A03) else Color(0xFFF8FAFC)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Outline shadow for crisp visibility on any square background
        Text(
            text = piece.symbol,
            fontSize = 28.sp,
            color = strokeShadow.copy(alpha = 0.5f),
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Default,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 1.dp, start = 1.dp)
        )
        Text(
            text = piece.symbol,
            fontSize = 28.sp,
            color = pieceColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default,
            textAlign = TextAlign.Center
        )
    }
}
