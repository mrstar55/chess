package com.example.chess

import com.example.model.ChessPiece
import com.example.model.PieceType

/**
 * Adapter interface in the chess layer that decouples the UI and ViewModel
 * from the concrete chess rules engine implementation.
 */
interface ChessRulesAdapter {
    val currentFen: String
    val activeColor: Char
    fun resetToFen(fen: String)
    fun getPiece(file: Int, rank: Int): ChessPiece?
    fun getPieceAt(notation: String): ChessPiece?
    fun getLegalMoves(): List<Move>
    fun getLegalMovesForSquare(file: Int, rank: Int): List<Move>
    fun executeMove(move: Move): MoveResult?
    fun isInCheck(): Boolean
    fun isCheckmate(): Boolean
    fun isStalemate(): Boolean
    fun perft(depth: Int): Long
}

/**
 * Default adapter backed by the verified Kotlin [ChessEngine].
 */
class KotlinChessRulesAdapter(
    initialFen: String = ChessEngine.STANDARD_START_FEN
) : ChessRulesAdapter {

    private val engine = ChessEngine(initialFen)

    override val currentFen: String
        get() = engine.toFen()

    override val activeColor: Char
        get() = engine.state.activeColor

    override fun resetToFen(fen: String) {
        engine.setFen(fen)
    }

    override fun getPiece(file: Int, rank: Int): ChessPiece? {
        return engine.state.getPiece(file, rank)
    }

    override fun getPieceAt(notation: String): ChessPiece? {
        return engine.state.getPieceAt(notation)
    }

    override fun getLegalMoves(): List<Move> {
        return engine.generateLegalMoves()
    }

    override fun getLegalMovesForSquare(file: Int, rank: Int): List<Move> {
        return engine.generateLegalMovesForSquare(file, rank)
    }

    override fun executeMove(move: Move): MoveResult? {
        return engine.makeMove(move)
    }

    override fun isInCheck(): Boolean {
        return engine.isInCheck()
    }

    override fun isCheckmate(): Boolean {
        return engine.isCheckmate()
    }

    override fun isStalemate(): Boolean {
        return engine.isStalemate()
    }

    override fun perft(depth: Int): Long {
        return engine.perft(depth)
    }
}
