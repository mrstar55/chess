package com.example

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.chess.ChessEngine
import com.example.data.ChapterRepository
import com.example.model.AuditStatus
import com.example.model.ExercisePhase
import com.example.model.PieceType
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.model.TranslationStatus
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.components.PieceMovementDiagram
import com.example.ui.components.PieceMovementRules
import com.example.ui.components.StartingPositionIntroBoard
import com.example.ui.screens.BookTocNavigationHelper
import com.example.ui.screens.ChaptersListScreen
import com.example.ui.screens.DiagramCard
import com.example.ui.screens.SectionsListScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Version22EditorialAndVisualTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var repository: ChapterRepository
    private lateinit var viewModel: ChapterViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        repository = ChapterRepository(app)
        viewModel = ChapterViewModel(app)
    }

    @Test
    fun test01_frontMatterUnifiedInChaptersList() {
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ChaptersListScreen(
                    state = state,
                    onSelectChapter = { viewModel.selectChapter(it) },
                    onBackToIntroduction = {},
                    onStartFrontMatter = { viewModel.openFrontMatter(it) },
                    onResumeFrontMatter = { viewModel.resumeFrontMatter() },
                    onNavigateToPdfPage = { page, chId -> viewModel.navigateToSourcePdfPage(page, chId) }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify unified Front Matter card exists as the first item in the main list without a fake chapter number
        composeTestRule.onNodeWithTag("front_matter_region_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("front_matter_nav_title_text", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("front_matter_page_range_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("پیش‌درآمد", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("صفحات PDF ۱۱ تا ۳۶", useUnmergedTree = true).assertIsDisplayed()

        // Verify Part One subtle divider (pages 37-38) between Front Matter and Chapter 1
        composeTestRule.onNodeWithTag("toc_part_divider_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("بخش اول — سطح مقدماتی", useUnmergedTree = true).assertIsDisplayed()

        // Ensure no duplicate standalone banner card exists
        assertEquals(
            1,
            composeTestRule.onAllNodesWithTag("front_matter_region_card").fetchSemanticsNodes().size
        )

        // Clicking the unified Front Matter row navigates directly to PDF page 11
        composeTestRule.onNodeWithTag("front_matter_region_card").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(11, viewModel.uiState.value.currentPage?.sourcePdfPage)
    }

    @Test
    fun test02_bookTableOfContentsCompleteWith15ChaptersAndFrontMatterTopics() {
        val fmPack = viewModel.uiState.value.frontMatterPack
        assertNotNull(fmPack)
        val toc = fmPack!!.tableOfContents
        assertNotNull(toc)
        val entries = toc!!.entries

        // Verify the 5 Front Matter subtopics from table_of_contents.json in exact order (pages 11-36)
        val fmTopics = BookTocNavigationHelper.extractFrontMatterTopics(entries)
        assertEquals(5, fmTopics.size)
        val expectedFmSubtopics = listOf(
            "مبانی شطرنج" to 11,
            "نمادگذاری شطرنج" to 31,
            "امتیازدهی" to 35,
            "تمرین شطرنج" to 36,
            "مطالب تکمیلی برای یادگیری" to 36
        )
        assertEquals(expectedFmSubtopics, fmTopics.map { it.labelFa to it.targetSourcePdfPage })

        // Verify all 15 chapters CH01..CH15 with exact authoritative page ranges
        val chapterGroups = BookTocNavigationHelper.extractChapterGroups(entries)
        assertEquals(15, chapterGroups.size)
        val expectedRanges = mapOf(
            "CH01" to (39 to 50),
            "CH02" to (51 to 85),
            "CH03" to (86 to 105),
            "CH04" to (106 to 134),
            "CH05" to (135 to 164),
            "CH06" to (165 to 184),
            "CH07" to (185 to 231),
            "CH08" to (232 to 260),
            "CH09" to (263 to 277),
            "CH10" to (278 to 302),
            "CH11" to (303 to 330),
            "CH12" to (331 to 376),
            "CH13" to (377 to 433),
            "CH14" to (434 to 474),
            "CH15" to (475 to 570)
        )
        for (i in 1..15) {
            val chId = "CH%02d".format(i)
            val group = chapterGroups.find { it.chapterId == chId }
            assertNotNull("Expected $chId in table_of_contents.json", group)
            assertEquals(i, group!!.chapterNumber)
            val expectedRange = expectedRanges[chId]!!
            assertEquals("Start page mismatch for $chId", expectedRange.first, group.startPdfPage)
            assertEquals("End page mismatch for $chId", expectedRange.second, group.endPdfPage)
        }
    }

    @Test
    fun test03_tocNavigationForPages1To50AndPreparationNoticeForFutureChapters() {
        // Navigating to Chapter 1 row (PDF page 39) opens page 39 (Rook chapter opener)
        viewModel.navigateToSourcePdfPage(39, "CH01")
        assertTrue(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(39, viewModel.uiState.value.currentPage?.sourcePdfPage)

        // Advancing from page 39 transitions to PDF page 40 in CH01
        assertTrue(viewModel.nextPage())
        assertFalse(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(40, viewModel.uiState.value.currentPage?.sourcePdfPage)

        // Navigating to each Chapter 1 subtopic opens its exact PDF page (40, 45, 46, 48, 50)
        val expectedCh01SubtopicPages = listOf(
            "کیش‌ومات" to 40,
            "تسلیم" to 45,
            "باخت بر اثر اتمام زمان" to 46,
            "ترک بازی" to 48,
            "خلاصه" to 50
        )
        val ch01Subtopics = BookTocNavigationHelper.extractChapterGroups(
            viewModel.uiState.value.frontMatterPack!!.tableOfContents!!.entries
        ).first { it.chapterId == "CH01" }.subtopics
        assertEquals(
            expectedCh01SubtopicPages,
            ch01Subtopics.map { it.labelFa to it.targetSourcePdfPage }
        )
        for ((_, targetPage) in expectedCh01SubtopicPages) {
            assertTrue(viewModel.navigateToSourcePdfPage(targetPage, "CH01"))
            assertFalse(viewModel.uiState.value.isReadingFrontMatter)
            assertEquals(targetPage, viewModel.uiState.value.currentPage?.sourcePdfPage)
        }

        // Navigating to unready chapters (CH02..CH15) shows preparation notice and does NOT enter CH01
        viewModel.openChaptersList()
        for (chNum in 2..15) {
            val chId = "CH%02d".format(chNum)
            val startPage = BookTocNavigationHelper.AUTHORITATIVE_CHAPTER_RANGES[chId]!!.first
            val navigated = viewModel.navigateToSourcePdfPage(startPage, chId)
            assertFalse("Unready chapter $chId must not navigate to Reader", navigated)
            assertEquals("Tab must remain CHAPTERS_LIST for $chId", AppTab.CHAPTERS_LIST, viewModel.uiState.value.currentTab)
            val notice = viewModel.uiState.value.lastNavigationNoticeFa
            assertNotNull(notice)
            assertTrue(
                "Notice should mention محتوا در حال آماده‌سازی, got: $notice",
                notice!!.contains("محتوا در حال آماده‌سازی")
            )
        }
    }

    @Test
    fun test04_pieceMovementDiagramsOnPages14To17MatchActualRules() {
        // Queen on d4 (row 4, col 3): 27 target squares in 8 directions
        val queenTargets = PieceMovementRules.computeTargetSquares(PieceType.QUEEN, 4, 3)
        assertEquals(27, queenTargets.size)
        assertTrue(queenTargets.contains(0 to 3)) // d8
        assertTrue(queenTargets.contains(7 to 3)) // d1
        assertTrue(queenTargets.contains(4 to 0)) // a4
        assertTrue(queenTargets.contains(4 to 7)) // h4
        assertTrue(queenTargets.contains(1 to 0)) // a7
        assertTrue(queenTargets.contains(0 to 7)) // h8

        // Rook on d4 (row 4, col 3): 14 target squares in 4 orthogonal directions only (no diagonals)
        val rookTargets = PieceMovementRules.computeTargetSquares(PieceType.ROOK, 4, 3)
        assertEquals(14, rookTargets.size)
        assertTrue(rookTargets.all { (r, c) -> r == 4 || c == 3 })

        // Bishop on d4 (row 4, col 3): 13 target squares in 4 diagonal directions only (no orthogonals)
        val bishopTargets = PieceMovementRules.computeTargetSquares(PieceType.BISHOP, 4, 3)
        assertEquals(13, bishopTargets.size)
        assertTrue(bishopTargets.all { (r, c) -> abs(r - 4) == abs(c - 3) })

        // Knight on d4 (row 4, col 3): 8 L-jump target squares (c6, e6, b5, f5, b3, f3, c2, e2)
        val knightTargets = PieceMovementRules.computeTargetSquares(PieceType.KNIGHT, 4, 3)
        assertEquals(8, knightTargets.size)
        val expectedKnightAlgebraic = setOf("c6", "e6", "b5", "f5", "b3", "f3", "c2", "e2")
        val actualKnightAlgebraic = knightTargets.map { (r, c) -> "${'a' + c}${8 - r}" }.toSet()
        assertEquals(expectedKnightAlgebraic, actualKnightAlgebraic)

        // Verify PieceMovementDiagram renders cleanly in Compose for ROOK
        composeTestRule.setContent {
            MyApplicationTheme {
                PieceMovementDiagram(pieceType = PieceType.ROOK, isReducedMotion = true)
            }
        }
        composeTestRule.onNodeWithTag("piece_movement_diagram_ROOK").assertIsDisplayed()
    }

    @Test
    fun test05_startingPositionIntroBoardOnPage14IntroducesAll6PieceTypesWithoutMoving() {
        val lastPlayedKey = mutableListOf<String>()

        composeTestRule.setContent {
            MyApplicationTheme {
                StartingPositionIntroBoard(
                    onPlayPieceAudio = { key, _ -> lastPlayedKey.add(key) }
                )
            }
        }

        composeTestRule.onNodeWithTag("starting_position_intro_board").assertIsDisplayed()
        composeTestRule.onNodeWithTag("starting_position_instruction").assertIsDisplayed()

        // Tap White Queen on d1
        composeTestRule.onNodeWithTag("square_d1").performClick()
        composeTestRule.onNodeWithTag("starting_position_selected_piece_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("piece_intro_narration_key_PIECE_QUEEN_NAME").assertIsDisplayed()
        assertTrue(lastPlayedKey.contains("PIECE_QUEEN_NAME"))

        // Tap White King on e1
        composeTestRule.onNodeWithTag("square_e1").performClick()
        composeTestRule.onNodeWithTag("piece_intro_narration_key_PIECE_KING_NAME").assertIsDisplayed()
        assertTrue(lastPlayedKey.contains("PIECE_KING_NAME"))

        // Tap White Rook on a1
        composeTestRule.onNodeWithTag("square_a1").performClick()
        composeTestRule.onNodeWithTag("piece_intro_narration_key_PIECE_ROOK_NAME").assertIsDisplayed()
        assertTrue(lastPlayedKey.contains("PIECE_ROOK_NAME"))

        // Tap White Bishop on c1
        composeTestRule.onNodeWithTag("square_c1").performClick()
        composeTestRule.onNodeWithTag("piece_intro_narration_key_PIECE_BISHOP_NAME").assertIsDisplayed()
        assertTrue(lastPlayedKey.contains("PIECE_BISHOP_NAME"))

        // Tap White Knight on b1
        composeTestRule.onNodeWithTag("square_b1").performClick()
        composeTestRule.onNodeWithTag("piece_intro_narration_key_PIECE_KNIGHT_NAME").assertIsDisplayed()
        assertTrue(lastPlayedKey.contains("PIECE_KNIGHT_NAME"))

        // Tap White Pawn on e2
        composeTestRule.onNodeWithTag("square_e2").performClick()
        composeTestRule.onNodeWithTag("piece_intro_narration_key_PIECE_PAWN_NAME").assertIsDisplayed()
        assertTrue(lastPlayedKey.contains("PIECE_PAWN_NAME"))
    }

    @Test
    fun test06_bd027CorrectedToValidCheckmateOnPage42() {
        val allDiagrams = viewModel.uiState.value.diagrams
        val bd027 = allDiagrams.find { it.diagramId == "BD-027" }
        assertNotNull(bd027)
        assertEquals("7k/6B1/6KN/8/8/8/8/8 b - - 0 1", bd027!!.initialFen)
        assertEquals(AuditStatus.VALID, bd027.auditStatus)
        assertNull(bd027.auditWarning)
        assertNotNull(bd027.digitalReconstructionNoteFa)

        val engine = ChessEngine(bd027.initialFen)
        assertTrue("Black king on h8 must be in check from Bg7", engine.isInCheck())
        assertTrue("Black king on h8 must be in checkmate (g8 covered by Bg7+Kg6, h7 covered by Kg6+Nh6)", engine.isCheckmate())

        // Verify all 10 diagrams in Chapter 1 are now VALID and 0 are BLOCKED
        assertEquals(10, allDiagrams.size)
        assertEquals(10, allDiagrams.count { it.auditStatus == AuditStatus.VALID })
        assertEquals(0, allDiagrams.count { it.auditStatus == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW })
    }

    @Test
    fun test07_compactAuditCheckReplacesFullWidthGreenBanner() {
        val state = viewModel.uiState.value
        val bd025 = state.diagrams.first { it.diagramId == "BD-025" }

        composeTestRule.setContent {
            MyApplicationTheme {
                DiagramCard(
                    diagram = bd025,
                    state = state,
                    viewModel = viewModel,
                    fontMultiplier = 1f
                )
            }
        }

        // Full-width green banner text must NOT exist
        assertEquals(0, composeTestRule.onAllNodesWithText("تأیید ممیزی قوانین (VALID)").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("audit_badge_VALID").fetchSemanticsNodes().size)

        // Compact header check icon MUST exist
        composeTestRule.onNodeWithTag("audit_valid_compact_icon").assertIsDisplayed()
    }

    @Test
    fun test08_bd032BoardMatchesStandardDiagramBoardSize() {
        val state = viewModel.uiState.value
        val bd025 = state.diagrams.first { it.diagramId == "BD-025" }
        val bd032 = state.diagrams.first { it.diagramId == "BD-032" }
        val showBd032 = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    if (!showBd032.value) {
                        DiagramCard(
                            diagram = bd025,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = 1f
                        )
                    } else {
                        DiagramCard(
                            diagram = bd032,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = 1f,
                            exerciseId = "EX_CH01_BD032"
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        val standardBounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        val standardWidthPx = standardBounds.width
        val standardHeightPx = standardBounds.height

        showBd032.value = true
        composeTestRule.waitForIdle()
        val bd032Bounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        val bd032WidthPx = bd032Bounds.width
        val bd032HeightPx = bd032Bounds.height

        assertTrue(
            "BD-032 width ($bd032WidthPx) must match standard diagram width ($standardWidthPx) within 2px",
            abs(bd032WidthPx - standardWidthPx) <= 2f
        )
        assertTrue(
            "BD-032 height ($bd032HeightPx) must match standard diagram height ($standardHeightPx) within 2px",
            abs(bd032HeightPx - standardHeightPx) <= 2f
        )
    }

    @Test
    fun test09_pgnDiagramsHideLastMoveUntilUserStartsReplay() {
        val state = viewModel.uiState.value
        val pgnDiagrams = listOf("BD-029", "BD-030", "BD-031", "BD-034")
        for (diagramId in pgnDiagrams) {
            val diagram = state.diagrams.first { it.diagramId == diagramId }
            val pgnRef = diagram.pgnRef!!
            val initialState = state.pgnStates[pgnRef]!!
            assertFalse("Initial state for $diagramId must have hasUserStartedReplay = false", initialState.hasUserStartedReplay)
        }

        // Render BD-029 on page 44 and verify "آخرین حرکت:" is hidden initially
        val bd029 = state.diagrams.first { it.diagramId == "BD-029" }

        composeTestRule.setContent {
            val currentState by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                DiagramCard(
                    diagram = bd029,
                    state = currentState,
                    viewModel = viewModel,
                    fontMultiplier = 1f
                )
            }
        }

        assertEquals(
            0,
            composeTestRule.onAllNodesWithText("آخرین حرکت:", substring = true).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag("pgn_start_replay_btn").assertIsDisplayed()

        // After user clicks start replay, hasUserStartedReplay becomes true
        composeTestRule.onNodeWithTag("pgn_start_replay_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.pgnStates[bd029.pgnRef!!]!!.hasUserStartedReplay)
    }

    @Test
    fun test10_noPlaceholderNoticeOnPage50OrTranslatedSections() {
        val sections = viewModel.uiState.value.sections
        assertEquals(6, sections.size)
        sections.forEach { section ->
            assertEquals(TranslationStatus.COMPLETE, section.translationSlot.translationStatus)
            assertFalse(
                "Section ${section.sectionId} must not show placeholder for translated blocks",
                section.translationSlot.orderedContentBlocks.any { block ->
                    section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)
                }
            )
        }
    }

    @Test
    fun test11_watermarkPdfObj010And011RemovedFromPdfPage7() {
        val fmPack = viewModel.uiState.value.frontMatterPack
        assertNotNull(fmPack)
        val page7 = fmPack!!.pages.first { it.sourcePdfPage == 7 }
        val renderableBlocks = page7.buildRenderableBlocks()
        val renderedAssetIds = renderableBlocks
            .filterIsInstance<SectionContentBlock.SourceImageBlock>()
            .map { it.assetId }

        assertFalse("PDFOBJ_010 watermark must not be rendered on page 7", renderedAssetIds.contains("PDFOBJ_010"))
        assertFalse("PDFOBJ_011 watermark must not be rendered on page 7", renderedAssetIds.contains("PDFOBJ_011"))
    }

    @Test
    fun test12_paginationPreservesSourcePdfPageAndReducesPage14Slices() {
        val fmPages = viewModel.uiState.value.frontMatterPages

        // Page 14 must be 2 slices instead of 7
        val page14Slices = fmPages.filter { it.sourcePdfPage == 14 }
        assertTrue("Expected page 14 to have <= 3 slices, got ${page14Slices.size}", page14Slices.size in 1..3)

        // Verify no slice merges two different sourcePdfPages across Front Matter or Chapter 1
        val ch1Slices = viewModel.uiState.value.sections.flatMap { sec ->
            ReaderPaginator.paginateSection(sec, viewModel.uiState.value.diagrams)
        }
        (fmPages + ch1Slices).forEach { page ->
            page.orderedBlocks.forEach { block ->
                val blockPage = block.sourcePdfPage
                if (blockPage != null && page.sourcePdfPage != null) {
                    assertEquals(
                        "Page ${page.pageId} (pdfPage ${page.sourcePdfPage}) must not contain block from page $blockPage",
                        page.sourcePdfPage,
                        blockPage
                    )
                }
            }
        }
    }

    @Test
    fun test13_bd032InteractiveExerciseStillAcceptsBothQh8AndQh2AndHidesHintsByDefault() {
        val exerciseId = "EX_CH01_BD032"
        viewModel.resetExercise(exerciseId)
        val initialState = viewModel.uiState.value.exerciseStates[exerciseId]!!
        assertFalse("Hints must be hidden by default", initialState.showLegalMovesHint)

        // Test Qh8# (e5 -> h8: file 4, rank 4 -> file 7, rank 7)
        viewModel.onExerciseSquareClicked(exerciseId, 4, 4)
        viewModel.onExerciseSquareClicked(exerciseId, 7, 7)
        viewModel.uiState.value.exerciseStates[exerciseId]?.activeTransition?.let {
            viewModel.onExerciseTransitionFinished(exerciseId, it.transitionId)
        }
        val afterQh8 = viewModel.uiState.value.exerciseStates[exerciseId]!!
        assertEquals(ExercisePhase.SOLVED, afterQh8.phase)
        assertEquals("Qh8#", afterQh8.solutionPlayed)

        // Reset and test Qh2# (e5 -> h2: file 4, rank 4 -> file 7, rank 1)
        viewModel.resetExercise(exerciseId)
        viewModel.onExerciseSquareClicked(exerciseId, 4, 4)
        viewModel.onExerciseSquareClicked(exerciseId, 7, 1)
        viewModel.uiState.value.exerciseStates[exerciseId]?.activeTransition?.let {
            viewModel.onExerciseTransitionFinished(exerciseId, it.transitionId)
        }
        val afterQh2 = viewModel.uiState.value.exerciseStates[exerciseId]!!
        assertEquals(ExercisePhase.SOLVED, afterQh2.phase)
        assertEquals("Qh2#", afterQh2.solutionPlayed)
    }

    @Test
    fun test14_auditReportsExistAndAreComplete() {
        val projectRoot = File("..").canonicalFile
        val csvFile = File(projectRoot, "SOURCE_IMAGE_EDITORIAL_AUDIT_FA.csv").let {
            if (it.exists()) it else File("SOURCE_IMAGE_EDITORIAL_AUDIT_FA.csv")
        }
        val editorialMdFile = File(projectRoot, "EDITORIAL_CONTENT_AUDIT_FA.md").let {
            if (it.exists()) it else File("EDITORIAL_CONTENT_AUDIT_FA.md")
        }
        val ch1AuditMdFile = File(projectRoot, "CHAPTER1_DATA_AUDIT.md").let {
            if (it.exists()) it else File("CHAPTER1_DATA_AUDIT.md")
        }

        assertTrue("SOURCE_IMAGE_EDITORIAL_AUDIT_FA.csv must exist", csvFile.exists() && csvFile.length() > 500)
        assertTrue("EDITORIAL_CONTENT_AUDIT_FA.md must exist", editorialMdFile.exists() && editorialMdFile.length() > 500)
        assertTrue("CHAPTER1_DATA_AUDIT.md must exist", ch1AuditMdFile.exists() && ch1AuditMdFile.length() > 500)

        val csvText = csvFile.readText()
        assertTrue(csvText.contains("PDFOBJ_010"))
        assertTrue(csvText.contains("PDFOBJ_018"))
        assertTrue(csvText.contains("PDFOBJ_022"))
        assertTrue(csvText.contains("PDFOBJ_025"))
        assertTrue(csvText.contains("PDFOBJ_028"))
        assertTrue(csvText.contains("BD-027"))
    }

    @Test
    fun test15_stage1UnifiedTocExactPageRangesAndNavigation() {
        viewModel.openChaptersList()
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ChaptersListScreen(
                    state = state,
                    onSelectChapter = { viewModel.selectChapter(it) },
                    onBackToIntroduction = {},
                    onStartFrontMatter = { viewModel.openFrontMatter(it) },
                    onResumeFrontMatter = { viewModel.resumeFrontMatter() },
                    onNavigateToPdfPage = { page, chId -> viewModel.navigateToSourcePdfPage(page, chId) },
                    onDismissNavigationNotice = { viewModel.dismissNavigationNotice() }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify unified row widths match between Front Matter and Chapter 1
        val fmBounds = composeTestRule.onNodeWithTag("front_matter_region_card").fetchSemanticsNode().boundsInRoot
        val ch1Bounds = composeTestRule.onNodeWithTag("chapter_card_CH01").fetchSemanticsNode().boundsInRoot
        assertEquals(fmBounds.width, ch1Bounds.width, 1f)

        // 2. Verify CH01 page range badge shows 39 to 50 and clicking CH01 row opens PDF page 39
        composeTestRule.onNodeWithText("صفحات PDF ۳۹ تا ۵۰", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("chapter_card_CH01").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(39, viewModel.uiState.value.currentPage?.sourcePdfPage)

        // 3. Verify clicking CH01 subtopic 1 (تسلیم -> 45) opens PDF page 45
        composeTestRule.onNodeWithTag("toc_subtopic_CH01_1").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(45, viewModel.uiState.value.currentPage?.sourcePdfPage)

        // 4. Verify clicking unready CH02 does NOT navigate to CH01 and shows preparation notice
        viewModel.openChaptersList()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chapter_card_CH02").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.CHAPTERS_LIST, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("navigation_notice_banner").assertIsDisplayed()
    }

    @Test
    fun test16_patch23A_activeRowIndicatorAndPartTwoDividerInChaptersListScreen() {
        viewModel.openChaptersList()
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ChaptersListScreen(
                    state = state,
                    onSelectChapter = { viewModel.selectChapter(it) },
                    onBackToIntroduction = {},
                    onStartFrontMatter = { viewModel.openFrontMatter(it) },
                    onResumeFrontMatter = { viewModel.resumeFrontMatter() },
                    onNavigateToPdfPage = { page, chId -> viewModel.navigateToSourcePdfPage(page, chId) },
                    onDismissNavigationNotice = { viewModel.dismissNavigationNotice() }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Pages 11..36: Front Matter card is active ("در حال مطالعه"), CH01 is NOT active
        viewModel.navigateToSourcePdfPage(11, null)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("front_matter_status_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        viewModel.navigateToSourcePdfPage(36, null)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("front_matter_status_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        // 2. Pages 37 and 38: NEITHER Front Matter nor Chapter 1 is active
        viewModel.navigateToSourcePdfPage(37, null)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        viewModel.navigateToSourcePdfPage(38, null)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        // 3. Page 39: Chapter 1 is active ("در حال مطالعه"), Front Matter is NOT active
        viewModel.navigateToSourcePdfPage(39, null)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onNodeWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertIsDisplayed()

        // 4. Page 40+ in CH01: Chapter 1 is active, Front Matter is NOT active
        viewModel.navigateToSourcePdfPage(40, "CH01")
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onNodeWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertIsDisplayed()

        // 5. Part Two divider (toc_part_divider_2) is unclickable (no navigation, no notice)
        viewModel.openChaptersList()
        viewModel.dismissNavigationNotice()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("chapters_lazy_list")
            .performScrollToNode(hasTestTag("toc_part_divider_2"))
        composeTestRule.waitForIdle()

        val partTwoDivider = composeTestRule.onNodeWithTag("toc_part_divider_2")
        partTwoDivider.assertIsDisplayed()
        partTwoDivider.assertHasNoClickAction()

        val pageBeforeClick = viewModel.uiState.value.currentPage?.sourcePdfPage
        val tabBeforeClick = viewModel.uiState.value.currentTab
        val noticeBeforeClick = viewModel.uiState.value.lastNavigationNoticeFa

        partTwoDivider.performClick()
        composeTestRule.waitForIdle()

        assertEquals(pageBeforeClick, viewModel.uiState.value.currentPage?.sourcePdfPage)
        assertEquals(tabBeforeClick, viewModel.uiState.value.currentTab)
        assertEquals(noticeBeforeClick, viewModel.uiState.value.lastNavigationNoticeFa)
        composeTestRule.onAllNodesWithTag("navigation_notice_banner", useUnmergedTree = true).assertCountEquals(0)

        // 6. Navigation for Front Matter, subtopics, and Part One divider remains working
        composeTestRule.onNodeWithTag("chapters_lazy_list")
            .performScrollToNode(hasTestTag("toc_part_divider_1"))
        composeTestRule.waitForIdle()
        val partOneDivider = composeTestRule.onNodeWithTag("toc_part_divider_1")
        partOneDivider.assertHasClickAction()
        partOneDivider.performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(37, viewModel.uiState.value.currentPage?.sourcePdfPage)
    }

    @Test
    fun test17_patch23A_activeRowIndicatorAndPartTwoDividerInSectionsListScreen() {
        viewModel.openSectionsList()
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                SectionsListScreen(
                    viewModel = viewModel,
                    state = state
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Pages 11..36: Front Matter active
        viewModel.navigateToSourcePdfPage(25, null)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sections_lazy_column")
            .performScrollToNode(hasTestTag("front_matter_region_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("front_matter_status_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        // 2. Pages 37 and 38: Neither active
        viewModel.navigateToSourcePdfPage(37, null)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        viewModel.navigateToSourcePdfPage(38, null)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertCountEquals(0)

        // 3. Page 39: CH01 active
        viewModel.navigateToSourcePdfPage(39, null)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onNodeWithTag("sections_lazy_column")
            .performScrollToNode(hasTestTag("chapter_card_CH01"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertIsDisplayed()

        // 4. Page 42 in CH01: CH01 active
        viewModel.navigateToSourcePdfPage(42, "CH01")
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag("front_matter_status_badge", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onNodeWithTag("sections_lazy_column")
            .performScrollToNode(hasTestTag("chapter_card_CH01"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chapter_status_badge_CH01", useUnmergedTree = true).assertIsDisplayed()

        // 5. Part Two divider is not clickable
        viewModel.openSectionsList()
        viewModel.dismissNavigationNotice()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sections_lazy_column")
            .performScrollToNode(hasTestTag("toc_part_divider_2"))
        composeTestRule.waitForIdle()

        val secPartTwoDivider = composeTestRule.onNodeWithTag("toc_part_divider_2")
        secPartTwoDivider.assertIsDisplayed()
        secPartTwoDivider.assertHasNoClickAction()

        val pageBeforeClick = viewModel.uiState.value.currentPage?.sourcePdfPage
        secPartTwoDivider.performClick()
        composeTestRule.waitForIdle()
        assertEquals(pageBeforeClick, viewModel.uiState.value.currentPage?.sourcePdfPage)
        assertNull(viewModel.uiState.value.lastNavigationNoticeFa)
    }
}
