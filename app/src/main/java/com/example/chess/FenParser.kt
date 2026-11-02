package com.example.chess

import com.example.model.ChessPiece

/**
 * Backward-compatible FenParser backed by the real ChessEngine.
 */
object FenParser {

    fun parse(fen: String): ChessBoardState {
        return ChessEngine.parseFen(fen)
    }

    fun toFen(position: ChessBoardState): String {
        return ChessEngine.stateToFen(position)
    }

    /**
     * Executes a move on a state and returns the resulting board state.
     */
    fun makeMove(
        pos: ChessBoardState,
        fromFile: Int,
        fromRank: Int,
        toFile: Int,
        toRank: Int
    ): ChessBoardState {
        val engine = ChessEngine(toFen(pos))
        val result = engine.makeMove(Move(fromFile, fromRank, toFile, toRank))
        return result?.newPosition ?: pos
    }

    /**
     * Validates and applies a move using the real ChessEngine.
     * Returns Pair(newPosition, isCheckmate).
     */
    fun applyDynamicMove(
        fen: String,
        fromFile: Int,
        fromRank: Int,
        toFile: Int,
        toRank: Int
    ): Pair<ChessBoardState, MoveResult>? {
        val engine = ChessEngine(fen)
        val result = engine.makeMove(Move(fromFile, fromRank, toFile, toRank)) ?: return null
        return Pair(result.newPosition, result)
    }
}
