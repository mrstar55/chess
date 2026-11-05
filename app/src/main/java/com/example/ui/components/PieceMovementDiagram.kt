package com.example.ui.components

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chess.ChessEngine
import com.example.model.ChessPiece
import com.example.model.PieceType
import com.example.ui.theme.AppIcons
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure domain helper for piece introduction metadata and valid movement target squares
 * used on Front Matter pages 14..19.
 */
object PieceMovementRules {

    data class PieceIntroMetadata(
        val pieceType: PieceType,
        val nameFa: String,
        val nameEn: String,
        val narrationKey: String,
        val shortRoleFa: String,
        val movementRuleSummaryFa: String
    )

    private val metadataByType = mapOf(
        PieceType.KING to PieceIntroMetadata(
            pieceType = PieceType.KING,
            nameFa = "شاه",
            nameEn = "King",
            narrationKey = "PIECE_KING_NAME",
            shortRoleFa = "مهم‌ترین مهره بازی؛ هدف شطرنج محافظت از شاه خود و مات کردن شاه حریف است.",
            movementRuleSummaryFa = "یک خانه در هر جهت (افقی، عمودی یا قطری)"
        ),
        PieceType.QUEEN to PieceIntroMetadata(
            pieceType = PieceType.QUEEN,
            nameFa = "وزیر",
            nameEn = "Queen",
            narrationKey = "PIECE_QUEEN_NAME",
            shortRoleFa = "قوی‌ترین مهره صفحه شطرنج (ارزش ۹ امتیاز).",
            movementRuleSummaryFa = "حرکت آزاد در هر ۸ جهت: افقی، عمودی و قطری"
        ),
        PieceType.ROOK to PieceIntroMetadata(
            pieceType = PieceType.ROOK,
            nameFa = "رخ",
            nameEn = "Rook",
            narrationKey = "PIECE_ROOK_NAME",
            shortRoleFa = "مهره دوربرد ستون‌ها و عرض‌ها (ارزش ۵ امتیاز).",
            movementRuleSummaryFa = "حرکت مستقیم در ۴ جهت افقی و عمودی (بدون حرکت قطری)"
        ),
        PieceType.BISHOP to PieceIntroMetadata(
            pieceType = PieceType.BISHOP,
            nameFa = "فیل",
            nameEn = "Bishop",
            narrationKey = "PIECE_BISHOP_NAME",
            shortRoleFa = "مهره دوربرد قطرهای هم‌رنگ (ارزش ۳ امتیاز).",
            movementRuleSummaryFa = "حرکت در ۴ جهت قطری روی خانه‌های هم‌رنگ خود"
        ),
        PieceType.KNIGHT to PieceIntroMetadata(
            pieceType = PieceType.KNIGHT,
            nameFa = "اسب",
            nameEn = "Knight",
            narrationKey = "PIECE_KNIGHT_NAME",
            shortRoleFa = "تنها مهره‌ای که از روی سایر مهره‌ها می‌پرد (ارزش ۳ امتیاز).",
            movementRuleSummaryFa = "حرکت به شکل حرف L انگلیسی (دو خانه در یک جهت و یک خانه عمود بر آن)"
        ),
        PieceType.PAWN to PieceIntroMetadata(
            pieceType = PieceType.PAWN,
            nameFa = "پیاده",
            nameEn = "Pawn",
            narrationKey = "PIECE_PAWN_NAME",
            shortRoleFa = "سرباز خط مقدم شطرنج (ارزش ۱ امتیاز) با قابلیت ارتقا در عرض آخر.",
            movementRuleSummaryFa = "یک خانه رو به جلو (یا دو خانه در حرکت اول) و زدن مهره به صورت یک خانه قطری"
        )
    )

    fun metadataFor(pieceType: PieceType): PieceIntroMetadata =
        metadataByType.getValue(pieceType)

