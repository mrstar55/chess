package com.example.model

import com.example.chess.ChessEngine
import com.example.chess.Move
import com.example.chess.MoveResult
import com.example.data.EducationalPlaybackSpeed

enum class BoardMoveType {
    NORMAL,
    CAPTURE,
    CASTLING_KINGSIDE,
    CASTLING_QUEENSIDE,
    EN_PASSANT,
    PROMOTION,
    PROMOTION_CAPTURE
}

data class BoardTransition(
    val transitionId: String,
    val fromFen: String,
    val toFen: String,
    val moveType: BoardMoveType,
    val movingPiece: ChessPiece,
    val fromSquare: String,
    val toSquare: String,
    val capturedSquare: String? = null,
    val capturedPiece: ChessPiece? = null,
    val rookFromSquare: String? = null,
    val rookToSquare: String? = null,
    val rookPiece: ChessPiece? = null,
    val promotionPiece: ChessPiece? = null,
    val isReverse: Boolean = false,
    val isCheck: Boolean = false,
    val isCheckmate: Boolean = false
)

/**
 * Educational Move Trail state rendered strictly as a temporary visual overlay
 * for forward educational moves (never stored in FEN or ChessEngine).
 */
data class MoveTrailState(
    val eventId: String,
    val fromSquare: String,
    val toSquare: String,
    val piece: ChessPiece,
    val arrivedPiece: ChessPiece,
    val moveType: BoardMoveType,
    val capturedSquare: String? = null,
    val capturedPiece: ChessPiece? = null,
    val rookFromSquare: String? = null,
    val rookToSquare: String? = null,
    val rookPiece: ChessPiece? = null,
    val promotionPiece: ChessPiece? = null,
    val isForwardEducationalMove: Boolean = true,
    val descriptionFa: String,
    val primaryPathSquares: List<String>,
    val secondaryPathSquares: List<String> = emptyList(),
    val moveDurationMs: Int = EducationalPlaybackSpeed.NORMAL.moveDurationMs,
    val holdDurationMs: Int = EducationalPlaybackSpeed.NORMAL.holdDurationMs,
    val fadeDurationMs: Int = EducationalPlaybackSpeed.NORMAL.fadeDurationMs
)

object BoardTransitionResolver {

    const val STANDARD_ANIMATION_DURATION_MS = 220
    const val CAPTURE_EXIT_DURATION_MS = 340
    const val CAPTURE_PULSE_DURATION_MS = 380
    const val DEFAULT_EDUCATIONAL_MOVE_DURATION_MS = 450
    const val DEFAULT_TRAIL_HOLD_DURATION_MS = 800
    const val DEFAULT_TRAIL_FADE_DURATION_MS = 320

