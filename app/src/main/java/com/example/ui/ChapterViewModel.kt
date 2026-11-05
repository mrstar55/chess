package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.audio.AudioSettingsState
import com.example.audio.BookNarrationManager
import com.example.audio.ChessSoundEvent
import com.example.audio.ChessSoundManager
import com.example.audio.ChessSoundType
import com.example.audio.NarrationPlaybackSpeed
import com.example.audio.NarrationUiState
import com.example.chess.ChessRulesAdapter
import com.example.chess.ExerciseHandlerRegistry
import com.example.chess.PgnReplayer
import com.example.chess.PgnSequence
import com.example.data.ChapterRepository
import com.example.data.EducationalPlaybackSpeed
import com.example.data.FontSizeScale
import com.example.data.UserPreferencesManager
import com.example.model.BoardTransition
import com.example.model.BoardTransitionResolver
import com.example.model.ChapterCatalogEntry
import com.example.model.ChapterSourcePageIndex
import com.example.model.DiagramItem
import com.example.model.ExerciseDefinition
import com.example.model.ExercisePhase
import com.example.model.ExerciseUiState
import com.example.model.FrontMatterCatalogEntry
import com.example.model.FrontMatterContentPack
import com.example.model.FrontMatterSourceLink
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

@Deprecated(
    message = "Use ExercisePhase from com.example.model instead.",
    replaceWith = ReplaceWith("ExercisePhase", "com.example.model.ExercisePhase")
)
typealias Bd032ExercisePhase = ExercisePhase

@Deprecated(
    message = "Use ExerciseUiState from com.example.model instead.",
    replaceWith = ReplaceWith("ExerciseUiState", "com.example.model.ExerciseUiState")
)
typealias Bd032UiState = ExerciseUiState

data class ChapterUiState(
    val activeBookId: String = "",
    val activeBookTitleFa: String = "",
    val activeContentRegionId: String = ChapterRepository.DEFAULT_CHAPTER_ID,
    val activeChapterId: String = "",
    val activeChapterNumber: Int = 1,
    val activeChapterTitleFa: String = "",
    val activeChapterTitleEn: String = "",
    val availableChapters: List<ChapterCatalogEntry> = emptyList(),
    val frontMatterEntry: FrontMatterCatalogEntry? = null,
    val frontMatterPack: FrontMatterContentPack? = null,
    val frontMatterPages: List<ReaderPage> = emptyList(),
    val chapterSourcePageIndex: ChapterSourcePageIndex? = null,
    val activeSourcePdfPageFilter: Int? = null,
    val lastReadFrontMatterSourcePdfPage: Int = 1,
    val lastReadFrontMatterPageIndex: Int = 0,
    val lastNavigationNoticeFa: String? = null,
    val lastOpenedExternalUri: String? = null,
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
    val exerciseDefinitions: Map<String, ExerciseDefinition> = emptyMap(),
    val exerciseStates: Map<String, ExerciseUiState> = emptyMap(),
    val audioSettings: AudioSettingsState = AudioSettingsState(),
    val narrationState: NarrationUiState = NarrationUiState(),
    val showMoveTrail: Boolean = true,
    val playbackSpeed: EducationalPlaybackSpeed = EducationalPlaybackSpeed.NORMAL,
    val showAudioSettingsDialog: Boolean = false,
    val currentTab: AppTab = AppTab.SPLASH
) {
    val hasMultipleChapters: Boolean
        get() = availableChapters.size > 1

    val hasFrontMatter: Boolean
        get() = frontMatterEntry != null && frontMatterPack != null && frontMatterPages.isNotEmpty()

    val isReadingFrontMatter: Boolean
        get() = activeContentRegionId == ChapterRepository.FRONT_MATTER_REGION_ID

    val frontMatterNavigationTitleFa: String
        get() = frontMatterEntry?.navigationTitleFa ?: "پیش‌درآمد و مبانی شطرنج"

    val frontMatterFullTitleFa: String
        get() = frontMatterPack?.manifest?.titleFa
            ?: frontMatterEntry?.titleFa
            ?: "پیش‌درآمد، مقدمه و آموزش مبانی شطرنج"

    val activeChapterShortTitleFa: String
        get() = activeChapterTitleFa.substringBefore(":").trim().ifBlank { activeChapterTitleFa }

    val currentPage: ReaderPage?
        get() = currentPages.getOrNull(currentPageIndex)

    fun exerciseStateFor(
        exerciseId: String,
        diagramId: String? = null
    ): ExerciseUiState? {
        exerciseStates[exerciseId]?.let { return it }
        if (diagramId != null) {
            val matchedDef = exerciseDefinitions.values.firstOrNull { it.diagramId == diagramId }
            if (matchedDef != null) {
                exerciseStates[matchedDef.exerciseId]?.let { return it }
            }
        }
        return null
    }

    @Deprecated(
        message = "Legacy test compatibility accessor; active code must use exerciseStates[exerciseId] or exerciseStateFor(exerciseId)."
    )
    val bd032State: ExerciseUiState
        get() = exerciseStates[UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID] ?: ExerciseUiState()
}

enum class AppTab {
    SPLASH,
    INTRODUCTION,
    CHAPTERS_LIST,
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
    val isTransitioning: Boolean = false,
    val hasUserStartedReplay: Boolean = false
) {
    val canGoPrevious: Boolean
        get() = currentStep > 0

    val canGoNext: Boolean
        get() = currentStep < totalSteps
}

