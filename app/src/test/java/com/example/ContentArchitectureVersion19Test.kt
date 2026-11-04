package com.example

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audio.BookNarrationManager
import com.example.audio.FakeNarrationPlaybackEngine
import com.example.audio.NarrationManifestValidator
import com.example.audio.NarrationStatus
import com.example.data.ChapterRepository
import com.example.data.UserPreferencesManager
import com.example.model.AuditStatus
import com.example.model.ContentBlockValidator
import com.example.model.DiagramItem
import com.example.model.ExerciseDefinition
import com.example.model.ExercisePhase
import com.example.model.ExerciseUiState
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import com.example.model.TranslationSlot
import com.example.ui.ChapterViewModel
import com.example.ui.components.InteractiveExerciseView
import com.example.ui.theme.MyApplicationTheme
import java.io.File
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
class ContentArchitectureVersion19Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun resolveProjectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "app/src/main").exists()) cwd else cwd.parentFile ?: cwd
    }

    // =========================================================================
    // Section 2 & 6 — Complete Exercise System Generalization & Multi-Exercise Independence
    // =========================================================================

    @Test
    fun `v19 exercise 1 - ExerciseUiState and ExercisePhase are real model classes and ExerciseEngine has zero UI or BD-032 dependencies`() {
        val state = ExerciseUiState()
        assertEquals("com.example.model.ExerciseUiState", ExerciseUiState::class.qualifiedName)
        assertEquals("com.example.model.ExercisePhase", ExercisePhase::class.qualifiedName)
        assertEquals(ExercisePhase.IDLE, state.phase)

        // Default exercise ID generator must not contain CH01
        val generatedId = SectionContentBlock.defaultExerciseIdForDiagram("BD-205")
        assertEquals("EX_BD_205", generatedId)
        assertFalse(generatedId.contains("CH01"))

        val root = resolveProjectRoot()
        val engineSource = File(root, "app/src/main/java/com/example/chess/ExerciseEngine.kt").readText()
        assertFalse(
            "ExerciseEngine.kt must not import com.example.ui",
            engineSource.contains("import com.example.ui")
        )
        assertFalse(
            "ExerciseEngine.kt must not reference BD-032 or bd032",
            engineSource.contains("BD-032") || engineSource.contains("bd032", ignoreCase = true)
        )

        val readerSource = File(root, "app/src/main/java/com/example/ui/screens/ReaderScreen.kt").readText()
        assertFalse(
            "ReaderScreen.kt must not reference BD-032 or bd032State",
            readerSource.contains("BD-032") || readerSource.contains("bd032State")
        )
        assertFalse(
            "ReaderScreen.kt must not hardcode activeChapterId == CH01",
            readerSource.contains("activeChapterId == \"CH01\"")
        )

        val sectionsListSource = File(root, "app/src/main/java/com/example/ui/screens/SectionsListScreen.kt").readText()
        assertFalse(
            "SectionsListScreen.kt must not hardcode activeChapterId == CH01",
            sectionsListSource.contains("activeChapterId == \"CH01\"")
        )
    }

    private fun createTwoExerciseChapterRepository(context: Context): ChapterRepository {
        val syntheticAssets = mapOf(
            "books/test_library_v19_exercises.json" to """
                {
                  "schemaVersion": 1,
                  "defaultBookId": "tactical_test_book",
                  "books": [
                    {
                      "bookId": "tactical_test_book",
                      "titleEn": "Tactical Test Book",
                      "titleFa": "کتاب آزمایشی تاکتیک",
                      "authorEn": "Test Author",
                      "authorFa": "نویسنده آزمایشی",
                      "chapters": [
                        {
                          "chapterId": "CH_EX_DUAL",
                          "chapterNumber": 3,
                          "titleEn": "Dual Exercise Chapter",
                          "titleFa": "فصل سوم: دو تمرین مستقل",
                          "manifestPath": "books/tactical_test_book/CH_EX_DUAL/chapter.json",
                          "enabled": true
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            "books/tactical_test_book/CH_EX_DUAL/chapter.json" to """
                {
                  "schemaVersion": 1,
                  "bookId": "tactical_test_book",
                  "chapterId": "CH_EX_DUAL",
                  "chapterNumber": 3,
                  "titleEn": "Dual Exercise Chapter",
                  "titleFa": "فصل سوم: دو تمرین مستقل",
                  "diagramsFile": "diagrams.json",
                  "pgnSequencesFile": "pgn_sequences.json",
                  "exercisesFile": "exercises.json",
                  "narrationManifestFile": "narration/manifest.json",
                  "sections": [
                    {
                      "sectionId": "SEC_DUAL_01",
                      "order": 1,
                      "sectionKind": "OPENING",
                      "titleEn": "Two Puzzles",
                      "titleFa": "دو معمای مات در یک حرکت",
                      "pdfPages": [10, 11],
                      "file": "sections/01_puzzles.json"
                    }
                  ]
                }
            """.trimIndent(),
            "books/tactical_test_book/CH_EX_DUAL/diagrams.json" to """
                {
                  "schema_version": "1.0",
                  "chapter_id": "CH_EX_DUAL",
                  "diagrams": [
                    {
                      "diagram_id": "BD-301",
                      "pdf_page": 10,
                      "section": "Two Puzzles",
                      "book_context": "White to move and mate in one with Rook",
                      "diagram_kind": "exercise_position",
                      "recommended_interaction": "interactive_exercise",
                      "fen": "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
                      "side_to_move": "white",
                      "move_history": "",
                      "pgn_ref": null,
                      "solution_pgn_refs": ["SOL_301_RD8"],
                      "highlight_squares": [],
                      "related_diagram_ids": [],
                      "visual_confidence": "high",
                      "audit_status": "VALID",
                      "audit_warning_fa": null,
                      "implementation_notes": "Exercise 1 diagram"
                    },
                    {
                      "diagram_id": "BD-302",
                      "pdf_page": 11,
                      "section": "Two Puzzles",
                      "book_context": "Black to move and mate in one with Rook",
                      "diagram_kind": "exercise_position",
                      "recommended_interaction": "interactive_exercise",
                      "fen": "3r2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1",
                      "side_to_move": "black",
                      "move_history": "",
                      "pgn_ref": null,
                      "solution_pgn_refs": ["SOL_302_RC1"],
                      "highlight_squares": [],
                      "related_diagram_ids": [],
                      "visual_confidence": "high",
                      "audit_status": "VALID",
                      "audit_warning_fa": null,
                      "implementation_notes": "Exercise 2 diagram"
                    }
                  ]
                }
            """.trimIndent(),
            "books/tactical_test_book/CH_EX_DUAL/pgn_sequences.json" to """
                {
                  "SOL_301_RD8": {
                    "id": "SOL_301_RD8",
                    "description": "White Rook mates on d8",
                    "moves": ["Rd8#"],
                    "fens": [
                      "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
                      "3R2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 1 1"
                    ]
                  },
                  "SOL_302_RC1": {
                    "id": "SOL_302_RC1",
                    "description": "Black Rook mates on d1",
                    "moves": ["Rd1#"],
                    "fens": [
                      "3r2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1",
                      "6k1/5ppp/8/8/8/8/5PPP/3r2K1 w - - 1 2"
                    ]
                  }
                }
            """.trimIndent(),
            "books/tactical_test_book/CH_EX_DUAL/exercises.json" to """
                {
                  "schemaVersion": 1,
                  "chapterId": "CH_EX_DUAL",
                  "exercises": [
                    {
                      "exerciseId": "EX_DUAL_301",
                      "diagramId": "BD-301",
                      "exerciseType": "MATE_IN_ONE",
                      "initialFen": "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
                      "sideToMove": "white",
                      "titleFa": "تمرین اول: مات در عرض آخر برای سفید",
                      "promptFa": "نوبت سفید است. رخ سفید را به خانه‌ای ببرید که شاه سیاه مات شود:",
                      "acceptedSolutionIds": ["SOL_301_RD8"],
                      "successFeedbackFa": "آفرین! تمرین اول با حرکت {move} حل شد.",
                      "wrongMoveFeedbackFa": "حرکت تمرین اول اشتباه است. دوباره تلاش کنید.",
                      "illegalMoveFeedbackFa": "حرکت غیرمجاز در تمرین اول.",
                      "hintFa": "رخ سفید را به ستون d عرض هشتم ببرید.",
                      "allowLegalMoveHints": true
                    },
                    {
                      "exerciseId": "EX_DUAL_302",
                      "diagramId": "BD-302",
                      "exerciseType": "MATE_IN_ONE",
                      "initialFen": "3r2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1",
                      "sideToMove": "black",
                      "titleFa": "تمرین دوم: مات در عرض اول برای سیاه",
                      "promptFa": "نوبت سیاه است. با حرکت رخ سیاه، شاه سفید را مات کنید:",
                      "acceptedSolutionIds": ["SOL_302_RC1"],
                      "successFeedbackFa": "عالی! تمرین دوم با حرکت {move} به درستی حل شد.",
                      "wrongMoveFeedbackFa": "حرکت تمرین دوم اشتباه است. دوباره تلاش کنید.",
                      "illegalMoveFeedbackFa": "حرکت غیرمجاز در تمرین دوم.",
                      "hintFa": "رخ سیاه را در عرض اول حرکت دهید.",
                      "allowLegalMoveHints": true
                    }
                  ]
                }
            """.trimIndent(),
            "books/tactical_test_book/CH_EX_DUAL/sections/01_puzzles.json" to """
                {
                  "schemaVersion": 1,
                  "sectionId": "SEC_DUAL_01",
                  "placeholderNoticeFa": "بخش دو تمرین",
                  "translationStatus": "TRANSLATED",
                  "orderedContentBlocks": [
                    {
                      "type": "exercise",
                      "blockId": "BLK_EX_301",
                      "diagramId": "BD-301",
                      "exerciseId": "EX_DUAL_301"
                    },
                    {
                      "type": "exercise",
                      "blockId": "BLK_EX_302",
                      "diagramId": "BD-302",
                      "exerciseId": "EX_DUAL_302"
                    }
                  ]
                }
            """.trimIndent(),
            "books/tactical_test_book/CH_EX_DUAL/narration/manifest.json" to """
                {
                  "schemaVersion": 1,
                  "chapters": [
                    {
                      "chapterId": "CH_EX_DUAL",
                      "sections": [
                        {
                          "sectionId": "SEC_DUAL_01",
                          "blocks": []
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
        )

        return ChapterRepository(
            context = context,
            libraryAssetPath = "books/test_library_v19_exercises.json",
            assetReaderOverride = { path ->
                syntheticAssets[path] ?: throw IllegalArgumentException("Missing synthetic asset: $path")
            }
        )
    }

    @Test
    fun `v19 exercise 2 - two simultaneous MATE_IN_ONE exercises have isolated state, rules adapters, hint, retry, reset, and UI tags without BD-032`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setExerciseSolved("EX_DUAL_301", false)
        prefs.setExerciseSolved("EX_DUAL_302", false)

        val repo = createTwoExerciseChapterRepository(app)
        val validation = repo.validateChapterPack("CH_EX_DUAL", "tactical_test_book")
        assertTrue("Dual exercise chapter pack must be valid: ${validation.errorMessages}", validation.isValid)

        val viewModel = ChapterViewModel(app, customRepository = repo)
        val initialUiState = viewModel.uiState.value

        // Active book and chapter resolved dynamically from catalog (no CH01 fallback)
        assertEquals("tactical_test_book", initialUiState.activeBookId)
        assertEquals("CH_EX_DUAL", initialUiState.activeChapterId)
        assertEquals("فصل سوم", initialUiState.activeChapterShortTitleFa)
        assertFalse(initialUiState.exerciseStates.containsKey("EX_CH01_BD032"))

        val ex1Initial = initialUiState.exerciseStateFor("EX_DUAL_301")
        val ex2Initial = initialUiState.exerciseStateFor("EX_DUAL_302")
        assertNotNull(ex1Initial)
        assertNotNull(ex2Initial)
        assertNotEquals(ex1Initial!!.currentFen, ex2Initial!!.currentFen)

        // 1. Select White Rook on d1 (file=3, rank=0) in EX_DUAL_301 and toggle its legal move hints
        viewModel.onExerciseSquareClicked("EX_DUAL_301", 3, 0)
        viewModel.toggleExerciseLegalMovesHint("EX_DUAL_301")
        val ex1Selected = viewModel.uiState.value.exerciseStateFor("EX_DUAL_301")!!
        val ex2Unchanged = viewModel.uiState.value.exerciseStateFor("EX_DUAL_302")!!
        assertEquals(ExercisePhase.PIECE_SELECTED, ex1Selected.phase)
        assertEquals("d1", ex1Selected.selectedSquare)
        assertTrue(ex1Selected.showLegalMovesHint)
        assertTrue(ex1Selected.visibleTargetSquares.isNotEmpty())
        // EX_DUAL_302 must remain completely untouched
        assertEquals(ExercisePhase.IDLE, ex2Unchanged.phase)
        assertNull(ex2Unchanged.selectedSquare)
        assertFalse(ex2Unchanged.showLegalMovesHint)

        // 2. Play wrong legal move in EX_DUAL_301: Rd1 -> d5 (file=3, rank=4)
        viewModel.onExerciseSquareClicked("EX_DUAL_301", 3, 4)
        val transWrong = viewModel.uiState.value.exerciseStateFor("EX_DUAL_301")?.activeTransition
        if (transWrong != null) {
            viewModel.onExerciseTransitionFinished("EX_DUAL_301", transWrong.transitionId)
        }
        val ex1Wrong = viewModel.uiState.value.exerciseStateFor("EX_DUAL_301")!!
        assertEquals(ExercisePhase.WRONG_LEGAL_MOVE, ex1Wrong.phase)
        assertTrue(ex1Wrong.canRetry)
        assertEquals("حرکت تمرین اول اشتباه است. دوباره تلاش کنید.", ex1Wrong.feedbackMessage)
        assertEquals(ExercisePhase.IDLE, viewModel.uiState.value.exerciseStateFor("EX_DUAL_302")!!.phase)

        // 3. Render both InteractiveExerciseViews simultaneously and test exerciseId-based testTags
        composeTestRule.setContent {
            val st = viewModel.uiState.value
            MyApplicationTheme {
                Column {
                    InteractiveExerciseView(
                        exerciseId = "EX_DUAL_301",
                        state = st.exerciseStateFor("EX_DUAL_301")!!,
                        definition = st.exerciseDefinitions["EX_DUAL_301"],
                        onSquareClick = { f, r -> viewModel.onExerciseSquareClicked("EX_DUAL_301", f, r) },
                        onReset = { viewModel.resetExercise("EX_DUAL_301") },
                        onRetry = { viewModel.retryExercise("EX_DUAL_301") },
                        onToggleLegalMovesHint = { viewModel.toggleExerciseLegalMovesHint("EX_DUAL_301") },
                        maxBoardHeight = 160.dp
                    )
                    InteractiveExerciseView(
                        exerciseId = "EX_DUAL_302",
                        state = st.exerciseStateFor("EX_DUAL_302")!!,
                        definition = st.exerciseDefinitions["EX_DUAL_302"],
                        onSquareClick = { f, r -> viewModel.onExerciseSquareClicked("EX_DUAL_302", f, r) },
                        onReset = { viewModel.resetExercise("EX_DUAL_302") },
                        onRetry = { viewModel.retryExercise("EX_DUAL_302") },
                        onToggleLegalMovesHint = { viewModel.toggleExerciseLegalMovesHint("EX_DUAL_302") },
                        maxBoardHeight = 160.dp
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("تمرین اول: مات در عرض آخر برای سفید").assertIsDisplayed()
        composeTestRule.onNodeWithText("تمرین دوم: مات در عرض اول برای سیاه").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exercise_retry_btn_EX_DUAL_301").assertIsDisplayed()

        // 4. Click Retry on EX_DUAL_301 via its exerciseId-specific tag
        composeTestRule.onNodeWithTag("exercise_retry_btn_EX_DUAL_301").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ExercisePhase.IDLE, viewModel.uiState.value.exerciseStateFor("EX_DUAL_301")!!.phase)
        assertNull(viewModel.uiState.value.exerciseStateFor("EX_DUAL_301")!!.feedbackMessage)

        // 5. Solve EX_DUAL_302 (Black Rook d8 [3,7] -> d1# [3,0]) while EX_DUAL_301 remains IDLE
        viewModel.onExerciseSquareClicked("EX_DUAL_302", 3, 7)
        viewModel.onExerciseSquareClicked("EX_DUAL_302", 3, 0)
        val transSolve2 = viewModel.uiState.value.exerciseStateFor("EX_DUAL_302")?.activeTransition
        if (transSolve2 != null) {
            viewModel.onExerciseTransitionFinished("EX_DUAL_302", transSolve2.transitionId)
        }
        composeTestRule.waitForIdle()

        val ex2Solved = viewModel.uiState.value.exerciseStateFor("EX_DUAL_302")!!
        assertTrue(ex2Solved.isSolved)
        assertEquals(ExercisePhase.SOLVED, ex2Solved.phase)
        assertEquals("عالی! تمرین دوم با حرکت Rd1# به درستی حل شد.", ex2Solved.feedbackMessage)
        assertFalse(viewModel.uiState.value.exerciseStateFor("EX_DUAL_301")!!.isSolved)

        // 6. Reset EX_DUAL_302 via UI tag and verify it returns to IDLE
        composeTestRule.onNodeWithTag("exercise_reset_btn_EX_DUAL_302").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.exerciseStateFor("EX_DUAL_302")!!.isSolved)
        assertEquals(ExercisePhase.IDLE, viewModel.uiState.value.exerciseStateFor("EX_DUAL_302")!!.phase)
    }

    @Test
    fun `v19 exercise 3 - ContentBlockValidator rejects ExerciseDefinition with initialFen mismatching diagram initialFen`() {
        val diagram = DiagramItem(
            diagramId = "BD-401",
            pdfPage = 1,
            sectionName = "Test",
            bookContext = "",
            diagramKind = "exercise",
            recommendedInteraction = "interactive_exercise",
            initialFen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
            currentFen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
            sideToMove = "white",
            moveHistory = "",
            pgnRef = null,
            solutionPgnRefs = listOf("SOL_401"),
            highlightSquares = emptyList(),
            relatedDiagramIds = emptyList(),
            visualConfidence = "high",
            implementationNotes = "",
            auditStatus = AuditStatus.VALID
        )
        val section = SectionItem(
            sectionId = "SEC_401",
            index = 0,
            titleEn = "Test",
            titleFa = "تست",
            pdfPages = listOf(1),
            diagramIds = listOf("BD-401"),
            translationSlot = TranslationSlot(
                sectionId = "SEC_401",
                orderedContentBlocks = listOf(
                    SectionContentBlock.ExerciseBlock(
                        blockId = "BLK_401",
                        diagramId = "BD-401",
                        exerciseId = "EX_401"
                    )
                )
            )
        )
        val mismatchedExercise = ExerciseDefinition(
            exerciseId = "EX_401",
            diagramId = "BD-401",
            handlerType = "MATE_IN_ONE",
            acceptedSolutionPgnRefs = listOf("SOL_401"),
            initialFen = "8/8/8/8/8/8/8/4K2k w - - 0 1" // Mismatches diagram.initialFen!
        )

        val result = ContentBlockValidator.validateSectionsAndBlocks(
            sections = listOf(section),
            diagrams = listOf(diagram),
            expectedSectionSlotIds = listOf("SEC_401"),
            exercises = listOf(mismatchedExercise)
        )
        assertFalse("Validator must reject mismatched exercise initialFen", result.isValid)
        assertTrue(result.invalidExerciseIds.contains("EX_401"))
    }

    // =========================================================================
    // Section 4 & 7 — BookNarrationManager Multi-Chapter & Variable-Section Generalization
    // =========================================================================

    @Test
    fun `v19 narration 1 - BookNarrationManager has no CH01 default, handles 3-section chapter, stops on chapter switch, and isolates saved position per chapter`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.narrationEnabled = true
        prefs.clearNarrationResumeState()
        prefs.clearNarrationResumeState(chapterId = "CH99", bookId = "book_test")
        prefs.clearNarrationResumeState(chapterId = "CH98", bookId = "book_test")

        // 1. Newly constructed BookNarrationManager does NOT default to CH01
        val standaloneManager = BookNarrationManager(app, prefs)
        assertEquals("", standaloneManager.activeChapterId)
        assertEquals("", standaloneManager.activeManifestAssetPath)
        assertFalse(standaloneManager.manifest.isValid)

        // NarrationManifestValidator does not default missing chapterId to CH01
        val manifestWithoutChapterId = NarrationManifestValidator.parseManifestJson("""{"schemaVersion": 1, "chapters": []}""")
        assertTrue(manifestWithoutChapterId.isValid)
        assertEquals("", manifestWithoutChapterId.chapterId)

        // 2. Build a 3-section chapter (CH99) and verify continuous playback across all 3 sections
        val threeSections = (1..3).map { idx ->
            SectionItem(
                sectionId = "SEC_CH99_0$idx",
                index = idx - 1,
                titleEn = "Section $idx",
                titleFa = "بخش $idx فصل ۹۹",
                pdfPages = listOf(idx),
                diagramIds = emptyList(),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH99_0$idx",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH99_0${idx}_P001",
                            textFa = "متن بخش $idx"
                        )
                    )
                )
            )
        }
        val ch99ManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH99",
                  "sections": [
                    {
                      "sectionId": "SEC_CH99_01",
                      "blocks": [
                        {
                          "blockId": "SEC_CH99_01_P001",
                          "audioAssetPath": "ch99/s1_p1.ogg",
                          "status": "AVAILABLE",
                          "durationMs": 8000
                        }
                      ]
                    },
                    {
                      "sectionId": "SEC_CH99_02",
                      "blocks": [
                        {
                          "blockId": "SEC_CH99_02_P001",
                          "audioAssetPath": "ch99/s2_p1.ogg",
                          "status": "AVAILABLE",
                          "durationMs": 9000
                        }
                      ]
                    },
                    {
                      "sectionId": "SEC_CH99_03",
                      "blocks": [
                        {
                          "blockId": "SEC_CH99_03_P001",
                          "audioAssetPath": "ch99/s3_p1.ogg",
                          "status": "AVAILABLE",
                          "durationMs": 10000
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val fakeEngine = FakeNarrationPlaybackEngine()
        fakeEngine.registerFakeAsset("ch99/s1_p1.ogg", durationMs = 8_000)
        fakeEngine.registerFakeAsset("ch99/s2_p1.ogg", durationMs = 9_000)
        fakeEngine.registerFakeAsset("ch99/s3_p1.ogg", durationMs = 10_000)

        val nm = BookNarrationManager(app, prefs, engineOverride = fakeEngine)
        nm.switchChapterManifest(
            chapterId = "CH99",
            manifestAssetPath = "books/book_test/CH99/narration/manifest.json",
            sections = threeSections,
            currentPage = null,
            bookId = "book_test",
            assetReaderOverride = { ch99ManifestJson }
        )

        assertTrue(nm.manifest.isValid)
        assertEquals("CH99", nm.manifest.chapterId)
        assertEquals(3, nm.manifest.declaredSectionIds.size)

        val queue = nm.buildContinuousChapterQueue(threeSections)
        assertEquals(
            listOf("SEC_CH99_01_P001", "SEC_CH99_02_P001", "SEC_CH99_03_P001"),
            queue.playableItems.map { it.blockId }
        )

        assertTrue(nm.playContinuousChapter(threeSections, startSectionIndex = 0))
        assertEquals(NarrationStatus.PLAYING, nm.state.value.status)
        assertEquals("SEC_CH99_01_P001", nm.state.value.activeBlockId)
        nm.seekTo(3200)

        // Advance through all 3 sections to completion and verify generic completion message (no "فصل اول")
        fakeEngine.simulateCompletion()
        assertEquals("SEC_CH99_02_P001", nm.state.value.activeBlockId)
        fakeEngine.simulateCompletion()
        assertEquals("SEC_CH99_03_P001", nm.state.value.activeBlockId)
        fakeEngine.simulateCompletion()
        assertEquals(NarrationStatus.COMPLETED, nm.state.value.status)
        assertEquals("پخش صوتی این فصل به پایان رسید", nm.state.value.statusMessageFa)

        // Start again and seek so CH99 saves resume state at SEC_CH99_02_P001 @ 4100ms
        assertTrue(nm.playContinuousChapter(threeSections, startSectionIndex = 1))
        nm.seekTo(4100)

        // 3. Switch to another chapter (CH98) whose manifest is missing:
        //    - Previous playback must stop and release
        //    - Must NOT fall back to CH01 manifest
        //    - Saved resume state for CH98 must be empty while CH99's saved state remains intact!
        val stopCountBeforeSwitch = fakeEngine.stopCount
        val releaseCountBeforeSwitch = fakeEngine.releaseCount

        nm.switchChapterManifest(
            chapterId = "CH98",
            manifestAssetPath = "books/book_test/CH98/narration/missing.json",
            sections = threeSections.take(2),
            currentPage = null,
            bookId = "book_test",
            assetReaderOverride = { throw IllegalStateException("File not found") }
        )

        assertTrue("Switching chapter must stop active player", fakeEngine.stopCount > stopCountBeforeSwitch)
        assertTrue("Switching chapter must release previous player", fakeEngine.releaseCount > releaseCountBeforeSwitch)
        assertFalse("Missing manifest must not fall back to CH01", nm.manifest.isValid)
        assertEquals("CH98", nm.activeChapterId)
        assertNull(nm.state.value.savedSectionId)

        assertFalse(nm.playContinuousChapter(threeSections.take(2)))
        assertEquals(NarrationStatus.UNAVAILABLE, nm.state.value.status)
        assertEquals("فایل صوتی این فصل هنوز آماده نشده است", nm.state.value.statusMessageFa)

        // Verify per-chapter saved resume isolation: CH99 still has its saved position, CH98 does not
        val savedCh99 = prefs.getSavedNarrationResumeState(chapterId = "CH99", bookId = "book_test")
        val savedCh98 = prefs.getSavedNarrationResumeState(chapterId = "CH98", bookId = "book_test")
        assertEquals("SEC_CH99_02", savedCh99.sectionId)
        assertEquals("SEC_CH99_02_P001", savedCh99.blockId)
        assertEquals(4100, savedCh99.positionMs)
        assertNull(savedCh98.sectionId)
        assertEquals(0, savedCh98.positionMs)
    }

    // =========================================================================
    // Section 5 — Official Chess Knight App Launcher Icon Verification
    // =========================================================================

    @Test
    fun `v19 icon - official chess knight master icon, adaptive bitmap foreground, extracted monochrome silhouette, and all 5 mipmap densities exist and are valid`() {
        val root = resolveProjectRoot()

        // 1. Master 1254x1254 PNG in branding/ (outside app/src/main/assets/)
        val masterIconFile = File(root, "branding/chess_knight_app_icon_source.png")
        assertTrue("branding/chess_knight_app_icon_source.png must exist", masterIconFile.exists())
        assertTrue("Master icon must be the original 1.24MB file, not a synthetic SVG export", masterIconFile.length() > 1_000_000L)
        val masterBitmap = BitmapFactory.decodeFile(masterIconFile.absolutePath)
        assertNotNull("Master icon PNG must decode as a valid Bitmap", masterBitmap)
        assertEquals(1254, masterBitmap.width)
        assertEquals(1254, masterBitmap.height)
        assertFalse(
            "Master icon must not be placed inside app/src/main/assets/",
            File(root, "app/src/main/assets/chess_knight_app_icon_source.png").exists()
        )

        // 2. Adaptive icon XMLs & Bitmap drawables (background, foreground, monochrome, anydpi-v26)
        val bgXml = File(root, "app/src/main/res/drawable/ic_launcher_background.xml").readText()
        val fgXml = File(root, "app/src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val monoXml = File(root, "app/src/main/res/drawable/ic_launcher_monochrome.xml").readText()
        val adaptiveXml = File(root, "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val adaptiveRoundXml = File(root, "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml").readText()

        assertTrue(
            "Background must use warm cream color (#FEF0D2 / #FCF2D9)",
            bgXml.contains("#FEF0D2", ignoreCase = true) || bgXml.contains("#FCF2D9", ignoreCase = true)
        )
        assertTrue(
            "Foreground XML must reference @drawable/ic_launcher_foreground_bitmap derived directly from original knight image",
            fgXml.contains("@drawable/ic_launcher_foreground_bitmap")
        )
        assertFalse("Foreground must not contain old Crown icon comment", fgXml.contains("Crown Body"))
        assertTrue(
            "Monochrome XML must reference @drawable/ic_launcher_monochrome_bitmap extracted from original knight image",
            monoXml.contains("@drawable/ic_launcher_monochrome_bitmap")
        )
        assertTrue(adaptiveXml.contains("@drawable/ic_launcher_monochrome"))
        assertTrue(adaptiveRoundXml.contains("@drawable/ic_launcher_monochrome"))

        val fgBitmapFile = File(root, "app/src/main/res/drawable-nodpi/ic_launcher_foreground_bitmap.png")
        val monoBitmapFile = File(root, "app/src/main/res/drawable-nodpi/ic_launcher_monochrome_bitmap.png")
        assertTrue("ic_launcher_foreground_bitmap.png must exist", fgBitmapFile.exists())
        assertTrue("ic_launcher_monochrome_bitmap.png must exist", monoBitmapFile.exists())
        val fgBmp = BitmapFactory.decodeFile(fgBitmapFile.absolutePath)
        val monoBmp = BitmapFactory.decodeFile(monoBitmapFile.absolutePath)
        assertNotNull(fgBmp)
        assertNotNull(monoBmp)
        assertEquals(432, fgBmp.width)
        assertEquals(432, fgBmp.height)
        assertEquals(432, monoBmp.width)
        assertEquals(432, monoBmp.height)

        // 3. All 5 mipmap densities for ic_launcher.png and ic_launcher_round.png
        val expectedDensities = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192
        )
        for ((folder, expectedPx) in expectedDensities) {
            for (fileName in listOf("ic_launcher.png", "ic_launcher_round.png")) {
                val file = File(root, "app/src/main/res/$folder/$fileName")
                assertTrue("$folder/$fileName must exist", file.exists())
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                assertNotNull("$folder/$fileName must decode", bmp)
                assertEquals("$folder/$fileName width", expectedPx, bmp.width)
                assertEquals("$folder/$fileName height", expectedPx, bmp.height)
            }
        }

        // 4. Updated preview screenshot exists
        val previewFile = File(root, "screenshots/20_official_knight_app_icon_preview.webp")
        assertTrue("screenshots/20_official_knight_app_icon_preview.webp must exist", previewFile.exists())
        val previewBmp = BitmapFactory.decodeFile(previewFile.absolutePath)
        assertNotNull(previewBmp)
    }

    // =========================================================================
    // Version 19.1 Section 2 — Recursive Narration Manifest Validation (9 Scenarios)
    // =========================================================================

    @Test
    fun `v19_1 narration 2 - recursive narration manifest validation covers all 9 required scenarios and unified contract`() {
        val root = resolveProjectRoot()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val ch01Sections = repo.getSections("CH01", "how_to_win_at_chess")
        val ch01SectionIds = ch01Sections.map { it.sectionId }.toSet()
        val ch01BlockIdsBySection = ch01Sections.associate { sec ->
            sec.sectionId to sec.translationSlot.orderedContentBlocks.map { it.blockId }.toSet()
        }

        // Verify schema file uses unified audioAssetPath and status contract
        val schemaText = File(root, "content_schemas/narration_manifest.schema.json").readText()
        assertTrue("Schema must require audioAssetPath", schemaText.contains("\"audioAssetPath\""))
        assertTrue("Schema must require status", schemaText.contains("\"status\""))
        assertTrue("Schema must define durationMs", schemaText.contains("\"durationMs\""))
        assertFalse("Schema must not allow legacy 'asset' field", schemaText.contains("\"asset\""))
        assertFalse("Schema must not allow legacy 'segments' field", schemaText.contains("\"segments\""))

        // Scenario 1 & Scenario 8: Real CH01 manifest with `chapters` structure and empty `blocks: []` is valid without error
        val realCh01ManifestFile = File(root, "app/src/main/assets/books/how_to_win_at_chess/CH01/narration/manifest.json")
        val realCh01Json = realCh01ManifestFile.readText()
        val resRealCh01 = NarrationManifestValidator.validateManifestStructure(
            jsonString = realCh01Json,
            expectedChapterId = "CH01",
            validSectionIds = ch01SectionIds,
            validBlockIdsBySection = ch01BlockIdsBySection,
            assetExistsPredicate = { false }
        )
        assertTrue("Scenario 1 & 8: Real CH01 manifest with empty blocks must be valid: ${resRealCh01.errors}", resRealCh01.isValid)
        assertEquals(listOf("CH01"), resRealCh01.chapterIds)
        assertEquals(6, resRealCh01.sectionIdsByChapter["CH01"]?.size)
        assertEquals(0, resRealCh01.validatedBlockCount)

        // Scenario 2: Manifest with multiple Chapters is recognized as valid
        val multiChapterJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    { "sectionId": "SEC_CH01_01_OPENING", "blocks": [] }
                  ]
                },
                {
                  "chapterId": "CH02",
                  "sections": [
                    { "sectionId": "SEC_CH02_01_INTRO", "blocks": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val resMultiChapter = NarrationManifestValidator.validateManifestStructure(
            jsonString = multiChapterJson,
            expectedChapterId = "CH01",
            validSectionIdsByChapter = mapOf(
                "CH01" to ch01SectionIds,
                "CH02" to setOf("SEC_CH02_01_INTRO")
            )
        )
        assertTrue("Scenario 2: Multi-chapter manifest must be valid: ${resMultiChapter.errors}", resMultiChapter.isValid)
        assertEquals(listOf("CH01", "CH02"), resMultiChapter.chapterIds)
        val parsedMulti = NarrationManifestValidator.parseManifestJson(multiChapterJson, expectedChapterId = "CH02")
        assertTrue(parsedMulti.isValid)
        assertEquals("CH02", parsedMulti.chapterId)
        assertEquals(setOf("SEC_CH02_01_INTRO"), parsedMulti.declaredSectionIds)

        // Scenario 3: Invalid sectionId (both malformed and non-existent in chapter) is detected and rejected
        val invalidSectionManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    { "sectionId": "SEC_NON_EXISTENT_999", "blocks": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val resInvalidSection = NarrationManifestValidator.validateManifestStructure(
            jsonString = invalidSectionManifestJson,
            expectedChapterId = "CH01",
            validSectionIds = ch01SectionIds
        )
        assertFalse("Scenario 3: Unknown sectionId must be rejected", resInvalidSection.isValid)
        assertTrue(resInvalidSection.errors.any { it.contains("SEC_NON_EXISTENT_999") })

        // Scenario 4: Duplicate blockId is detected and rejected
        val firstRealBlockId = ch01BlockIdsBySection["SEC_CH01_01_OPENING"]!!.first()
        val duplicateBlockManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    {
                      "sectionId": "SEC_CH01_01_OPENING",
                      "blocks": [
                        { "blockId": "$firstRealBlockId", "audioAssetPath": "ch01/b1.ogg", "status": "UNAVAILABLE", "durationMs": 4500 },
                        { "blockId": "$firstRealBlockId", "audioAssetPath": "ch01/b2.ogg", "status": "UNAVAILABLE", "durationMs": 5000 }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val resDuplicateBlock = NarrationManifestValidator.validateManifestStructure(
            jsonString = duplicateBlockManifestJson,
            expectedChapterId = "CH01",
            validSectionIds = ch01SectionIds,
            validBlockIdsBySection = ch01BlockIdsBySection
        )
        assertFalse("Scenario 4: Duplicate blockId must be rejected", resDuplicateBlock.isValid)
        assertTrue(resDuplicateBlock.errors.any { it.contains("Duplicate blockId", ignoreCase = true) })

        // Scenario 5: Path containing ../ is rejected
        val traversalPathManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    {
                      "sectionId": "SEC_CH01_01_OPENING",
                      "blocks": [
                        { "blockId": "$firstRealBlockId", "audioAssetPath": "ch01/../../secret.mp3", "status": "AVAILABLE", "durationMs": 3000 }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val resTraversal = NarrationManifestValidator.validateManifestStructure(
            jsonString = traversalPathManifestJson,
            expectedChapterId = "CH01",
            validSectionIds = ch01SectionIds,
            validBlockIdsBySection = ch01BlockIdsBySection
        )
        assertFalse("Scenario 5: Path with ../ must be rejected", resTraversal.isValid)
        assertTrue(resTraversal.errors.any { it.contains("audioAssetPath") })

        // Scenario 6: Non-existent audio file when status is AVAILABLE is rejected
        val missingAudioManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH01",
                  "sections": [
                    {
                      "sectionId": "SEC_CH01_01_OPENING",
                      "blocks": [
                        { "blockId": "$firstRealBlockId", "audioAssetPath": "ch01/missing_file.mp3", "status": "AVAILABLE", "durationMs": 3500 }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val resMissingAudio = NarrationManifestValidator.validateManifestStructure(
            jsonString = missingAudioManifestJson,
            expectedChapterId = "CH01",
            validSectionIds = ch01SectionIds,
            validBlockIdsBySection = ch01BlockIdsBySection,
            assetExistsPredicate = { false }
        )
        assertFalse("Scenario 6: Non-existent audio file when AVAILABLE must be rejected", resMissingAudio.isValid)
        assertTrue(resMissingAudio.errors.any { it.contains("does not exist") })

        // Scenario 7: Real audio file in `assets/narration/` with valid extension is accepted,
        // and all 8 mandatory audio path resolution rules (including rejection of `assets/$path` and `CH01/$path`) are enforced
        val tempAssetsDir = kotlin.io.path.createTempDirectory("narration_assets_v19_3").toFile()
        try {
            // 1. Valid audio file inside `assets/narration/ch01/sec01_intro.wav`
            val validNarrationAudioFile = File(tempAssetsDir, "narration/ch01/sec01_intro.wav")
            validNarrationAudioFile.parentFile?.mkdirs()
            validNarrationAudioFile.writeBytes(ByteArray(44))

            // 2. Audio file placed ONLY in root of `assets/` (outside `assets/narration/`)
            val rootOnlyAudioFile = File(tempAssetsDir, "ch01/only_in_assets_root.wav")
            rootOnlyAudioFile.parentFile?.mkdirs()
            rootOnlyAudioFile.writeBytes(ByteArray(44))

            // 3. Audio file placed ONLY in `CH01/` chapter folder (outside `assets/narration/`)
            val ch01FolderOnlyAudioFile = File(tempAssetsDir, "books/how_to_win_at_chess/CH01/ch01/only_in_ch01.wav")
            ch01FolderOnlyAudioFile.parentFile?.mkdirs()
            ch01FolderOnlyAudioFile.writeBytes(ByteArray(44))

            // 4. Audio file placed in another sibling directory outside `assets/narration/`
            val outsideNarrationAudioFile = File(tempAssetsDir, "other_audio/valid_name.mp3")
            outsideNarrationAudioFile.parentFile?.mkdirs()
            outsideNarrationAudioFile.writeBytes(ByteArray(44))

            val validFixtureManifestJson = """
                {
                  "schemaVersion": 1,
                  "chapters": [
                    {
                      "chapterId": "CH01",
                      "sections": [
                        {
                          "sectionId": "SEC_CH01_01_OPENING",
                          "blocks": [
                            {
                              "blockId": "$firstRealBlockId",
                              "audioAssetPath": "ch01/sec01_intro.wav",
                              "status": "AVAILABLE",
                              "durationMs": 6200
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val resFixture = NarrationManifestValidator.validateManifestStructure(
                jsonString = validFixtureManifestJson,
                expectedChapterId = "CH01",
                validSectionIds = ch01SectionIds,
                validBlockIdsBySection = ch01BlockIdsBySection,
                assetExistsPredicate = { relPath ->
                    NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, relPath) != null
                }
            )
            assertTrue("Scenario 7 (Rule 1): File in assets/narration/ with AVAILABLE must be accepted: ${resFixture.errors}", resFixture.isValid)
            assertEquals(1, resFixture.validatedBlockCount)

            val parsedFixture = NarrationManifestValidator.parseManifestJson(validFixtureManifestJson, expectedChapterId = "CH01")
            assertTrue(parsedFixture.isValid)
            assertEquals("ch01/sec01_intro.wav", parsedFixture.resolveAssetPath("SEC_CH01_01_OPENING", firstRealBlockId))
            assertEquals(6200, parsedFixture.entriesByBlockId[firstRealBlockId]?.durationMs)

            // Verify with FakeNarrationPlaybackEngine that the received relative path and expected physical path in `assets/narration/` match identically
            val prefs = UserPreferencesManager(app)
            prefs.narrationEnabled = true
            val fakeEngine = FakeNarrationPlaybackEngine()
            fakeEngine.registerFakeAsset("ch01/sec01_intro.wav", durationMs = 6200)
            val nm = BookNarrationManager(app, prefs, engineOverride = fakeEngine)
            nm.switchChapterManifest(
                chapterId = "CH01",
                manifestAssetPath = "books/how_to_win_at_chess/CH01/narration/manifest.json",
                sections = ch01Sections,
                currentPage = null,
                bookId = "how_to_win_at_chess",
                assetReaderOverride = { validFixtureManifestJson }
            )
            assertTrue(nm.playContinuousChapter(ch01Sections, startSectionIndex = 0))
            assertEquals("ch01/sec01_intro.wav", fakeEngine.activeAssetPath)
            val resolvedRuntimeFile = NarrationManifestValidator.resolveAudioFileInAssetsDir(
                tempAssetsDir,
                fakeEngine.activeAssetPath!!
            )
            assertNotNull("Engine asset path must resolve inside assets/narration/", resolvedRuntimeFile)
            assertEquals(validNarrationAudioFile.canonicalFile, resolvedRuntimeFile?.canonicalFile)
            nm.release()

            // Rule 2: File existing ONLY in root of `assets/` (`assets/ch01/only_in_assets_root.wav`) must be rejected
            assertTrue(rootOnlyAudioFile.exists())
            assertNull(NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, "ch01/only_in_assets_root.wav"))
            val rootOnlyManifestJson = validFixtureManifestJson.replace("ch01/sec01_intro.wav", "ch01/only_in_assets_root.wav")
            val resRootOnly = NarrationManifestValidator.validateManifestStructure(
                jsonString = rootOnlyManifestJson,
                expectedChapterId = "CH01",
                validSectionIds = ch01SectionIds,
                validBlockIdsBySection = ch01BlockIdsBySection,
                assetExistsPredicate = { relPath ->
                    NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, relPath) != null
                }
            )
            assertFalse("Rule 2: File only in root of assets/ must be rejected", resRootOnly.isValid)

            // Rule 3: File existing ONLY in CH01 folder (`assets/books/how_to_win_at_chess/CH01/ch01/only_in_ch01.wav`) must be rejected
            assertTrue(ch01FolderOnlyAudioFile.exists())
            assertNull(NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, "ch01/only_in_ch01.wav"))
            val ch01OnlyManifestJson = validFixtureManifestJson.replace("ch01/sec01_intro.wav", "ch01/only_in_ch01.wav")
            val resCh01Only = NarrationManifestValidator.validateManifestStructure(
                jsonString = ch01OnlyManifestJson,
                expectedChapterId = "CH01",
                validSectionIds = ch01SectionIds,
                validBlockIdsBySection = ch01BlockIdsBySection,
                assetExistsPredicate = { relPath ->
                    NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, relPath) != null
                }
            )
            assertFalse("Rule 3: File only in CH01 folder must be rejected", resCh01Only.isValid)

            // Rule 4: File outside `assets/narration/` despite valid name (`other_audio/valid_name.mp3`) must be rejected
            assertTrue(outsideNarrationAudioFile.exists())
            assertNull(NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, "other_audio/valid_name.mp3"))
            val outsideManifestJson = validFixtureManifestJson.replace("ch01/sec01_intro.wav", "other_audio/valid_name.mp3")
            val resOutside = NarrationManifestValidator.validateManifestStructure(
                jsonString = outsideManifestJson,
                expectedChapterId = "CH01",
                validSectionIds = ch01SectionIds,
                validBlockIdsBySection = ch01BlockIdsBySection,
                assetExistsPredicate = { relPath ->
                    NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, relPath) != null
                }
            )
            assertFalse("Rule 4: File outside assets/narration/ must be rejected", resOutside.isValid)

            // Rule 5: UNAVAILABLE without physical file remains permitted
            val unavailableManifestJson = validFixtureManifestJson
                .replace("ch01/sec01_intro.wav", "ch01/unrecorded_block.mp3")
                .replace("\"AVAILABLE\"", "\"UNAVAILABLE\"")
            val resUnavailable = NarrationManifestValidator.validateManifestStructure(
                jsonString = unavailableManifestJson,
                expectedChapterId = "CH01",
                validSectionIds = ch01SectionIds,
                validBlockIdsBySection = ch01BlockIdsBySection,
                assetExistsPredicate = { relPath ->
                    NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, relPath) != null
                }
            )
            assertTrue("Rule 5: UNAVAILABLE without physical file must be accepted", resUnavailable.isValid)

            // Rule 6 & Rule 7: Path containing `../` or absolute path is rejected by resolveAudioFileInAssetsDir
            assertNull(NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, "../ch01/only_in_assets_root.wav"))
            assertNull(NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, "/narration/ch01/sec01_intro.wav"))

            // Rule 8: Missing AVAILABLE file is rejected and also verify build.gradle.kts has no fallback to assets/ or chapterFolder/
            val buildGradleText = File(root, "app/build.gradle.kts").readText()
            assertTrue(buildGradleText.contains("File(assetsDir, \"narration/\$audioPath\")"))
            assertFalse(
                "build.gradle.kts must not fall back to File(assetsDir, audioPath)",
                buildGradleText.contains("File(assetsDir, audioPath)")
            )
            assertFalse(
                "build.gradle.kts must not fall back to File(chapterFolder, audioPath)",
                buildGradleText.contains("File(chapterFolder, audioPath)")
            )
        } finally {
            tempAssetsDir.deleteRecursively()
        }

        // Scenario 9: chapterId mismatch is detected and rejected
        val mismatchedChapterManifestJson = """
            {
              "schemaVersion": 1,
              "chapters": [
                {
                  "chapterId": "CH09_WRONG",
                  "sections": [
                    { "sectionId": "SEC_CH01_01_OPENING", "blocks": [] }
                  ]
                }
              ]
            }
        """.trimIndent()
        val resMismatchedChapter = NarrationManifestValidator.validateManifestStructure(
            jsonString = mismatchedChapterManifestJson,
            expectedChapterId = "CH01",
            validSectionIds = ch01SectionIds
        )
        assertFalse("Scenario 9: chapterId mismatch must be rejected by validateManifestStructure", resMismatchedChapter.isValid)
        assertTrue(resMismatchedChapter.errors.any { it.contains("does not match expected chapterId") })

        val parsedMismatched = NarrationManifestValidator.parseManifestJson(mismatchedChapterManifestJson, expectedChapterId = "CH01")
        assertFalse("Scenario 9: chapterId mismatch must be rejected by parseManifestJson", parsedMismatched.isValid)
    }

    // =========================================================================
    // Version 19.3 Section 2 — Structural JSON Schema Evaluation vs Kotlin Semantic Validation (18 Fixtures)
    // =========================================================================

    /**
     * Manual evaluator that strictly checks the structural rules defined in
     * `content_schemas/narration_manifest.schema.json` (Layer 1: Structural Validation):
     * - Required properties & `additionalProperties: false` at all 4 levels (`root`, `chapter`, `section`, `block`)
     * - Data types (`object`, `array`, `string`, `integer`)
     * - `Identifier` pattern & `not: { pattern: "__part_" }`
     * - `SafeAudioAssetPath` pattern & length bounds
     * - `BlockAudioStatus` enum (`AVAILABLE`, `UNAVAILABLE`)
     * - `durationMs` (`integer >= 0`)
     * - Standard JSON Schema `uniqueItems: true` (rejects only deeply identical array item objects,
     *   NOT two distinct objects that happen to share the same `blockId`, `sectionId`, or `chapterId`).
     *
     * Semantic rules (duplicate IDs across distinct objects, `chapterId`/`sectionId` ownership,
     * and physical file existence under `assets/narration/`) belong to Layer 2 (Kotlin Semantic Validation)
     * and Layer 3 (Gradle Content Pack Validation), NOT Layer 1 (JSON Schema).
     */
    private fun evaluateAgainstSchemaFile(
        schemaRoot: org.json.JSONObject,
        manifestJson: String
    ): Boolean {
        val defs = schemaRoot.getJSONObject("\$defs")
        val idDef = defs.getJSONObject("Identifier")
        val idRegex = Regex(idDef.getString("pattern"))
        val idNotRegex = Regex(idDef.getJSONObject("not").getString("pattern"))
        val pathDef = defs.getJSONObject("SafeAudioAssetPath")
        val pathMinLen = pathDef.optInt("minLength", 1)
        val pathMaxLen = pathDef.optInt("maxLength", 512)
        val pathRegex = Regex(pathDef.getString("pattern"))
        val statusEnumArr = defs.getJSONObject("BlockAudioStatus").getJSONArray("enum")
        val allowedStatuses = (0 until statusEnumArr.length()).map { statusEnumArr.getString(it) }.toSet()

        fun validId(s: Any?): Boolean =
            s is String && s.isNotEmpty() && idRegex.matches(s) && !idNotRegex.containsMatchIn(s)

        fun canonicalJsonValue(v: Any?): String = when (v) {
            null, org.json.JSONObject.NULL -> "null"
            is org.json.JSONObject -> {
                val sortedKeys = mutableListOf<String>().apply { v.keys().forEach { add(it) } }.sorted()
                sortedKeys.joinToString(prefix = "{", postfix = "}") { k ->
                    "\"$k\":${canonicalJsonValue(v.opt(k))}"
                }
            }
            is org.json.JSONArray -> {
                (0 until v.length()).joinToString(prefix = "[", postfix = "]") { idx ->
                    canonicalJsonValue(v.opt(idx))
                }
            }
            is String -> "\"$v\""
            else -> v.toString()
        }

        val root = try {
            org.json.JSONObject(manifestJson)
        } catch (_: Exception) {
            return false
        }

        val rootKeys = mutableSetOf<String>().apply { root.keys().forEach { add(it) } }
        if (rootKeys != setOf("schemaVersion", "chapters")) return false
        val sv = root.opt("schemaVersion")
        if ((sv as? Number)?.toInt() != 1 || sv is Double || sv is Float) return false
        val chapters = root.opt("chapters") as? org.json.JSONArray ?: return false

        val seenIdenticalChapterItems = mutableSetOf<String>()
        for (c in 0 until chapters.length()) {
            val ch = chapters.optJSONObject(c) ?: return false
            if (!seenIdenticalChapterItems.add(canonicalJsonValue(ch))) return false

            val chKeys = mutableSetOf<String>().apply { ch.keys().forEach { add(it) } }
            if (chKeys != setOf("chapterId", "sections")) return false
            val chId = ch.opt("chapterId")
            if (!validId(chId)) return false

            val sections = ch.opt("sections") as? org.json.JSONArray ?: return false
            val seenIdenticalSectionItems = mutableSetOf<String>()
            for (s in 0 until sections.length()) {
                val sec = sections.optJSONObject(s) ?: return false
                if (!seenIdenticalSectionItems.add(canonicalJsonValue(sec))) return false

                val secKeys = mutableSetOf<String>().apply { sec.keys().forEach { add(it) } }
                if (secKeys != setOf("sectionId", "blocks")) return false
                val secId = sec.opt("sectionId")
                if (!validId(secId)) return false

                val blocks = sec.opt("blocks") as? org.json.JSONArray ?: return false
                val seenIdenticalBlockItems = mutableSetOf<String>()
                for (b in 0 until blocks.length()) {
                    val blk = blocks.optJSONObject(b) ?: return false
                    if (!seenIdenticalBlockItems.add(canonicalJsonValue(blk))) return false

                    val blkKeys = mutableSetOf<String>().apply { blk.keys().forEach { add(it) } }
                    if (!blkKeys.containsAll(setOf("blockId", "audioAssetPath", "status"))) return false
                    if ((blkKeys - setOf("blockId", "audioAssetPath", "status", "durationMs")).isNotEmpty()) return false
                    val blkId = blk.opt("blockId")
                    if (!validId(blkId)) return false
                    val path = blk.opt("audioAssetPath")
                    if (path !is String || path.length !in pathMinLen..pathMaxLen || !pathRegex.matches(path)) return false
                    val st = blk.opt("status")
                    if (st !is String || st !in allowedStatuses) return false
                    if (blk.has("durationMs")) {
                        if (blk.isNull("durationMs")) return false
                        val dur = blk.opt("durationMs")
                        val durInt = when (dur) {
                            is Int -> dur
                            is Long -> if (dur in 0..Int.MAX_VALUE) dur.toInt() else -1
                            else -> -1
                        }
                        if (durInt < 0) return false
                    }
                }
            }
        }
        return true
    }

    @Test
    fun `v19_2 narration 3 - all 18 mandatory fixtures verify structural JSON Schema evaluation and Kotlin runtime semantic validation`() {
        val root = resolveProjectRoot()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val ch01Sections = repo.getSections("CH01", "how_to_win_at_chess")
        val ch01SectionIds = ch01Sections.map { it.sectionId }.toSet()
        val ch01BlockIdsBySection = ch01Sections.associate { sec ->
            sec.sectionId to sec.translationSlot.orderedContentBlocks.map { it.blockId }.toSet()
        }
        val sec1Block1 = ch01BlockIdsBySection["SEC_CH01_01_OPENING"]!!.first()
        val sec2Block1 = ch01BlockIdsBySection["SEC_CH01_02_CHECKMATE"]!!.first()

        val schemaJsonObj = org.json.JSONObject(File(root, "content_schemas/narration_manifest.schema.json").readText())

        val tempAssetsDir = kotlin.io.path.createTempDirectory("narration_v19_3_fixtures").toFile()
        try {
            // Audio files placed strictly under `<tempAssetsDir>/narration/chapter_01/...` matching real `assets/narration/`
            val realAudio1 = File(tempAssetsDir, "narration/chapter_01/audio_001.mp3").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(64))
            }
            val realAudio2 = File(tempAssetsDir, "narration/chapter_01/audio_002.ogg").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(64))
            }
            assertTrue(realAudio1.exists() && realAudio2.exists())

            data class FixtureCase(
                val id: Int,
                val name: String,
                val json: String,
                val expectedSemanticValid: Boolean,
                val expectedStructuralSchemaValid: Boolean = expectedSemanticValid,
                val expectedChapterId: String? = "CH01"
            )

            val fixtures = listOf(
                // 1. Valid empty Manifest
                FixtureCase(
                    id = 1,
                    name = "1. Valid empty Manifest",
                    json = """{"schemaVersion": 1, "chapters": []}""",
                    expectedSemanticValid = true,
                    expectedStructuralSchemaValid = true
                ),
                // 2. Manifest with 1 Chapter and 1 Section
                FixtureCase(
                    id = 2,
                    name = "2. Manifest with 1 Chapter and 1 Section",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                { "sectionId": "SEC_CH01_01_OPENING", "blocks": [] }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = true,
                    expectedStructuralSchemaValid = true
                ),
                // 3. Manifest with multiple Chapters
                FixtureCase(
                    id = 3,
                    name = "3. Manifest with multiple Chapters",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                { "sectionId": "SEC_CH01_01_OPENING", "blocks": [] }
                              ]
                            },
                            {
                              "chapterId": "CH02",
                              "sections": [
                                { "sectionId": "SEC_CH02_01_OPENING", "blocks": [] }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = true,
                    expectedStructuralSchemaValid = true
                ),
                // 4. Multiple Sections with independent Blocks
                FixtureCase(
                    id = 4,
                    name = "4. Multiple Sections with independent Blocks",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/audio_001.mp3",
                                      "status": "AVAILABLE",
                                      "durationMs": 12000
                                    }
                                  ]
                                },
                                {
                                  "sectionId": "SEC_CH01_02_CHECKMATE",
                                  "blocks": [
                                    {
                                      "blockId": "$sec2Block1",
                                      "audioAssetPath": "chapter_01/audio_002.ogg",
                                      "status": "AVAILABLE",
                                      "durationMs": 9500
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = true,
                    expectedStructuralSchemaValid = true
                ),
                // 5. Valid Block with existing audio file in assets/narration/
                FixtureCase(
                    id = 5,
                    name = "5. Valid Block with existing audio file",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/audio_001.mp3",
                                      "status": "AVAILABLE",
                                      "durationMs": 12000
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = true,
                    expectedStructuralSchemaValid = true
                ),
                // 6. Block with status UNAVAILABLE and without audio file
                FixtureCase(
                    id = 6,
                    name = "6. Block with status UNAVAILABLE and without audio file",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/not_created_yet.mp3",
                                      "status": "UNAVAILABLE",
                                      "durationMs": 0
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = true,
                    expectedStructuralSchemaValid = true
                ),
                // 7. Negative durationMs (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 7,
                    name = "7. Negative durationMs",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/audio_001.mp3",
                                      "status": "AVAILABLE",
                                      "durationMs": -250
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 8. Empty chapterId (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 8,
                    name = "8. Empty chapterId",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "",
                              "sections": []
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 9. Empty sectionId (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 9,
                    name = "9. Empty sectionId",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                { "sectionId": "", "blocks": [] }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 10. Duplicate blockId with distinct audioAssetPath values:
                //     Structural Schema ACCEPTS (since the two JSON objects are not identical),
                //     Kotlin Semantic Validator REJECTS (duplicate blockId)
                FixtureCase(
                    id = 10,
                    name = "10. Duplicate blockId with different audioAssetPath",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/audio_001.mp3",
                                      "status": "AVAILABLE"
                                    },
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/audio_002.ogg",
                                      "status": "AVAILABLE"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = true
                ),
                // 11. Path containing ../ (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 11,
                    name = "11. Path containing ../",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/../secret.mp3",
                                      "status": "AVAILABLE"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 12. Absolute path (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 12,
                    name = "12. Absolute path",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "/sdcard/chapter_01/audio_001.mp3",
                                      "status": "AVAILABLE"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 13. URL instead of file path (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 13,
                    name = "13. URL instead of file path",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "https://example.com/audio_001.mp3",
                                      "status": "AVAILABLE"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 14. Missing audio file with status AVAILABLE:
                //     Structural Schema ACCEPTS (valid syntax), Kotlin Semantic REJECTS (missing file in assets/narration/)
                FixtureCase(
                    id = 14,
                    name = "14. Missing audio file with status AVAILABLE",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/missing_audio_999.mp3",
                                      "status": "AVAILABLE"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = true
                ),
                // 15. Unsupported audio extension (Rejected by Structural Schema & Kotlin Semantic)
                FixtureCase(
                    id = 15,
                    name = "15. Unsupported audio extension",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                {
                                  "sectionId": "SEC_CH01_01_OPENING",
                                  "blocks": [
                                    {
                                      "blockId": "$sec1Block1",
                                      "audioAssetPath": "chapter_01/audio_001.flac",
                                      "status": "AVAILABLE"
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 16. Legacy incompatible Manifest structure (segments / asset / audioAvailable)
                //     (Rejected by Structural Schema & Kotlin Semantic due to additionalProperties: false & missing chapters)
                FixtureCase(
                    id = 16,
                    name = "16. Legacy incompatible Manifest structure",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapterId": "CH01",
                          "segments": [
                            {
                              "sectionId": "SEC_CH01_01_OPENING",
                              "blockId": "$sec1Block1",
                              "asset": "chapter_01/audio_001.mp3",
                              "audioAvailable": true
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = false
                ),
                // 17. Chapter ID mismatch with owning chapter:
                //     Structural Schema ACCEPTS (valid syntax), Kotlin Semantic REJECTS (chapter ownership mismatch)
                FixtureCase(
                    id = 17,
                    name = "17. Chapter ID mismatch with owning chapter",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH05_OTHER",
                              "sections": [
                                { "sectionId": "SEC_CH01_01_OPENING", "blocks": [] }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = true
                ),
                // 18. Section ID mismatch with real chapter sections:
                //     Structural Schema ACCEPTS (valid syntax), Kotlin Semantic REJECTS (section membership mismatch)
                FixtureCase(
                    id = 18,
                    name = "18. Section ID mismatch with real chapter sections",
                    json = """
                        {
                          "schemaVersion": 1,
                          "chapters": [
                            {
                              "chapterId": "CH01",
                              "sections": [
                                { "sectionId": "SEC_CH99_FOREIGN_SECTION", "blocks": [] }
                              ]
                            }
                          ]
                        }
                    """.trimIndent(),
                    expectedSemanticValid = false,
                    expectedStructuralSchemaValid = true
                )
            )

            assertEquals(18, fixtures.size)

            for (fx in fixtures) {
                val schemaOk = evaluateAgainstSchemaFile(schemaJsonObj, fx.json)
                assertEquals(
                    "Structural JSON Schema evaluation mismatch on [${fx.name}]",
                    fx.expectedStructuralSchemaValid,
                    schemaOk
                )

                val runtimeResult = NarrationManifestValidator.validateManifestStructure(
                    jsonString = fx.json,
                    expectedChapterId = fx.expectedChapterId,
                    validSectionIdsByChapter = mapOf(
                        "CH01" to ch01SectionIds,
                        "CH02" to setOf("SEC_CH02_01_OPENING")
                    ),
                    validBlockIdsBySection = ch01BlockIdsBySection,
                    assetExistsPredicate = { relPath ->
                        NarrationManifestValidator.resolveAudioFileInAssetsDir(tempAssetsDir, relPath) != null
                    }
                )
                assertEquals(
                    "Kotlin validateManifestStructure mismatch on [${fx.name}] (errors=${runtimeResult.errors})",
                    fx.expectedSemanticValid,
                    runtimeResult.isValid
                )
            }
        } finally {
            tempAssetsDir.deleteRecursively()
        }
    }
}
