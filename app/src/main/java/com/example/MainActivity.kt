package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.ChapterViewModel
import com.example.ui.screens.AuditReportScreen
import com.example.ui.screens.ChaptersListScreen
import com.example.ui.screens.IntroductionScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SectionsListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChessAppMain()
                }
            }
        }
    }
}

@Composable
fun ChessAppMain(viewModel: ChapterViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    // Back navigation:
    // - From AUDIT_REPORT -> returns to SETTINGS (via closeAuditReport)
    // - From SETTINGS -> returns to previous screen (via closeSettings)
    // - From READER -> returns to SECTIONS_LIST
    // - From SECTIONS_LIST (when multiple chapters are active) -> returns to CHAPTERS_LIST
    // - From CHAPTERS_LIST -> returns to INTRODUCTION
    // - From INTRODUCTION -> returns to SPLASH
    BackHandler(
        enabled = state.currentTab == AppTab.READER ||
            state.currentTab == AppTab.AUDIT_REPORT ||
            state.currentTab == AppTab.SETTINGS ||
            (state.currentTab == AppTab.SECTIONS_LIST && state.hasMultipleChapters) ||
            state.currentTab == AppTab.CHAPTERS_LIST ||
            state.currentTab == AppTab.INTRODUCTION
    ) {
        when (state.currentTab) {
            AppTab.AUDIT_REPORT -> viewModel.closeAuditReport()
            AppTab.SETTINGS -> viewModel.closeSettings()
            AppTab.READER -> viewModel.setTab(AppTab.SECTIONS_LIST)
            AppTab.SECTIONS_LIST -> if (state.hasMultipleChapters) viewModel.setTab(AppTab.CHAPTERS_LIST)
            AppTab.CHAPTERS_LIST -> viewModel.setTab(AppTab.INTRODUCTION)
            AppTab.INTRODUCTION -> viewModel.setTab(AppTab.SPLASH)
            else -> Unit
        }
    }

    when (state.currentTab) {
        AppTab.SPLASH -> SplashScreen(
            onStartClicked = { viewModel.openIntroduction() }
        )
        AppTab.INTRODUCTION -> IntroductionScreen(
            onEnterBookClicked = { viewModel.openBookFromIntroduction() }
        )
        AppTab.CHAPTERS_LIST -> ChaptersListScreen(
            state = state,
            onSelectChapter = { viewModel.selectChapter(it) },
            onBackToIntroduction = { viewModel.openIntroduction() },
            onStartFrontMatter = { viewModel.openFrontMatter(it) },
            onResumeFrontMatter = { viewModel.resumeFrontMatter() },
            onNavigateToPdfPage = { pdfPage, chapterId -> viewModel.navigateToSourcePdfPage(pdfPage, chapterId) },
            onDismissNavigationNotice = { viewModel.dismissNavigationNotice() },
            onOpenSettings = { viewModel.openSettings() }
        )
        AppTab.SECTIONS_LIST -> SectionsListScreen(
            viewModel = viewModel,
            state = state,
            onOpenSettings = { viewModel.openSettings() }
        )
        AppTab.READER -> ReaderScreen(viewModel = viewModel, state = state)
        AppTab.SETTINGS -> SettingsScreen(
            onBack = { viewModel.closeSettings() },
            onOpenAuditReport = { viewModel.openAuditReport() }
        )
        AppTab.AUDIT_REPORT -> AuditReportScreen(viewModel = viewModel, state = state)
    }
}
