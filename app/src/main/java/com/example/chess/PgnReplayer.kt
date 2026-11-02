package com.example.chess

/**
 * Represents a Precomputed Replay sequence of chess moves and their corresponding FEN states.
 * Note: This sequence uses precomputed FEN steps generated during build/audit time and
 * verified step-by-step by [ChessEngine], rather than parsing raw PGN text at runtime.
 */
data class PgnSequence(
    val id: String,
    val description: String,
    val moves: List<String>,
    val fens: List<String>
) {
    val totalSteps: Int get() = moves.size

    init {
        require(fens.size == moves.size + 1) {
            "Precomputed PgnSequence '$id' invalid: fens.size (${fens.size}) must equal moves.size + 1 (${moves.size + 1})"
        }
    }

    /**
     * Verifies all precomputed replay invariants using [ChessEngine]:
     * 1. fens.size == moves.size + 1
     * 2. Initial FEN matches expectedStartFen (if provided)
     * 3. Each step i -> i+1 corresponds to a legal move in [ChessEngine] whose SAN matches moves[i]
     *    and whose resulting board state matches fens[i+1]
     * 4. Final FEN matches expectedFinalFen (if provided)
     */
    fun validateIntegrity(
        expectedStartFen: String? = null,
        expectedFinalFen: String? = null
    ): Boolean {
        if (fens.size != moves.size + 1) return false
        if (expectedStartFen != null && fens.first() != expectedStartFen) return false
        if (expectedFinalFen != null && fens.last() != expectedFinalFen) return false

        for (i in moves.indices) {
            val expectedSan = moves[i]
            val startStateFen = fens[i]
            val expectedNextState = ChessEngine.parseFen(fens[i + 1])

            val stepEngine = ChessEngine(startStateFen)
            val legalMoves = stepEngine.generateLegalMoves()

            var matchedMoveResult: MoveResult? = null
            for (candidate in legalMoves) {
                val simEngine = ChessEngine(startStateFen)
                val res = simEngine.makeMove(candidate)
                if (res != null && res.san == expectedSan) {
                    matchedMoveResult = res
                    break
                }
            }

            if (matchedMoveResult == null) return false

            val actualNext = matchedMoveResult.newPosition
            // Verify piece placement, activeColor, castling, enPassant, halfMove, and fullMove match fens[i+1]
            for (r in 0..7) {
                for (f in 0..7) {
                    if (actualNext.board[r][f] != expectedNextState.board[r][f]) return false
                }
            }
            if (actualNext.activeColor != expectedNextState.activeColor) return false
            if (actualNext.castling != expectedNextState.castling) return false
            if (actualNext.enPassant != expectedNextState.enPassant) return false
            if (actualNext.halfMove != expectedNextState.halfMove) return false
            if (actualNext.fullMove != expectedNextState.fullMove) return false
        }

        return true
    }
}

/**
 * Step-by-step controller for a precomputed replay sequence ([PgnSequence]).
 */
class PgnReplayer(val sequence: PgnSequence) {
    var currentStep: Int = 0
        private set

    val currentFen: String
        get() = sequence.fens.getOrElse(currentStep) { sequence.fens.last() }

    val currentMoveSan: String?
        get() = if (currentStep > 0 && currentStep <= sequence.moves.size) {
            sequence.moves[currentStep - 1]
        } else null

    val isAtStart: Boolean get() = currentStep <= 0
    val isAtEnd: Boolean get() = currentStep >= sequence.totalSteps

    fun next(): Boolean {
        if (!isAtEnd) {
            currentStep++
            return true
        }
        return false
    }

    fun previous(): Boolean {
        if (!isAtStart) {
            currentStep--
            return true
        }
        return false
    }

    fun reset() {
        currentStep = 0
    }

    fun jumpToEnd() {
        currentStep = sequence.totalSteps
    }

    fun jumpTo(step: Int) {
        currentStep = step.coerceIn(0, sequence.totalSteps)
    }
}
