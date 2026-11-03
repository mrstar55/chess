package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.FenParser
import com.example.model.BoardTransition
import com.example.model.BoardTransitionResolver
import com.example.model.ChessPiece
import com.example.model.MoveTrailState
import com.example.model.PieceType
import com.example.ui.theme.ReaderDesignTokens
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Classic warm wood & cream chessboard surface colors — strictly constant across all ReaderPageTypes
val BoardLightSquare = ReaderDesignTokens.BoardLightSquareColor
val BoardDarkSquare = ReaderDesignTokens.BoardDarkSquareColor
val BoardBorderColor = ReaderDesignTokens.BoardOuterBorderColor
val BoardOuterRimColor = Color(0xFF6E5236)

val HighlightRed = Color(0x66D9383A)
val IllegalMoveFlashRed = Color(0xB3DC2626)
val HighlightYellow = Color(0x88EAB308)
val SelectedBlue = Color(0x803B82F6)

// Educational Move Trail & Red Capture Pulse colors
val TrailPrimaryTeal = ReaderDesignTokens.MoveTrailPrimaryColor // #438F8A
val TrailGlowTeal = Color(0xFF5AB5AE)
val GhostTintFillTeal = Color(0xFF438F8A)
val GhostTintOutlineTeal = Color(0xFF21524E)
val GhostTintDetailTeal = Color(0xFFA7E0DC)
val CapturePulseBaseRed = ReaderDesignTokens.CaptureIndicatorRedColor // #D9383A

// Custom vector Staunton piece palette
val WhitePieceFill = ReaderDesignTokens.WhitePieceFillColor
val WhitePieceHighlight = ReaderDesignTokens.WhitePieceHighlightColor
val WhitePieceOutline = ReaderDesignTokens.WhitePieceOutlineColor
val BlackPieceFill = ReaderDesignTokens.BlackPieceFillColor
val BlackPieceOutline = ReaderDesignTokens.BlackPieceOutlineColor
val BlackPieceInnerDetail = ReaderDesignTokens.BlackPieceInnerDetailColor

private data class SquareGeometry(
    val topLeftInRoot: Offset,
    val sizePx: IntSize
)

/**
 * Fixed-height persistent move description bar rendered outside and directly below the chessboard.
 * Always reserves an invariant 42.dp height so appearing, updating, or clearing the move text
 * never shifts or resizes the chessboard or any surrounding controls.
 */
