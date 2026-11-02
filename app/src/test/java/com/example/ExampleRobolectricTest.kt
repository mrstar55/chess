package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.chess.ChessEngine
import com.example.data.ChapterRepository
import com.example.data.UserPreferencesManager
import com.example.model.ContentBlockValidator
import com.example.model.SectionContentBlock
import com.example.model.TranslationSlot
import com.example.model.TranslationStatus
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.components.ChessBoardView
import com.example.ui.screens.DiagramCard
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SectionContentBlockItem
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("آموزش شطرنج", appName)
    }

    @Test
    fun `verify chapter repository loads 10 diagrams and 6 sections with fixed ids`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val diagrams = repo.getDiagrams()
        val sections = repo.getSections()

        assertEquals(10, diagrams.size)
        assertEquals(6, sections.size)
        assertEquals("BD-025", diagrams.first().diagramId)
        assertEquals("BD-034", diagrams.last().diagramId)

        assertEquals("SEC_CH01_01_OPENING", sections[0].sectionId)
        assertEquals("SEC_CH01_02_CHECKMATE", sections[1].sectionId)
        assertEquals("SEC_CH01_03_RESIGNATION", sections[2].sectionId)
        assertEquals("SEC_CH01_04_TIMING_OUT", sections[3].sectionId)
        assertEquals("SEC_CH01_05_ABANDONMENT", sections[4].sectionId)
        assertEquals("SEC_CH01_06_SUMMARY", sections[5].sectionId)

        sections.forEach { section ->
            assertTrue(section.translationSlot.isPending)
            assertEquals(section.sectionId, section.translationSlot.sectionId)
        }
    }

    @Test
    fun `verify all PGN and exercise references in repository are valid with no dangling refs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val diagrams = repo.getDiagrams()

        val bd032 = diagrams.first { it.diagramId == "BD-032" }
        assertNull("BD-032 single pgnRef must be null", bd032.pgnRef)
        assertEquals(
            "BD-032 solutionPgnRefs must contain both verified mate solutions",
            listOf("BD032_MATE_QH8", "BD032_MATE_QH2"),
            bd032.solutionPgnRefs
        )

        // Verify all PGN references and precomputed sequences across all 10 diagrams
        assertTrue(
            "All diagram PGN references, solution references, and precomputed replay steps must be valid",
            repo.validateAllReferencesAndReplays()
        )
    }

    @Test
    fun `verify BD032 does not reveal solutions before solving and supports retry without reset`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setBd032Solved(false)

        val viewModel = ChapterViewModel(app)
        val initialFen = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1"

        // 1. Before solving, no solution or target coordinates are revealed
        val state0 = viewModel.uiState.value.bd032State
        assertFalse(state0.isSolved)
        assertNull(state0.solutionPlayed)
        assertNull(state0.feedbackMessage)
        assertEquals(initialFen, state0.currentFen)

        // 2. Select Queen at e5 (file 4, rank 4)
        viewModel.onBd032SquareClicked(4, 4)
        val stateSelected = viewModel.uiState.value.bd032State
        assertEquals("e5", stateSelected.selectedSquare)
        // Selection feedback must NOT leak 'h8' or 'h2'
        assertFalse(stateSelected.feedbackMessage?.contains("h8") == true)
        assertFalse(stateSelected.feedbackMessage?.contains("h2") == true)

        // 3. Play legal non-mating move: e5 -> e8 (file 4, rank 7)
        viewModel.onBd032SquareClicked(4, 7)
        val stateAfterWrong = viewModel.uiState.value.bd032State
        assertFalse("Qe8 is not checkmate, so exercise must remain unsolved", stateAfterWrong.isSolved)
        assertFalse(stateAfterWrong.feedbackIsSuccess)
        assertEquals("Board must automatically revert to initial FEN after non-mating move", initialFen, stateAfterWrong.currentFen)
        assertNull("Selection must be cleared", stateAfterWrong.selectedSquare)

        // 4. Retry immediately WITHOUT pressing Reset: select Queen at e5 (4, 4) -> move to h2 (7, 1)
        viewModel.onBd032SquareClicked(4, 4)
        assertEquals("e5", viewModel.uiState.value.bd032State.selectedSquare)
        viewModel.onBd032SquareClicked(7, 1) // Qh2#

        val stateSolved = viewModel.uiState.value.bd032State
        assertTrue("Qh2# must mark exercise as solved", stateSolved.isSolved)
        assertTrue(stateSolved.feedbackIsSuccess)
        assertEquals("Qh2#", stateSolved.solutionPlayed)
        assertEquals("8/8/1p6/1k4P1/7K/8/7q/6r1 w - - 1 2", stateSolved.currentFen)

        // 5. Once solved, further clicks on the board must be ignored until Reset
        val solvedFen = stateSolved.currentFen
        viewModel.onBd032SquareClicked(6, 0) // click rook at g1
        assertEquals(solvedFen, viewModel.uiState.value.bd032State.currentFen)
        assertNull(viewModel.uiState.value.bd032State.selectedSquare)
    }

    @Test
    fun `verify BD032 reset clears persisted solved status across app restart`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        val viewModel = ChapterViewModel(app)

        // Solve BD-032 with Qh8# (e5 -> h8)
        viewModel.onBd032SquareClicked(4, 4)
        viewModel.onBd032SquareClicked(7, 7)
        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        assertTrue(prefs.isBd032Solved())

        // Call Reset
        viewModel.resetBd032()
        assertFalse("UI state must be unsolved after reset", viewModel.uiState.value.bd032State.isSolved)
        assertFalse("Persisted preference must be false after reset", prefs.isBd032Solved())

        // Simulate closing and reopening the app by creating a new ViewModel instance
        val restartedViewModel = ChapterViewModel(app)
        assertFalse(
            "After app restart following reset, exercise must still be unsolved",
            restartedViewModel.uiState.value.bd032State.isSolved
        )
    }

    @Test
    fun `verify ChessBoardView composable renders files a to h LTR and ranks 8 to 1 top to bottom inside RTL layout`() {
        composeTestRule.setContent {
            // Render ChessBoardView inside a Persian RTL parent container
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ChessBoardView(fen = ChessEngine.STANDARD_START_FEN)
            }
        }
        composeTestRule.waitForIdle()

        // Verify files a1..h1 are positioned strictly left-to-right on screen (no RTL mirroring)
        val fileSquares = listOf("a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1")
        val fileXPositions = fileSquares.map { sq ->
            composeTestRule.onNodeWithTag("square_$sq").fetchSemanticsNode().boundsInRoot.left
        }
        for (i in 0 until fileXPositions.size - 1) {
            assertTrue(
                "Square ${fileSquares[i]} (left=${fileXPositions[i]}) must be strictly to the left of ${fileSquares[i + 1]} (left=${fileXPositions[i + 1]}) even in RTL mode",
                fileXPositions[i] < fileXPositions[i + 1]
            )
        }

        // Verify ranks a8..a1 are positioned strictly top-to-bottom on screen
        val rankSquares = listOf("a8", "a7", "a6", "a5", "a4", "a3", "a2", "a1")
        val rankYPositions = rankSquares.map { sq ->
            composeTestRule.onNodeWithTag("square_$sq").fetchSemanticsNode().boundsInRoot.top
        }
        for (i in 0 until rankYPositions.size - 1) {
            assertTrue(
                "Square ${rankSquares[i]} (top=${rankYPositions[i]}) must be strictly above ${rankSquares[i + 1]} (top=${rankYPositions[i + 1]})",
                rankYPositions[i] < rankYPositions[i + 1]
            )
        }
    }

    private fun collectAllSemanticsStrings(node: SemanticsNode): List<String> {
        val result = mutableListOf<String>()
        if (SemanticsProperties.Text in node.config) {
            node.config[SemanticsProperties.Text].forEach { result.add(it.text) }
        }
        if (SemanticsProperties.ContentDescription in node.config) {
            node.config[SemanticsProperties.ContentDescription].forEach { result.add(it) }
        }
        node.children.forEach { child ->
            result.addAll(collectAllSemanticsStrings(child))
        }
        return result
    }

    @Test
    fun `verify BD032 Compose UI does not reveal Qh8 or Qh2 or target squares before solving and shows success after solving`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        UserPreferencesManager(app).setBd032Solved(false)
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val bd032Diagram = state.diagrams.first { it.diagramId == "BD-032" }
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DiagramCard(
                        diagram = bd032Diagram,
                        state = state,
                        viewModel = viewModel,
                        fontMultiplier = 1.0f
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Before solving: verify no text in the Compose UI contains Qh8#, Qh2#, Qh8, Qh2, h8, or h2
        val allStringsBefore = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertFalse(
            "UI must NOT contain 'Qh8#' before solving",
            allStringsBefore.any { it.contains("Qh8#", ignoreCase = true) || it.contains("Qh8", ignoreCase = true) }
        )
        assertFalse(
            "UI must NOT contain 'Qh2#' before solving",
            allStringsBefore.any { it.contains("Qh2#", ignoreCase = true) || it.contains("Qh2", ignoreCase = true) }
        )
        assertFalse(
            "UI must NOT introduce 'h8' or 'h2' as answer coordinates before solving",
            allStringsBefore.any { it.contains("h8", ignoreCase = true) || it.contains("h2", ignoreCase = true) }
        )

        composeTestRule.onNodeWithText("Qh8", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Qh2", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("exercise_feedback").assertDoesNotExist()

        // Also verify that BD-032 diagram and exercise state do not highlight h8 or h2 as answers
        val bd032Diagram = viewModel.uiState.value.diagrams.first { it.diagramId == "BD-032" }
        assertTrue("BD-032 highlightSquares must be empty so h8/h2 are not highlighted", bd032Diagram.highlightSquares.isEmpty())
        assertTrue("BD-032 legalTargetSquares must be empty before piece selection", viewModel.uiState.value.bd032State.legalTargetSquares.isEmpty())

        saveRootViewScreenshot("04_bd032_section4_before_solve.png")

        // 2. Solve BD-032 via real Compose UI clicks: select Black Queen at e5, then click destination h8
        composeTestRule.onNodeWithTag("square_e5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("square_h8").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify success feedback is displayed in the Compose UI
        composeTestRule.onNodeWithTag("exercise_feedback").assertIsDisplayed()
        composeTestRule.onNodeWithText("پاسخ صحیح! حرکت Qh8# توسط موتور قوانین بررسی شد و شاه سفید مات شد.", substring = false).assertIsDisplayed()

        saveRootViewScreenshot("05_bd032_section4_after_solve.png")

        // 4. Reset via UI button and verify second winning move (Qh2#) via real Compose UI clicks
        composeTestRule.onNodeWithTag("exercise_reset_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Qh8", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Qh2", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithTag("square_e5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("square_h2").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("exercise_feedback").assertIsDisplayed()
        composeTestRule.onNodeWithText("پاسخ صحیح! حرکت Qh2# توسط موتور قوانین بررسی شد و شاه سفید مات شد.", substring = false).assertIsDisplayed()
    }

    private fun saveRootViewScreenshot(fileName: String) {
        composeTestRule.waitForIdle()
        val screenshotsDir = File("../screenshots").let {
            if (it.parentFile?.exists() == true) it else File("screenshots")
        }
        screenshotsDir.mkdirs()

        // Find the active window decorView via WindowInspector / ActivityThread or Robolectric
        val decorViews = android.view.inspector.WindowInspector.getGlobalWindowViews()
        val targetView: View = decorViews.lastOrNull() ?: return
        val width = if (targetView.width > 0) targetView.width else 1080
        val height = if (targetView.height > 0) targetView.height else 2340

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        targetView.draw(canvas)

        val outFile = File(screenshotsDir, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    @Test
    fun `capture real UI screenshots of main screens and BD032 exercise`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.resetBd032()

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Main Screen (Sections List / TOC)
        viewModel.setTab(AppTab.SECTIONS_LIST)
        composeTestRule.waitForIdle()
        // Verify English subtitles are hidden from SectionsListScreen UI
        val tocStrings = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        viewModel.uiState.value.sections.forEach { section ->
            assertFalse(
                "English subtitle '${section.titleEn}' must be hidden in SectionsListScreen UI",
                tocStrings.any { it.contains(section.titleEn, ignoreCase = true) }
            )
        }
        saveRootViewScreenshot("01_main_sections_list.png")

        // 2. Reader Screen (Section 1 - Opening & Standard Chessboard)
        viewModel.selectSection(0)
        composeTestRule.waitForIdle()
        val readerSec1Strings = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertFalse(
            "English section title must be hidden in ReaderScreen UI",
            readerSec1Strings.any { it.contains("Chapter opening", ignoreCase = true) }
        )
        assertFalse(
            "English book_context must be hidden in ReaderScreen UI",
            readerSec1Strings.any { it.contains("The chapter recalls the standard initial chess position", ignoreCase = true) }
        )
        assertTrue(
            "Persian pending notice must be displayed in ReaderScreen UI",
            readerSec1Strings.any { it.contains("محتوای این بخش در حال آماده‌سازی است.") }
        )
        saveRootViewScreenshot("02_reader_screen_section1.png")

        // 3. Chessboard & Section 2 (Checkmate & BD-027 Blocked Warning)
        viewModel.selectSection(1)
        composeTestRule.waitForIdle()
        val readerSec2Strings = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertFalse(
            "English book_context for BD-026 must be hidden in ReaderScreen UI",
            readerSec2Strings.any { it.contains("The simplest checkmate example", ignoreCase = true) }
        )
        saveRootViewScreenshot("03_chessboard_section2_checkmate.png")

        val screenshotsDir = File("../screenshots").let {
            if (it.exists()) it else File("screenshots")
        }
        assertTrue(File(screenshotsDir, "01_main_sections_list.png").exists())
        assertTrue(File(screenshotsDir, "02_reader_screen_section1.png").exists())
        assertTrue(File(screenshotsDir, "03_chessboard_section2_checkmate.png").exists())
    }

    @Test
    fun `test 1 - preserve interleaved block order during rendering`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val interleavedBlocks = listOf(
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_02_CHECKMATE_P001",
                textFa = "پاراگراف اول پیش از نمودار ۲۶"
            ),
            SectionContentBlock.DiagramBlock(
                blockId = "SEC_CH01_02_CHECKMATE_D026",
                diagramId = "BD-026"
            ),
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_02_CHECKMATE_P002",
                textFa = "پاراگراف دوم میان دو نمودار ۲۶ و ۲۷"
            ),
            SectionContentBlock.DiagramBlock(
                blockId = "SEC_CH01_02_CHECKMATE_D027",
                diagramId = "BD-027"
            )
        )

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_02_CHECKMATE",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_02_CHECKMATE",
                orderedContentBlocks = interleavedBlocks,
                translationStatus = TranslationStatus.COMPLETE
            )
        )
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val section = state.sections[1]
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )
                ) {
                    section.translationSlot.orderedContentBlocks.forEach { block ->
                        SectionContentBlockItem(
                            block = block,
                            section = section,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = 1.0f
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val topP001 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P001")
            .fetchSemanticsNode().positionInRoot.y
        val topD026 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D026")
            .fetchSemanticsNode().positionInRoot.y
        val topP002 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P002")
            .fetchSemanticsNode().positionInRoot.y
        val topD027 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D027")
            .fetchSemanticsNode().positionInRoot.y

        assertTrue("P001 must be rendered above D026", topP001 < topD026)
        assertTrue("D026 must be rendered above P002", topD026 < topP002)
        assertTrue("P002 must be rendered above D027", topP002 < topD027)
    }

    @Test
    fun `test 2 - render paragraph before diagram`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_01_OPENING",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_01_OPENING",
                orderedContentBlocks = listOf(
                    SectionContentBlock.ParagraphBlock(
                        blockId = "SEC_CH01_01_OPENING_P001",
                        textFa = "متن آموزشی قرارگرفته پیش از نمودار آغازین"
                    ),
                    SectionContentBlock.DiagramBlock(
                        blockId = "SEC_CH01_01_OPENING_D025",
                        diagramId = "BD-025"
                    )
                ),
                translationStatus = TranslationStatus.COMPLETE
            )
        )
        viewModel.selectSection(0)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("متن آموزشی قرارگرفته پیش از نمودار آغازین").assertIsDisplayed()
        val topParagraph = composeTestRule.onNodeWithTag("content_block_SEC_CH01_01_OPENING_P001")
            .fetchSemanticsNode().positionInRoot.y
        val topDiagram = composeTestRule.onNodeWithTag("content_block_SEC_CH01_01_OPENING_D025")
            .fetchSemanticsNode().positionInRoot.y

        assertTrue("Paragraph must appear strictly before diagram BD-025", topParagraph < topDiagram)
    }

    @Test
    fun `test 3 - render paragraph after diagram`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_01_OPENING",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_01_OPENING",
                orderedContentBlocks = listOf(
                    SectionContentBlock.DiagramBlock(
                        blockId = "SEC_CH01_01_OPENING_D025",
                        diagramId = "BD-025"
                    ),
                    SectionContentBlock.ParagraphBlock(
                        blockId = "SEC_CH01_01_OPENING_P002",
                        textFa = "متن توضیحی قرارگرفته پس از نمودار آغازین"
                    )
                ),
                translationStatus = TranslationStatus.COMPLETE
            )
        )
        viewModel.selectSection(0)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("متن توضیحی قرارگرفته پس از نمودار آغازین").assertIsDisplayed()
        val topDiagram = composeTestRule.onNodeWithTag("content_block_SEC_CH01_01_OPENING_D025")
            .fetchSemanticsNode().positionInRoot.y
        val topParagraph = composeTestRule.onNodeWithTag("content_block_SEC_CH01_01_OPENING_P002")
            .fetchSemanticsNode().positionInRoot.y

        assertTrue("Paragraph must appear strictly after diagram BD-025", topDiagram < topParagraph)
    }

    @Test
    fun `test 4 - render multiple diagrams in a section in exact registered order`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val checkmateSection = repo.getSections().first { it.sectionId == "SEC_CH01_02_CHECKMATE" }

        val registeredDiagramIds = checkmateSection.translationSlot.orderedContentBlocks
            .filterIsInstance<SectionContentBlock.DiagramBlock>()
            .map { it.diagramId }

        assertEquals(
            listOf("BD-026", "BD-027", "BD-028", "BD-029", "BD-030"),
            registeredDiagramIds
        )

        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val section = state.sections[1]
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )
                ) {
                    section.translationSlot.orderedContentBlocks
                        .filterIsInstance<SectionContentBlock.DiagramBlock>()
                        .forEach { block ->
                            SectionContentBlockItem(
                                block = block,
                                section = section,
                                state = state,
                                viewModel = viewModel,
                                fontMultiplier = 1.0f
                            )
                        }
                }
            }
        }
        composeTestRule.waitForIdle()

        val y26 = composeTestRule.onNodeWithTag("diagram_card_BD-026").fetchSemanticsNode().positionInRoot.y
        val y27 = composeTestRule.onNodeWithTag("diagram_card_BD-027").fetchSemanticsNode().positionInRoot.y
        val y28 = composeTestRule.onNodeWithTag("diagram_card_BD-028").fetchSemanticsNode().positionInRoot.y
        val y29 = composeTestRule.onNodeWithTag("diagram_card_BD-029").fetchSemanticsNode().positionInRoot.y
        val y30 = composeTestRule.onNodeWithTag("diagram_card_BD-030").fetchSemanticsNode().positionInRoot.y

        assertTrue("BD-026 must be rendered before BD-027", y26 < y27)
        assertTrue("BD-027 must be rendered before BD-028", y27 < y28)
        assertTrue("BD-028 must be rendered before BD-029", y28 < y29)
        assertTrue("BD-029 must be rendered before BD-030", y29 < y30)
    }

    @Test
    fun `test 5 - detect duplicate blockIds across blocks`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)

        // Default repository sections must have zero duplicate blockIds
        val defaultDuplicates = ContentBlockValidator.findDuplicateBlockIds(repo.getSections())
        assertTrue("Default repository blocks must have no duplicate IDs", defaultDuplicates.isEmpty())

        // Create a list of blocks containing a duplicate blockId
        val blocksWithDuplicate = listOf(
            SectionContentBlock.ParagraphBlock(blockId = "SEC_CH01_02_CHECKMATE_P001", textFa = "متن اول"),
            SectionContentBlock.DiagramBlock(blockId = "SEC_CH01_02_CHECKMATE_D026", diagramId = "BD-026"),
            SectionContentBlock.ParagraphBlock(blockId = "SEC_CH01_02_CHECKMATE_P001", textFa = "متن با شناسه تکراری")
        )

        val detectedDuplicates = ContentBlockValidator.findDuplicateBlockIdsInBlocks(blocksWithDuplicate)
        assertEquals(setOf("SEC_CH01_02_CHECKMATE_P001"), detectedDuplicates)

        val tamperedSections = repo.getSections().map { sec ->
            if (sec.sectionId == "SEC_CH01_02_CHECKMATE") {
                sec.copy(
                    translationSlot = sec.translationSlot.copy(orderedContentBlocks = blocksWithDuplicate)
                )
            } else {
                sec
            }
        }
        val validation = ContentBlockValidator.validateSectionsAndBlocks(tamperedSections, repo.getDiagrams())
        assertFalse("Validation must fail when duplicate blockId exists", validation.isValid)
        assertTrue(validation.duplicateBlockIds.contains("SEC_CH01_02_CHECKMATE_P001"))
    }

    @Test
    fun `test 6 - detect DiagramBlock with invalid diagramId`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val validDiagramIds = repo.getDiagrams().map { it.diagramId }.toSet()

        val blocksWithInvalidDiagram = listOf(
            SectionContentBlock.ParagraphBlock(blockId = "SEC_CH01_01_OPENING_P001"),
            SectionContentBlock.DiagramBlock(blockId = "SEC_CH01_01_OPENING_D999", diagramId = "BD-999"),
            SectionContentBlock.ExerciseBlock(blockId = "SEC_CH01_04_TIMING_OUT_E000", diagramId = "")
        )

        val invalidBlockIds = ContentBlockValidator.findInvalidDiagramBlocksInList(
            blocks = blocksWithInvalidDiagram,
            validDiagramIds = validDiagramIds
        )
        assertEquals(
            listOf("SEC_CH01_01_OPENING_D999", "SEC_CH01_04_TIMING_OUT_E000"),
            invalidBlockIds
        )

        val tamperedSections = repo.getSections().map { sec ->
            if (sec.sectionId == "SEC_CH01_01_OPENING") {
                sec.copy(
                    translationSlot = sec.translationSlot.copy(orderedContentBlocks = blocksWithInvalidDiagram)
                )
            } else {
                sec
            }
        }
        val validation = ContentBlockValidator.validateSectionsAndBlocks(tamperedSections, repo.getDiagrams())
        assertFalse("Validation must fail when DiagramBlock has invalid diagramId", validation.isValid)
        assertTrue(validation.invalidDiagramBlockIds.contains("SEC_CH01_01_OPENING_D999"))
    }

    @Test
    fun `test 7 - preserve the 6 primary TranslationSlot sectionIds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val sections = repo.getSections()

        val expectedIds = listOf(
            "SEC_CH01_01_OPENING",
            "SEC_CH01_02_CHECKMATE",
            "SEC_CH01_03_RESIGNATION",
            "SEC_CH01_04_TIMING_OUT",
            "SEC_CH01_05_ABANDONMENT",
            "SEC_CH01_06_SUMMARY"
        )

        assertEquals(expectedIds, TranslationSlot.REQUIRED_SECTION_SLOT_IDS)
        assertEquals(expectedIds, sections.map { it.translationSlot.sectionId })

        val fullValidation = ContentBlockValidator.validateSectionsAndBlocks(sections, repo.getDiagrams())
        assertTrue("All 6 primary TranslationSlots must be valid", fullValidation.isValid)
        assertTrue(fullValidation.missingSectionSlotIds.isEmpty())
    }

    @Test
    fun `test 8 - placeholder notice is not repeated under diagrams`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        // Select Section 2 (Checkmate), which has 1 untranslated ParagraphBlock and 5 DiagramBlocks
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val allStrings = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        val placeholderOccurrences = allStrings.count {
            it == "محتوای این بخش در حال آماده‌سازی است."
        }

        assertEquals(
            "Placeholder notice must appear ONLY ONCE in the untranslated content slot, never repeated under diagrams",
            1,
            placeholderOccurrences
        )
    }

    @Test
    fun `test 9 - empty block does not affect other blocks`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_03_RESIGNATION",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_03_RESIGNATION",
                orderedContentBlocks = listOf(
                    SectionContentBlock.ParagraphBlock(
                        blockId = "SEC_CH01_03_RESIGNATION_P001",
                        textFa = "پاراگراف اول پیش از بلوک خالی"
                    ),
                    SectionContentBlock.ParagraphBlock(
                        blockId = "SEC_CH01_03_RESIGNATION_P002_EMPTY",
                        textFa = null,
                        showPlaceholderWhenEmpty = false
                    ),
                    SectionContentBlock.DiagramBlock(
                        blockId = "SEC_CH01_03_RESIGNATION_D031",
                        diagramId = "BD-031"
                    ),
                    SectionContentBlock.ParagraphBlock(
                        blockId = "SEC_CH01_03_RESIGNATION_P003",
                        textFa = "پاراگراف سوم پس از نمودار ۳۱"
                    )
                ),
                translationStatus = TranslationStatus.PARTIAL
            )
        )
        viewModel.selectSection(2)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val section = state.sections[2]
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column {
                    section.translationSlot.orderedContentBlocks.forEach { block ->
                        SectionContentBlockItem(
                            block = block,
                            section = section,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = 1.0f
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("پاراگراف اول پیش از بلوک خالی").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagram_card_BD-031").assertIsDisplayed()
        composeTestRule.onNodeWithText("پاراگراف سوم پس از نمودار ۳۱").assertIsDisplayed()

        // Ensure the intentionally empty block did not inject a placeholder notice
        val allStrings = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertFalse(
            "Empty block with showPlaceholderWhenEmpty=false must not show placeholder notice",
            allStrings.any { it.contains("محتوای این بخش در حال آماده‌سازی است.") }
        )

        val topP001 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_03_RESIGNATION_P001")
            .fetchSemanticsNode().boundsInRoot.top
        val topD031 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_03_RESIGNATION_D031")
            .fetchSemanticsNode().boundsInRoot.top
        val topP003 = composeTestRule.onNodeWithTag("content_block_SEC_CH01_03_RESIGNATION_P003")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(topP001 < topD031)
        assertTrue(topD031 < topP003)
    }

    @Test
    fun `test 10 - single placeholder per section with 3 empty paragraphs 2 diagrams and 1 translated paragraph shifts and disappears`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val initialBlocks = listOf(
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_02_CHECKMATE_P001",
                textFa = null,
                showPlaceholderWhenEmpty = true
            ),
            SectionContentBlock.DiagramBlock(
                blockId = "SEC_CH01_02_CHECKMATE_D026",
                diagramId = "BD-026"
            ),
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_02_CHECKMATE_P002",
                textFa = null,
                showPlaceholderWhenEmpty = true
            ),
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_02_CHECKMATE_P003_TRANSLATED",
                textFa = "پاراگراف ترجمه‌شده میانی بین دو نمودار",
                showPlaceholderWhenEmpty = true
            ),
            SectionContentBlock.DiagramBlock(
                blockId = "SEC_CH01_02_CHECKMATE_D027",
                diagramId = "BD-027"
            ),
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_02_CHECKMATE_P004",
                textFa = null,
                showPlaceholderWhenEmpty = true
            )
        )

        val baseSlot = TranslationSlot(
            sectionId = "SEC_CH01_02_CHECKMATE",
            orderedContentBlocks = initialBlocks,
            translationStatus = TranslationStatus.PARTIAL
        )

        // Verify model-level placeholder target is the first empty paragraph (P001)
        assertEquals("SEC_CH01_02_CHECKMATE_P001", baseSlot.firstPendingPlaceholderBlockId())
        assertTrue(baseSlot.shouldShowPlaceholderForBlock("SEC_CH01_02_CHECKMATE_P001"))
        assertFalse(baseSlot.shouldShowPlaceholderForBlock("SEC_CH01_02_CHECKMATE_P002"))
        assertFalse(baseSlot.shouldShowPlaceholderForBlock("SEC_CH01_02_CHECKMATE_P004"))

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_02_CHECKMATE",
            updatedSlot = baseSlot
        )
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val section = state.sections[1]
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )
                ) {
                    section.translationSlot.orderedContentBlocks.forEach { block ->
                        SectionContentBlockItem(
                            block = block,
                            section = section,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = 1.0f
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Step A: With 3 empty paragraphs, ONLY ONE placeholder message is displayed at P001
        val stringsInitial = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertEquals(
            "Only ONE placeholder message must be rendered even when 3 paragraphs are empty",
            1,
            stringsInitial.count { it == TranslationSlot.DEFAULT_PLACEHOLDER_NOTICE }
        )
        composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P001").assertIsDisplayed()
        composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P002").assertDoesNotExist()
        composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P004").assertDoesNotExist()
        composeTestRule.onNodeWithTag("diagram_card_BD-026").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagram_card_BD-027").assertIsDisplayed()
        composeTestRule.onNodeWithText("پاراگراف ترجمه‌شده میانی بین دو نمودار").assertIsDisplayed()

        val yP001Initial = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P001")
            .fetchSemanticsNode().positionInRoot.y
        val yD026Initial = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D026")
            .fetchSemanticsNode().positionInRoot.y
        val yP003Initial = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P003_TRANSLATED")
            .fetchSemanticsNode().positionInRoot.y
        val yD027Initial = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D027")
            .fetchSemanticsNode().positionInRoot.y

        assertTrue(yP001Initial < yD026Initial)
        assertTrue(yD026Initial < yP003Initial)
        assertTrue(yP003Initial < yD027Initial)

        // Step B: Inject translation for the first empty paragraph (P001) -> placeholder shifts to P002
        val slotAfterFirstTranslated = baseSlot.withBlockTranslations(
            translations = mapOf("SEC_CH01_02_CHECKMATE_P001" to "متن ترجمه‌شده پاراگراف اول"),
            status = TranslationStatus.PARTIAL
        )
        assertEquals(
            "orderedContentBlocks must remain unchanged when injecting translation",
            initialBlocks,
            slotAfterFirstTranslated.orderedContentBlocks
        )
        assertEquals("SEC_CH01_02_CHECKMATE_P002", slotAfterFirstTranslated.firstPendingPlaceholderBlockId())

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_02_CHECKMATE",
            updatedSlot = slotAfterFirstTranslated
        )
        composeTestRule.waitForIdle()

        val stringsAfterFirst = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertEquals(
            "Placeholder must still appear ONLY ONCE after translating P001",
            1,
            stringsAfterFirst.count { it == TranslationSlot.DEFAULT_PLACEHOLDER_NOTICE }
        )
        composeTestRule.onNodeWithText("متن ترجمه‌شده پاراگراف اول").assertIsDisplayed()
        composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P002").assertIsDisplayed()
        composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P004").assertDoesNotExist()

        val yP001After = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P001")
            .fetchSemanticsNode().positionInRoot.y
        val yD026After = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D026")
            .fetchSemanticsNode().positionInRoot.y
        val yP002Placeholder = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P002")
            .fetchSemanticsNode().positionInRoot.y
        val yP003After = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P003_TRANSLATED")
            .fetchSemanticsNode().positionInRoot.y
        val yD027After = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D027")
            .fetchSemanticsNode().positionInRoot.y

        assertTrue("Translated P001 must remain above D026", yP001After < yD026After)
        assertTrue("Shifted placeholder at P002 must appear below D026", yD026After < yP002Placeholder)
        assertTrue("Shifted placeholder at P002 must appear above P003", yP002Placeholder < yP003After)
        assertTrue("P003 must remain above D027", yP003After < yD027After)

        // Step C: Translate remaining empty paragraphs (P002 and P004) -> placeholder disappears completely
        val slotAllTranslated = slotAfterFirstTranslated.withBlockTranslations(
            translations = mapOf(
                "SEC_CH01_02_CHECKMATE_P002" to "متن ترجمه‌شده پاراگراف دوم",
                "SEC_CH01_02_CHECKMATE_P004" to "متن ترجمه‌شده پاراگراف چهارم پس از نمودار ۲۷"
            ),
            status = TranslationStatus.COMPLETE
        )
        assertNull(slotAllTranslated.firstPendingPlaceholderBlockId())

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_02_CHECKMATE",
            updatedSlot = slotAllTranslated
        )
        composeTestRule.waitForIdle()

        val stringsAfterAll = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertEquals(
            "Placeholder must be completely removed once all text blocks are translated",
            0,
            stringsAfterAll.count { it == TranslationSlot.DEFAULT_PLACEHOLDER_NOTICE }
        )
        val yD027Final = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_D027")
            .fetchSemanticsNode().positionInRoot.y
        val yP004Final = composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P004")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("P004 must appear below D027", yD027Final < yP004Final)

        composeTestRule.onNodeWithTag("content_block_SEC_CH01_02_CHECKMATE_P004")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("متن ترجمه‌شده پاراگراف چهارم پس از نمودار ۲۷").assertIsDisplayed()
    }

    @Test
    fun `test 11 - ExternalReferenceBlock translation updates via Translation Map without changing orderedContentBlocks`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val extBlock = SectionContentBlock.ExternalReferenceBlock(
            blockId = "SEC_CH01_06_SUMMARY_EXT001",
            titleFa = "عنوان پیش‌فرض ارجاع",
            url = "https://example.com/lesson",
            isAuthorizedAndEnabled = true
        )
        val blocks = listOf(
            SectionContentBlock.HeadingBlock(
                blockId = "SEC_CH01_06_SUMMARY_H001",
                textFa = "جمع‌بندی فصل"
            ),
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_06_SUMMARY_P001",
                textFa = "پاراگراف پایانی فصل اول"
            ),
            extBlock
        )

        val initialSlot = TranslationSlot(
            sectionId = "SEC_CH01_06_SUMMARY",
            orderedContentBlocks = blocks,
            translationStatus = TranslationStatus.PARTIAL
        )

        assertEquals("عنوان پیش‌فرض ارجاع", initialSlot.resolveBlockText(extBlock))

        viewModel.updateSectionTranslationSlot("SEC_CH01_06_SUMMARY", initialSlot)
        viewModel.selectSection(5)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("عنوان پیش‌فرض ارجاع").assertIsDisplayed()

        // Update ExternalReferenceBlock title via Translation Map without touching orderedContentBlocks
        val updatedSlot = initialSlot.withBlockTranslations(
            translations = mapOf(
                "SEC_CH01_06_SUMMARY_EXT001" to "درس تکمیلی فصل اول (ترجمه‌شده از طریق نقشه ترجمه)"
            ),
            status = TranslationStatus.COMPLETE
        )

        assertEquals(
            "orderedContentBlocks must not be modified when updating ExternalReferenceBlock via Translation Map",
            blocks,
            updatedSlot.orderedContentBlocks
        )
        assertEquals(
            "درس تکمیلی فصل اول (ترجمه‌شده از طریق نقشه ترجمه)",
            updatedSlot.resolveBlockText(extBlock)
        )

        viewModel.updateSectionTranslationSlot("SEC_CH01_06_SUMMARY", updatedSlot)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("درس تکمیلی فصل اول (ترجمه‌شده از طریق نقشه ترجمه)").assertIsDisplayed()
    }
}
