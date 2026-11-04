package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChapterRepository
import com.example.data.FontSizeScale
import com.example.model.AuditStatus
import com.example.model.ExercisePhase
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.ui.ChapterViewModel
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject
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
class FrontMatterVersion20Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun saveWebpScreenshot(fileName: String) {
        composeTestRule.waitForIdle()
        val screenshotsDir = File("../screenshots").let {
            if (it.parentFile?.exists() == true) it else File("screenshots")
        }
        screenshotsDir.mkdirs()

        val decorViews = android.view.inspector.WindowInspector.getGlobalWindowViews()
        val targetView: View = decorViews.lastOrNull()
            ?: throw IllegalStateException("No root window view found for screenshot $fileName")
        val width = if (targetView.width > 0) targetView.width else 1080
        val height = if (targetView.height > 0) targetView.height else 2340

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        targetView.draw(canvas)

        val outFile = File(screenshotsDir, fileName)
        FileOutputStream(outFile).use { fos ->
            @Suppress("DEPRECATION")
            val format = Bitmap.CompressFormat.WEBP
            bitmap.compress(format, 88, fos)
        }
        assertTrue("Screenshot $fileName must exist and be non-empty", outFile.exists() && outFile.length() > 1000L)
    }

    // =========================================================================
    // Test 1: بارگذاری موفق هر ۳۹ صفحه page_001.json تا page_039.json
    // =========================================================================
    @Test
    fun `test 01 - successfully loads all 39 front matter pages page_001 to page_039`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val pack = repo.getFrontMatterContentPack()

        assertNotNull("Front matter content pack must be loaded", pack)
        assertEquals("FRONT_MATTER", pack!!.manifest.contentRegionId)
        assertEquals(1, pack.manifest.sourcePdfPageStart)
        assertEquals(39, pack.manifest.sourcePdfPageEnd)
        assertEquals(40, pack.manifest.nextSourcePage)
        assertEquals(39, pack.pages.size)
        assertEquals((1..39).toList(), pack.pages.map { it.sourcePdfPage })

        // Verify library catalog keeps FRONT_MATTER separate and never creates a fake CH00 chapter
        val catalog = repo.getLibraryCatalog()
        val book = catalog.books.first()
        assertNotNull(book.frontMatter)
        assertEquals("FRONT_MATTER", book.frontMatter!!.contentRegionId)
        assertEquals("پیش‌درآمد و مبانی شطرنج", book.frontMatter!!.navigationTitleFa)
        assertEquals(listOf("CH01"), book.chapters.map { it.chapterId })
        assertFalse("Fake chapter CH00 must never exist", book.chapters.any { it.chapterId == "CH00" })
    }

    // =========================================================================
    // Test 2: داشتن دقیقا یک sourcePdfPage در هر فایل صفحه
    // =========================================================================
    @Test
    fun `test 02 - each page JSON file has exactly one top-level sourcePdfPage matching its file index`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (pageNum in 1..39) {
            val fileName = "books/how_to_win_at_chess/front_matter/pages/page_${pageNum.toString().padStart(3, '0')}.json"
            val rawJson = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(rawJson)

            assertTrue("$fileName must contain sourcePdfPage", jsonObj.has("sourcePdfPage"))
            assertEquals(pageNum, jsonObj.getInt("sourcePdfPage"))
            assertEquals("FRONT_MATTER", jsonObj.getString("contentRegionId"))

            // Ensure no other sourcePdfPage value appears anywhere inside the file
            val regex = Regex(""""sourcePdfPage"\s*:\s*(\d+)""")
            val allValues = regex.findAll(rawJson).map { it.groupValues[1].toInt() }.toSet()
            assertEquals(
                "File $fileName must contain strictly one unique sourcePdfPage value ($pageNum)",
                setOf(pageNum),
                allValues
            )
        }
    }

    // =========================================================================
    // Test 3: برابری sourcePdfPage همه بلوک‌های داخل هر صفحه با شماره همان صفحه
    // =========================================================================
    @Test
    fun `test 03 - all blocks inside each front matter page have sourcePdfPage equal to that page`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val pages = repo.getFrontMatterPages()

        assertEquals(39, pages.size)
        for (page in pages) {
            for (block in page.orderedContentBlocks) {
                assertEquals(
                    "Block ${block.blockId} on page ${page.sourcePdfPage} must have matching sourcePdfPage",
                    page.sourcePdfPage,
                    block.sourcePdfPage
                )
            }
            for (renderBlock in page.buildRenderableBlocks()) {
                assertEquals(
                    "Renderable block ${renderBlock.blockId} on page ${page.sourcePdfPage} must have matching sourcePdfPage",
                    page.sourcePdfPage,
                    renderBlock.sourcePdfPage
                )
            }
        }
    }

    // =========================================================================
    // Test 4: عدم ادغام دو sourcePdfPage متفاوت در یک صفحه منبع هنگام صفحه‌بندی اپلیکیشن
    // =========================================================================
    @Test
    fun `test 04 - reader paginator never merges blocks from two different sourcePdfPages into one slice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val frontMatterPages = repo.getFrontMatterPages()

        for (scale in FontSizeScale.values()) {
            val paginatedSlices = ReaderPaginator.paginateFrontMatterPages(
                frontMatterPages = frontMatterPages,
                fontScale = scale,
                viewportWidthDp = 380,
                viewportHeightDp = 640
            )
            assertTrue("Must produce at least 39 reader slices", paginatedSlices.size >= 39)

            // Every single sourcePdfPage 1..39 must be represented
            val distinctPdfPages = paginatedSlices.mapNotNull { it.sourcePdfPage }.distinct()
            assertEquals((1..39).toList(), distinctPdfPages)

            // Within every single ReaderPage slice, all blocks must share the exact same sourcePdfPage
            for (slice in paginatedSlices) {
                assertNotNull("Every front matter slice must have sourcePdfPage", slice.sourcePdfPage)
                val blockPdfPages = slice.orderedBlocks.mapNotNull { it.sourcePdfPage }.toSet()
                assertEquals(
                    "Slice ${slice.pageId} must never mix blocks from different sourcePdfPage values",
                    setOf(slice.sourcePdfPage),
                    blockPdfPages
                )
            }
        }
    }

    // =========================================================================
    // Test 5: حفظ همان sourcePdfPage در تمام برش‌های یک صفحه بلند در صفحه‌نمایش موبایل
    // =========================================================================
    @Test
    fun `test 05 - multi-slice source pages retain the exact same sourcePdfPage and sequential slice labels`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val frontMatterPages = repo.getFrontMatterPages()

        val slices = ReaderPaginator.paginateFrontMatterPages(
            frontMatterPages = frontMatterPages,
            fontScale = FontSizeScale.LARGE,
            viewportWidthDp = 340,
            viewportHeightDp = 520
        )

        val groupedByPdfPage = slices.groupBy { it.sourcePdfPage }
        val multiSliceEntry = groupedByPdfPage.entries.firstOrNull { it.value.size > 1 }
        assertNotNull("At least one long PDF page must split into multiple slices on small phone viewport", multiSliceEntry)

        for ((pdfPage, pageSlices) in groupedByPdfPage) {
            assertNotNull(pdfPage)
            val totalSlices = pageSlices.size
            pageSlices.forEachIndexed { idx, slice ->
                assertEquals(pdfPage, slice.sourcePdfPage)
                assertEquals(idx + 1, slice.sliceIndexInSourcePage)
                assertEquals(totalSlices, slice.totalSlicesInSourcePage)
                val pageFa = ReaderPaginator.toPersianDigits(pdfPage!!)
                if (totalSlices > 1) {
                    val expectedLabel = "صفحه PDF $pageFa — بخش ${ReaderPaginator.toPersianDigits(idx + 1)} از ${ReaderPaginator.toPersianDigits(totalSlices)}"
                    assertEquals(expectedLabel, slice.sourcePdfPageDisplayLabelFa)
                } else {
                    assertEquals("صفحه PDF $pageFa", slice.sourcePdfPageDisplayLabelFa)
                }
            }
        }
    }

    // =========================================================================
    // Test 6: عدم تغییر sourcePdfPage هنگام تغییر اندازه فونت از کوچک به بزرگ
    // =========================================================================
    @Test
    fun `test 06 - changing font scale from SMALL to NORMAL to LARGE never alters sourcePdfPage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        for (targetPdfPage in listOf(1, 6, 11, 14, 21, 31, 36, 37, 38, 39)) {
            viewModel.openFrontMatter(initialSourcePdfPage = targetPdfPage)
            assertEquals(targetPdfPage, viewModel.uiState.value.currentPage?.sourcePdfPage)

            viewModel.setFontScale(FontSizeScale.SMALL)
            assertEquals(
                "sourcePdfPage must remain $targetPdfPage on SMALL font",
                targetPdfPage,
                viewModel.uiState.value.currentPage?.sourcePdfPage
            )

            viewModel.setFontScale(FontSizeScale.LARGE)
            assertEquals(
                "sourcePdfPage must remain $targetPdfPage on LARGE font",
                targetPdfPage,
                viewModel.uiState.value.currentPage?.sourcePdfPage
            )

            viewModel.setFontScale(FontSizeScale.NORMAL)
            assertEquals(
                "sourcePdfPage must remain $targetPdfPage on NORMAL font",
                targetPdfPage,
                viewModel.uiState.value.currentPage?.sourcePdfPage
            )
        }
    }

    // =========================================================================
    // Test 7: بارگذاری و وجود هر ۷۰ فایل شیء تصویری در assets/pdf_embedded_objects/
    // =========================================================================
    @Test
    fun `test 07 - all 70 embedded PDF image objects exist in assets and match placement CSV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val sourceAssets = repo.getFrontMatterSourceAssets()
        val placements = repo.getFrontMatterImagePlacements()

        assertEquals(70, sourceAssets.size)
        assertEquals(70, placements.size)

        val expectedIds = (0..69).map { "PDFOBJ_${it.toString().padStart(3, '0')}" }
        assertEquals(expectedIds, sourceAssets.map { it.assetId })
        assertEquals(expectedIds, placements.map { it.assetId })

        for (asset in sourceAssets) {
            assertTrue(
                "Asset file ${asset.resolvedAssetPath} for ${asset.assetId} must exist in assets",
                repo.assetExists(asset.resolvedAssetPath)
            )
            val bytes = context.assets.open(asset.resolvedAssetPath).use { it.readBytes() }
            assertTrue("Asset ${asset.assetId} must be non-empty", bytes.isNotEmpty())
        }
    }

    // =========================================================================
    // Test 8: تطابق تمام assetIdهای بلوک‌های sourceImage با فایل‌های واقعی
    // =========================================================================
    @Test
    fun `test 08 - all sourceImage blocks across pages 1 to 39 resolve to real image files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val pack = repo.getFrontMatterContentPack()!!

        val allSourceImageBlocks = pack.pages.flatMap { page ->
            page.buildRenderableBlocks().filterIsInstance<SectionContentBlock.SourceImageBlock>()
        }
        assertTrue("Front matter must contain sourceImage blocks", allSourceImageBlocks.size >= 28)

        for (imgBlock in allSourceImageBlocks) {
            val asset = pack.sourceAssetsById[imgBlock.assetId]
            assertNotNull("Asset ${imgBlock.assetId} for block ${imgBlock.blockId} must exist in manifest", asset)
            assertTrue(
                "Resolved asset path '${imgBlock.resolvedAssetPath}' must not be blank",
                imgBlock.resolvedAssetPath.isNotBlank()
            )
            assertTrue(
                "Asset file '${imgBlock.resolvedAssetPath}' must exist in Android assets",
                repo.assetExists(imgBlock.resolvedAssetPath)
            )
        }
    }

    // =========================================================================
    // Test 9: بارگذاری و اعتبار هر ۱۲۶ پیوند در source_links_pages_001_039.json
    // =========================================================================
    @Test
    fun `test 09 - all 126 source links in source_links_pages_001_039 json load and validate`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val pack = repo.getFrontMatterContentPack()!!
        val links = repo.getFrontMatterSourceLinks()

        assertEquals(126, links.size)
        assertEquals(126, pack.pages.sumOf { it.sourceLinks.size })

        // Verify page 21 internal links to pages 52 and 86
        val page21Links = pack.pageForSourcePdfPage(21)!!.sourceLinks
        assertEquals(2, page21Links.size)
        assertEquals(listOf(52, 86), page21Links.mapNotNull { it.targetSourcePdfPage })

        // Verify page 4 external/internal links (TenSpeed.com + cover + pages 2, 5, 10, 37, 261)
        val page4Links = pack.pageForSourcePdfPage(4)!!.sourceLinks
        assertEquals(7, page4Links.size)
        assertEquals(
            "https://crownpublishing.com/archives/imprint/ten-speed-press",
            page4Links.first { it.sourceLabel == "TenSpeed.com" }.targetUri
        )
        assertEquals(
            listOf(2, 5, 10, 37, 261),
            page4Links.mapNotNull { it.targetSourcePdfPage }
        )
    }

    // =========================================================================
    // Test 10: بررسی اینکه پیوندهای صفحه ۶ فهرست مطالب برای CHAPTER ONE به 39
    //          و برای HOW TO (LITERALLY) WIN AT CHESS به 40 اشاره می‌کنند
    // =========================================================================
    @Test
    fun `test 10 - page 6 TOC links target 39 for CHAPTER ONE and 40 for HOW TO LITERALLY WIN AT CHESS`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val pack = repo.getFrontMatterContentPack()!!
        val toc = repo.getFrontMatterTableOfContents()
        val page6 = pack.pageForSourcePdfPage(6)!!

        val ch1Link = page6.sourceLinks.find { it.sourceLabel == "CHAPTER ONE" }
        val ch1TitleLink = page6.sourceLinks.find { it.sourceLabel == "HOW TO (LITERALLY) WIN AT CHESS" }

        assertNotNull(ch1Link)
        assertEquals(39, ch1Link!!.targetSourcePdfPage)

        assertNotNull(ch1TitleLink)
        assertEquals(40, ch1TitleLink!!.targetSourcePdfPage)

        val tocCh1 = toc.entries.find { it.sourceLabel == "CHAPTER ONE" }
        val tocCh1Title = toc.entries.find { it.sourceLabel == "HOW TO (LITERALLY) WIN AT CHESS" }
        assertEquals(39, tocCh1?.targetSourcePdfPage)
        assertEquals("CH01", tocCh1?.chapterId)
        assertEquals(40, tocCh1Title?.targetSourcePdfPage)
        assertEquals("CH01", tocCh1Title?.chapterId)

        // Verify ViewModel navigation from TOC targets 39 and 40
        val viewModel = ChapterViewModel(app)
        assertTrue(viewModel.navigateToSourcePdfPage(39, "CH01"))
        assertTrue(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(39, viewModel.uiState.value.currentPage?.sourcePdfPage)

        assertTrue(viewModel.navigateToSourcePdfPage(40, "CH01"))
        assertFalse(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals("CH01", viewModel.uiState.value.activeChapterId)
        assertEquals("SEC_CH01_01_OPENING", viewModel.uiState.value.currentPage?.sectionId)
        assertEquals(40, viewModel.uiState.value.currentPage?.sourcePdfPage)
    }

    // =========================================================================
    // Test 11: بررسی اینکه صفحه ۳۸ شامل ۶ هدف آموزشی است و عبارت «میانه‌بازی»
    //          در FM_P038_L001 وجود دارد و «وسط‌بازی» وجود ندارد
    // =========================================================================
    @Test
    fun `test 11 - page 38 has 6 learning objectives with middlegame translated as میانه‌بازی and never وسط‌بازی`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val pack = repo.getFrontMatterContentPack()!!
        val page38 = pack.pageForSourcePdfPage(38)!!

        val listBlock = page38.orderedContentBlocks
            .filterIsInstance<SectionContentBlock.OrderedListBlock>()
            .firstOrNull { it.blockId == "FM_P038_L001" }

        assertNotNull("FM_P038_L001 must exist on page 38", listBlock)
        assertEquals(6, listBlock!!.items.size)
        assertTrue(
            "FM_P038_L001 must contain 'میانه‌بازی'",
            listBlock.items.any { it.contains("میانه‌بازی") }
        )
        assertFalse(
            "FM_P038_L001 must never contain 'وسط‌بازی'",
            listBlock.items.any { it.contains("وسط‌بازی") }
        )

        // Also check across all 39 pages that "وسط‌بازی" never appears
        for (page in pack.pages) {
            for (block in page.orderedContentBlocks) {
                val text = when (block) {
                    is SectionContentBlock.ParagraphBlock -> block.textFa.orEmpty()
                    is SectionContentBlock.HeadingBlock -> block.textFa.orEmpty()
                    is SectionContentBlock.OrderedListBlock -> block.items.joinToString(" ")
                    is SectionContentBlock.TableBlock -> block.rows.flatten().joinToString(" ")
                    else -> ""
                }
                assertFalse(
                    "Forbidden term 'وسط‌بازی' must not appear in block ${block.blockId} on page ${page.sourcePdfPage}",
                    text.contains("وسط‌بازی")
                )
            }
        }
    }

    // =========================================================================
    // Test 12: بررسی اینکه صفحه ۳۹ حذف نشده و شامل PDFOBJ_069 است
    // =========================================================================
    @Test
    fun `test 12 - page 39 is preserved as INTERSTITIAL_IMAGE with rook icon PDFOBJ_069`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ChapterRepository(context)
        val validation = repo.validateFrontMatterPack()
        assertTrue("Front matter pack validation must pass: ${validation.errorMessages}", validation.isValid)

        val pack = repo.getFrontMatterContentPack()!!
        val page39 = pack.pageForSourcePdfPage(39)
        assertNotNull("Page 39 must not be omitted", page39)
        assertEquals(39, page39!!.sourcePdfPage)
        assertEquals(listOf("PDFOBJ_069"), page39.sourceAssetIds)

        val imgBlock = page39.orderedContentBlocks
            .filterIsInstance<SectionContentBlock.SourceImageBlock>()
            .firstOrNull { it.blockId == "FM_P039_I001" }
        assertNotNull("FM_P039_I001 must exist on page 39", imgBlock)
        assertEquals("PDFOBJ_069", imgBlock!!.assetId)
        assertEquals(
            "books/how_to_win_at_chess/assets/pdf_embedded_objects/pdf_p039_obj069.jpg",
            imgBlock.resolvedAssetPath
        )
        assertTrue(repo.assetExists(imgBlock.resolvedAssetPath))
    }

    // =========================================================================
    // Test 13: بررسی عدم تغییر عملکرد فصل اول (CH01)، دیاگرام‌ها، PGN، تمرین BD-032 و BD-027
    // =========================================================================
    @Test
    fun `test 13 - chapter 1 CH01 diagrams PGN replay exercise BD-032 and blocked BD-027 remain intact`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val ch01Validation = repo.validateChapterPack("CH01")
        assertTrue("CH01 validation must succeed: ${ch01Validation.errorMessages}", ch01Validation.isValid)

        val viewModel = ChapterViewModel(app)
        val state = viewModel.uiState.value
        assertEquals("CH01", state.activeChapterId)
        assertEquals(6, state.sections.size)
        assertEquals(10, state.diagrams.size)

        // Verify BD-027 remains BLOCKED_NEEDS_HUMAN_REVIEW
        val bd027 = state.diagrams.first { it.diagramId == "BD-027" }
        assertEquals(AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW, bd027.auditStatus)

        // Verify PGN replay on BD-029 (Scholar's Mate)
        val bd029 = state.diagrams.first { it.diagramId == "BD-029" }
        val pgnRef = bd029.pgnRef!!
        viewModel.resetPgn(pgnRef)
        assertTrue(viewModel.uiState.value.pgnStates[pgnRef]!!.isAtStart)
        viewModel.stepPgnNext(pgnRef)
        assertEquals(1, viewModel.uiState.value.pgnStates[pgnRef]!!.currentStep)
        viewModel.jumpPgnToEnd(pgnRef)
        assertTrue(viewModel.uiState.value.pgnStates[pgnRef]!!.isAtEnd)

        // Verify BD-032 exercise (EX_CH01_BD032) solving flow: Qh8# (e5 -> h8)
        val exId = "EX_CH01_BD032"
        viewModel.resetExercise(exId)
        assertEquals(ExercisePhase.IDLE, viewModel.uiState.value.exerciseStateFor(exId)!!.phase)
        viewModel.onExerciseSquareClicked(exId, 4, 4) // e5
        viewModel.onExerciseSquareClicked(exId, 7, 7) // h8 (Qh8#)
        val exAfterMove = viewModel.uiState.value.exerciseStateFor(exId)!!
        if (exAfterMove.activeTransition != null) {
            viewModel.onExerciseTransitionFinished(exId, exAfterMove.activeTransition!!.transitionId)
        }
        assertEquals(ExercisePhase.SOLVED, viewModel.uiState.value.exerciseStateFor(exId)!!.phase)
    }

    // =========================================================================
    // Test 14: تولید ۸ اسکرین‌شات واقعی از صفحات ۱۱، ۱۴، ۲۱، ۳۶، ۳۷، ۳۸، ۳۹ و ۴۰
    // =========================================================================
    @Test
    fun `test 14 - render and capture 8 required screenshots for PDF pages 11 14 21 36 37 38 39 and 40`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.openFrontMatter(11)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val frontMatterTargets = listOf(
            11 to "pdf_page_011.webp",
            14 to "pdf_page_014.webp",
            21 to "pdf_page_021.webp",
            36 to "pdf_page_036.webp",
            37 to "pdf_page_037.webp",
            38 to "pdf_page_038.webp",
            39 to "pdf_page_039.webp"
        )

        for ((pdfPage, fileName) in frontMatterTargets) {
            composeTestRule.runOnIdle {
                viewModel.openFrontMatter(pdfPage)
            }
            composeTestRule.waitForIdle()
            assertEquals(pdfPage, viewModel.uiState.value.currentPage?.sourcePdfPage)
            composeTestRule.onNodeWithTag("reader_source_pdf_page_badge").assertIsDisplayed()
            saveWebpScreenshot(fileName)
        }

        // Page 40: Beginning of Chapter One (CH01)
        composeTestRule.runOnIdle {
            viewModel.navigateToSourcePdfPage(40, "CH01")
        }
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.isReadingFrontMatter)
        assertEquals(40, viewModel.uiState.value.currentPage?.sourcePdfPage)
        composeTestRule.onNodeWithTag("reader_source_pdf_page_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("شروع فصل").assertIsDisplayed()
        saveWebpScreenshot("pdf_page_040.webp")
    }
}