    /**
     * Resolves a forward single-move [BoardTransition] between [fromFen] and [toFen]
     * using the verified [ChessEngine] API without modifying or guessing rules.
     * Returns `null` if [fromFen] == [toFen] or if the transition does not correspond
     * to a single legal move in [ChessEngine] whose resulting full 6-field FEN state matches [toFen].
     */
    fun resolveForwardTransition(
        transitionId: String,
        fromFen: String,
        toFen: String
    ): BoardTransition? {
        if (fromFen == toFen) return null
        val startState = try {
            ChessEngine.parseFen(fromFen)
        } catch (e: Exception) {
            return null
        }
        val targetState = try {
            ChessEngine.parseFen(toFen)
        } catch (e: Exception) {
            return null
        }

        val engine = ChessEngine(startState)
        val legalMoves = engine.generateLegalMoves()

        var matchedMove: Move? = null
        var matchedResult: MoveResult? = null

        for (candidate in legalMoves) {
            val sim = ChessEngine(startState)
            val res = sim.makeMove(candidate)
            if (res != null && boardsAndTurnMatch(res.newPosition, targetState)) {
                matchedMove = candidate
                matchedResult = res
                break
            }
        }

        val move = matchedMove ?: return null
        val result = matchedResult ?: return null
        val movingPiece = startState.getPiece(move.fromFile, move.fromRank) ?: return null
        val targetPieceAtDest = startState.getPiece(move.toFile, move.toRank)

        val isCastling = movingPiece.type == PieceType.KING &&
                kotlin.math.abs(move.toFile - move.fromFile) == 2

        val isEnPassant = movingPiece.type == PieceType.PAWN &&
                move.fromFile != move.toFile &&
                targetPieceAtDest == null

        val isPromotion = move.promotion != null

        val capturedSquare: String?
        val capturedPiece: ChessPiece?

        if (isEnPassant) {
            val epPawnRank = move.fromRank
            capturedSquare = "${('a' + move.toFile)}${epPawnRank + 1}"
            capturedPiece = startState.getPiece(move.toFile, epPawnRank)
        } else if (targetPieceAtDest != null) {
            capturedSquare = move.toNotation
            capturedPiece = targetPieceAtDest
        } else {
            capturedSquare = null
            capturedPiece = null
        }

        val rookFromSquare: String?
        val rookToSquare: String?
        val rookPiece: ChessPiece?

        if (isCastling) {
            val homeRank = move.fromRank + 1
            if (move.toFile == 6) {
                rookFromSquare = "h$homeRank"
                rookToSquare = "f$homeRank"
                rookPiece = startState.getPiece(7, move.fromRank)
            } else {
                rookFromSquare = "a$homeRank"
                rookToSquare = "d$homeRank"
                rookPiece = startState.getPiece(0, move.fromRank)
            }
        } else {
            rookFromSquare = null
            rookToSquare = null
            rookPiece = null
        }

        val promotionPiece = if (move.promotion != null) {
            ChessPiece(move.promotion, movingPiece.isWhite)
        } else {
            null
        }

        val moveType = when {
            isCastling && move.toFile == 6 -> BoardMoveType.CASTLING_KINGSIDE
            isCastling -> BoardMoveType.CASTLING_QUEENSIDE
            isEnPassant -> BoardMoveType.EN_PASSANT
            isPromotion && capturedPiece != null -> BoardMoveType.PROMOTION_CAPTURE
            isPromotion -> BoardMoveType.PROMOTION
            capturedPiece != null -> BoardMoveType.CAPTURE
            else -> BoardMoveType.NORMAL
        }

        return BoardTransition(
            transitionId = transitionId,
            fromFen = fromFen,
            toFen = toFen,
            moveType = moveType,
            movingPiece = movingPiece,
            fromSquare = move.fromNotation,
            toSquare = move.toNotation,
            capturedSquare = capturedSquare,
            capturedPiece = capturedPiece,
            rookFromSquare = rookFromSquare,
            rookToSquare = rookToSquare,
            rookPiece = rookPiece,
            promotionPiece = promotionPiece,
            isReverse = false,
            isCheck = result.isCheck,
            isCheckmate = result.isCheckmate
        )
    }

    /**
     * Resolves a reverse single-move [BoardTransition] when stepping backward (Previous)
     * from [currentFen] back to [previousFen].
     */
    fun resolveReverseTransition(
        transitionId: String,
        currentFen: String,
        previousFen: String
    ): BoardTransition? {
        val forward = resolveForwardTransition(
            transitionId = transitionId,
            fromFen = previousFen,
            toFen = currentFen
        ) ?: return null

        val reverseMovingPiece = forward.promotionPiece ?: forward.movingPiece

        return BoardTransition(
            transitionId = transitionId,
            fromFen = currentFen,
            toFen = previousFen,
            moveType = forward.moveType,
            movingPiece = reverseMovingPiece,
            fromSquare = forward.toSquare,
            toSquare = forward.fromSquare,
            capturedSquare = forward.capturedSquare,
            capturedPiece = forward.capturedPiece,
            rookFromSquare = forward.rookToSquare,
            rookToSquare = forward.rookFromSquare,
            rookPiece = forward.rookPiece,
            promotionPiece = forward.promotionPiece,
            isReverse = true,
            isCheck = forward.isCheck,
            isCheckmate = forward.isCheckmate
        )
    }

    /**
     * Automatically resolves either a forward or reverse single-move transition between [fromFen] and [toFen].
     */
    fun resolveTransition(
        transitionId: String,
        fromFen: String,
        toFen: String
    ): BoardTransition? {
        if (fromFen == toFen) return null
        return resolveForwardTransition(transitionId, fromFen, toFen)
            ?: resolveReverseTransition(transitionId, fromFen, toFen)
    }

    /**
     * Builds a [MoveTrailState] strictly for a forward educational move.
     * Returns `null` if [transition] is a reverse move (`isReverse == true`).
     */
    fun createMoveTrail(
        transition: BoardTransition,
        speed: EducationalPlaybackSpeed = EducationalPlaybackSpeed.NORMAL
    ): MoveTrailState? {
        if (transition.isReverse) return null

        val primaryPath = computePrimaryPathSquares(
            fromSquare = transition.fromSquare,
            toSquare = transition.toSquare,
            pieceType = transition.movingPiece.type
        )
        val secondaryPath = if (
            transition.rookFromSquare != null &&
            transition.rookToSquare != null
        ) {
            listOf(transition.rookFromSquare, transition.rookToSquare)
        } else {
            emptyList()
        }

        return MoveTrailState(
            eventId = transition.transitionId,
            fromSquare = transition.fromSquare,
            toSquare = transition.toSquare,
            piece = transition.movingPiece,
            arrivedPiece = transition.promotionPiece ?: transition.movingPiece,
            moveType = transition.moveType,
            capturedSquare = transition.capturedSquare,
            capturedPiece = transition.capturedPiece,
            rookFromSquare = transition.rookFromSquare,
            rookToSquare = transition.rookToSquare,
            rookPiece = transition.rookPiece,
            promotionPiece = transition.promotionPiece,
            isForwardEducationalMove = true,
            descriptionFa = buildMoveDescriptionFa(transition),
            primaryPathSquares = primaryPath,
            secondaryPathSquares = secondaryPath,
            moveDurationMs = speed.moveDurationMs,
            holdDurationMs = speed.holdDurationMs,
            fadeDurationMs = speed.fadeDurationMs
        )
    }

