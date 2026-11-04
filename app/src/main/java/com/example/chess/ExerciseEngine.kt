package com.example.chess

import com.example.audio.ChessSoundManager
import com.example.audio.ChessSoundType
import com.example.data.EducationalPlaybackSpeed
import com.example.model.BoardTransitionResolver
import com.example.model.DiagramItem
import com.example.model.ExerciseDefinition
import com.example.model.ExercisePhase
import com.example.model.ExerciseUiState

data class ExerciseActionOutcome(
    val updatedState: ExerciseUiState,
    val soundToEmit: Pair<ChessSoundType, String>? = null,
    val newlySolved: Boolean = false,
    val resetSolvedPreference: Boolean = false
)

interface ExerciseHandler {
    val handlerType: String

    fun createRulesAdapter(
        definition: ExerciseDefinition,
        diagram: DiagramItem
    ): ChessRulesAdapter

    fun createInitialState(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        isAlreadySolved: Boolean
    ): ExerciseUiState

    fun onToggleLegalMovesHint(currentState: ExerciseUiState): ExerciseUiState

    fun onSquareClicked(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        pgnSequences: Map<String, PgnSequence>,
        currentState: ExerciseUiState,
        adapter: ChessRulesAdapter,
        file: Int,
        rank: Int,
        showMoveTrail: Boolean,
        playbackSpeed: EducationalPlaybackSpeed,
        nextTransitionNumber: () -> Long
    ): ExerciseActionOutcome

    fun onTransitionFinished(
        definition: ExerciseDefinition,
        currentState: ExerciseUiState,
        transitionId: String
    ): ExerciseActionOutcome

    fun onMoveTrailFinished(
        currentState: ExerciseUiState,
        eventId: String
    ): ExerciseUiState

    fun onRetry(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        adapter: ChessRulesAdapter
    ): ExerciseActionOutcome

    fun onReset(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        adapter: ChessRulesAdapter
    ): ExerciseActionOutcome
}

class MateInOneExerciseHandler : ExerciseHandler {
    override val handlerType: String = HANDLER_TYPE

    override fun createRulesAdapter(
        definition: ExerciseDefinition,
        diagram: DiagramItem
    ): ChessRulesAdapter {
        return KotlinChessRulesAdapter(diagram.initialFen)
    }

    override fun createInitialState(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        isAlreadySolved: Boolean
    ): ExerciseUiState {
        return ExerciseUiState(
            currentFen = diagram.initialFen,
            isSolved = isAlreadySolved,
            phase = if (isAlreadySolved) ExercisePhase.SOLVED else ExercisePhase.IDLE,
            activeTransition = null,
            activeMoveTrail = null,
            lastMoveDescriptionFa = null
        )
    }

    override fun onToggleLegalMovesHint(currentState: ExerciseUiState): ExerciseUiState {
        if (currentState.isBoardLocked) return currentState
        return currentState.copy(
            showLegalMovesHint = !currentState.showLegalMovesHint
        )
    }

    override fun onSquareClicked(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        pgnSequences: Map<String, PgnSequence>,
        currentState: ExerciseUiState,
        adapter: ChessRulesAdapter,
        file: Int,
        rank: Int,
        showMoveTrail: Boolean,
        playbackSpeed: EducationalPlaybackSpeed,
        nextTransitionNumber: () -> Long
    ): ExerciseActionOutcome {
        if (currentState.isBoardLocked) {
            return ExerciseActionOutcome(currentState)
        }

        val initialFen = diagram.initialFen
        if (adapter.currentFen != initialFen) {
            adapter.resetToFen(initialFen)
        }

        val clickedNotation = "${('a' + file)}${rank + 1}"
        val pieceAtClicked = adapter.getPiece(file, rank)
        val isWhiteTurn = adapter.activeColor == 'w'
        val isFriendlyPiece = pieceAtClicked != null && (pieceAtClicked.isWhite == isWhiteTurn)

        // 1. Selecting a friendly piece of the active color
        if (isFriendlyPiece) {
            val legalMoves = adapter.getLegalMovesForSquare(file, rank)
            val legalTargets = legalMoves.map { it.toNotation }
            return ExerciseActionOutcome(
                updatedState = currentState.copy(
                    currentFen = initialFen,
                    selectedSquare = clickedNotation,
                    selectedFile = file,
                    selectedRank = rank,
                    legalTargetSquares = legalTargets,
                    illegalFlashSquares = emptyList(),
                    phase = ExercisePhase.PIECE_SELECTED,
                    feedbackMessage = DEFAULT_PIECE_SELECTED_FEEDBACK_FA,
                    feedbackIsSuccess = false,
                    activeTransition = null
                )
            )
        }

        // 2. Attempting a move from the selected square
        if (currentState.selectedSquare != null && currentState.selectedFile != -1) {
            val move = Move(
                fromFile = currentState.selectedFile,
                fromRank = currentState.selectedRank,
                toFile = file,
                toRank = rank
            )
            return executeMove(
                definition = definition,
                diagram = diagram,
                pgnSequences = pgnSequences,
                currentState = currentState,
                adapter = adapter,
                move = move,
                showMoveTrail = showMoveTrail,
                playbackSpeed = playbackSpeed,
                nextTransitionNumber = nextTransitionNumber
            )
        }

        return ExerciseActionOutcome(currentState)
    }