    /**
     * Computes all valid educational target squares for [pieceType] placed at [centerSquare]
     * (default `"d4"`, or `"e2"` for [PieceType.PAWN]).
     */
    @JvmOverloads
    fun getTargetSquares(
        pieceType: PieceType,
        centerSquare: String = if (pieceType == PieceType.PAWN) "e2" else "d4"
    ): List<String> {
        val file = (centerSquare[0] - 'a').coerceIn(0, 7)
        val rank = (8 - (centerSquare[1] - '0')).coerceIn(0, 7)
        val result = mutableListOf<String>()

        fun addIfOnBoard(f: Int, r: Int) {
            if (f in 0..7 && r in 0..7) {
                result.add("${'a' + f}${8 - r}")
            }
        }

        when (pieceType) {
            PieceType.QUEEN -> {
                val dirs = listOf(
                    0 to -1, 0 to 1, -1 to 0, 1 to 0,
                    -1 to -1, 1 to -1, -1 to 1, 1 to 1
                )
                for ((df, dr) in dirs) {
                    var step = 1
                    while (true) {
                        val nf = file + df * step
                        val nr = rank + dr * step
                        if (nf !in 0..7 || nr !in 0..7) break
                        addIfOnBoard(nf, nr)
                        step++
                    }
                }
            }
            PieceType.ROOK -> {
                val dirs = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
                for ((df, dr) in dirs) {
                    var step = 1
                    while (true) {
                        val nf = file + df * step
                        val nr = rank + dr * step
                        if (nf !in 0..7 || nr !in 0..7) break
                        addIfOnBoard(nf, nr)
                        step++
                    }
                }
            }
            PieceType.BISHOP -> {
                val dirs = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
                for ((df, dr) in dirs) {
                    var step = 1
                    while (true) {
                        val nf = file + df * step
                        val nr = rank + dr * step
                        if (nf !in 0..7 || nr !in 0..7) break
                        addIfOnBoard(nf, nr)
                        step++
                    }
                }
            }
            PieceType.KNIGHT -> {
                val jumps = listOf(
                    -1 to -2, 1 to -2,
                    -2 to -1, 2 to -1,
                    -2 to 1, 2 to 1,
                    -1 to 2, 1 to 2
                )
                for ((df, dr) in jumps) {
                    addIfOnBoard(file + df, rank + dr)
                }
            }
            PieceType.KING -> {
                val dirs = listOf(
                    0 to -1, 0 to 1, -1 to 0, 1 to 0,
                    -1 to -1, 1 to -1, -1 to 1, 1 to 1
                )
                for ((df, dr) in dirs) {
                    addIfOnBoard(file + df, rank + dr)
                }
            }
            PieceType.PAWN -> {
                addIfOnBoard(file, rank - 1)
                if (rank == 6) {
                    addIfOnBoard(file, rank - 2)
                }
                addIfOnBoard(file - 1, rank - 1)
                addIfOnBoard(file + 1, rank - 1)
            }
        }
        return result
    }

    /**
     * Overload returning 0-indexed `(row, col)` pairs where `row = 0..7` (top rank 8 = 0, bottom rank 1 = 7)
     * and `col = 0..7` (`a..h`).
     */
    fun computeTargetSquares(
        pieceType: PieceType,
        row: Int = if (pieceType == PieceType.PAWN) 6 else 4,
        col: Int = if (pieceType == PieceType.PAWN) 4 else 3
    ): List<Pair<Int, Int>> {
        val centerSquare = "${'a' + col.coerceIn(0, 7)}${8 - row.coerceIn(0, 7)}"
        return getTargetSquares(pieceType, centerSquare).map { sq ->
            val f = (sq[0] - 'a').coerceIn(0, 7)
            val r = (8 - (sq[1] - '0')).coerceIn(0, 7)
            r to f
        }
    }

