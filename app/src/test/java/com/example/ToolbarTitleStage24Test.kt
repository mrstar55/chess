package com.example

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.screens.BookTocNavigationHelper
import com.example.ui.screens.ChaptersListScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SectionsListScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ToolbarTitleStage24Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: ChapterViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = ChapterViewModel(app)
    }

    @Test
    fun test01_mainChaptersListToolbarTitleIsOnlyAmoozeshShatranj() {
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
                    onNavigateToPdfPage = { page, chId -> viewModel.navigateToSourcePdfPage(page, chId) }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Requirement 1: In the main book TOC, title must be exclusively "آموزش شطرنج" without any chapter number
        val titleNode = composeTestRule.onNodeWithTag("chapters_list_title")
        titleNode.assertIsDisplayed()
        titleNode.assertTextEquals("آموزش شطرنج")
    }

    @Test
    fun test02_readingFrontMatterToolbarTitleIsAmoozeshShatranjPishdaramad() {
        // Requirement 2: When user is reading front matter (pages 11..36), title is "آموزش شطرنج — پیش‌درآمد"
        viewModel.navigateToSourcePdfPage(11, null)
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(
                    viewModel = viewModel,
                    state = state
                )
            }
        }
        composeTestRule.waitForIdle()

        val readerTitle = composeTestRule.onNodeWithTag("reader_toolbar_title")
        readerTitle.assertIsDisplayed()
        readerTitle.assertTextEquals("آموزش شطرنج — پیش‌درآمد")

        // Also test page 36
        viewModel.navigateToSourcePdfPage(36, null)
        composeTestRule.waitForIdle()
        readerTitle.assertIsDisplayed()
        readerTitle.assertTextEquals("آموزش شطرنج — پیش‌درآمد")
    }

    @Test
    fun test03_chapter1OnPage39AndPage40ShowsChapter1ToolbarTitle() {
        // Requirement 3 & 4: Inside Chapter 1 (page 39 and page 40+), title must show Chapter 1
        viewModel.navigateToSourcePdfPage(39, null)
        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                ReaderScreen(
                    viewModel = viewModel,
                    state = state
                )
            }
        }
        composeTestRule.waitForIdle()

        val readerTitle = composeTestRule.onNodeWithTag("reader_toolbar_title")
        readerTitle.assertIsDisplayed()
        readerTitle.assertTextEquals("آموزش شطرنج — فصل اول")

        // Page 40 in Chapter 1
        viewModel.navigateToSourcePdfPage(40, "CH01")
        composeTestRule.waitForIdle()
        readerTitle.assertIsDisplayed()
        readerTitle.assertTextEquals("آموزش شطرنج — فصل اول")
    }

    @Test
    fun test04_chapter2AndChapter3ToolbarTitlesAreDynamic() {
        // Requirement 4: The chapter label is dynamically derived, never fixed/hardcoded
        assertEquals("فصل اول", BookTocNavigationHelper.chapterIdToLabelFa("CH01"))
        assertEquals("فصل دوم", BookTocNavigationHelper.chapterIdToLabelFa("CH02"))
        assertEquals("فصل سوم", BookTocNavigationHelper.chapterIdToLabelFa("CH03"))
        assertEquals("فصل چهارم", BookTocNavigationHelper.chapterIdToLabelFa("CH04"))

        val titleCh02 = BookTocNavigationHelper.resolveToolbarTitle(
            isMainToc = false,
            isReadingFrontMatter = false,
            activePdfPage = 52,
            activeChapterId = "CH02",
            activeChapterShortTitleFa = ""
        )
        assertEquals("آموزش شطرنج — فصل دوم", titleCh02)

        val titleCh03 = BookTocNavigationHelper.resolveToolbarTitle(
            isMainToc = false,
            isReadingFrontMatter = false,
            activePdfPage = 86,
            activeChapterId = "CH03",
            activeChapterShortTitleFa = ""
        )
        assertEquals("آموزش شطرنج — فصل سوم", titleCh03)

        // SectionsListScreen with CH01
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
        composeTestRule.onNodeWithTag("sections_list_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sections_list_title").assertTextEquals("آموزش شطرنج — فصل اول")
    }

    @Test
    fun test05_backFromInsideChapterToMainTocRestoresTitleToAmoozeshShatranj() {
        // Requirement 5: Returning from inside a chapter to the main TOC restores title to "آموزش شطرنج"
        viewModel.openSectionsList()
        var onBackCalled = false

        composeTestRule.setContent {
            val state by viewModel.uiState.collectAsState()
            MyApplicationTheme {
                if (state.currentTab == AppTab.SECTIONS_LIST) {
                    SectionsListScreen(
                        viewModel = viewModel,
                        state = state
                    )
                } else if (state.currentTab == AppTab.CHAPTERS_LIST) {
                    ChaptersListScreen(
                        state = state,
                        onSelectChapter = { viewModel.selectChapter(it) },
                        onBackToIntroduction = { onBackCalled = true },
                        onStartFrontMatter = { viewModel.openFrontMatter(it) },
                        onResumeFrontMatter = { viewModel.resumeFrontMatter() },
                        onNavigateToPdfPage = { page, chId -> viewModel.navigateToSourcePdfPage(page, chId) }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Inside Chapter 1 Sections:
        composeTestRule.onNodeWithTag("sections_list_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sections_list_title").assertTextEquals("آموزش شطرنج — فصل اول")

        // Click back button to return to main chapters list:
        composeTestRule.onNodeWithTag("back_to_chapters_btn").performClick()
        composeTestRule.waitForIdle()

        assertEquals(AppTab.CHAPTERS_LIST, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("chapters_list_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chapters_list_title").assertTextEquals("آموزش شطرنج")
    }
}
