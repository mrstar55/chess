package com.example.chess

import com.example.model.ChessPiece
import com.example.model.PieceType

/**
 * Pure-Kotlin Chess Rules Engine for the interactive chess book application.
 *
 * Implements:
 * - Strict 6-field FEN validation (rejects malformed ranks, consecutive digits, invalid pieces, missing/extra/adjacent kings, inactive king in check, and invalid colors/castling/en-passant/counters)
 * - Legal move generation (pins, king safety, double checks)
 * - En passant execution with actual removal of the captured pawn in makeMove and horizontal pin validation
 * - Mandatory pawn promotion validation (rejects promotion=null on 1st/8th rank, rejects non-null promotion on other ranks/pieces)
 * - Castling validation checking actual presence and color of the Rook on its home square (a1/h1/a8/h8) and path safety
 * - Castling rights updates when a King moves, a Rook moves from its home square, OR a Rook is captured on its home square (a1/h1/a8/h8)
 * - SAN generation including en passant captures, piece disambiguation (file, rank, or both), pawn promotions, check (+), and checkmate (#)
 * - Perft move tree enumeration for mathematical verification
 */

data class Move(
    val fromFile: Int, // 0..7 (a..h)
    val fromRank: Int, // 0..7 (1..8)
    val toFile: Int,   // 0..7
    val toRank: Int,   // 0..7
    val promotion: PieceType? = null
) {
    val fromNotation: String get() = "${('a' + fromFile)}${fromRank + 1}"
    val toNotation: String get() = "${('a' + toFile)}${toRank + 1}"
    val uci: String
        get() {
            val promoSuffix = when (promotion) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> ""
            }
            return "$fromNotation$toNotation$promoSuffix"
        }
}

data class MoveResult(
    val move: Move,
    val san: String,
    val newPosition: ChessBoardState,
    val isCheck: Boolean,
    val isCheckmate: Boolean,
    val isStalemate: Boolean
)

data class ChessBoardState(
    val board: Array<Array<ChessPiece?>>, // [rank][file], rank 0 is 1st rank, rank 7 is 8th rank
    val activeColor: Char, // 'w' or 'b'
    val castling: String,
    val enPassant: String,
    val halfMove: Int,
    val fullMove: Int
) {
    fun getPiece(file: Int, rank: Int): ChessPiece? {
        if (rank in 0..7 && file in 0..7) {
            return board[rank][file]
        }
        return null
    }

    fun getPieceAt(notation: String): ChessPiece? {
        if (notation.length != 2) return null
        val file = notation[0] - 'a'
        val rank = notation[1] - '1'
        return getPiece(file, rank)
    }

    fun copyBoard(): Array<Array<ChessPiece?>> {
        return Array(8) { r -> Array(8) { f -> board[r][f] } }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ChessBoardState
        for (r in 0..7) {
            for (f in 0..7) {
                if (board[r][f] != other.board[r][f]) return false
            }
        }
        return activeColor == other.activeColor &&
                castling == other.castling &&
                enPassant == other.enPassant
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + activeColor.hashCode()
        result = 31 * result + castling.hashCode()
        result = 31 * result + enPassant.hashCode()
        return result
    }
}

class ChessEngine(initialFen: String = STANDARD_START_FEN) {

    var state: ChessBoardState = parseFen(initialFen)
        private set

    constructor(initialState: ChessBoardState) : this(STANDARD_START_FEN) {
        state = initialState
    }

    fun setFen(fen: String) {
        state = parseFen(fen)
    }

    fun toFen(): String = stateToFen(state)