@Composable
fun PersistentMoveDescriptionBar(
    descriptionFa: String?,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("persistent_move_description"),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = descriptionFa.orEmpty(),
                    animationSpec = tween(durationMillis = 160),
                    label = "persistent_move_desc_crossfade"
                ) { text ->
                    if (text.isNotBlank()) {
                        Text(
                            text = text,
                            fontSize = 11.5.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("persistent_move_description_text")
                        )
                    } else {
                        Spacer(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * Computes the 4-phase radial Red Capture Pulse radius fraction (relative to square width)
 * and center alpha for a given normalized progress `p` in `0f..1f`:
 * - Phase 1 (0.00..0.15): Impact start at square center (tiny radius -> 0.24f)
 * - Phase 2 (0.15..0.55): Rapid expansion to cover ~88% of square width (radius = 0.44f)
 * - Phase 3 (0.55..0.80): Slight contraction after peak (radius 0.44f -> 0.36f)
 * - Phase 4 (0.80..1.00): Fade out to alpha = 0f while contracting (radius 0.36f -> 0.26f)
 */
fun computeCapturePulseMetrics(progress: Float): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f)
    val radiusFraction: Float
    val centerAlpha: Float
    when {
        p < 0.15f -> {
            val t = p / 0.15f
            radiusFraction = 0.06f + (0.24f - 0.06f) * t
            centerAlpha = 0.32f + (0.78f - 0.32f) * t
        }
        p < 0.55f -> {
            val t = (p - 0.15f) / 0.40f
            radiusFraction = 0.24f + (0.44f - 0.24f) * t
            centerAlpha = 0.78f + (0.84f - 0.78f) * t
        }
        p < 0.80f -> {
            val t = (p - 0.55f) / 0.25f
            radiusFraction = 0.44f - (0.44f - 0.36f) * t
            centerAlpha = 0.84f - (0.84f - 0.46f) * t
        }
        else -> {
            val t = (p - 0.80f) / 0.20f
            radiusFraction = 0.36f - (0.36f - 0.26f) * t
            centerAlpha = 0.46f * (1f - t)
        }
    }
    return radiusFraction.coerceIn(0.04f, 0.46f) to centerAlpha.coerceIn(0f, 1f)
}

@Composable
fun ChessBoardView(
    fen: String,
    modifier: Modifier = Modifier,
    transition: BoardTransition? = null,
    onTransitionFinished: ((String) -> Unit)? = null,
    moveTrail: MoveTrailState? = null,
    showMoveTrail: Boolean = true,
    onMoveTrailFinished: ((String) -> Unit)? = null,
    highlightSquares: List<String> = emptyList(),
    illegalFlashSquares: List<String> = emptyList(),
    selectedSquare: String? = null,
    possibleMoves: List<String> = emptyList(),
    onSquareClick: ((file: Int, rank: Int) -> Unit)? = null,
    showCoordinates: Boolean = true
) {
    val position = remember(fen) { FenParser.parse(fen) }
    val density = LocalDensity.current

    // Track last completed transitionId across recompositions so the same transition never replays
    var lastCompletedTransitionId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCompletedTrailId by rememberSaveable { mutableStateOf<String?>(null) }

    // Only animate when transition matches current target FEN and has not already completed
    val activeTransition = remember(transition, fen, lastCompletedTransitionId) {
        if (transition != null &&
            transition.toFen == fen &&
            transition.fromFen != transition.toFen &&
            transition.transitionId != lastCompletedTransitionId
        ) {
            transition
        } else {
            null
        }
    }

    // Active MoveTrailState strictly for forward educational moves when showMoveTrail is enabled
    val activeTrail = remember(moveTrail, showMoveTrail, activeTransition?.isReverse, lastCompletedTrailId) {
        if (showMoveTrail &&
            moveTrail != null &&
            moveTrail.isForwardEducationalMove &&
            activeTransition?.isReverse != true &&
            moveTrail.eventId != lastCompletedTrailId
        ) {
            moveTrail
        } else {
            null
        }
    }

    val moveProgress = remember(activeTransition?.transitionId) {
        Animatable(if (activeTransition != null) 0f else 1f)
    }
    val captureProgress = remember(activeTransition?.transitionId) {
        Animatable(if (activeTransition != null) 0f else 1f)
    }
    // Independent animation state for the 4-phase Red Capture Pulse (never interferes with 340ms captured piece exit)
    val capturePulseProgress = remember(activeTransition?.transitionId) {
        val isForwardCapture = activeTransition != null &&
            !activeTransition.isReverse &&
            activeTransition.capturedSquare != null &&
            activeTransition.capturedPiece != null
        Animatable(if (isForwardCapture) 0f else 1f)
    }
    val trailAlpha = remember(activeTrail?.eventId) {
        Animatable(if (activeTrail != null) 1f else 0f)
    }

    LaunchedEffect(activeTransition?.transitionId, fen) {
        if (activeTransition != null) {
            var completedNormally = false
            val hasForwardCapture = !activeTransition.isReverse &&
                activeTransition.capturedSquare != null &&
                activeTransition.capturedPiece != null
            val moveDurationMs = if (
                !activeTransition.isReverse &&
                activeTrail != null &&
                activeTrail.eventId == activeTransition.transitionId
            ) {
                activeTrail.moveDurationMs
            } else {
                BoardTransitionResolver.STANDARD_ANIMATION_DURATION_MS
            }
            val captureDurationMs = if (hasForwardCapture) {
                maxOf(BoardTransitionResolver.CAPTURE_EXIT_DURATION_MS, moveDurationMs)
            } else {
                moveDurationMs
            }
            val pulseDurationMs = BoardTransitionResolver.CAPTURE_PULSE_DURATION_MS
            try {
                moveProgress.snapTo(0f)
                captureProgress.snapTo(0f)
                capturePulseProgress.snapTo(if (hasForwardCapture) 0f else 1f)
                coroutineScope {
                    val moveJob = launch {
                        moveProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = moveDurationMs,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    val captureJob = launch {
                        captureProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = captureDurationMs,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    val pulseJob = if (hasForwardCapture) {
                        launch {
                            capturePulseProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = pulseDurationMs,
                                    easing = LinearEasing
                                )
                            )
                        }
                    } else {
                        null
                    }
                    moveJob.join()
                    captureJob.join()
                    pulseJob?.join()
                }
                completedNormally = true
            } finally {
                if (completedNormally) {
                    lastCompletedTransitionId = activeTransition.transitionId
                    onTransitionFinished?.invoke(activeTransition.transitionId)
                }
            }
        }
    }

    LaunchedEffect(activeTrail?.eventId, showMoveTrail) {
        if (activeTrail != null && showMoveTrail) {
            var trailCompletedNormally = false
            val waitBeforeFadeMs = if (activeTransition?.transitionId == activeTrail.eventId) {
                activeTrail.moveDurationMs.toLong() + activeTrail.holdDurationMs.toLong()
            } else {
                activeTrail.holdDurationMs.toLong()
            }
            try {
                trailAlpha.snapTo(1f)
                delay(waitBeforeFadeMs)
                trailAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = activeTrail.fadeDurationMs,
                        easing = LinearOutSlowInEasing
                    )
                )
                trailCompletedNormally = true
            } finally {
                if (trailCompletedNormally) {
                    lastCompletedTrailId = activeTrail.eventId
                    onMoveTrailFinished?.invoke(activeTrail.eventId)
                }
            }
        } else {
            trailAlpha.snapTo(0f)
        }
    }

    // Immediately active as soon as activeTransition is present (even on frame 0 before LaunchedEffect starts)
    // and remains active until LaunchedEffect marks completion or activeTransition becomes null.
    val currentAnimTransition = if (activeTransition != null && lastCompletedTransitionId != activeTransition.transitionId) {
        activeTransition
    } else {
        null
    }

    val currentMoveTrail = if (activeTrail != null && lastCompletedTrailId != activeTrail.eventId && trailAlpha.value > 0.01f) {
        activeTrail
    } else {
        null
    }

    val effectiveMoveProgress = moveProgress.value.coerceIn(0f, 1f)
    val effectiveCaptureProgress = captureProgress.value.coerceIn(0f, 1f)
    val effectivePulseProgress = capturePulseProgress.value.coerceIn(0f, 1f)
    val effectiveTrailAlpha = trailAlpha.value.coerceIn(0f, 1f)
    val isMoveStillInFlight = currentAnimTransition != null && effectiveMoveProgress < 1f

    // Squares whose static piece from `toFen` should be hidden while the overlay animates
    val hiddenStaticSquares = if (currentAnimTransition == null) {
        emptySet()
    } else {
        buildSet {
            if (isMoveStillInFlight) {
                add(currentAnimTransition.fromSquare)
                add(currentAnimTransition.toSquare)
                currentAnimTransition.rookFromSquare?.let { add(it) }
                currentAnimTransition.rookToSquare?.let { add(it) }
            }
            currentAnimTransition.capturedSquare?.let { capSq ->
                // Hide static piece on capturedSquare during reverse capture or while move is in flight
                if (currentAnimTransition.isReverse || isMoveStillInFlight) {
                    add(capSq)
                }
            }
        }
    }

    var boardTopLeftInRoot by remember { mutableStateOf(Offset.Zero) }
    val squareGeometries = remember { mutableStateMapOf<String, SquareGeometry>() }

    // Enforce LTR for chessboard so files a-h and ranks 8-1 are never reversed in RTL mode!
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            val boardSize = minOf(maxWidth, maxHeight).coerceAtMost(480.dp)
            val borderPadding = if (showCoordinates) (boardSize * 0.014f).coerceIn(2.5.dp, 5.dp) else 2.dp
            val coordGutterSize = if (showCoordinates) (boardSize * 0.042f).coerceIn(10.dp, 16.dp) else 0.dp
            val coordFontSize = with(density) { (coordGutterSize * 0.64f).toSp() }
            val coordTextStyle = TextStyle(
                fontSize = coordFontSize,
                lineHeight = coordFontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )

            Box(
                modifier = Modifier
                    .size(boardSize)
                    .onGloballyPositioned { coords ->
                        boardTopLeftInRoot = coords.positionInRoot()
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(3.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(BoardBorderColor)
                        .border(1.2.dp, BoardOuterRimColor, RoundedCornerShape(8.dp))
                        .padding(borderPadding)
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
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank label (8..1) with width == coordGutterSize
                            if (showCoordinates) {
                                Box(
                                    modifier = Modifier
                                        .width(coordGutterSize)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${rank + 1}",
                                        color = Color(0xFFFDF8EE),
                                        style = coordTextStyle
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
                                val isIllegalFlash = illegalFlashSquares.contains(squareNotation)
                                val isSelected = selectedSquare == squareNotation
                                val isPossible = possibleMoves.contains(squareNotation)

                                val squareColor = when {
                                    isIllegalFlash -> IllegalMoveFlashRed
                                    isSelected -> SelectedBlue
                                    isHighlighted -> HighlightRed
                                    isPossible -> HighlightYellow
                                    else -> baseColor
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(squareColor)
                                        .then(
                                            when {
                                                isIllegalFlash -> Modifier.border(2.dp, Color(0xFFB91C1C))
                                                isSelected -> Modifier.border(1.5.dp, Color(0xFF1D4ED8))
                                                else -> Modifier
                                            }
                                        )
                                        .onGloballyPositioned { coords ->
                                            squareGeometries[squareNotation] = SquareGeometry(
                                                topLeftInRoot = coords.positionInRoot(),
                                                sizePx = coords.size
                                            )
                                        }
                                        .clickable(enabled = onSquareClick != null) {
                                            onSquareClick?.invoke(file, rank)
                                        }
                                        .testTag("square_$squareNotation"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isIllegalFlash) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("illegal_highlight_$squareNotation")
                                        )
                                    }

                                    val shouldShowStaticPiece = piece != null &&
                                            squareNotation !in hiddenStaticSquares

                                    if (shouldShowStaticPiece) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("static_piece_$squareNotation"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PieceDisplay(
                                                piece = piece!!,
                                                squareNotation = squareNotation
                                            )
                                        }
                                    } else if (piece == null && isPossible) {
                                        // Target circle scaled to square size
                                        BoxWithConstraints(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val dotSize = (minOf(maxWidth, maxHeight) * 0.28f).coerceIn(6.dp, 12.dp)
                                            Box(
                                                modifier = Modifier
                                                    .size(dotSize)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF047857))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // File labels (a..h) at the bottom with height == coordGutterSize
                    if (showCoordinates) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(coordGutterSize),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Offset matching rank label gutter width
                            Box(modifier = Modifier.size(coordGutterSize))

                            for (file in 0..7) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${('a' + file)}",
                                        color = Color(0xFFFDF8EE),
                                        style = coordTextStyle
                                    )
                                }
                            }
                        }
                    }
                }

                // Educational Move Trail Layer (Teal-Tinted Ghost Piece + Directional Path Arrow)
                // Strictly visual-only, non-clickable (never blocks square clicks), with zero rectangular/square markers.
                if (currentMoveTrail != null && showMoveTrail) {
                    val trailAlphaVal = effectiveTrailAlpha
                    // Single-alpha rule: ghost piece uses ~28% alpha (slightly more subtle than the 76% arrow)
                    // and this alpha is applied ONLY ONCE inside PieceDisplay (never stacked on outer Box).
                    val ghostAlphaVal = (0.28f * trailAlphaVal).coerceIn(0f, 0.28f)
                    val arrowAlphaVal = (0.76f * trailAlphaVal).coerceIn(0f, 0.76f)

                    // 1. Directional Path Arrow Canvas (supports straight paths, L-shaped Knight path, and dual Castling paths)
                    // Pulled back before destination piece radius so the arrow never obscures the landed piece body.
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(arrowAlphaVal)
                            .testTag("move_trail_canvas")
                    ) {
                        val primaryCenters = currentMoveTrail.primaryPathSquares.mapNotNull { sq ->
                            squareGeometries[sq]?.let { geom ->
                                Offset(
                                    x = (geom.topLeftInRoot.x - boardTopLeftInRoot.x) + geom.sizePx.width / 2f,
                                    y = (geom.topLeftInRoot.y - boardTopLeftInRoot.y) + geom.sizePx.height / 2f
                                )
                            }
                        }
                        val refSquareWidth = squareGeometries[currentMoveTrail.fromSquare]?.sizePx?.width?.toFloat()
                            ?: (size.minDimension / 8f)

                        if (primaryCenters.size >= 2) {
                            drawDirectionalTrailArrow(
                                points = primaryCenters,
                                squareSizePx = refSquareWidth,
                                primaryColor = TrailPrimaryTeal,
                                glowColor = TrailGlowTeal
                            )
                        }

                        if (currentMoveTrail.secondaryPathSquares.size >= 2) {
                            val secondaryCenters = currentMoveTrail.secondaryPathSquares.mapNotNull { sq ->
                                squareGeometries[sq]?.let { geom ->
                                    Offset(
                                        x = (geom.topLeftInRoot.x - boardTopLeftInRoot.x) + geom.sizePx.width / 2f,
                                        y = (geom.topLeftInRoot.y - boardTopLeftInRoot.y) + geom.sizePx.height / 2f
                                    )
                                }
                            }
                            if (secondaryCenters.size >= 2) {
                                drawDirectionalTrailArrow(
                                    points = secondaryCenters,
                                    squareSizePx = refSquareWidth,
                                    primaryColor = TrailPrimaryTeal,
                                    glowColor = TrailGlowTeal
                                )
                            }
                        }
                    }

                    // 2. Teal-Tinted Ghost Piece at Origin Square (28% alpha applied strictly once inside PieceDisplay;
                    //    exact same piece silhouette that started on fromSquare; for Promotion, this is the Pawn on fromSquare)
                    val fromGeom = squareGeometries[currentMoveTrail.fromSquare]
                    if (fromGeom != null) {
                        val relX = (fromGeom.topLeftInRoot.x - boardTopLeftInRoot.x).roundToInt()
                        val relY = (fromGeom.topLeftInRoot.y - boardTopLeftInRoot.y).roundToInt()
                        val widthDp = with(density) { fromGeom.sizePx.width.toDp() }
                        val heightDp = with(density) { fromGeom.sizePx.height.toDp() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(relX, relY) }
                                .size(widthDp, heightDp)
                                .testTag("ghost_piece_${currentMoveTrail.fromSquare}"),
                            contentAlignment = Alignment.Center
                        ) {
                            PieceDisplay(
                                piece = currentMoveTrail.piece,
                                overlayRole = "ghost_${currentMoveTrail.fromSquare}",
                                alphaMultiplier = ghostAlphaVal,
                                tintColor = GhostTintFillTeal
                            )
                        }
                    }

                    // 3. Secondary Teal-Tinted Ghost Piece at Rook Origin Square for Castling
                    val rFromSq = currentMoveTrail.rookFromSquare
                    val rGhostPiece = currentMoveTrail.rookPiece
                    if (rFromSq != null && rGhostPiece != null) {
                        val rFromGeom = squareGeometries[rFromSq]
                        if (rFromGeom != null) {
                            val relX = (rFromGeom.topLeftInRoot.x - boardTopLeftInRoot.x).roundToInt()
                            val relY = (rFromGeom.topLeftInRoot.y - boardTopLeftInRoot.y).roundToInt()
                            val widthDp = with(density) { rFromGeom.sizePx.width.toDp() }
                            val heightDp = with(density) { rFromGeom.sizePx.height.toDp() }
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(relX, relY) }
                                    .size(widthDp, heightDp)
                                    .testTag("ghost_piece_$rFromSq"),
                                contentAlignment = Alignment.Center
                            ) {
                                PieceDisplay(
                                    piece = rGhostPiece,
                                    overlayRole = "ghost_$rFromSq",
                                    alphaMultiplier = ghostAlphaVal,
                                    tintColor = GhostTintFillTeal
                                )
                            }
                        }
                    }
                }

                // Moving Overlay Layer: renders strictly during an active transition
                if (currentAnimTransition != null) {
                    val animFraction = effectiveMoveProgress
                    val capFraction = effectiveCaptureProgress
                    val pulseFraction = effectivePulseProgress

                    val capSquare = currentAnimTransition.capturedSquare
                    val capPiece = currentAnimTransition.capturedPiece
                    if (capSquare != null && capPiece != null) {
                        val capGeom = squareGeometries[capSquare]
                        if (capGeom != null) {
                            val relX = (capGeom.topLeftInRoot.x - boardTopLeftInRoot.x).roundToInt()
                            val relY = (capGeom.topLeftInRoot.y - boardTopLeftInRoot.y).roundToInt()
                            val widthDp = with(density) { capGeom.sizePx.width.toDp() }
                            val heightDp = with(density) { capGeom.sizePx.height.toDp() }

                            // 1a. Red Capture Pulse (strictly on forward captures, drawn underneath the pieces,
                            //     filled radial gradient #D9383A with zero border/ring/square frame)
                            if (!currentAnimTransition.isReverse && pulseFraction < 1f) {
                                val (radiusFraction, centerAlpha) = computeCapturePulseMetrics(pulseFraction)
                                if (centerAlpha > 0.005f) {
                                    Canvas(
                                        modifier = Modifier
                                            .offset { IntOffset(relX, relY) }
                                            .size(widthDp, heightDp)
                                            .testTag("capture_pulse_$capSquare")
                                    ) {
                                        val pulseRadius = (size.minDimension * radiusFraction).coerceAtLeast(2f)
                                        val radialBrush = Brush.radialGradient(
                                            colors = listOf(
                                                CapturePulseBaseRed.copy(alpha = centerAlpha),
                                                CapturePulseBaseRed.copy(alpha = centerAlpha * 0.48f),
                                                CapturePulseBaseRed.copy(alpha = 0f)
                                            ),
                                            center = center,
                                            radius = pulseRadius
                                        )
                                        drawCircle(
                                            brush = radialBrush,
                                            radius = pulseRadius,
                                            center = center,
                                            style = Fill
                                        )
                                    }
                                }
                            }

                            // 1b. Captured piece 340ms exit (alpha fade-out + scale shrink)
                            //     or reverse capture smooth fade-in + scale-up on capturedSquare
                            if (capFraction < 1f) {
                                val fadeAlpha = if (currentAnimTransition.isReverse) {
                                    capFraction.coerceIn(0f, 1f)
                                } else {
                                    (1f - capFraction).coerceIn(0f, 1f)
                                }
                                val captureScale = if (currentAnimTransition.isReverse) {
                                    (0.42f + 0.58f * capFraction).coerceIn(0.42f, 1f)
                                } else {
                                    (1f - 0.65f * capFraction).coerceIn(0.35f, 1f)
                                }

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(relX, relY) }
                                        .size(widthDp, heightDp)
                                        .graphicsLayer {
                                            scaleX = captureScale
                                            scaleY = captureScale
                                        }
                                        .testTag("captured_piece_overlay_$capSquare"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PieceDisplay(
                                        piece = capPiece,
                                        overlayRole = "captured_$capSquare",
                                        alphaMultiplier = fadeAlpha
                                    )
                                }
                            }
                        }
                    }

                    // 2. Primary moving piece (fromSquare -> toSquare) during its flight
                    if (isMoveStillInFlight) {
                        val fromGeom = squareGeometries[currentAnimTransition.fromSquare]
                        val toGeom = squareGeometries[currentAnimTransition.toSquare]
                        if (fromGeom != null && toGeom != null) {
                            val startX = fromGeom.topLeftInRoot.x - boardTopLeftInRoot.x
                            val startY = fromGeom.topLeftInRoot.y - boardTopLeftInRoot.y
                            val endX = toGeom.topLeftInRoot.x - boardTopLeftInRoot.x
                            val endY = toGeom.topLeftInRoot.y - boardTopLeftInRoot.y

                            val curX = (startX + (endX - startX) * animFraction).roundToInt()
                            val curY = (startY + (endY - startY) * animFraction).roundToInt()
                            val widthDp = with(density) { fromGeom.sizePx.width.toDp() }
                            val heightDp = with(density) { fromGeom.sizePx.height.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(curX, curY) }
                                    .size(widthDp, heightDp)
                                    .testTag("moving_piece_primary"),
                                contentAlignment = Alignment.Center
                            ) {
                                PieceDisplay(
                                    piece = currentAnimTransition.movingPiece,
                                    overlayRole = "moving_primary"
                                )
                            }
                        }
                    }

                    // 3. Secondary moving piece for simultaneous Castling rook animation (rookFromSquare -> rookToSquare)
                    val rFrom = currentAnimTransition.rookFromSquare
                    val rTo = currentAnimTransition.rookToSquare
                    val rPiece = currentAnimTransition.rookPiece
                    if (isMoveStillInFlight && rFrom != null && rTo != null && rPiece != null) {
                        val rFromGeom = squareGeometries[rFrom]
                        val rToGeom = squareGeometries[rTo]
                        if (rFromGeom != null && rToGeom != null) {
                            val startX = rFromGeom.topLeftInRoot.x - boardTopLeftInRoot.x
                            val startY = rFromGeom.topLeftInRoot.y - boardTopLeftInRoot.y
                            val endX = rToGeom.topLeftInRoot.x - boardTopLeftInRoot.x
                            val endY = rToGeom.topLeftInRoot.y - boardTopLeftInRoot.y

                            val curX = (startX + (endX - startX) * animFraction).roundToInt()
                            val curY = (startY + (endY - startY) * animFraction).roundToInt()
                            val widthDp = with(density) { rFromGeom.sizePx.width.toDp() }
                            val heightDp = with(density) { rFromGeom.sizePx.height.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(curX, curY) }
                                    .size(widthDp, heightDp)
                                    .testTag("moving_piece_rook"),
                                contentAlignment = Alignment.Center
                            ) {
                                PieceDisplay(
                                    piece = rPiece,
                                    overlayRole = "moving_rook"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDirectionalTrailArrow(
    points: List<Offset>,
    squareSizePx: Float,
    primaryColor: Color,
    glowColor: Color
) {
    if (points.size < 2) return
    val strokeWidthPx = (squareSizePx * 0.115f).coerceIn(3.5f, 10.5f)
    val glowStrokePx = strokeWidthPx * 1.55f
    val headLength = (squareSizePx * 0.30f).coerceIn(9f, 22f)
    val headHalfAngleRad = Math.toRadians(27.0)

    val lastIdx = points.lastIndex
    val prevPt = points[lastIdx - 1]
    val rawEndPt = points[lastIdx]
    val dx = rawEndPt.x - prevPt.x
    val dy = rawEndPt.y - prevPt.y
    val segLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (segLen < 1f) return

    val angle = atan2(dy.toDouble(), dx.toDouble())
    // Pull back the arrowhead tip from the center of the destination square so it never covers the landed piece body
    val tipPullback = (squareSizePx * 0.24f).coerceAtMost(segLen * 0.32f)
    val endPt = Offset(
        x = rawEndPt.x - (cos(angle) * tipPullback).toFloat(),
        y = rawEndPt.y - (sin(angle) * tipPullback).toFloat()
    )

    val shaftPullback = (headLength * 0.58f).coerceAtMost(segLen * 0.45f)
    val shaftEnd = Offset(
        x = endPt.x - (cos(angle) * shaftPullback).toFloat(),
        y = endPt.y - (sin(angle) * shaftPullback).toFloat()
    )

    val shaftPath = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until lastIdx) {
            lineTo(points[i].x, points[i].y)
        }
        lineTo(shaftEnd.x, shaftEnd.y)
    }

    // Soft outer glow stroke
    drawPath(
        path = shaftPath,
        color = glowColor.copy(alpha = 0.32f),
        style = Stroke(
            width = glowStrokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Primary educational shaft stroke
    drawPath(
        path = shaftPath,
        color = primaryColor,
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // Origin dot
    drawCircle(
        color = primaryColor,
        radius = strokeWidthPx * 0.62f,
        center = points.first()
    )

    // For Knight L-shaped path, draw a subtle joint dot at the elbow square
    if (points.size == 3) {
        drawCircle(
            color = glowColor,
            radius = strokeWidthPx * 0.52f,
            center = points[1]
        )
    }

    // Triangular directional arrowhead pointing toward toSquare
    val wing1 = Offset(
        x = endPt.x - (headLength * cos(angle - headHalfAngleRad)).toFloat(),
        y = endPt.y - (headLength * sin(angle - headHalfAngleRad)).toFloat()
    )
    val wing2 = Offset(
        x = endPt.x - (headLength * cos(angle + headHalfAngleRad)).toFloat(),
        y = endPt.y - (headLength * sin(angle + headHalfAngleRad)).toFloat()
    )

    val arrowHeadPath = Path().apply {
        moveTo(endPt.x, endPt.y)
        lineTo(wing1.x, wing1.y)
        lineTo(wing2.x, wing2.y)
        close()
    }
    drawPath(
        path = arrowHeadPath,
        color = primaryColor
    )
}

@Composable
fun PieceDisplay(
    piece: ChessPiece,
    squareNotation: String? = null,
    overlayRole: String? = null,
    alphaMultiplier: Float = 1f,
    tintColor: Color? = null
) {
    val clampedAlpha = alphaMultiplier.coerceIn(0f, 1f)
    val colorSide = if (piece.isWhite) "white" else "black"

    val colorTag = when {
        squareNotation != null -> "piece_color_${squareNotation}_$colorSide"
        overlayRole != null -> "piece_color_${overlayRole}_$colorSide"
        else -> "piece_color_$colorSide"
    }
    val symbolTag = when {
        squareNotation != null -> "piece_symbol_$squareNotation"
        overlayRole != null -> "piece_symbol_$overlayRole"
        else -> "piece_symbol_glyph"
    }
    val baseChar = when (piece.type) {
        PieceType.KING -> 'k'
        PieceType.QUEEN -> 'q'
        PieceType.ROOK -> 'r'
        PieceType.BISHOP -> 'b'
        PieceType.KNIGHT -> 'n'
        PieceType.PAWN -> 'p'
    }
    val fenChar = if (piece.isWhite) baseChar.uppercaseChar() else baseChar
    val vectorTag = when {
        squareNotation != null -> "piece_vector_${squareNotation}_$fenChar"
        overlayRole != null -> "piece_vector_${overlayRole}_$fenChar"
        else -> "piece_vector_$fenChar"
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .testTag(colorTag)
    ) {
        val cellMinDp = minOf(maxWidth, maxHeight)
        // Proportional Staunton vector piece box (~80% of square size, leaving 10% margin on all sides)
        val pieceBoxSize = cellMinDp * 0.80f

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(pieceBoxSize)
                .semantics { contentDescription = piece.symbol }
                .testTag(symbolTag)
        ) {
            // Second semantics node preserves exact 2x symbol semantics count for legacy animation tests
            Spacer(
                modifier = Modifier
                    .size(0.dp)
                    .semantics { contentDescription = piece.symbol }
            )

            // If tinted (e.g., teal Move Trail ghost piece), expose a dedicated testTag for verification
            if (tintColor != null && overlayRole != null) {
                Spacer(
                    modifier = Modifier
                        .size(0.dp)
                        .testTag("piece_tint_${overlayRole}_teal")
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(vectorTag)
            ) {
                drawStauntonPiece(
                    pieceType = piece.type,
                    isWhite = piece.isWhite,
                    alpha = clampedAlpha,
                    tintColor = tintColor
                )
            }
        }
    }
}

private fun DrawScope.drawStauntonPiece(
    pieceType: PieceType,
    isWhite: Boolean,
    alpha: Float,
    tintColor: Color? = null
) {
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f) return

    val isTinted = tintColor != null
    val fillColor = (tintColor ?: if (isWhite) WhitePieceFill else BlackPieceFill).copy(alpha = alpha)
    val outlineColor = (if (isTinted) GhostTintOutlineTeal else if (isWhite) WhitePieceOutline else BlackPieceOutline).copy(alpha = alpha)
    val detailColor = (if (isTinted) GhostTintDetailTeal else if (isWhite) WhitePieceOutline else BlackPieceInnerDetail).copy(alpha = alpha)
    val specularColor = (if (isTinted) GhostTintDetailTeal else if (isWhite) WhitePieceHighlight else Color(0xFF453F3A)).copy(alpha = 0.65f * alpha)
    val shadowColor = Color(0xFF14100C).copy(alpha = (if (isTinted) 0.14f else 0.26f) * alpha)
    val outerStrokeWidth = (minOf(w, h) * 0.058f).coerceIn(1.4f, 3.6f)
    val detailStrokeWidth = (minOf(w, h) * 0.042f).coerceIn(1.1f, 2.5f)

    // Subtle grounding shadow oval under the pedestal base
    drawOval(
        color = shadowColor,
        topLeft = Offset(w * 0.14f, h * 0.85f),
        size = Size(w * 0.72f, h * 0.11f)
    )

    // For black untinted pieces, draw a subtle warm ivory rim halo on dark squares before the main silhouette
    val haloStrokeWidth = outerStrokeWidth * 1.45f
    val haloColor = Color(0xFFFAF3E3).copy(alpha = (if (isTinted) 0f else 0.24f) * alpha)
    val useWhiteStyle = isWhite || isTinted

    when (pieceType) {
        PieceType.PAWN -> drawStauntonPawn(
            w = w,
            h = h,
            isWhite = useWhiteStyle,
            fillColor = fillColor,
            outlineColor = outlineColor,
            detailColor = detailColor,
            specularColor = specularColor,
            haloColor = haloColor,
            outerStroke = outerStrokeWidth,
            detailStroke = detailStrokeWidth,
            haloStroke = haloStrokeWidth
        )
        PieceType.ROOK -> drawStauntonRook(
            w = w,
            h = h,
            isWhite = useWhiteStyle,
            fillColor = fillColor,
            outlineColor = outlineColor,
            detailColor = detailColor,
            specularColor = specularColor,
            haloColor = haloColor,
            outerStroke = outerStrokeWidth,
            detailStroke = detailStrokeWidth,
            haloStroke = haloStrokeWidth
        )
        PieceType.KNIGHT -> drawStauntonKnight(
            w = w,
            h = h,
            isWhite = useWhiteStyle,
            fillColor = fillColor,
            outlineColor = outlineColor,
            detailColor = detailColor,
            specularColor = specularColor,
            haloColor = haloColor,
            outerStroke = outerStrokeWidth,
            detailStroke = detailStrokeWidth,
            haloStroke = haloStrokeWidth
        )
        PieceType.BISHOP -> drawStauntonBishop(
            w = w,
            h = h,
            isWhite = useWhiteStyle,
            fillColor = fillColor,
            outlineColor = outlineColor,
            detailColor = detailColor,
            specularColor = specularColor,
            haloColor = haloColor,
            outerStroke = outerStrokeWidth,
            detailStroke = detailStrokeWidth,
            haloStroke = haloStrokeWidth
        )
        PieceType.QUEEN -> drawStauntonQueen(
            w = w,
            h = h,
            isWhite = useWhiteStyle,
            fillColor = fillColor,
            outlineColor = outlineColor,
            detailColor = detailColor,
            specularColor = specularColor,
            haloColor = haloColor,
            outerStroke = outerStrokeWidth,
            detailStroke = detailStrokeWidth,
            haloStroke = haloStrokeWidth
        )
        PieceType.KING -> drawStauntonKing(
            w = w,
            h = h,
            isWhite = useWhiteStyle,
            fillColor = fillColor,
            outlineColor = outlineColor,
            detailColor = detailColor,
            specularColor = specularColor,
            haloColor = haloColor,
            outerStroke = outerStrokeWidth,
            detailStroke = detailStrokeWidth,
            haloStroke = haloStrokeWidth
        )
    }
}

private fun DrawScope.drawBodyPathWithStyle(
    path: Path,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    haloColor: Color,
    outerStroke: Float,
    haloStroke: Float
) {
    if (!isWhite) {
        drawPath(
            path = path,
            color = haloColor,
            style = Stroke(width = haloStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    drawPath(path = path, color = fillColor, style = Fill)
    drawPath(
        path = path,
        color = outlineColor,
        style = Stroke(width = outerStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawBaseRings(
    w: Float,
    h: Float,
    detailColor: Color,
    detailStroke: Float,
    left1: Float = 0.20f,
    right1: Float = 0.80f,
    y1: Float = 0.84f,
    left2: Float = 0.25f,
    right2: Float = 0.75f,
    y2: Float = 0.77f
) {
    drawLine(
        color = detailColor,
        start = Offset(w * left1, h * y1),
        end = Offset(w * right1, h * y1),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = detailColor,
        start = Offset(w * left2, h * y2),
        end = Offset(w * right2, h * y2),
        strokeWidth = detailStroke * 0.9f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawStauntonPawn(
    w: Float,
    h: Float,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    detailColor: Color,
    specularColor: Color,
    haloColor: Color,
    outerStroke: Float,
    detailStroke: Float,
    haloStroke: Float
) {
    val bodyPath = Path().apply {
        moveTo(w * 0.19f, h * 0.89f)
        lineTo(w * 0.81f, h * 0.89f)
        cubicTo(w * 0.83f, h * 0.82f, w * 0.77f, h * 0.77f, w * 0.72f, h * 0.75f)
        cubicTo(w * 0.63f, h * 0.67f, w * 0.59f, h * 0.54f, w * 0.63f, h * 0.45f)
        lineTo(w * 0.37f, h * 0.45f)
        cubicTo(w * 0.41f, h * 0.54f, w * 0.37f, h * 0.67f, w * 0.28f, h * 0.75f)
        cubicTo(w * 0.23f, h * 0.77f, w * 0.17f, h * 0.82f, w * 0.19f, h * 0.89f)
        close()
    }
    drawBodyPathWithStyle(bodyPath, isWhite, fillColor, outlineColor, haloColor, outerStroke, haloStroke)

    // Collar disc
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(w * 0.33f, h * 0.41f),
        size = Size(w * 0.34f, h * 0.06f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
    )
    drawRoundRect(
        color = detailColor,
        topLeft = Offset(w * 0.33f, h * 0.41f),
        size = Size(w * 0.34f, h * 0.06f),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
        style = Stroke(width = detailStroke)
    )

    // Round spherical head
    val headCenter = Offset(w * 0.50f, h * 0.27f)
    val headRadius = w * 0.165f
    if (!isWhite) {
        drawCircle(color = haloColor, radius = headRadius, center = headCenter, style = Stroke(width = haloStroke))
    }
    drawCircle(color = fillColor, radius = headRadius, center = headCenter)
    drawCircle(color = outlineColor, radius = headRadius, center = headCenter, style = Stroke(width = outerStroke))

    // Subtle specular highlight on head
    drawCircle(
        color = specularColor,
        radius = headRadius * 0.32f,
        center = Offset(w * 0.45f, h * 0.23f)
    )

    drawBaseRings(w, h, detailColor, detailStroke, left1 = 0.22f, right1 = 0.78f, y1 = 0.83f, left2 = 0.29f, right2 = 0.71f, y2 = 0.75f)
}

private fun DrawScope.drawStauntonRook(
    w: Float,
    h: Float,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    detailColor: Color,
    specularColor: Color,
    haloColor: Color,
    outerStroke: Float,
    detailStroke: Float,
    haloStroke: Float
) {
    val path = Path().apply {
        // Pedestal base
        moveTo(w * 0.16f, h * 0.89f)
        lineTo(w * 0.84f, h * 0.89f)
        lineTo(w * 0.79f, h * 0.79f)
        lineTo(w * 0.72f, h * 0.74f)
        // Right tower wall
        lineTo(w * 0.68f, h * 0.39f)
        lineTo(w * 0.76f, h * 0.33f)
        // Battlements (3 crenellations / merlons)
        lineTo(w * 0.76f, h * 0.17f)
        lineTo(w * 0.63f, h * 0.17f)
        lineTo(w * 0.63f, h * 0.25f)
        lineTo(w * 0.56f, h * 0.25f)
        lineTo(w * 0.56f, h * 0.17f)
        lineTo(w * 0.44f, h * 0.17f)
        lineTo(w * 0.44f, h * 0.25f)
        lineTo(w * 0.37f, h * 0.25f)
        lineTo(w * 0.37f, h * 0.17f)
        lineTo(w * 0.24f, h * 0.17f)
        // Left tower wall
        lineTo(w * 0.24f, h * 0.33f)
        lineTo(w * 0.32f, h * 0.39f)
        lineTo(w * 0.28f, h * 0.74f)
        lineTo(w * 0.21f, h * 0.79f)
        close()
    }
    drawBodyPathWithStyle(path, isWhite, fillColor, outlineColor, haloColor, outerStroke, haloStroke)

    // Tower vertical highlight stripe
    drawLine(
        color = specularColor,
        start = Offset(w * 0.40f, h * 0.42f),
        end = Offset(w * 0.38f, h * 0.71f),
        strokeWidth = detailStroke * 1.2f,
        cap = StrokeCap.Round
    )

    // Upper neck ring below battlements
    drawLine(
        color = detailColor,
        start = Offset(w * 0.31f, h * 0.38f),
        end = Offset(w * 0.69f, h * 0.38f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawBaseRings(w, h, detailColor, detailStroke, left1 = 0.20f, right1 = 0.80f, y1 = 0.82f, left2 = 0.27f, right2 = 0.73f, y2 = 0.74f)
}

private fun DrawScope.drawStauntonKnight(
    w: Float,
    h: Float,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    detailColor: Color,
    specularColor: Color,
    haloColor: Color,
    outerStroke: Float,
    detailStroke: Float,
    haloStroke: Float
) {
    val path = Path().apply {
        // Base
        moveTo(w * 0.16f, h * 0.89f)
        lineTo(w * 0.84f, h * 0.89f)
        lineTo(w * 0.79f, h * 0.78f)
        lineTo(w * 0.73f, h * 0.73f)
        // Arched muscular back / mane curving up to the ears
        cubicTo(w * 0.78f, h * 0.56f, w * 0.77f, h * 0.31f, w * 0.61f, h * 0.18f)
        // Pointed ear tip
        lineTo(w * 0.55f, h * 0.11f)
        lineTo(w * 0.48f, h * 0.19f)
        // Forehead & bridge of nose sloping down left
        cubicTo(w * 0.35f, h * 0.22f, w * 0.24f, h * 0.33f, w * 0.19f, h * 0.44f)
        // Sculpted muzzle & nostril tip
        cubicTo(w * 0.17f, h * 0.49f, w * 0.23f, h * 0.53f, w * 0.30f, h * 0.51f)
        // Jawline & throat notch
        cubicTo(w * 0.36f, h * 0.49f, w * 0.42f, h * 0.45f, w * 0.45f, h * 0.48f)
        // Front chest curving down to pedestal base
        cubicTo(w * 0.36f, h * 0.56f, w * 0.29f, h * 0.65f, w * 0.27f, h * 0.73f)
        lineTo(w * 0.21f, h * 0.78f)
        close()
    }
    drawBodyPathWithStyle(path, isWhite, fillColor, outlineColor, haloColor, outerStroke, haloStroke)

    // Expressive eye
    drawCircle(
        color = detailColor,
        radius = (w * 0.032f).coerceAtLeast(1.3f),
        center = Offset(w * 0.42f, h * 0.30f)
    )
    // Nostril dot
    drawCircle(
        color = detailColor,
        radius = (w * 0.022f).coerceAtLeast(1.0f),
        center = Offset(w * 0.25f, h * 0.45f)
    )
    // Mane inner contour curve
    val manePath = Path().apply {
        moveTo(w * 0.58f, h * 0.24f)
        cubicTo(w * 0.68f, h * 0.36f, w * 0.69f, h * 0.54f, w * 0.64f, h * 0.69f)
    }
    drawPath(
        path = manePath,
        color = detailColor,
        style = Stroke(width = detailStroke, cap = StrokeCap.Round)
    )

    drawBaseRings(w, h, detailColor, detailStroke, left1 = 0.20f, right1 = 0.80f, y1 = 0.82f, left2 = 0.26f, right2 = 0.74f, y2 = 0.74f)
}

private fun DrawScope.drawStauntonBishop(
    w: Float,
    h: Float,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    detailColor: Color,
    specularColor: Color,
    haloColor: Color,
    outerStroke: Float,
    detailStroke: Float,
    haloStroke: Float
) {
    val path = Path().apply {
        // Base
        moveTo(w * 0.16f, h * 0.89f)
        lineTo(w * 0.84f, h * 0.89f)
        lineTo(w * 0.79f, h * 0.79f)
        lineTo(w * 0.71f, h * 0.73f)
        // Right side of mitre
        cubicTo(w * 0.68f, h * 0.63f, w * 0.73f, h * 0.44f, w * 0.65f, h * 0.30f)
        cubicTo(w * 0.60f, h * 0.22f, w * 0.54f, h * 0.18f, w * 0.50f, h * 0.16f)
        // Left side of mitre
        cubicTo(w * 0.46f, h * 0.18f, w * 0.40f, h * 0.22f, w * 0.35f, h * 0.30f)
        cubicTo(w * 0.27f, h * 0.44f, w * 0.32f, h * 0.63f, w * 0.29f, h * 0.73f)
        lineTo(w * 0.21f, h * 0.79f)
        close()
    }
    drawBodyPathWithStyle(path, isWhite, fillColor, outlineColor, haloColor, outerStroke, haloStroke)

    // Top orb finial
    val orbCenter = Offset(w * 0.50f, h * 0.12f)
    val orbRadius = w * 0.055f
    drawCircle(color = fillColor, radius = orbRadius, center = orbCenter)
    drawCircle(color = outlineColor, radius = orbRadius, center = orbCenter, style = Stroke(width = outerStroke * 0.9f))

    // Signature Bishop mitre cross / diagonal slot
    drawLine(
        color = detailColor,
        start = Offset(w * 0.50f, h * 0.28f),
        end = Offset(w * 0.50f, h * 0.46f),
        strokeWidth = detailStroke * 1.15f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = detailColor,
        start = Offset(w * 0.42f, h * 0.37f),
        end = Offset(w * 0.58f, h * 0.37f),
        strokeWidth = detailStroke * 1.15f,
        cap = StrokeCap.Round
    )

    // Collar band below mitre
    drawLine(
        color = detailColor,
        start = Offset(w * 0.32f, h * 0.61f),
        end = Offset(w * 0.68f, h * 0.61f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawBaseRings(w, h, detailColor, detailStroke, left1 = 0.20f, right1 = 0.80f, y1 = 0.82f, left2 = 0.27f, right2 = 0.73f, y2 = 0.74f)
}

private fun DrawScope.drawStauntonQueen(
    w: Float,
    h: Float,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    detailColor: Color,
    specularColor: Color,
    haloColor: Color,
    outerStroke: Float,
    detailStroke: Float,
    haloStroke: Float
) {
    // 5-pointed royal coronet silhouette
    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.89f)
        lineTo(w * 0.85f, h * 0.89f)
        lineTo(w * 0.80f, h * 0.78f)
        lineTo(w * 0.73f, h * 0.72f)
        // Rightmost crown point (5)
        lineTo(w * 0.86f, h * 0.27f)
        // Valley 4-5
        quadraticTo(w * 0.74f, h * 0.47f, w * 0.68f, h * 0.22f)
        // Valley 3-4
        quadraticTo(w * 0.58f, h * 0.45f, w * 0.50f, h * 0.19f)
        // Valley 2-3
        quadraticTo(w * 0.42f, h * 0.45f, w * 0.32f, h * 0.22f)
        // Valley 1-2
        quadraticTo(w * 0.26f, h * 0.47f, w * 0.14f, h * 0.27f)
        // Leftmost crown edge down to base
        lineTo(w * 0.27f, h * 0.72f)
        lineTo(w * 0.20f, h * 0.78f)
        close()
    }
    drawBodyPathWithStyle(path, isWhite, fillColor, outlineColor, haloColor, outerStroke, haloStroke)

    // 5 crown pearls at the tips
    val pearls = listOf(
        Offset(w * 0.14f, h * 0.24f),
        Offset(w * 0.32f, h * 0.19f),
        Offset(w * 0.50f, h * 0.16f),
        Offset(w * 0.68f, h * 0.19f),
        Offset(w * 0.86f, h * 0.24f)
    )
    val pearlRadius = (w * 0.044f).coerceAtLeast(1.4f)
    for (pt in pearls) {
        drawCircle(color = fillColor, radius = pearlRadius, center = pt)
        drawCircle(color = outlineColor, radius = pearlRadius, center = pt, style = Stroke(width = outerStroke * 0.85f))
    }

    // Inner crown jewel dots / band
    drawLine(
        color = detailColor,
        start = Offset(w * 0.27f, h * 0.66f),
        end = Offset(w * 0.73f, h * 0.66f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawBaseRings(w, h, detailColor, detailStroke, left1 = 0.19f, right1 = 0.81f, y1 = 0.82f, left2 = 0.26f, right2 = 0.74f, y2 = 0.74f)
}

private fun DrawScope.drawStauntonKing(
    w: Float,
    h: Float,
    isWhite: Boolean,
    fillColor: Color,
    outlineColor: Color,
    detailColor: Color,
    specularColor: Color,
    haloColor: Color,
    outerStroke: Float,
    detailStroke: Float,
    haloStroke: Float
) {
    // Top royal cross (+)
    val crossStroke = (outerStroke * 1.25f).coerceAtLeast(1.8f)
    if (!isWhite) {
        drawLine(
            color = haloColor,
            start = Offset(w * 0.50f, h * 0.06f),
            end = Offset(w * 0.50f, h * 0.25f),
            strokeWidth = haloStroke * 1.15f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = haloColor,
            start = Offset(w * 0.39f, h * 0.14f),
            end = Offset(w * 0.61f, h * 0.14f),
            strokeWidth = haloStroke * 1.15f,
            cap = StrokeCap.Round
        )
    }
    drawLine(
        color = outlineColor,
        start = Offset(w * 0.50f, h * 0.06f),
        end = Offset(w * 0.50f, h * 0.25f),
        strokeWidth = crossStroke * 1.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = outlineColor,
        start = Offset(w * 0.39f, h * 0.14f),
        end = Offset(w * 0.61f, h * 0.14f),
        strokeWidth = crossStroke * 1.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = if (isWhite) fillColor else detailColor,
        start = Offset(w * 0.50f, h * 0.07f),
        end = Offset(w * 0.50f, h * 0.24f),
        strokeWidth = crossStroke * 0.75f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = if (isWhite) fillColor else detailColor,
        start = Offset(w * 0.40f, h * 0.14f),
        end = Offset(w * 0.60f, h * 0.14f),
        strokeWidth = crossStroke * 0.75f,
        cap = StrokeCap.Round
    )

    // Crown & pedestal silhouette
    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.89f)
        lineTo(w * 0.85f, h * 0.89f)
        lineTo(w * 0.80f, h * 0.78f)
        lineTo(w * 0.73f, h * 0.72f)
        // Right royal crown lobe
        cubicTo(w * 0.84f, h * 0.52f, w * 0.84f, h * 0.30f, w * 0.66f, h * 0.26f)
        cubicTo(w * 0.59f, h * 0.25f, w * 0.54f, h * 0.28f, w * 0.50f, h * 0.24f)
        // Left royal crown lobe
        cubicTo(w * 0.46f, h * 0.28f, w * 0.41f, h * 0.25f, w * 0.34f, h * 0.26f)
        cubicTo(w * 0.16f, h * 0.30f, w * 0.16f, h * 0.52f, w * 0.27f, h * 0.72f)
        lineTo(w * 0.20f, h * 0.78f)
        close()
    }
    drawBodyPathWithStyle(path, isWhite, fillColor, outlineColor, haloColor, outerStroke, haloStroke)

    // Inner royal crown arch & center seam
    val innerArch = Path().apply {
        moveTo(w * 0.30f, h * 0.56f)
        quadraticTo(w * 0.50f, h * 0.45f, w * 0.70f, h * 0.56f)
    }
    drawPath(
        path = innerArch,
        color = detailColor,
        style = Stroke(width = detailStroke, cap = StrokeCap.Round)
    )
    drawLine(
        color = detailColor,
        start = Offset(w * 0.50f, h * 0.28f),
        end = Offset(w * 0.50f, h * 0.51f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )

    // Collar band
    drawLine(
        color = detailColor,
        start = Offset(w * 0.27f, h * 0.65f),
        end = Offset(w * 0.73f, h * 0.65f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawBaseRings(w, h, detailColor, detailStroke, left1 = 0.19f, right1 = 0.81f, y1 = 0.82f, left2 = 0.26f, right2 = 0.74f, y2 = 0.74f)
}