    private fun executeMove(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        pgnSequences: Map<String, PgnSequence>,
        currentState: ExerciseUiState,
        adapter: ChessRulesAdapter,
        move: Move,
        showMoveTrail: Boolean,
        playbackSpeed: EducationalPlaybackSpeed,
        nextTransitionNumber: () -> Long
    ): ExerciseActionOutcome {
        val initialFen = diagram.initialFen
        adapter.resetToFen(initialFen)
        val moveResult = adapter.executeMove(move)
        val tagPrefix = definition.exerciseId.lowercase()

        // Case 1: Illegal chess move
        if (moveResult == null) {
            adapter.resetToFen(initialFen)
            val flashSquares = listOfNotNull(currentState.selectedSquare, move.toNotation).distinct()
            val illegalMsg = definition.illegalMoveFeedbackFa?.takeIf { it.isNotBlank() }
                ?: DEFAULT_ILLEGAL_MOVE_FEEDBACK_FA
            return ExerciseActionOutcome(
                updatedState = currentState.copy(
                    currentFen = initialFen,
                    phase = ExercisePhase.PIECE_SELECTED,
                    illegalFlashSquares = flashSquares,
                    feedbackMessage = illegalMsg,
                    feedbackIsSuccess = false,
                    activeTransition = null
                ),
                soundToEmit = ChessSoundType.ILLEGAL_MOVE to "${tagPrefix}_illegal_move"
            )
        }

        val san = moveResult.san
        val finalFen = adapter.currentFen

        // Check whether this legal move matches acceptedSolutionPgnRefs (if defined in PGN map) and delivers checkmate
        val solutionSequences = definition.acceptedSolutionPgnRefs.mapNotNull { pgnSequences[it] }
        val matchesAcceptedSolution = if (solutionSequences.isNotEmpty()) {
            solutionSequences.any { seq ->
                seq.fens.lastOrNull() == finalFen || seq.moves.firstOrNull() == san
            } && moveResult.isCheckmate
        } else {
            moveResult.isCheckmate
        }

        // Case 2: Legal move AND delivers checkmate matching exercise solutions
        if (matchesAcceptedSolution) {
            val transitionId = "${tagPrefix}_solve_${nextTransitionNumber()}"
            val transition = BoardTransitionResolver.resolveForwardTransition(
                transitionId = transitionId,
                fromFen = initialFen,
                toFen = finalFen
            )
            val moveTrail = if (transition != null && showMoveTrail) {
                BoardTransitionResolver.createMoveTrail(transition, playbackSpeed)
            } else {
                null
            }
            val moveDescFa = BoardTransitionResolver.buildPersistentMoveDescriptionFa(transition)
            val matedKingSideFa = if (initialFen.contains(" b ")) "شاه سفید" else "شاه سیاه"
            val successMsg = resolveSuccessMessageFa(definition, san, matedKingSideFa)
            return ExerciseActionOutcome(
                updatedState = currentState.copy(
                    currentFen = finalFen,
                    selectedSquare = null,
                    selectedFile = -1,
                    selectedRank = -1,
                    legalTargetSquares = emptyList(),
                    illegalFlashSquares = emptyList(),
                    isSolved = true,
                    phase = if (transition != null) ExercisePhase.ANIMATING_CORRECT else ExercisePhase.SOLVED,
                    canRetry = false,
                    feedbackMessage = successMsg,
                    feedbackIsSuccess = true,
                    solutionPlayed = san,
                    activeTransition = transition,
                    activeMoveTrail = moveTrail,
                    lastMoveDescriptionFa = moveDescFa
                ),
                soundToEmit = ChessSoundType.CHECKMATE to "${tagPrefix}_checkmate",
                newlySolved = true
            )
        }

        // Case 3: Legal chess move, but NOT checkmate in 1
        val transitionId = "${tagPrefix}_wrong_${nextTransitionNumber()}"
        val transition = BoardTransitionResolver.resolveForwardTransition(
            transitionId = transitionId,
            fromFen = initialFen,
            toFen = finalFen
        )
        val moveTrail = if (transition != null && showMoveTrail) {
            BoardTransitionResolver.createMoveTrail(transition, playbackSpeed)
        } else {
            null
        }
        val moveDescFa = BoardTransitionResolver.buildPersistentMoveDescriptionFa(transition)
        val moveSound = if (transition != null) {
            ChessSoundManager.resolveSoundForTransition(transition, preferCaptureOverCheck = true)
        } else {
            ChessSoundType.MOVE_NORMAL
        }
        val wrongMsg = definition.wrongMoveFeedbackFa?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WRONG_MOVE_FEEDBACK_FA

        return if (transition != null) {
            ExerciseActionOutcome(
                updatedState = currentState.copy(
                    currentFen = finalFen,
                    selectedSquare = null,
                    selectedFile = -1,
                    selectedRank = -1,
                    legalTargetSquares = emptyList(),
                    illegalFlashSquares = emptyList(),
                    isSolved = false,
                    phase = ExercisePhase.ANIMATING_WRONG,
                    canRetry = true,
                    feedbackMessage = null,
                    feedbackIsSuccess = false,
                    solutionPlayed = san,
                    activeTransition = transition,
                    activeMoveTrail = moveTrail,
                    lastMoveDescriptionFa = moveDescFa
                ),
                soundToEmit = moveSound to "${tagPrefix}_wrong_legal_step"
            )
        } else {
            ExerciseActionOutcome(
                updatedState = currentState.copy(
                    currentFen = finalFen,
                    selectedSquare = null,
                    selectedFile = -1,
                    selectedRank = -1,
                    legalTargetSquares = emptyList(),
                    illegalFlashSquares = emptyList(),
                    isSolved = false,
                    phase = ExercisePhase.WRONG_LEGAL_MOVE,
                    canRetry = true,
                    feedbackMessage = wrongMsg,
                    feedbackIsSuccess = false,
                    solutionPlayed = san,
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = moveDescFa
                ),
                soundToEmit = ChessSoundType.EXERCISE_WRONG to "${tagPrefix}_wrong_feedback"
            )
        }
    }

