package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChapterRepository
import com.example.data.FontSizeScale
import com.example.data.UserPreferencesManager
import com.example.model.ReaderPageType
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.model.TranslationSlot
import com.example.model.TranslationStatus
import com.example.ui.ChapterViewModel
import com.example.ui.components.BoardDarkSquare
import com.example.ui.components.BoardLightSquare
import com.example.ui.screens.ReaderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ReaderDesignTokens
import com.example.ui.theme.SemanticBadgeCategory
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class ReaderPaginationRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun collectAllNodes(node: SemanticsNode): List<SemanticsNode> {
        val list = mutableListOf(node)
        node.children.forEach { child ->
            list.addAll(collectAllNodes(child))
        }
        return list
    }

    private fun saveReaderScreenshot(fileName: String) {
        composeTestRule.waitForIdle()
        val screenshotsDir = File("../screenshots").let {
            if (it.parentFile?.exists() == true) it else File("screenshots")
        }
        screenshotsDir.mkdirs()

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

    // =========================================================================
    // 1. حرکت صفحه بعد با Swipe راست (کشیدن صفحه از چپ به راست -> صفحه بعد)
    // =========================================================================
    @Test
    fun `reader test 1 - swipe right from left to right advances to next page in Persian RTL`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()
        val total = viewModel.uiState.value.currentPages.size

        assertEquals(0, viewModel.uiState.value.currentPageIndex)
        composeTestRule.onNodeWithText("صفحه 1 از $total").assertIsDisplayed()

        // Swipe from Left to Right (swipeRight) -> must move to Next Page (Page 2)
        composeTestRule.onNodeWithTag("reader_horizontal_pager").performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Swiping from left to right (swipeRight) must advance to the next page in Persian Reader",
            1,
            viewModel.uiState.value.currentPageIndex
        )
        composeTestRule.onNodeWithText("صفحه 2 از $total").assertIsDisplayed()
    }

    // =========================================================================
    // 2. حرکت صفحه قبل با Swipe چپ (کشیدن صفحه از راست به چپ -> صفحه قبل)
    // =========================================================================
    @Test
    fun `reader test 2 - swipe left from right to left returns to previous page in Persian RTL`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1)
        val total = viewModel.uiState.value.currentPages.size
        val bd027Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-027" }
        viewModel.goToPage(bd027Idx)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(bd027Idx, viewModel.uiState.value.currentPageIndex)
        composeTestRule.onNodeWithText("صفحه ${bd027Idx + 1} از $total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagram_card_BD-027").assertIsDisplayed()

        // Swipe from Right to Left (swipeLeft) -> must return to Previous Page
        composeTestRule.onNodeWithTag("reader_horizontal_pager").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Swiping from right to left (swipeLeft) must return to the previous page in Persian Reader",
            bd027Idx - 1,
            viewModel.uiState.value.currentPageIndex
        )
        composeTestRule.onNodeWithText("صفحه $bd027Idx از $total").assertIsDisplayed()
    }

    // =========================================================================
    // 3. عملکرد دکمه فلش چپ (صفحه بعد) و دکمه فلش راست (صفحه قبل)
    // =========================================================================
    @Test
    fun `reader test 3 - left arrow button advances to next page and right arrow button returns to previous page`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()
        val total = viewModel.uiState.value.currentPages.size

        // Verify physical geometry: Left arrow button (reader_next_page_btn) is physically on the left side,
        // and Right arrow button (reader_prev_page_btn) is physically on the right side
        val leftBtnBounds = composeTestRule.onNodeWithTag("reader_next_page_btn").fetchSemanticsNode().boundsInRoot
        val rightBtnBounds = composeTestRule.onNodeWithTag("reader_prev_page_btn").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Left arrow button (Next Page) must be physically to the left of Right arrow button (Previous Page)",
            leftBtnBounds.left < rightBtnBounds.left
        )

        // Click Left arrow button -> advances from Page 1 to Page 2
        composeTestRule.onNodeWithTag("reader_next_page_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.currentPageIndex)
        composeTestRule.onNodeWithText("صفحه 2 از $total").assertIsDisplayed()

        // Click Right arrow button -> returns from Page 2 to Page 1
        composeTestRule.onNodeWithTag("reader_prev_page_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, viewModel.uiState.value.currentPageIndex)
        composeTestRule.onNodeWithText("صفحه 1 از $total").assertIsDisplayed()
    }

    // =========================================================================
    // 4. عدم ایجاد اسکرول عمودی در Reader
    // =========================================================================
    @Test
    fun `reader test 4 - Reader has no vertical scroll container or nested vertical scroll`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val rootNode = composeTestRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val allNodes = collectAllNodes(rootNode)
        val verticallyScrollableNodes = allNodes.filter {
            SemanticsProperties.VerticalScrollAxisRange in it.config
        }

        assertTrue(
            "ReaderScreen must NOT contain any vertical scroll container (found ${verticallyScrollableNodes.size})",
            verticallyScrollableNodes.isEmpty()
        )
    }

    // =========================================================================
    // 5. وجود حداکثر یک DiagramBlock در هر صفحه
    // =========================================================================
    @Test
    fun `reader test 5 - every ReaderPage across all sections has at most one diagram or exercise block`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val diagrams = repo.getDiagrams()
        val sections = repo.getSections()

        for (section in sections) {
            for (scale in FontSizeScale.values()) {
                val pages = ReaderPaginator.paginateSection(
                    section = section,
                    diagrams = diagrams,
                    fontScale = scale
                )
                assertTrue("Section ${section.sectionId} must produce at least 1 page", pages.isNotEmpty())
                pages.forEach { page ->
                    assertTrue(
                        "Page ${page.pageId} in ${section.sectionId} must have at most 1 diagram/exercise block, but had ${page.diagramBlockCount}",
                        page.diagramBlockCount <= 1
                    )
                }
            }
        }
    }

    // =========================================================================
    // 6. حفظ ترتیب تمام orderedContentBlocks
    // =========================================================================
    @Test
    fun `reader test 6 - pagination preserves exact sequence of all orderedContentBlocks`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val diagrams = repo.getDiagrams()
        val checkmateSection = repo.getSections().first { it.sectionId == "SEC_CH01_02_CHECKMATE" }

        val complexBlocks = listOf(
            SectionContentBlock.HeadingBlock("B_H1", textFa = "عنوان اول"),
            SectionContentBlock.ParagraphBlock("B_P1", textFa = "پاراگراف اول در ابتدای بخش."),
            SectionContentBlock.ParagraphBlock("B_P2", textFa = "پاراگراف دوم توضیح نمودار بیست و شش."),
            SectionContentBlock.DiagramBlock("B_D26", diagramId = "BD-026"),
            SectionContentBlock.ParagraphBlock("B_P3_EMPTY", textFa = null, showPlaceholderWhenEmpty = false),
            SectionContentBlock.ParagraphBlock("B_P4", textFa = "پاراگراف چهارم پیش از نمودار بیست و هفت."),
            SectionContentBlock.DiagramBlock("B_D27", diagramId = "BD-027"),
            SectionContentBlock.FootnoteBlock("B_F1", textFa = "پاورقی مربوط به وضعیت صفحه."),
            SectionContentBlock.ParagraphBlock("B_P5", textFa = "پاراگراف پنجم توضیح نمودار بیست و هشت."),
            SectionContentBlock.DiagramBlock("B_D28", diagramId = "BD-028")
        )

        val testSection = checkmateSection.copy(
            translationSlot = TranslationSlot(
                sectionId = checkmateSection.sectionId,
                orderedContentBlocks = complexBlocks,
                translationStatus = TranslationStatus.COMPLETE
            )
        )

        val pages = ReaderPaginator.paginateSection(testSection, diagrams, FontSizeScale.NORMAL)
        val flattenedOriginalIds = pages
            .flatMap { it.orderedBlocks }
            .map { ReaderPaginator.originalBlockId(it.blockId) }
            .distinct()

        assertEquals(
            "Pagination must preserve the exact sequence of all orderedContentBlocks",
            complexBlocks.map { it.blockId },
            flattenedOriginalIds
        )
    }

    // =========================================================================
    // 7. عدم گم شدن پاراگراف هنگام تقسیم صفحات
    // =========================================================================
    @Test
    fun `reader test 7 - long paragraphs split across pages without losing any text or paragraphs`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val repo = ChapterRepository(app)
        val diagrams = repo.getDiagrams()
        val openingSection = repo.getSections().first()

        val sentenceUnit = "در بازی شطرنج هدف اصلی رساندن شاه حریف به وضعیت مات است و هر بازیکن باید با دقت تمام حرکات قانونی را بررسی کند."
        val longParagraph1 = (1..10).joinToString(" ") { "جمله شماره $it: $sentenceUnit" }
        val paragraph2 = "پاراگراف دوم: در این بخش به بررسی قوانین پایه و اهمیت هماهنگی مهره‌ها در صفحه شطرنج می‌پردازیم."
        val longParagraph3 = (11..18).joinToString(" ") { "جمله شماره $it: $sentenceUnit" }

        val blocks = listOf(
            SectionContentBlock.HeadingBlock("SEC_CH01_01_H01", textFa = "مقدمه‌ای بر پیروزی در شطرنج"),
            SectionContentBlock.ParagraphBlock("SEC_CH01_01_P01", textFa = longParagraph1),
            SectionContentBlock.ParagraphBlock("SEC_CH01_01_P02", textFa = paragraph2),
            SectionContentBlock.ParagraphBlock("SEC_CH01_01_P03", textFa = longParagraph3),
            SectionContentBlock.DiagramBlock("SEC_CH01_01_D025", diagramId = "BD-025")
        )

        val slot = TranslationSlot(
            sectionId = openingSection.sectionId,
            orderedContentBlocks = blocks,
            translationStatus = TranslationStatus.COMPLETE
        )
        val updatedSection = openingSection.copy(translationSlot = slot)

        val pages = ReaderPaginator.paginateSection(updatedSection, diagrams, FontSizeScale.NORMAL)
        assertTrue("Long text + diagram must split across at least 3 pages", pages.size >= 3)

        // Reconstruct text for P01, P02, P03 from the paginated blocks using joinToString("")
        // and verify zero characters, spaces, or sentences are lost
        val p01Reconstructed = pages.flatMap { it.orderedBlocks }
            .filterIsInstance<SectionContentBlock.ParagraphBlock>()
            .filter { ReaderPaginator.originalBlockId(it.blockId) == "SEC_CH01_01_P01" }
            .mapNotNull { slot.resolveBlockText(it) }
            .joinToString("")

        val p02Reconstructed = pages.flatMap { it.orderedBlocks }
            .filterIsInstance<SectionContentBlock.ParagraphBlock>()
            .filter { ReaderPaginator.originalBlockId(it.blockId) == "SEC_CH01_01_P02" }
            .mapNotNull { slot.resolveBlockText(it) }
            .joinToString("")

        val p03Reconstructed = pages.flatMap { it.orderedBlocks }
            .filterIsInstance<SectionContentBlock.ParagraphBlock>()
            .filter { ReaderPaginator.originalBlockId(it.blockId) == "SEC_CH01_01_P03" }
            .mapNotNull { slot.resolveBlockText(it) }
            .joinToString("")

        assertEquals(longParagraph1, p01Reconstructed)
        assertEquals(paragraph2, p02Reconstructed)
        assertEquals(longParagraph3, p03Reconstructed)

        // Render Page 1 (Text page) in ReaderScreen and capture sample screenshot
        viewModel.updateSectionTranslationSlot(openingSection.sectionId, slot)
        viewModel.selectSection(0)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()
        saveReaderScreenshot("09_reader_text_page.png")
    }

    // =========================================================================
    // 8. قرار گرفتن نمودار کنار توضیح مرتبط
    // =========================================================================
    @Test
    fun `reader test 8 - diagram is placed on the same page alongside its related explanatory paragraph`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val diagrams = repo.getDiagrams()
        val checkmateSection = repo.getSections()[1]

        // Matches the prompt's Section 3 structure:
        // P001 (general intro), P002 (companion to BD-026), BD-026, P003 (long standalone), BD-027, P004 (companion to BD-028), BD-028
        val longStandaloneText = (1..3).joinToString(" ") {
            "این یک پاراگراف مستقل و طولانی بین دو نمودار است که به تنهایی بیش از ظرفیت متن همراه یک صفحه شطرنج فضا نیاز دارد."
        }
        val blocks = listOf(
            SectionContentBlock.ParagraphBlock("P001", textFa = "پاراگراف اول مقدماتی فصل."),
            SectionContentBlock.ParagraphBlock("P002", textFa = "توضیح مستقیم مرتبط با نمودار BD-026."),
            SectionContentBlock.DiagramBlock("D026", diagramId = "BD-026"),
            SectionContentBlock.ParagraphBlock("P003", textFa = longStandaloneText),
            SectionContentBlock.DiagramBlock("D027", diagramId = "BD-027"),
            SectionContentBlock.ParagraphBlock("P004", textFa = "توضیح مستقیم مرتبط با نمودار BD-028."),
            SectionContentBlock.DiagramBlock("D028", diagramId = "BD-028")
        )

        val testSection = checkmateSection.copy(
            translationSlot = TranslationSlot(
                sectionId = checkmateSection.sectionId,
                orderedContentBlocks = blocks,
                translationStatus = TranslationStatus.COMPLETE
            )
        )

        val pages = ReaderPaginator.paginateSection(testSection, diagrams, FontSizeScale.NORMAL)
        assertEquals(5, pages.size)

        // Page 1: P001
        assertEquals(listOf("P001"), pages[0].orderedBlocks.map { it.blockId })
        // Page 2: P002 + D026 together!
        assertEquals(listOf("P002", "D026"), pages[1].orderedBlocks.map { it.blockId })
        // Page 3: P003
        assertEquals(listOf("P003"), pages[2].orderedBlocks.map { it.blockId })
        // Page 4: D027
        assertEquals(listOf("D027"), pages[3].orderedBlocks.map { it.blockId })
        // Page 5: P004 + D028 together!
        assertEquals(listOf("P004", "D028"), pages[4].orderedBlocks.map { it.blockId })
    }

    // =========================================================================
    // 9. صحت شماره صفحه و تعداد کل صفحات
    // =========================================================================
    @Test
    fun `reader test 9 - pageNumber and totalPages are accurate and displayed in UI`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(1) // SEC_CH01_02_CHECKMATE

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val pages = viewModel.uiState.value.currentPages
        val total = pages.size
        assertTrue(total >= 5)
        pages.forEachIndexed { idx, page ->
            assertEquals(idx + 1, page.pageNumber)
            assertEquals(total, page.totalPages)
        }

        composeTestRule.onNodeWithTag("reader_breadcrumb_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("فصل اول / بخش 2 / مات").assertIsDisplayed()
        composeTestRule.onNodeWithText("صفحه 1 از $total").assertIsDisplayed()

        viewModel.goToPage(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("صفحه 4 از $total").assertIsDisplayed()
    }

    // =========================================================================
    // 10. حفظ وضعیت مطالعه پس از بازکردن مجدد برنامه
    // =========================================================================
    @Test
    fun `reader test 10 - reading position section and page are persisted across app restart and reset to page 1 on new section`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = UserPreferencesManager(app)
        val viewModel = ChapterViewModel(app)

        // Navigate to Section 1 (Checkmate), Page 4 (0-based index 3)
        viewModel.selectSection(1)
        viewModel.goToPage(3)
        assertEquals(1, viewModel.uiState.value.currentSectionIndex)
        assertEquals(3, viewModel.uiState.value.currentPageIndex)
        assertEquals(1, prefs.lastReadSectionIndex)
        assertEquals(3, prefs.lastReadPageIndex)

        // Simulate closing and reopening the app with a fresh ViewModel instance
        val restartedViewModel = ChapterViewModel(app)
        assertEquals(
            "Restored section index must match saved reading position",
            1,
            restartedViewModel.uiState.value.currentSectionIndex
        )
        assertEquals(
            "Restored page index must match saved reading position (Page 4, index 3)",
            3,
            restartedViewModel.uiState.value.currentPageIndex
        )
        assertEquals(4, restartedViewModel.uiState.value.currentPage?.pageNumber)

        // Entering a new section must open Page 1 (index 0) of that section
        restartedViewModel.selectSection(3)
        assertEquals(3, restartedViewModel.uiState.value.currentSectionIndex)
        assertEquals(
            "Selecting a new section must open its first page (index 0)",
            0,
            restartedViewModel.uiState.value.currentPageIndex
        )
        assertEquals(1, restartedViewModel.uiState.value.currentPage?.pageNumber)
    }

    // =========================================================================
    // 11. محاسبه مجدد صفحهبندی هنگام تغییر اندازه قلم
    // =========================================================================
    @Test
    fun `reader test 11 - changing font scale deterministically recalculates pagination and moves excess text to subsequent pages`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val paraText = "در مطالعه شطرنج، درک الگوهای مات و هماهنگی میان سوارها اهمیت بسیار زیادی دارد و هر بازیکن باید موقعیت شاه و خطوط باز را به دقت ارزیابی نماید تا بهترین طرح را انتخاب کند."
        val blocks = (1..5).map { idx ->
            SectionContentBlock.ParagraphBlock(
                blockId = "SEC_CH01_06_SUMMARY_P00$idx",
                textFa = "$paraText ($idx) $paraText"
            )
        }

        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_06_SUMMARY",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_06_SUMMARY",
                orderedContentBlocks = blocks,
                translationStatus = TranslationStatus.COMPLETE
            )
        )
        viewModel.selectSection(5)

        viewModel.setFontScale(FontSizeScale.SMALL)
        val smallPageCount = viewModel.uiState.value.currentPages.size

        viewModel.setFontScale(FontSizeScale.NORMAL)
        val normalPageCount = viewModel.uiState.value.currentPages.size

        viewModel.setFontScale(FontSizeScale.LARGE)
        val largePageCount = viewModel.uiState.value.currentPages.size
        val largePagesFirstRun = viewModel.uiState.value.currentPages

        assertTrue(
            "LARGE font scale ($largePageCount pages) must produce strictly more pages than SMALL font scale ($smallPageCount pages)",
            largePageCount > smallPageCount
        )
        assertTrue(
            "LARGE font scale ($largePageCount pages) must produce >= pages than NORMAL font scale ($normalPageCount pages)",
            largePageCount >= normalPageCount
        )

        // Verify deterministic output: running again with LARGE produces the exact same pages
        viewModel.setFontScale(FontSizeScale.NORMAL)
        viewModel.setFontScale(FontSizeScale.LARGE)
        assertEquals(
            "Pagination must be 100% deterministic for identical input and font scale",
            largePagesFirstRun,
            viewModel.uiState.value.currentPages
        )

        // Reset font scale back to NORMAL
        viewModel.setFontScale(FontSizeScale.NORMAL)
    }

    // =========================================================================
    // 12. ثابت ماندن رنگ صفحه شطرنج در تمام pageTypeها
    // =========================================================================
    @Test
    fun `reader test 12 - chessboard light and dark square colors remain constant ECDBBA and C5A076 across all pageTypes`() {
        val expectedLight = Color(0xFFECDBBA)
        val expectedDark = Color(0xFFC5A076)

        assertEquals(expectedLight, ReaderDesignTokens.BoardLightSquareColor)
        assertEquals(expectedDark, ReaderDesignTokens.BoardDarkSquareColor)
        assertEquals(expectedLight, BoardLightSquare)
        assertEquals(expectedDark, BoardDarkSquare)

        // Verify that none of the semantic badge palettes alter the chessboard square colors
        for (category in SemanticBadgeCategory.values()) {
            val lightPalette = ReaderDesignTokens.paletteFor(category, darkTheme = false)
            val darkPalette = ReaderDesignTokens.paletteFor(category, darkTheme = true)
            assertNotNull(lightPalette)
            assertNotNull(darkPalette)
            // Board colors remain strictly #ECDBBA and #C5A076 regardless of pageType or theme
            assertEquals(expectedLight, BoardLightSquare)
            assertEquals(expectedDark, BoardDarkSquare)
        }
    }

    // =========================================================================
    // 13. نمایش صحیح رنگ Badge هر نوع محتوا
    // =========================================================================
    @Test
    fun `reader test 13 - semantic badge displays correct color title and icon for all 6 content categories in light and dark themes`() {
        // 1. Verify all 6 categories have distinct container colors in both Light and Dark themes + title + icon
        val lightPalettes = SemanticBadgeCategory.values().map {
            ReaderDesignTokens.paletteFor(it, darkTheme = false)
        }
        val darkPalettes = SemanticBadgeCategory.values().map {
            ReaderDesignTokens.paletteFor(it, darkTheme = true)
        }

        assertEquals(6, lightPalettes.map { it.containerColor }.distinct().size)
        assertEquals(6, darkPalettes.map { it.containerColor }.distinct().size)

        assertEquals("مثال تصویری", ReaderDesignTokens.paletteFor(SemanticBadgeCategory.VISUAL_EXAMPLE).titleFa)
        assertEquals("بازپخش حرکات", ReaderDesignTokens.paletteFor(SemanticBadgeCategory.MOVE_REPLAY).titleFa)
        assertEquals("تمرین تعاملی", ReaderDesignTokens.paletteFor(SemanticBadgeCategory.INTERACTIVE_EXERCISE).titleFa)
        assertEquals("مقایسه موقعیت", ReaderDesignTokens.paletteFor(SemanticBadgeCategory.POSITION_COMPARISON).titleFa)
        assertEquals("نیازمند بازبینی", ReaderDesignTokens.paletteFor(SemanticBadgeCategory.NEEDS_REVIEW).titleFa)
        assertEquals("متن آموزشی", ReaderDesignTokens.paletteFor(SemanticBadgeCategory.EDUCATIONAL_TEXT).titleFa)

        // 2. Render ReaderScreen across different page types and verify badge UI + capture sample screenshots
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        // A. STATIC_DIAGRAM (BD-025 in Section 0) -> VISUAL_EXAMPLE ("مثال تصویری")
        viewModel.selectSection(0)
        val bd025Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" }
        viewModel.goToPage(bd025Idx)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_semantic_badge_VISUAL_EXAMPLE").assertIsDisplayed()
        composeTestRule.onNodeWithText("مثال تصویری").assertIsDisplayed()
        saveReaderScreenshot("10_reader_static_diagram_page.png")
        saveReaderScreenshot("02_static_diagram_page.png")

        // B. STATIC_DIAGRAM (BD-027 in Section 1) -> VISUAL_EXAMPLE ("مثال تصویری") after V22 FEN correction
        viewModel.selectSection(1)
        val bd027Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-027" }
        viewModel.goToPage(bd027Idx)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_semantic_badge_VISUAL_EXAMPLE").assertIsDisplayed()
        composeTestRule.onNodeWithText("مثال تصویری").assertIsDisplayed()

        // C. MOVE_REPLAY (BD-029 in Section 1) -> MOVE_REPLAY ("بازپخش حرکات")
        val bd029Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-029" }
        viewModel.goToPage(bd029Idx)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_semantic_badge_MOVE_REPLAY").assertIsDisplayed()
        composeTestRule.onNodeWithText("بازپخش حرکات").assertIsDisplayed()
        saveReaderScreenshot("11_reader_move_replay_page.png")
        saveReaderScreenshot("03_move_replay_page.png")

        // D. INTERACTIVE_EXERCISE (BD-032 in Section 3) -> INTERACTIVE_EXERCISE ("تمرین تعاملی")
        viewModel.selectSection(3)
        val bd032Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-032" }
        viewModel.goToPage(bd032Idx)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_semantic_badge_INTERACTIVE_EXERCISE").assertIsDisplayed()
        composeTestRule.onNodeWithText("تمرین تعاملی").assertIsDisplayed()
        saveReaderScreenshot("12_reader_exercise_bd032_page.png")
        saveReaderScreenshot("04_exercise_bd032_page.png")

        // E. POSITION_COMPARISON (BD-033 in Section 3) -> POSITION_COMPARISON ("مقایسه موقعیت")
        val bd033Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-033" }
        viewModel.goToPage(bd033Idx)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_semantic_badge_POSITION_COMPARISON").assertIsDisplayed()
        composeTestRule.onNodeWithText("مقایسه موقعیت").assertIsDisplayed()

        // F. SUMMARY (Section 5, Page 1) -> EDUCATIONAL_TEXT ("مرور و جمع‌بندی")
        viewModel.selectSection(5)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reader_semantic_badge_EDUCATIONAL_TEXT").assertIsDisplayed()
        composeTestRule.onNodeWithText("مرور و جمع‌بندی").assertIsDisplayed()
    }

    // =========================================================================
    // 14. حفظ انیمیشن مهرهها هنگام ورق زدن و بازگشت
    // =========================================================================
    @Test
    fun `reader test 14 - piece movement animation and reverse animation are preserved after turning pages and returning`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        // Section 1, BD-029 (Scholar's Mate MOVE_REPLAY)
        viewModel.selectSection(1)
        val bd029Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-029" }
        viewModel.goToPage(bd029Idx)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        // Turn to next page and then turn back to BD-029 page
        composeTestRule.onNodeWithTag("reader_next_page_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(bd029Idx + 1, viewModel.uiState.value.currentPageIndex)

        composeTestRule.onNodeWithTag("reader_prev_page_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(bd029Idx, viewModel.uiState.value.currentPageIndex)
        composeTestRule.onNodeWithTag("diagram_card_BD-029").assertIsDisplayed()

        // Now pause clock and test Reverse Animation (Previous step: Qxf7# reversed f7 -> h5 with f7 pawn fade-in)
        composeTestRule.mainClock.autoAdvance = false
        viewModel.stepPgnPrev("SCHOLARS_MATE_CANDIDATE")
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()
        composeTestRule.onNodeWithTag("captured_piece_overlay_f7").assertExists()

        // Finish reverse animation
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.advanceTimeByFrame()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()

        // Now test Forward Animation (Next step: Qxf7# h5 -> f7)
        viewModel.stepPgnNext("SCHOLARS_MATE_CANDIDATE")
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        composeTestRule.onNodeWithTag("moving_piece_primary").assertExists()

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()
    }

    // =========================================================================
    // 15. حفظ عملکرد تمرین BD-032
    // =========================================================================
    @Test
    fun `reader test 15 - BD-032 interactive exercise functions properly inside paginated ReaderPage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.resetBd032()
        // Section 3 (Timing Out): navigate to BD-032 page
        viewModel.selectSection(3)
        val bd032Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-032" }
        viewModel.goToPage(bd032Idx)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(ReaderPageType.INTERACTIVE_EXERCISE, viewModel.uiState.value.currentPage?.pageType)
        composeTestRule.onNodeWithTag("diagram_card_BD-032").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagram_card_BD-033").assertDoesNotExist()

        // Select Black Queen on e5 and play winning move Qh8# inside paginated Reader
        composeTestRule.onNodeWithTag("square_e5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("square_h8").performClick()
        composeTestRule.waitForIdle()

        assertTrue(viewModel.uiState.value.bd032State.isSolved)
        composeTestRule.onNodeWithTag("exercise_feedback").assertIsDisplayed()
        composeTestRule.onNodeWithText("پاسخ صحیح! حرکت Qh8# توسط موتور قوانین بررسی شد و شاه سفید مات شد.").assertIsDisplayed()
    }

    // =========================================================================
    // 16. جهت صحیح تخته شطرنج در RTL
    // =========================================================================
    @Test
    fun `reader test 16 - chessboard files a to h LTR and ranks 8 to 1 top to bottom are preserved inside paginated RTL ReaderPage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        viewModel.selectSection(0)
        val bd025Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" }
        viewModel.goToPage(bd025Idx)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val files = listOf("a1", "b1", "c1", "d1", "e1", "f1", "g1", "h1")
        val xCoords = files.map { sq ->
            composeTestRule.onNodeWithTag("square_$sq").fetchSemanticsNode().boundsInRoot.left
        }
        for (i in 0 until xCoords.size - 1) {
            assertTrue(
                "Square ${files[i]} must be strictly to the left of ${files[i + 1]} inside RTL ReaderScreen",
                xCoords[i] < xCoords[i + 1]
            )
        }

        val ranks = listOf("a8", "a7", "a6", "a5", "a4", "a3", "a2", "a1")
        val yCoords = ranks.map { sq ->
            composeTestRule.onNodeWithTag("square_$sq").fetchSemanticsNode().boundsInRoot.top
        }
        for (i in 0 until yCoords.size - 1) {
            assertTrue(
                "Square ${ranks[i]} must be strictly above ${ranks[i + 1]} inside RTL ReaderScreen",
                yCoords[i] < yCoords[i + 1]
            )
        }
    }

    // =========================================================================
    // 17. بازسازی دقیق متن پس از تقسیم (فاصله، نیم‌فاصله، چند فاصله متوالی، نشانه‌گذاری و خط جدید)
    // =========================================================================
    @Test
    fun `reader test 17 - splitLongParagraphText preserves every character including spaces ZWNJ consecutive spaces punctuation and newlines`() {
        val zwnj = "\u200C"
        val complexPersianText = buildString {
            append("  در بازی${zwnj}های شطرنج، هماهنگی میان مهره${zwnj}ها   اهمیت فوق${zwnj}العاده${zwnj}ای دارد!  ")
            append("\n")
            append("آیا می${zwnj}دانید چرا خانه${zwnj}های مرکزی (e4، d4، e5 و d5) کلیدی هستند؟   بله؛ زیرا کنترل مرکز به سوارها تحرک بیشتری می${zwnj}دهد.\n\n")
            append("یک_کلمه_بسیار_طولانی_بدون_فاصله_برای_آزمون_شکستن_ایمن_در_مرز_ظرفیت_صفحه_بدون_حذف_هیچ_کاراکتری ")
            append("و در پایان نیز دو فاصله متوالی  و نیم${zwnj}فاصله${zwnj}ها دقیقاً حفظ می${zwnj}شوند. ")
        }

        // Test across multiple small and medium chunk capacities
        for (maxChars in listOf(45, 80, 120, 200)) {
            val chunks = ReaderPaginator.splitLongParagraphText(
                text = complexPersianText,
                maxChunkChars = maxChars,
                maxChunkLines = 4,
                charsPerLine = 30
            )
            assertTrue("Text must be split into multiple chunks for maxChars=$maxChars", chunks.size >= 2)
            assertEquals(
                "Joining chunks with empty string must reconstruct the exact original text for maxChars=$maxChars",
                complexPersianText,
                chunks.joinToString("")
            )
        }
    }

    // =========================================================================
    // 18. تست پاراگراف با تعداد خط بیشتر از maxLinesPerTextPage و کاراکتر کمتر از maxCharsPerTextPage
    // =========================================================================
    @Test
    fun `reader test 18 - paragraph with char count below maxCharsPerTextPage but line count above maxLinesPerTextPage splits across pages`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ChapterRepository(app)
        val diagrams = repo.getDiagrams()
        val openingSection = repo.getSections().first()

        // 18 short lines separated by '\n': total length is ~230 chars (< maxCharsPerTextPage = 520),
        // but total visual lines is 18 (> maxLinesPerTextPage = 14)
        val multiLineShortText = (1..18).joinToString("\n") { lineNum ->
            "سطر کوتاه شماره $lineNum"
        }
        assertTrue(
            "Test paragraph must be shorter than 350 chars (was ${multiLineShortText.length})",
            multiLineShortText.length < 350
        )

        val slot = TranslationSlot(
            sectionId = openingSection.sectionId,
            orderedContentBlocks = listOf(
                SectionContentBlock.ParagraphBlock(
                    blockId = "P_MULTILINE",
                    textFa = multiLineShortText
                )
            ),
            translationStatus = TranslationStatus.COMPLETE
        )
        val testSection = openingSection.copy(translationSlot = slot)

        val pages = ReaderPaginator.paginateSection(
            section = testSection,
            diagrams = diagrams,
            fontScale = FontSizeScale.NORMAL,
            viewportWidthDp = 380,
            viewportHeightDp = 640
        )

        assertTrue(
            "A 18-line paragraph (< maxCharsPerTextPage) must be split across at least 2 pages so it does not overflow line capacity",
            pages.size >= 2
        )

        val reconstructed = pages.flatMap { it.orderedBlocks }
            .filterIsInstance<SectionContentBlock.ParagraphBlock>()
            .mapNotNull { slot.resolveBlockText(it) }
            .joinToString("")

        assertEquals(
            "Rejoining the line-split paragraph chunks with joinToString(\"\") must match the exact original multi-line string",
            multiLineShortText,
            reconstructed
        )
    }

    // =========================================================================
    // 19. تست تمام‌صفحه واقعی در ابعاد گوشی کوچک (w320dp-h560dp) بدون حاشیه سیاه مصنوعی
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h560dp-xxhdpi")
    fun `reader test 19 - ReaderScreen measures real full-window small phone viewport and recalculates pagination compared to large screen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val sentence = "در شطرنج باید پیش از هر حرکت، تهدیدهای حریف و امنیت شاه خود را به دقت بررسی کنید تا از مات شدن ناگهانی جلوگیری شود."
        val longText = (1..8).joinToString(" ") { "گزاره $it: $sentence" }
        val slot = TranslationSlot(
            sectionId = "SEC_CH01_06_SUMMARY",
            orderedContentBlocks = listOf(
                SectionContentBlock.HeadingBlock("SUM_H1", textFa = "جمع‌بندی اصول فصل اول"),
                SectionContentBlock.ParagraphBlock("SUM_P1", textFa = longText),
                SectionContentBlock.ParagraphBlock("SUM_P2", textFa = longText)
            ),
            translationStatus = TranslationStatus.COMPLETE
        )
        viewModel.updateSectionTranslationSlot("SEC_CH01_06_SUMMARY", slot)
        viewModel.selectSection(5)

        // Render ReaderScreen full-screen inside the actual 320x560dp Robolectric phone window (NO artificial inner Box!)
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val fixedViewportBounds = composeTestRule.onNodeWithTag("reader_fixed_viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(
            "Reader width must match the full 320dp window width without artificial side margins",
            rootBounds.width,
            fixedViewportBounds.width,
            2f
        )

        val smallViewportWidth = viewModel.uiState.value.viewportWidthDp
        val smallViewportHeight = viewModel.uiState.value.viewportHeightDp
        val smallScreenPages = viewModel.uiState.value.currentPages.size

        assertTrue(
            "Measured content width ($smallViewportWidth dp) on 320dp phone must be in 280..320dp",
            smallViewportWidth in 280..320
        )
        assertTrue(
            "Measured content height ($smallViewportHeight dp) must be < full 560dp screen height because TopAppBar, Header, and Footer are excluded",
            smallViewportHeight in 260..480
        )
        saveReaderScreenshot("13_reader_small_screen_text_page.png")

        // Compare against large phone/tablet viewport dimensions (411dp x 740dp content area)
        viewModel.updateViewportSize(widthDp = 387, heightDp = 700)
        val largeViewportWidth = viewModel.uiState.value.viewportWidthDp
        val largeViewportHeight = viewModel.uiState.value.viewportHeightDp
        val largeScreenPages = viewModel.uiState.value.currentPages.size

        assertTrue("Large screen content width ($largeViewportWidth) must exceed small screen width ($smallViewportWidth)", largeViewportWidth > smallViewportWidth)
        assertTrue("Large screen content height ($largeViewportHeight) must exceed small screen height ($smallViewportHeight)", largeViewportHeight > smallViewportHeight)
        assertTrue(
            "Small 320x560dp phone ($smallScreenPages pages) must paginate into strictly more pages than large screen ($largeScreenPages pages)",
            smallScreenPages > largeScreenPages
        )
    }

    // =========================================================================
    // 20. تست فونت بزرگ (FontSizeScale.LARGE) و عدم برش یا تداخل با فوتر
    // =========================================================================
    @Test
    fun `reader test 20 - FontSizeScale LARGE paginates without text clipping or overlapping the footer`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val paragraphText = (1..7).joinToString(" ") {
            "در وضعیت فونت بزرگ، متن کتاب باید بدون بریدگی و بدون برخورد با فوتر پایین صفحه به صفحه بعد منتقل شود."
        }
        val slot = TranslationSlot(
            sectionId = "SEC_CH01_06_SUMMARY",
            orderedContentBlocks = listOf(
                SectionContentBlock.HeadingBlock("LG_H1", textFa = "بررسی اندازه قلم بزرگ در کتاب‌خوان"),
                SectionContentBlock.ParagraphBlock("LG_P1", textFa = paragraphText),
                SectionContentBlock.ParagraphBlock("LG_P2", textFa = paragraphText)
            ),
            translationStatus = TranslationStatus.COMPLETE
        )
        viewModel.updateSectionTranslationSlot("SEC_CH01_06_SUMMARY", slot)
        viewModel.selectSection(5)
        viewModel.setFontScale(FontSizeScale.LARGE)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val pages = viewModel.uiState.value.currentPages
        assertTrue("LARGE font must paginate long text into multiple pages", pages.size >= 3)

        val viewportBounds = composeTestRule.onNodeWithTag("reader_content_viewport").fetchSemanticsNode().boundsInRoot
        val footerBounds = composeTestRule.onNodeWithTag("reader_page_footer").fetchSemanticsNode().boundsInRoot

        // Verify on Page 1 and Page 2 that every visible content block fits strictly inside the viewport and above the footer
        for (pageIdx in 0..1) {
            viewModel.goToPage(pageIdx)
            composeTestRule.waitForIdle()
            val currentPage = viewModel.uiState.value.currentPage!!
            for (block in currentPage.orderedBlocks) {
                val blockBounds = composeTestRule.onNodeWithTag("content_block_${block.blockId}")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "Block ${block.blockId} bottom (${blockBounds.bottom}) in LARGE font must not exceed content viewport bottom (${viewportBounds.bottom})",
                    blockBounds.bottom <= viewportBounds.bottom + 1f
                )
                assertTrue(
                    "Block ${block.blockId} bottom (${blockBounds.bottom}) in LARGE font must stay above footer top (${footerBounds.top})",
                    blockBounds.bottom <= footerBounds.top + 1f
                )
            }
        }
        saveReaderScreenshot("14_reader_large_font_text_page.png")
        viewModel.setFontScale(FontSizeScale.NORMAL)
    }

    // =========================================================================
    // 21. تست صفحه دارای نمودار، توضیح و کنترل‌ها بدون بیرون‌زدگی از کادر صفحه
    // =========================================================================
    @Test
    fun `reader test 21 - diagram page with companion explanation and controls fits completely inside visible page bounds`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val repo = ChapterRepository(app)
        val checkmateSection = repo.getSections()[1]

        // Inject a real translated companion explanation before BD-029 (Scholar's Mate MOVE_REPLAY)
        val slotWithExplanation = TranslationSlot(
            sectionId = checkmateSection.sectionId,
            orderedContentBlocks = listOf(
                SectionContentBlock.ParagraphBlock(
                    blockId = "COMPANION_BD029",
                    textFa = "در این نمودار، مات ناپلئونی در چهار حرکت نمایش داده شده است؛ با دکمه‌های بازپخش مراحل را مرور کنید."
                ),
                SectionContentBlock.DiagramBlock(
                    blockId = "BLOCK_BD029",
                    diagramId = "BD-029"
                )
            ),
            translationStatus = TranslationStatus.COMPLETE
        )
        viewModel.updateSectionTranslationSlot(checkmateSection.sectionId, slotWithExplanation)
        viewModel.selectSection(1)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        val currentPage = viewModel.uiState.value.currentPage!!
        assertEquals(listOf("COMPANION_BD029", "BLOCK_BD029"), currentPage.orderedBlocks.map { it.blockId })

        val viewportBounds = composeTestRule.onNodeWithTag("reader_content_viewport").fetchSemanticsNode().boundsInRoot
        val footerBounds = composeTestRule.onNodeWithTag("reader_page_footer").fetchSemanticsNode().boundsInRoot
        val companionBounds = composeTestRule.onNodeWithTag("content_block_COMPANION_BD029").fetchSemanticsNode().boundsInRoot
        val diagramCardBounds = composeTestRule.onNodeWithTag("diagram_card_BD-029").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Companion text must be above diagram card",
            companionBounds.bottom <= diagramCardBounds.top + 1f
        )
        assertTrue(
            "Diagram card with PGN controls (${diagramCardBounds.bottom}) must stay inside content viewport (${viewportBounds.bottom})",
            diagramCardBounds.bottom <= viewportBounds.bottom + 1f
        )
        assertTrue(
            "Diagram card with PGN controls (${diagramCardBounds.bottom}) must not overlap footer (${footerBounds.top})",
            diagramCardBounds.bottom <= footerBounds.top + 1f
        )
    }

    // =========================================================================
    // 22. تست حفظ موقعیت مطالعه هنگام تغییر ابعاد صفحه یا تغییر اندازه فونت
    // =========================================================================
    @Test
    fun `reader test 22 - reading position anchor block is preserved when viewport dimensions or font scale change`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        val unit = "هر یک از این پاراگراف‌ها بخش مشخصی از مفاهیم فصل اول کتاب شطرنج را توضیح می‌دهد و باید در صفحه مربوطه قابل ردیابی باشد."
        val blocks = listOf(
            SectionContentBlock.ParagraphBlock("ANCHOR_P1", textFa = "$unit (۱) $unit $unit"),
            SectionContentBlock.ParagraphBlock("ANCHOR_P2", textFa = "$unit (۲) $unit $unit"),
            SectionContentBlock.ParagraphBlock("ANCHOR_P3", textFa = "$unit (۳) $unit $unit"),
            SectionContentBlock.DiagramBlock("ANCHOR_D028", diagramId = "BD-028"),
            SectionContentBlock.ParagraphBlock("ANCHOR_P4", textFa = "$unit (۴) $unit $unit")
        )
        viewModel.updateSectionTranslationSlot(
            sectionId = "SEC_CH01_02_CHECKMATE",
            updatedSlot = TranslationSlot(
                sectionId = "SEC_CH01_02_CHECKMATE",
                orderedContentBlocks = blocks,
                translationStatus = TranslationStatus.COMPLETE
            )
        )
        viewModel.selectSection(1)

        // Locate the page containing ANCHOR_P3 and navigate to it
        val pageWithP3 = viewModel.uiState.value.currentPages.indexOfFirst { page ->
            page.orderedBlocks.any { ReaderPaginator.originalBlockId(it.blockId) == "ANCHOR_P3" }
        }
        assertTrue("ANCHOR_P3 must exist in paginated pages", pageWithP3 >= 1)
        viewModel.goToPage(pageWithP3)

        // 1. Change font scale to LARGE -> currentPage must still contain ANCHOR_P3
        viewModel.setFontScale(FontSizeScale.LARGE)
        val activeIdsAfterLargeFont = viewModel.uiState.value.currentPage!!.orderedBlocks.map {
            ReaderPaginator.originalBlockId(it.blockId)
        }
        assertTrue(
            "After changing font scale to LARGE, active page must still contain ANCHOR_P3 (found $activeIdsAfterLargeFont)",
            "ANCHOR_P3" in activeIdsAfterLargeFont
        )

        // 2. Change viewport dimensions to a smaller phone screen -> currentPage must still contain ANCHOR_P3
        viewModel.updateViewportSize(widthDp = 300, heightDp = 420)
        val activeIdsAfterSmallViewport = viewModel.uiState.value.currentPage!!.orderedBlocks.map {
            ReaderPaginator.originalBlockId(it.blockId)
        }
        assertTrue(
            "After resizing viewport to 300x420, active page must still contain ANCHOR_P3 (found $activeIdsAfterSmallViewport)",
            "ANCHOR_P3" in activeIdsAfterSmallViewport
        )

        // 3. Navigate to diagram BD-028 page, then resize viewport to a large tablet and reset font scale -> BD-028 stays active
        val pageWithDiagram = viewModel.uiState.value.currentPages.indexOfFirst {
            it.primaryDiagramId == "BD-028"
        }
        viewModel.goToPage(pageWithDiagram)
        assertEquals("BD-028", viewModel.uiState.value.currentPage?.primaryDiagramId)

        viewModel.updateViewportSize(widthDp = 450, heightDp = 760)
        assertEquals(
            "After resizing viewport to 450x760, active page must still be on diagram BD-028",
            "BD-028",
            viewModel.uiState.value.currentPage?.primaryDiagramId
        )

        viewModel.setFontScale(FontSizeScale.NORMAL)
        assertEquals(
            "After changing font scale back to NORMAL, active page must still be on diagram BD-028",
            "BD-028",
            viewModel.uiState.value.currentPage?.primaryDiagramId
        )
    }

    // =========================================================================
    // 23. تست جریان ورود (SplashScreen -> IntroductionScreen -> SectionsListScreen -> ReaderScreen)، نمایش هر ۶ بخش و بازگشت به فهرست
    // =========================================================================
    @Test
    fun `reader test 23 - initial app launch flows Splash to Introduction to SectionsListScreen with all 6 selectable sections and TOC returns to list`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        // 1. Verify initial tab on launch is SPLASH
        assertEquals(
            "Initial screen upon launching the app must be AppTab.SPLASH",
            com.example.ui.AppTab.SPLASH,
            viewModel.uiState.value.currentTab
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify SplashScreen is displayed and does NOT auto-advance without user click
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()
        assertEquals(
            "SplashScreen must never auto-advance via timer without user click",
            com.example.ui.AppTab.SPLASH,
            viewModel.uiState.value.currentTab
        )

        // Click Start on SplashScreen -> transitions to IntroductionScreen
        composeTestRule.onNodeWithTag("splash_start_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            com.example.ui.AppTab.INTRODUCTION,
            viewModel.uiState.value.currentTab
        )
        composeTestRule.onNodeWithTag("introduction_screen").assertIsDisplayed()

        // Click Enter Book on IntroductionScreen -> transitions to SectionsListScreen
        composeTestRule.onNodeWithTag("intro_enter_book_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            com.example.ui.AppTab.SECTIONS_LIST,
            viewModel.uiState.value.currentTab
        )

        // 2. Verify SectionsListScreen is displayed and all 6 sections are visible and selectable
        composeTestRule.onNodeWithTag("sections_list_screen").assertIsDisplayed()
        val expectedTitles = listOf(
            "شروع فصل",
            "مات",
            "تسلیم",
            "اتمام زمان",
            "ترک بازی",
            "خلاصه"
        )
        expectedTitles.forEachIndexed { idx, titleFa ->
            composeTestRule.onNodeWithTag("section_item_$idx").assertIsDisplayed()
            composeTestRule.onNodeWithText(titleFa).assertIsDisplayed()
        }

        saveReaderScreenshot("01_sections_list_screen.png")

        // 3. Click each of the 6 sections in turn, verify it opens ReaderScreen for that exact section on Page 1,
        //    and verify the TOC button (nav_toc_btn) returns to SectionsListScreen (and NOT to SplashScreen)
        expectedTitles.forEachIndexed { idx, titleFa ->
            composeTestRule.onNodeWithTag("section_item_$idx").performClick()
            composeTestRule.waitForIdle()

            assertEquals(com.example.ui.AppTab.READER, viewModel.uiState.value.currentTab)
            assertEquals(idx, viewModel.uiState.value.currentSectionIndex)
            assertEquals(0, viewModel.uiState.value.currentPageIndex)
            composeTestRule.onNodeWithTag("reader_fixed_viewport").assertIsDisplayed()
            composeTestRule.onNodeWithText("فصل اول / بخش ${idx + 1} / $titleFa").assertIsDisplayed()

            // Return to SectionsListScreen using the top bar TOC button
            composeTestRule.onNodeWithTag("nav_toc_btn").performClick()
            composeTestRule.waitForIdle()

            assertEquals(com.example.ui.AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)
            composeTestRule.onNodeWithTag("sections_list_screen").assertIsDisplayed()
        }
    }

    // =========================================================================
    // 24. تست دکمه «ادامه مطالعه» (resume_reading_btn) و حفظ آخرین بخش و آخرین صفحه ذخیره‌شده پس از عبور از اسپلش و معرفی
    // =========================================================================
    @Test
    fun `reader test 24 - continue reading button in SectionsListScreen opens last saved section and page without resetting to page 1`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Navigate Splash -> Introduction -> SectionsListScreen
        composeTestRule.onNodeWithTag("splash_start_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("intro_enter_book_btn").performClick()
        composeTestRule.waitForIdle()

        // 1. Select Section 2 ("مات", index 1) and advance to BD-029 page
        composeTestRule.onNodeWithTag("section_item_1").performClick()
        composeTestRule.waitForIdle()
        val total = viewModel.uiState.value.currentPages.size
        val bd029Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-029" }
        viewModel.goToPage(bd029Idx)
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.uiState.value.currentSectionIndex)
        assertEquals(bd029Idx, viewModel.uiState.value.currentPageIndex)
        composeTestRule.onNodeWithText("صفحه ${bd029Idx + 1} از $total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagram_card_BD-029").assertIsDisplayed()

        // 2. Return to SectionsListScreen via nav_toc_btn
        composeTestRule.onNodeWithTag("nav_toc_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)

        // 3. Click "ادامه مطالعه" (resume_reading_btn) -> must return to Section 1, BD-029 page, NOT Page 1!
        composeTestRule.onNodeWithTag("resume_reading_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals(com.example.ui.AppTab.READER, viewModel.uiState.value.currentTab)
        assertEquals(1, viewModel.uiState.value.currentSectionIndex)
        assertEquals(
            "Continue Reading must preserve the last saved page index ($bd029Idx), NOT reset to 0",
            bd029Idx,
            viewModel.uiState.value.currentPageIndex
        )
        composeTestRule.onNodeWithText("صفحه ${bd029Idx + 1} از $total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diagram_card_BD-029").assertIsDisplayed()
    }

    // Helper that verifies all 7 board & piece geometry/visibility criteria on the active ReaderScreen
    private fun assertBoardAndAll32PiecesUnclippedAndAnimated(
        viewModel: ChapterViewModel,
        screenLabel: String,
        screenshotFileName: String
    ): Float {
        // Navigate to Section 0 (Opening -> BD-025 starting position with all 32 pieces)
        viewModel.selectSection(0)
        val bd025Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" }
        viewModel.goToPage(bd025Idx)
        composeTestRule.waitForIdle()

        // 1. Verify board container is strictly 1:1 square
        val boardBounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        assertTrue("Board width must be positive on $screenLabel", boardBounds.width > 100f)
        assertEquals(
            "Chessboard aspect ratio must be strictly 1:1 square on $screenLabel",
            boardBounds.width,
            boardBounds.height,
            1.5f
        )

        // 2, 3, 4, 5. Verify all 32 pieces in starting position exist, are visible, have white/black distinction,
        // and each piece symbol's bounds lie strictly inside its square's bounds (including rank 1 and rank 8 top/bottom)
        val whiteRanks = listOf(1, 2)
        val blackRanks = listOf(7, 8)
        val files = ('a'..'h').toList()
        var totalPiecesVerified = 0
        var e1PieceHeightPx = 0f

        for (rank in whiteRanks + blackRanks) {
            val expectedColorSide = if (rank in whiteRanks) "white" else "black"
            for (file in files) {
                val sq = "$file$rank"
                val squareBounds = composeTestRule.onNodeWithTag("square_$sq", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val symbolNode = composeTestRule.onNodeWithTag("piece_symbol_$sq", useUnmergedTree = true)
                    .fetchSemanticsNode()
                val symbolBounds = symbolNode.boundsInRoot

                // Verify color tag exists to distinguish white vs black pieces
                composeTestRule.onNodeWithTag("piece_color_${sq}_$expectedColorSide", useUnmergedTree = true)
                    .assertExists()

                // Verify piece symbol bounds are strictly inside square bounds on all 4 sides
                assertTrue(
                    "[$screenLabel] Piece symbol on $sq left (${symbolBounds.left}) must be >= square left (${squareBounds.left})",
                    symbolBounds.left >= squareBounds.left - 0.5f
                )
                assertTrue(
                    "[$screenLabel] Piece symbol on $sq right (${symbolBounds.right}) must be <= square right (${squareBounds.right})",
                    symbolBounds.right <= squareBounds.right + 0.5f
                )
                assertTrue(
                    "[$screenLabel] Piece symbol on $sq top (${symbolBounds.top}) must not clip above square top (${squareBounds.top})",
                    symbolBounds.top >= squareBounds.top - 0.5f
                )
                assertTrue(
                    "[$screenLabel] Piece symbol on $sq bottom (${symbolBounds.bottom}) must not clip below square bottom (${squareBounds.bottom})",
                    symbolBounds.bottom <= squareBounds.bottom + 0.5f
                )
                assertTrue(
                    "[$screenLabel] Piece symbol height (${symbolBounds.height}) on $sq must be smaller than square height (${squareBounds.height})",
                    symbolBounds.height > 4f && symbolBounds.height <= squareBounds.height
                )

                // Extra check for Rank 1 and Rank 8 top/bottom safety against board container
                if (rank == 1 || rank == 8) {
                    assertTrue(
                        "[$screenLabel] Rank $rank piece on $sq must be strictly inside board vertical bounds",
                        symbolBounds.top >= boardBounds.top && symbolBounds.bottom <= boardBounds.bottom
                    )
                }

                if (sq == "e1") {
                    e1PieceHeightPx = symbolBounds.height
                }
                totalPiecesVerified++
            }
        }
        assertEquals("All 32 starting pieces must be rendered and verified on $screenLabel", 32, totalPiecesVerified)

        // Capture full-screen screenshot of the 32-piece board at this screen width
        saveReaderScreenshot(screenshotFileName)

        // 7. Verify piece movement animation works at this screen width (Section 1 -> BD-029 Scholar's Mate)
        viewModel.selectSection(1)
        composeTestRule.waitForIdle()
        val bd029Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-029" }
        viewModel.goToPage(bd029Idx)
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        viewModel.stepPgnPrev("SCHOLARS_MATE_CANDIDATE")
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(110)

        val movingNode = composeTestRule.onNodeWithTag("moving_piece_primary", useUnmergedTree = true).fetchSemanticsNode()
        val movingSymbolBounds = composeTestRule.onNodeWithTag("piece_symbol_moving_primary", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "[$screenLabel] Animated piece symbol must stay within moving square bounds during flight",
            movingSymbolBounds.top >= movingNode.boundsInRoot.top - 0.5f &&
                movingSymbolBounds.bottom <= movingNode.boundsInRoot.bottom + 0.5f
        )

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("moving_piece_primary").assertDoesNotExist()

        return e1PieceHeightPx
    }

    // =========================================================================
    // 25. تست کامل تخته و ۳۲ مهره در عرض صفحه کوچک (۳۱۰dp در بازه ۳۰۰ تا ۳۲۰dp)
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w310dp-h660dp-xxhdpi")
    fun `reader test 25 - chessboard and all 32 pieces render unclipped in small screen width 310dp`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        assertBoardAndAll32PiecesUnclippedAndAnimated(
            viewModel = viewModel,
            screenLabel = "SmallWidth_310dp",
            screenshotFileName = "05_board_small_width_310dp.png"
        )
    }

    // =========================================================================
    // 26. تست کامل تخته و ۳۲ مهره در عرض صفحه معمولی (۳۷۰dp در بازه ۳۶۰ تا ۳۸۰dp)
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w370dp-h780dp-xxhdpi")
    fun `reader test 26 - chessboard and all 32 pieces render unclipped in normal screen width 370dp`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        assertBoardAndAll32PiecesUnclippedAndAnimated(
            viewModel = viewModel,
            screenLabel = "NormalWidth_370dp",
            screenshotFileName = "06_board_normal_width_370dp.png"
        )
    }

    // =========================================================================
    // 27. تست کامل تخته و ۳۲ مهره در عرض بزرگ (۴۱۱dp در بازه ۴۰۰ تا ۴۲۰dp) و کوچک شدن متناسب نماد مهره‌ها
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `reader test 27 - chessboard and all 32 pieces render unclipped in large width 411dp and piece symbols scale proportionally with board width`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        var testWidthDp by mutableStateOf(411.dp)

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                Box(modifier = Modifier.size(width = testWidthDp, height = 891.dp)) {
                    ReaderScreen(viewModel = viewModel, state = state)
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify full-screen 411dp width and capture 07_board_large_width_411dp.png
        val pieceHeightAt411 = assertBoardAndAll32PiecesUnclippedAndAnimated(
            viewModel = viewModel,
            screenLabel = "LargeWidth_411dp",
            screenshotFileName = "07_board_large_width_411dp.png"
        )

        // 2. Measure piece symbol height at 370dp width
        viewModel.selectSection(0)
        viewModel.goToPage(viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" })
        testWidthDp = 370.dp
        composeTestRule.waitForIdle()
        viewModel.goToPage(viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" })
        composeTestRule.waitForIdle()
        val pieceHeightAt370 = composeTestRule.onNodeWithTag("piece_symbol_e1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height

        // 3. Measure piece symbol height at 310dp width
        testWidthDp = 310.dp
        composeTestRule.waitForIdle()
        viewModel.goToPage(viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" })
        composeTestRule.waitForIdle()
        val pieceHeightAt310 = composeTestRule.onNodeWithTag("piece_symbol_e1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height

        assertTrue(
            "Piece symbol height at 411dp ($pieceHeightAt411 px) must be strictly larger than at 370dp ($pieceHeightAt370 px)",
            pieceHeightAt411 > pieceHeightAt370
        )
        assertTrue(
            "Piece symbol height at 370dp ($pieceHeightAt370 px) must be strictly larger than at 310dp ($pieceHeightAt310 px)",
            pieceHeightAt370 > pieceHeightAt310
        )
    }

    // =========================================================================
    // 28. تست عدم رزرو کاذب کنترل‌های PGN در نمودارهای ثابت و عدم محدودیت ۲۰۰dp در تمرین BD-032
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `reader test 28 - static diagrams use full available card width and BD-032 is not artificially capped at 200dp`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)
        val density = app.resources.displayMetrics.density

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(viewModel = viewModel, state = state)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Static diagram BD-025 (Section 0): board must expand well beyond the old 215dp cap
        viewModel.selectSection(0)
        val bd025Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-025" }
        viewModel.goToPage(bd025Idx)
        composeTestRule.waitForIdle()
        val staticBoardBounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        val staticBoardDp = staticBoardBounds.width / density
        assertTrue(
            "Static diagram board width ($staticBoardDp dp) on 411dp screen must exceed 300dp (not capped at 215dp)",
            staticBoardDp > 300f
        )

        // 2. Interactive exercise BD-032 (Section 3): board must expand well beyond the old 200dp cap
        viewModel.selectSection(3)
        val bd032Idx = viewModel.uiState.value.currentPages.indexOfFirst { it.primaryDiagramId == "BD-032" }
        viewModel.goToPage(bd032Idx)
        composeTestRule.waitForIdle()
        val bd032BoardBounds = composeTestRule.onNodeWithTag("chessboard_container").fetchSemanticsNode().boundsInRoot
        val bd032BoardDp = bd032BoardBounds.width / density
        val footerBounds = composeTestRule.onNodeWithTag("reader_page_footer").fetchSemanticsNode().boundsInRoot
        val cardBounds = composeTestRule.onNodeWithTag("diagram_card_BD-032").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "BD-032 exercise board width ($bd032BoardDp dp) on 411dp screen must exceed 280dp (not capped at 200dp)",
            bd032BoardDp > 280f
        )
        assertTrue(
            "BD-032 card bottom (${cardBounds.bottom}) must stay above footer top (${footerBounds.top})",
            cardBounds.bottom <= footerBounds.top + 1f
        )
    }

    // Helper to assert all SplashScreen and IntroductionScreen requirements and capture screenshots
    private fun verifySplashAndIntroScreensOnCurrentConfig(
        viewModel: ChapterViewModel,
        screenLabel: String,
        splashScreenshotName: String,
        introScreenshotName: String,
        isPortrait: Boolean = true
    ) {
        viewModel.setTab(com.example.ui.AppTab.SPLASH)
        composeTestRule.waitForIdle()

        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_background_image").assertIsDisplayed()

        // 1. Verify all 6 required Persian texts exist, are displayed, and lie completely inside root screen bounds
        val splashTextPairs = listOf(
            "splash_author_name" to "لوی روزمن",
            "splash_author_alias" to "معروف به گاتهام‌چس",
            "splash_main_title" to "چگونه در شطرنج برنده شویم",
            "splash_subtitle" to "راهنمای جامع برای مبتدیان و فراتر از آن",
            "splash_youtube_badge" to "مدرس شمارهٔ ۱ شطرنج در یوتیوب",
            "splash_start_btn" to "برای شروع لمس کنید"
        )
        splashTextPairs.forEach { (tag, expectedText) ->
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
            node.assertIsDisplayed()
            composeTestRule.onNodeWithText(expectedText, useUnmergedTree = true).assertIsDisplayed()
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue("[$screenLabel] $tag must be inside screen horizontally", b.left >= -1f && b.right <= rootBounds.width + 1f)
            assertTrue("[$screenLabel] $tag must be inside screen vertically", b.top >= -1f && b.bottom <= rootBounds.height + 1f)
        }

        // Verify visual hierarchy & non-overlap on Portrait
        if (isPortrait) {
            val artBounds = composeTestRule.onNodeWithTag("splash_artwork_viewport").fetchSemanticsNode().boundsInRoot
            val authorBounds = composeTestRule.onNodeWithTag("splash_author_name", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val aliasBounds = composeTestRule.onNodeWithTag("splash_author_alias", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val mainTitleBounds = composeTestRule.onNodeWithTag("splash_main_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val subtitleBounds = composeTestRule.onNodeWithTag("splash_subtitle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val badgeBounds = composeTestRule.onNodeWithTag("splash_youtube_badge_container", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val startBtnBounds = composeTestRule.onNodeWithTag("splash_start_btn").fetchSemanticsNode().boundsInRoot

            // Alias "معروف به گاتهام‌چس" must be smaller in height than author name "لوی روزمن"
            assertTrue(
                "[$screenLabel] Alias height (${aliasBounds.height}) must be smaller than author name height (${authorBounds.height})",
                aliasBounds.height < authorBounds.height
            )

            // Compute where the King and chessboard sit inside artBounds (exact 1:1 1096x1435 geometry)
            val imgAspect = 1096f / 1435f
            val renderedImgH = minOf(artBounds.width / imgAspect, (artBounds.height * 0.50f) / 0.5589f)
            val kingTopYPx = artBounds.bottom - renderedImgH * 0.5589f
            val horizonYPx = artBounds.bottom - renderedImgH * 0.2746f
            val kingRightEdgePx = artBounds.width * 0.62f

            // Main title must stay in the upper section strictly above the King's top cross
            assertTrue(
                "[$screenLabel] Main title bottom (${mainTitleBounds.bottom}) must stay above King top ($kingTopYPx)",
                mainTitleBounds.bottom <= kingTopYPx + 2f
            )

            // Subtitle must be to the right of the King (x >= 62%) and above the chessboard horizon
            assertTrue(
                "[$screenLabel] Subtitle left (${subtitleBounds.left}) must stay to the right of the King ($kingRightEdgePx)",
                subtitleBounds.left >= kingRightEdgePx - 4f
            )
            assertTrue(
                "[$screenLabel] Subtitle bottom (${subtitleBounds.bottom}) must stay above the chessboard horizon ($horizonYPx)",
                subtitleBounds.bottom <= horizonYPx + 4f
            )

            // Badge must stay to the right of the King
            assertTrue(
                "[$screenLabel] Badge left (${badgeBounds.left}) must stay to the right of the King ($kingRightEdgePx)",
                badgeBounds.left >= kingRightEdgePx - 4f
            )

            // Start button must sit below the artwork viewport so it never covers the King or chessboard
            assertTrue(
                "[$screenLabel] Start button top (${startBtnBounds.top}) must be below artwork viewport bottom (${artBounds.bottom})",
                startBtnBounds.top >= artBounds.bottom - 1f
            )
        }

        saveReaderScreenshot(splashScreenshotName)

        // 2. Click Start button to navigate to IntroductionScreen
        composeTestRule.onNodeWithTag("splash_start_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.ui.AppTab.INTRODUCTION, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("introduction_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("intro_background_image").assertIsDisplayed()

        // Verify all 9 required Persian texts on IntroductionScreen exist, are displayed, and lie inside screen bounds
        val introTextPairs = listOf(
            "intro_main_title" to "چگونه در شطرنج برنده شویم",
            "intro_subtitle" to "راهنمای جامع برای مبتدیان و فراتر از آن",
            "intro_author_name" to "لوی روزمن",
            "intro_welcome_title" to "به دنیای شطرنج خوش آمدید",
            "intro_welcome_body" to "با این راهنمای گام‌به‌گام، شطرنج را یاد بگیرید و مهارت خود را با مثال‌ها و تمرین‌های تعاملی تقویت کنید.",
            "intro_guide_title" to "چطور از برنامه استفاده کنید؟",
            "intro_guide_step_1" to "۱. بخش موردنظر را از فهرست انتخاب کنید.",
            "intro_guide_step_2" to "۲. توضیحات و حرکت مهره‌ها را دنبال کنید.",
            "intro_guide_step_3" to "۳. تمرین‌ها را حل کنید و پاسخ خود را بررسی کنید.",
            "intro_enter_book_btn" to "ورود به کتاب و شروع یادگیری"
        )
        introTextPairs.forEach { (tag, expectedText) ->
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)
            node.assertIsDisplayed()
            composeTestRule.onNodeWithText(expectedText, useUnmergedTree = true).assertIsDisplayed()
            val b = node.fetchSemanticsNode().boundsInRoot
            assertTrue("[$screenLabel] Intro $tag must be inside screen horizontally", b.left >= -1f && b.right <= rootBounds.width + 1f)
            assertTrue("[$screenLabel] Intro $tag must be inside screen vertically", b.top >= -1f && b.bottom <= rootBounds.height + 1f)
        }

        // Verify main title is in the light section and subtitle/author/guide/button are in the brown section
        val lightSectionBounds = composeTestRule.onNodeWithTag("intro_light_section").fetchSemanticsNode().boundsInRoot
        val brownSectionBounds = composeTestRule.onNodeWithTag("intro_brown_section").fetchSemanticsNode().boundsInRoot
        val introTitleBounds = composeTestRule.onNodeWithTag("intro_main_title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val introSubBounds = composeTestRule.onNodeWithTag("intro_subtitle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val introBtnBounds = composeTestRule.onNodeWithTag("intro_enter_book_btn").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "[$screenLabel] Intro main title must reside inside the top light section",
            introTitleBounds.bottom <= lightSectionBounds.bottom + 1f
        )
        assertTrue(
            "[$screenLabel] Intro subtitle must reside inside the bottom brown section",
            introSubBounds.top >= brownSectionBounds.top - 1f
        )
        assertTrue(
            "[$screenLabel] Intro Enter Book button must be at the bottom inside the brown section",
            introBtnBounds.bottom <= brownSectionBounds.bottom + 1f && introBtnBounds.top > introSubBounds.bottom
        )

        saveReaderScreenshot(introScreenshotName)
    }

    // =========================================================================
    // 29. تست کامل صفحات اسپلش و معرفی در عرض صفحه کوچک (۳۱۰dp)
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w310dp-h660dp-xxhdpi")
    fun `reader test 29 - SplashScreen and IntroductionScreen render all texts and preserve background in small width 310dp`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        verifySplashAndIntroScreensOnCurrentConfig(
            viewModel = viewModel,
            screenLabel = "SplashIntro_Small_310dp",
            splashScreenshotName = "00a_splash_small_310dp.png",
            introScreenshotName = "00b_intro_small_310dp.png"
        )
    }

    // =========================================================================
    // 30. تست کامل صفحات اسپلش و معرفی در عرض صفحه معمولی (۳۷۰dp)
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w370dp-h780dp-xxhdpi")
    fun `reader test 30 - SplashScreen and IntroductionScreen render all texts and preserve background in normal width 370dp`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        verifySplashAndIntroScreensOnCurrentConfig(
            viewModel = viewModel,
            screenLabel = "SplashIntro_Normal_370dp",
            splashScreenshotName = "00c_splash_normal_370dp.png",
            introScreenshotName = "00d_intro_normal_370dp.png"
        )
    }

    // =========================================================================
    // 31. تست کامل صفحات اسپلش و معرفی در عرض صفحه بزرگ (۴۱۱dp)
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `reader test 31 - SplashScreen and IntroductionScreen render all texts and preserve background in large width 411dp`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        verifySplashAndIntroScreensOnCurrentConfig(
            viewModel = viewModel,
            screenLabel = "SplashIntro_Large_411dp",
            splashScreenshotName = "00e_splash_large_411dp.png",
            introScreenshotName = "00f_intro_large_411dp.png"
        )
    }

    // =========================================================================
    // 32. تست حالت افقی (Landscape) و صحت ابعاد اصلی تصاویر ۰۱ و ۰۲ بدون تغییر
    // =========================================================================
    @Test
    @Config(sdk = [36], qualifiers = "w660dp-h360dp-xxhdpi")
    fun `reader test 32 - SplashScreen and IntroductionScreen support Landscape without clipping and verify unmodified 01 and 02 assets`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChapterViewModel(app)

        // Verify unmodified 01.png and 02.png dimensions (1096 x 1435) in drawable-nodpi
        val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        val bmp01 = android.graphics.BitmapFactory.decodeResource(app.resources, R.drawable.bg_splash_01, opts)
        val bmp02 = android.graphics.BitmapFactory.decodeResource(app.resources, R.drawable.bg_intro_02, opts)
        assertNotNull("bg_splash_01 resource must exist", bmp01)
        assertNotNull("bg_intro_02 resource must exist", bmp02)
        assertEquals("01.png original width must be preserved (1096px)", 1096, bmp01.width)
        assertEquals("01.png original height must be preserved (1435px)", 1435, bmp01.height)
        assertEquals("02.png original width must be preserved (1096px)", 1096, bmp02.width)
        assertEquals("02.png original height must be preserved (1435px)", 1435, bmp02.height)

        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify SplashScreen in Landscape
        composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_main_title", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_start_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_start_btn").performClick()
        composeTestRule.waitForIdle()

        // Verify IntroductionScreen in Landscape: even with short height, the Enter Book button remains visible and clickable
        assertEquals(com.example.ui.AppTab.INTRODUCTION, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("introduction_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("intro_main_title", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("intro_enter_book_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("intro_enter_book_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals(com.example.ui.AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("sections_list_screen").assertIsDisplayed()
    }
}