    /**
     * Computes the square waypoints for the directional path arrow:
     * - For Knight (`PieceType.KNIGHT`): returns a 3-square L-shaped path
     *   `[fromSquare, elbowSquare, toSquare]` traversing 2 squares along the major axis
     *   followed by 1 square along the perpendicular axis.
     * - For all other pieces: returns the 2-square straight path `[fromSquare, toSquare]`.
     */
    fun computePrimaryPathSquares(
        fromSquare: String,
        toSquare: String,
        pieceType: PieceType
    ): List<String> {
        if (pieceType != PieceType.KNIGHT || fromSquare.length != 2 || toSquare.length != 2) {
            return listOf(fromSquare, toSquare)
        }
        val fromFile = fromSquare[0] - 'a'
        val fromRank = fromSquare[1] - '1'
        val toFile = toSquare[0] - 'a'
        val toRank = toSquare[1] - '1'
        val df = kotlin.math.abs(toFile - fromFile)
        val dr = kotlin.math.abs(toRank - fromRank)

        return when {
            df == 1 && dr == 2 -> {
                // 2 squares vertically first, then 1 square horizontally
                val elbow = "${('a' + fromFile)}${toRank + 1}"
                listOf(fromSquare, elbow, toSquare)
            }
            df == 2 && dr == 1 -> {
                // 2 squares horizontally first, then 1 square vertically
                val elbow = "${('a' + toFile)}${fromRank + 1}"
                listOf(fromSquare, elbow, toSquare)
            }
            else -> listOf(fromSquare, toSquare)
        }
    }

    fun pieceNameFa(type: PieceType): String = when (type) {
        PieceType.KING -> "شاه"
        PieceType.QUEEN -> "وزیر"
        PieceType.ROOK -> "رخ"
        PieceType.BISHOP -> "فیل"
        PieceType.KNIGHT -> "اسب"
        PieceType.PAWN -> "پیاده"
    }

    fun pieceColorFa(isWhite: Boolean): String = if (isWhite) "سفید" else "سیاه"

    /**
     * Dynamically generates the Persian educational move caption from the real [BoardTransition].
     * Algebraic square coordinates (`g1`, `f3`, `e1`, `h8`, etc.) are preserved in Latin.
     */
    fun buildMoveDescriptionFa(transition: BoardTransition): String {
        val checkSuffix = when {
            transition.isCheckmate -> " (مات)"
            transition.isCheck -> " (کیش)"
            else -> ""
        }
        return when (transition.moveType) {
            BoardMoveType.CASTLING_KINGSIDE -> {
                val rFrom = transition.rookFromSquare ?: "h1"
                val rTo = transition.rookToSquare ?: "f1"
                "قلعه کوتاه: شاه از ${transition.fromSquare} به ${transition.toSquare} و رخ از $rFrom به $rTo$checkSuffix"
            }
            BoardMoveType.CASTLING_QUEENSIDE -> {
                val rFrom = transition.rookFromSquare ?: "a1"
                val rTo = transition.rookToSquare ?: "d1"
                "قلعه بلند: شاه از ${transition.fromSquare} به ${transition.toSquare} و رخ از $rFrom به $rTo$checkSuffix"
            }
            BoardMoveType.EN_PASSANT -> {
                val capSq = transition.capturedSquare ?: transition.toSquare
                "آن‌پاسان: پیاده از خانه ${transition.fromSquare} به خانه ${transition.toSquare} (گرفتن پیاده در $capSq)$checkSuffix"
            }
            BoardMoveType.PROMOTION -> {
                val promoName = pieceNameFa(transition.promotionPiece?.type ?: PieceType.QUEEN)
                "ارتقای پیاده: پیاده از خانه ${transition.fromSquare} به خانه ${transition.toSquare} و تبدیل به $promoName$checkSuffix"
            }
            BoardMoveType.PROMOTION_CAPTURE -> {
                val promoName = pieceNameFa(transition.promotionPiece?.type ?: PieceType.QUEEN)
                val capName = transition.capturedPiece?.let { pieceNameFa(it.type) } ?: "مهره"
                "ارتقای پیاده: پیاده از خانه ${transition.fromSquare} به خانه ${transition.toSquare} (گرفتن $capName) و تبدیل به $promoName$checkSuffix"
            }
            BoardMoveType.CAPTURE -> {
                val moverName = pieceNameFa(transition.movingPiece.type)
                val capName = transition.capturedPiece?.let { pieceNameFa(it.type) } ?: "مهره"
                "$moverName: از خانه ${transition.fromSquare} به خانه ${transition.toSquare} (گرفتن $capName)$checkSuffix"
            }
            BoardMoveType.NORMAL -> {
                val moverName = pieceNameFa(transition.movingPiece.type)
                "$moverName: از خانه ${transition.fromSquare} به خانه ${transition.toSquare}$checkSuffix"
            }
        }
    }