class ChapterViewModel @JvmOverloads constructor(
    application: Application,
    customRepository: ChapterRepository? = null
) : AndroidViewModel(application) {

    val repository = customRepository ?: ChapterRepository(application)
    private val prefs = UserPreferencesManager(application)
    val soundManager = ChessSoundManager(application)
    val narrationManager = BookNarrationManager(application, prefs)

    private val replayers = mutableMapOf<String, PgnReplayer>()
    private val exerciseAdapters = mutableMapOf<String, ChessRulesAdapter>()
    private var loadedPgnSequences: Map<String, PgnSequence> = emptyMap()

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
        val (book, initialChapter) = repository.resolveInitialChapterEntry(
            preferredBookId = prefs.getSavedActiveBookIdOrNull(),
            preferredChapterId = prefs.getSavedActiveChapterIdOrNull()
        )
        val chapters = book.enabledChapters

        loadChapterInternal(
            bookId = book.bookId,
            bookTitleFa = book.titleFa,
            chapterEntry = initialChapter,
            availableChapters = chapters,
            targetTab = AppTab.SPLASH
        )
    }

    private fun loadChapterInternal(
        bookId: String,
        bookTitleFa: String,
        chapterEntry: ChapterCatalogEntry,
        availableChapters: List<ChapterCatalogEntry>,
        targetTab: AppTab
    ) {
        val chapterId = chapterEntry.chapterId
        val manifest = repository.getChapterManifest(chapterId = chapterId, bookId = bookId)
        val sections = repository.getSections(chapterId = chapterId, bookId = bookId)
        val diagrams = repository.getDiagrams(chapterId = chapterId, bookId = bookId)
        val pgnSequences = repository.getAllPgnSequences(chapterId = chapterId, bookId = bookId)
        val exercises = repository.getExercises(chapterId = chapterId, bookId = bookId)
        val frontMatterEntry = repository.getFrontMatterCatalogEntry(bookId = bookId)
        val frontMatterPack = runCatching { repository.getFrontMatterContentPack(bookId = bookId) }.getOrNull()
        val chapterSourcePageIndex = runCatching {
            repository.getChapterSourcePageIndex(chapterId = chapterId, bookId = bookId)
        }.getOrNull()

        loadedPgnSequences = pgnSequences

        val savedPos = prefs.getSavedReadingPosition(chapterId = chapterId, bookId = bookId)
        val savedSection = if (!savedPos.sectionId.isNullOrBlank()) {
            val byId = sections.indexOfFirst { it.sectionId == savedPos.sectionId }
            if (byId >= 0) byId else savedPos.sectionIndexFallback.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        } else {
            savedPos.sectionIndexFallback.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        }

        val fontScale = prefs.fontScale
        val bookmarks = prefs.getBookmarks()
        val audioSettings = prefs.loadAudioSettings()
        val showMoveTrail = prefs.showMoveTrail
        val playbackSpeed = prefs.playbackSpeed
        val vpWidth = _uiState.value.viewportWidthDp
        val vpHeight = _uiState.value.viewportHeightDp

        val frontMatterPages = if (frontMatterPack != null) {
            ReaderPaginator.paginateFrontMatterPages(
                frontMatterPages = frontMatterPack.pages,
                fontScale = fontScale,
                viewportWidthDp = vpWidth,
                viewportHeightDp = vpHeight
            )
        } else {
            emptyList()
        }
        val savedFmPos = prefs.getSavedFrontMatterReadingPosition(bookId = bookId)
        val savedFmPageIdx = if (!savedFmPos.pageId.isNullOrBlank()) {
            val byPageId = frontMatterPages.indexOfFirst { it.pageId == savedFmPos.pageId }
            if (byPageId >= 0) {
                byPageId
            } else {
                frontMatterPages.indexOfFirst { it.sourcePdfPage == savedFmPos.sourcePdfPage }
                    .takeIf { it >= 0 }
                    ?: savedFmPos.pageIndex.coerceIn(0, (frontMatterPages.size - 1).coerceAtLeast(0))
            }
        } else {
            frontMatterPages.indexOfFirst { it.sourcePdfPage == savedFmPos.sourcePdfPage }
                .takeIf { it >= 0 }
                ?: savedFmPos.pageIndex.coerceIn(0, (frontMatterPages.size - 1).coerceAtLeast(0))
        }

        val restoredSourcePageEntry = if (
            savedPos.pageId?.startsWith("PDF_P") == true &&
            savedPos.sourcePdfPage != null
        ) {
            chapterSourcePageIndex?.entryForPage(savedPos.sourcePdfPage)
        } else {
            null
        }

        val initialPages = if (restoredSourcePageEntry != null && sections.isNotEmpty()) {
            ReaderPaginator.paginateChapterSourcePdfPage(
                pageEntry = restoredSourcePageEntry,
                sections = sections,
                diagrams = diagrams,
                fontScale = fontScale,
                viewportWidthDp = vpWidth,
                viewportHeightDp = vpHeight
            )
        } else {
            sections.getOrNull(savedSection)?.let { section ->
                ReaderPaginator.paginateSection(
                    section = section,
                    diagrams = diagrams,
                    fontScale = fontScale,
                    viewportWidthDp = vpWidth,
                    viewportHeightDp = vpHeight
                )
            } ?: emptyList()
        }

        val savedPage = when {
            !savedPos.pageId.isNullOrBlank() -> {
                val byPageId = initialPages.indexOfFirst { it.pageId == savedPos.pageId }
                if (byPageId >= 0) byPageId else savedPos.pageIndex.coerceIn(0, (initialPages.size - 1).coerceAtLeast(0))
            }
            !savedPos.blockId.isNullOrBlank() -> {
                val byBlockId = initialPages.indexOfFirst { page ->
                    page.orderedBlocks.any { ReaderPaginator.originalBlockId(it.blockId) == savedPos.blockId }
                }
                if (byBlockId >= 0) byBlockId else savedPos.pageIndex.coerceIn(0, (initialPages.size - 1).coerceAtLeast(0))
            }
            savedPos.sourcePdfPage != null -> {
                val byPdfPage = initialPages.indexOfFirst { it.sourcePdfPage == savedPos.sourcePdfPage }
                if (byPdfPage >= 0) byPdfPage else savedPos.pageIndex.coerceIn(0, (initialPages.size - 1).coerceAtLeast(0))
            }
            else -> savedPos.pageIndex.coerceIn(0, (initialPages.size - 1).coerceAtLeast(0))
        }

        replayers.clear()
        val pgnStatesMap = mutableMapOf<String, PgnReplayerState>()
        diagrams.forEach { diagram ->
            diagram.pgnRef?.let { pgnRef ->
                val seq = pgnSequences[pgnRef]
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
                        isTransitioning = false,
                        hasUserStartedReplay = false
                    )
                }
            }
        }

        val diagramsById = diagrams.associateBy { it.diagramId }
        exerciseAdapters.clear()
        val exerciseDefsMap = linkedMapOf<String, ExerciseDefinition>()
        val exerciseStatesMap = linkedMapOf<String, ExerciseUiState>()

        for (exDef in exercises) {
            exerciseDefsMap[exDef.exerciseId] = exDef
            val diag = diagramsById[exDef.diagramId] ?: continue
            val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: continue
            val adapter = handler.createRulesAdapter(exDef, diag)
            exerciseAdapters[exDef.exerciseId] = adapter
            val solved = prefs.isExerciseSolved(exDef.exerciseId)
            exerciseStatesMap[exDef.exerciseId] = handler.createInitialState(exDef, diag, solved)
        }

        narrationManager.switchChapterManifest(
            chapterId = chapterId,
            manifestAssetPath = manifest.narrationManifest,
            sections = sections,
            currentPage = initialPages.getOrNull(savedPage),
            bookId = bookId,
            assetReaderOverride = repository.assetReaderOverride
        )

        _uiState.update {
            it.copy(
                activeBookId = bookId,
                activeBookTitleFa = bookTitleFa,
                activeContentRegionId = chapterId,
                activeChapterId = chapterId,
                activeChapterNumber = manifest.chapterNumber,
                activeChapterTitleFa = manifest.titleFa.ifBlank { chapterEntry.titleFa },
                activeChapterTitleEn = manifest.titleEn.ifBlank { chapterEntry.titleEn },
                availableChapters = availableChapters,
                frontMatterEntry = frontMatterEntry,
                frontMatterPack = frontMatterPack,
                frontMatterPages = frontMatterPages,
                chapterSourcePageIndex = chapterSourcePageIndex,
                activeSourcePdfPageFilter = restoredSourcePageEntry?.sourcePdfPage,
                lastReadFrontMatterSourcePdfPage = savedFmPos.sourcePdfPage,
                lastReadFrontMatterPageIndex = savedFmPageIdx,
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
                exerciseDefinitions = exerciseDefsMap,
                exerciseStates = exerciseStatesMap,
                narrationState = narrationManager.state.value,
                currentTab = targetTab
            )
        }
    }

    private fun saveCurrentReadingPosition(
        state: ChapterUiState,
        sectionIndex: Int,
        pageIndex: Int,
        pages: List<ReaderPage>
    ) {
        val safeSecIdx = sectionIndex.coerceIn(0, (state.sections.size - 1).coerceAtLeast(0))
        val safePageIdx = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val currentPage = pages.getOrNull(safePageIdx)
        val firstBlock = currentPage?.orderedBlocks?.firstOrNull()
        val firstBlockId = firstBlock?.blockId?.let { ReaderPaginator.originalBlockId(it) }
        val blockOwnerSectionId = if (firstBlockId != null) {
            state.sections.firstOrNull { sec ->
                sec.translationSlot.orderedContentBlocks.any { it.blockId == firstBlockId }
            }?.sectionId
        } else {
            null
        }
        val secId = blockOwnerSectionId
            ?: currentPage?.sectionId?.takeIf { it.isNotBlank() }
            ?: state.sections.getOrNull(safeSecIdx)?.sectionId
        val pageId = currentPage?.pageId
        val sourcePdfPage = currentPage?.sourcePdfPage
        if (!secId.isNullOrBlank()) {
            prefs.saveReadingPosition(
                bookId = state.activeBookId,
                chapterId = state.activeChapterId,
                sectionId = secId,
                sectionIndex = safeSecIdx,
                pageIndex = safePageIdx,
                pageId = pageId,
                sourcePdfPage = sourcePdfPage,
                blockId = firstBlockId
            )
        } else {
            prefs.saveReadingPosition(sectionIndex = safeSecIdx, pageIndex = safePageIdx)
        }
    }

    private fun saveCurrentFrontMatterPosition(
        state: ChapterUiState,
        pageIndex: Int,
        pages: List<ReaderPage>
    ) {
        if (pages.isEmpty()) return
        val safeIdx = pageIndex.coerceIn(0, pages.lastIndex)
        val page = pages[safeIdx]
        val pdfPage = (page.sourcePdfPage ?: 1).coerceIn(1, 39)
        prefs.saveFrontMatterReadingPosition(
            bookId = state.activeBookId,
            sourcePdfPage = pdfPage,
            pageIndex = safeIdx,
            pageId = page.pageId
        )
    }

    /**
     * Opens the Front Matter region (`FRONT_MATTER`, PDF pages 1..39) at the specified 1-based [initialSourcePdfPage].
     */
    @JvmOverloads
    fun openFrontMatter(initialSourcePdfPage: Int = 1) {
        narrationManager.stop()
        val clampedPdfPage = initialSourcePdfPage.coerceIn(1, 39)
        _uiState.update { state ->
            val pack = state.frontMatterPack
                ?: runCatching { repository.getFrontMatterContentPack(state.activeBookId) }.getOrNull()
                ?: return@update state
            val fmPages = state.frontMatterPages.ifEmpty {
                ReaderPaginator.paginateFrontMatterPages(
                    frontMatterPages = pack.pages,
                    fontScale = state.fontScale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            }
            val targetIdx = fmPages.indexOfFirst {
                it.sourcePdfPage == clampedPdfPage && it.sliceIndexInSourcePage == 1
            }.takeIf { it >= 0 }
                ?: fmPages.indexOfFirst { it.sourcePdfPage == clampedPdfPage }.takeIf { it >= 0 }
                ?: 0
            val activePdfPage = fmPages.getOrNull(targetIdx)?.sourcePdfPage ?: clampedPdfPage
            saveCurrentFrontMatterPosition(state, targetIdx, fmPages)
            state.copy(
                activeContentRegionId = ChapterRepository.FRONT_MATTER_REGION_ID,
                frontMatterPack = pack,
                frontMatterPages = fmPages,
                currentPages = fmPages,
                currentPageIndex = targetIdx,
                lastReadFrontMatterSourcePdfPage = activePdfPage,
                lastReadFrontMatterPageIndex = targetIdx,
                lastNavigationNoticeFa = null,
                currentTab = AppTab.READER
            )
        }
    }

    /**
     * Resumes reading the Front Matter region (`FRONT_MATTER`) from its last saved PDF page / slice index.
     */
    fun resumeFrontMatter() {
        narrationManager.stop()
        _uiState.update { state ->
            val pack = state.frontMatterPack
                ?: runCatching { repository.getFrontMatterContentPack(state.activeBookId) }.getOrNull()
                ?: return@update state
            val fmPages = state.frontMatterPages.ifEmpty {
                ReaderPaginator.paginateFrontMatterPages(
                    frontMatterPages = pack.pages,
                    fontScale = state.fontScale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            }
            val savedFm = prefs.getSavedFrontMatterReadingPosition(state.activeBookId)
            val targetIdx = when {
                !savedFm.pageId.isNullOrBlank() -> {
                    fmPages.indexOfFirst { it.pageId == savedFm.pageId }.takeIf { it >= 0 }
                }
                else -> null
            } ?: fmPages.indexOfFirst { it.sourcePdfPage == savedFm.sourcePdfPage }.takeIf { it >= 0 }
                ?: savedFm.pageIndex.coerceIn(0, (fmPages.size - 1).coerceAtLeast(0))

            val activePdfPage = fmPages.getOrNull(targetIdx)?.sourcePdfPage ?: savedFm.sourcePdfPage
            saveCurrentFrontMatterPosition(state, targetIdx, fmPages)
            state.copy(
                activeContentRegionId = ChapterRepository.FRONT_MATTER_REGION_ID,
                frontMatterPack = pack,
                frontMatterPages = fmPages,
                currentPages = fmPages,
                currentPageIndex = targetIdx,
                lastReadFrontMatterSourcePdfPage = activePdfPage,
                lastReadFrontMatterPageIndex = targetIdx,
                lastNavigationNoticeFa = null,
                currentTab = AppTab.READER
            )
        }
    }

    /**
     * Navigates directly to a 1-based physical [targetSourcePdfPage] across the book:
     * - Pages 1..39: opens Front Matter (`FRONT_MATTER`) at the first slice of [targetSourcePdfPage].
     * - Pages 40..50: uses `source_page_index.json` (`orderedBlockIds` and `sectionRefs`) to open
     *   the exact physical PDF page in Chapter 1 (`CH01`) without merging adjacent PDF pages.
     * - Pages > 50 (future chapters): sets a clear Persian notice without breaking current state.
     */
    @JvmOverloads
    fun navigateToSourcePdfPage(targetSourcePdfPage: Int, chapterIdHint: String? = null): Boolean {
        if (targetSourcePdfPage in 1..39) {
            openFrontMatter(initialSourcePdfPage = targetSourcePdfPage)
            return true
        }
        val state = _uiState.value
        val sourcePageIndex = state.chapterSourcePageIndex ?: runCatching {
            repository.getChapterSourcePageIndex(
                chapterId = chapterIdHint?.takeIf { it.isNotBlank() } ?: state.activeChapterId,
                bookId = state.activeBookId
            )
        }.getOrNull()

        val pageEntry = sourcePageIndex?.entryForPage(targetSourcePdfPage)
        if (pageEntry != null && state.sections.isNotEmpty()) {
            _uiState.update { st ->
                val primarySecId = pageEntry.sectionIds.firstOrNull()
                val matchingSectionIdx = st.sections.indexOfFirst { it.sectionId == primarySecId }
                    .takeIf { it >= 0 }
                    ?: st.sections.indexOfFirst { targetSourcePdfPage in it.pdfPages }
                        .takeIf { it >= 0 }
                    ?: 0
                val pages = ReaderPaginator.paginateChapterSourcePdfPage(
                    pageEntry = pageEntry,
                    sections = st.sections,
                    diagrams = st.diagrams,
                    fontScale = st.fontScale,
                    viewportWidthDp = st.viewportWidthDp,
                    viewportHeightDp = st.viewportHeightDp
                )
                val targetPageIdx = 0
                saveCurrentReadingPosition(st, matchingSectionIdx, targetPageIdx, pages)
                val clearedPgnStates = st.pgnStates.mapValues { (_, pgn) ->
                    pgn.copy(
                        activeTransition = null,
                        activeMoveTrail = null,
                        lastMoveDescriptionFa = null,
                        isTransitioning = false,
                        hasUserStartedReplay = false
                    )
                }
                val clearedExStates = st.exerciseStates.mapValues { (_, ex) ->
                    ex.copy(
                        activeTransition = null,
                        activeMoveTrail = null,
                        lastMoveDescriptionFa = null
                    )
                }
                st.copy(
                    activeContentRegionId = st.activeChapterId,
                    chapterSourcePageIndex = sourcePageIndex,
                    activeSourcePdfPageFilter = targetSourcePdfPage,
                    currentSectionIndex = matchingSectionIdx,
                    currentPages = pages,
                    currentPageIndex = targetPageIdx,
                    lastNavigationNoticeFa = null,
                    currentTab = AppTab.READER,
                    pgnStates = clearedPgnStates,
                    exerciseStates = clearedExStates
                )
            }
            val updated = _uiState.value
            narrationManager.onManualPageOrSectionChanged(updated.sections, updated.currentPage)
            return true
        }

        val matchingSectionIdx = state.sections.indexOfFirst { section ->
            targetSourcePdfPage in section.pdfPages ||
                section.translationSlot.orderedContentBlocks.any { it.sourcePdfPage == targetSourcePdfPage }
        }.takeIf { it >= 0 } ?: if (targetSourcePdfPage == 40 || chapterIdHint == "CH01") 0 else -1

        if (matchingSectionIdx >= 0) {
            _uiState.update { st ->
                val targetSection = st.sections[matchingSectionIdx]
                val pages = ReaderPaginator.paginateSection(
                    section = targetSection,
                    diagrams = st.diagrams,
                    fontScale = st.fontScale,
                    viewportWidthDp = st.viewportWidthDp,
                    viewportHeightDp = st.viewportHeightDp
                )
                val targetPageIdx = pages.indexOfFirst { it.sourcePdfPage == targetSourcePdfPage }
                    .takeIf { it >= 0 } ?: 0
                saveCurrentReadingPosition(st, matchingSectionIdx, targetPageIdx, pages)
                st.copy(
                    activeContentRegionId = st.activeChapterId,
                    activeSourcePdfPageFilter = null,
                    currentSectionIndex = matchingSectionIdx,
                    currentPages = pages,
                    currentPageIndex = targetPageIdx,
                    lastNavigationNoticeFa = null,
                    currentTab = AppTab.READER
                )
            }
            val updated = _uiState.value
            narrationManager.onManualPageOrSectionChanged(updated.sections, updated.currentPage)
            return true
        }

        _uiState.update { st ->
            st.copy(
                lastNavigationNoticeFa = "محتوا در حال آماده‌سازی — صفحه PDF شماره ${ReaderPaginator.toPersianDigits(targetSourcePdfPage)} مربوط به فصل‌های بعدی کتاب است که در بسته‌های محتوایی آتی بارگذاری می‌شود."
            )
        }
        return false
    }

    /**
     * Handles clicking a [FrontMatterSourceLink] (internal PDF page link or external URI).
     */
    fun openSourceLink(link: FrontMatterSourceLink): Boolean {
        val targetPage = link.targetSourcePdfPage
        if (targetPage != null) {
            return navigateToSourcePdfPage(targetSourcePdfPage = targetPage, chapterIdHint = link.chapterId)
        }
        val uri = link.targetUri?.trim().orEmpty()
        if (uri.isNotEmpty()) {
            if (uri.startsWith("clbr://", ignoreCase = true) && uri.contains("cover", ignoreCase = true)) {
                return navigateToSourcePdfPage(1)
            }
            return openExternalUri(uri)
        }
        return false
    }

    fun openExternalUri(uri: String): Boolean {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return false
        _uiState.update {
            it.copy(
                lastOpenedExternalUri = trimmed,
                lastNavigationNoticeFa = "پیوند خارجی: $trimmed"
            )
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
        }
        return true
    }

    fun dismissNavigationNotice() {
        _uiState.update { it.copy(lastNavigationNoticeFa = null) }
    }

    /**
     * Enters a section of the active chapter and always opens its first page (page index 0).
     */
    fun selectSection(index: Int) {
        val stateSnapshot = _uiState.value
        val target = index.coerceIn(0, (stateSnapshot.sections.size - 1).coerceAtLeast(0))
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
            saveCurrentReadingPosition(state, target, 0, pages)
            // Clear any transient animations, move trails, and persistent move descriptions when switching sections
            val clearedPgnStates = state.pgnStates.mapValues { (_, pgn) ->
                pgn.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null,
                    isTransitioning = false,
                    hasUserStartedReplay = false
                )
            }
            val clearedExStates = state.exerciseStates.mapValues { (_, ex) ->
                ex.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            }
            state.copy(
                activeContentRegionId = state.activeChapterId,
                activeSourcePdfPageFilter = null,
                currentSectionIndex = target,
                currentPages = pages,
                currentPageIndex = 0,
                lastNavigationNoticeFa = null,
                currentTab = AppTab.READER,
                pgnStates = clearedPgnStates,
                exerciseStates = clearedExStates
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
            val pages = if (!state.isReadingFrontMatter && state.currentPages.isNotEmpty()) {
                state.currentPages
            } else if (targetSection != null) {
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
            val savedPos = prefs.getSavedReadingPosition(chapterId = state.activeChapterId, bookId = state.activeBookId)
            val basePageIdx = if (state.isReadingFrontMatter) savedPos.pageIndex else state.currentPageIndex
            val savedPageIdx = basePageIdx.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            saveCurrentReadingPosition(state, targetSectionIdx, savedPageIdx, pages)
            val clearedPgnStates = state.pgnStates.mapValues { (_, pgn) ->
                pgn.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null,
                    isTransitioning = false,
                    hasUserStartedReplay = false
                )
            }
            val clearedExStates = state.exerciseStates.mapValues { (_, ex) ->
                ex.copy(
                    activeTransition = null,
                    activeMoveTrail = null,
                    lastMoveDescriptionFa = null
                )
            }
            state.copy(
                activeContentRegionId = state.activeChapterId,
                currentSectionIndex = targetSectionIdx,
                currentPages = pages,
                currentPageIndex = savedPageIdx,
                lastNavigationNoticeFa = null,
                currentTab = AppTab.READER,
                pgnStates = clearedPgnStates,
                exerciseStates = clearedExStates
            )
        }
        val updated = _uiState.value
        narrationManager.refreshAvailability(updated.sections, updated.currentPage)
    }

    fun nextSection() {
        val current = _uiState.value
        if (current.isReadingFrontMatter) {
            // Transition from Front Matter (after PDF page 39) to Chapter 1 Section 0 (PDF page 40)
            selectSection(0)
            return
        }
        if (current.currentSectionIndex < current.sections.size - 1) {
            selectSection(current.currentSectionIndex + 1)
        }
    }

    fun prevSection() {
        val current = _uiState.value
        if (current.isReadingFrontMatter) {
            return
        }
        if (current.currentSectionIndex > 0) {
            selectSection(current.currentSectionIndex - 1)
        }
    }

    /**
     * Navigates to a specific 0-based page index within the current region/section and persists the reading position.
     */
    fun goToPage(pageIndex: Int) {
        val currentState = _uiState.value
        val maxIdx = (currentState.currentPages.size - 1).coerceAtLeast(0)
        val targetPage = pageIndex.coerceIn(0, maxIdx)
        val pageChanged = targetPage != currentState.currentPageIndex
        if (currentState.isReadingFrontMatter) {
            saveCurrentFrontMatterPosition(currentState, targetPage, currentState.currentPages)
            val activePdfPage = currentState.currentPages.getOrNull(targetPage)?.sourcePdfPage
                ?: currentState.lastReadFrontMatterSourcePdfPage
            _uiState.update { state ->
                state.copy(
                    currentPageIndex = targetPage,
                    lastReadFrontMatterSourcePdfPage = activePdfPage,
                    lastReadFrontMatterPageIndex = targetPage
                )
            }
            return
        }

        saveCurrentReadingPosition(currentState, currentState.currentSectionIndex, targetPage, currentState.currentPages)
        _uiState.update { state ->
            val updatedPgnStates = if (pageChanged) {
                state.pgnStates.mapValues { (_, pgn) ->
                    pgn.copy(
                        activeTransition = null,
                        activeMoveTrail = null,
                        lastMoveDescriptionFa = null,
                        isTransitioning = false,
                        hasUserStartedReplay = false
                    )
                }
            } else {
                state.pgnStates
            }
            val updatedExStates = if (pageChanged) {
                state.exerciseStates.mapValues { (_, ex) ->
                    ex.copy(
                        activeTransition = null,
                        activeMoveTrail = null,
                        lastMoveDescriptionFa = null
                    )
                }
            } else {
                state.exerciseStates
            }
            state.copy(
                currentPageIndex = targetPage,
                pgnStates = updatedPgnStates,
                exerciseStates = updatedExStates
            )
        }
        if (pageChanged) {
            val updated = _uiState.value
            narrationManager.onManualPageOrSectionChanged(updated.sections, updated.currentPage)
        }
    }

    /**
     * Auto-navigates to the page containing [blockId] inside [sectionIndex] during Mode B
     * (Continuous Chapter narration) without interrupting active narration playback.
     */
    private fun navigateToSectionAndBlockForNarration(sectionIndex: Int, blockId: String) {
        val normalizedBlockId = ReaderPaginator.originalBlockId(blockId)
        _uiState.update { state ->
            val targetSecIdx = sectionIndex.coerceIn(0, (state.sections.size - 1).coerceAtLeast(0))
            val targetSection = state.sections.getOrNull(targetSecIdx) ?: return@update state
            val pages = if (!state.isReadingFrontMatter && targetSecIdx == state.currentSectionIndex && state.currentPages.isNotEmpty()) {
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
            saveCurrentReadingPosition(state, targetSecIdx, pageIdx, pages)
            state.copy(
                activeContentRegionId = state.activeChapterId,
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
        if (state.isReadingFrontMatter) {
            // At the very last slice of Front Matter (PDF page 39), advancing transitions to CH01 (PDF page 40)
            selectSection(0)
            return true
        }
        val activePdfFilter = state.activeSourcePdfPageFilter
        if (activePdfFilter != null) {
            val nextEntry = state.chapterSourcePageIndex?.entryForPage(activePdfFilter + 1)
            if (nextEntry != null) {
                return navigateToSourcePdfPage(activePdfFilter + 1)
            }
        }
        return false
    }

    fun prevPage(): Boolean {
        val state = _uiState.value
        if (state.currentPageIndex > 0) {
            goToPage(state.currentPageIndex - 1)
            return true
        }
        val activePdfFilter = state.activeSourcePdfPageFilter
        if (activePdfFilter != null) {
            val prevEntry = state.chapterSourcePageIndex?.entryForPage(activePdfFilter - 1)
            if (prevEntry != null) {
                return navigateToSourcePdfPage(activePdfFilter - 1)
            }
        }
        return false
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun openIntroduction() {
        _uiState.update { it.copy(currentTab = AppTab.INTRODUCTION) }
    }

    fun openBookFromIntroduction() {
        val s = _uiState.value
        if (s.hasMultipleChapters) {
            _uiState.update { it.copy(currentTab = AppTab.CHAPTERS_LIST) }
        } else {
            openSectionsList()
        }
    }

    fun openChaptersList() {
        narrationManager.stop()
        _uiState.update { it.copy(currentTab = AppTab.CHAPTERS_LIST) }
    }

    fun openSectionsList() {
        _uiState.update { it.copy(currentTab = AppTab.SECTIONS_LIST) }
    }

    fun selectChapter(chapterId: String, navigateToSectionsList: Boolean = true) {
        val s = _uiState.value
        val chapterEntry = s.availableChapters.find { it.chapterId == chapterId } ?: return
        prefs.activeBookId = s.activeBookId
        prefs.activeChapterId = chapterId
        loadChapterInternal(
            bookId = s.activeBookId,
            bookTitleFa = s.activeBookTitleFa,
            chapterEntry = chapterEntry,
            availableChapters = s.availableChapters,
            targetTab = if (navigateToSectionsList) AppTab.SECTIONS_LIST else s.currentTab
        )
    }

    private fun findPreservedFrontMatterPageIndex(
        oldPages: List<ReaderPage>,
        oldPageIndex: Int,
        newPages: List<ReaderPage>
    ): Int {
        if (newPages.isEmpty()) return 0
        val oldPage = oldPages.getOrNull(oldPageIndex)
            ?: return oldPageIndex.coerceIn(0, newPages.lastIndex)
        val targetSourcePdfPage = oldPage.sourcePdfPage

        if (targetSourcePdfPage != null) {
            val newSlicesForPdfPage = newPages.indices.filter { idx ->
                newPages[idx].sourcePdfPage == targetSourcePdfPage
            }
            if (newSlicesForPdfPage.isNotEmpty()) {
                val anchorBlock = oldPage.orderedBlocks.firstOrNull()
                if (anchorBlock != null) {
                    val origId = ReaderPaginator.originalBlockId(anchorBlock.blockId)
                    val blockMatch = newSlicesForPdfPage.firstOrNull { idx ->
                        newPages[idx].orderedBlocks.any {
                            ReaderPaginator.originalBlockId(it.blockId) == origId
                        }
                    }
                    if (blockMatch != null) return blockMatch
                }
                val sliceIdx = (oldPage.sliceIndexInSourcePage - 1).coerceIn(0, newSlicesForPdfPage.lastIndex)
                return newSlicesForPdfPage[sliceIdx]
            }
        }
        return oldPageIndex.coerceIn(0, newPages.lastIndex)
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

        // 2. Match the first content block on the page (preferring non-heading content if a heading precedes a paragraph)
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
            val updatedFmPages = state.frontMatterPack?.let { pack ->
                ReaderPaginator.paginateFrontMatterPages(
                    frontMatterPages = pack.pages,
                    fontScale = scale,
                    viewportWidthDp = state.viewportWidthDp,
                    viewportHeightDp = state.viewportHeightDp
                )
            } ?: state.frontMatterPages

            if (state.isReadingFrontMatter) {
                val mappedFmIndex = findPreservedFrontMatterPageIndex(
                    oldPages = state.currentPages,
                    oldPageIndex = state.currentPageIndex,
                    newPages = updatedFmPages
                )
                val activePdfPage = updatedFmPages.getOrNull(mappedFmIndex)?.sourcePdfPage
                    ?: state.lastReadFrontMatterSourcePdfPage
                saveCurrentFrontMatterPosition(state, mappedFmIndex, updatedFmPages)
                state.copy(
                    fontScale = scale,
                    frontMatterPages = updatedFmPages,
                    currentPages = updatedFmPages,
                    currentPageIndex = mappedFmIndex,
                    lastReadFrontMatterSourcePdfPage = activePdfPage,
                    lastReadFrontMatterPageIndex = mappedFmIndex
                )
            } else {
                val activePdfFilter = state.activeSourcePdfPageFilter
                val filterEntry = activePdfFilter?.let { state.chapterSourcePageIndex?.entryForPage(it) }
                val currentSection = state.sections.getOrNull(state.currentSectionIndex)
                val newPages = if (filterEntry != null && state.sections.isNotEmpty()) {
                    ReaderPaginator.paginateChapterSourcePdfPage(
                        pageEntry = filterEntry,
                        sections = state.sections,
                        diagrams = state.diagrams,
                        fontScale = scale,
                        viewportWidthDp = state.viewportWidthDp,
                        viewportHeightDp = state.viewportHeightDp
                    )
                } else if (currentSection != null) {
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
                saveCurrentReadingPosition(state, state.currentSectionIndex, mappedPageIndex, newPages)
                state.copy(
                    fontScale = scale,
                    frontMatterPages = updatedFmPages,
                    currentPages = newPages,
                    currentPageIndex = mappedPageIndex
                )
            }
        }
        val updated = _uiState.value
        if (!updated.isReadingFrontMatter) {
            narrationManager.refreshAvailability(updated.sections, updated.currentPage)
        }
    }

    fun updateViewportSize(widthDp: Int, heightDp: Int) {
        val clampedWidth = widthDp.coerceAtLeast(200)
        val clampedHeight = heightDp.coerceAtLeast(200)
        val current = _uiState.value
        if (current.viewportWidthDp == clampedWidth && current.viewportHeightDp == clampedHeight) return
        _uiState.update { state ->
            val updatedFmPages = state.frontMatterPack?.let { pack ->
                ReaderPaginator.paginateFrontMatterPages(
                    frontMatterPages = pack.pages,
                    fontScale = state.fontScale,
                    viewportWidthDp = clampedWidth,
                    viewportHeightDp = clampedHeight
                )
            } ?: state.frontMatterPages

            if (state.isReadingFrontMatter) {
                val mappedFmIndex = findPreservedFrontMatterPageIndex(
                    oldPages = state.currentPages,
                    oldPageIndex = state.currentPageIndex,
                    newPages = updatedFmPages
                )
                val activePdfPage = updatedFmPages.getOrNull(mappedFmIndex)?.sourcePdfPage
                    ?: state.lastReadFrontMatterSourcePdfPage
                saveCurrentFrontMatterPosition(state, mappedFmIndex, updatedFmPages)
                state.copy(
                    viewportWidthDp = clampedWidth,
                    viewportHeightDp = clampedHeight,
                    frontMatterPages = updatedFmPages,
                    currentPages = updatedFmPages,
                    currentPageIndex = mappedFmIndex,
                    lastReadFrontMatterSourcePdfPage = activePdfPage,
                    lastReadFrontMatterPageIndex = mappedFmIndex
                )
            } else {
                val activePdfFilter = state.activeSourcePdfPageFilter
                val filterEntry = activePdfFilter?.let { state.chapterSourcePageIndex?.entryForPage(it) }
                val currentSection = state.sections.getOrNull(state.currentSectionIndex)
                val newPages = if (filterEntry != null && state.sections.isNotEmpty()) {
                    ReaderPaginator.paginateChapterSourcePdfPage(
                        pageEntry = filterEntry,
                        sections = state.sections,
                        diagrams = state.diagrams,
                        fontScale = state.fontScale,
                        viewportWidthDp = clampedWidth,
                        viewportHeightDp = clampedHeight
                    )
                } else if (currentSection != null) {
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
                saveCurrentReadingPosition(state, state.currentSectionIndex, mappedPageIndex, newPages)
                state.copy(
                    viewportWidthDp = clampedWidth,
                    viewportHeightDp = clampedHeight,
                    frontMatterPages = updatedFmPages,
                    currentPages = newPages,
                    currentPageIndex = mappedPageIndex
                )
            }
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
            if (state.isReadingFrontMatter) {
                state.copy(sections = updatedSections)
            } else {
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
        }
        val updated = _uiState.value
        if (!updated.isReadingFrontMatter) {
            narrationManager.refreshAvailability(updated.sections, updated.currentPage)
        }
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
        lastMoveDescriptionFa: String? = null,
        hasUserStartedReplay: Boolean = true
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
                isTransitioning = transition != null,
                hasUserStartedReplay = hasUserStartedReplay
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
            val updatedPgn = if (!enabled) {
                state.pgnStates.mapValues { (_, pgn) -> pgn.copy(activeMoveTrail = null) }
            } else {
                state.pgnStates
            }
            val updatedEx = if (!enabled) {
                state.exerciseStates.mapValues { (_, ex) -> ex.copy(activeMoveTrail = null) }
            } else {
                state.exerciseStates
            }
            state.copy(
                showMoveTrail = enabled,
                pgnStates = updatedPgn,
                exerciseStates = updatedEx
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
    // Generalized Exercise Engine Dispatch
    // ==========================================

    private fun resolveExerciseContext(
        state: ChapterUiState,
        exerciseId: String
    ): Triple<ExerciseDefinition, DiagramItem, ExerciseUiState>? {
        val exDef = state.exerciseDefinitions[exerciseId] ?: return null
        val diagram = state.diagrams.find { it.diagramId == exDef.diagramId } ?: return null
        val currentExState = state.exerciseStates[exerciseId] ?: return null
        return Triple(exDef, diagram, currentExState)
    }

    private fun applyExerciseStateUpdate(exerciseId: String, updatedExState: ExerciseUiState) {
        _uiState.update { state ->
            val updatedMap = state.exerciseStates + (exerciseId to updatedExState)
            state.copy(exerciseStates = updatedMap)
        }
    }

    fun toggleExerciseLegalMovesHint(exerciseId: String) {
        val current = _uiState.value
        val (exDef, _, exState) = resolveExerciseContext(current, exerciseId) ?: return
        val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: return
        val updated = handler.onToggleLegalMovesHint(exState)
        applyExerciseStateUpdate(exerciseId, updated)
    }

    fun onExerciseSquareClicked(exerciseId: String, file: Int, rank: Int) {
        val current = _uiState.value
        val (exDef, diagram, exState) = resolveExerciseContext(current, exerciseId) ?: return
        val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: return
        val adapter = exerciseAdapters.getOrPut(exerciseId) {
            handler.createRulesAdapter(exDef, diagram)
        }
        val outcome = handler.onSquareClicked(
            definition = exDef,
            diagram = diagram,
            pgnSequences = loadedPgnSequences,
            currentState = exState,
            adapter = adapter,
            file = file,
            rank = rank,
            showMoveTrail = current.showMoveTrail,
            playbackSpeed = current.playbackSpeed,
            nextTransitionNumber = { transitionCounter.getAndIncrement() }
        )
        if (outcome.newlySolved) {
            prefs.setExerciseSolved(exerciseId, true)
        }
        applyExerciseStateUpdate(exerciseId, outcome.updatedState)
        outcome.soundToEmit?.let { (type, tag) -> emitSound(type, tag) }
    }

    fun onExerciseTransitionFinished(exerciseId: String, transitionId: String) {
        val current = _uiState.value
        val (exDef, _, exState) = resolveExerciseContext(current, exerciseId) ?: return
        val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: return
        val outcome = handler.onTransitionFinished(exDef, exState, transitionId)
        applyExerciseStateUpdate(exerciseId, outcome.updatedState)
        outcome.soundToEmit?.let { (type, tag) -> emitSound(type, tag) }
    }

    fun onExerciseMoveTrailFinished(exerciseId: String, eventId: String) {
        val current = _uiState.value
        val (exDef, _, exState) = resolveExerciseContext(current, exerciseId) ?: return
        val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: return
        val updated = handler.onMoveTrailFinished(exState, eventId)
        applyExerciseStateUpdate(exerciseId, updated)
    }

    fun retryExercise(exerciseId: String) {
        val current = _uiState.value
        val (exDef, diagram, _) = resolveExerciseContext(current, exerciseId) ?: return
        val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: return
        val adapter = exerciseAdapters.getOrPut(exerciseId) {
            handler.createRulesAdapter(exDef, diagram)
        }
        val outcome = handler.onRetry(exDef, diagram, adapter)
        applyExerciseStateUpdate(exerciseId, outcome.updatedState)
        outcome.soundToEmit?.let { (type, tag) -> emitSound(type, tag) }
    }

    fun resetExercise(exerciseId: String) {
        val current = _uiState.value
        val (exDef, diagram, _) = resolveExerciseContext(current, exerciseId) ?: return
        val handler = ExerciseHandlerRegistry.getHandler(exDef.handlerType) ?: return
        val adapter = exerciseAdapters.getOrPut(exerciseId) {
            handler.createRulesAdapter(exDef, diagram)
        }
        val outcome = handler.onReset(exDef, diagram, adapter)
        if (outcome.resetSolvedPreference) {
            prefs.setExerciseSolved(exerciseId, false)
        }
        applyExerciseStateUpdate(exerciseId, outcome.updatedState)
        outcome.soundToEmit?.let { (type, tag) -> emitSound(type, tag) }
    }

    // --- Legacy BD-032 Exercise Wrappers (for backward test compatibility only) ---

    @Deprecated("Use toggleExerciseLegalMovesHint(exerciseId) instead")
    fun toggleBd032LegalMovesHint() {
        toggleExerciseLegalMovesHint(UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID)
    }

    @Deprecated("Use onExerciseSquareClicked(exerciseId, file, rank) instead")
    fun onBd032SquareClicked(file: Int, rank: Int) {
        onExerciseSquareClicked(UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID, file, rank)
    }

    @Deprecated("Use onExerciseTransitionFinished(exerciseId, transitionId) instead")
    fun onBd032TransitionFinished(transitionId: String) {
        onExerciseTransitionFinished(UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID, transitionId)
    }

    @Deprecated("Use onExerciseMoveTrailFinished(exerciseId, eventId) instead")
    fun onBd032MoveTrailFinished(eventId: String) {
        onExerciseMoveTrailFinished(UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID, eventId)
    }

    @Deprecated("Use retryExercise(exerciseId) instead")
    fun retryBd032() {
        retryExercise(UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID)
    }

    @Deprecated("Use resetExercise(exerciseId) instead")
    fun resetBd032() {
        resetExercise(UserPreferencesManager.DEFAULT_CH01_EXERCISE_ID)
    }

    public override fun onCleared() {
        super.onCleared()
        soundManager.release()
        narrationManager.release()
    }
}