    companion object {
        const val STANDARD_START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        /**
         * Strictly parses and validates a standard 6-field FEN string.
         * Throws [IllegalArgumentException] on any malformed structure, incomplete field count,
         * consecutive rank digits, unknown piece, invalid square count, invalid king count,
         * adjacent kings, inconsistent castling rights, or inconsistent en-passant square.
         */
        fun parseFen(fen: String): ChessBoardState {
            val trimmed = fen.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("Invalid FEN: empty string")
            }

            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size != 6) {
                throw IllegalArgumentException(
                    "Invalid standard FEN: expected exactly 6 fields, got ${parts.size}. Use parseIncompleteFen() for 2..5 fields."
                )
            }
            return parseValidatedFenParts(parts)
        }

        /**
         * Explicitly parses an incomplete FEN string (2 to 5 fields) by supplying default values
         * for omitted trailing fields, while enforcing all board, king, castling, and en-passant rules.
         */
        fun parseIncompleteFen(fen: String): ChessBoardState {
            val trimmed = fen.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("Invalid FEN: empty string")
            }
            val rawParts = trimmed.split("\\s+".toRegex())
            if (rawParts.size < 2 || rawParts.size > 6) {
                throw IllegalArgumentException("Invalid FEN: expected between 2 and 6 fields, got ${rawParts.size}")
            }
            val fullParts = listOf(
                rawParts[0],
                rawParts[1],
                if (rawParts.size > 2) rawParts[2] else "-",
                if (rawParts.size > 3) rawParts[3] else "-",
                if (rawParts.size > 4) rawParts[4] else "0",
                if (rawParts.size > 5) rawParts[5] else "1"
            )
            return parseValidatedFenParts(fullParts)
        }

        private fun parseValidatedFenParts(parts: List<String>): ChessBoardState {
            val boardPart = parts[0]
            if (boardPart.count { it == '/' } != 7) {
                throw IllegalArgumentException("Invalid FEN: board must contain exactly 7 '/' separators")
            }
            val ranks = boardPart.split("/")
            if (ranks.size != 8) {
                throw IllegalArgumentException("Invalid FEN: board must have exactly 8 ranks, got ${ranks.size}")
            }

            val board = Array(8) { Array<ChessPiece?>(8) { null } }
            var whiteKings = 0
            var blackKings = 0
            var whiteKingFile = -1
            var whiteKingRank = -1
            var blackKingFile = -1
            var blackKingRank = -1

            for (rankIndex in 0 until 8) {
                val fenRank = ranks[rankIndex]
                if (fenRank.isEmpty()) {
                    throw IllegalArgumentException("Invalid FEN: empty rank at index $rankIndex")
                }
                val actualRank = 7 - rankIndex
                var file = 0
                var prevWasDigit = false
                for (char in fenRank) {
                    if (char in '1'..'8') {
                        if (prevWasDigit) {
                            throw IllegalArgumentException(
                                "Invalid FEN: consecutive digits in rank '${fenRank}' are not allowed"
                            )
                        }
                        file += char - '0'
                        prevWasDigit = true
                    } else {
                        prevWasDigit = false
                        val isWhite = char.isUpperCase()
                        val type = when (char.lowercaseChar()) {
                            'p' -> PieceType.PAWN
                            'n' -> PieceType.KNIGHT
                            'b' -> PieceType.BISHOP
                            'r' -> PieceType.ROOK
                            'q' -> PieceType.QUEEN
                            'k' -> PieceType.KING
                            else -> throw IllegalArgumentException("Invalid FEN: unknown piece character '$char'")
                        }
                        if (file !in 0..7) {
                            throw IllegalArgumentException("Invalid FEN: rank ${actualRank + 1} exceeds 8 files")
                        }
                        if (type == PieceType.PAWN && (actualRank == 0 || actualRank == 7)) {
                            throw IllegalArgumentException("Invalid FEN: pawn cannot be on rank ${actualRank + 1}")
                        }
                        if (type == PieceType.KING) {
                            if (isWhite) {
                                whiteKings++
                                whiteKingFile = file
                                whiteKingRank = actualRank
                            } else {
                                blackKings++
                                blackKingFile = file
                                blackKingRank = actualRank
                            }
                        }
                        board[actualRank][file] = ChessPiece(type, isWhite)
                        file++
                    }
                }
                if (file != 8) {
                    throw IllegalArgumentException("Invalid FEN: rank ${actualRank + 1} has $file squares instead of 8")
                }
            }

            if (whiteKings != 1 || blackKings != 1) {
                throw IllegalArgumentException("Invalid FEN: must have exactly 1 White King and 1 Black King (found w=$whiteKings, b=$blackKings)")
            }

            // Kings cannot occupy adjacent squares
            if (kotlin.math.abs(whiteKingFile - blackKingFile) <= 1 &&
                kotlin.math.abs(whiteKingRank - blackKingRank) <= 1
            ) {
                throw IllegalArgumentException("Invalid FEN: White King and Black King cannot be on adjacent squares")
            }

            val colorField = parts[1]
            if (colorField != "w" && colorField != "b") {
                throw IllegalArgumentException("Invalid FEN: active color must be 'w' or 'b', got '$colorField'")
            }
            val activeColor = colorField[0]

            val castling = parts[2]
            if (castling != "-") {
                val validChars = setOf('K', 'Q', 'k', 'q')
                if (castling.isEmpty() || castling.length > 4 ||
                    castling.any { it !in validChars } ||
                    castling.toSet().size != castling.length
                ) {
                    throw IllegalArgumentException("Invalid FEN: malformed castling field '$castling'")
                }
                // Validate castling rights against actual King and Rook placement
                if ('K' in castling) {
                    val wk = board[0][4]
                    val wr = board[0][7]
                    if (wk?.type != PieceType.KING || !wk.isWhite || wr?.type != PieceType.ROOK || !wr.isWhite) {
                        throw IllegalArgumentException("Invalid FEN: castling right 'K' requires White King on e1 and White Rook on h1")
                    }
                }
                if ('Q' in castling) {
                    val wk = board[0][4]
                    val wr = board[0][0]
                    if (wk?.type != PieceType.KING || !wk.isWhite || wr?.type != PieceType.ROOK || !wr.isWhite) {
                        throw IllegalArgumentException("Invalid FEN: castling right 'Q' requires White King on e1 and White Rook on a1")
                    }
                }
                if ('k' in castling) {
                    val bk = board[7][4]
                    val br = board[7][7]
                    if (bk?.type != PieceType.KING || bk.isWhite || br?.type != PieceType.ROOK || br.isWhite) {
                        throw IllegalArgumentException("Invalid FEN: castling right 'k' requires Black King on e8 and Black Rook on h8")
                    }
                }
                if ('q' in castling) {
                    val bk = board[7][4]
                    val br = board[7][0]
                    if (bk?.type != PieceType.KING || bk.isWhite || br?.type != PieceType.ROOK || br.isWhite) {
                        throw IllegalArgumentException("Invalid FEN: castling right 'q' requires Black King on e8 and Black Rook on a8")
                    }
                }
            }

            val enPassant = parts[3]
            if (enPassant != "-") {
                if (enPassant.length != 2 ||
                    enPassant[0] !in 'a'..'h' ||
                    (enPassant[1] != '3' && enPassant[1] != '6')
                ) {
                    throw IllegalArgumentException("Invalid FEN: malformed en passant target '$enPassant'")
                }
                val epFile = enPassant[0] - 'a'
                val epRankChar = enPassant[1]
                if (activeColor == 'w') {
                    if (epRankChar != '6') {
                        throw IllegalArgumentException("Invalid FEN: en passant rank must be '6' when White is to move, got '$enPassant'")
                    }
                    val blackPawn = board[4][epFile]
                    if (blackPawn?.type != PieceType.PAWN || blackPawn.isWhite ||
                        board[5][epFile] != null || board[6][epFile] != null
                    ) {
                        throw IllegalArgumentException("Invalid FEN: en passant target '$enPassant' requires a Black pawn on ${enPassant[0]}5 and empty ${enPassant[0]}6/${enPassant[0]}7")
                    }
                } else {
                    if (epRankChar != '3') {
                        throw IllegalArgumentException("Invalid FEN: en passant rank must be '3' when Black is to move, got '$enPassant'")
                    }
                    val whitePawn = board[3][epFile]
                    if (whitePawn?.type != PieceType.PAWN || !whitePawn.isWhite ||
                        board[2][epFile] != null || board[1][epFile] != null
                    ) {
                        throw IllegalArgumentException("Invalid FEN: en passant target '$enPassant' requires a White pawn on ${enPassant[0]}4 and empty ${enPassant[0]}3/${enPassant[0]}2")
                    }
                }
            }

            val halfMove = parts[4].toIntOrNull()?.takeIf { it >= 0 }
                ?: throw IllegalArgumentException("Invalid FEN: halfmove clock must be non-negative integer, got '${parts[4]}'")

            val fullMove = parts[5].toIntOrNull()?.takeIf { it >= 1 }
                ?: throw IllegalArgumentException("Invalid FEN: fullmove number must be positive integer, got '${parts[5]}'")

            // A position where the king of the side NOT to move is already in check is illegal in chess
            val inactiveColor = if (activeColor == 'w') 'b' else 'w'
            if (isColorInCheck(inactiveColor, board)) {
                throw IllegalArgumentException(
                    "Invalid FEN: king of the side not to move ('$inactiveColor') cannot be in check"
                )
            }

            return ChessBoardState(board, activeColor, castling, enPassant, halfMove, fullMove)
        }

        fun stateToFen(pos: ChessBoardState): String {
            val sb = StringBuilder()
            for (rank in 7 downTo 0) {
                var emptyCount = 0
                for (file in 0..7) {
                    val piece = pos.board[rank][file]
                    if (piece == null) {
                        emptyCount++
                    } else {
                        if (emptyCount > 0) {
                            sb.append(emptyCount)
                            emptyCount = 0
                        }
                        val char = when (piece.type) {
                            PieceType.PAWN -> 'p'
                            PieceType.KNIGHT -> 'n'
                            PieceType.BISHOP -> 'b'
                            PieceType.ROOK -> 'r'
                            PieceType.QUEEN -> 'q'
                            PieceType.KING -> 'k'
                        }
                        sb.append(if (piece.isWhite) char.uppercaseChar() else char)
                    }
                }
                if (emptyCount > 0) {
                    sb.append(emptyCount)
                }
                if (rank > 0) {
                    sb.append('/')
                }
            }

            sb.append(" ").append(pos.activeColor)
            sb.append(" ").append(pos.castling)
            sb.append(" ").append(pos.enPassant)
            sb.append(" ").append(pos.halfMove)
            sb.append(" ").append(pos.fullMove)

            return sb.toString()
        }

        fun isSquareAttacked(targetFile: Int, targetRank: Int, byColor: Char, board: Array<Array<ChessPiece?>>): Boolean {
            val isAttackerWhite = byColor == 'w'

            // 1. Pawn attacks
            val pawnPusherRank = if (isAttackerWhite) targetRank - 1 else targetRank + 1
            if (pawnPusherRank in 0..7) {
                val leftFile = targetFile - 1
                if (leftFile >= 0) {
                    val p = board[pawnPusherRank][leftFile]
                    if (p != null && p.isWhite == isAttackerWhite && p.type == PieceType.PAWN) return true
                }
                val rightFile = targetFile + 1
                if (rightFile <= 7) {
                    val p = board[pawnPusherRank][rightFile]
                    if (p != null && p.isWhite == isAttackerWhite && p.type == PieceType.PAWN) return true
                }
            }

            // 2. Knight attacks
            val knightOffsets = arrayOf(
                intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
                intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1)
            )
            for (offset in knightOffsets) {
                val f = targetFile + offset[0]
                val r = targetRank + offset[1]
                if (f in 0..7 && r in 0..7) {
                    val p = board[r][f]
                    if (p != null && p.isWhite == isAttackerWhite && p.type == PieceType.KNIGHT) {
                        return true
                    }
                }
            }

            // 3. Bishop & Queen diagonal attacks
            val diagonalDirections = arrayOf(
                intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1)
            )
            for (dir in diagonalDirections) {
                var f = targetFile + dir[0]
                var r = targetRank + dir[1]
                while (f in 0..7 && r in 0..7) {
                    val p = board[r][f]
                    if (p != null) {
                        if (p.isWhite == isAttackerWhite && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                            return true
                        }
                        break
                    }
                    f += dir[0]
                    r += dir[1]
                }
            }

            // 4. Rook & Queen orthogonal attacks
            val orthogonalDirections = arrayOf(
                intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
            )
            for (dir in orthogonalDirections) {
                var f = targetFile + dir[0]
                var r = targetRank + dir[1]
                while (f in 0..7 && r in 0..7) {
                    val p = board[r][f]
                    if (p != null) {
                        if (p.isWhite == isAttackerWhite && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                            return true
                        }
                        break
                    }
                    f += dir[0]
                    r += dir[1]
                }
            }

            // 5. King adjacent attacks
            for (df in -1..1) {
                for (dr in -1..1) {
                    if (df == 0 && dr == 0) continue
                    val f = targetFile + df
                    val r = targetRank + dr
                    if (f in 0..7 && r in 0..7) {
                        val p = board[r][f]
                        if (p != null && p.isWhite == isAttackerWhite && p.type == PieceType.KING) {
                            return true
                        }
                    }
                }
            }

            return false
        }

        fun isColorInCheck(color: Char, board: Array<Array<ChessPiece?>>): Boolean {
            val isWhite = color == 'w'
            val opponentColor = if (isWhite) 'b' else 'w'
            var kingFile = -1
            var kingRank = -1

            for (r in 0..7) {
                for (f in 0..7) {
                    val p = board[r][f]
                    if (p != null && p.isWhite == isWhite && p.type == PieceType.KING) {
                        kingFile = f
                        kingRank = r
                        break
                    }
                }
                if (kingFile != -1) break
            }

            if (kingFile == -1) return false
            return isSquareAttacked(kingFile, kingRank, opponentColor, board)
        }
    }

    // ==========================================
    // Attack & King Safety Detection
    // ==========================================

    fun isSquareAttacked(targetFile: Int, targetRank: Int, byColor: Char, board: Array<Array<ChessPiece?>>): Boolean =
        Companion.isSquareAttacked(targetFile, targetRank, byColor, board)

    fun isColorInCheck(color: Char, board: Array<Array<ChessPiece?>>): Boolean =
        Companion.isColorInCheck(color, board)

    fun isInCheck(): Boolean = isColorInCheck(state.activeColor, state.board)

    // ==========================================
    // Legal Move Generation
    // ==========================================

    fun generateLegalMoves(forState: ChessBoardState = state): List<Move> {
        val pseudoMoves = generatePseudoLegalMoves(forState)
        val legalMoves = ArrayList<Move>(pseudoMoves.size)
        val activeColor = forState.activeColor

        for (move in pseudoMoves) {
            val simulatedBoard = forState.copyBoard()
            val piece = simulatedBoard[move.fromRank][move.fromFile] ?: continue
            val isEnPassantCapture = piece.type == PieceType.PAWN &&
                    move.fromFile != move.toFile &&
                    forState.board[move.toRank][move.toFile] == null

            simulatedBoard[move.fromRank][move.fromFile] = null
            simulatedBoard[move.toRank][move.toFile] = if (move.promotion != null) {
                ChessPiece(move.promotion, piece.isWhite)
            } else {
                piece
            }

            if (isEnPassantCapture) {
                val capturedPawnRank = if (piece.isWhite) move.toRank - 1 else move.toRank + 1
                simulatedBoard[capturedPawnRank][move.toFile] = null
            }

            if (piece.type == PieceType.KING && kotlin.math.abs(move.toFile - move.fromFile) == 2) {
                if (move.toFile == 6) {
                    val rook = simulatedBoard[move.fromRank][7]
                    simulatedBoard[move.fromRank][7] = null
                    simulatedBoard[move.fromRank][5] = rook
                } else if (move.toFile == 2) {
                    val rook = simulatedBoard[move.fromRank][0]
                    simulatedBoard[move.fromRank][0] = null
                    simulatedBoard[move.fromRank][3] = rook
                }
            }

            if (!isColorInCheck(activeColor, simulatedBoard)) {
                legalMoves.add(move)
            }
        }

        return legalMoves
    }

    fun generateLegalMovesForSquare(file: Int, rank: Int, forState: ChessBoardState = state): List<Move> {
        return generateLegalMoves(forState).filter { it.fromFile == file && it.fromRank == rank }
    }

    private fun generatePseudoLegalMoves(pos: ChessBoardState): List<Move> {
        val moves = ArrayList<Move>(48)
        val isWhite = pos.activeColor == 'w'

        for (r in 0..7) {
            for (f in 0..7) {
                val piece = pos.board[r][f] ?: continue
                if (piece.isWhite != isWhite) continue

                when (piece.type) {
                    PieceType.PAWN -> generatePawnMoves(f, r, isWhite, pos, moves)
                    PieceType.KNIGHT -> generateKnightMoves(f, r, isWhite, pos, moves)
                    PieceType.BISHOP -> generateSlidingMoves(f, r, isWhite, pos, BISHOP_DIRS, moves)
                    PieceType.ROOK -> generateSlidingMoves(f, r, isWhite, pos, ROOK_DIRS, moves)
                    PieceType.QUEEN -> generateSlidingMoves(f, r, isWhite, pos, QUEEN_DIRS, moves)
                    PieceType.KING -> generateKingMoves(f, r, isWhite, pos, moves)
                }
            }
        }
        return moves
    }

    private val BISHOP_DIRS = arrayOf(
        intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1)
    )
    private val ROOK_DIRS = arrayOf(
        intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
    )
    private val QUEEN_DIRS = arrayOf(
        intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1),
        intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1)
    )
    private val PROMOTION_PIECES = arrayOf(
        PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
    )

    private fun generatePawnMoves(file: Int, rank: Int, isWhite: Boolean, pos: ChessBoardState, moves: MutableList<Move>) {
        val forwardDir = if (isWhite) 1 else -1
        val startRank = if (isWhite) 1 else 6
        val promoRank = if (isWhite) 7 else 0

        val nextRank = rank + forwardDir
        if (nextRank in 0..7 && pos.board[nextRank][file] == null) {
            if (nextRank == promoRank) {
                for (promo in PROMOTION_PIECES) {
                    moves.add(Move(file, rank, file, nextRank, promo))
                }
            } else {
                moves.add(Move(file, rank, file, nextRank))
                val doubleRank = rank + 2 * forwardDir
                if (rank == startRank && pos.board[doubleRank][file] == null) {
                    moves.add(Move(file, rank, file, doubleRank))
                }
            }
        }

        // Captures (standard + en passant)
        val enPassantNotation = pos.enPassant
        val epFile = if (enPassantNotation.length == 2) enPassantNotation[0] - 'a' else -1
        val epRank = if (enPassantNotation.length == 2) enPassantNotation[1] - '1' else -1

        for (df in intArrayOf(-1, 1)) {
            val targetFile = file + df
            if (targetFile in 0..7 && nextRank in 0..7) {
                val targetPiece = pos.board[nextRank][targetFile]
                val isEnPassant = (targetFile == epFile && nextRank == epRank) &&
                        pos.board[rank][targetFile]?.let { it.type == PieceType.PAWN && it.isWhite != isWhite } == true

                if ((targetPiece != null && targetPiece.isWhite != isWhite) || isEnPassant) {
                    if (nextRank == promoRank) {
                        for (promo in PROMOTION_PIECES) {
                            moves.add(Move(file, rank, targetFile, nextRank, promo))
                        }
                    } else {
                        moves.add(Move(file, rank, targetFile, nextRank))
                    }
                }
            }
        }
    }

    private fun generateKnightMoves(file: Int, rank: Int, isWhite: Boolean, pos: ChessBoardState, moves: MutableList<Move>) {
        val offsets = arrayOf(
            intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
            intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1)
        )
        for (offset in offsets) {
            val f = file + offset[0]
            val r = rank + offset[1]
            if (f in 0..7 && r in 0..7) {
                val p = pos.board[r][f]
                if (p == null || p.isWhite != isWhite) {
                    moves.add(Move(file, rank, f, r))
                }
            }
        }
    }

    private fun generateSlidingMoves(
        file: Int,
        rank: Int,
        isWhite: Boolean,
        pos: ChessBoardState,
        directions: Array<IntArray>,
        moves: MutableList<Move>
    ) {
        for (dir in directions) {
            var f = file + dir[0]
            var r = rank + dir[1]
            while (f in 0..7 && r in 0..7) {
                val p = pos.board[r][f]
                if (p == null) {
                    moves.add(Move(file, rank, f, r))
                } else {
                    if (p.isWhite != isWhite) {
                        moves.add(Move(file, rank, f, r))
                    }
                    break
                }
                f += dir[0]
                r += dir[1]
            }
        }
    }

    private fun generateKingMoves(file: Int, rank: Int, isWhite: Boolean, pos: ChessBoardState, moves: MutableList<Move>) {
        for (df in -1..1) {
            for (dr in -1..1) {
                if (df == 0 && dr == 0) continue
                val f = file + df
                val r = rank + dr
                if (f in 0..7 && r in 0..7) {
                    val p = pos.board[r][f]
                    if (p == null || p.isWhite != isWhite) {
                        moves.add(Move(file, rank, f, r))
                    }
                }
            }
        }

        // Castling: strictly verify castling flag, presence & color of Rook on home square, empty path, and unattacked King path
        val opponentColor = if (isWhite) 'b' else 'w'
        val homeRank = if (isWhite) 0 else 7
        if (rank == homeRank && file == 4 && !isSquareAttacked(4, homeRank, opponentColor, pos.board)) {
            // Kingside castling (e1->g1 / e8->g8, Rook at h1/h8)
            val canCastleKing = if (isWhite) pos.castling.contains("K") else pos.castling.contains("k")
            val kingsideRook = pos.board[homeRank][7]
            if (canCastleKing &&
                kingsideRook != null && kingsideRook.type == PieceType.ROOK && kingsideRook.isWhite == isWhite &&
                pos.board[homeRank][5] == null && pos.board[homeRank][6] == null
            ) {
                if (!isSquareAttacked(5, homeRank, opponentColor, pos.board) &&
                    !isSquareAttacked(6, homeRank, opponentColor, pos.board)
                ) {
                    moves.add(Move(file, rank, 6, homeRank))
                }
            }

            // Queenside castling (e1->c1 / e8->c8, Rook at a1/a8)
            val canCastleQueen = if (isWhite) pos.castling.contains("Q") else pos.castling.contains("q")
            val queensideRook = pos.board[homeRank][0]
            if (canCastleQueen &&
                queensideRook != null && queensideRook.type == PieceType.ROOK && queensideRook.isWhite == isWhite &&
                pos.board[homeRank][3] == null && pos.board[homeRank][2] == null && pos.board[homeRank][1] == null
            ) {
                if (!isSquareAttacked(3, homeRank, opponentColor, pos.board) &&
                    !isSquareAttacked(2, homeRank, opponentColor, pos.board)
                ) {
                    moves.add(Move(file, rank, 2, homeRank))
                }
            }
        }
    }

    // ==========================================
    // Move Execution & Game Over Evaluation
    // ==========================================

    fun isCheckmate(): Boolean {
        val legalMoves = generateLegalMoves(state)
        return legalMoves.isEmpty() && isInCheck()
    }

    fun isStalemate(): Boolean {
        val legalMoves = generateLegalMoves(state)
        return legalMoves.isEmpty() && !isInCheck()
    }

    /**
     * Executes a move if and only if it is strictly legal (including exact promotion match).
     */
    fun makeMove(move: Move): MoveResult? {
        val legalMoves = generateLegalMoves(state)
        val matchedLegalMove = legalMoves.firstOrNull {
            it.fromFile == move.fromFile &&
                    it.fromRank == move.fromRank &&
                    it.toFile == move.toFile &&
                    it.toRank == move.toRank &&
                    it.promotion == move.promotion
        } ?: return null

        val movingPiece = state.board[matchedLegalMove.fromRank][matchedLegalMove.fromFile] ?: return null
        val targetPiece = state.board[matchedLegalMove.toRank][matchedLegalMove.toFile]
        val isEnPassantCapture = movingPiece.type == PieceType.PAWN &&
                matchedLegalMove.fromFile != matchedLegalMove.toFile &&
                targetPiece == null
        val isCapture = targetPiece != null || isEnPassantCapture

        val newState = applyLegalMoveToState(state, matchedLegalMove, movingPiece, isEnPassantCapture, isCapture)

        val nextInCheck = isColorInCheck(newState.activeColor, newState.board)
        val opponentLegalMoves = generateLegalMoves(newState)
        val isMate = nextInCheck && opponentLegalMoves.isEmpty()
        val isStale = !nextInCheck && opponentLegalMoves.isEmpty()

        val san = buildSan(state, legalMoves, movingPiece, matchedLegalMove, isCapture, isMate, nextInCheck)

        state = newState
        return MoveResult(
            move = matchedLegalMove,
            san = san,
            newPosition = newState,
            isCheck = nextInCheck,
            isCheckmate = isMate,
            isStalemate = isStale
        )
    }

    private fun applyLegalMoveToState(
        currentState: ChessBoardState,
        move: Move,
        movingPiece: ChessPiece,
        isEnPassantCapture: Boolean,
        isCapture: Boolean
    ): ChessBoardState {
        val newBoard = currentState.copyBoard()
        newBoard[move.fromRank][move.fromFile] = null
        newBoard[move.toRank][move.toFile] = if (move.promotion != null) {
            ChessPiece(move.promotion, movingPiece.isWhite)
        } else {
            movingPiece
        }

        // Remove captured pawn on en passant
        if (isEnPassantCapture) {
            val capturedPawnRank = if (movingPiece.isWhite) move.toRank - 1 else move.toRank + 1
            newBoard[capturedPawnRank][move.toFile] = null
        }

        // Move rook on castling
        if (movingPiece.type == PieceType.KING && kotlin.math.abs(move.toFile - move.fromFile) == 2) {
            if (move.toFile == 6) {
                val rook = newBoard[move.fromRank][7]
                newBoard[move.fromRank][7] = null
                newBoard[move.fromRank][5] = rook
            } else if (move.toFile == 2) {
                val rook = newBoard[move.fromRank][0]
                newBoard[move.fromRank][0] = null
                newBoard[move.fromRank][3] = rook
            }
        }

        val nextColor = if (currentState.activeColor == 'w') 'b' else 'w'
        val nextFullMove = if (currentState.activeColor == 'b') currentState.fullMove + 1 else currentState.fullMove

        val newEnPassant = if (movingPiece.type == PieceType.PAWN && kotlin.math.abs(move.toRank - move.fromRank) == 2) {
            "${('a' + move.fromFile)}${(move.fromRank + move.toRank) / 2 + 1}"
        } else {
            "-"
        }

        return ChessBoardState(
            board = newBoard,
            activeColor = nextColor,
            castling = updateCastlingRights(currentState.castling, move, movingPiece),
            enPassant = newEnPassant,
            halfMove = if (movingPiece.type == PieceType.PAWN || isCapture) 0 else currentState.halfMove + 1,
            fullMove = nextFullMove
        )
    }

    /**
     * Updates castling rights when:
     * 1. A king moves
     * 2. A rook moves from its home corner (a1, h1, a8, h8)
     * 3. Any piece captures/lands on a rook's home corner (a1, h1, a8, h8)
     */
    private fun updateCastlingRights(current: String, move: Move, piece: ChessPiece): String {
        if (current == "-") return "-"
        var c = current
        if (piece.type == PieceType.KING) {
            c = if (piece.isWhite) c.replace("K", "").replace("Q", "") else c.replace("k", "").replace("q", "")
        }
        if ((move.fromFile == 0 && move.fromRank == 0) || (move.toFile == 0 && move.toRank == 0)) {
            c = c.replace("Q", "")
        }
        if ((move.fromFile == 7 && move.fromRank == 0) || (move.toFile == 7 && move.toRank == 0)) {
            c = c.replace("K", "")
        }
        if ((move.fromFile == 0 && move.fromRank == 7) || (move.toFile == 0 && move.toRank == 7)) {
            c = c.replace("q", "")
        }
        if ((move.fromFile == 7 && move.fromRank == 7) || (move.toFile == 7 && move.toRank == 7)) {
            c = c.replace("k", "")
        }
        return if (c.isEmpty()) "-" else c
    }

    /**
     * Builds Standard Algebraic Notation (SAN) including:
     * - En passant captures (e.g. exd6)
     * - Disambiguation of identical piece types by file, rank, or both (e.g. Rad1, N1f3, Qh4e1)
     * - Pawn promotions (e.g. e8=Q, exd8=N+)
     * - Check (+) and Checkmate (#)
     */
    private fun buildSan(
        priorState: ChessBoardState,
        legalMoves: List<Move>,
        piece: ChessPiece,
        move: Move,
        isCapture: Boolean,
        isCheckmate: Boolean,
        isCheck: Boolean
    ): String {
        val sb = StringBuilder()
        when (piece.type) {
            PieceType.PAWN -> {
                if (isCapture) {
                    sb.append(('a' + move.fromFile)).append('x')
                }
                sb.append(move.toNotation)
                if (move.promotion != null) {
                    sb.append("=").append(
                        when (move.promotion) {
                            PieceType.QUEEN -> "Q"
                            PieceType.ROOK -> "R"
                            PieceType.BISHOP -> "B"
                            PieceType.KNIGHT -> "N"
                            else -> "Q"
                        }
                    )
                }
            }
            PieceType.KING -> {
                if (kotlin.math.abs(move.toFile - move.fromFile) == 2) {
                    if (move.toFile == 6) sb.append("O-O") else sb.append("O-O-O")
                } else {
                    sb.append("K").append(if (isCapture) "x" else "").append(move.toNotation)
                }
            }
            else -> {
                val pChar = when (piece.type) {
                    PieceType.KNIGHT -> "N"
                    PieceType.BISHOP -> "B"
                    PieceType.ROOK -> "R"
                    PieceType.QUEEN -> "Q"
                    else -> ""
                }
                sb.append(pChar)

                // Disambiguation: check if other pieces of the same type can legally reach the same destination
                val ambiguousMoves = legalMoves.filter {
                    it.toFile == move.toFile &&
                            it.toRank == move.toRank &&
                            !(it.fromFile == move.fromFile && it.fromRank == move.fromRank) &&
                            priorState.board[it.fromRank][it.fromFile]?.type == piece.type
                }
                if (ambiguousMoves.isNotEmpty()) {
                    val sameFile = ambiguousMoves.any { it.fromFile == move.fromFile }
                    val sameRank = ambiguousMoves.any { it.fromRank == move.fromRank }
                    when {
                        !sameFile -> sb.append(('a' + move.fromFile))
                        !sameRank -> sb.append(move.fromRank + 1)
                        else -> sb.append(('a' + move.fromFile)).append(move.fromRank + 1)
                    }
                }

                if (isCapture) {
                    sb.append("x")
                }
                sb.append(move.toNotation)
            }
        }

        if (isCheckmate) {
            sb.append("#")
        } else if (isCheck) {
            sb.append("+")
        }
        return sb.toString()
    }

    // ==========================================
    // Perft (Performance & Move Tree Verification)
    // ==========================================

    fun perft(depth: Int, forState: ChessBoardState = state): Long {
        if (depth <= 0) return 1L
        val moves = generateLegalMoves(forState)
        if (depth == 1) return moves.size.toLong()

        var nodes = 0L
        for (move in moves) {
            val movingPiece = forState.board[move.fromRank][move.fromFile] ?: continue
            val targetPiece = forState.board[move.toRank][move.toFile]
            val isEnPassantCapture = movingPiece.type == PieceType.PAWN &&
                    move.fromFile != move.toFile &&
                    targetPiece == null
            val isCapture = targetPiece != null || isEnPassantCapture
            val nextState = applyLegalMoveToState(forState, move, movingPiece, isEnPassantCapture, isCapture)
            nodes += perft(depth - 1, nextState)
        }
        return nodes
    }
}
