package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.chess.ChessRulesAdapter
import com.example.chess.KotlinChessRulesAdapter
import com.example.chess.Move
import com.example.chess.PgnReplayer
import com.example.data.ChapterRepository
import com.example.data.FontSizeScale
import com.example.data.UserPreferencesManager
import com.example.model.DiagramItem
import com.example.model.SectionItem
import com.example.model.TranslationSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Bd032UiState(
    val currentFen: String = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1",
    val selectedSquare: String? = null,
    val selectedFile: Int = -1,
    val selectedRank: Int = -1,
    val legalTargetSquares: List<String> = emptyList(),
    val isSolved: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsSuccess: Boolean = false,
    val solutionPlayed: String? = null
)

data class ChapterUiState(
    val sections: List<SectionItem> = emptyList(),
    val diagrams: List<DiagramItem> = emptyList(),
    val currentSectionIndex: Int = 0,
    val fontScale: FontSizeScale = FontSizeScale.NORMAL,
    val bookmarks: Set<String> = emptySet(),
    val pgnStates: Map<String, PgnReplayerState> = emptyMap(),
    val bd032State: Bd032UiState = Bd032UiState(),
    val currentTab: AppTab = AppTab.READER
)

enum class AppTab {
    READER,
    SECTIONS_LIST,
    AUDIT_REPORT
}

data class PgnReplayerState(
    val pgnRef: String,
    val currentStep: Int,
    val totalSteps: Int,
    val currentFen: String,
    val currentMoveSan: String?,
    val isAtStart: Boolean,
    val isAtEnd: Boolean
)

class ChapterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChapterRepository(application)
    private val prefs = UserPreferencesManager(application)

    private val replayers = mutableMapOf<String, PgnReplayer>()
    private val bd032InitialFen = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1"

    // Single consistent adapter instance for BD-032 exercise
    private val bd032Adapter: ChessRulesAdapter = KotlinChessRulesAdapter(bd032InitialFen)

    private val _uiState = MutableStateFlow(ChapterUiState())
    val uiState: StateFlow<ChapterUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val sections = repository.getSections()
        val diagrams = repository.getDiagrams()
        val savedSection = prefs.lastReadSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        val fontScale = prefs.fontScale
        val bookmarks = prefs.getBookmarks()

        val pgnStatesMap = mutableMapOf<String, PgnReplayerState>()
        diagrams.forEach { diagram ->
            diagram.pgnRef?.let { pgnRef ->
                val seq = repository.getPgnSequence(pgnRef)
                if (seq != null) {
                    val replayer = PgnReplayer(seq)
                    replayer.jumpToEnd()
                    replayers[pgnRef] = replayer
                    pgnStatesMap[pgnRef] = PgnReplayerState(
                        pgnRef = pgnRef,
                        currentStep = replayer.currentStep,
                        totalSteps = seq.totalSteps,
                        currentFen = replayer.currentFen,
                        currentMoveSan = replayer.currentMoveSan,
                        isAtStart = replayer.isAtStart,
                        isAtEnd = replayer.isAtEnd
                    )
                }
            }
        }

        bd032Adapter.resetToFen(bd032InitialFen)
        val previouslySolved = prefs.isBd032Solved()

        _uiState.update {
            it.copy(
                sections = sections,
                diagrams = diagrams,
                currentSectionIndex = savedSection,
                fontScale = fontScale,
                bookmarks = bookmarks,
                pgnStates = pgnStatesMap,
                bd032State = Bd032UiState(
                    currentFen = bd032InitialFen,
                    isSolved = previouslySolved
                )
            )
        }
    }

    fun selectSection(index: Int) {
        val target = index.coerceIn(0, _uiState.value.sections.size - 1)
        prefs.lastReadSectionIndex = target
        _uiState.update {
            it.copy(currentSectionIndex = target, currentTab = AppTab.READER)
        }
    }

    fun nextSection() {
        if (_uiState.value.currentSectionIndex < _uiState.value.sections.size - 1) {
            selectSection(_uiState.value.currentSectionIndex + 1)
        }
    }

    fun prevSection() {
        if (_uiState.value.currentSectionIndex > 0) {
            selectSection(_uiState.value.currentSectionIndex - 1)
        }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setFontScale(scale: FontSizeScale) {
        prefs.fontScale = scale
        _uiState.update { it.copy(fontScale = scale) }
    }

    fun toggleBookmark(diagramId: String) {
        prefs.toggleBookmark(diagramId)
        _uiState.update { it.copy(bookmarks = prefs.getBookmarks()) }
    }

    fun updateSectionTranslationSlot(sectionId: String, updatedSlot: TranslationSlot) {
        _uiState.update { state ->
            val updatedSections = state.sections.map { sec ->
                if (sec.sectionId == sectionId) {
                    sec.copy(translationSlot = updatedSlot)
                } else {
                    sec
                }
            }
            state.copy(sections = updatedSections)
        }
    }

    fun stepPgnNext(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        replayer.next()
        updatePgnState(pgnRef, replayer)
    }

    fun stepPgnPrev(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        replayer.previous()
        updatePgnState(pgnRef, replayer)
    }

    fun resetPgn(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        replayer.reset()
        updatePgnState(pgnRef, replayer)
    }

    fun jumpPgnToEnd(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        replayer.jumpToEnd()
        updatePgnState(pgnRef, replayer)
    }

    private fun updatePgnState(pgnRef: String, replayer: PgnReplayer) {
        _uiState.update { state ->
            val updatedMap = state.pgnStates.toMutableMap()
            updatedMap[pgnRef] = PgnReplayerState(
                pgnRef = pgnRef,
                currentStep = replayer.currentStep,
                totalSteps = replayer.sequence.totalSteps,
                currentFen = replayer.currentFen,
                currentMoveSan = replayer.currentMoveSan,
                isAtStart = replayer.isAtStart,
                isAtEnd = replayer.isAtEnd
            )
            state.copy(pgnStates = updatedMap)
        }
    }

    // ==========================================
    // Dynamic BD-032 Chess Engine Exercise Logic
    // ==========================================

    fun onBd032SquareClicked(file: Int, rank: Int) {
        val currentState = _uiState.value.bd032State

        // Disable further interaction once solved until Reset is pressed
        if (currentState.isSolved) return

        // Ensure adapter is synced with initial position before evaluating selection/move
        if (bd032Adapter.currentFen != bd032InitialFen) {
            bd032Adapter.resetToFen(bd032InitialFen)
        }

        val clickedNotation = "${('a' + file)}${rank + 1}"
        val pieceAtClicked = bd032Adapter.getPiece(file, rank)

        // 1. If clicking a friendly black piece, select it and compute its real legal moves
        if (pieceAtClicked != null && !pieceAtClicked.isWhite && bd032Adapter.activeColor == 'b') {
            val legalMoves = bd032Adapter.getLegalMovesForSquare(file, rank)
            val legalTargets = legalMoves.map { it.toNotation }
            _uiState.update {
                it.copy(
                    bd032State = currentState.copy(
                        currentFen = bd032InitialFen,
                        selectedSquare = clickedNotation,
                        selectedFile = file,
                        selectedRank = rank,
                        legalTargetSquares = legalTargets,
                        feedbackMessage = "مهره انتخاب شد. اکنون خانه مقصد را روی صفحه لمس کنید.",
                        feedbackIsSuccess = false
                    )
                )
            }
            return
        }

        // 2. If a piece is already selected, attempt to move it to the clicked destination square
        if (currentState.selectedSquare != null && currentState.selectedFile != -1) {
            val move = Move(
                fromFile = currentState.selectedFile,
                fromRank = currentState.selectedRank,
                toFile = file,
                toRank = rank
            )
            executeBd032Move(move)
        }
    }

    private fun executeBd032Move(move: Move) {
        // Always evaluate from the initial exercise position using the single adapter
        bd032Adapter.resetToFen(bd032InitialFen)
        val moveResult = bd032Adapter.executeMove(move)

        // Case 1: Illegal move -> keep board unchanged at initial position
        if (moveResult == null) {
            bd032Adapter.resetToFen(bd032InitialFen)
            _uiState.update {
                it.copy(
                    bd032State = it.bd032State.copy(
                        currentFen = bd032InitialFen,
                        selectedSquare = null,
                        selectedFile = -1,
                        selectedRank = -1,
                        legalTargetSquares = emptyList(),
                        feedbackMessage = "این حرکت طبق قوانین شطرنج غیرقانونی است. وضعیت صفحه تغییر نکرد.",
                        feedbackIsSuccess = false
                    )
                )
            }
            return
        }

        val san = moveResult.san

        // Case 2: Legal move AND delivers checkmate -> show final position, mark solved, lock board until Reset
        if (moveResult.isCheckmate) {
            val finalFen = bd032Adapter.currentFen
            prefs.setBd032Solved(true)
            _uiState.update {
                it.copy(
                    bd032State = it.bd032State.copy(
                        currentFen = finalFen,
                        selectedSquare = null,
                        selectedFile = -1,
                        selectedRank = -1,
                        legalTargetSquares = emptyList(),
                        isSolved = true,
                        feedbackMessage = "پاسخ صحیح! حرکت $san توسط موتور قوانین بررسی شد و شاه سفید مات شد.",
                        feedbackIsSuccess = true,
                        solutionPlayed = san
                    )
                )
            }
        } else {
            // Case 3: Legal move, but NOT checkmate -> revert board & engine to initial position, allow immediate retry
            bd032Adapter.resetToFen(bd032InitialFen)
            _uiState.update {
                it.copy(
                    bd032State = it.bd032State.copy(
                        currentFen = bd032InitialFen,
                        selectedSquare = null,
                        selectedFile = -1,
                        selectedRank = -1,
                        legalTargetSquares = emptyList(),
                        isSolved = false,
                        feedbackMessage = "حرکت $san قانونی است اما مات در یک حرکت نیست. صفحه به حالت اول بازگشت؛ حرکت دیگری را امتحان کنید.",
                        feedbackIsSuccess = false,
                        solutionPlayed = san
                    )
                )
            }
        }
    }

    fun resetBd032() {
        prefs.setBd032Solved(false)
        bd032Adapter.resetToFen(bd032InitialFen)
        _uiState.update {
            it.copy(
                bd032State = Bd032UiState(
                    currentFen = bd032InitialFen,
                    selectedSquare = null,
                    selectedFile = -1,
                    selectedRank = -1,
                    legalTargetSquares = emptyList(),
                    isSolved = false,
                    feedbackMessage = "تمرین بازنشانی شد. نوبت سیاه است؛ مهره خود را انتخاب کرده و خانه مقصد را لمس کنید.",
                    feedbackIsSuccess = false,
                    solutionPlayed = null
                )
            )
        }
    }
}
