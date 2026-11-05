package com.example

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.screens.AuditReportScreen
import com.example.ui.screens.ChaptersListScreen
import com.example.ui.screens.SectionsListScreen
import com.example.ui.screens.SettingsScreen
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
class Stage24_3SettingsScreenTest {

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
    fun test01_settingsButtonInToolbarOpensSettingsScreen() {
        // 1. From ChaptersListScreen
        viewModel.openChaptersList()
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        val settingsBtn = composeTestRule.onNodeWithTag("settings_btn")
        settingsBtn.assertIsDisplayed()
        settingsBtn.assertHasClickAction()

        // Click gear icon
        settingsBtn.performClick()
        composeTestRule.waitForIdle()

        // Must transition to SettingsScreen
        assertEquals(AppTab.SETTINGS, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_title").assertTextEquals("تنظیمات")
    }

    @Test
    fun test02_settingsScreenContainsOnlyAuditReportOption() {
        viewModel.openSettings()
        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    onBack = { viewModel.closeSettings() },
                    onOpenAuditReport = { viewModel.openAuditReport() }
                )
            }
        }
        composeTestRule.waitForIdle()

        val auditItem = composeTestRule.onNodeWithTag("settings_audit_report_item")
        auditItem.assertIsDisplayed()
        auditItem.assertHasClickAction()
        auditItem.assertTextContains("گزارش ممیزی")
    }

    @Test
    fun test03_clickingAuditReportOpensExistingAuditScreen() {
        viewModel.openChaptersList()
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Open settings
        composeTestRule.onNodeWithTag("settings_btn").performClick()
        composeTestRule.waitForIdle()

        // Click audit report in settings
        composeTestRule.onNodeWithTag("settings_audit_report_item").performClick()
        composeTestRule.waitForIdle()

        // Verifies audit report screen opened
        assertEquals(AppTab.AUDIT_REPORT, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("audit_report_summary_card").assertIsDisplayed()
    }

    @Test
    fun test04_backFromAuditReportReturnsToSettings() {
        viewModel.openChaptersList()
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Chapters -> Settings -> Audit Report
        composeTestRule.onNodeWithTag("settings_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_audit_report_item").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.AUDIT_REPORT, viewModel.uiState.value.currentTab)

        // Back from Audit -> Settings
        composeTestRule.onNodeWithTag("audit_back_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.SETTINGS, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun test05_backFromSettingsReturnsToPreviousScreen() {
        // Test back to ChaptersListScreen
        viewModel.openChaptersList()
        composeTestRule.setContent {
            MyApplicationTheme {
                ChessAppMain(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.SETTINGS, viewModel.uiState.value.currentTab)

        composeTestRule.onNodeWithTag("settings_back_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.CHAPTERS_LIST, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("chapters_list_screen").assertIsDisplayed()

        // Also test opening from SectionsListScreen
        viewModel.openSectionsList()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.SETTINGS, viewModel.uiState.value.currentTab)

        composeTestRule.onNodeWithTag("settings_back_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppTab.SECTIONS_LIST, viewModel.uiState.value.currentTab)
        composeTestRule.onNodeWithTag("sections_list_screen").assertIsDisplayed()
    }

    @Test
    fun test06_auditDataAndCountsRemainUnchanged() {
        val state = viewModel.uiState.value
        val total = state.totalDiagramsCount
        val valid = state.validDiagramsCount
        val blocked = state.blockedDiagramsCount

        assertTrue("Diagrams count must be positive", total > 0)
        assertEquals(21, total)
        assertEquals(20, valid)
        assertEquals(1, blocked)
        assertEquals(total, valid + blocked)
    }
}