    private fun resolveSuccessMessageFa(
        definition: ExerciseDefinition,
        san: String,
        matedKingSideFa: String
    ): String {
        val raw = definition.successFeedbackFa?.takeIf { it.isNotBlank() }
        if (raw != null) {
            return if (raw.contains("{move}") || raw.contains("{san}") || raw.contains("{matedKing}")) {
                raw.replace("{move}", san)
                    .replace("{san}", san)
                    .replace("{matedKing}", matedKingSideFa)
            } else {
                raw
            }
        }
        return "پاسخ صحیح! حرکت $san توسط موتور قوانین بررسی شد و $matedKingSideFa مات شد."
    }

    override fun onTransitionFinished(
        definition: ExerciseDefinition,
        currentState: ExerciseUiState,
        transitionId: String
    ): ExerciseActionOutcome {
        if (currentState.activeTransition?.transitionId != transitionId) {
            return ExerciseActionOutcome(currentState)
        }
        val tagPrefix = definition.exerciseId.lowercase()
        val wrongMsg = definition.wrongMoveFeedbackFa?.takeIf { it.isNotBlank() }
            ?: DEFAULT_WRONG_MOVE_FEEDBACK_FA

        return when (currentState.phase) {
            ExercisePhase.ANIMATING_WRONG -> {
                ExerciseActionOutcome(
                    updatedState = currentState.copy(
                        phase = ExercisePhase.WRONG_LEGAL_MOVE,
                        canRetry = true,
                        feedbackMessage = wrongMsg,
                        feedbackIsSuccess = false,
                        activeTransition = null
                    ),
                    soundToEmit = ChessSoundType.EXERCISE_WRONG to "${tagPrefix}_wrong_feedback"
                )
            }
            ExercisePhase.ANIMATING_CORRECT -> {
                ExerciseActionOutcome(
                    updatedState = currentState.copy(
                        phase = ExercisePhase.SOLVED,
                        activeTransition = null
                    ),
                    soundToEmit = ChessSoundType.EXERCISE_CORRECT to "${tagPrefix}_correct_complete"
                )
            }
            else -> {
                ExerciseActionOutcome(
                    updatedState = currentState.copy(activeTransition = null)
                )
            }
        }
    }

