package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.audio.BookNarrationManager
import com.example.chess.ExerciseHandlerRegistry
import com.example.chess.FenParser
import com.example.data.ChapterRepository
import com.example.data.EducationalPlaybackSpeed
import com.example.data.FontSizeScale
import com.example.data.UserPreferencesManager
import com.example.model.AuditStatus
import com.example.model.ContentBlockValidator
import com.example.model.ReaderPageType
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import com.example.model.SectionKind
import com.example.model.TranslationSlot
import com.example.ui.AppTab
import com.example.ui.Bd032ExercisePhase
import com.example.ui.ChapterViewModel
import com.example.ui.screens.ChaptersListScreen
import com.example.ui.screens.SectionsListScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ContentArchitectureVersion18Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    // =========================================================================
    // 9.1 — Chapter 1 Full Parity via Book Content Pack Architecture
    // =========================================================================

    @Test
    fun `ch01 parity 1 to 6 - loads from assets books pack with 6 sections 10 diagrams valid FENs PGNs and BD-027 blocked`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)

        // Catalog & Chapter manifest
        val catalog = repo.getLibraryCatalog()
        assertEquals("how_to_win_at_chess", catalog.defaultBookId)
        val chapters = repo.getChapters()
        assertEquals("Production library.json must have only CH01 enabled", 1, chapters.size)
        assertEquals("CH01", chapters.first().chapterId)

        val manifest = repo.getChapterManifest("CH01")
        assertEquals("CH01", manifest.chapterId)
        assertEquals(6, manifest.sections.size)

        // 1 & 2: 6 sections with exact IDs, order, titles, and SectionKind
        val sections = repo.getSections("CH01")
        assertEquals(6, sections.size)
        val expectedIds = listOf(
            "SEC_CH01_01_OPENING",
            "SEC_CH01_02_CHECKMATE",
            "SEC_CH01_03_RESIGNATION",
            "SEC_CH01_04_TIMING_OUT",
            "SEC_CH01_05_ABANDONMENT",
            "SEC_CH01_06_SUMMARY"
        )
        assertEquals(expectedIds, sections.map { it.sectionId })
        assertEquals(SectionKind.OPENING, sections[0].sectionKind)
        assertEquals(SectionKind.CONTENT, sections[1].sectionKind)
        assertEquals(SectionKind.SUMMARY, sections[5].sectionKind)

        // 3 & 4: 10 diagrams with valid FENs
        val diagrams = repo.getDiagrams("CH01")
        assertEquals(10, diagrams.size)
        assertEquals((25..34).map { "BD-0$it" }, diagrams.map { it.diagramId })
        diagrams.forEach { d ->
            val parsed = FenParser.parse(d.initialFen)
            assertNotNull("FEN must parse for ${d.diagramId}", parsed)
        }

        // 5: All PGNs valid and replayable
        val pgnMap = repo.getAllPgnSequences("CH01")
        assertTrue(pgnMap.containsKey("SCHOLARS_MATE_CANDIDATE"))
        assertTrue(pgnMap.containsKey("FOOLS_MATE_CANDIDATE"))
        assertTrue(pgnMap.containsKey("BD032_MATE_QH8"))
        assertTrue(pgnMap.containsKey("BD032_MATE_QH2"))
        val validation = repo.validateChapterPack("CH01")
        assertTrue("CH01 pack validation must pass: ${validation.errorMessages}", validation.isValid)

        // 6: BD-027 remains BLOCKED_NEEDS_HUMAN_REVIEW from JSON metadata
        val bd027 = diagrams.first { it.diagramId == "BD-027" }
        assertEquals(AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW, bd027.auditStatus)
        assertNotNull(bd027.auditWarning)
        assertTrue(bd027.auditWarning!!.contains("g8"))
    }

    @Test
    fun `ch01 parity 7 to 11 - BD-032 solves with Qh8 and Qh2 paginator narration sounds and preferences work`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setExerciseSolved("EX_CH01_BD032", false)

        val viewModel = ChapterViewModel(app)

        // 7: Exercise BD-032 (EX_CH01_BD032) solves with both Qh8# (e5->h8) and Qh2# (e5->h2)
        // Solution 1: Queen e5 (4,4) -> h8 (7,7)
        viewModel.onBd032SquareClicked(4, 4)
        assertEquals(Bd032ExercisePhase.PIECE_SELECTED, viewModel.uiState.value.bd032State.phase)
        viewModel.onBd032SquareClicked(7, 7)
        val trans1 = viewModel.uiState.value.bd032State.activeTransition
        if (trans1 != null) {
            viewModel.onBd032TransitionFinished(trans1.transitionId)
        }
        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        assertEquals("Qh8#", viewModel.uiState.value.bd032State.solutionPlayed)
        assertTrue(prefs.isExerciseSolved("EX_CH01_BD032"))

        // Reset and test Solution 2: Queen e5 (4,4) -> h2 (7,1)
        viewModel.resetBd032()
        assertFalse(viewModel.uiState.value.bd032State.isSolved)
        viewModel.onBd032SquareClicked(4, 4)
        viewModel.onBd032SquareClicked(7, 1)
        val trans2 = viewModel.uiState.value.bd032State.activeTransition
        if (trans2 != null) {
            viewModel.onBd032TransitionFinished(trans2.transitionId)
        }
        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        assertEquals("Qh2#", viewModel.uiState.value.bd032State.solutionPlayed)

        // 8: ReaderPaginator page counts & page types across all 6 sections
        val sections = viewModel.uiState.value.sections
        val diagrams = viewModel.uiState.value.diagrams
        val summaryPages = ReaderPaginator.paginateSection(
            section = sections[5],
            diagrams = diagrams,
            fontScale = FontSizeScale.NORMAL
        )
        assertTrue(summaryPages.isNotEmpty())
        assertTrue(summaryPages.all { it.pageType == ReaderPageType.SUMMARY && it.sourcePdfPage == 50 })

        // 9: Narration manifest loads from CH01 narration pack with all 6 declared sections via ChapterViewModel
        val narrationManager = viewModel.narrationManager
        assertTrue(narrationManager.manifest.isValid)
        assertEquals("CH01", narrationManager.manifest.chapterId)
        assertEquals(6, narrationManager.manifest.declaredSectionIds.size)
        assertEquals("SEC_CH01_01_OPENING", narrationManager.manifest.declaredSectionIds.first())
        assertEquals("SEC_CH01_06_SUMMARY", narrationManager.manifest.declaredSectionIds.last())

        // 11: UserPreferencesManager saves stable IDs and migrates legacy keys
        prefs.saveReadingPosition(
            bookId = "how_to_win_at_chess",
            chapterId = "CH01",
            sectionId = "SEC_CH01_04_TIMING_OUT",
            sectionIndex = 3,
            pageIndex = 1,
            pageId = "SEC_CH01_04_TIMING_OUT_page_2"
        )
        val savedPos = prefs.getSavedReadingPosition(chapterId = "CH01", bookId = "how_to_win_at_chess")
        assertEquals("how_to_win_at_chess", savedPos.bookId)
        assertEquals("CH01", savedPos.chapterId)
        assertEquals("SEC_CH01_04_TIMING_OUT", savedPos.sectionId)
        assertEquals("SEC_CH01_04_TIMING_OUT_page_2", savedPos.pageId)
    }

    // =========================================================================
    // 9.2 — Multi-Chapter Synthetic Fixture (CH01 + CH02)
    // =========================================================================

    private fun createMultiChapterRepository(context: Context): ChapterRepository {
        val syntheticAssets = mapOf(
            "books/test_library_two_chapters.json" to """
                {
                  "schemaVersion": 1,
                  "defaultBookId": "how_to_win_at_chess",
                  "books": [
                    {
                      "bookId": "how_to_win_at_chess",
                      "titleEn": "How to Win at Chess",
                      "titleFa": "چگونه در شطرنج برنده شویم",
                      "authorEn": "Levy Rozman",
                      "authorFa": "لوی روزمن",
                      "chapters": [
                        {
                          "chapterId": "CH01",
                          "chapterNumber": 1,
                          "titleEn": "How to (Literally) Win at Chess",
                          "titleFa": "چگونه واقعاً در شطرنج برنده شویم",
                          "manifestPath": "books/how_to_win_at_chess/CH01/chapter.json",
                          "enabled": true
                        },
                        {
                          "chapterId": "CH02",
                          "chapterNumber": 2,
                          "titleEn": "Basic Endgame Tactics (Test Fixture)",
                          "titleFa": "تاکتیک‌های پایه آخر بازی (آزمایشی)",
                          "manifestPath": "books/how_to_win_at_chess/CH02/chapter.json",
                          "enabled": true
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/chapter.json" to """
                {
                  "schemaVersion": 1,
                  "bookId": "how_to_win_at_chess",
                  "chapterId": "CH02",
                  "chapterNumber": 2,
                  "titleEn": "Basic Endgame Tactics (Test Fixture)",
                  "titleFa": "تاکتیک‌های پایه آخر بازی (آزمایشی)",
                  "diagramsFile": "diagrams.json",
                  "pgnSequencesFile": "pgn_sequences.json",
                  "exercisesFile": "exercises.json",
                  "narrationManifestFile": "narration/manifest.json",
                  "sections": [
                    {
                      "sectionId": "SEC_CH02_01_OPENING",
                      "order": 1,
                      "sectionKind": "OPENING",
                      "titleEn": "Back Rank Mate",
                      "titleFa": "مات در عرض آخر",
                      "pdfPages": [51, 52],
                      "file": "sections/01_opening.json"
                    },
                    {
                      "sectionId": "SEC_CH02_02_SUMMARY",
                      "order": 2,
                      "sectionKind": "SUMMARY",
                      "titleEn": "Chapter 2 Summary",
                      "titleFa": "خلاصه فصل دوم",
                      "pdfPages": [53],
                      "file": "sections/02_summary.json"
                    }
                  ]
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/diagrams.json" to """
                {
                  "schema_version": "1.0",
                  "chapter_id": "CH02",
                  "diagrams": [
                    {
                      "diagram_id": "BD-201",
                      "pdf_page": 51,
                      "section": "Back Rank Mate",
                      "book_context": "Static & PGN example in CH02",
                      "diagram_kind": "tactical_example",
                      "recommended_interaction": "pgn_replay",
                      "fen": "4R1k1/5ppp/8/8/8/8/5PPP/6K1 b - - 1 1",
                      "side_to_move": "black",
                      "move_history": "1. Re8#",
                      "pgn_ref": "CH02_PGN_LINE",
                      "highlight_squares": ["e1", "e8"],
                      "related_diagram_ids": [],
                      "visual_confidence": "high",
                      "audit_status": "VALID",
                      "audit_warning_fa": null,
                      "implementation_notes": "Test fixture diagram 1"
                    },
                    {
                      "diagram_id": "BD-202",
                      "pdf_page": 52,
                      "section": "Back Rank Mate",
                      "book_context": "Interactive Mate in One exercise in CH02",
                      "diagram_kind": "exercise_position",
                      "recommended_interaction": "interactive_exercise",
                      "fen": "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
                      "side_to_move": "white",
                      "move_history": "Find Rd8#",
                      "pgn_ref": null,
                      "solution_pgn_refs": ["CH02_MATE_RD8"],
                      "highlight_squares": [],
                      "related_diagram_ids": [],
                      "visual_confidence": "high",
                      "audit_status": "VALID",
                      "audit_warning_fa": null,
                      "implementation_notes": "Test fixture exercise diagram"
                    }
                  ]
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/pgn_sequences.json" to """
                {
                  "CH02_PGN_LINE": {
                    "id": "CH02_PGN_LINE",
                    "description": "Back rank checkmate line",
                    "moves": ["Re8#"],
                    "fens": [
                      "6k1/5ppp/8/8/8/8/5PPP/4R1K1 w - - 0 1",
                      "4R1k1/5ppp/8/8/8/8/5PPP/6K1 b - - 1 1"
                    ]
                  },
                  "CH02_MATE_RD8": {
                    "id": "CH02_MATE_RD8",
                    "description": "CH02 exercise solution Rd8#",
                    "moves": ["Rd8#"],
                    "fens": [
                      "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
                      "3R2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 1 1"
                    ]
                  }
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/exercises.json" to """
                {
                  "schemaVersion": 1,
                  "chapterId": "CH02",
                  "exercises": [
                    {
                      "exerciseId": "EX_CH02_BD202",
                      "diagramId": "BD-202",
                      "exerciseType": "MATE_IN_ONE",
                      "initialFen": "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
                      "sideToMove": "white",
                      "promptFa": "نوبت سفید است: مات در یک حرکت را بیابید.",
                      "acceptedSolutionIds": ["CH02_MATE_RD8"],
                      "successFeedbackFa": "آفرین! رخ سفید در d8 شاه سیاه را مات کرد.",
                      "wrongMoveFeedbackFa": "این حرکت قانونی است اما مات در یک حرکت نیست.",
                      "illegalMoveFeedbackFa": "حرکت غیرمجاز است.",
                      "hintFa": "رخ سفید را به عرض هشتم ببرید.",
                      "allowLegalMoveHints": true
                    }
                  ]
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/sections/01_opening.json" to """
                {
                  "schemaVersion": 1,
                  "sectionId": "SEC_CH02_01_OPENING",
                  "placeholderNoticeFa": "محتوای آزمایشی فصل دوم.",
                  "translationStatus": "TRANSLATED",
                  "orderedContentBlocks": [
                    {
                      "type": "paragraph",
                      "blockId": "SEC_CH02_01_P001",
                      "textFa": "در این بخش آزمایشی با الگوی مات در عرض آخر آشنا می‌شوید.",
                      "showPlaceholderWhenEmpty": true
                    },
                    {
                      "type": "diagram",
                      "blockId": "SEC_CH02_01_D201",
                      "diagramId": "BD-201"
                    },
                    {
                      "type": "exercise",
                      "blockId": "SEC_CH02_01_E202",
                      "diagramId": "BD-202",
                      "exerciseId": "EX_CH02_BD202"
                    }
                  ]
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/sections/02_summary.json" to """
                {
                  "schemaVersion": 1,
                  "sectionId": "SEC_CH02_02_SUMMARY",
                  "placeholderNoticeFa": "خلاصه فصل دوم.",
                  "translationStatus": "TRANSLATED",
                  "orderedContentBlocks": [
                    {
                      "type": "heading",
                      "blockId": "SEC_CH02_02_H001",
                      "textFa": "جمع‌بندی فصل دوم",
                      "level": 2,
                      "showPlaceholderWhenEmpty": false
                    },
                    {
                      "type": "paragraph",
                      "blockId": "SEC_CH02_02_P001",
                      "textFa": "همواره پیش از حمله، راه‌های فرار شاه خود در عرض اول را بررسی کنید.",
                      "showPlaceholderWhenEmpty": true
                    }
                  ]
                }
            """.trimIndent(),
            "books/how_to_win_at_chess/CH02/narration/manifest.json" to """
                {
                  "schemaVersion": 1,
                  "chapters": [
                    {
                      "chapterId": "CH02",
                      "sections": [
                        {
                          "sectionId": "SEC_CH02_01_OPENING",
                          "blocks": [
                            {
                              "blockId": "SEC_CH02_01_P001",
                              "audioAssetPath": "ch02/sec01_p001.ogg",
                              "status": "UNAVAILABLE",
                              "durationMs": 5000
                            }
                          ]
                        },
                        {
                          "sectionId": "SEC_CH02_02_SUMMARY",
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
            libraryAssetPath = "books/test_library_two_chapters.json",
            assetReaderOverride = { path ->
                syntheticAssets[path]
                    ?: context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        )
    }

    @Test
    fun `multi-chapter 1 to 7 - CH02 fixture navigation pagination PGN replay exercise and narration stop`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val multiRepo = createMultiChapterRepository(app)
        assertTrue(multiRepo.validateChapterPack("CH01").isValid)
        val ch02Val = multiRepo.validateChapterPack("CH02")
        assertTrue("CH02 validation must pass: ${ch02Val.errorMessages}", ch02Val.isValid)

        val viewModel = ChapterViewModel(app, customRepository = multiRepo)

        // 1: With two active chapters, openBookFromIntroduction opens CHAPTERS_LIST
        assertTrue(viewModel.uiState.value.hasMultipleChapters)
        assertEquals(2, viewModel.uiState.value.availableChapters.size)
        viewModel.openBookFromIntroduction()
        assertEquals(AppTab.CHAPTERS_LIST, viewModel.uiState.value.currentTab)

        // 2: Selecting CH01 opens 6 sections of Chapter 1
        viewModel.selectChapter("CH01")
        assertEquals(AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)
        assertEquals("CH01", viewModel.uiState.value.activeChapterId)
        assertEquals(6, viewModel.uiState.value.sections.size)

        // 3: Selecting CH02 opens 2 sections of Chapter 2
        viewModel.selectChapter("CH02")
        assertEquals(AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)
        assertEquals("CH02", viewModel.uiState.value.activeChapterId)
        assertEquals(2, viewModel.uiState.value.sections.size)
        assertEquals("SEC_CH02_01_OPENING", viewModel.uiState.value.sections[0].sectionId)
        assertEquals("SEC_CH02_02_SUMMARY", viewModel.uiState.value.sections[1].sectionId)

        // 4: ReaderPaginator works on CH02 without any code changes (including SUMMARY sectionKind)
        val ch02Sec1Pages = ReaderPaginator.paginateSection(
            section = viewModel.uiState.value.sections[0],
            diagrams = viewModel.uiState.value.diagrams,
            fontScale = FontSizeScale.NORMAL
        )
        assertEquals(2, ch02Sec1Pages.size) // 1 diagram page + 1 exercise page
        assertEquals(ReaderPageType.MOVE_REPLAY, ch02Sec1Pages[0].pageType)
        assertEquals(ReaderPageType.INTERACTIVE_EXERCISE, ch02Sec1Pages[1].pageType)

        val ch02Sec2Pages = ReaderPaginator.paginateSection(
            section = viewModel.uiState.value.sections[1],
            diagrams = viewModel.uiState.value.diagrams,
            fontScale = FontSizeScale.NORMAL
        )
        assertEquals(1, ch02Sec2Pages.size)
        assertEquals(ReaderPageType.SUMMARY, ch02Sec2Pages[0].pageType)

        // 5: PGN replay in CH02 works
        val initialPgnState = viewModel.uiState.value.pgnStates["CH02_PGN_LINE"]
        assertNotNull(initialPgnState)
        assertEquals(1, initialPgnState!!.currentStep)
        viewModel.resetPgn("CH02_PGN_LINE")
        assertEquals(0, viewModel.uiState.value.pgnStates["CH02_PGN_LINE"]!!.currentStep)
        viewModel.stepPgnNext("CH02_PGN_LINE")
        val steppedPgnState = viewModel.uiState.value.pgnStates["CH02_PGN_LINE"]!!
        assertEquals(1, steppedPgnState.currentStep)
        assertEquals("4R1k1/5ppp/8/8/8/8/5PPP/6K1 b - - 1 1", steppedPgnState.currentFen)

        // 6: Exercise EX_CH02_BD202 (White Rd1 -> d8#) works via generalized ExerciseEngine
        val exStateBefore = viewModel.uiState.value.exerciseStates["EX_CH02_BD202"]
        assertNotNull(exStateBefore)
        assertFalse(exStateBefore!!.isSolved)
        // Click White Rook on d1 (file=3, rank=0) -> d8 (file=3, rank=7)
        viewModel.onExerciseSquareClicked("EX_CH02_BD202", 3, 0)
        assertEquals(
            Bd032ExercisePhase.PIECE_SELECTED,
            viewModel.uiState.value.exerciseStates["EX_CH02_BD202"]!!.phase
        )
        viewModel.onExerciseSquareClicked("EX_CH02_BD202", 3, 7)
        val exTransition = viewModel.uiState.value.exerciseStates["EX_CH02_BD202"]!!.activeTransition
        if (exTransition != null) {
            viewModel.onExerciseTransitionFinished("EX_CH02_BD202", exTransition.transitionId)
        }
        val exStateAfter = viewModel.uiState.value.exerciseStates["EX_CH02_BD202"]!!
        assertTrue(exStateAfter.isSolved)
        assertEquals("Rd8#", exStateAfter.solutionPlayed)

        // 7: Switching chapters stops narration cleanly
        viewModel.selectChapter("CH01")
        assertFalse(viewModel.uiState.value.narrationState.isPlaying)
        assertEquals("CH01", viewModel.uiState.value.activeChapterId)
    }

    @Test
    fun `multi-chapter UI - ChaptersListScreen renders active chapters and navigates to selected chapter`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val multiRepo = createMultiChapterRepository(app)
        val viewModel = ChapterViewModel(app, customRepository = multiRepo)
        viewModel.openBookFromIntroduction()

        composeTestRule.setContent {
            MyApplicationTheme {
                val state = viewModel.uiState.value
                if (state.currentTab == AppTab.CHAPTERS_LIST) {
                    ChaptersListScreen(
                        state = state,
                        onSelectChapter = { viewModel.selectChapter(it) },
                        onBackToIntroduction = { viewModel.openIntroduction() }
                    )
                } else if (state.currentTab == AppTab.SECTIONS_LIST) {
                    SectionsListScreen(viewModel = viewModel, state = state)
                }
            }
        }

        composeTestRule.onNodeWithTag("chapters_list_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chapter_card_CH01").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chapter_card_CH02").assertIsDisplayed()

        // Click CH02 card
        composeTestRule.onNodeWithTag("chapter_card_CH02").performClick()
        composeTestRule.waitForIdle()

        assertEquals("CH02", viewModel.uiState.value.activeChapterId)
        assertEquals(AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)
    }

    // =========================================================================
    // 9.3 — Generalized Exercise Engine & Content Pack Validator Tests
    // =========================================================================

    @Test
    fun `exercise engine 1 to 4 - loads from exercises json binds by exerciseId runs 2 exercises independently and rejects invalid refs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val multiRepo = createMultiChapterRepository(context)

        // 1 & 2: Exercise loads from exercises.json and links to ExerciseBlock by exerciseId
        val ch01Exercises = multiRepo.getExercises("CH01")
        assertEquals(1, ch01Exercises.size)
        assertEquals("EX_CH01_BD032", ch01Exercises.first().exerciseId)
        assertEquals("BD-032", ch01Exercises.first().diagramId)

        val ch01Sec4Blocks = multiRepo.getSections("CH01")[3].translationSlot.orderedContentBlocks
        val exerciseBlock = ch01Sec4Blocks.filterIsInstance<SectionContentBlock.ExerciseBlock>().first()
        assertEquals("EX_CH01_BD032", exerciseBlock.exerciseId)
        assertEquals("BD-032", exerciseBlock.diagramId)

        // 3: Two different MATE_IN_ONE exercises run concurrently with zero state interference
        val handler = ExerciseHandlerRegistry.getHandler("MATE_IN_ONE")!!
        val def1 = ch01Exercises.first()
        val diag1 = multiRepo.getDiagrams("CH01").first { it.diagramId == "BD-032" }
        val pgns1 = multiRepo.getAllPgnSequences("CH01")
        val adapter1 = handler.createRulesAdapter(def1, diag1)

        val def2 = multiRepo.getExercises("CH02").first()
        val diag2 = multiRepo.getDiagrams("CH02").first { it.diagramId == "BD-202" }
        val pgns2 = multiRepo.getAllPgnSequences("CH02")
        val adapter2 = handler.createRulesAdapter(def2, diag2)

        var seq = 0L
        var state1 = handler.createInitialState(def1, diag1, false)
        var state2 = handler.createInitialState(def2, diag2, false)

        // Select Queen on e5 in Exercise 1
        state1 = handler.onSquareClicked(
            definition = def1,
            diagram = diag1,
            pgnSequences = pgns1,
            currentState = state1,
            adapter = adapter1,
            file = 4,
            rank = 4,
            showMoveTrail = true,
            playbackSpeed = EducationalPlaybackSpeed.NORMAL,
            nextTransitionNumber = { ++seq }
        ).updatedState
        assertEquals(Bd032ExercisePhase.PIECE_SELECTED, state1.phase)
        assertEquals("e5", state1.selectedSquare)
        // Exercise 2 must remain completely IDLE
        assertEquals(Bd032ExercisePhase.IDLE, state2.phase)

        // Solve Exercise 2 (Rd1 -> d8#) while Exercise 1 is still PIECE_SELECTED
        state2 = handler.onSquareClicked(
            definition = def2,
            diagram = diag2,
            pgnSequences = pgns2,
            currentState = state2,
            adapter = adapter2,
            file = 3,
            rank = 0,
            showMoveTrail = true,
            playbackSpeed = EducationalPlaybackSpeed.NORMAL,
            nextTransitionNumber = { ++seq }
        ).updatedState
        val step2 = handler.onSquareClicked(
            definition = def2,
            diagram = diag2,
            pgnSequences = pgns2,
            currentState = state2,
            adapter = adapter2,
            file = 3,
            rank = 7,
            showMoveTrail = true,
            playbackSpeed = EducationalPlaybackSpeed.NORMAL,
            nextTransitionNumber = { ++seq }
        )
        state2 = step2.updatedState
        if (state2.activeTransition != null) {
            state2 = handler.onTransitionFinished(def2, state2, state2.activeTransition!!.transitionId).updatedState
        }
        assertTrue("Exercise 2 must be solved", state2.isSolved)
        assertEquals("Rd8#", state2.solutionPlayed)
        assertFalse("Exercise 1 must still be unsolved", state1.isSolved)
        assertEquals(Bd032ExercisePhase.PIECE_SELECTED, state1.phase)

        // 4: Validator rejects invalid exerciseId, mismatched diagramId, or missing solution PGN ref
        val invalidSection = SectionItem(
            sectionId = "SEC_TEST_01",
            index = 0,
            titleEn = "Test",
            titleFa = "تست",
            pdfPages = listOf(1),
            diagramIds = listOf("BD-032"),
            translationSlot = TranslationSlot(
                sectionId = "SEC_TEST_01",
                orderedContentBlocks = listOf(
                    SectionContentBlock.ExerciseBlock(
                        blockId = "BLK_EX_INVALID",
                        diagramId = "BD-032",
                        exerciseId = "NON_EXISTENT_EXERCISE_ID"
                    )
                )
            )
        )
        val invalidResult = ContentBlockValidator.validateSectionsAndBlocks(
            sections = listOf(invalidSection),
            diagrams = listOf(diag1),
            expectedSectionSlotIds = listOf("SEC_TEST_01"),
            exercises = listOf(def1)
        )
        assertFalse("Validator must reject unknown exerciseId in ExerciseBlock", invalidResult.isValid)
        assertTrue(invalidResult.invalidExerciseIds.contains("BLK_EX_INVALID"))

        val badSolutionDef = def1.copy(acceptedSolutionPgnRefs = emptyList())
        val badSolutionResult = ContentBlockValidator.validateSectionsAndBlocks(
            sections = multiRepo.getSections("CH01"),
            diagrams = multiRepo.getDiagrams("CH01"),
            expectedSectionSlotIds = multiRepo.getSections("CH01").map { it.sectionId },
            exercises = listOf(badSolutionDef)
        )
        assertFalse("Validator must reject exercise with empty solution PGN refs", badSolutionResult.isValid)
        assertTrue(badSolutionResult.invalidExerciseIds.contains("EX_CH01_BD032"))
    }
}
