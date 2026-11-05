package com.example

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.screens.SectionsListScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class Stage24ChapterScreenCleanupTest {

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
    fun test01_bookInfoCardAndAuditButtonRemoved_onlyResumeReadingButtonRemains() {
        // Navigate to Chapter 1 sections screen
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

        // 1. Audit button ("view_audit_btn") must NOT exist in this screen
        composeTestRule.onNodeWithTag("view_audit_btn").assertDoesNotExist()

        // 2. The old large info card container must NOT exist
        composeTestRule.onNodeWithTag("book_info_card").assertDoesNotExist()

        // 3. Only the single "resume_reading_btn" must exist and be visible and clickable
        val resumeBtn = composeTestRule.onNodeWithTag("resume_reading_btn")
        resumeBtn.assertIsDisplayed()
        resumeBtn.assertHasClickAction()
        resumeBtn.assertTextContains("ادامه مطالعه")
    }

    @Test
    fun test02_pdfPageNumbersIndexCardRemoved() {
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

        // Green area #2: "chapter_pdf_pages_index_card" and individual chips (40..50) must NOT exist
        composeTestRule.onNodeWithTag("chapter_pdf_pages_index_card").assertDoesNotExist()
        for (page in 40..50) {
            composeTestRule.onNodeWithTag("chapter_pdf_page_chip_$page").assertDoesNotExist()
        }
    }

    @Test
    fun test03_chapterSectionsListRemoved() {
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

        // Purple area: Section cards (section_item_0 .. section_item_5) must NOT exist
        for (i in 0..5) {
            composeTestRule.onNodeWithTag("section_item_$i").assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag("section_item_start").assertDoesNotExist()
    }

    @Test
    fun test04_unifiedTableOfContentsRemainsIntactAndFunctional() {
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

        // Critical retention: Unified TOC header and chapter list remain
        composeTestRule.onNodeWithTag("unified_book_toc_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("front_matter_region_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toc_part_divider_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chapter_card_CH01").assertIsDisplayed()
    }

    @Test
    fun test05_resumeReadingButtonNavigatesToSavedReadingPosition() {
        viewModel.openSectionsList()

        // Simulate saved reading position at page 45
        viewModel.navigateToSourcePdfPage(45, "CH01")
        // Return to sections list
        viewModel.openSectionsList()
        assertEquals(AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)

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

        // Click resume reading button
        composeTestRule.onNodeWithTag("resume_reading_btn").performClick()
        composeTestRule.waitForIdle()

        val state = viewModel.uiState.value
        assertEquals(AppTab.READER, state.currentTab)
        assertEquals(45, state.currentPages[state.currentPageIndex].sourcePdfPage)
    }

    @Test
    fun test06_underlyingContentAndNavigationDataPreserved() {
        // Ensure state sections and reader pagination are not damaged
        val state = viewModel.uiState.value
        assertTrue("Underlying sections data must still be present", state.sections.isNotEmpty())
        assertEquals(6, state.sections.size)
        assertEquals("شروع فصل", state.sections[0].titleFa)
        assertEquals("خلاصه", state.sections.last().titleFa)
    }
}
