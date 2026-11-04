package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
    fun `verify BD032 does not reveal solutions before solving and supports retry after wrong legal move`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setBd032Solved(false)

        val viewModel = ChapterViewModel(app)
        val initialFen = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1"
        val afterQe8Fen = "4q3/8/1p6/1k4P1/7K/8/8/6r1 w - - 1 2"

        // 1. Before solving, no solution or target coordinates are revealed
        val state0 = viewModel.uiState.value.bd032State
        assertFalse(state0.isSolved)
        assertNull(state0.solutionPlayed)
        assertNull(state0.feedbackMessage)
        assertEquals(initialFen, state0.currentFen)
        assertFalse(state0.showLegalMovesHint)
        assertTrue(state0.visibleTargetSquares.isEmpty())

        // 2. Select Queen at e5 (file 4, rank 4) -> default showLegalMovesHint is false, so visibleTargetSquares is empty
        viewModel.onBd032SquareClicked(4, 4)
        val stateSelected = viewModel.uiState.value.bd032State
        assertEquals("e5", stateSelected.selectedSquare)
        assertTrue("By default, selecting a piece must not show destination dots", stateSelected.visibleTargetSquares.isEmpty())
        assertFalse(stateSelected.feedbackMessage?.contains("h8") == true)
        assertFalse(stateSelected.feedbackMessage?.contains("h2") == true)

        // 3. Play legal non-mating move: e5 -> e8 (file 4, rank 7) -> executes move on board and animates
        viewModel.onBd032SquareClicked(4, 7)
        val stateAnimatingWrong = viewModel.uiState.value.bd032State
        assertFalse("Qe8 is not checkmate, so exercise must remain unsolved", stateAnimatingWrong.isSolved)
        assertEquals("Board must execute the legal wrong move and stay on post-move FEN", afterQe8Fen, stateAnimatingWrong.currentFen)
        assertNotNull("Legal wrong move must trigger BoardTransition animation", stateAnimatingWrong.activeTransition)
        assertEquals(com.example.ui.Bd032ExercisePhase.ANIMATING_WRONG, stateAnimatingWrong.phase)

        // Complete animation -> phase transitions to WRONG_LEGAL_MOVE and displays exact error message
        viewModel.onBd032TransitionFinished(stateAnimatingWrong.activeTransition!!.transitionId)
        val stateAfterWrong = viewModel.uiState.value.bd032State
        assertEquals(com.example.ui.Bd032ExercisePhase.WRONG_LEGAL_MOVE, stateAfterWrong.phase)
        assertEquals("این حرکت قانونی است، اما مسئله را حل نمی‌کند. دوباره تلاش کنید.", stateAfterWrong.feedbackMessage)
        assertTrue(stateAfterWrong.canRetry)

        // Board is locked in WRONG_LEGAL_MOVE until retryBd032 ("تلاش دوباره") is clicked
        viewModel.onBd032SquareClicked(6, 0)
        assertEquals(afterQe8Fen, viewModel.uiState.value.bd032State.currentFen)

        // 4. Click Retry ("تلاش دوباره") -> resets board to initialFen, clears feedback, disables legal hint
        viewModel.retryBd032()
        assertEquals(initialFen, viewModel.uiState.value.bd032State.currentFen)
        assertNull(viewModel.uiState.value.bd032State.feedbackMessage)
        assertFalse(viewModel.uiState.value.bd032State.showLegalMovesHint)

        // Now select Queen at e5 (4, 4) -> move to h2 (7, 1) (Qh2#)
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

    // =========================================================================
    // 14 Mandatory ChessBoard Piece Movement Animation Tests (Version 08)
    // =========================================================================

    @Test
    fun `anim test 1 - smooth movement of a White piece and mid-animation position verification`() {
        val fromFen = ChessEngine.STANDARD_START_FEN
        val toFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1" // 1. e4 (e2 -> e4)
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_white_e4",
            fromFen = fromFen,
            toFen = toFen
        )!!

        assertEquals(com.example.model.BoardMoveType.NORMAL, transition.moveType)
        assertEquals("e2", transition.fromSquare)
        assertEquals("e4", transition.toSquare)
        assertTrue(transition.movingPiece.isWhite)
        assertEquals(com.example.model.PieceType.PAWN, transition.movingPiece.type)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = toFen,
                    transition = transition
                )
            }
        }

        // Advance 1 frame so LaunchedEffect starts and layout coordinates are measured
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110) // Midpoint of 220ms animation

        val e2Top = composeTestRule.onNodeWithTag("square_e2").fetchSemanticsNode().boundsInRoot.top
        val e4Top = composeTestRule.onNodeWithTag("square_e4").fetchSemanticsNode().boundsInRoot.top
        val movingNode = composeTestRule.onNodeWithTag("moving_piece_primary").fetchSemanticsNode()
        val movingTop = movingNode.boundsInRoot.top

        // Since rank 4 (e4) is higher on screen (smaller Y) than rank 2 (e2):
        assertTrue(
            "At 110ms (mid-animation), moving White pawn Y ($movingTop) must be strictly between e4 ($e4Top) and e2 ($e2Top)",
            movingTop > e4Top && movingTop < e2Top
        )

        // Capture screenshot during mid-animation
        saveMidAnimationScreenshot("06_chessboard_mid_animation.png")

        // Complete animation
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_e4", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_e2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `anim test 2 - smooth movement of a Black piece`() {
        val fromFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val toFen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2" // 1... e5 (e7 -> e5)
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_black_e5",
            fromFen = fromFen,
            toFen = toFen
        )!!

        assertFalse(transition.movingPiece.isWhite)
        assertEquals("e7", transition.fromSquare)
        assertEquals("e5", transition.toSquare)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = toFen,
                    transition = transition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        val e7Top = composeTestRule.onNodeWithTag("square_e7").fetchSemanticsNode().boundsInRoot.top
        val e5Top = composeTestRule.onNodeWithTag("square_e5").fetchSemanticsNode().boundsInRoot.top
        val movingTop = composeTestRule.onNodeWithTag("moving_piece_primary").fetchSemanticsNode().boundsInRoot.top

        // Rank 7 (e7) is above Rank 5 (e5), so e7Top < movingTop < e5Top
        assertTrue(
            "At 110ms, moving Black pawn Y ($movingTop) must be strictly between e7 ($e7Top) and e5 ($e5Top)",
            movingTop > e7Top && movingTop < e5Top
        )

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_e5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `anim test 3 - reverse movement in Previous PGN step`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE" // Scholar's Mate

        // Initially at end (step 7: Qxf7#). Step previous (step 7 -> step 6: f7 -> h5 reverse)
        viewModel.stepPgnPrev(pgnRef)
        val pgnState = viewModel.uiState.value.pgnStates[pgnRef]!!
        val reverseTransition = pgnState.activeTransition!!

        assertEquals("f7", reverseTransition.fromSquare)
        assertEquals("h5", reverseTransition.toSquare)
        assertEquals(com.example.model.PieceType.QUEEN, reverseTransition.movingPiece.type)
        assertTrue(reverseTransition.movingPiece.isWhite)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = pgnState.currentFen,
                    transition = reverseTransition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        val f7Bounds = composeTestRule.onNodeWithTag("square_f7").fetchSemanticsNode().boundsInRoot
        val h5Bounds = composeTestRule.onNodeWithTag("square_h5").fetchSemanticsNode().boundsInRoot
        val movingBounds = composeTestRule.onNodeWithTag("moving_piece_primary").fetchSemanticsNode().boundsInRoot

        assertTrue("Reverse X position must be between f7 and h5", movingBounds.left > f7Bounds.left && movingBounds.left < h5Bounds.left)
        assertTrue("Reverse Y position must be between f7 and h5", movingBounds.top > f7Bounds.top && movingBounds.top < h5Bounds.top)

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("static_piece_h5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_f7", useUnmergedTree = true).assertExists() // Restored black pawn on f7
    }

    @Test
    fun `anim test 4 - simultaneous King and Rook movement in Kingside and Queenside Castling`() {
        val castleStartFen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
        val kingsideEndFen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 b kq - 1 1"
        val queensideEndFen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/2KR3R b kq - 1 1"

        val ksTransition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_castle_ks",
            fromFen = castleStartFen,
            toFen = kingsideEndFen
        )!!
        assertEquals(com.example.model.BoardMoveType.CASTLING_KINGSIDE, ksTransition.moveType)
        assertEquals("e1", ksTransition.fromSquare)
        assertEquals("g1", ksTransition.toSquare)
        assertEquals("h1", ksTransition.rookFromSquare)
        assertEquals("f1", ksTransition.rookToSquare)

        val qsTransition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_castle_qs",
            fromFen = castleStartFen,
            toFen = queensideEndFen
        )!!
        assertEquals(com.example.model.BoardMoveType.CASTLING_QUEENSIDE, qsTransition.moveType)
        assertEquals("e1", qsTransition.fromSquare)
        assertEquals("c1", qsTransition.toSquare)
        assertEquals("a1", qsTransition.rookFromSquare)
        assertEquals("d1", qsTransition.rookToSquare)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = kingsideEndFen,
                    transition = ksTransition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // Both King (moving_piece_primary) and Rook (moving_piece_rook) must be animating simultaneously
        val kingNode = composeTestRule.onNodeWithTag("moving_piece_primary").fetchSemanticsNode()
        val rookNode = composeTestRule.onNodeWithTag("moving_piece_rook").fetchSemanticsNode()
        val e1Left = composeTestRule.onNodeWithTag("square_e1").fetchSemanticsNode().boundsInRoot.left
        val g1Left = composeTestRule.onNodeWithTag("square_g1").fetchSemanticsNode().boundsInRoot.left
        val h1Left = composeTestRule.onNodeWithTag("square_h1").fetchSemanticsNode().boundsInRoot.left
        val f1Left = composeTestRule.onNodeWithTag("square_f1").fetchSemanticsNode().boundsInRoot.left

        assertTrue("King must be mid-flight between e1 and g1", kingNode.boundsInRoot.left > e1Left && kingNode.boundsInRoot.left < g1Left)
        assertTrue("Rook must be mid-flight between h1 and f1 simultaneously", rookNode.boundsInRoot.left < h1Left && rookNode.boundsInRoot.left > f1Left)

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("moving_piece_rook").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_g1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_f1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `anim test 5 - Capture animation and smooth fade-out of captured piece`() {
        val fromFen = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"
        val toFen = "4k3/8/8/3P4/8/8/8/4K3 b - - 0 1" // exd5
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_capture_exd5",
            fromFen = fromFen,
            toFen = toFen
        )!!

        assertEquals(com.example.model.BoardMoveType.CAPTURE, transition.moveType)
        assertEquals("e4", transition.fromSquare)
        assertEquals("d5", transition.toSquare)
        assertEquals("d5", transition.capturedSquare)
        assertFalse(transition.capturedPiece!!.isWhite)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = toFen,
                    transition = transition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // During capture animation, captured piece overlay is fading on d5 while attacker moves e4 -> d5
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertExists()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertDoesNotExist()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `anim test 6 - En Passant capture removes adjacent pawn on capturedSquare distinct from toSquare`() {
        val epFromFen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
        val epToFen = "4k3/8/3P4/8/8/8/8/4K3 b - - 0 1" // exd6 e.p.
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_en_passant",
            fromFen = epFromFen,
            toFen = epToFen
        )!!

        assertEquals(com.example.model.BoardMoveType.EN_PASSANT, transition.moveType)
        assertEquals("e5", transition.fromSquare)
        assertEquals("d6", transition.toSquare)
        assertEquals("d5", transition.capturedSquare) // Adjacent square d5, NOT d6!
        assertNotEquals(transition.toSquare, transition.capturedSquare)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = epToFen,
                    transition = transition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // Captured pawn fades on d5 while white pawn moves e5 -> d6
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertExists()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_d6", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_e5", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `anim test 7 - Promotion animation moves Pawn during flight and reveals promoted piece (Q, R, B, N) on arrival`() {
        val promoStartFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val promotionTargets = mapOf(
            com.example.model.PieceType.QUEEN to ("Q" to "♕"),
            com.example.model.PieceType.ROOK to ("R" to "♖"),
            com.example.model.PieceType.BISHOP to ("B" to "♗"),
            com.example.model.PieceType.KNIGHT to ("N" to "♘")
        )

        for ((pieceType, charAndSymbol) in promotionTargets) {
            val (fenChar, _) = charAndSymbol
            val promoEndFen = "${fenChar}3k3/8/8/8/8/8/8/4K3 b - - 0 1"
            val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
                transitionId = "test_promo_${pieceType.name}",
                fromFen = promoStartFen,
                toFen = promoEndFen
            )!!

            assertEquals(com.example.model.BoardMoveType.PROMOTION, transition.moveType)
            assertEquals(com.example.model.PieceType.PAWN, transition.movingPiece.type)
            assertEquals(pieceType, transition.promotionPiece?.type)
        }

        // UI verification for Queen promotion
        val queenPromoFen = "Q3k3/8/8/8/8/8/8/4K3 b - - 0 1"
        val qTransition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_promo_ui_q",
            fromFen = promoStartFen,
            toFen = queenPromoFen
        )!!

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = queenPromoFen,
                    transition = qTransition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // During flight: moving piece is the White Pawn (♙) and destination Queen (♕) is hidden on static board
        composeTestRule.onNodeWithTag("static_piece_a8", useUnmergedTree = true).assertDoesNotExist()
        val movingTexts = collectAllSemanticsStrings(
            composeTestRule.onNodeWithTag("moving_piece_primary", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertTrue("Moving piece during promotion flight must be Pawn (♙)", movingTexts.contains("♙"))
        assertFalse("Promoted Queen (♕) must not appear prematurely during flight", movingTexts.contains("♕"))

        // Finish animation
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        val a8Texts = collectAllSemanticsStrings(
            composeTestRule.onNodeWithTag("static_piece_a8", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertTrue("After arrival, promoted Queen (♕) must be displayed on a8", a8Texts.contains("♕"))
    }

    @Test
    fun `anim test 8 - no animation executed on initial FEN render or diagram switch`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        // Verify all PGN states and BD-032 have null activeTransition on initial load
        viewModel.uiState.value.pgnStates.values.forEach { pgn ->
            assertNull("Initial PGN state must not trigger animation", pgn.activeTransition)
            assertFalse(pgn.isTransitioning)
        }
        assertNull("Initial BD-032 state must not trigger animation", viewModel.uiState.value.bd032State.activeTransition)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(fen = ChessEngine.STANDARD_START_FEN, transition = null)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // No moving piece overlay exists on initial render
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_e1", useUnmergedTree = true).assertExists()
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun `anim test 9 - Reset jumps directly to initial position without false visual animation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"

        viewModel.resetPgn(pgnRef)
        val pgnState = viewModel.uiState.value.pgnStates[pgnRef]!!

        assertTrue(pgnState.isAtStart)
        assertNull("Reset must NOT create a BoardTransition animation", pgnState.activeTransition)
        assertFalse(pgnState.isTransitioning)
        assertEquals(ChessEngine.STANDARD_START_FEN, pgnState.currentFen)
    }

    @Test
    fun `anim test 10 - JumpToEnd jumps directly to final position without sequential animations`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"

        viewModel.resetPgn(pgnRef)
        viewModel.jumpPgnToEnd(pgnRef)
        val pgnState = viewModel.uiState.value.pgnStates[pgnRef]!!

        assertTrue(pgnState.isAtEnd)
        assertNull("JumpToEnd must NOT create a BoardTransition animation", pgnState.activeTransition)
        assertFalse(pgnState.isTransitioning)
        assertEquals("r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4", pgnState.currentFen)
    }

    @Test
    fun `anim test 11 - no duplicate piece displayed during transition`() {
        val fromFen = "4k3/8/8/8/8/8/8/4K2R w K - 0 1"
        val toFen = "4k3/8/8/8/8/8/8/5RK1 b - - 1 1" // O-O: King e1->g1, Rook h1->f1
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_no_duplicates",
            fromFen = fromFen,
            toFen = toFen
        )!!

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = toFen,
                    transition = transition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // Static squares e1, g1, h1, f1 must NOT render static pieces while the overlay is moving
        composeTestRule.onNodeWithTag("static_piece_e1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_g1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_h1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_f1", useUnmergedTree = true).assertDoesNotExist()

        // Exactly 1 White King (♔) and 1 White Rook (♖) exist in the moving overlay
        val allStringsMid = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        // Note: PieceDisplay renders 2 Text nodes per piece (1 shadow + 1 main glyph)
        assertEquals("White King must only be rendered once (shadow+glyph) during animation", 2, allStringsMid.count { it == "♔" })
        assertEquals("White Rook must only be rendered once (shadow+glyph) during animation", 2, allStringsMid.count { it == "♖" })

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun `anim test 12 - preserve RTL board direction, coordinates, and square colors during and after animation`() {
        val fromFen = ChessEngine.STANDARD_START_FEN
        val toFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "test_rtl_geom",
            fromFen = fromFen,
            toFen = toFen
        )!!

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ChessBoardView(
                    fen = toFen,
                    transition = transition
                )
            }
        }

        composeTestRule.mainClock.advanceTimeByFrame()
        val boardSizeBefore = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().size
        val a1Left = composeTestRule.onNodeWithTag("square_a1").fetchSemanticsNode().boundsInRoot.left
        val h1Left = composeTestRule.onNodeWithTag("square_h1").fetchSemanticsNode().boundsInRoot.left
        val a8Top = composeTestRule.onNodeWithTag("square_a8").fetchSemanticsNode().boundsInRoot.top
        val a1Top = composeTestRule.onNodeWithTag("square_a1").fetchSemanticsNode().boundsInRoot.top

        assertTrue("a1 must be to the left of h1 in RTL", a1Left < h1Left)
        assertTrue("a8 must be above a1 in RTL", a8Top < a1Top)

        composeTestRule.mainClock.advanceTimeBy(110)
        val boardSizeMid = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().size
        assertEquals("Board size must not change during animation", boardSizeBefore, boardSizeMid)

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        val boardSizeAfter = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().size
        assertEquals("Board size must remain identical after animation", boardSizeBefore, boardSizeAfter)
    }

    @Test
    fun `anim test 13 - BD-032 exercise piece selection has no animation, illegal move has no animation, legal wrong and winning mate animate smoothly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        UserPreferencesManager(app).setBd032Solved(false)
        val viewModel = ChapterViewModel(app)

        // 1. Selecting Black Queen at e5 (4, 4) -> no animation
        viewModel.onBd032SquareClicked(4, 4)
        assertEquals("e5", viewModel.uiState.value.bd032State.selectedSquare)
        assertNull("Piece selection must not trigger animation", viewModel.uiState.value.bd032State.activeTransition)

        // 2. Illegal move attempt: e5 (4, 4) -> c4 (2, 3) (knight jump) -> no animation, piece stays selected
        viewModel.onBd032SquareClicked(2, 3)
        assertNull("Illegal move must not trigger animation", viewModel.uiState.value.bd032State.activeTransition)
        assertEquals("e5", viewModel.uiState.value.bd032State.selectedSquare)

        // 3. Legal non-mating move: e5 (4, 4) -> e8 (4, 7) -> animates smoothly to e8 and locks until retry
        viewModel.onBd032SquareClicked(4, 7)
        val wrongState = viewModel.uiState.value.bd032State
        assertFalse(wrongState.isSolved)
        assertNotNull("Legal non-mating move must animate on the board", wrongState.activeTransition)
        assertEquals("e5", wrongState.activeTransition?.fromSquare)
        assertEquals("e8", wrongState.activeTransition?.toSquare)

        // Retry to return to initial position
        viewModel.retryBd032()

        // 4. Winning checkmate move: e5 (4, 4) -> h8 (7, 7) (Qh8#) -> triggers real BoardTransition!
        viewModel.onBd032SquareClicked(4, 4)
        viewModel.onBd032SquareClicked(7, 7)
        val solvedState = viewModel.uiState.value.bd032State
        assertTrue(solvedState.isSolved)
        assertNotNull("Winning mate move in BD-032 must trigger smooth animation", solvedState.activeTransition)
        assertEquals("e5", solvedState.activeTransition?.fromSquare)
        assertEquals("h8", solvedState.activeTransition?.toSquare)

        // 5. Reset BD-032 -> clears transition and returns to initial position
        viewModel.resetBd032()
        assertNull(viewModel.uiState.value.bd032State.activeTransition)
        assertFalse(viewModel.uiState.value.bd032State.isSolved)
    }

    @Test
    fun `anim test 14 - exact FEN integrity and piece placement after animation finishes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"

        viewModel.resetPgn(pgnRef)
        viewModel.stepPgnNext(pgnRef) // 1. e4
        val pgnState = viewModel.uiState.value.pgnStates[pgnRef]!!
        val expectedFenAfterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        assertEquals(expectedFenAfterE4, pgnState.currentFen)
        assertTrue(pgnState.isTransitioning)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val curPgn = state.pgnStates[pgnRef]!!
            MyApplicationTheme {
                ChessBoardView(
                    fen = curPgn.currentFen,
                    transition = curPgn.activeTransition,
                    onTransitionFinished = { tid -> viewModel.onPgnTransitionFinished(pgnRef, tid) }
                )
            }
        }
        composeTestRule.waitForIdle()

        // After animation completes, isTransitioning is false, activeTransition is cleared, and every square matches FEN
        val finalPgnState = viewModel.uiState.value.pgnStates[pgnRef]!!
        assertFalse(finalPgnState.isTransitioning)
        assertNull(finalPgnState.activeTransition)
        assertEquals(expectedFenAfterE4, finalPgnState.currentFen)

        val parsed = ChessEngine.parseFen(expectedFenAfterE4)
        for (r in 0..7) {
            for (f in 0..7) {
                val sq = "${('a' + f)}${r + 1}"
                val expectedPiece = parsed.board[r][f]
                if (expectedPiece != null) {
                    composeTestRule.onNodeWithTag("static_piece_$sq", useUnmergedTree = true).assertExists()
                } else {
                    composeTestRule.onNodeWithTag("static_piece_$sq", useUnmergedTree = true).assertDoesNotExist()
                }
            }
        }
    }

    private fun saveMidAnimationScreenshot(fileName: String) {
        val screenshotsDir = File("../screenshots").let {
            if (it.parentFile?.exists() == true) it else File("screenshots")
        }
        screenshotsDir.mkdirs()

        val decorViews = android.view.inspector.WindowInspector.getGlobalWindowViews()
        val targetView: View = decorViews.lastOrNull() ?: return
        val width = if (targetView.width > 0) targetView.width else 1080
        val height = if (targetView.height > 0) targetView.height else 1080

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        targetView.draw(canvas)

        val outFile = File(screenshotsDir, fileName)
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    // =========================================================================
    // Version 09 Refinements: Reverse Promotion (4 pieces), Reverse Capture &
    // Reverse En Passant, Animation Cancellation Safety, Full 6-Field FEN Match
    // =========================================================================

    @Test
    fun `anim test 15 - Reverse Promotion for all 4 pieces (Q, R, B, N) moves promoted piece in flight and restores Pawn on arrival`() {
        val previousFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1" // Pawn on a7
        val promoCases = listOf(
            Triple(com.example.model.PieceType.QUEEN, "Q3k3/8/8/8/8/8/8/4K3 b - - 0 1", "♕"),
            Triple(com.example.model.PieceType.ROOK, "R3k3/8/8/8/8/8/8/4K3 b - - 0 1", "♖"),
            Triple(com.example.model.PieceType.BISHOP, "B3k3/8/8/8/8/8/8/4K3 b - - 0 1", "♗"),
            Triple(com.example.model.PieceType.KNIGHT, "N3k3/8/8/8/8/8/8/4K3 b - - 0 1", "♘")
        )

        var currentTestFen by androidx.compose.runtime.mutableStateOf(previousFen)
        var currentTestTransition by androidx.compose.runtime.mutableStateOf<com.example.model.BoardTransition?>(null)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = currentTestFen,
                    transition = currentTestTransition
                )
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        for ((pieceType, promotedFen, expectedSymbol) in promoCases) {
            val reverseTransition = com.example.model.BoardTransitionResolver.resolveReverseTransition(
                transitionId = "rev_promo_${pieceType.name}",
                currentFen = promotedFen,
                previousFen = previousFen
            )
            assertNotNull("Reverse promotion transition for $pieceType must be resolved", reverseTransition)
            assertTrue(reverseTransition!!.isReverse)
            assertEquals("a8", reverseTransition.fromSquare)
            assertEquals("a7", reverseTransition.toSquare)
            assertEquals(
                "Reverse promotion movingPiece in flight must be the promoted piece ($pieceType), NOT Pawn",
                pieceType,
                reverseTransition.movingPiece.type
            )

            // Trigger reverse transition to previousFen and flush snapshot notifications for manual clock
            currentTestFen = previousFen
            currentTestTransition = reverseTransition
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.mainClock.advanceTimeBy(110) // Midpoint of 220ms reverse animation

            // 1. Verify neither a8 nor a7 shows a static piece during flight
            composeTestRule.onNodeWithTag("static_piece_a8", useUnmergedTree = true).assertDoesNotExist()
            composeTestRule.onNodeWithTag("static_piece_a7", useUnmergedTree = true).assertDoesNotExist()

            // 2. Verify the moving piece in flight is the promoted piece (♕, ♖, ♗, or ♘) and positioned between a8 and a7
            val a8Top = composeTestRule.onNodeWithTag("square_a8").fetchSemanticsNode().boundsInRoot.top
            val a7Top = composeTestRule.onNodeWithTag("square_a7").fetchSemanticsNode().boundsInRoot.top
            val movingNode = composeTestRule.onNodeWithTag("moving_piece_primary", useUnmergedTree = true).fetchSemanticsNode()
            val movingTop = movingNode.boundsInRoot.top
            assertTrue(
                "During reverse promotion ($pieceType), moving piece Y ($movingTop) must be strictly between a8 ($a8Top) and a7 ($a7Top)",
                movingTop > a8Top && movingTop < a7Top
            )

            val movingTexts = collectAllSemanticsStrings(movingNode)
            assertTrue(
                "During reverse promotion flight for $pieceType, moving overlay must display '$expectedSymbol'",
                movingTexts.contains(expectedSymbol)
            )
            assertFalse(
                "During reverse promotion flight for $pieceType, moving overlay must NOT display Pawn '♙'",
                movingTexts.contains("♙")
            )

            if (pieceType == com.example.model.PieceType.QUEEN) {
                saveMidAnimationScreenshot("07_reverse_promotion_mid_animation.png")
            }

            // 3. Complete reverse animation -> upon arrival at a7, Pawn (♙) from previousFen is displayed
            composeTestRule.mainClock.advanceTimeBy(200)
            composeTestRule.mainClock.advanceTimeByFrame()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            composeTestRule.mainClock.advanceTimeByFrame()

            composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
            composeTestRule.onNodeWithTag("static_piece_a8", useUnmergedTree = true).assertDoesNotExist()
            val a7Node = composeTestRule.onNodeWithTag("static_piece_a7", useUnmergedTree = true).fetchSemanticsNode()
            val a7Texts = collectAllSemanticsStrings(a7Node)
            assertTrue(
                "After reverse promotion arrival for $pieceType, static square a7 must display Pawn '♙'",
                a7Texts.contains("♙")
            )
        }

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun `anim test 16 - Reverse Capture and Reverse En Passant gradually fade in captured piece without duplicates`() {
        // Part A: Standard Reverse Capture (after exd5, reversing back to e4 with Black pawn fading in on d5)
        val beforeCaptureFen = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 4 10"
        val afterCaptureFen = "4k3/8/8/3P4/8/8/8/4K3 b - - 0 10"

        val revCapture = com.example.model.BoardTransitionResolver.resolveReverseTransition(
            transitionId = "rev_capture_exd5",
            currentFen = afterCaptureFen,
            previousFen = beforeCaptureFen
        )!!

        assertTrue(revCapture.isReverse)
        assertEquals("d5", revCapture.fromSquare)
        assertEquals("e4", revCapture.toSquare)
        assertEquals("d5", revCapture.capturedSquare)
        assertNotNull("Reverse capture must include capturedPiece for gradual fade-in", revCapture.capturedPiece)
        assertFalse(revCapture.capturedPiece!!.isWhite)
        assertEquals(com.example.model.PieceType.PAWN, revCapture.capturedPiece!!.type)

        // Part B: Reverse En Passant (after exd6 e.p., reversing d6->e5 with Black pawn fading in on adjacent d5)
        val beforeEpFen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
        val afterEpFen = "4k3/8/3P4/8/8/8/8/4K3 b - - 0 1"

        val revEp = com.example.model.BoardTransitionResolver.resolveReverseTransition(
            transitionId = "rev_ep_exd6",
            currentFen = afterEpFen,
            previousFen = beforeEpFen
        )!!

        assertTrue(revEp.isReverse)
        assertEquals("d6", revEp.fromSquare)
        assertEquals("e5", revEp.toSquare)
        assertEquals("d5", revEp.capturedSquare) // Adjacent square d5!
        assertNotNull("Reverse en passant must include capturedPiece on d5", revEp.capturedPiece)
        assertFalse(revEp.capturedPiece!!.isWhite)

        var currentFenState by androidx.compose.runtime.mutableStateOf(beforeCaptureFen)
        var currentTransitionState by androidx.compose.runtime.mutableStateOf<com.example.model.BoardTransition?>(revCapture)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = currentFenState,
                    transition = currentTransitionState
                )
            }
        }

        // Mid-animation of Reverse Capture (110ms)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // Static pieces on d5 and e4 are hidden while overlay animates
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_e4", useUnmergedTree = true).assertDoesNotExist()
        // Captured Black pawn is fading in on d5 while White pawn moves d5 -> e4
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertExists()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()

        saveMidAnimationScreenshot("08_reverse_capture_mid_animation.png")

        // Complete Reverse Capture animation
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.advanceTimeByFrame()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertDoesNotExist()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_e4", useUnmergedTree = true).assertExists()

        // Now test Reverse En Passant UI mid-animation
        currentFenState = beforeEpFen
        currentTransitionState = revEp
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        // During Reverse En Passant: d6, e5, and adjacent d5 static pieces are hidden;
        // captured Black pawn fades in on d5 while White pawn moves d6 -> e5
        composeTestRule.onNodeWithTag("static_piece_d6", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_e5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertExists()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()

        // Complete Reverse En Passant animation
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_e5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_d6", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `anim test 17 - Animation cancellation does NOT record completion or invoke onTransitionFinished`() {
        val startFen = ChessEngine.STANDARD_START_FEN
        val afterE4Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "cancel_test_id_1",
            fromFen = startFen,
            toFen = afterE4Fen
        )!!

        val finishedCallbacks = mutableListOf<String>()
        var boundFen by androidx.compose.runtime.mutableStateOf(afterE4Fen)
        var boundTransition by androidx.compose.runtime.mutableStateOf<com.example.model.BoardTransition?>(transition)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = boundFen,
                    transition = boundTransition,
                    onTransitionFinished = { finishedId ->
                        finishedCallbacks.add(finishedId)
                    }
                )
            }
        }

        // Start animation and advance only 80ms (incomplete)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(80)
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()
        assertTrue("Callback must not fire while animation is still in progress", finishedCallbacks.isEmpty())

        // Cancel mid-animation by simulating Reset / diagram switch (boundFen -> startFen, boundTransition -> null)
        boundFen = startFen
        boundTransition = null
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(300)

        assertTrue(
            "Cancelled transition must NOT invoke onTransitionFinished callback",
            finishedCallbacks.isEmpty()
        )
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()

        // Furthermore, because 'cancel_test_id_1' was cancelled and NOT marked completed,
        // re-supplying the same transitionId now MUST animate and complete normally!
        boundFen = afterE4Fen
        boundTransition = transition
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()

        // Let it finish to 220ms
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(
            "Only after full normal completion should onTransitionFinished be invoked once",
            listOf("cancel_test_id_1"),
            finishedCallbacks
        )
    }

    @Test
    fun `anim test 18 - Full 6-field FEN matching rejects transitions with mismatched castling, enPassant, halfMove, or fullMove`() {
        val startFen = ChessEngine.STANDARD_START_FEN
        // Exact legal FEN after 1. e4:
        val validAfterE4 = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"

        assertNotNull(
            "Exact 6-field FEN match must succeed",
            com.example.model.BoardTransitionResolver.resolveForwardTransition("ok", startFen, validAfterE4)
        )

        // 1. Mismatched castling rights ('KQk' instead of 'KQkq')
        val badCastlingFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQk e3 0 1"
        assertNull(
            "Transition must be rejected when castling rights do not match engine result",
            com.example.model.BoardTransitionResolver.resolveForwardTransition("bad_castle", startFen, badCastlingFen)
        )

        // 2. Mismatched en passant square ('-' instead of 'e3')
        val badEnPassantFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
        assertNull(
            "Transition must be rejected when en passant field does not match engine result",
            com.example.model.BoardTransitionResolver.resolveForwardTransition("bad_ep", startFen, badEnPassantFen)
        )

        // 3. Mismatched halfmove clock ('1' instead of '0' after pawn move)
        val badHalfMoveFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 1 1"
        assertNull(
            "Transition must be rejected when halfmove clock does not match engine result",
            com.example.model.BoardTransitionResolver.resolveForwardTransition("bad_half", startFen, badHalfMoveFen)
        )

        // 4. Mismatched fullmove number ('2' instead of '1' after White's first move)
        val badFullMoveFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 2"
        assertNull(
            "Transition must be rejected when fullmove number does not match engine result",
            com.example.model.BoardTransitionResolver.resolveForwardTransition("bad_full", startFen, badFullMoveFen)
        )
    }

    // =========================================================================
    // Version 13 Comprehensive Tests:
    // - Chess Audio System (Settings Persistence, Master/Category Mute, EventId Deduplication,
    //   Sound Type Resolution, PGN Step Sound Emission, Non-Empty Raw WAV Assets)
    // - Redesigned BD-032 Exercise Flow (Default Hidden Legal Moves, Toggle Hint Button,
    //   Animated Wrong Legal Move + Post-Move Lock + Retry, Wrong Capture Move Animation & Sound,
    //   Illegal Move Red Flash & Preserved Selection)
    // - Enhanced 340ms Capture Exit Animation (Alpha + Scale + Impact Highlight)
    // =========================================================================

    @Test
    fun `v13 audio test 1 - verify all 11 local raw sound files exist are non-empty valid WAVs and preloaded in ChessSoundManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundManager = com.example.audio.ChessSoundManager(context)

        for (type in com.example.audio.ChessSoundType.values()) {
            val afd = context.resources.openRawResourceFd(type.rawResId)
            assertNotNull("Raw resource for $type must exist", afd)
            assertTrue("Raw audio file for $type must be non-empty (> 1000 bytes)", afd.length > 1000L)
            afd.close()

            // Verify WAV RIFF header
            val header = ByteArray(12)
            context.resources.openRawResource(type.rawResId).use { input ->
                val read = input.read(header)
                assertEquals(12, read)
            }
            assertEquals("RIFF", String(header, 0, 4))
            assertEquals("WAVE", String(header, 8, 4))
            assertTrue("ChessSoundManager must preload $type", soundManager.isPreloaded(type))
        }
        assertEquals(11, com.example.audio.ChessSoundType.values().size)
        assertEquals(11, soundManager.preloadedSoundCount())
        soundManager.release()
    }

    @Test
    fun `v13 audio test 2 - verify audio settings persistence in UserPreferencesManager and ViewModel`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        // Reset to defaults first
        prefs.saveAudioSettings(com.example.audio.AudioSettingsState())

        val vm1 = ChapterViewModel(app)
        val initial = vm1.uiState.value.audioSettings
        assertTrue(initial.soundEnabled)
        assertEquals(70, initial.volumePercent)
        assertTrue(initial.moveSoundEnabled)
        assertTrue(initial.captureSoundEnabled)
        assertTrue(initial.specialMoveSoundEnabled)
        assertTrue(initial.checkMateSoundEnabled)
        assertTrue(initial.exerciseFeedbackSoundEnabled)

        // Modify settings via ViewModel
        vm1.setMasterSoundEnabled(false)
        vm1.setSoundVolumePercent(45)
        vm1.setMoveSoundEnabled(false)
        vm1.setCaptureSoundEnabled(true)
        vm1.setSpecialMoveSoundEnabled(false)
        vm1.setCheckMateSoundEnabled(true)
        vm1.setExerciseFeedbackSoundEnabled(false)

        // Create a fresh ViewModel instance to verify persistence
        val vm2 = ChapterViewModel(app)
        val restored = vm2.uiState.value.audioSettings
        assertFalse(restored.soundEnabled)
        assertEquals(45, restored.volumePercent)
        assertFalse(restored.moveSoundEnabled)
        assertTrue(restored.captureSoundEnabled)
        assertFalse(restored.specialMoveSoundEnabled)
        assertTrue(restored.checkMateSoundEnabled)
        assertFalse(restored.exerciseFeedbackSoundEnabled)

        // Restore defaults for subsequent tests
        vm2.setMasterSoundEnabled(true)
        vm2.setSoundVolumePercent(70)
        vm2.setMoveSoundEnabled(true)
        vm2.setCaptureSoundEnabled(true)
        vm2.setSpecialMoveSoundEnabled(true)
        vm2.setCheckMateSoundEnabled(true)
        vm2.setExerciseFeedbackSoundEnabled(true)
    }

    @Test
    fun `v13 audio test 3 - verify master mute, category mute, and eventId deduplication in ChessSoundManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = com.example.audio.ChessSoundManager(context)
        val defaultSettings = com.example.audio.AudioSettingsState()

        // 1. EventId deduplication: same eventId must never play more than once
        val ev1 = com.example.audio.ChessSoundEvent(eventId = 1001L, soundType = com.example.audio.ChessSoundType.MOVE_NORMAL)
        assertTrue("First time eventId=1001 is handled must play", manager.handleEvent(ev1, defaultSettings))
        assertFalse("Second time same eventId=1001 is handled must be suppressed", manager.handleEvent(ev1, defaultSettings))
        assertEquals("DUPLICATE_EVENT_ID", manager.playbackHistory.last().suppressedReason)

        // 2. Master mute blocks all categories
        val mutedMaster = defaultSettings.copy(soundEnabled = false)
        for ((idx, type) in com.example.audio.ChessSoundType.values().withIndex()) {
            val ev = com.example.audio.ChessSoundEvent(eventId = 2000L + idx, soundType = type)
            assertFalse("Master mute must block $type", manager.handleEvent(ev, mutedMaster))
            assertEquals("MASTER_DISABLED", manager.playbackHistory.last().suppressedReason)
        }

        // 3. Category mute disables only that specific category while other categories still play
        val captureMutedOnly = defaultSettings.copy(captureSoundEnabled = false)
        val evCapture = com.example.audio.ChessSoundEvent(eventId = 3001L, soundType = com.example.audio.ChessSoundType.CAPTURE)
        val evMove = com.example.audio.ChessSoundEvent(eventId = 3002L, soundType = com.example.audio.ChessSoundType.MOVE_NORMAL)
        val evCastle = com.example.audio.ChessSoundEvent(eventId = 3003L, soundType = com.example.audio.ChessSoundType.CASTLING)
        assertFalse("Capture sound must be blocked when captureSoundEnabled=false", manager.handleEvent(evCapture, captureMutedOnly))
        assertEquals("CATEGORY_DISABLED", manager.playbackHistory.last().suppressedReason)
        assertTrue("Normal move sound must still play when only capture is muted", manager.handleEvent(evMove, captureMutedOnly))
        assertTrue("Castling special sound must still play when only capture is muted", manager.handleEvent(evCastle, captureMutedOnly))

        manager.release()
    }

    @Test
    fun `v13 audio test 4 - verify distinct sound types for Normal, Capture, Castling, EnPassant, Promotion, Check, Checkmate, and PGN step changes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.saveAudioSettings(com.example.audio.AudioSettingsState())
        val viewModel = ChapterViewModel(app)
        val sm = viewModel.soundManager

        // 1. Initial load must NOT play any sound
        assertTrue("No sound may play on initial chapter load", sm.playedEvents.isEmpty())

        // 2. Verify resolveSoundForTransition across all move types
        // Normal move: 1. e4
        val tNormal = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_norm",
            ChessEngine.STANDARD_START_FEN,
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        )!!
        assertEquals(com.example.audio.ChessSoundType.MOVE_NORMAL, com.example.audio.ChessSoundManager.resolveSoundForTransition(tNormal))

        // Capture move: exd5
        val tCapture = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_cap",
            "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1",
            "4k3/8/8/3P4/8/8/8/4K3 b - - 0 1"
        )!!
        assertEquals(com.example.audio.ChessSoundType.CAPTURE, com.example.audio.ChessSoundManager.resolveSoundForTransition(tCapture))

        // Castling: O-O
        val tCastle = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_castle",
            "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1",
            "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 b kq - 1 1"
        )!!
        assertEquals(com.example.audio.ChessSoundType.CASTLING, com.example.audio.ChessSoundManager.resolveSoundForTransition(tCastle))

        // En Passant: exd6 e.p.
        val tEp = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_ep",
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            "4k3/8/3P4/8/8/8/8/4K3 b - - 0 1"
        )!!
        assertEquals(com.example.audio.ChessSoundType.EN_PASSANT, com.example.audio.ChessSoundManager.resolveSoundForTransition(tEp))

        // Promotion (non-check): a7 -> a8=Q with Black king on h6
        val tPromo = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_promo",
            "8/P7/7k/8/8/8/8/4K3 w - - 0 1",
            "Q7/8/7k/8/8/8/8/4K3 b - - 0 1"
        )!!
        assertEquals(com.example.audio.ChessSoundType.PROMOTION, com.example.audio.ChessSoundManager.resolveSoundForTransition(tPromo))

        // Check (non-mate): Qe5 -> e4+ against White king on h4 in BD-032 (King can escape to h3/h5)
        val tCheck = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_check",
            "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1",
            "8/8/1p6/1k4P1/4q2K/8/8/6r1 w - - 1 2"
        )!!
        assertTrue(tCheck.isCheck)
        assertFalse(tCheck.isCheckmate)
        assertEquals(com.example.audio.ChessSoundType.CHECK, com.example.audio.ChessSoundManager.resolveSoundForTransition(tCheck))

        // Checkmate: Qh8# in BD-032
        val tMate = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "t_mate",
            "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1",
            "7q/8/1p6/1k4P1/7K/8/8/6r1 w - - 1 2"
        )!!
        assertTrue(tMate.isCheckmate)
        assertEquals(com.example.audio.ChessSoundType.CHECKMATE, com.example.audio.ChessSoundManager.resolveSoundForTransition(tMate))

        // 3. Verify PGN replay emits sound ONLY on actual step changes
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"
        sm.clearHistoryForTesting()

        // Initially at end (step 7): calling stepPgnNext at end must NOT emit any sound
        viewModel.stepPgnNext(pgnRef)
        assertTrue("stepPgnNext at end must not emit sound", sm.playedEvents.isEmpty())

        // Reset PGN to start: resetPgn must NOT emit move sound
        viewModel.resetPgn(pgnRef)
        assertTrue("resetPgn must not emit move sound", sm.playedEvents.isEmpty())

        // Calling stepPgnPrev at start must NOT emit any sound
        viewModel.stepPgnPrev(pgnRef)
        assertTrue("stepPgnPrev at start must not emit sound", sm.playedEvents.isEmpty())

        // Step 1 (1. e4) -> emits MOVE_NORMAL
        viewModel.stepPgnNext(pgnRef)
        assertEquals(1, sm.playedEvents.size)
        assertEquals(com.example.audio.ChessSoundType.MOVE_NORMAL, sm.playedEvents.last().soundType)

        // Step backward to start -> emits MOVE_NORMAL
        viewModel.stepPgnPrev(pgnRef)
        assertEquals(2, sm.playedEvents.size)
        assertEquals(com.example.audio.ChessSoundType.MOVE_NORMAL, sm.playedEvents.last().soundType)

        // Step all 7 moves to Scholar's Mate (4. Qxf7#) -> final move must emit CHECKMATE
        for (i in 1..7) {
            viewModel.stepPgnNext(pgnRef)
        }
        assertEquals(com.example.audio.ChessSoundType.CHECKMATE, sm.playedEvents.last().soundType)
    }

    @Test
    fun `v13 bd032 test 5 - verify complete BD-032 interactive exercise flow in Compose UI including legal hint toggle, illegal move red flash, wrong capture move, and retry`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setBd032Solved(false)
        prefs.saveAudioSettings(com.example.audio.AudioSettingsState())

        val viewModel = ChapterViewModel(app)
        val sm = viewModel.soundManager
        sm.clearHistoryForTesting()
        val initialFen = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1"

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

        // 1. Select Black Queen at e5 -> by default showLegalMovesHint is false, so visibleTargetSquares is empty
        composeTestRule.onNodeWithTag("square_e5").performClick()
        composeTestRule.waitForIdle()
        assertEquals("e5", viewModel.uiState.value.bd032State.selectedSquare)
        assertFalse(viewModel.uiState.value.bd032State.showLegalMovesHint)
        assertTrue(viewModel.uiState.value.bd032State.visibleTargetSquares.isEmpty())

        // 2. Click "نمایش حرکت‌های قانونی" (exercise_legal_hint_btn_EX_CH01_BD032) -> legal target squares become visible
        composeTestRule.onNodeWithTag("exercise_legal_hint_btn_EX_CH01_BD032").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.bd032State.showLegalMovesHint)
        assertTrue("Legal moves for Queen on e5 must be visible when hint is toggled on", viewModel.uiState.value.bd032State.visibleTargetSquares.isNotEmpty())

        // 3. Attempt an illegal chess move: e5 -> c4 (knight jump) -> board unchanged, Queen on e5 stays selected, red highlight shown, ILLEGAL_MOVE sound played
        sm.clearHistoryForTesting()
        composeTestRule.onNodeWithTag("square_c4").performClick()
        composeTestRule.waitForIdle()
        val stateAfterIllegal = viewModel.uiState.value.bd032State
        assertEquals(initialFen, stateAfterIllegal.currentFen)
        assertEquals("e5", stateAfterIllegal.selectedSquare) // Still selected!
        assertEquals("این حرکت طبق قوانین شطرنج مجاز نیست.", stateAfterIllegal.feedbackMessage)
        composeTestRule.onNodeWithText("این حرکت طبق قوانین شطرنج مجاز نیست.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("illegal_highlight_c4", useUnmergedTree = true).assertExists()
        assertEquals(listOf(com.example.audio.ChessSoundType.ILLEGAL_MOVE), sm.playedEvents.map { it.soundType })

        // 4. Since e5 is STILL selected after the illegal move, immediately play a wrong legal CAPTURE move: Qxg5+ (e5 -> g5)
        sm.clearHistoryForTesting()
        composeTestRule.onNodeWithTag("square_g5").performClick()
        // Wait for the 340ms capture animation to finish
        composeTestRule.waitForIdle()

        val stateAfterWrongCapture = viewModel.uiState.value.bd032State
        assertEquals(com.example.ui.Bd032ExercisePhase.WRONG_LEGAL_MOVE, stateAfterWrongCapture.phase)
        assertEquals("8/8/1p6/1k4q1/7K/8/8/6r1 w - - 0 2", stateAfterWrongCapture.currentFen)
        assertEquals("این حرکت قانونی است، اما مسئله را حل نمی‌کند. دوباره تلاش کنید.", stateAfterWrongCapture.feedbackMessage)
        composeTestRule.onNodeWithText("این حرکت قانونی است، اما مسئله را حل نمی‌کند. دوباره تلاش کنید.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exercise_retry_btn_EX_CH01_BD032").assertIsDisplayed()

        // Verify both CAPTURE sound (at move start) and EXERCISE_WRONG sound (after animation end) were emitted in order!
        assertEquals(
            listOf(com.example.audio.ChessSoundType.CAPTURE, com.example.audio.ChessSoundType.EXERCISE_WRONG),
            sm.playedEvents.map { it.soundType }
        )

        saveRootViewScreenshot("09_bd032_wrong_legal_move_locked.png")

        // 5. Click "تلاش دوباره" (exercise_retry_btn_EX_CH01_BD032) -> returns to initial FEN, clears feedback, resets showLegalMovesHint to false
        composeTestRule.onNodeWithTag("exercise_retry_btn_EX_CH01_BD032").performClick()
        composeTestRule.waitForIdle()
        val stateAfterRetry = viewModel.uiState.value.bd032State
        assertEquals(initialFen, stateAfterRetry.currentFen)
        assertNull(stateAfterRetry.selectedSquare)
        assertNull(stateAfterRetry.feedbackMessage)
        assertFalse("Retry must reset showLegalMovesHint to false", stateAfterRetry.showLegalMovesHint)

        // 6. Solve with Qh8# (e5 -> h8) and verify CHECKMATE + EXERCISE_CORRECT sounds
        sm.clearHistoryForTesting()
        composeTestRule.onNodeWithTag("square_e5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("square_h8").performClick()
        composeTestRule.waitForIdle()

        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        assertEquals(com.example.ui.Bd032ExercisePhase.SOLVED, viewModel.uiState.value.bd032State.phase)
        assertEquals(
            listOf(com.example.audio.ChessSoundType.CHECKMATE, com.example.audio.ChessSoundType.EXERCISE_CORRECT),
            sm.playedEvents.map { it.soundType }
        )
    }

    @Test
    fun `v13 capture anim test 6 - verify 340ms capture exit animation with alpha fade, scale shrink, impact highlight, and zero ghost pieces`() {
        val fromFen = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"
        val toFen = "4k3/8/8/3P4/8/8/8/4K3 b - - 0 1" // exd5
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "v13_capture_340ms",
            fromFen = fromFen,
            toFen = toFen
        )!!

        assertEquals(340, com.example.model.BoardTransitionResolver.CAPTURE_EXIT_DURATION_MS)
        assertTrue(com.example.model.BoardTransitionResolver.CAPTURE_EXIT_DURATION_MS in 320..380)

        val finishedIds = mutableListOf<String>()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = toFen,
                    transition = transition,
                    onTransitionFinished = { finishedIds.add(it) }
                )
            }
        }

        // 1. At 110ms (mid-flight of 220ms attacker move & 340ms capture exit & 380ms radial capture pulse):
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)
        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertExists()
        composeTestRule.onNodeWithTag("capture_pulse_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("capture_impact_highlight_d5").assertDoesNotExist()
        assertTrue(finishedIds.isEmpty())

        // 2. At 260ms (attacker 220ms flight has finished, but 340ms captured piece exit is still in its final fade+shrink phase!):
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertExists()
        assertTrue("Transition must not finish before 340ms capture exit completes", finishedIds.isEmpty())

        // 3. Advance past 380ms -> capture exit and capture pulse complete, callback fires, and zero ghost/duplicate pieces remain
        composeTestRule.mainClock.advanceTimeBy(180)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(listOf("v13_capture_340ms"), finishedIds)
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5").assertDoesNotExist()
        composeTestRule.onNodeWithTag("capture_pulse_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_e4", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `v13 audio settings ui test 7 - verify AudioSettingsDialog opens from both SectionsListScreen and ReaderScreen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.setTab(AppTab.SECTIONS_LIST)

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Open Audio Settings from SectionsListScreen
        composeTestRule.onNodeWithTag("nav_audio_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audio_settings_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("فعال بودن صداها").assertIsDisplayed()
        composeTestRule.onNodeWithText("صدای حرکت مهره‌ها").assertIsDisplayed()
        composeTestRule.onNodeWithText("صدای گرفتن مهره").assertIsDisplayed()
        composeTestRule.onNodeWithText("صدای حرکت‌های ویژه").assertIsDisplayed()
        composeTestRule.onNodeWithText("صدای کیش و مات").assertIsDisplayed()
        composeTestRule.onNodeWithText("صدای بازخورد تمرین").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_test_sound_btn").assertIsDisplayed()

        saveRootViewScreenshot("10_audio_settings_dialog.png")

        // Click test sound button
        viewModel.soundManager.clearHistoryForTesting()
        composeTestRule.onNodeWithTag("audio_test_sound_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.soundManager.playedEvents.size)

        // Close dialog
        composeTestRule.onNodeWithTag("audio_dialog_close_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audio_settings_dialog").assertDoesNotExist()

        // 2. Enter ReaderScreen and open Audio Settings from ReaderScreen top bar
        viewModel.selectSection(0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_audio_settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("audio_settings_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_dialog_close_btn").performClick()
        composeTestRule.waitForIdle()
    }

    // =========================================================================
    // Version 14 Comprehensive Tests:
    // - Part 1: Studio-Grade Acoustic WAV Verification & Stream Coordination
    // - Part 2-4: Educational Move Trail System (All 17 Mandatory Scenarios)
    // - Part 5: PGN Previous/Next Move Controls & Static Diagram Layout (All 12 Scenarios)
    // - Part 6: Reader Settings Persistence (showMoveTrail & EducationalPlaybackSpeed)
    // =========================================================================

    @Test
    fun `v14 audio upgrade test 1 - verify all 11 WAV files are 44100Hz 16-bit PCM mono with zero clipping, distinct acoustic envelopes, and double-tap castling`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val signatures = mutableSetOf<String>()

        for (type in com.example.audio.ChessSoundType.values()) {
            val bytes = context.resources.openRawResource(type.rawResId).use { it.readBytes() }
            assertTrue("WAV file for $type must be > 10KB", bytes.size > 10_000)

            val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WAVE", String(bytes, 8, 4))
            val audioFormat = bb.getShort(20).toInt()
            val channels = bb.getShort(22).toInt()
            val sampleRate = bb.getInt(24)
            val bitsPerSample = bb.getShort(34).toInt()
            val dataBytes = bb.getInt(40)

            assertEquals("PCM format required for $type", 1, audioFormat)
            assertEquals("Mono channel required for $type", 1, channels)
            assertEquals("44100 Hz sample rate required for $type", 44100, sampleRate)
            assertEquals("16-bit PCM required for $type", 16, bitsPerSample)

            val numSamples = dataBytes / 2
            val durationMs = numSamples * 1000.0 / sampleRate
            assertTrue("Duration for $type ($durationMs ms) must be within 110..650 ms", durationMs in 110.0..650.0)

            var maxAbs = 0
            var firstActiveSample = -1
            var lastActiveSample = 0
            for (i in 0 until numSamples) {
                val sample = bb.getShort(44 + i * 2).toInt()
                val abs = kotlin.math.abs(sample)
                if (abs > maxAbs) maxAbs = abs
                if (abs > 120) {
                    if (firstActiveSample == -1) firstActiveSample = i
                    lastActiveSample = i
                }
            }

            val peakNorm = maxAbs / 32767.0
            assertTrue("Peak normalized amplitude for $type ($peakNorm) must be in 0.50..0.92 (zero clipping)", peakNorm in 0.50..0.92)

            val leadingSilenceMs = firstActiveSample * 1000.0 / sampleRate
            val trailingSilenceMs = (numSamples - 1 - lastActiveSample) * 1000.0 / sampleRate
            assertTrue("Leading silence for $type ($leadingSilenceMs ms) must be trimmed (< 5ms)", leadingSilenceMs < 5.0)
            assertTrue("Trailing silence for $type ($trailingSilenceMs ms) must be trimmed (< 35ms)", trailingSilenceMs < 35.0)

            signatures.add("${bytes.size}_${maxAbs}_${firstActiveSample}_${lastActiveSample}")

            // Verify Castling specifically has two distinct impulse peaks (0..40ms and 75..125ms)
            if (type == com.example.audio.ChessSoundType.CASTLING) {
                var peakFirstTap = 0
                var dipBetweenTaps = 0
                var peakSecondTap = 0
                for (i in 0 until numSamples) {
                    val tMs = i * 1000.0 / sampleRate
                    val abs = kotlin.math.abs(bb.getShort(44 + i * 2).toInt())
                    when {
                        tMs in 0.0..35.0 -> if (abs > peakFirstTap) peakFirstTap = abs
                        tMs in 55.0..78.0 -> if (abs > dipBetweenTaps) dipBetweenTaps = abs
                        tMs in 80.0..120.0 -> if (abs > peakSecondTap) peakSecondTap = abs
                    }
                }
                assertTrue("Castling must have strong first wooden tap", peakFirstTap > 15000)
                assertTrue("Castling must have strong second wooden tap around 82ms", peakSecondTap > 15000)
                assertTrue("Castling must decay between first and second taps", dipBetweenTaps < peakFirstTap && dipBetweenTaps < peakSecondTap)
            }
        }

        assertEquals("All 11 chess sounds must have unique acoustic waveforms", 11, signatures.size)
    }

    @Test
    fun `v14 move trail test 2 - forward educational move displays ghost piece at origin, directional arrow, glow, and single full-opacity piece at destination`() {
        val fromFen = "4k3/8/8/8/8/8/8/R3K3 w Q - 0 1"
        val toFen = "4k3/8/8/R7/8/8/8/4K3 b - - 1 1" // Rook a1 -> a5
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            transitionId = "v14_rook_a1_a5",
            fromFen = fromFen,
            toFen = toFen
        )!!
        val moveTrail = com.example.model.BoardTransitionResolver.createMoveTrail(
            transition = transition,
            speed = com.example.data.EducationalPlaybackSpeed.NORMAL
        )!!

        assertEquals("رخ: از خانه a1 به خانه a5", moveTrail.descriptionFa)
        assertEquals(listOf("a1", "a5"), moveTrail.primaryPathSquares)

        val trailFinishedIds = mutableListOf<String>()
        val persistentDesc = com.example.model.BoardTransitionResolver.buildPersistentMoveDescriptionFa(transition)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    androidx.compose.foundation.layout.Column {
                        ChessBoardView(
                            fen = toFen,
                            transition = transition,
                            moveTrail = moveTrail,
                            showMoveTrail = true,
                            onMoveTrailFinished = { trailFinishedIds.add(it) }
                        )
                        com.example.ui.components.PersistentMoveDescriptionBar(
                            descriptionFa = persistentDesc
                        )
                    }
                }
            }
        }

        // 1. Mid-flight (at 225ms of 450ms educational move):
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(225)

        // Ghost piece is at a1 with exact same piece type (♖), color (white), and #438F8A teal tint
        composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_color_ghost_a1_white", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_tint_ghost_a1_teal", useUnmergedTree = true).assertExists()
        val ghostTexts = collectAllSemanticsStrings(
            composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertTrue("Ghost piece at a1 must be White Rook (♖)", ghostTexts.contains("♖"))

        // Directional arrow canvas and outside persistent move description exist; square borders do NOT exist
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("trail_source_marker_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_dest_glow_a5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("move_trail_caption", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("persistent_move_description", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("رخ سفید: از خانه a1 به خانه a5").assertIsDisplayed()

        // During flight, static_piece_a1 and static_piece_a5 do not exist; moving_piece_primary is in flight
        composeTestRule.onNodeWithTag("static_piece_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_a5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("moving_piece_primary", useUnmergedTree = true).assertExists()

        // 2. After piece arrives at destination (advance to 500ms -> in the 800ms hold window):
        composeTestRule.mainClock.advanceTimeBy(275)
        composeTestRule.mainClock.advanceTimeByFrame()

        // Real full-opacity piece is now at a5, moving overlay is gone, and ghost piece + arrow remain visible during hold!
        composeTestRule.onNodeWithTag("moving_piece_primary", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("static_piece_a5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertExists()
        assertTrue("Trail must not finish during hold phase", trailFinishedIds.isEmpty())

        saveMidAnimationScreenshot("12_educational_replay_ghost_and_arrow.png")

        // 3. Advance past hold (800ms) + fade (320ms) -> trail fades out completely and fires callback, while persistent description stays!
        composeTestRule.mainClock.advanceTimeBy(1200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(listOf("v14_rook_a1_a5"), trailFinishedIds)
        composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_source_marker_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_dest_glow_a5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("رخ سفید: از خانه a1 به خانه a5").assertIsDisplayed()
    }

    @Test
    fun `v14 move trail test 3 - Knight move renders 3-square L-shaped path and specialized special moves (Castling, En Passant, Promotion)`() {
        // 1. Knight move: Ng1 -> f3
        val knightFromFen = ChessEngine.STANDARD_START_FEN
        val knightToFen = "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 1 1"
        val knightTransition = com.example.model.BoardTransitionResolver.resolveForwardTransition(
            "v14_knight_g1_f3", knightFromFen, knightToFen
        )!!
        val knightTrail = com.example.model.BoardTransitionResolver.createMoveTrail(knightTransition)!!

        // Must be a 3-point L-shaped path [g1, g3, f3], NOT a 2-point diagonal line
        assertEquals(3, knightTrail.primaryPathSquares.size)
        assertEquals(listOf("g1", "g3", "f3"), knightTrail.primaryPathSquares)
        assertEquals("اسب: از خانه g1 به خانه f3", knightTrail.descriptionFa)

        // Also test Knight horizontal-first 2-file jump: Nb1 -> d2 (when d2 is empty)
        val knightHFrom = "4k3/8/8/8/8/8/8/1N2K3 w - - 0 1"
        val knightHTo = "4k3/8/8/8/8/8/3N4/4K3 b - - 1 1"
        val knightHTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v14_nb1_d2", knightHFrom, knightHTo)!!
        val knightHTrail = com.example.model.BoardTransitionResolver.createMoveTrail(knightHTrans)!!
        assertEquals(listOf("b1", "d1", "d2"), knightHTrail.primaryPathSquares)

        // Render Knight L-shaped trail and capture screenshot
        var currentFen by androidx.compose.runtime.mutableStateOf(knightToFen)
        var currentTrans by androidx.compose.runtime.mutableStateOf<com.example.model.BoardTransition?>(knightTransition)
        var currentTrail by androidx.compose.runtime.mutableStateOf<com.example.model.MoveTrailState?>(knightTrail)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    androidx.compose.foundation.layout.Column {
                        ChessBoardView(
                            fen = currentFen,
                            transition = currentTrans,
                            moveTrail = currentTrail,
                            showMoveTrail = true
                        )
                        com.example.ui.components.PersistentMoveDescriptionBar(
                            descriptionFa = com.example.model.BoardTransitionResolver.buildPersistentMoveDescriptionFa(currentTrans)
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(500) // In hold phase after arrival at f3
        composeTestRule.onNodeWithTag("ghost_piece_g1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_tint_ghost_g1_teal", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("static_piece_f3", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("اسب سفید: از خانه g1 به خانه f3").assertIsDisplayed()
        saveMidAnimationScreenshot("13_knight_move_l_shaped_trail.png")

        // 2. Castling (O-O): King e1 -> g1 and Rook h1 -> f1 simultaneously
        val castleFrom = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
        val castleTo = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 b kq - 1 1"
        val castleTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v14_castle_oo", castleFrom, castleTo)!!
        val castleTrail = com.example.model.BoardTransitionResolver.createMoveTrail(castleTrans)!!

        assertEquals(listOf("e1", "g1"), castleTrail.primaryPathSquares)
        assertEquals(listOf("h1", "f1"), castleTrail.secondaryPathSquares)
        assertEquals("قلعه کوتاه: شاه از e1 به g1 و رخ از h1 به f1", castleTrail.descriptionFa)

        currentFen = castleTo
        currentTrans = castleTrans
        currentTrail = castleTrail
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(500) // Hold phase

        // Both King ghost on e1 and Rook ghost on h1 must exist simultaneously with teal tint and NO square markers!
        composeTestRule.onNodeWithTag("ghost_piece_e1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("ghost_piece_h1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_tint_ghost_e1_teal", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_tint_ghost_h1_teal", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("trail_source_marker_e1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_source_marker_h1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_dest_glow_g1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_dest_glow_f1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("قلعه کوتاه سفید: شاه از e1 به g1 و رخ از h1 به f1").assertIsDisplayed()
        saveMidAnimationScreenshot("14_castling_dual_move_trail.png")

        // 3. En Passant (e5 -> d6 capturing pawn on d5):
        val epFrom = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
        val epTo = "4k3/8/3P4/8/8/8/8/4K3 b - - 0 1"
        val epTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v14_ep_exd6", epFrom, epTo)!!
        val epTrail = com.example.model.BoardTransitionResolver.createMoveTrail(epTrans)!!

        assertEquals("e5", epTrail.fromSquare)
        assertEquals("d6", epTrail.toSquare)
        assertEquals("d5", epTrail.capturedSquare)
        assertEquals("آن‌پاسان: پیاده از خانه e5 به خانه d6 (گرفتن پیاده در d5)", epTrail.descriptionFa)

        currentFen = epTo
        currentTrans = epTrans
        currentTrail = epTrail
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(180) // Inside 380ms capture pulse window

        composeTestRule.onNodeWithTag("ghost_piece_e5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_tint_ghost_e5_teal", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("capture_pulse_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("trail_dest_glow_d6", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_ep_captured_marker_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("آن‌پاسان: پیاده سفید از خانه e5 به خانه d6 (گرفتن پیاده در d5)").assertIsDisplayed()

        // 4. Promotion (a7 -> a8=Q): Pawn ghost on a7 (♙) while promoted Queen (♕) is on a8
        val promoFrom = "8/P7/7k/8/8/8/8/4K3 w - - 0 1"
        val promoTo = "Q7/8/7k/8/8/8/8/4K3 b - - 0 1"
        val promoTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v14_promo_a8q", promoFrom, promoTo)!!
        val promoTrail = com.example.model.BoardTransitionResolver.createMoveTrail(promoTrans)!!

        assertEquals(com.example.model.PieceType.PAWN, promoTrail.piece.type)
        assertEquals(com.example.model.PieceType.QUEEN, promoTrail.promotionPiece?.type)
        assertEquals("ارتقای پیاده: پیاده از خانه a7 به خانه a8 و تبدیل به وزیر", promoTrail.descriptionFa)

        currentFen = promoTo
        currentTrans = promoTrans
        currentTrail = promoTrail
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(500)

        val promoGhostTexts = collectAllSemanticsStrings(
            composeTestRule.onNodeWithTag("ghost_piece_a7", useUnmergedTree = true).fetchSemanticsNode()
        )
        val promoDestTexts = collectAllSemanticsStrings(
            composeTestRule.onNodeWithTag("static_piece_a8", useUnmergedTree = true).fetchSemanticsNode()
        )
        assertTrue("Origin ghost on a7 must remain Pawn (♙)", promoGhostTexts.contains("♙"))
        assertTrue("Destination piece on a8 must be promoted Queen (♕)", promoDestTexts.contains("♕"))

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun `v14 move trail test 4 - new forward move replaces previous trail, Undo clears trail immediately, initial load has no trail, and page or section change clears trail`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.showMoveTrail = true
        prefs.playbackSpeed = com.example.data.EducationalPlaybackSpeed.NORMAL
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"

        // 1. Initial load has no MoveTrail
        assertNull("Initial load must not have an active MoveTrail", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)

        // 2. Reset PGN has no MoveTrail
        viewModel.resetPgn(pgnRef)
        assertNull("resetPgn must not create a MoveTrail", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)

        // 3. Step 1 (1. e4) creates MoveTrail for e2 -> e4
        viewModel.stepPgnNext(pgnRef)
        val trailStep1 = viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail
        assertNotNull("stepPgnNext must create an activeMoveTrail", trailStep1)
        assertEquals("e2", trailStep1?.fromSquare)
        assertEquals("e4", trailStep1?.toSquare)

        // 4. Immediately stepping to Step 2 (1... e5) before Step 1 trail finishes replaces it with e7 -> e5
        viewModel.stepPgnNext(pgnRef)
        val trailStep2 = viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail
        assertNotNull(trailStep2)
        assertEquals("e7", trailStep2?.fromSquare)
        assertEquals("e5", trailStep2?.toSquare)
        assertNotEquals(trailStep1?.eventId, trailStep2?.eventId)

        // 5. Stepping backward (Undo / Previous move: stepPgnPrev) immediately clears activeMoveTrail to null!
        viewModel.stepPgnPrev(pgnRef)
        val stateAfterUndo = viewModel.uiState.value.pgnStates[pgnRef]!!
        assertNull("stepPgnPrev (Undo) must NOT show a MoveTrail and must clear activeMoveTrail", stateAfterUndo.activeMoveTrail)
        assertNotNull("stepPgnPrev still performs smooth reverse piece movement", stateAfterUndo.activeTransition)
        assertTrue(stateAfterUndo.activeTransition!!.isReverse)

        // 6. Select Section 1 (multi-page section), step forward to create an active trail, then verify page navigation clears it
        viewModel.selectSection(1)
        viewModel.stepPgnNext(pgnRef)
        assertNotNull(viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
        assertTrue(viewModel.nextPage())
        assertNull("Changing page must clear activeMoveTrail", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)

        // 7. Step forward again, then verify section change clears it
        viewModel.stepPgnNext(pgnRef)
        assertNotNull(viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
        viewModel.selectSection(0)
        assertNull("Changing section must clear activeMoveTrail", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)

        // 8. Step forward again, then verify jumpPgnToEnd clears it
        viewModel.stepPgnNext(pgnRef)
        assertNotNull(viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
        viewModel.jumpPgnToEnd(pgnRef)
        assertNull("jumpPgnToEnd must clear activeMoveTrail", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
    }

    @Test
    fun `v14 move trail test 5 - verify showMoveTrail toggle, EducationalPlaybackSpeed persistence (600ms, 450ms, 300ms), viewport scaling, and non-blocking clicks`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.showMoveTrail = true
        prefs.playbackSpeed = com.example.data.EducationalPlaybackSpeed.NORMAL

        val vm1 = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"
        assertTrue(vm1.uiState.value.showMoveTrail)
        assertEquals(com.example.data.EducationalPlaybackSpeed.NORMAL, vm1.uiState.value.playbackSpeed)

        // 1. Verify SLOW (600ms), NORMAL (450ms), FAST (300ms) durations in MoveTrailState
        for (speed in com.example.data.EducationalPlaybackSpeed.values()) {
            vm1.setPlaybackSpeed(speed)
            vm1.resetPgn(pgnRef)
            vm1.stepPgnNext(pgnRef)
            val trail = vm1.uiState.value.pgnStates[pgnRef]?.activeMoveTrail
            assertNotNull(trail)
            assertEquals(speed.moveDurationMs, trail?.moveDurationMs)
        }

        // 2. Disable showMoveTrail -> activeMoveTrail is immediately cleared and subsequent stepPgnNext does not create trail
        vm1.setShowMoveTrail(false)
        assertFalse(vm1.uiState.value.showMoveTrail)
        assertNull("Disabling showMoveTrail must clear activeMoveTrail immediately", vm1.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)

        vm1.soundManager.clearHistoryForTesting()
        vm1.stepPgnNext(pgnRef)
        val stateTrailOff = vm1.uiState.value.pgnStates[pgnRef]!!
        assertNull("When showMoveTrail is false, stepPgnNext must not create MoveTrail", stateTrailOff.activeMoveTrail)
        assertNotNull("Piece transition animation must still work when showMoveTrail is false", stateTrailOff.activeTransition)
        assertEquals("Sound must still play when showMoveTrail is false", 1, vm1.soundManager.playedEvents.size)

        // 3. Verify persistence in a new ViewModel instance
        val vm2 = ChapterViewModel(app)
        assertFalse(vm2.uiState.value.showMoveTrail)
        assertEquals(com.example.data.EducationalPlaybackSpeed.FAST, vm2.uiState.value.playbackSpeed)

        // Restore defaults
        vm2.setShowMoveTrail(true)
        vm2.setPlaybackSpeed(com.example.data.EducationalPlaybackSpeed.NORMAL)

        // 4. Verify viewport size responsiveness (280dp vs 420dp) and that MoveTrail overlay does NOT block square clicks
        val clickedSquares = mutableListOf<Pair<Int, Int>>()
        var boardSizePx by androidx.compose.runtime.mutableStateOf(280)
        vm2.resetPgn(pgnRef)
        vm2.stepPgnNext(pgnRef)
        val activePgn = vm2.uiState.value.pgnStates[pgnRef]!!

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = activePgn.currentFen,
                    transition = null,
                    moveTrail = activePgn.activeMoveTrail,
                    showMoveTrail = true,
                    modifier = Modifier.size(boardSizePx.dp),
                    onSquareClick = { file, rank -> clickedSquares.add(file to rank) }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Ghost piece on e2 must align accurately with square_e2 bounds at 280dp
        val e2BoundsSmall = composeTestRule.onNodeWithTag("square_e2").fetchSemanticsNode().boundsInRoot
        val ghostBoundsSmall = composeTestRule.onNodeWithTag("ghost_piece_e2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(e2BoundsSmall.center.x, ghostBoundsSmall.center.x, 2.0f)
        assertEquals(e2BoundsSmall.center.y, ghostBoundsSmall.center.y, 2.0f)

        // Resize board to 420dp and verify ghost piece stays aligned with square_e2
        boardSizePx = 420
        composeTestRule.waitForIdle()
        val e2BoundsLarge = composeTestRule.onNodeWithTag("square_e2").fetchSemanticsNode().boundsInRoot
        val ghostBoundsLarge = composeTestRule.onNodeWithTag("ghost_piece_e2", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(e2BoundsLarge.width > e2BoundsSmall.width)
        assertEquals(e2BoundsLarge.center.x, ghostBoundsLarge.center.x, 2.0f)
        assertEquals(e2BoundsLarge.center.y, ghostBoundsLarge.center.y, 2.0f)

        // Clicking square_e2 (file=4, rank=1) and square_e4 (file=4, rank=3) while MoveTrail overlay is active must trigger onSquareClick normally
        composeTestRule.onNodeWithTag("square_e2").performClick()
        composeTestRule.onNodeWithTag("square_e4").performClick()
        assertEquals(listOf(4 to 1, 4 to 3), clickedSquares)
    }

    @Test
    fun `v14 replay controls test 6 - verify static diagrams have no PGN controls or reserved space, and PGN diagrams have RTL-correct Previous and Next buttons`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.showMoveTrail = true
        val viewModel = ChapterViewModel(app)

        var selectedDiagramId by androidx.compose.runtime.mutableStateOf("BD-025")

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val diagram = state.diagrams.first { it.diagramId == selectedDiagramId }
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DiagramCard(
                        diagram = diagram,
                        state = state,
                        viewModel = viewModel,
                        fontMultiplier = 1.0f
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify all 6 static/exercise diagrams (pgn_ref == null) do NOT render PgnControlsView
        val staticIds = listOf("BD-025", "BD-026", "BD-027", "BD-028", "BD-032", "BD-033")
        for (id in staticIds) {
            selectedDiagramId = id
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("pgn_prev_btn").assertDoesNotExist()
            composeTestRule.onNodeWithTag("pgn_next_btn").assertDoesNotExist()
            if (id == "BD-025") {
                saveRootViewScreenshot("11_static_diagram_no_move_controls.png")
            }
        }

        // 2. Switch to PGN diagram BD-029 (SCHOLARS_MATE_CANDIDATE)
        selectedDiagramId = "BD-029"
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("pgn_controls_bar_SCHOLARS_MATE_CANDIDATE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pgn_prev_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pgn_next_btn").assertIsDisplayed()
        composeTestRule.onNodeWithText("حرکت قبلی").assertIsDisplayed()
        composeTestRule.onNodeWithText("حرکت بعدی").assertIsDisplayed()

        // Verify RTL directional icons and visual button ordering:
        // In RTL, "حرکت قبلی" (Previous) has right-pointing chevron and sits to the right of "حرکت بعدی" (Next, left-pointing chevron)
        composeTestRule.onNodeWithTag("pgn_prev_icon_chevron_right", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("pgn_next_icon_chevron_left", useUnmergedTree = true).assertExists()
        val prevBounds = composeTestRule.onNodeWithTag("pgn_prev_btn").fetchSemanticsNode().boundsInRoot
        val nextBounds = composeTestRule.onNodeWithTag("pgn_next_btn").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "In Persian RTL layout, 'حرکت قبلی' (${prevBounds.left}) must be placed to the right of 'حرکت بعدی' (${nextBounds.left})",
            prevBounds.left > nextBounds.left
        )

        // Helper to check enabled state via SemanticsProperties.Disabled
        fun isButtonEnabled(tag: String): Boolean {
            val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
            return !node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        }

        // 3. Initially BD-028 is at end (step 7 of 7): Previous is enabled, Next is disabled
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"
        assertEquals(7, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
        assertTrue(isButtonEnabled("pgn_prev_btn"))
        assertFalse(isButtonEnabled("pgn_next_btn"))

        // 4. Reset to step 0: Previous is disabled, Next is enabled
        composeTestRule.onNodeWithTag("pgn_reset_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
        assertFalse(isButtonEnabled("pgn_prev_btn"))
        assertTrue(isButtonEnabled("pgn_next_btn"))

        // Calling stepPgnPrev at step 0 must stay at 0 (never wrap to end)
        viewModel.stepPgnPrev(pgnRef)
        assertEquals(0, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)

        // 5. Click "حرکت بعدی" twice (step 0 -> 1 -> 2): both buttons enabled, MoveTrail active
        composeTestRule.onNodeWithTag("pgn_next_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
        assertTrue(isButtonEnabled("pgn_prev_btn"))
        assertTrue(isButtonEnabled("pgn_next_btn"))

        composeTestRule.onNodeWithTag("pgn_next_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)

        // 6. Click "حرکت قبلی" (step 2 -> 1): decrements by 1 and clears activeMoveTrail immediately
        composeTestRule.onNodeWithTag("pgn_prev_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
        assertNull("Clicking 'حرکت قبلی' must clear activeMoveTrail", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
        composeTestRule.onNodeWithTag("ghost_piece_e7", useUnmergedTree = true).assertDoesNotExist()
        saveRootViewScreenshot("15_undo_previous_move_trail_cleared.png")

        // 7. Jump to end (step 7) and verify stepPgnNext at end stays at 7 (never wraps to 0)
        composeTestRule.onNodeWithTag("pgn_end_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(7, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
        viewModel.stepPgnNext(pgnRef)
        assertEquals(7, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
    }

    @Test
    fun `v14 replay controls test 7 - verify AudioSettingsDialog MoveTrail switch and speed selector UI and independent book page navigation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.showMoveTrail = true
        prefs.playbackSpeed = com.example.data.EducationalPlaybackSpeed.NORMAL
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1) // Section 1 (مات) contains static diagrams and BD-028 PGN diagram

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify book page navigation works independently across pages in Section 1
        val startPage = viewModel.uiState.value.currentPageIndex
        composeTestRule.onNodeWithTag("reader_next_page_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(startPage + 1, viewModel.uiState.value.currentPageIndex)

        composeTestRule.onNodeWithTag("reader_prev_page_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(startPage, viewModel.uiState.value.currentPageIndex)

        // 2. Open Audio & Educational Settings Dialog and test Move Trail toggle + Playback Speed selector
        composeTestRule.onNodeWithTag("nav_audio_settings_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("نمایش مسیر حرکت مهره‌ها").assertIsDisplayed()
        composeTestRule.onNodeWithText("سرعت بازپخش آموزشی").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setting_show_move_trail_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_option_SLOW").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_option_NORMAL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_option_FAST").assertIsDisplayed()

        // Select SLOW (آرام - 600ms)
        composeTestRule.onNodeWithTag("speed_option_SLOW").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.EducationalPlaybackSpeed.SLOW, viewModel.uiState.value.playbackSpeed)

        // Toggle Move Trail switch off and back on
        composeTestRule.onNodeWithTag("setting_show_move_trail_switch").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.showMoveTrail)

        composeTestRule.onNodeWithTag("setting_show_move_trail_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.showMoveTrail)

        // Restore NORMAL speed and close dialog
        composeTestRule.onNodeWithTag("speed_option_NORMAL").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.EducationalPlaybackSpeed.NORMAL, viewModel.uiState.value.playbackSpeed)

        saveRootViewScreenshot("10_audio_settings_dialog.png")

        composeTestRule.onNodeWithTag("audio_dialog_close_btn").performClick()
        composeTestRule.waitForIdle()
    }

    // =========================================================================
    // Version 15 Tests: RTL Outer Jump Icons, Staunton Vector Pieces, Board Surface & Capture Indicator
    // =========================================================================

    @Test
    fun `v15 test 1 - verify outer jump button icons are swapped for RTL while callbacks and inner Prev-Next buttons remain intact`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val diagram = state.diagrams.first { it.diagramId == "BD-029" }
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DiagramCard(
                        diagram = diagram,
                        state = state,
                        viewModel = viewModel,
                        fontMultiplier = 1.0f
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify outer jump buttons use the swapped RTL-aligned icons:
        //    - pgn_reset_btn (rightmost in RTL) has pgn_reset_icon_fast_forward (⏭)
        //    - pgn_end_btn (leftmost in RTL) has pgn_end_icon_fast_rewind (⏮)
        composeTestRule.onNodeWithTag("pgn_reset_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pgn_end_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pgn_reset_icon_fast_forward", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("pgn_end_icon_fast_rewind", useUnmergedTree = true).assertExists()

        // 2. Verify inner Previous and Next buttons remain untouched:
        composeTestRule.onNodeWithTag("pgn_prev_icon_chevron_right", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("pgn_next_icon_chevron_left", useUnmergedTree = true).assertExists()

        // 3. Verify RTL horizontal ordering: Reset (rightmost) > Prev > Next > End (leftmost)
        val resetBounds = composeTestRule.onNodeWithTag("pgn_reset_btn").fetchSemanticsNode().boundsInRoot
        val prevBounds = composeTestRule.onNodeWithTag("pgn_prev_btn").fetchSemanticsNode().boundsInRoot
        val nextBounds = composeTestRule.onNodeWithTag("pgn_next_btn").fetchSemanticsNode().boundsInRoot
        val endBounds = composeTestRule.onNodeWithTag("pgn_end_btn").fetchSemanticsNode().boundsInRoot

        assertTrue("In RTL, pgn_reset_btn must be to the right of pgn_prev_btn", resetBounds.left > prevBounds.left)
        assertTrue("In RTL, pgn_prev_btn must be to the right of pgn_next_btn", prevBounds.left > nextBounds.left)
        assertTrue("In RTL, pgn_next_btn must be to the right of pgn_end_btn", nextBounds.left > endBounds.left)

        // 4. Verify callbacks: clicking pgn_reset_btn resets to step 0; clicking pgn_end_btn jumps to step 7
        assertEquals(7, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
        composeTestRule.onNodeWithTag("pgn_reset_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)

        composeTestRule.onNodeWithTag("pgn_end_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(7, viewModel.uiState.value.pgnStates[pgnRef]?.currentStep)
    }

    @Test
    fun `v15 test 2 - verify custom Staunton vector piece rendering for all 12 piece types and warm wood-cream board tokens`() {
        // 1. Verify V15 board & piece design tokens
        assertEquals(androidx.compose.ui.graphics.Color(0xFFECDBBA), com.example.ui.theme.ReaderDesignTokens.BoardLightSquareColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC5A076), com.example.ui.theme.ReaderDesignTokens.BoardDarkSquareColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFAF3E3), com.example.ui.theme.ReaderDesignTokens.WhitePieceFillColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF282019), com.example.ui.theme.ReaderDesignTokens.WhitePieceOutlineColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF282522), com.example.ui.theme.ReaderDesignTokens.BlackPieceFillColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF968C82), com.example.ui.theme.ReaderDesignTokens.BlackPieceInnerDetailColor)

        // 2. Render standard starting board and verify all 12 piece types render custom vector canvases
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ChessBoardView(
                        fen = ChessEngine.STANDARD_START_FEN,
                        modifier = Modifier.size(360.dp)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val expectedSquarePieceVectors = listOf(
            "a1" to 'R', "b1" to 'N', "c1" to 'B', "d1" to 'Q', "e1" to 'K', "f1" to 'B', "g1" to 'N', "h1" to 'R',
            "a2" to 'P', "e2" to 'P',
            "a7" to 'p', "e7" to 'p',
            "a8" to 'r', "b8" to 'n', "c8" to 'b', "d8" to 'q', "e8" to 'k', "f8" to 'b', "g8" to 'n', "h8" to 'r'
        )
        for ((sq, fenChar) in expectedSquarePieceVectors) {
            composeTestRule.onNodeWithTag("piece_vector_${sq}_$fenChar", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `v15 test 3 - verify Capture radial pulse on normal capture and En Passant and absence on non-capture moves`() {
        // 1. Non-capture move (1. e4: e2 -> e4) must NOT display capture_pulse
        val e4From = ChessEngine.STANDARD_START_FEN
        val e4To = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        val e4Trans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v15_e4", e4From, e4To)!!
        val e4Trail = com.example.model.BoardTransitionResolver.createMoveTrail(e4Trans)!!

        // 2. Normal capture move (Scholar's Mate Qxf7#: f3 -> f7 capturing black pawn on f7)
        val capFrom = "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 4 4"
        val capTo = "r1bqk1nr/pppp1Qpp/2n5/2b1p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
        val capTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v15_qxf7", capFrom, capTo)!!
        val capTrail = com.example.model.BoardTransitionResolver.createMoveTrail(capTrans)!!

        // 3. En Passant capture (e5 -> d6 capturing black pawn on d5)
        val epFrom = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
        val epTo = "4k3/8/3P4/8/8/8/8/4K3 b - - 0 1"
        val epTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v15_ep", epFrom, epTo)!!
        val epTrail = com.example.model.BoardTransitionResolver.createMoveTrail(epTrans)!!

        var curFen by androidx.compose.runtime.mutableStateOf(e4To)
        var curTrans by androidx.compose.runtime.mutableStateOf<com.example.model.BoardTransition?>(e4Trans)
        var curTrail by androidx.compose.runtime.mutableStateOf<com.example.model.MoveTrailState?>(e4Trail)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ChessBoardView(
                        fen = curFen,
                        transition = curTrans,
                        moveTrail = curTrail,
                        showMoveTrail = true,
                        modifier = Modifier.size(360.dp)
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(180)

        // Non-capture move has move_trail_canvas, NO square borders, and NO capture_pulse_e4
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("trail_dest_glow_e4", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("capture_pulse_e4", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("capture_indicator_e4", useUnmergedTree = true).assertDoesNotExist()

        // Switch to normal capture (Qxf7#) -> capture_pulse_f7 must exist during 380ms pulse window
        curFen = capTo
        curTrans = capTrans
        curTrail = capTrail
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(180)

        composeTestRule.onNodeWithTag("capture_pulse_f7", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("capture_indicator_f7", useUnmergedTree = true).assertDoesNotExist()

        // Switch to En Passant capture (exd6) -> capture_pulse_d5 must exist on the captured pawn square d5
        curFen = epTo
        curTrans = epTrans
        curTrail = epTrail
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(180)

        composeTestRule.onNodeWithTag("capture_pulse_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("capture_pulse_d6", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_dest_glow_d6", useUnmergedTree = true).assertDoesNotExist()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    // =========================================================================
    // Version 16 Comprehensive Tests:
    // - Suite A: Persistent Move Description Outside & Below Board
    // - Suite B: Move Trail Redesign (Teal Ghost Piece, Single Alpha, Zero Square Borders)
    // - Suite C: Red Radial Capture Pulse (4-Phase Expansion/Contraction, En Passant d5)
    // - Suite D: BD-032 Board Size Stability Across All Interactive States
    // - Suite E: Book Narration Infrastructure (Manifest Validation, Queue, Pagination Dedup,
    //            Missing Assets, Controls, Persistence, Lifecycle, ChessSoundManager Isolation)
    // =========================================================================

    @Test
    fun `v16 test A - verify Persistent Move Description appears outside and below board, persists after trail fade, updates on next move, clears on Undo-Reset-PageChange, and works with showMoveTrail false`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.showMoveTrail = true
        prefs.setBd032Solved(false)
        val viewModel = ChapterViewModel(app)
        val pgnRef = "SCHOLARS_MATE_CANDIDATE"

        // 1. Initial load: no persistent move description on any PGN diagram or BD-032
        assertNull("Initial load must not have lastMoveDescriptionFa", viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)
        assertNull("Initial BD-032 load must not have lastMoveDescriptionFa", viewModel.uiState.value.bd032State.lastMoveDescriptionFa)

        // Reset PGN to step 0
        viewModel.resetPgn(pgnRef)
        assertNull(viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            val diagram = state.diagrams.first { it.diagramId == "BD-029" }
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    DiagramCard(
                        diagram = diagram,
                        state = state,
                        viewModel = viewModel,
                        fontMultiplier = 1.0f
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        // Measure board and persistent bar bounds before any move
        val boardBoundsInitial = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        val descBarBoundsInitial = composeTestRule.onNodeWithTag("persistent_move_description").fetchSemanticsNode().boundsInRoot
        composeTestRule.onNodeWithTag("persistent_move_description_text", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(
            "persistent_move_description (${descBarBoundsInitial.top}) must be strictly outside and below chessboard_container (${boardBoundsInitial.bottom})",
            descBarBoundsInitial.top >= boardBoundsInitial.bottom - 1.0f
        )

        // 2. Step 1 (1. e4: e2 -> e4) -> persistent move description appears immediately
        viewModel.stepPgnNext(pgnRef)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(250)

        assertEquals("پیاده سفید: از خانه e2 به خانه e4", viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)
        composeTestRule.onNodeWithText("پیاده سفید: از خانه e2 به خانه e4").assertIsDisplayed()

        // Verify zero layout shift when text appears
        val boardBoundsDuringMove = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        val descBarBoundsDuringMove = composeTestRule.onNodeWithTag("persistent_move_description").fetchSemanticsNode().boundsInRoot
        assertEquals(boardBoundsInitial.width, boardBoundsDuringMove.width, 0.5f)
        assertEquals(boardBoundsInitial.height, boardBoundsDuringMove.height, 0.5f)
        assertEquals(descBarBoundsInitial.height, descBarBoundsDuringMove.height, 0.5f)

        // 3. Advance past Move Trail hold (800ms) + fade (320ms) -> trail is gone, but Persistent Move Description REMAINS visible!
        composeTestRule.mainClock.advanceTimeBy(1500)
        composeTestRule.mainClock.advanceTimeByFrame()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()

        assertNull("MoveTrail must be cleared after fade-out", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
        composeTestRule.onNodeWithTag("ghost_piece_e2", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(
            "lastMoveDescriptionFa must remain visible after MoveTrail fades out",
            "پیاده سفید: از خانه e2 به خانه e4",
            viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa
        )
        composeTestRule.onNodeWithText("پیاده سفید: از خانه e2 به خانه e4").assertIsDisplayed()

        saveMidAnimationScreenshot("16_persistent_move_description_after_trail_fade.png")

        // 4. Next forward move (1... e5: e7 -> e5) replaces previous text with new move description
        viewModel.stepPgnNext(pgnRef)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(300)

        assertEquals("پیاده سیاه: از خانه e7 به خانه e5", viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)
        composeTestRule.onNodeWithText("پیاده سیاه: از خانه e7 به خانه e5").assertIsDisplayed()

        // 5. Undo (stepPgnPrev) clears persistent move description immediately with 0px layout shift
        viewModel.stepPgnPrev(pgnRef)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(250)

        assertNull("Undo (stepPgnPrev) must clear lastMoveDescriptionFa", viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)
        composeTestRule.onNodeWithTag("persistent_move_description_text", useUnmergedTree = true).assertDoesNotExist()
        val boardBoundsAfterUndo = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        assertEquals(boardBoundsInitial.height, boardBoundsAfterUndo.height, 0.5f)

        // 6. When showMoveTrail == false, forward moves STILL populate and display Persistent Move Description!
        viewModel.setShowMoveTrail(false)
        viewModel.stepPgnNext(pgnRef)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(250)

        assertNull("When showMoveTrail is false, activeMoveTrail must be null", viewModel.uiState.value.pgnStates[pgnRef]?.activeMoveTrail)
        assertEquals(
            "When showMoveTrail is false, lastMoveDescriptionFa must STILL be populated",
            "پیاده سیاه: از خانه e7 به خانه e5",
            viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa
        )
        composeTestRule.onNodeWithText("پیاده سیاه: از خانه e7 به خانه e5").assertIsDisplayed()

        // 7. Reset, Page Change, and Section Change clear lastMoveDescriptionFa; BD-032 piece selection & illegal move do NOT set it
        viewModel.setShowMoveTrail(true)
        viewModel.resetPgn(pgnRef)
        assertNull("resetPgn must clear lastMoveDescriptionFa", viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)

        viewModel.selectSection(1)
        viewModel.stepPgnNext(pgnRef)
        assertNotNull(viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)
        viewModel.nextPage()
        assertNull("Page change must clear lastMoveDescriptionFa", viewModel.uiState.value.pgnStates[pgnRef]?.lastMoveDescriptionFa)

        // BD-032 piece selection & illegal move must not set lastMoveDescriptionFa
        viewModel.onBd032SquareClicked(4, 4) // Select Queen at e5
        assertNull("Selecting a piece must not set lastMoveDescriptionFa", viewModel.uiState.value.bd032State.lastMoveDescriptionFa)
        viewModel.onBd032SquareClicked(2, 3) // Illegal move e5 -> c4
        assertNull("Illegal move must not set lastMoveDescriptionFa", viewModel.uiState.value.bd032State.lastMoveDescriptionFa)
        viewModel.onBd032SquareClicked(7, 7) // Legal winning move Qh8#
        assertEquals("وزیر سیاه: از خانه e5 به خانه h8 (مات)", viewModel.uiState.value.bd032State.lastMoveDescriptionFa)
        viewModel.resetBd032()
        assertNull("resetBd032 must clear lastMoveDescriptionFa", viewModel.uiState.value.bd032State.lastMoveDescriptionFa)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun `v16 test B - verify Move Trail redesign with teal ghost piece (#438F8A), single alpha application, simultaneous fade, and zero square markers`() {
        // 1. Verify design tokens
        assertEquals(androidx.compose.ui.graphics.Color(0xFF438F8A), com.example.ui.theme.ReaderDesignTokens.MoveTrailPrimaryColor)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF438F8A), com.example.ui.components.GhostTintFillTeal)

        val fromFen = "4k3/8/8/8/8/8/8/R3K3 w Q - 0 1"
        val toFen = "4k3/8/8/R7/8/8/8/4K3 b - - 1 1"
        val transition = com.example.model.BoardTransitionResolver.resolveForwardTransition("v16_trail_b", fromFen, toFen)!!
        val moveTrail = com.example.model.BoardTransitionResolver.createMoveTrail(transition)!!

        val finishedEvents = mutableListOf<String>()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = toFen,
                    transition = transition,
                    moveTrail = moveTrail,
                    showMoveTrail = true,
                    onMoveTrailFinished = { finishedEvents.add(it) },
                    modifier = Modifier.size(360.dp)
                )
            }
        }

        // At 500ms (hold phase):
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(500)

        // Ghost piece has #438F8A teal tint and arrow canvas exists; all square borders are absent
        composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("piece_tint_ghost_a1_teal", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("trail_source_marker_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("trail_dest_glow_a5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("move_trail_caption", useUnmergedTree = true).assertDoesNotExist()

        // At 1050ms (mid-fade of 320ms fade window after 800ms hold): both ghost and arrow are still fading together
        composeTestRule.mainClock.advanceTimeBy(550)
        composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertExists()
        assertTrue(finishedEvents.isEmpty())

        // After fade completes: both ghost piece and arrow disappear simultaneously
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(listOf("v16_trail_b"), finishedEvents)
        composeTestRule.onNodeWithTag("ghost_piece_a1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("move_trail_canvas", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `v16 test C - verify 4-phase Red Capture Pulse radial expansion, contraction, fade-out, and En Passant d5 square accuracy`() {
        // 1. Verify 4-phase mathematical curve of computeCapturePulseMetrics (returns radiusFraction to centerAlpha):
        //    Phase 1 (p=0.0): starts near center (radiusFraction = 0.06f)
        val (rStart, aStart) = com.example.ui.components.computeCapturePulseMetrics(0.0f)
        assertEquals(0.06f, rStart, 0.01f)
        assertTrue(aStart > 0f)

        //    Phase 2 (p=0.35): expanding toward peak
        val (rMidExpand, _) = com.example.ui.components.computeCapturePulseMetrics(0.35f)
        assertTrue(rMidExpand > rStart)

        //    Phase 3 (p=0.55): peak expansion at ~88% of square width (radiusFraction = 0.44f < 0.50f so it never overflows the square!)
        val (rPeak, aPeak) = com.example.ui.components.computeCapturePulseMetrics(0.55f)
        assertEquals(0.44f, rPeak, 0.015f)
        assertTrue("Max diameter (2 * $rPeak) must not exceed square width (1.0)", rPeak * 2f <= 0.90f)
        assertEquals(0.84f, aPeak, 0.02f)

        //    Phase 4 (p=0.80 -> p=1.0): contracts slightly and fades out to 0
        val (rContract, aContract) = com.example.ui.components.computeCapturePulseMetrics(0.80f)
        assertTrue("Radius must contract during phase 3/4", rContract < rPeak)
        assertTrue("Alpha must decay during phase 3/4", aContract < aPeak)

        val (_, aEnd) = com.example.ui.components.computeCapturePulseMetrics(1.0f)
        assertEquals(0.0f, aEnd, 0.001f)

        // 2. Verify Compose rendering during capture and complete removal after 380ms
        val capFrom = "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"
        val capTo = "4k3/8/8/3P4/8/8/8/4K3 b - - 0 1"
        val capTrans = com.example.model.BoardTransitionResolver.resolveForwardTransition("v16_pulse_d5", capFrom, capTo)!!
        val capTrail = com.example.model.BoardTransitionResolver.createMoveTrail(capTrans)!!

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessBoardView(
                    fen = capTo,
                    transition = capTrans,
                    moveTrail = capTrail,
                    showMoveTrail = true,
                    modifier = Modifier.size(360.dp)
                )
            }
        }

        // At 200ms (near peak of 380ms capture pulse):
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.onNodeWithTag("capture_pulse_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("captured_piece_overlay_d5", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("capture_indicator_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("capture_impact_highlight_d5", useUnmergedTree = true).assertDoesNotExist()

        saveMidAnimationScreenshot("17_capture_pulse_radial_mid_animation.png")

        // Advance past 380ms -> capture_pulse_d5 is completely removed while MoveTrail is still in its 800ms hold phase!
        composeTestRule.mainClock.advanceTimeBy(250)
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("capture_pulse_d5", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("ghost_piece_e4", useUnmergedTree = true).assertExists()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    @Test
    fun `v16 test D - verify BD-032 chessboard_container size remains 100 percent constant across all interactive states and screen widths`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setBd032Solved(false)
        val viewModel = ChapterViewModel(app)

        // Open Section 3 (index 3: اتمام زمان / BD-032 interactive exercise) and navigate to BD-032 page
        viewModel.selectSection(3)
        val bd032PageIdx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-032" }.coerceAtLeast(0)
        viewModel.goToPage(bd032PageIdx)

        var simulatedScreenWidthDp by androidx.compose.runtime.mutableStateOf(370)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(width = simulatedScreenWidthDp.dp, height = 840.dp)
                    ) {
                        ReaderScreen(viewModel = viewModel, state = state)
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        for (widthDp in listOf(310, 370, 411)) {
            simulatedScreenWidthDp = widthDp
            viewModel.resetBd032()
            // Clear initial reset message via retryBd032 so we start with feedbackMessage == null
            viewModel.retryBd032()
            composeTestRule.waitForIdle()

            // Ensure we are on BD-032 page after any viewport pagination update
            val pageIdx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-032" }.coerceAtLeast(0)
            if (viewModel.uiState.value.currentPageIndex != pageIdx) {
                viewModel.goToPage(pageIdx)
                composeTestRule.waitForIdle()
            }

            // 1. Initial state (feedbackMessage == null, canRetry == false, lastMoveDescriptionFa == null)
            assertNull(viewModel.uiState.value.bd032State.feedbackMessage)
            val initialBounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot

            fun assertBoardSizeUnchanged(stageName: String) {
                val curBounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
                assertEquals(
                    "BD-032 board width must not change at stage '$stageName' (width=${widthDp}dp)",
                    initialBounds.width,
                    curBounds.width,
                    1.0f
                )
                assertEquals(
                    "BD-032 board height must not change at stage '$stageName' (width=${widthDp}dp)",
                    initialBounds.height,
                    curBounds.height,
                    1.0f
                )
            }

            // 2. Select Black Queen at e5 (feedbackMessage becomes non-null!)
            composeTestRule.onNodeWithTag("square_e5").performClick()
            composeTestRule.waitForIdle()
            assertNotNull(viewModel.uiState.value.bd032State.feedbackMessage)
            assertBoardSizeUnchanged("After selecting piece e5")

            // 3. Toggle "نمایش حرکت‌های قانونی" (exercise_legal_hint_btn_EX_CH01_BD032)
            composeTestRule.onNodeWithTag("exercise_legal_hint_btn_EX_CH01_BD032").performClick()
            composeTestRule.waitForIdle()
            assertTrue(viewModel.uiState.value.bd032State.showLegalMovesHint)
            assertBoardSizeUnchanged("After toggling legal moves hint")

            // 4. Attempt illegal move e5 -> c4 (illegal feedback + red flash)
            composeTestRule.onNodeWithTag("square_c4").performClick()
            composeTestRule.waitForIdle()
            assertBoardSizeUnchanged("After illegal move e5 -> c4")

            // 5. Play wrong legal move e5 -> e8 (animates, shows exercise_feedback + exercise_retry_btn_EX_CH01_BD032 + persistent_move_description)
            composeTestRule.onNodeWithTag("square_e8").performClick()
            composeTestRule.waitForIdle()
            assertEquals(com.example.ui.Bd032ExercisePhase.WRONG_LEGAL_MOVE, viewModel.uiState.value.bd032State.phase)
            composeTestRule.onNodeWithTag("exercise_feedback").assertIsDisplayed()
            composeTestRule.onNodeWithTag("exercise_retry_btn_EX_CH01_BD032").assertIsDisplayed()
            assertBoardSizeUnchanged("After wrong legal move e5 -> e8 with feedback and Retry button visible")

            if (widthDp == 370) {
                saveRootViewScreenshot("18_bd032_board_stability_wrong_move.png")
            }

            // 6. Click Retry ("تلاش دوباره") -> clears feedback, Retry button, and move description
            composeTestRule.onNodeWithTag("exercise_retry_btn_EX_CH01_BD032").performClick()
            composeTestRule.waitForIdle()
            assertNull(viewModel.uiState.value.bd032State.feedbackMessage)
            assertBoardSizeUnchanged("After clicking Retry")

            // 7. Solve puzzle with Qh8# (e5 -> h8)
            composeTestRule.onNodeWithTag("square_e5").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("square_h8").performClick()
            composeTestRule.waitForIdle()
            assertTrue(viewModel.uiState.value.bd032State.isSolved)
            composeTestRule.onNodeWithTag("exercise_feedback").assertIsDisplayed()
            assertBoardSizeUnchanged("After solving with Qh8#")

            // 8. Click Reset ("شروع مجدد وضعیت")
            composeTestRule.onNodeWithTag("exercise_reset_btn").performClick()
            composeTestRule.waitForIdle()
            assertBoardSizeUnchanged("After clicking Reset")
        }
    }

    @Test
    fun `v16 test E1 - verify BookNarrationManager manifest validation, path security, missing asset handling, and independence from ChessSoundManager`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.narrationEnabled = true
        prefs.narrationSpeed = com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X
        val viewModel = ChapterViewModel(app)
        val nm = viewModel.narrationManager

        // 1. Default assets/narration/manifest.json must be valid, schemaVersion=1, chapterId="CH01", with all 6 sections declared and 0 entries
        assertTrue("Default manifest.json must be valid", nm.manifest.isValid)
        assertEquals(1, nm.manifest.schemaVersion)
        assertEquals("CH01", nm.manifest.chapterId)
        assertEquals(6, nm.manifest.declaredSectionIds.size)
        assertTrue(nm.manifest.entriesByCompositeKey.isEmpty())

        // 2. Verify NarrationManifestValidator rejects invalid block IDs and unsafe paths
        assertFalse(com.example.audio.NarrationManifestValidator.isValidBlockOrSectionId(""))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidBlockOrSectionId("SEC_CH01_01_OPENING_P001__part_2"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidBlockOrSectionId("bad/id"))
        assertTrue(com.example.audio.NarrationManifestValidator.isValidBlockOrSectionId("SEC_CH01_01_OPENING_P001"))

        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("../secret.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("ch01/../../secret.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("/sdcard/audio.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("\\windows\\audio.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("C:\\audio.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("https://example.com/audio.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("http://example.com/audio.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("file:///android_asset/audio.mp3"))
        assertFalse(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("ch01/not_audio.txt"))
        assertTrue(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("ch01/sec01/p001.mp3"))
        assertTrue(com.example.audio.NarrationManifestValidator.isValidRelativeAssetPath("ch01/sec02/d026.ogg"))

        // Malformed / tampered manifest entries are rejected without crashing
        val mixedManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    {
                      "sectionId": "SEC_CH01_01_OPENING",
                      "blocks": [
                        { "blockId": "SEC_CH01_01_OPENING_P001", "audioAssetPath": "ch01/sec01_p001.mp3", "status": "AVAILABLE", "durationMs": 12000 },
                        { "blockId": "SEC_CH01_01_OPENING_P001__part_2", "audioAssetPath": "ch01/dup.mp3", "status": "AVAILABLE" },
                        { "blockId": "SEC_CH01_01_OPENING_D025", "audioAssetPath": "../traversal.mp3", "status": "AVAILABLE" },
                        { "blockId": "SEC_CH01_01_OPENING_D026", "audioAssetPath": "https://evil.com/a.mp3", "status": "AVAILABLE" }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val parsedManifest = com.example.audio.NarrationManifestValidator.parseManifestJson(mixedManifestJson)
        assertTrue(parsedManifest.isValid)
        assertEquals("Only the 1 safe entry must be accepted", 1, parsedManifest.entriesByCompositeKey.size)
        assertEquals("3 unsafe/invalid entries must be rejected", 3, parsedManifest.rejectedEntryCount)

        // 3. When audio files are not yet present in assets, clicking "پخش صفحه" in ReaderScreen does NOT crash and displays exact Persian notice
        viewModel.selectSection(0)
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reader_narration_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("narration_play_page_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals(com.example.audio.NarrationStatus.UNAVAILABLE, viewModel.uiState.value.narrationState.status)
        assertEquals(
            "فایل صوتی این صفحه هنوز آماده نشده است",
            viewModel.uiState.value.narrationState.statusMessageFa
        )
        composeTestRule.onNodeWithText("فایل صوتی این صفحه هنوز آماده نشده است").assertIsDisplayed()

        saveRootViewScreenshot("19_reader_narration_bar_audio_not_ready.png")
    }

    @Test
    fun `v16 test E2 - verify BookNarrationManager queue ordering, pagination __part_2 deduplication, partial missing skip, controls, persistence, lifecycle release, and active block highlight`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.narrationEnabled = true
        prefs.narrationSpeed = com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X
        prefs.clearNarrationResumeState()

        val viewModel = ChapterViewModel(app)
        val nm = viewModel.narrationManager
        val fakeEngine = com.example.audio.FakeNarrationPlaybackEngine()

        // Inject translated content into Section 0 with a long paragraph that splits into __part_2 across pages, plus a diagram and second paragraph
        val longText = (1..28).joinToString(" ") { "این یک جمله آموزشی طولانی برای بررسی عدم تکرار خوانش صوتی پاراگراف‌های شکسته شده در صفحه‌بندی کتاب است." }
        val sec0Blocks = listOf(
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_01_OPENING_P001",
                textFa = longText
            ),
            SectionContentBlock.DiagramBlock(
                blockId = "SEC_CH01_01_OPENING_D025",
                diagramId = "BD-025"
            ),
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_01_OPENING_P002",
                textFa = "پاراگراف دوم پس از نمودار آغازین فصل اول."
            )
        )
        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_01_OPENING",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_01_OPENING",
                orderedContentBlocks = sec0Blocks,
                translationStatus = TranslationStatus.COMPLETE
            )
        )
        viewModel.selectSection(0)

        // Verify pagination actually split SEC_CH01_01_OPENING_P001 into __part_2
        val pagesSec0 = viewModel.uiState.value.currentPages
        assertTrue(
            "Long paragraph must be split across multiple pages with __part_2",
            pagesSec0.flatMap { it.orderedBlocks }.any { it.blockId.contains("__part_2") }
        )

        // Create test manifest mapping blocks across sections
        val testManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    {
                      "sectionId": "SEC_CH01_01_OPENING",
                      "blocks": [
                        { "blockId": "SEC_CH01_01_OPENING_P001", "audioAssetPath": "ch01/s1_p001.mp3", "status": "AVAILABLE", "durationMs": 15000 },
                        { "blockId": "SEC_CH01_01_OPENING_D025", "audioAssetPath": "ch01/s1_d025.mp3", "status": "AVAILABLE", "durationMs": 8000 },
                        { "blockId": "SEC_CH01_01_OPENING_P002", "audioAssetPath": "ch01/s1_p002.mp3", "status": "AVAILABLE", "durationMs": 10000 }
                      ]
                    },
                    {
                      "sectionId": "SEC_CH01_02_CHECKMATE",
                      "blocks": [
                        { "blockId": "SEC_CH01_02_CHECKMATE_P001", "audioAssetPath": "ch01/s2_p001.mp3", "status": "AVAILABLE", "durationMs": 12000 },
                        { "blockId": "SEC_CH01_02_CHECKMATE_D026", "audioAssetPath": "ch01/s2_d026.mp3", "status": "AVAILABLE", "durationMs": 9000 }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val customManifest = com.example.audio.NarrationManifestValidator.parseManifestJson(testManifestJson)

        // Register fake audio assets for P001, P002, and Section 2 P001 (intentionally leave D025 missing to test partial-missing skip!)
        fakeEngine.registerFakeAsset("ch01/s1_p001.mp3", durationMs = 15_000)
        fakeEngine.registerFakeAsset("ch01/s1_p002.mp3", durationMs = 10_000)
        fakeEngine.registerFakeAsset("ch01/s2_p001.mp3", durationMs = 12_000)

        nm.overrideForTesting(
            customManifest = customManifest,
            customEngine = fakeEngine,
            sections = viewModel.uiState.value.sections,
            currentPage = viewModel.uiState.value.currentPage
        )

        // 1. Verify pagination deduplication: even though P001 is split into P001 and P001__part_2,
        //    buildContinuousChapterQueue queues SEC_CH01_01_OPENING_P001 ONLY ONCE!
        val chapterQueue = nm.buildContinuousChapterQueue(viewModel.uiState.value.sections, startSectionIndex = 0)
        assertEquals(
            "Split paragraph P001 must be queued only once, missing D025 skipped, and P002 + S2_P001 queued in order",
            listOf("SEC_CH01_01_OPENING_P001", "SEC_CH01_01_OPENING_P002", "SEC_CH01_02_CHECKMATE_P001"),
            chapterQueue.playableItems.map { it.blockId }
        )
        assertTrue("Partial missing flag must be true when some blocks lack audio", chapterQueue.hasPartialMissing)

        // 2. Render ReaderScreen and test active block highlight + Play / Pause / Resume / Seek / Speed / Stop
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        // Start Continuous Chapter playback (Mode B)
        assertTrue(viewModel.playChapterNarration(fromCurrentSection = true))
        composeTestRule.waitForIdle()

        assertEquals(com.example.audio.NarrationStatus.PLAYING, viewModel.uiState.value.narrationState.status)
        assertEquals("SEC_CH01_01_OPENING_P001", viewModel.uiState.value.narrationState.activeBlockId)
        assertEquals(
            "برخی بلوک‌های فاقد فایل صوتی رد شدند",
            viewModel.uiState.value.narrationState.statusMessageFa
        )

        // Active block visual highlight must exist in ReaderScreen for SEC_CH01_01_OPENING_P001
        composeTestRule.onNodeWithTag("narrated_active_block_SEC_CH01_01_OPENING_P001").assertExists()

        // Test Seek (seekNarrationTo 6000ms)
        viewModel.seekNarrationTo(6000)
        assertEquals(6000, viewModel.uiState.value.narrationState.currentPositionMs)
        assertEquals(6000, prefs.narrationLastPositionMs)

        // Test Speed cycling (1.0x -> 1.2x -> 0.8x -> 1.0x)
        assertEquals(com.example.audio.NarrationPlaybackSpeed.SPEED_1_2X, viewModel.cycleNarrationSpeed())
        assertEquals(1.2f, fakeEngine.currentSpeed, 0.001f)
        assertEquals(com.example.audio.NarrationPlaybackSpeed.SPEED_1_2X, prefs.narrationSpeed)
        assertEquals(com.example.audio.NarrationPlaybackSpeed.SPEED_0_8X, viewModel.cycleNarrationSpeed())
        assertEquals(0.8f, fakeEngine.currentSpeed, 0.001f)
        assertEquals(com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X, viewModel.cycleNarrationSpeed())

        // Test Pause and Resume
        viewModel.pauseNarration()
        assertEquals(com.example.audio.NarrationStatus.PAUSED, viewModel.uiState.value.narrationState.status)
        assertEquals(1, fakeEngine.pauseCount)

        assertTrue(viewModel.resumeNarration())
        assertEquals(com.example.audio.NarrationStatus.PLAYING, viewModel.uiState.value.narrationState.status)
        assertEquals(1, fakeEngine.resumeCount)

        // Simulate completion of P001 -> automatically skips missing D025 and advances to P002 (and auto-navigates to P002's page!)
        fakeEngine.simulateCompletion()
        composeTestRule.waitForIdle()
        assertEquals("SEC_CH01_01_OPENING_P002", viewModel.uiState.value.narrationState.activeBlockId)
        composeTestRule.onNodeWithTag("narrated_active_block_SEC_CH01_01_OPENING_P002").assertExists()

        // Simulate completion of P002 -> automatically transitions to Section 1 (SEC_CH01_02_CHECKMATE_P001)!
        fakeEngine.simulateCompletion()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.currentSectionIndex)
        assertEquals("SEC_CH01_02_CHECKMATE_P001", viewModel.uiState.value.narrationState.activeBlockId)

        // Verify complete independence from ChessSoundManager:
        // Muting ChessSoundManager does NOT stop BookNarrationManager, and stepping PGN while narration plays still emits chess sound!
        viewModel.setMasterSoundEnabled(false)
        assertEquals(com.example.audio.NarrationStatus.PLAYING, viewModel.uiState.value.narrationState.status)
        viewModel.setMasterSoundEnabled(true)
        viewModel.soundManager.clearHistoryForTesting()
        viewModel.resetPgn("SCHOLARS_MATE_CANDIDATE")
        viewModel.stepPgnNext("SCHOLARS_MATE_CANDIDATE")
        assertEquals("Chess sound must play normally while narration is active", 1, viewModel.soundManager.playedEvents.size)
        assertEquals("Narration must remain playing when chess sound fires", com.example.audio.NarrationStatus.PLAYING, viewModel.uiState.value.narrationState.status)

        // Test Stop and persisted resume state in UserPreferencesManager
        viewModel.seekNarrationTo(4500)
        viewModel.stopNarration()
        assertEquals(com.example.audio.NarrationStatus.STOPPED, viewModel.uiState.value.narrationState.status)
        assertEquals("SEC_CH01_02_CHECKMATE", prefs.narrationLastSectionId)
        assertEquals("SEC_CH01_02_CHECKMATE_P001", prefs.narrationLastBlockId)
        assertEquals(4500, prefs.narrationLastPositionMs)

        // Test Lifecycle cleanup: onCleared() releases narration engine
        val releaseBefore = fakeEngine.releaseCount
        viewModel.onCleared()
        assertEquals(releaseBefore + 1, fakeEngine.releaseCount)
    }
}