    /**
     * Generates the persistent Persian move description shown below the chessboard in Version 16
     * (e.g., «اسب سفید: از خانه g1 به خانه f3» or «رخ سفید: از خانه a1 به خانه a5»).
     * Returns `null` if [transition] is a reverse/undo move.
     */
    fun buildPersistentMoveDescriptionFa(transition: BoardTransition?): String? {
        if (transition == null || transition.isReverse) return null
        val colorLabel = pieceColorFa(transition.movingPiece.isWhite)
        val checkSuffix = when {
            transition.isCheckmate -> " (مات)"
            transition.isCheck -> " (کیش)"
            else -> ""
        }
        return when (transition.moveType) {
            BoardMoveType.CASTLING_KINGSIDE -> {
                val rFrom = transition.rookFromSquare ?: "h1"
                val rTo = transition.rookToSquare ?: "f1"
                "قلعه کوتاه $colorLabel: شاه از ${transition.fromSquare} به ${transition.toSquare} و رخ از $rFrom به $rTo$checkSuffix"
            }
            BoardMoveType.CASTLING_QUEENSIDE -> {
                val rFrom = transition.rookFromSquare ?: "a1"
                val rTo = transition.rookToSquare ?: "d1"
                "قلعه بلند $colorLabel: شاه از ${transition.fromSquare} به ${transition.toSquare} و رخ از $rFrom به $rTo$checkSuffix"
            }
            BoardMoveType.EN_PASSANT -> {
                val capSq = transition.capturedSquare ?: transition.toSquare
                "آن‌پاسان: پیاده $colorLabel از خانه ${transition.fromSquare} به خانه ${transition.toSquare} (گرفتن پیاده در $capSq)$checkSuffix"
            }
            BoardMoveType.PROMOTION -> {
                val promoName = pieceNameFa(transition.promotionPiece?.type ?: PieceType.QUEEN)
                "ارتقای پیاده $colorLabel: پیاده از خانه ${transition.fromSquare} به خانه ${transition.toSquare} و تبدیل به $promoName$checkSuffix"
            }
            BoardMoveType.PROMOTION_CAPTURE -> {
                val promoName = pieceNameFa(transition.promotionPiece?.type ?: PieceType.QUEEN)
                val capName = transition.capturedPiece?.let { pieceNameFa(it.type) } ?: "مهره"
                "ارتقای پیاده $colorLabel: پیاده از خانه ${transition.fromSquare} به خانه ${transition.toSquare} (گرفتن $capName) و تبدیل به $promoName$checkSuffix"
            }
            BoardMoveType.CAPTURE -> {
                val moverName = pieceNameFa(transition.movingPiece.type)
                val capName = transition.capturedPiece?.let { pieceNameFa(it.type) } ?: "مهره"
                "$moverName $colorLabel: از خانه ${transition.fromSquare} به خانه ${transition.toSquare} (گرفتن $capName)$checkSuffix"
            }
            BoardMoveType.NORMAL -> {
                val moverName = pieceNameFa(transition.movingPiece.type)
                "$moverName $colorLabel: از خانه ${transition.fromSquare} به خانه ${transition.toSquare}$checkSuffix"
            }
        }
    }

    private fun boardsAndTurnMatch(
        a: com.example.chess.ChessBoardState,
        b: com.example.chess.ChessBoardState
    ): Boolean {
        if (a.activeColor != b.activeColor) return false
        if (a.castling != b.castling) return false
        if (a.enPassant != b.enPassant) return false
        if (a.halfMove != b.halfMove) return false
        if (a.fullMove != b.fullMove) return false
        for (r in 0..7) {
            for (f in 0..7) {
                if (a.board[r][f] != b.board[r][f]) return false
            }
        }
        return true
    }
}

