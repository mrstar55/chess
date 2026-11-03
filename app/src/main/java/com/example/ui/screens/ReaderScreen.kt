package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.NarrationStatus
import com.example.audio.NarrationUiState
import com.example.data.FontSizeScale
import com.example.model.AuditStatus
import com.example.model.DiagramItem
import com.example.model.ReaderPage
import com.example.model.ReaderPageType
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import com.example.ui.AppTab
import com.example.ui.ChapterUiState
import com.example.ui.ChapterViewModel
import com.example.ui.components.AuditBadge
import com.example.ui.components.AudioSettingsDialog
import com.example.ui.components.Bd032ExerciseView
import com.example.ui.components.ChessBoardView
import com.example.ui.components.PersistentMoveDescriptionBar
import com.example.ui.components.PgnControlsView
import com.example.ui.theme.AppIcons
import com.example.ui.theme.ReaderDesignTokens
import com.example.ui.theme.SemanticBadgeCategory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ChapterViewModel,
    state: ChapterUiState,
    modifier: Modifier = Modifier
) {
    var showFontMenu by remember { mutableStateOf(false) }
    val currentSection = state.sections.getOrNull(state.currentSectionIndex)
    val fontMultiplier = state.fontScale.scale
    val darkTheme = isSystemInDarkTheme()

    // Enforce RTL for Persian book reader chrome
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (state.showAudioSettingsDialog) {
            AudioSettingsDialog(
                settings = state.audioSettings,
                onMasterToggled = { viewModel.setMasterSoundEnabled(it) },
                onVolumeChanged = { viewModel.setSoundVolumePercent(it) },
                onMoveSoundToggled = { viewModel.setMoveSoundEnabled(it) },
                onCaptureSoundToggled = { viewModel.setCaptureSoundEnabled(it) },
                onSpecialMoveSoundToggled = { viewModel.setSpecialMoveSoundEnabled(it) },
                onCheckMateSoundToggled = { viewModel.setCheckMateSoundEnabled(it) },
                onExerciseFeedbackSoundToggled = { viewModel.setExerciseFeedbackSoundEnabled(it) },
                onPlayTestSound = { viewModel.playTestSound() },
                showMoveTrail = state.showMoveTrail,
                onShowMoveTrailToggled = { viewModel.setShowMoveTrail(it) },
                playbackSpeed = state.playbackSpeed,
                onPlaybackSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { viewModel.closeAudioSettings() }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "کتاب تعاملی شطرنج — فصل اول",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentSection != null) {
                                Text(
                                    text = "فصل اول / بخش ${state.currentSectionIndex + 1} / ${currentSection.titleFa}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("reader_breadcrumb_text")
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.setTab(AppTab.SECTIONS_LIST) },
                            modifier = Modifier.testTag("nav_toc_btn")
                        ) {
                            Icon(AppIcons.Menu, contentDescription = "فهرست بخش‌ها")
                        }
                    },
                    actions = {
                        // Audio Settings button
                        IconButton(
                            onClick = { viewModel.openAudioSettings() },
                            modifier = Modifier.testTag("nav_audio_settings_btn")
                        ) {
                            Icon(
                                imageVector = if (state.audioSettings.soundEnabled) AppIcons.VolumeUp else AppIcons.VolumeOff,
                                contentDescription = "تنظیمات صدا"
                            )
                        }

                        // Audit Report button
                        IconButton(
                            onClick = { viewModel.setTab(AppTab.AUDIT_REPORT) },
                            modifier = Modifier.testTag("nav_audit_btn")
                        ) {
                            Icon(AppIcons.Shield, contentDescription = "گزارش ممیزی")
                        }

                        // Font size scale selector
                        Box {
                            IconButton(
                                onClick = { showFontMenu = true },
                                modifier = Modifier.testTag("nav_font_size_btn")
                            ) {
                                Icon(AppIcons.FormatSize, contentDescription = "اندازه متن")
                            }
                            DropdownMenu(
                                expanded = showFontMenu,
                                onDismissRequest = { showFontMenu = false }
                            ) {
                                FontSizeScale.values().forEach { scale ->
                                    DropdownMenuItem(
                                        text = { Text(scale.labelFa) },
                                        onClick = {
                                            viewModel.setFontScale(scale)
                                            showFontMenu = false
                                        },
                                        modifier = Modifier.testTag("font_option_${scale.name}")
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = modifier
        ) { paddingValues ->
            if (currentSection == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("در حال بارگذاری داده‌های فصل اول...")
                }
                return@Scaffold
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag("reader_fixed_viewport")
            ) {
                val pages = state.currentPages.ifEmpty {
                    ReaderPaginator.paginateSection(
                        section = currentSection,
                        diagrams = state.diagrams,
                        fontScale = state.fontScale,
                        viewportWidthDp = state.viewportWidthDp,
                        viewportHeightDp = state.viewportHeightDp
                    )
                }
                val safePageIndex = state.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                val activePage = pages.getOrNull(safePageIndex) ?: pages.first()

                val pagerState = androidx.compose.runtime.key(currentSection.sectionId) {
                    rememberPagerState(
                        initialPage = safePageIndex,
                        pageCount = { pages.size.coerceAtLeast(1) }
                    )
                }

                var previousPageCount by remember(currentSection.sectionId) { mutableStateOf(pages.size) }

                // Sync ViewModel page index changes -> PagerState
                LaunchedEffect(safePageIndex, currentSection.sectionId, pages.size) {
                    val pageCountChanged = previousPageCount != pages.size
                    previousPageCount = pages.size
                    if (pagerState.currentPage != safePageIndex && safePageIndex in 0 until pages.size) {
                        if (pageCountChanged) {
                            pagerState.scrollToPage(safePageIndex)
                        } else {
                            pagerState.animateScrollToPage(
                                page = safePageIndex,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }

                // Sync user swipe gestures on HorizontalPager -> ViewModel
                LaunchedEffect(pagerState, currentSection.sectionId) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .drop(1)
                        .collect { settled ->
                            val latestState = viewModel.uiState.value
                            val latestPages = latestState.currentPages
                            if (settled != latestState.currentPageIndex &&
                                settled in latestPages.indices
                            ) {
                                viewModel.goToPage(settled)
                            }
                        }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Top Page Header: Section/Topic title, Semantic Content Badge, and Page Number
                    ReaderPageHeader(
                        section = currentSection,
                        page = activePage,
                        darkTheme = darkTheme,
                        fontMultiplier = fontMultiplier
                    )

                    // 1b. Compact Book Narration Control Bar (Fixed 38.dp height, RTL Persian)
                    ReaderNarrationBar(
                        narrationState = state.narrationState,
                        onPlayPage = { viewModel.playCurrentPageNarration() },
                        onPlayChapter = { viewModel.playChapterNarration(fromCurrentSection = true) },
                        onPause = { viewModel.pauseNarration() },
                        onResume = { viewModel.resumeNarration() },
                        onStop = { viewModel.stopNarration() },
                        onCycleSpeed = { viewModel.cycleNarrationSpeed() },
                        modifier = Modifier.padding(top = 3.dp)
                    )

                    // 2. Fixed-Height Page Body via HorizontalPager (NO vertical scroll)
                    // Measure the exact content viewport available to HorizontalPager (excluding Header, Narration Bar, and Footer)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("reader_content_viewport")
                    ) {
                        val contentWidthDp = maxWidth.value.toInt()
                        val contentHeightDp = maxHeight.value.toInt()
                        val pagerAvailableWidth = maxWidth
                        val pagerAvailableHeight = maxHeight

                        LaunchedEffect(contentWidthDp, contentHeightDp) {
                            if (contentWidthDp > 0 && contentHeightDp > 0) {
                                viewModel.updateViewportSize(contentWidthDp, contentHeightDp)
                            }
                        }

                        // Explicit Ltr + reverseLayout = true guarantees the exact Persian book contract:
                        // - Swiping Left-to-Right (swipeRight) advances to Next Page (page + 1)
                        // - Swiping Right-to-Left (swipeLeft) returns to Previous Page (page - 1)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            HorizontalPager(
                                state = pagerState,
                                reverseLayout = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("reader_horizontal_pager")
                            ) { pageIdx ->
                                val pageItem = pages.getOrNull(pageIdx) ?: activePage
                                // Restore RTL inside each page for Persian text
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    ReaderSinglePageContent(
                                        page = pageItem,
                                        section = currentSection,
                                        state = state,
                                        viewModel = viewModel,
                                        fontMultiplier = fontMultiplier,
                                        availableWidthDp = pagerAvailableWidth,
                                        availableHeightDp = pagerAvailableHeight
                                    )
                                }
                            }
                        }
                    }

                    // 3. Bottom Page Footer:
                    // Explicit Ltr Row so physical screen sides are guaranteed:
                    // - Left side: Left Arrow button -> Next Page (صفحه بعد)
                    // - Center: Progress indicator & page number
                    // - Right side: Right Arrow button -> Previous Page (صفحه قبل)
                    ReaderPageFooter(
                        page = activePage,
                        canGoNextPage = safePageIndex < pages.size - 1,
                        canGoPrevPage = safePageIndex > 0,
                        onNextPage = { viewModel.nextPage() },
                        onPrevPage = { viewModel.prevPage() },
                        canGoNextSection = state.currentSectionIndex < state.sections.size - 1,
                        canGoPrevSection = state.currentSectionIndex > 0,
                        onNextSection = { viewModel.nextSection() },
                        onPrevSection = { viewModel.prevSection() }
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderNarrationBar(
    narrationState: NarrationUiState,
    onPlayPage: () -> Unit,
    onPlayChapter: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onCycleSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = narrationState.isPlaying || narrationState.isPaused
    val elapsedSec = narrationState.currentPositionMs / 1000
    val totalSec = narrationState.durationMs / 1000
    val timeFormatted = String.format("%d:%02d / %d:%02d", elapsedSec / 60, elapsedSec % 60, totalSec / 60, totalSec % 60)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .testTag("reader_narration_bar"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (narrationState.isPlaying) {
                ReaderDesignTokens.MoveTrailPrimaryColor.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mode A: Play Current Page ("پخش صفحه")
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier
                    .height(28.dp)
                    .clickable(enabled = narrationState.narrationEnabled, onClick = onPlayPage)
                    .testTag("narration_play_page_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.PlayArrow,
                        contentDescription = "پخش همین صفحه",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "پخش صفحه",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            // Mode B: Play Continuous Chapter ("پخش فصل")
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier
                    .height(28.dp)
                    .clickable(enabled = narrationState.narrationEnabled, onClick = onPlayChapter)
                    .testTag("narration_play_chapter_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.VolumeUp,
                        contentDescription = "پخش پیوسته فصل اول",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "پخش فصل",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }
            }

            // Pause / Resume button when active or resumable
            if (isActive || narrationState.canResumeSavedPosition) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .height(28.dp)
                        .clickable {
                            if (narrationState.isPlaying) onPause() else onResume()
                        }
                        .testTag("narration_pause_resume_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (narrationState.isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = if (narrationState.isPlaying) "مکث" else "ادامه",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (narrationState.isPlaying) "مکث" else "ادامه",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }

            // Stop button when playing or paused
            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                    modifier = Modifier
                        .height(28.dp)
                        .clickable(onClick = onStop)
                        .testTag("narration_stop_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Stop,
                            contentDescription = "توقف",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "توقف",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1
                        )
                    }
                }
            }

            // Center Status / Progress area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (narrationState.statusMessageFa != null && !isActive) {
                    Text(
                        text = narrationState.statusMessageFa,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (narrationState.status == NarrationStatus.UNAVAILABLE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("narration_status_text")
                    )
                } else if (isActive) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = narrationState.statusMessageFa ?: timeFormatted,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag(
                                    if (narrationState.statusMessageFa != null) "narration_status_text" else "narration_time_text"
                                )
                            )
                            if (narrationState.statusMessageFa != null) {
                                Text(
                                    text = timeFormatted,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("narration_time_text")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { narrationState.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .testTag("narration_progress_bar")
                        )
                    }
                } else {
                    Text(
                        text = "خوانش صوتی کتاب",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("narration_idle_label")
                    )
                }
            }

            // Speed selector pill (0.8x / 1.0x / 1.2x)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .height(26.dp)
                    .clickable(onClick = onCycleSpeed)
                    .testTag("narration_speed_btn")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = narrationState.speed.labelFa,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderPageHeader(
    section: SectionItem,
    page: ReaderPage,
    darkTheme: Boolean,
    fontMultiplier: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reader_page_header"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Section Title & Semantic Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = section.titleFa,
                    fontSize = (15 * fontMultiplier).coerceAtMost(18f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                ReaderSemanticBadge(
                    category = page.semanticBadge,
                    pageType = page.pageType,
                    darkTheme = darkTheme
                )
            }

            // Page Number Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "صفحه ${page.pageNumber} از ${page.totalPages}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("reader_page_number_text")
                )
            }
        }
    }
}

@Composable
fun ReaderSemanticBadge(
    category: SemanticBadgeCategory,
    pageType: ReaderPageType,
    darkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val customTitle = if (category == SemanticBadgeCategory.EDUCATIONAL_TEXT && pageType == ReaderPageType.SUMMARY) {
        pageType.labelFa
    } else {
        category.defaultTitleFa
    }
    val palette = ReaderDesignTokens.paletteFor(
        category = category,
        darkTheme = darkTheme,
        customTitleFa = customTitle
    )

    Surface(
        modifier = modifier
            .testTag("reader_semantic_badge_${category.badgeId}"),
        shape = RoundedCornerShape(8.dp),
        color = palette.containerColor,
        border = BorderStroke(1.dp, palette.borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .testTag("reader_semantic_badge"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = palette.icon,
                contentDescription = null,
                tint = palette.contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = palette.titleFa,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReaderSinglePageContent(
    page: ReaderPage,
    section: SectionItem,
    state: ChapterUiState,
    viewModel: ChapterViewModel,
    fontMultiplier: Float,
    availableWidthDp: Dp = state.viewportWidthDp.dp,
    availableHeightDp: Dp = state.viewportHeightDp.dp
) {
    val hasDiagram = page.diagramBlockCount > 0
    val slot = section.translationSlot
    val charsPerLine = ((availableWidthDp.value - 24f) / (8.5f * fontMultiplier)).toInt().coerceIn(18, 65)

    // Calculate actual height used by companion text blocks on this page (0.dp if there is no companion text)
    val companionHeightDp = page.orderedBlocks.fold(0.dp) { acc, block ->
        when (block) {
            is SectionContentBlock.ParagraphBlock -> {
                val resolved = slot.resolveBlockText(block)
                val origId = ReaderPaginator.originalBlockId(block.blockId)
                if (!resolved.isNullOrBlank()) {
                    val lines = ReaderPaginator.estimateRawTextLines(resolved, charsPerLine).coerceAtLeast(1)
                    acc + (lines * 22f * fontMultiplier + 22f).dp
                } else if (slot.shouldShowPlaceholderForBlock(origId)) {
                    acc + (76f * fontMultiplier).dp
                } else {
                    acc
                }
            }
            is SectionContentBlock.HeadingBlock -> {
                val resolved = slot.resolveBlockText(block)
                if (!resolved.isNullOrBlank()) {
                    acc + (28f * fontMultiplier).dp
                } else if (slot.shouldShowPlaceholderForBlock(block.blockId)) {
                    acc + (76f * fontMultiplier).dp
                } else {
                    acc
                }
            }
            is SectionContentBlock.FootnoteBlock -> {
                val resolved = slot.resolveBlockText(block)
                if (!resolved.isNullOrBlank()) {
                    val lines = ReaderPaginator.estimateRawTextLines(resolved, charsPerLine).coerceAtLeast(1)
                    acc + (lines * 18f * fontMultiplier + 16f).dp
                } else {
                    acc
                }
            }
            else -> acc
        }
    }

    // Calculate only the real non-board chrome height required by this specific diagram type.
    // IMPORTANT: For interactive exercises (BD-032), this height is strictly constant (256.dp)
    // and never depends on feedbackMessage, selectedSquare, or retry button visibility!
    val diagram = page.primaryDiagramId?.let { id -> state.diagrams.find { it.diagramId == id } }
    val pgnStepsForDiagram = diagram?.pgnRef?.let { state.pgnStates[it]?.totalSteps } ?: 0
    val hasPgnControls = diagram != null && diagram.pgnRef != null && pgnStepsForDiagram > 0 && diagram.auditStatus == AuditStatus.VALID
    val isBlockedDiagram = diagram?.auditStatus == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW
    val isInteractiveExercise = page.pageType == ReaderPageType.INTERACTIVE_EXERCISE || diagram?.diagramId == "BD-032"

    val diagramNonBoardChromeDp = when {
        !hasDiagram -> 0.dp
        isInteractiveExercise -> 244.dp
        hasPgnControls -> 190.dp
        isBlockedDiagram -> 162.dp
        else -> 76.dp // Static diagram: card header + audit badge only (NO PGN controls reservation)
    }

    val interBlockSpacingDp = if (companionHeightDp > 0.dp) 6.dp else 0.dp
    val remainingHeightForBoard = availableHeightDp - companionHeightDp - interBlockSpacingDp - diagramNonBoardChromeDp
    val maxBoardWidthFromPage = availableWidthDp - 16.dp
    val boardMaxHeight = minOf(maxBoardWidthFromPage, remainingHeightForBoard).coerceAtLeast(120.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_page_container_${page.pageNumber}"),
        verticalArrangement = if (hasDiagram) Arrangement.spacedBy(6.dp, Alignment.Top) else Arrangement.spacedBy(8.dp, Alignment.Top)
    ) {
        page.orderedBlocks.forEach { block ->
            SectionContentBlockItem(
                block = block,
                section = section,
                state = state,
                viewModel = viewModel,
                fontMultiplier = fontMultiplier,
                maxBoardHeight = boardMaxHeight,
                compactMode = true
            )
        }
    }
}

@Composable
fun ReaderPageFooter(
    page: ReaderPage,
    canGoNextPage: Boolean,
    canGoPrevPage: Boolean,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    canGoNextSection: Boolean = false,
    canGoPrevSection: Boolean = false,
    onNextSection: () -> Unit = {},
    onPrevSection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progressFraction = if (page.totalPages > 0) {
        page.pageNumber.toFloat() / page.totalPages.toFloat()
    } else {
        1f
    }

    // Explicit LTR layout direction so Left vs Right screen coordinates are strictly guaranteed:
    // - Left button (دکمه فلش چپ): Next Page (صفحه بعد)
    // - Right button (دکمه فلش راست): Previous Page (صفحه قبل)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("reader_page_footer"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT BUTTON: Left Arrow -> Next Page (صفحه بعد)
                    FilledTonalButton(
                        onClick = onNextPage,
                        enabled = canGoNextPage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("reader_next_page_btn")
                    ) {
                        Icon(
                            imageVector = AppIcons.ChevronLeft,
                            contentDescription = "صفحه بعد"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صفحه بعد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // CENTER: Progress Indicator & Page Counter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .testTag("reader_progress_indicator")
                    ) {
                        Text(
                            text = "${page.pageNumber} / ${page.totalPages} (${(progressFraction * 100).toInt()}٪)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        // Render progress bar right-to-left for Persian reading flow
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .testTag("reader_progress_bar")
                            )
                        }
                    }

                    // RIGHT BUTTON: Right Arrow -> Previous Page (صفحه قبل)
                    FilledTonalButton(
                        onClick = onPrevPage,
                        enabled = canGoPrevPage,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("reader_prev_page_btn")
                    ) {
                        Text("صفحه قبل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = "صفحه قبل"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionContentBlockItem(
    block: SectionContentBlock,
    section: SectionItem,
    state: ChapterUiState,
    viewModel: ChapterViewModel,
    fontMultiplier: Float,
    maxBoardHeight: Dp = 380.dp,
    compactMode: Boolean = false
) {
    val cardPadding = if (compactMode) 10.dp else 14.dp
    val originalId = ReaderPaginator.originalBlockId(block.blockId)
    val isActivelyNarrated = state.narrationState.isPlaying &&
        state.narrationState.activeSectionId == section.sectionId &&
        state.narrationState.activeBlockId == originalId

    val highlightWrapperModifier = if (isActivelyNarrated) {
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = ReaderDesignTokens.MoveTrailPrimaryColor,
                shape = RoundedCornerShape(10.dp)
            )
            .testTag("narrated_active_block_$originalId")
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = highlightWrapperModifier) {
        when (block) {
            is SectionContentBlock.ParagraphBlock -> {
                val resolvedText = section.translationSlot.resolveBlockText(block)
                if (!resolvedText.isNullOrBlank()) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("content_block_${block.blockId}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isActivelyNarrated) {
                                ReaderDesignTokens.MoveTrailPrimaryColor.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Text(
                            text = resolvedText,
                            fontSize = (14.5f * fontMultiplier).sp,
                            lineHeight = (22f * fontMultiplier).sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(cardPadding)
                        )
                    }
                } else if (section.translationSlot.shouldShowPlaceholderForBlock(originalId)) {
                    PendingTranslationSlotCard(
                        blockId = block.blockId,
                        sectionId = section.sectionId,
                        placeholderNotice = section.translationSlot.placeholderNotice,
                        fontMultiplier = fontMultiplier,
                        compactMode = compactMode
                    )
                }
            }

            is SectionContentBlock.HeadingBlock -> {
                val resolvedText = section.translationSlot.resolveBlockText(block)
                if (!resolvedText.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isActivelyNarrated) {
                                    ReaderDesignTokens.MoveTrailPrimaryColor.copy(alpha = 0.08f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .testTag("content_block_${block.blockId}")
                    ) {
                        Text(
                            text = resolvedText,
                            fontSize = (17 * fontMultiplier).sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)
                        )
                    }
                } else if (section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)) {
                    PendingTranslationSlotCard(
                        blockId = block.blockId,
                        sectionId = section.sectionId,
                        placeholderNotice = section.translationSlot.placeholderNotice,
                        fontMultiplier = fontMultiplier,
                        compactMode = compactMode
                    )
                }
            }

            is SectionContentBlock.DiagramBlock -> {
                val diagram = state.diagrams.find { it.diagramId == block.diagramId }
                if (diagram != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("content_block_${block.blockId}")
                    ) {
                        DiagramCard(
                            diagram = diagram,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = fontMultiplier,
                            maxBoardHeight = maxBoardHeight,
                            compactMode = compactMode
                        )
                    }
                }
            }

            is SectionContentBlock.ExerciseBlock -> {
                val diagram = state.diagrams.find { it.diagramId == block.diagramId }
                if (diagram != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("content_block_${block.blockId}")
                    ) {
                        DiagramCard(
                            diagram = diagram,
                            state = state,
                            viewModel = viewModel,
                            fontMultiplier = fontMultiplier,
                            maxBoardHeight = maxBoardHeight,
                            compactMode = compactMode
                        )
                    }
                }
            }

            is SectionContentBlock.FootnoteBlock -> {
                val resolvedText = section.translationSlot.resolveBlockText(block)
                if (!resolvedText.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("content_block_${block.blockId}"),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActivelyNarrated) {
                            ReaderDesignTokens.MoveTrailPrimaryColor.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ) {
                        Text(
                            text = resolvedText,
                            fontSize = (12 * fontMultiplier).sp,
                            lineHeight = (18 * fontMultiplier).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else if (section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)) {
                    PendingTranslationSlotCard(
                        blockId = block.blockId,
                        sectionId = section.sectionId,
                        placeholderNotice = section.translationSlot.placeholderNotice,
                        fontMultiplier = fontMultiplier,
                        compactMode = compactMode
                    )
                }
            }

            is SectionContentBlock.ExternalReferenceBlock -> {
                val resolvedTitle = section.translationSlot.resolveBlockText(block)
                if (block.isAuthorizedAndEnabled && !resolvedTitle.isNullOrBlank()) {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("content_block_${block.blockId}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isActivelyNarrated) {
                                ReaderDesignTokens.MoveTrailPrimaryColor.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Text(
                            text = resolvedTitle,
                            fontSize = (13 * fontMultiplier).sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingTranslationSlotCard(
    blockId: String,
    sectionId: String,
    placeholderNotice: String,
    fontMultiplier: Float,
    compactMode: Boolean = false
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("content_block_$blockId"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(if (compactMode) 10.dp else 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.PendingActions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = "محل اتصال محتوای ترجمه‌شده",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = sectionId,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = placeholderNotice,
                fontSize = (12.5f * fontMultiplier).sp,
                lineHeight = (18f * fontMultiplier).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DiagramCard(
    diagram: DiagramItem,
    state: ChapterUiState,
    viewModel: ChapterViewModel,
    fontMultiplier: Float,
    maxBoardHeight: Dp = 380.dp,
    compactMode: Boolean = false
) {
    val isBookmarked = state.bookmarks.contains(diagram.diagramId)
    val pgnState = diagram.pgnRef?.let { state.pgnStates[it] }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagram_card_${diagram.diagramId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(if (compactMode) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 8.dp)
        ) {
            // Header with ID, PDF Page, and Bookmark icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = diagram.diagramId,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "صفحه ${diagram.pdfPage} کتاب",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleBookmark(diagram.diagramId) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                        contentDescription = "نشانک",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Audit Status Badge
            AuditBadge(
                status = diagram.auditStatus,
                warningText = diagram.auditWarning
            )

            // Specific UI per Diagram Type (no duplicate placeholder text inside DiagramCard)
            when (diagram.diagramId) {
                "BD-032" -> {
                    // Dynamic Interactive ChessEngine Puzzle for BD-032
                    Bd032ExerciseView(
                        state = state.bd032State,
                        onSquareClick = { file, rank -> viewModel.onBd032SquareClicked(file, rank) },
                        onReset = { viewModel.resetBd032() },
                        onRetry = { viewModel.retryBd032() },
                        onToggleLegalMovesHint = { viewModel.toggleBd032LegalMovesHint() },
                        onTransitionFinished = { transitionId -> viewModel.onBd032TransitionFinished(transitionId) },
                        showMoveTrail = state.showMoveTrail,
                        onMoveTrailFinished = { eventId -> viewModel.onBd032MoveTrailFinished(eventId) },
                        maxBoardHeight = maxBoardHeight
                    )
                }
                else -> {
                    val boardFen = pgnState?.currentFen ?: diagram.initialFen
                    ChessBoardView(
                        fen = boardFen,
                        transition = pgnState?.activeTransition,
                        onTransitionFinished = { transitionId ->
                            pgnState?.pgnRef?.let { ref ->
                                viewModel.onPgnTransitionFinished(ref, transitionId)
                            }
                        },
                        moveTrail = pgnState?.activeMoveTrail,
                        showMoveTrail = state.showMoveTrail,
                        onMoveTrailFinished = { eventId ->
                            pgnState?.pgnRef?.let { ref ->
                                viewModel.onPgnMoveTrailFinished(ref, eventId)
                            }
                        },
                        highlightSquares = diagram.highlightSquares,
                        modifier = Modifier.heightIn(max = maxBoardHeight)
                    )

                    if (pgnState != null && pgnState.totalSteps > 0 && diagram.auditStatus == AuditStatus.VALID) {
                        // Fixed-height Persistent Move Description Bar directly below the board (42.dp permanently reserved)
                        PersistentMoveDescriptionBar(
                            descriptionFa = pgnState.lastMoveDescriptionFa
                        )

                        PgnControlsView(
                            state = pgnState,
                            onNext = { viewModel.stepPgnNext(pgnState.pgnRef) },
                            onPrev = { viewModel.stepPgnPrev(pgnState.pgnRef) },
                            onReset = { viewModel.resetPgn(pgnState.pgnRef) },
                            onEnd = { viewModel.jumpPgnToEnd(pgnState.pgnRef) }
                        )
                    } else if (diagram.auditStatus == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW) {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "وضعیت تعاملی مسدود است: به دلیل تناقض در FEN اصلی کتاب (امکان فرار قانونی شاه به g8)، کنترل‌های بازی فعال نشده‌اند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