    /**
     * Returns the ray endpoints (from -> to) for directional arrow rendering on [pieceType].
     */
    fun getDirectionalArrows(
        pieceType: PieceType,
        centerSquare: String = if (pieceType == PieceType.PAWN) "e2" else "d4"
    ): List<Pair<String, String>> {
        return when (pieceType) {
            PieceType.QUEEN -> listOf(
                centerSquare to "d8",
                centerSquare to "d1",
                centerSquare to "a4",
                centerSquare to "h4",
                centerSquare to "a7",
                centerSquare to "h8",
                centerSquare to "a1",
                centerSquare to "g1"
            )
            PieceType.ROOK -> listOf(
                centerSquare to "d8",
                centerSquare to "d1",
                centerSquare to "a4",
                centerSquare to "h4"
            )
            PieceType.BISHOP -> listOf(
                centerSquare to "a7",
                centerSquare to "h8",
                centerSquare to "a1",
                centerSquare to "g1"
            )
            PieceType.KNIGHT -> getTargetSquares(PieceType.KNIGHT, centerSquare).map { centerSquare to it }
            PieceType.KING -> getTargetSquares(PieceType.KING, centerSquare).map { centerSquare to it }
            PieceType.PAWN -> listOf(
                centerSquare to "e4",
                centerSquare to "d3",
                centerSquare to "f3"
            )
        }
    }
}

/**
 * Interactive & animated educational movement diagram for Front Matter pages 14..17
 * (Queen on p.14, Rook on p.15, Bishop on p.16, Knight on p.17).
 * Corrects the source PDF printing mismatch on pages 15..17 and matches the visual style of [ChessBoardView].
 */
