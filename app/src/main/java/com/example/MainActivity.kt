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
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SectionsListScreen
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

    // Handle back button navigation
    BackHandler(enabled = state.currentTab != AppTab.READER) {
        viewModel.setTab(AppTab.READER)
    }

    when (state.currentTab) {
        AppTab.READER -> ReaderScreen(viewModel = viewModel, state = state)
        AppTab.SECTIONS_LIST -> SectionsListScreen(viewModel = viewModel, state = state)
        AppTab.AUDIT_REPORT -> AuditReportScreen(viewModel = viewModel, state = state)
    }
}