    override fun onMoveTrailFinished(
        currentState: ExerciseUiState,
        eventId: String
    ): ExerciseUiState {
        if (currentState.activeMoveTrail?.eventId != eventId) return currentState
        return currentState.copy(activeMoveTrail = null)
    }

    override fun onRetry(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        adapter: ChessRulesAdapter
    ): ExerciseActionOutcome {
        val initialFen = diagram.initialFen
        adapter.resetToFen(initialFen)
        val tagPrefix = definition.exerciseId.lowercase()
        return ExerciseActionOutcome(
            updatedState = ExerciseUiState(
                currentFen = initialFen,
                selectedSquare = null,
                selectedFile = -1,
                selectedRank = -1,
                legalTargetSquares = emptyList(),
                showLegalMovesHint = false,
                illegalFlashSquares = emptyList(),
                isSolved = false,
                phase = ExercisePhase.IDLE,
                canRetry = false,
                feedbackMessage = null,
                feedbackIsSuccess = false,
                solutionPlayed = null,
                activeTransition = null,
                activeMoveTrail = null,
                lastMoveDescriptionFa = null
            ),
            soundToEmit = ChessSoundType.EXERCISE_RESET to "${tagPrefix}_retry"
        )
    }

    override fun onReset(
        definition: ExerciseDefinition,
        diagram: DiagramItem,
        adapter: ChessRulesAdapter
    ): ExerciseActionOutcome {
        val initialFen = diagram.initialFen
        adapter.resetToFen(initialFen)
        val tagPrefix = definition.exerciseId.lowercase()
        val turnSideFa = if (initialFen.contains(" w ")) "نوبت سفید است" else "نوبت سیاه است"
        return ExerciseActionOutcome(
            updatedState = ExerciseUiState(
                currentFen = initialFen,
                selectedSquare = null,
                selectedFile = -1,
                selectedRank = -1,
                legalTargetSquares = emptyList(),
                showLegalMovesHint = false,
                illegalFlashSquares = emptyList(),
                isSolved = false,
                phase = ExercisePhase.IDLE,
                canRetry = false,
                feedbackMessage = "تمرین بازنشانی شد. $turnSideFa؛ مهره خود را انتخاب کرده و خانه مقصد را لمس کنید.",
                feedbackIsSuccess = false,
                solutionPlayed = null,
                activeTransition = null,
                activeMoveTrail = null,
                lastMoveDescriptionFa = null
            ),
            soundToEmit = ChessSoundType.EXERCISE_RESET to "${tagPrefix}_reset",
            resetSolvedPreference = true
        )
    }

    companion object {
        const val HANDLER_TYPE = "MATE_IN_ONE"
        const val DEFAULT_PIECE_SELECTED_FEEDBACK_FA = "مهره انتخاب شد. اکنون خانه مقصد را روی صفحه لمس کنید."
        const val DEFAULT_ILLEGAL_MOVE_FEEDBACK_FA = "این حرکت طبق قوانین شطرنج مجاز نیست."
        const val DEFAULT_WRONG_MOVE_FEEDBACK_FA = "این حرکت قانونی است، اما مسئله را حل نمی‌کند. دوباره تلاش کنید."
    }
}

object ExerciseHandlerRegistry {
    private val handlers: Map<String, ExerciseHandler> = mapOf(
        MateInOneExerciseHandler.HANDLER_TYPE to MateInOneExerciseHandler()
    )

    fun getHandler(handlerType: String): ExerciseHandler? {
        return handlers[handlerType.trim().uppercase()]
    }

    fun isSupportedHandlerType(handlerType: String): Boolean {
        return handlers.containsKey(handlerType.trim().uppercase())
    }

    fun supportedHandlerTypes(): Set<String> = handlers.keys
}
