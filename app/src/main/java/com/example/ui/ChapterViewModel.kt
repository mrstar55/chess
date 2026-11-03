package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.audio.AudioSettingsState
import com.example.audio.BookNarrationManager
import com.example.audio.ChessSoundEvent
import com.example.audio.ChessSoundManager
import com.example.audio.ChessSoundType
import com.example.audio.NarrationPlaybackSpeed
import com.example.audio.NarrationUiState
import com.example.chess.ChessRulesAdapter
import com.example.chess.KotlinChessRulesAdapter
import com.example.chess.Move
import com.example.chess.PgnReplayer
import com.example.data.ChapterRepository
import com.example.data.EducationalPlaybackSpeed
import com.example.data.FontSizeScale
import com.example.data.UserPreferencesManager
import com.example.model.BoardTransition
import com.example.model.BoardTransitionResolver
import com.example.model.DiagramItem
import com.example.model.MoveTrailState
import com.example.model.ReaderPage
import com.example.model.ReaderPaginator
import com.example.model.SectionItem
import com.example.model.TranslationSlot
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Bd032ExercisePhase {
    IDLE,
    PIECE_SELECTED,
    ANIMATING_CORRECT,
    SOLVED,
    ANIMATING_WRONG,
    WRONG_LEGAL_MOVE
}

data class Bd032UiState(
    val currentFen: String = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1",
    val selectedSquare: String? = null,
    val selectedFile: Int = -1,
    val selectedRank: Int = -1,
    val legalTargetSquares: List<String> = emptyList(),
    val showLegalMovesHint: Boolean = false,
    val illegalFlashSquares: List<String> = emptyList(),
    val isSolved: Boolean = false,
    val phase: Bd032ExercisePhase = if (isSolved) Bd032ExercisePhase.SOLVED else Bd032ExercisePhase.IDLE,
    val canRetry: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsSuccess: Boolean = false,
    val solutionPlayed: String? = null,
    val activeTransition: BoardTransition? = null,
    val activeMoveTrail: MoveTrailState? = null,
    val lastMoveDescriptionFa: String? = null
) {
    /**
     * By default (showLegalMovesHint == false), legal destination squares are hidden
     * so the puzzle solution is never given away upon selecting a piece.
     */
    val visibleTargetSquares: List<String>
        get() = if (showLegalMovesHint) legalTargetSquares else emptyList()

    val isBoardLocked: Boolean
        get() = isSolved ||
            phase == Bd032ExercisePhase.SOLVED ||
            phase == Bd032ExercisePhase.ANIMATING_CORRECT ||
            phase == Bd032ExercisePhase.ANIMATING_WRONG ||
            phase == Bd032ExercisePhase.WRONG_LEGAL_MOVE
}

data class ChapterUiState(
    val sections: List<SectionItem> = emptyList(),
    val diagrams: List<DiagramItem> = emptyList(),
    val currentSectionIndex: Int = 0,
    val currentPages: List<ReaderPage> = emptyList(),
    val currentPageIndex: Int = 0,
    val viewportWidthDp: Int = 380,
    val viewportHeightDp: Int = 640,
    val fontScale: FontSizeScale = FontSizeScale.NORMAL,
    val bookmarks: Set<String> = emptySet(),
    val pgnStates: Map<String, PgnReplayerState> = emptyMap(),
    val bd032State: Bd032UiState = Bd032UiState(),
    val audioSettings: AudioSettingsState = AudioSettingsState(),
    val narrationState: NarrationUiState = NarrationUiState(),
    val showMoveTrail: Boolean = true,
    val playbackSpeed: EducationalPlaybackSpeed = EducationalPlaybackSpeed.NORMAL,
    val showAudioSettingsDialog: Boolean = false,
    val currentTab: AppTab = AppTab.SPLASH
) {
    val currentPage: ReaderPage?
        get() = currentPages.getOrNull(currentPageIndex)
}

enum class AppTab {
    SPLASH,
    INTRODUCTION,
    SECTIONS_LIST,
    READER,
    AUDIT_REPORT
}

data class PgnReplayerState(
    val pgnRef: String,
    val currentStep: Int,
    val totalSteps: Int,
    val currentFen: String,
    val currentMoveSan: String?,
    val isAtStart: Boolean,
    val isAtEnd: Boolean,
    val activeTransition: BoardTransition? = null,
    val activeMoveTrail: MoveTrailState? = null,
    val lastMoveDescriptionFa: String? = null,
    val isTransitioning: Boolean = false
) {
    val canGoPrevious: Boolean
        get() = currentStep > 0

    val canGoNext: Boolean
        get() = currentStep < totalSteps
}

class ChapterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChapterRepository(application)
    private val prefs = UserPreferencesManager(application)
    val soundManager = ChessSoundManager(application)
    val narrationManager = BookNarrationManager(application, prefs)

    private val replayers = mutableMapOf<String, PgnReplayer>()
    private val bd032InitialFen = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1"

    // Single consistent adapter instance for BD-032 exercise
    private val bd032Adapter: ChessRulesAdapter = KotlinChessRulesAdapter(bd032InitialFen)

    private val transitionCounter = AtomicLong(1L)
    private val soundEventCounter = AtomicLong(1L)

    private val _soundEvents = MutableSharedFlow<ChessSoundEvent>(extraBufferCapacity = 64)
    val soundEvents: SharedFlow<ChessSoundEvent> = _soundEvents.asSharedFlow()

    private val _uiState = MutableStateFlow(ChapterUiState())
    val uiState: StateFlow<ChapterUiState> = _uiState.asStateFlow()

    init {
        narrationManager.setOnStateChangedListener { narrationSt ->
            _uiState.update { it.copy(narrationState = narrationSt) }
        }
        narrationManager.setAutoNavigationCallback { secIdx, blockId ->
            navigateToSectionAndBlockForNarration(secIdx, blockId)
        }
        loadData()
    }

    private fun emitSound(type: ChessSoundType, sourceTag: String = ""): ChessSoundEvent {
        val event = ChessSoundEvent(
            eventId = soundEventCounter.getAndIncrement(),
            soundType = type,
            sourceTag = sourceTag
        )
        soundManager.handleEvent(event, _uiState.value.audioSettings)
        _soundEvents.tryEmit(event)
        return event
    }

    private fun loadData() {
        val sections = repository.getSections()
        val diagrams = repository.getDiagrams()
        val savedSection = prefs.lastReadSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        val fontScale = prefs.fontScale
        val bookmarks = prefs.getBookmarks()
        val audioSettings = prefs.loadAudioSettings()
        val showMoveTrail = prefs.showMoveTrail
        val playbackSpeed = prefs.playbackSpeed

        val initialPages = sections.getOrNull(savedSection)?.let { section ->
            ReaderPaginator.paginateSection(
                section = section,
                diagrams = diagrams,
                fontScale = fontScale,
                viewportWidthDp = 380,
                viewportHeightDp = 640
            )
        } ?: emptyList()

        val savedPage = prefs.lastReadPageIndex.coerceIn(0, (initialPages.size - 1).coerceAtLeast(0))

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
                        isAtEnd = replayer.isAtEnd,
                        activeTransition = null,
                        activeMoveTrail = null,
                        lastMoveDescriptionFa = null,
                        isTransitioning = false
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
                currentPages = initialPages,
                currentPageIndex = savedPage,
                fontScale = fontScale,
                bookmarks = bookmarks,
                audioSettings = audioSettings,
                showMoveTrail = showMoveTrail,
                playbackSpeed = playbackSpeed,
                pgnStates = pgnStatesMap,
                bd032State = Bd032UiState(
                    currentFen = bd032InitialFen,
                    isSolved = previouslySolved,
                    phase = if (previouslySolved) Bd032ExercisePhase.SOLVED else Bd032ExercisePhase.IDLE,
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            )
        }
        narrationManager.refreshAvailability(sections, initialPages.getOrNull(savedPage))
    }

    /**
     * Enters a section and always opens its first page (page index 0).
     */
    fun selectSection(index: Int) {
        val stateSnapshot = _uiState.value
        val target = index.coerceIn(0, (stateSnapshot.sections.size - 1).coerceAtLeast(0))
        prefs.saveReadingPosition(sectionIndex = target, pageIndex = 0)
        _uiState.update { state ->
            val targetSection = state.sections.getOrNull(target)
            val pages = if (targetSection != null) {
                ReaderPaginator.paginateSection(
                    section = targetSection,
                    diagrams = state.diagrams,
                    fontScale = state.fontScale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            } else {
                emptyList()
            }
            // Clear any transient animations, move trails, and persistent move descriptions when switching sections
            val clearedPgnStates = state.pgnStates.mapValues { (_, pgn) ->
                pgn.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null,
                    isTransitioning = false
                )
            }
            state.copy(
                currentSectionIndex = target,
                currentPages = pages,
                currentPageIndex = 0,
                currentTab = AppTab.READER,
                pgnStates = clearedPgnStates,
                bd032State = state.bd032State.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            )
        }
        val updated = _uiState.value
        narrationManager.onManualPageOrSectionChanged(updated.sections, updated.currentPage)
    }

    /**
     * Resumes reading at the last saved section and saved page index without resetting to page 0.
     */
    fun resumeReading() {
        _uiState.update { state ->
            val targetSectionIdx = state.currentSectionIndex.coerceIn(0, (state.sections.size - 1).coerceAtLeast(0))
            val targetSection = state.sections.getOrNull(targetSectionIdx)
            val pages = state.currentPages.ifEmpty {
                if (targetSection != null) {
                    ReaderPaginator.paginateSection(
                        section = targetSection,
                        diagrams = state.diagrams,
                        fontScale = state.fontScale,
                        viewportWidthDp = state.viewportWidthDp,
                        viewportHeightDp = state.viewportHeightDp
                    )
                } else {
                    emptyList()
                }
            }
            val savedPageIdx = state.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            prefs.saveReadingPosition(sectionIndex = targetSectionIdx, pageIndex = savedPageIdx)
            val clearedPgnStates = state.pgnStates.mapValues { (_, pgn) ->
                pgn.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null,
                    isTransitioning = false
                )
            }
            state.copy(
                currentSectionIndex = targetSectionIdx,
                currentPages = pages,
                currentPageIndex = savedPageIdx,
                currentTab = AppTab.READER,
                pgnStates = clearedPgnStates,
                bd032State = state.bd032State.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            )
        }
        val updated = _uiState.value
        narrationManager.refreshAvailability(updated.sections, updated.currentPage)
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

    /**
     * Navigates to a specific 0-based page index within the current section and persists the reading position.
     */
    fun goToPage(pageIndex: Int) {
        val currentState = _uiState.value
        val maxIdx = (currentState.currentPages.size - 1).coerceAtLeast(0)
        val targetPage = pageIndex.coerceIn(0, maxIdx)
        val pageChanged = targetPage != currentState.currentPageIndex
        prefs.saveReadingPosition(sectionIndex = currentState.currentSectionIndex, pageIndex = targetPage)
        _uiState.update { state ->
            val updatedPgnStates = if (pageChanged) {
                state.pgnStates.mapValues { (_, pgn) ->
                    pgn.copy(
                        activeTransition = null,
                        activeMoveTrail = null,
                        lastMoveDescriptionFa = null,
                        isTransitioning = false
                    )
                }
            } else {
                state.pgnStates
            }
            val updatedBd032 = if (pageChanged) {
                state.bd032State.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            } else {
                state.bd032State
            }
            state.copy(
                currentPageIndex = targetPage,
                pgnStates = updatedPgnStates,
                bd032State = updatedBd032
            )
        }
        if (pageChanged) {
            val updated = _uiState.value
            narrationManager.onManualPageOrSectionChanged(updated.sections, updated.currentPage)
        }
    }

    /**
     * Auto-navigates to the page containing [blockId] inside [sectionIndex] during Mode B
     * (Continuous Chapter 1 narration) without interrupting active narration playback.
     */
    private fun navigateToSectionAndBlockForNarration(sectionIndex: Int, blockId: String) {
        val normalizedBlockId = ReaderPaginator.originalBlockId(blockId)
        _uiState.update { state ->
            val targetSecIdx = sectionIndex.coerceIn(0, (state.sections.size - 1).coerceAtLeast(0))
            val targetSection = state.sections.getOrNull(targetSecIdx) ?: return@update state
            val pages = if (targetSecIdx == state.currentSectionIndex && state.currentPages.isNotEmpty()) {
                state.currentPages
            } else {
                ReaderPaginator.paginateSection(
                    section = targetSection,
                    diagrams = state.diagrams,
                    fontScale = state.fontScale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            }
            val pageIdx = pages.indexOfFirst { page ->
                page.orderedBlocks.any { ReaderPaginator.originalBlockId(it.blockId) == normalizedBlockId }
            }.coerceAtLeast(0)
            prefs.saveReadingPosition(sectionIndex = targetSecIdx, pageIndex = pageIdx)
            state.copy(
                currentSectionIndex = targetSecIdx,
                currentPages = pages,
                currentPageIndex = pageIdx
            )
        }
    }

    fun nextPage(): Boolean {
        val state = _uiState.value
        if (state.currentPageIndex < state.currentPages.size - 1) {
            goToPage(state.currentPageIndex + 1)
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        val state = _uiState.value
        if (state.currentPageIndex > 0) {
            goToPage(state.currentPageIndex - 1)
            return true
        }
        return false
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun openIntroduction() {
        _uiState.update { it.copy(currentTab = AppTab.INTRODUCTION) }
    }

    fun openSectionsList() {
        _uiState.update { it.copy(currentTab = AppTab.SECTIONS_LIST) }
    }

    private fun findPreservedPageIndex(
        oldPages: List<ReaderPage>,
        oldPageIndex: Int,
        newPages: List<ReaderPage>
    ): Int {
        if (newPages.isEmpty()) return 0
        val oldPage = oldPages.getOrNull(oldPageIndex)
            ?: return oldPageIndex.coerceIn(0, newPages.lastIndex)

        // 1. If the user was on a page with a primary diagram, keep them on the page with that diagram
        val diagramId = oldPage.primaryDiagramId
        if (diagramId != null) {
            val diagramMatch = newPages.indexOfFirst { it.primaryDiagramId == diagramId }
            if (diagramMatch >= 0) return diagramMatch
        }

        // 2. Match the first content block on the page ( preferring non-heading content if a heading precedes a paragraph )
        val anchorBlock = oldPage.orderedBlocks.firstOrNull {
            it !is com.example.model.SectionContentBlock.HeadingBlock
        } ?: oldPage.orderedBlocks.firstOrNull()

        if (anchorBlock != null) {
            val exactId = anchorBlock.blockId
            val origId = ReaderPaginator.originalBlockId(exactId)

            val oldPagesWithOrig = oldPages.indices.filter { idx ->
                oldPages[idx].orderedBlocks.any { ReaderPaginator.originalBlockId(it.blockId) == origId }
            }
            val newPagesWithOrig = newPages.indices.filter { idx ->
                newPages[idx].orderedBlocks.any { ReaderPaginator.originalBlockId(it.blockId) == origId }
            }

            if (newPagesWithOrig.isNotEmpty()) {
                if (oldPagesWithOrig.size > 1 && newPagesWithOrig.size > 1) {
                    val posInOldGroup = oldPagesWithOrig.indexOf(oldPageIndex).coerceAtLeast(0)
                    val fraction = posInOldGroup.toFloat() / (oldPagesWithOrig.size - 1).coerceAtLeast(1).toFloat()
                    val targetInNewGroup = kotlin.math.round(fraction * (newPagesWithOrig.size - 1)).toInt()
                        .coerceIn(0, newPagesWithOrig.lastIndex)
                    return newPagesWithOrig[targetInNewGroup]
                }
                val exactMatch = newPages.indexOfFirst { page ->
                    page.orderedBlocks.any { it.blockId == exactId }
                }
                if (exactMatch >= 0) return exactMatch
                return newPagesWithOrig.first()
            }
        }

        return oldPageIndex.coerceIn(0, newPages.lastIndex)
    }

    fun setFontScale(scale: FontSizeScale) {
        prefs.fontScale = scale
        _uiState.update { state ->
            val currentSection = state.sections.getOrNull(state.currentSectionIndex)
            val newPages = if (currentSection != null) {
                ReaderPaginator.paginateSection(
                    section = currentSection,
                    diagrams = state.diagrams,
                    fontScale = scale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            } else {
                state.currentPages
            }
            val mappedPageIndex = findPreservedPageIndex(
                oldPages = state.currentPages,
                oldPageIndex = state.currentPageIndex,
                newPages = newPages
            )
            prefs.saveReadingPosition(state.currentSectionIndex, mappedPageIndex)
            state.copy(
                fontScale = scale,
                currentPages = newPages,
                currentPageIndex = mappedPageIndex
            )
        }
        val updated = _uiState.value
        narrationManager.refreshAvailability(updated.sections, updated.currentPage)
    }

    fun updateViewportSize(widthDp: Int, heightDp: Int) {
        val clampedWidth = widthDp.coerceAtLeast(200)
        val clampedHeight = heightDp.coerceAtLeast(200)
        val current = _uiState.value
        if (current.viewportWidthDp == clampedWidth && current.viewportHeightDp == clampedHeight) return
        _uiState.update { state ->
            val currentSection = state.sections.getOrNull(state.currentSectionIndex)
            val newPages = if (currentSection != null) {
                ReaderPaginator.paginateSection(
                    section = currentSection,
                    diagrams = state.diagrams,
                    fontScale = state.fontScale,
                    viewportWidthDp = clampedWidth,
                    viewportHeightDp = clampedHeight
                )
            } else {
                state.currentPages
            }
            val mappedPageIndex = findPreservedPageIndex(
                oldPages = state.currentPages,
                oldPageIndex = state.currentPageIndex,
                newPages = newPages
            )
            prefs.saveReadingPosition(state.currentSectionIndex, mappedPageIndex)
            state.copy(
                viewportWidthDp = clampedWidth,
                viewportHeightDp = clampedHeight,
                currentPages = newPages,
                currentPageIndex = mappedPageIndex
            )
        }
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
            val activeSection = updatedSections.getOrNull(state.currentSectionIndex)
            val newPages = if (activeSection != null) {
                ReaderPaginator.paginateSection(
                    section = activeSection,
                    diagrams = state.diagrams,
                    fontScale = state.fontScale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            } else {
                state.currentPages
            }
            val clampedPageIdx = state.currentPageIndex.coerceIn(0, (newPages.size - 1).coerceAtLeast(0))
            state.copy(
                sections = updatedSections,
                currentPages = newPages,
                currentPageIndex = clampedPageIdx
            )
        }
        val updated = _uiState.value
        narrationManager.refreshAvailability(updated.sections, updated.currentPage)
    }

    fun stepPgnNext(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        val fromFen = replayer.currentFen
        if (replayer.next()) {
            val toFen = replayer.currentFen
            val transitionId = "pgn_${pgnRef}_next_${replayer.currentStep}_${transitionCounter.getAndIncrement()}"
            val transition = BoardTransitionResolver.resolveForwardTransition(
                transitionId = transitionId,
                fromFen = fromFen,
                toFen = toFen
            )
            val moveTrail = if (transition != null && _uiState.value.showMoveTrail) {
                BoardTransitionResolver.createMoveTrail(transition, _uiState.value.playbackSpeed)
            } else {
                null
            }
            // Persistent move description is independent of showMoveTrail and stays visible after trail fades
            val moveDescriptionFa = BoardTransitionResolver.buildPersistentMoveDescriptionFa(transition)
            updatePgnState(
                pgnRef = pgnRef,
                replayer = replayer,
                transition = transition,
                moveTrail = moveTrail,
                lastMoveDescriptionFa = moveDescriptionFa
            )
            if (transition != null) {
                val soundType = ChessSoundManager.resolveSoundForTransition(transition)
                emitSound(soundType, "pgn_next_$pgnRef")
            }
        }
    }

    fun stepPgnPrev(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        val fromFen = replayer.currentFen
        if (replayer.previous()) {
            val toFen = replayer.currentFen
            val transitionId = "pgn_${pgnRef}_prev_${replayer.currentStep}_${transitionCounter.getAndIncrement()}"
            val transition = BoardTransitionResolver.resolveReverseTransition(
                transitionId = transitionId,
                currentFen = fromFen,
                previousFen = toFen
            )
            // Undo / Previous move strictly clears MoveTrailState AND clears persistent move description
            updatePgnState(
                pgnRef = pgnRef,
                replayer = replayer,
                transition = transition,
                moveTrail = null,
                lastMoveDescriptionFa = null
            )
            if (transition != null) {
                val soundType = ChessSoundManager.resolveSoundForTransition(transition)
                emitSound(soundType, "pgn_prev_$pgnRef")
            }
        }
    }

    fun resetPgn(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        replayer.reset()
        // Direct jump to start: clears transition, move trail, and persistent move description
        updatePgnState(
            pgnRef = pgnRef,
            replayer = replayer,
            transition = null,
            moveTrail = null,
            lastMoveDescriptionFa = null
        )
    }

    fun jumpPgnToEnd(pgnRef: String) {
        val replayer = replayers[pgnRef] ?: return
        replayer.jumpToEnd()
        // Direct jump to end: clears transition, move trail, and persistent move description
        updatePgnState(
            pgnRef = pgnRef,
            replayer = replayer,
            transition = null,
            moveTrail = null,
            lastMoveDescriptionFa = null
        )
    }

    fun onPgnTransitionFinished(pgnRef: String, transitionId: String) {
        _uiState.update { state ->
            val current = state.pgnStates[pgnRef] ?: return@update state
            if (current.activeTransition?.transitionId != transitionId) return@update state
            val updatedMap = state.pgnStates.toMutableMap()
            updatedMap[pgnRef] = current.copy(
                activeTransition = null,
                isTransitioning = false
            )
            state.copy(pgnStates = updatedMap)
        }
    }

    fun onPgnMoveTrailFinished(pgnRef: String, eventId: String) {
        _uiState.update { state ->
            val current = state.pgnStates[pgnRef] ?: return@update state
            if (current.activeMoveTrail?.eventId != eventId) return@update state
            val updatedMap = state.pgnStates.toMutableMap()
            // Clear only activeMoveTrail; lastMoveDescriptionFa remains visible!
            updatedMap[pgnRef] = current.copy(
                activeMoveTrail = null
            )
            state.copy(pgnStates = updatedMap)
        }
    }

    private fun updatePgnState(
        pgnRef: String,
        replayer: PgnReplayer,
        transition: BoardTransition?,
        moveTrail: MoveTrailState? = null,
        lastMoveDescriptionFa: String? = null
    ) {
        _uiState.update { state ->
            val updatedMap = state.pgnStates.toMutableMap()
            updatedMap[pgnRef] = PgnReplayerState(
                pgnRef = pgnRef,
                currentStep = replayer.currentStep,
                totalSteps = replayer.sequence.totalSteps,
                currentFen = replayer.currentFen,
                currentMoveSan = replayer.currentMoveSan,
                isAtStart = replayer.isAtStart,
                isAtEnd = replayer.isAtEnd,
                activeTransition = transition,
                activeMoveTrail = moveTrail,
                lastMoveDescriptionFa = lastMoveDescriptionFa,
                isTransitioning = transition != null
            )
            state.copy(pgnStates = updatedMap)
        }
    }

    // ==========================================
    // Book Narration Controls (Independent of ChessSoundManager)
    // ==========================================

    fun playCurrentPageNarration(): Boolean {
        val st = _uiState.value
        val page = st.currentPage ?: return false
        return narrationManager.playCurrentPage(
            sections = st.sections,
            sectionIndex = st.currentSectionIndex,
            page = page
        )
    }

    fun playChapterNarration(fromCurrentSection: Boolean = true): Boolean {
        val st = _uiState.value
        val startIdx = if (fromCurrentSection) st.currentSectionIndex else 0
        return narrationManager.playContinuousChapter(
            sections = st.sections,
            startSectionIndex = startIdx,
            startBlockId = null,
            startPositionMs = 0
        )
    }

    fun pauseNarration() {
        narrationManager.pause()
    }

    fun resumeNarration(): Boolean {
        return narrationManager.resume(_uiState.value.sections)
    }

    fun stopNarration() {
        narrationManager.stop()
    }

    fun seekNarrationTo(positionMs: Int) {
        narrationManager.seekTo(positionMs)
    }

    fun setNarrationSpeed(speed: NarrationPlaybackSpeed) {
        narrationManager.setSpeed(speed)
    }

    fun cycleNarrationSpeed(): NarrationPlaybackSpeed {
        return narrationManager.cycleSpeed()
    }

    fun setNarrationVolumePercent(volumePercent: Int) {
        narrationManager.setVolumePercent(volumePercent)
    }

    fun setNarrationEnabled(enabled: Boolean) {
        narrationManager.setNarrationEnabled(enabled)
        val st = _uiState.value
        narrationManager.refreshAvailability(st.sections, st.currentPage)
    }

    // ==========================================
    // Audio Settings & Educational Visual Settings
    // ==========================================

    fun openAudioSettings() {
        _uiState.update { it.copy(showAudioSettingsDialog = true) }
    }

    fun closeAudioSettings() {
        _uiState.update { it.copy(showAudioSettingsDialog = false) }
    }

    private fun updateAudioSettings(transform: (AudioSettingsState) -> AudioSettingsState) {
        val updated = transform(_uiState.value.audioSettings)
        prefs.saveAudioSettings(updated)
        _uiState.update { it.copy(audioSettings = updated) }
    }

    fun setMasterSoundEnabled(enabled: Boolean) {
        updateAudioSettings { it.copy(soundEnabled = enabled) }
    }

    fun setSoundVolumePercent(volumePercent: Int) {
        updateAudioSettings { it.copy(volumePercent = volumePercent.coerceIn(0, 100)) }
    }

    fun setMoveSoundEnabled(enabled: Boolean) {
        updateAudioSettings { it.copy(moveSoundEnabled = enabled) }
    }

    fun setCaptureSoundEnabled(enabled: Boolean) {
        updateAudioSettings { it.copy(captureSoundEnabled = enabled) }
    }

    fun setSpecialMoveSoundEnabled(enabled: Boolean) {
        updateAudioSettings { it.copy(specialMoveSoundEnabled = enabled) }
    }

    fun setCheckMateSoundEnabled(enabled: Boolean) {
        updateAudioSettings { it.copy(checkMateSoundEnabled = enabled) }
    }

    fun setExerciseFeedbackSoundEnabled(enabled: Boolean) {
        updateAudioSettings { it.copy(exerciseFeedbackSoundEnabled = enabled) }
    }

    fun setShowMoveTrail(enabled: Boolean) {
        prefs.showMoveTrail = enabled
        _uiState.update { state ->
            // Note: disabling showMoveTrail clears only activeMoveTrail; lastMoveDescriptionFa stays intact!
            val updatedPgn = if (!enabled) {
                state.pgnStates.mapValues { (_, pgn) -> pgn.copy(activeMoveTrail = null) }
            } else {
                state.pgnStates
            }
            state.copy(
                showMoveTrail = enabled,
                pgnStates = updatedPgn,
                bd032State = if (!enabled) state.bd032State.copy(activeMoveTrail = null) else state.bd032State
            )
        }
    }

    fun setPlaybackSpeed(speed: EducationalPlaybackSpeed) {
        prefs.playbackSpeed = speed
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun playTestSound() {
        emitSound(ChessSoundType.MOVE_NORMAL, "audio_test_btn")
    }

    // ==========================================
    // Dynamic BD-032 Chess Engine Exercise Logic
    // ==========================================

    fun toggleBd032LegalMovesHint() {
        _uiState.update { state ->
            val currentBd = state.bd032State
            if (currentBd.isBoardLocked) return@update state
            state.copy(
                bd032State = currentBd.copy(
                    showLegalMovesHint = !currentBd.showLegalMovesHint
                )
            )
        }
    }

    fun onBd032SquareClicked(file: Int, rank: Int) {
        val currentState = _uiState.value.bd032State

        // Disable further interaction when board is locked (solved, animating, or waiting for retry after wrong legal move)
        if (currentState.isBoardLocked) return

        // Ensure adapter is synced with initial position before evaluating selection/move
        if (bd032Adapter.currentFen != bd032InitialFen) {
            bd032Adapter.resetToFen(bd032InitialFen)
        }

        val clickedNotation = "${('a' + file)}${rank + 1}"
        val pieceAtClicked = bd032Adapter.getPiece(file, rank)

        // 1. If clicking a friendly black piece, select it and compute its real legal moves (hidden by default unless showLegalMovesHint is toggled on)
        // Note: piece selection alone does NOT create a persistent move description
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
                        illegalFlashSquares = emptyList(),
                        phase = Bd032ExercisePhase.PIECE_SELECTED,
                        feedbackMessage = "مهره انتخاب شد. اکنون خانه مقصد را روی صفحه لمس کنید.",
                        feedbackIsSuccess = false,
                        activeTransition = null
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
        val currentBd = _uiState.value.bd032State
        // Always evaluate from the initial exercise position using the single adapter
        bd032Adapter.resetToFen(bd032InitialFen)
        val moveResult = bd032Adapter.executeMove(move)

        // Case 1: Illegal chess move -> keep piece selected, keep board unchanged, show red highlight and exact error message, emit ILLEGAL_MOVE sound.
        // Do NOT create a move trail or persistent move description for illegal moves.
        if (moveResult == null) {
            bd032Adapter.resetToFen(bd032InitialFen)
            val flashSquares = listOfNotNull(currentBd.selectedSquare, move.toNotation).distinct()
            _uiState.update {
                it.copy(
                    bd032State = currentBd.copy(
                        currentFen = bd032InitialFen,
                        // Keep selected piece selected so user can try another destination immediately
                        phase = Bd032ExercisePhase.PIECE_SELECTED,
                        illegalFlashSquares = flashSquares,
                        feedbackMessage = "این حرکت طبق قوانین شطرنج مجاز نیست.",
                        feedbackIsSuccess = false,
                        activeTransition = null
                    )
                )
            }
            emitSound(ChessSoundType.ILLEGAL_MOVE, "bd032_illegal_move")
            return
        }

        val san = moveResult.san
        val finalFen = bd032Adapter.currentFen

        // Case 2: Legal move AND delivers checkmate -> animate winning move, show final position, mark solved, lock board until Reset
        if (moveResult.isCheckmate) {
            val transitionId = "bd032_solve_${transitionCounter.getAndIncrement()}"
            val transition = BoardTransitionResolver.resolveForwardTransition(
                transitionId = transitionId,
                fromFen = bd032InitialFen,
                toFen = finalFen
            )
            val moveTrail = if (transition != null && _uiState.value.showMoveTrail) {
                BoardTransitionResolver.createMoveTrail(transition, _uiState.value.playbackSpeed)
            } else {
                null
            }
            val moveDescFa = BoardTransitionResolver.buildPersistentMoveDescriptionFa(transition)
            prefs.setBd032Solved(true)
            _uiState.update {
                it.copy(
                    bd032State = currentBd.copy(
                        currentFen = finalFen,
                        selectedSquare = null,
                        selectedFile = -1,
                        selectedRank = -1,
                        legalTargetSquares = emptyList(),
                        illegalFlashSquares = emptyList(),
                        isSolved = true,
                        phase = if (transition != null) Bd032ExercisePhase.ANIMATING_CORRECT else Bd032ExercisePhase.SOLVED,
                        canRetry = false,
                        feedbackMessage = "پاسخ صحیح! حرکت $san توسط موتور قوانین بررسی شد و شاه سفید مات شد.",
                        feedbackIsSuccess = true,
                        solutionPlayed = san,
                        activeTransition = transition,
                        activeMoveTrail = moveTrail,
                        lastMoveDescriptionFa = moveDescFa
                    )
                )
            }
            // Dominant checkmate sound (no overlapping normal move sound)
            emitSound(ChessSoundType.CHECKMATE, "bd032_checkmate")
        } else {
            // Case 3: Legal chess move, but NOT checkmate in 1 ->
            // Execute and animate the move on the board, keep board at post-move FEN,
            // and after animation finishes transition to WRONG_LEGAL_MOVE with retry button and feedback.
            val transitionId = "bd032_wrong_${transitionCounter.getAndIncrement()}"
            val transition = BoardTransitionResolver.resolveForwardTransition(
                transitionId = transitionId,
                fromFen = bd032InitialFen,
                toFen = finalFen
            )
            val moveTrail = if (transition != null && _uiState.value.showMoveTrail) {
                BoardTransitionResolver.createMoveTrail(transition, _uiState.value.playbackSpeed)
            } else {
                null
            }
            val moveDescFa = BoardTransitionResolver.buildPersistentMoveDescriptionFa(transition)
            val moveSound = if (transition != null) {
                ChessSoundManager.resolveSoundForTransition(transition, preferCaptureOverCheck = true)
            } else {
                ChessSoundType.MOVE_NORMAL
            }
            emitSound(moveSound, "bd032_wrong_legal_step")

            if (transition != null) {
                _uiState.update {
                    it.copy(
                        bd032State = currentBd.copy(
                            currentFen = finalFen,
                            selectedSquare = null,
                            selectedFile = -1,
                            selectedRank = -1,
                            legalTargetSquares = emptyList(),
                            illegalFlashSquares = emptyList(),
                            isSolved = false,
                            phase = Bd032ExercisePhase.ANIMATING_WRONG,
                            canRetry = true,
                            feedbackMessage = null,
                            feedbackIsSuccess = false,
                            solutionPlayed = san,
                            activeTransition = transition,
                            activeMoveTrail = moveTrail,
                            lastMoveDescriptionFa = moveDescFa
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        bd032State = currentBd.copy(
                            currentFen = finalFen,
                            selectedSquare = null,
                            selectedFile = -1,
                            selectedRank = -1,
                            legalTargetSquares = emptyList(),
                            illegalFlashSquares = emptyList(),
                            isSolved = false,
                            phase = Bd032ExercisePhase.WRONG_LEGAL_MOVE,
                            canRetry = true,
                            feedbackMessage = "این حرکت قانونی است، اما مسئله را حل نمی‌کند. دوباره تلاش کنید.",
                            feedbackIsSuccess = false,
                            solutionPlayed = san,
                            activeTransition = null,
                            activeMoveTrail = null,
                            lastMoveDescriptionFa = moveDescFa
                        )
                    )
                }
                emitSound(ChessSoundType.EXERCISE_WRONG, "bd032_wrong_feedback")
            }
        }
    }

    fun onBd032TransitionFinished(transitionId: String) {
        val currentBd = _uiState.value.bd032State
        if (currentBd.activeTransition?.transitionId != transitionId) return

        when (currentBd.phase) {
            Bd032ExercisePhase.ANIMATING_WRONG -> {
                _uiState.update { state ->
                    state.copy(
                        bd032State = state.bd032State.copy(
                            phase = Bd032ExercisePhase.WRONG_LEGAL_MOVE,
                            canRetry = true,
                            feedbackMessage = "این حرکت قانونی است، اما مسئله را حل نمی‌کند. دوباره تلاش کنید.",
                            feedbackIsSuccess = false,
                            activeTransition = null
                        )
                    )
                }
                emitSound(ChessSoundType.EXERCISE_WRONG, "bd032_wrong_feedback")
            }
            Bd032ExercisePhase.ANIMATING_CORRECT -> {
                _uiState.update { state ->
                    state.copy(
                        bd032State = state.bd032State.copy(
                            phase = Bd032ExercisePhase.SOLVED,
                            activeTransition = null
                        )
                    )
                }
                emitSound(ChessSoundType.EXERCISE_CORRECT, "bd032_correct_complete")
            }
            else -> {
                _uiState.update { state ->
                    state.copy(
                        bd032State = state.bd032State.copy(activeTransition = null)
                    )
                }
            }
        }
    }

    fun onBd032MoveTrailFinished(eventId: String) {
        _uiState.update { state ->
            val currentBd = state.bd032State
            if (currentBd.activeMoveTrail?.eventId != eventId) return@update state
            // Clear only activeMoveTrail; lastMoveDescriptionFa remains visible!
            state.copy(
                bd032State = currentBd.copy(activeMoveTrail = null)
            )
        }
    }

    /**
     * Retries BD-032 after a wrong legal move ("تلاش دوباره"):
     * - Returns board to initial exercise FEN
     * - Clears piece selection
     * - Clears previous feedback message and persistent move description
     * - Resets legal moves hint to false (off)
     * - Re-enables piece selection
     */
    fun retryBd032() {
        bd032Adapter.resetToFen(bd032InitialFen)
        _uiState.update {
            it.copy(
                bd032State = Bd032UiState(
                    currentFen = bd032InitialFen,
                    selectedSquare = null,
                    selectedFile = -1,
                    selectedRank = -1,
                    legalTargetSquares = emptyList(),
                    showLegalMovesHint = false,
                    illegalFlashSquares = emptyList(),
                    isSolved = false,
                    phase = Bd032ExercisePhase.IDLE,
                    canRetry = false,
                    feedbackMessage = null,
                    feedbackIsSuccess = false,
                    solutionPlayed = null,
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            )
        }
        emitSound(ChessSoundType.EXERCISE_RESET, "bd032_retry")
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
                    showLegalMovesHint = false,
                    illegalFlashSquares = emptyList(),
                    isSolved = false,
                    phase = Bd032ExercisePhase.IDLE,
                    canRetry = false,
                    feedbackMessage = "تمرین بازنشانی شد. نوبت سیاه است؛ مهره خود را انتخاب کرده و خانه مقصد را لمس کنید.",
                    feedbackIsSuccess = false,
                    solutionPlayed = null,
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            )
        }
        emitSound(ChessSoundType.EXERCISE_RESET, "bd032_reset")
    }

    public override fun onCleared() {
        super.onCleared()
        soundManager.release()
        narrationManager.release()
    }
}