@Composable
fun PieceMovementDiagram(
    pieceType: PieceType,
    modifier: Modifier = Modifier,
    centerSquare: String = if (pieceType == PieceType.PAWN) "e2" else "d4",
    maxBoardHeight: Dp = 320.dp,
    isReducedMotion: Boolean? = null
) {
    val context = LocalContext.current
    val resolvedReducedMotion = remember(context, isReducedMotion) {
        isReducedMotion ?: runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val meta = remember(pieceType) { PieceMovementRules.metadataFor(pieceType) }
    val targetSquares = remember(pieceType, centerSquare) {
        PieceMovementRules.getTargetSquares(pieceType, centerSquare)
    }
    val directionalArrows = remember(pieceType, centerSquare) {
        PieceMovementRules.getDirectionalArrows(pieceType, centerSquare)
    }
    var selectedTargetSquare by remember(pieceType) { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "piece_movement_pulse_${pieceType.name}")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val effectivePulse = if (resolvedReducedMotion) 0.85f else pulseProgress

    val centerFile = (centerSquare[0] - 'a').coerceIn(0, 7)
    val centerRank = (8 - (centerSquare[1] - '0')).coerceIn(0, 7)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("piece_movement_diagram_${pieceType.name}")
            .semantics {
                contentDescription = "نمودار آموزشی حرکت ${meta.nameFa}: ${targetSquares.joinToString(",")}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Header badge explaining the rule
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("piece_movement_targets_${pieceType.name}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الگوی حرکت ${meta.nameFa} (${meta.nameEn}) از خانه $centerSquare",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${targetSquares.size} خانه مجاز",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 8x8 Chessboard matching ChessBoardView LTR coordinate layout
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth, maxHeight, maxBoardHeight)
                val coordGutter = (side * 0.055f).coerceIn(13.dp, 19.dp)
                val gridSide = side - coordGutter
                val cellSize = gridSide / 8f

                Column(
                    modifier = Modifier
                        .size(side)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3E2723))
                        .border(1.5.dp, Color(0xFF271815), RoundedCornerShape(8.dp))
                        .padding(1.5.dp)
                ) {
                    Box(modifier = Modifier.size(gridSide + coordGutter - 3.dp)) {
                        Column {
                            for (rank in 0 until 8) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(coordGutter)
                                            .height(cellSize),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${8 - rank}",
                                            color = Color(0xFFF0D9B5),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    for (file in 0 until 8) {
                                        val isLight = (rank + file) % 2 == 0
                                        val sqName = "${'a' + file}${8 - rank}"
                                        val isCenter = file == centerFile && rank == centerRank
                                        val isTarget = sqName in targetSquares
                                        val isSelectedTarget = sqName == selectedTargetSquare

                                        val baseBg = if (isLight) BoardLightSquare else BoardDarkSquare
                                        val bg = when {
                                            isCenter -> SelectedBlue
                                            isSelectedTarget -> Color(0xFF66BB6A).copy(alpha = 0.8f)
                                            isTarget -> Color(0xFF81C784).copy(alpha = 0.28f * effectivePulse)
                                            else -> baseBg
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(cellSize)
                                                .background(bg)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    if (isTarget) {
                                                        selectedTargetSquare = if (selectedTargetSquare == sqName) null else sqName
                                                    } else if (isCenter) {
                                                        selectedTargetSquare = null
                                                    }
                                                }
                                                .let { mod ->
                                                    if (isTarget) {
                                                        mod.testTag("piece_movement_target_$sqName")
                                                    } else {
                                                        mod
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCenter) {
                                                PieceDisplay(
                                                    piece = ChessPiece(pieceType, isWhite = true),
                                                    squareNotation = sqName
                                                )
                                            } else if (isTarget) {
                                                val dotFraction = if (pieceType == PieceType.KNIGHT) 0.40f else 0.30f
                                                Box(
                                                    modifier = Modifier
                                                        .size(cellSize * dotFraction)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Color(0xFF1B5E20).copy(alpha = (0.55f + 0.35f * effectivePulse).coerceIn(0f, 1f))
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(modifier = Modifier.padding(start = coordGutter)) {
                                for (file in 0 until 8) {
                                    Box(
                                        modifier = Modifier
                                            .width(cellSize)
                                            .height(coordGutter),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${'a' + file}",
                                            color = Color(0xFFF0D9B5),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Directional rays / L-jump indicators overlay
                        Canvas(
                            modifier = Modifier
                                .padding(start = coordGutter)
                                .size(gridSide)
                        ) {
                            val cellPx = size.width / 8f
                            val arrowColor = Color(0xFF1B5E20).copy(alpha = (0.45f + 0.35f * effectivePulse).coerceIn(0f, 0.85f))
                            val strokeW = (cellPx * 0.09f).coerceAtLeast(3f)

                            for ((fromSq, toSq) in directionalArrows) {
                                val f0 = (fromSq[0] - 'a').coerceIn(0, 7)
                                val r0 = (8 - (fromSq[1] - '0')).coerceIn(0, 7)
                                val f1 = (toSq[0] - 'a').coerceIn(0, 7)
                                val r1 = (8 - (toSq[1] - '0')).coerceIn(0, 7)

                                val startCenter = Offset((f0 + 0.5f) * cellPx, (r0 + 0.5f) * cellPx)
                                val endCenter = Offset((f1 + 0.5f) * cellPx, (r1 + 0.5f) * cellPx)

                                if (pieceType == PieceType.KNIGHT) {
                                    // Draw subtle L-shaped path from d4 to each knight jump square
                                    val corner = if (kotlin.math.abs(r1 - r0) == 2) {
                                        Offset(startCenter.x, endCenter.y)
                                    } else {
                                        Offset(endCenter.x, startCenter.y)
                                    }
                                    drawLine(
                                        color = arrowColor,
                                        start = startCenter,
                                        end = corner,
                                        strokeWidth = strokeW * 0.75f,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = arrowColor,
                                        start = corner,
                                        end = endCenter,
                                        strokeWidth = strokeW * 0.75f,
                                        cap = StrokeCap.Round
                                    )
                                } else {
                                    val angle = atan2(endCenter.y - startCenter.y, endCenter.x - startCenter.x)
                                    val startOffset = Offset(
                                        startCenter.x + cos(angle) * (cellPx * 0.34f),
                                        startCenter.y + sin(angle) * (cellPx * 0.34f)
                                    )
                                    val tipOffset = Offset(
                                        endCenter.x - cos(angle) * (cellPx * 0.16f),
                                        endCenter.y - sin(angle) * (cellPx * 0.16f)
                                    )
                                    drawLine(
                                        color = arrowColor,
                                        start = startOffset,
                                        end = tipOffset,
                                        strokeWidth = strokeW,
                                        cap = StrokeCap.Round
                                    )
                                    val headLen = cellPx * 0.24f
                                    val wingAngle = 0.5f
                                    val p1 = Offset(
                                        tipOffset.x - headLen * cos(angle - wingAngle),
                                        tipOffset.y - headLen * sin(angle - wingAngle)
                                    )
                                    val p2 = Offset(
                                        tipOffset.x - headLen * cos(angle + wingAngle),
                                        tipOffset.y - headLen * sin(angle + wingAngle)
                                    )
                                    val headPath = Path().apply {
                                        moveTo(tipOffset.x, tipOffset.y)
                                        lineTo(p1.x, p1.y)
                                        lineTo(p2.x, p2.y)
                                        close()
                                    }
                                    drawPath(path = headPath, color = arrowColor)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rule summary footer bar
        Text(
            text = if (selectedTargetSquare != null) {
                "خانه انتخابی: $selectedTargetSquare — ${meta.movementRuleSummaryFa}"
            } else {
                meta.movementRuleSummaryFa
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

/**
 * Interactive 32-piece starting position board for PDF page 14 (`FM_P014_I001` / `PDFOBJ_018`).
 * Tapping any piece selects its square WITHOUT moving it, displays the Persian & English piece name
 * and role summary, and exposes/triggers its narration audio key (`PIECE_KING_NAME`, etc.).
 */
@Composable
fun StartingPositionIntroBoard(
    modifier: Modifier = Modifier,
    maxBoardHeight: Dp = 300.dp,
    onPieceSelected: ((PieceMovementRules.PieceIntroMetadata, String) -> Unit)? = null,
    onPlayPieceAudio: ((String, String) -> Unit)? = null
) {
    val startFen = ChessEngine.STANDARD_START_FEN
    val startState = remember { ChessEngine.parseFen(startFen) }

    var selectedSquare by remember { mutableStateOf("e1") }
    var selectedMeta by remember {
        mutableStateOf(PieceMovementRules.metadataFor(PieceType.KING))
    }
    var selectedIsWhite by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("starting_position_intro_board"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "برای آشنایی با نام و نقش هر مهره، روی مهره‌های تخته زیر بزنید:",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("starting_position_instruction")
        )

        ChessBoardView(
            fen = startFen,
            selectedSquare = selectedSquare,
            onSquareClick = { file, rank ->
                val piece = startState.getPiece(file, rank)
                if (piece != null) {
                    val sq = "${'a' + file}${rank + 1}"
                    selectedSquare = sq
                    val meta = PieceMovementRules.metadataFor(piece.type)
                    selectedMeta = meta
                    selectedIsWhite = piece.isWhite
                    onPieceSelected?.invoke(meta, sq)
                    onPlayPieceAudio?.invoke(meta.narrationKey, meta.nameFa)
                }
            },
            modifier = Modifier.height(maxBoardHeight)
        )

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("piece_intro_selected_info")
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("starting_position_selected_piece_card"),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colorFa = if (selectedIsWhite) "سفید" else "سیاه"
                    Text(
                        text = "نام مهره: ${selectedMeta.nameFa} $colorFa (${selectedMeta.nameEn}) — خانه $selectedSquare",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("piece_intro_selected_title")
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.testTag("piece_intro_narration_key_${selectedMeta.narrationKey}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.VolumeUp,
                                contentDescription = "تلفظ صوتی نام مهره",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = selectedMeta.nameFa,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = "${selectedMeta.shortRoleFa} (${selectedMeta.movementRuleSummaryFa})",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
