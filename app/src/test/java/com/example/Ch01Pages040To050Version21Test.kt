package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChapterRepository
import com.example.data.FontSizeScale
import com.example.data.UserPreferencesManager
import com.example.model.AuditStatus
import com.example.model.ChapterImageVisualRole
import com.example.model.ExercisePhase
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.screens.ReaderScreen
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
class Ch01Pages040To050Version21Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun collectAllSemanticsStrings(node: SemanticsNode): List<String> {
        val list = mutableListOf<String>()
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.forEach {
            list.add(it.text)
        }
        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.forEach {
            list.add(it)
        }
        node.children.forEach { child ->
            list.addAll(collectAllSemanticsStrings(child))
        }
        return list
    }

    // =========================================================================
    // Test 1: صفحات ۴۰ تا ۵۰ همگی در مدل داده وجود دارند و از کتابخانه/TOC/Search یا انتخاب صفحه باز می‌شوند
    // =========================================================================
    @Test
    fun `v21 test 1 - pages 40 to 50 exist in source_page_index and are navigable from Library TOC and navigateToSourcePdfPage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val sourcePageIndex = repo.getChapterSourcePageIndex("CH01")
        assertNotNull("CH01/source_page_index.json must load", sourcePageIndex)
        assertEquals(40, sourcePageIndex!!.sourcePdfPageStart)
        assertEquals(50, sourcePageIndex.sourcePdfPageEnd)
        assertEquals((40..50).toList(), sourcePageIndex.pages.map { it.sourcePdfPage })

        val viewModel = ChapterViewModel(app)
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                when (state.currentTab) {
                    AppTab.READER -> ReaderScreen(viewModel = viewModel, state = state)
                    else -> SectionsListScreen(viewModel = viewModel, state = state)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify all 11 pages 40..50 open via navigateToSourcePdfPage and show exact PDF page indicator
        for (pdfPage in 40..50) {
            viewModel.navigateToSourcePdfPage(pdfPage)
            composeTestRule.waitForIdle()

            val state = viewModel.uiState.value
            assertEquals(AppTab.READER, state.currentTab)
            assertFalse(state.isReadingFrontMatter)
            assertEquals(
                "Active reader page must have sourcePdfPage == $pdfPage",
                pdfPage,
                state.currentPages[state.currentPageIndex].sourcePdfPage
            )
            composeTestRule.onNodeWithTag("reader_source_pdf_page_badge").assertIsDisplayed()
            val badgeTexts = collectAllSemanticsStrings(
                composeTestRule.onNodeWithTag("reader_source_pdf_page_badge", useUnmergedTree = true).fetchSemanticsNode()
            )
            val persianNum = pdfPage.toString().map { ch ->
                if (ch in '0'..'9') ('۰'.code + (ch.code - '0'.code)).toChar() else ch
            }.joinToString("")
            assertTrue(
                "Reader badge must display 'صفحه PDF $persianNum', got $badgeTexts",
                badgeTexts.any { it.contains("صفحه PDF $persianNum") || it.contains("صفحه PDF $pdfPage") }
            )
        }

        // Also verify clicking a page chip from SectionsListScreen opens that exact PDF page
        viewModel.openSectionsList()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chapter_pdf_page_chip_45").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.READER, viewModel.uiState.value.currentTab)
        assertEquals(
            45,
            viewModel.uiState.value.currentPages[viewModel.uiState.value.currentPageIndex].sourcePdfPage
        )
    }

    // =========================================================================
    // Test 2: صفحه ۴۰ دارای بلوک‌های CH01_P040_B001 تا CH01_P040_B007 و نمودار تعاملی BD-025 است
    // =========================================================================
    @Test
    fun `v21 test 2 - page 40 contains CH01_P040_B001 to CH01_P040_B007 and interactive diagram BD-025`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val page40Entry = repo.getChapterSourcePageIndex("CH01")!!.entryForPage(40)!!
        val expectedIds = listOf(
            "CH01_P040_B001",
            "CH01_P040_B002",
            "CH01_P040_B003",
            "CH01_P040_D025",
            "CH01_P040_B004",
            "CH01_P040_B005",
            "CH01_P040_B006",
            "CH01_P040_B007"
        )
        assertEquals(expectedIds, page40Entry.orderedBlockIds)

        val blocksOn40 = repo.getBlocksForSourcePdfPage(40, "CH01")
        assertEquals(expectedIds, blocksOn40.map { it.blockId })

        val diagramBlock = blocksOn40.filterIsInstance<SectionContentBlock.DiagramBlock>().single()
        assertEquals("BD-025", diagramBlock.diagramId)

        // Verify via ViewModel & ReaderPaginator
        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(40)
        val pages = viewModel.uiState.value.currentPages
        assertTrue(pages.all { it.sourcePdfPage == 40 })
        val allBaseIds = pages.flatMap { it.orderedBlocks }.map { it.blockId.substringBefore("__part_") }.distinct()
        assertEquals(expectedIds, allBaseIds)
    }

    // =========================================================================
    // Test 3: صفحه ۴۲ متن BD-027 را نشان می‌دهد و هشدار ممیزی BD-027 را حفظ می‌کند
    // =========================================================================
    @Test
    fun `v21 test 3 - page 42 displays BD-027 text and preserves BLOCKED_NEEDS_HUMAN_REVIEW audit warning`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val bd027 = viewModel.uiState.value.diagrams.first { it.diagramId == "BD-027" }
        assertEquals(AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW, bd027.auditStatus)
        assertNotNull(bd027.auditWarning)
        assertTrue(bd027.auditWarning!!.isNotBlank())

        viewModel.navigateToSourcePdfPage(42)
        val pages = viewModel.uiState.value.currentPages
        assertTrue(pages.all { it.sourcePdfPage == 42 })

        // Verify CH01_P042_B001..B006 and BD-027 are on page 42
        val baseIds = pages.flatMap { it.orderedBlocks }.map { it.blockId.substringBefore("__part_") }.distinct()
        assertTrue(baseIds.contains("CH01_P042_D027"))
        assertTrue(baseIds.contains("CH01_P042_B002"))

        // Navigate to the slice containing BD-027 and verify UI renders the audit warning
        val bd027SliceIdx = pages.indexOfFirst { page ->
            page.orderedBlocks.any { it.blockId == "CH01_P042_D027" }
        }
        assertTrue(bd027SliceIdx >= 0)
        viewModel.goToPage(bd027SliceIdx)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("diagram_card_BD-027").assertIsDisplayed()
        val semanticsStrings = collectAllSemanticsStrings(
            composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        )
        assertTrue(
            "BD-027 Persian audit warning must remain displayed on page 42",
            semanticsStrings.any { it.contains("g8") || it.contains("بازبینی") }
        )
    }

    // =========================================================================
    // Test 4: صفحه ۴۵ بخش پایانی کیش‌ومات (BD-030 و پاراگراف‌های آن) و شروع تسلیم را نمایش می‌دهد
    // =========================================================================
    @Test
    fun `v21 test 4 - shared page 45 displays ending of Checkmate BD-030 and start of Resignation without page 44 or 46 blocks`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val expectedPage45Ids = listOf(
            "CH01_P045_D030",
            "CH01_P045_B001",
            "CH01_P045_B002",
            "CH01_P045_B003",
            "CH01_P045_B004",
            "CH01_P045_B005",
            "CH01_P045_B006"
        )
        val page45Entry = repo.getChapterSourcePageIndex("CH01")!!.entryForPage(45)!!
        assertEquals(expectedPage45Ids, page45Entry.orderedBlockIds)
        assertEquals(listOf("SEC_CH01_02_CHECKMATE", "SEC_CH01_03_RESIGNATION"), page45Entry.sectionIds)

        val blocksOn45 = repo.getBlocksForSourcePdfPage(45, "CH01")
        assertEquals(expectedPage45Ids, blocksOn45.map { it.blockId })

        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(45)
        val pages = viewModel.uiState.value.currentPages
        assertTrue(pages.isNotEmpty())
        assertTrue("Every slice of page 45 must have sourcePdfPage == 45", pages.all { it.sourcePdfPage == 45 })

        val renderedBaseIds = pages.flatMap { it.orderedBlocks }.map { it.blockId.substringBefore("__part_") }.distinct()
        assertEquals(expectedPage45Ids, renderedBaseIds)
        assertFalse("Page 45 must never contain page 44 blocks", renderedBaseIds.any { it.contains("P044") })
        assertFalse("Page 45 must never contain page 46 blocks", renderedBaseIds.any { it.contains("P046") })
    }

    // =========================================================================
    // Test 5: صفحه ۴۶ دارای نکته جالب، آیکن منبع PDFOBJ_P046_X1111_01، نمودار BD-031 و عنوان پایانی اتمام زمان است
    // =========================================================================
    @Test
    fun `v21 test 5 - shared page 46 contains Fun Fact icon PDFOBJ_P046_X1111_01 diagram BD-031 and trailing heading Timing Out`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val expectedPage46Ids = listOf(
            "CH01_P046_B001",
            "CH01_P046_B002",
            "CH01_P046_D031",
            "CH01_P046_B003",
            "CH01_P046_B004",
            "CH01_P046_B005"
        )
        val blocksOn46 = repo.getBlocksForSourcePdfPage(46, "CH01")
        assertEquals(expectedPage46Ids, blocksOn46.map { it.blockId })

        val funFactBlock = blocksOn46.first { it.blockId == "CH01_P046_B001" } as SectionContentBlock.HeadingBlock
        assertTrue(funFactBlock.textFa!!.contains("نکته جالب"))
        assertTrue(
            "CH01_P046_B001 must have inline source asset PDFOBJ_P046_X1111_01",
            funFactBlock.inlineSourceAssets.any {
                it.assetId == "PDFOBJ_P046_X1111_01" && it.visualRole == ChapterImageVisualRole.FUN_FACT_ICON
            }
        )

        val trailingHeading = blocksOn46.last() as SectionContentBlock.HeadingBlock
        assertEquals("CH01_P046_B005", trailingHeading.blockId)
        assertEquals("اتمام زمان", trailingHeading.textFa)
        assertEquals(46, trailingHeading.sourcePdfPage)

        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(46)
        val pages = viewModel.uiState.value.currentPages
        assertTrue(pages.all { it.sourcePdfPage == 46 })

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        // Slice 0 contains CH01_P046_B001 and its inline asset PDFOBJ_P046_X1111_01
        composeTestRule.onNodeWithTag("inline_source_asset_PDFOBJ_P046_X1111_01").assertIsDisplayed()
    }

    // =========================================================================
    // Test 6: صفحه ۴۷ تمرین تعاملی BD-032 را با دو پاسخ Qh8# و Qh2# نشان می‌دهد
    // =========================================================================
    @Test
    fun `v21 test 6 - page 47 shows interactive exercise BD-032 with both Qh8# and Qh2# accepted`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        prefs.setExerciseSolved("EX_CH01_BD032", false)

        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(47)
        val pages = viewModel.uiState.value.currentPages
        assertTrue(pages.all { it.sourcePdfPage == 47 })

        val exSliceIndex = pages.indexOfFirst { p ->
            p.orderedBlocks.any { it.blockId == "CH01_P047_EX_BD032" }
        }
        assertTrue("CH01_P047_EX_BD032 must exist on page 47", exSliceIndex >= 0)
        val exBlock = pages[exSliceIndex].orderedBlocks.filterIsInstance<SectionContentBlock.ExerciseBlock>().single()
        assertEquals("BD-032", exBlock.diagramId)
        assertEquals("EX_CH01_BD032", exBlock.exerciseId)

        // Test Solution 1: Qh8# (e5 -> h8)
        viewModel.resetBd032()
        viewModel.onBd032SquareClicked(4, 4)
        viewModel.onBd032SquareClicked(7, 7)
        viewModel.uiState.value.bd032State.activeTransition?.let {
            viewModel.onBd032TransitionFinished(it.transitionId)
        }
        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        assertEquals("Qh8#", viewModel.uiState.value.bd032State.solutionPlayed)

        // Test Solution 2: Qh2# (e5 -> h2)
        viewModel.resetBd032()
        viewModel.onBd032SquareClicked(4, 4)
        viewModel.onBd032SquareClicked(7, 1)
        viewModel.uiState.value.bd032State.activeTransition?.let {
            viewModel.onBd032TransitionFinished(it.transitionId)
        }
        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        assertEquals("Qh2#", viewModel.uiState.value.bd032State.solutionPlayed)
    }

    // =========================================================================
    // Test 7: راهنمای حرکات قانونی در BD-032 به‌صورت پیش‌فرض مخفی است و فقط با دکمه راهنما فعال می‌شود
    // =========================================================================
    @Test
    fun `v21 test 7 - BD-032 legal move hints are hidden by default and only appear when hint button is toggled`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.resetBd032()

        // Select Queen on e5 (4, 4)
        viewModel.onBd032SquareClicked(4, 4)
        assertEquals(ExercisePhase.PIECE_SELECTED, viewModel.uiState.value.bd032State.phase)
        assertFalse(
            "showLegalMovesHint must be false by default when selecting a piece in BD-032",
            viewModel.uiState.value.bd032State.showLegalMovesHint
        )
        assertTrue(
            "visibleTargetSquares must be empty by default",
            viewModel.uiState.value.bd032State.visibleTargetSquares.isEmpty()
        )

        // Toggle hint button -> showLegalMovesHint becomes true
        viewModel.toggleBd032LegalMovesHint()
        assertTrue(
            "showLegalMovesHint must become true after toggling hint button",
            viewModel.uiState.value.bd032State.showLegalMovesHint
        )
        assertTrue(
            "visibleTargetSquares must become non-empty when hint is enabled",
            viewModel.uiState.value.bd032State.visibleTargetSquares.isNotEmpty()
        )
    }

    // =========================================================================
    // Test 8: صفحه ۴۸ شامل BD-033، توضیح ناکافی بودن مهره برای مات و شروع بخش ترک بازی است
    // =========================================================================
    @Test
    fun `v21 test 8 - shared page 48 includes BD-033 insufficient material text and start of Abandonment`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val expectedPage48Ids = listOf(
            "CH01_P048_D033",
            "CH01_P048_B001",
            "CH01_P048_B002",
            "CH01_P048_B003"
        )
        val blocksOn48 = repo.getBlocksForSourcePdfPage(48, "CH01")
        assertEquals(expectedPage48Ids, blocksOn48.map { it.blockId })

        val insufficientMaterialPara = blocksOn48[1] as SectionContentBlock.ParagraphBlock
        assertTrue(insufficientMaterialPara.textFa!!.contains("ناکافی بودن مهره برای مات"))

        val abandonmentHeading = blocksOn48[2] as SectionContentBlock.HeadingBlock
        assertEquals("ترک بازی", abandonmentHeading.textFa)

        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(48)
        val pages = viewModel.uiState.value.currentPages
        assertTrue(pages.all { it.sourcePdfPage == 48 })
        val renderedBaseIds = pages.flatMap { it.orderedBlocks }.map { it.blockId.substringBefore("__part_") }.distinct()
        assertEquals(expectedPage48Ids, renderedBaseIds)
    }

    // =========================================================================
    // Test 9: صفحه ۴۹ شامل BD-034 و داستان اشتاینیتز و فُن باردلبن است
    // =========================================================================
    @Test
    fun `v21 test 9 - page 49 includes BD-034 and the Steinitz vs von Bardeleben story`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val expectedPage49Ids = listOf(
            "CH01_P049_D034",
            "CH01_P049_B001",
            "CH01_P049_B002"
        )
        val blocksOn49 = repo.getBlocksForSourcePdfPage(49, "CH01")
        assertEquals(expectedPage49Ids, blocksOn49.map { it.blockId })

        val storyText = blocksOn49.filterIsInstance<SectionContentBlock.ParagraphBlock>()
            .joinToString(" ") { it.textFa.orEmpty() }
        assertTrue(storyText.contains("ویلهلم اشتاینیتز"))
        assertTrue(storyText.contains("کورت فون باردلبن"))

        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(49)
        assertTrue(viewModel.uiState.value.currentPages.all { it.sourcePdfPage == 49 })
    }

    // =========================================================================
    // Test 10: صفحه ۵۰ شامل ۴ مورد خلاصه، لینک درس تکمیلی و پاورقی پاسخ تمرین صفحه ۴۷ است
    // =========================================================================
    @Test
    fun `v21 test 10 - page 50 renders 4 summary items external link and footnote referencing page 47`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)

        val blocksOn50 = repo.getBlocksForSourcePdfPage(50, "CH01")
        val listItemBlocks = blocksOn50.filter { it.blockId.startsWith("CH01_P050_L00") }
        assertEquals(4, listItemBlocks.size)
        assertEquals(
            listOf("CH01_P050_L001", "CH01_P050_L002", "CH01_P050_L003", "CH01_P050_L004"),
            listItemBlocks.map { it.blockId }
        )

        val externalLink = blocksOn50.filterIsInstance<SectionContentBlock.ExternalReferenceBlock>().single()
        assertEquals("CH01_P050_LINK001", externalLink.blockId)
        assertEquals(
            "https://chessly.com/books/f86193bb-7b63-412c-87fe-2859fc8c3948/chapters/6e46cbf4-d249-4ff1-9100-d9f081183f51",
            externalLink.url
        )

        val footnote = blocksOn50.filterIsInstance<SectionContentBlock.FootnoteBlock>().single()
        assertEquals("CH01_P050_FN001", footnote.blockId)
        assertEquals(listOf(47), footnote.sourcePageReferences)
        assertTrue(footnote.textFa!!.contains("h2"))
        assertTrue(footnote.textFa!!.contains("h8"))

        // Verify clicking footnote page 47 reference navigates directly to PDF page 47
        val viewModel = ChapterViewModel(app)
        viewModel.navigateToSourcePdfPage(50)
        val pages = viewModel.uiState.value.currentPages
        val fnSliceIdx = pages.indexOfFirst { p -> p.orderedBlocks.any { it.blockId == "CH01_P050_FN001" } }
        assertTrue("Footnote CH01_P050_FN001 must be paginated on page 50", fnSliceIdx >= 0)
        viewModel.goToPage(fnSliceIdx)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("footnote_page_ref_CH01_P050_FN001_47").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            47,
            viewModel.uiState.value.currentPages[viewModel.uiState.value.currentPageIndex].sourcePdfPage
        )
    }

    // =========================================================================
    // Test 11: تمام ۲۱ فایل تصویر صفحات ۴۰ تا ۴۹ در Assets موجود و با CSV منبع هم‌خوان‌اند
    // =========================================================================
    @Test
    fun `v21 test 11 - all 21 image files for pages 40 to 49 exist in assets and match source_image_placements_pages_040_050 csv`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)

        val placements = repo.getChapterImagePlacements("CH01")
        assertEquals("source_page_index.json / CSV must declare 21 image placements", 21, placements.size)

        // Read CSV from assets and verify 21 rows match
        val csvPath = "books/how_to_win_at_chess/source_metadata/source_image_placements_pages_040_050.csv"
        val csvLines = context.assets.open(csvPath).bufferedReader().readLines().filter { it.isNotBlank() }
        assertEquals("CSV must have 1 header + 21 data rows", 22, csvLines.size)

        // Column index 1 in CSV is asset_id
        val csvAssetIds = csvLines.drop(1).map { it.split(",")[1].trim() }
        assertEquals(csvAssetIds.toSet(), placements.map { it.assetId }.toSet())

        // Verify every single asset file opens and has non-zero bytes
        for (placement in placements) {
            val bytes = context.assets.open(placement.resolvedAssetPath).use { it.readBytes() }
            assertTrue("Asset ${placement.resolvedAssetPath} must be non-empty", bytes.isNotEmpty())
        }
    }

    // =========================================================================
    // Test 12: هیچ صفحه اپی دو sourcePdfPage متفاوت را با هم ترکیب نمی‌کند
    // =========================================================================
    @Test
    fun `v21 test 12 - no ReaderPage across sections or source PDF pages ever combines blocks from two different sourcePdfPage values`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val diagrams = repo.getDiagrams("CH01")
        val sections = repo.getSections("CH01")
        val sourcePageIndex = repo.getChapterSourcePageIndex("CH01")!!

        // Check section-based pagination across all font scales
        for (scale in FontSizeScale.values()) {
            for (section in sections) {
                val pages = ReaderPaginator.paginateSection(
                    section = section,
                    diagrams = diagrams,
                    fontScale = scale
                )
                assertTrue("Section ${section.sectionId} must produce at least 1 page", pages.isNotEmpty())
                for (page in pages) {
                    val distinctBlockPdfPages = page.orderedBlocks.mapNotNull { it.sourcePdfPage }.toSet()
                    assertTrue(
                        "ReaderPage ${page.pageId} in ${section.sectionId} mixed multiple sourcePdfPages: $distinctBlockPdfPages",
                        distinctBlockPdfPages.size <= 1
                    )
                    if (distinctBlockPdfPages.size == 1) {
                        assertEquals(
                            "ReaderPage.sourcePdfPage must match its blocks' sourcePdfPage",
                            distinctBlockPdfPages.first(),
                            page.sourcePdfPage
                        )
                    }
                }
            }

            // Check sourcePdfPage-based pagination across pages 40..50
            for (pdfPage in 40..50) {
                val pageEntry = sourcePageIndex.entryForPage(pdfPage)!!
                val pages = ReaderPaginator.paginateChapterSourcePdfPage(
                    pageEntry = pageEntry,
                    sections = sections,
                    diagrams = diagrams,
                    fontScale = scale
                )
                assertTrue("PDF page $pdfPage must produce at least 1 ReaderPage", pages.isNotEmpty())
                for (page in pages) {
                    assertEquals(pdfPage, page.sourcePdfPage)
                    val blockPdfPages = page.orderedBlocks.mapNotNull { it.sourcePdfPage }.toSet()
                    assertEquals(setOf(pdfPage), blockPdfPages)
                }
            }
        }
    }
}
