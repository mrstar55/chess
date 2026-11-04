package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.ChapterRepository
import com.example.data.FontSizeScale
import com.example.model.AuditStatus
import com.example.model.DiagramItem
import com.example.model.FrontMatterSourceLink
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
import com.example.ui.components.ChessBoardView
import com.example.ui.components.InteractiveExerciseView
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
    val isFrontMatter = state.isReadingFrontMatter

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

        val chapterDisplayName = if (isFrontMatter) {
            state.frontMatterNavigationTitleFa
        } else {
            state.activeChapterShortTitleFa.ifBlank { state.activeChapterTitleFa }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "کتاب تعاملی شطرنج — $chapterDisplayName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val activePage = state.currentPage
                            if (isFrontMatter) {
                                val pdfPageNum = activePage?.sourcePdfPage ?: state.lastReadFrontMatterSourcePdfPage
                                val pdfLabel = activePage?.sourcePdfPageDisplayLabelFa
                                    ?: "صفحه PDF ${ReaderPaginator.toPersianDigits(pdfPageNum)}"
                                Text(
                                    text = "${state.frontMatterNavigationTitleFa} / $pdfLabel / ${activePage?.subtitleFa ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("reader_breadcrumb_text")
                                )
                            } else if (currentSection != null) {
                                Text(
                                    text = "$chapterDisplayName / بخش ${state.currentSectionIndex + 1} / ${currentSection.titleFa}",
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
            if (!isFrontMatter && currentSection == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("در حال بارگذاری داده‌های فصل...")
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
                    if (isFrontMatter && state.frontMatterPack != null) {
                        ReaderPaginator.paginateFrontMatterPages(
                            frontMatterPages = state.frontMatterPack.pages,
                            fontScale = state.fontScale,
                            viewportWidthDp = state.viewportWidthDp,
                            viewportHeightDp = state.viewportHeightDp
                        )
                    } else if (currentSection != null) {
                        ReaderPaginator.paginateSection(
                            section = currentSection,
                            diagrams = state.diagrams,
                            fontScale = state.fontScale,
                            viewportWidthDp = state.viewportWidthDp,
                            viewportHeightDp = state.viewportHeightDp
                        )
                    } else {
                        emptyList()
                    }
                }
                if (pages.isEmpty()) return@Box

                val safePageIndex = state.currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                val activePage = pages.getOrNull(safePageIndex) ?: pages.first()
                val pagerIdentityKey = if (isFrontMatter) {
                    ChapterRepository.FRONT_MATTER_REGION_ID
                } else {
                    currentSection?.sectionId ?: "SECTION"
                }

                val pagerState = androidx.compose.runtime.key(pagerIdentityKey) {
                    rememberPagerState(
                        initialPage = safePageIndex,
                        pageCount = { pages.size.coerceAtLeast(1) }
                    )
                }

                var previousPageCount by remember(pagerIdentityKey) { mutableStateOf(pages.size) }

                // Sync ViewModel page index changes -> PagerState
                LaunchedEffect(safePageIndex, pagerIdentityKey, pages.size) {
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
                LaunchedEffect(pagerState, pagerIdentityKey) {
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
                    // 1. Top Page Header: Section/Topic title, Semantic Content Badge, Authoritative sourcePdfPage Badge, and Page Number
                    ReaderPageHeader(
                        section = currentSection,
                        page = activePage,
                        darkTheme = darkTheme,
                        fontMultiplier = fontMultiplier
                    )

                    // 1b. Compact Book Narration Control Bar (Fixed 32.dp height, RTL Persian)
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

                    // Optional navigation notice banner (e.g., when clicking links to future chapters 52/86)
                    if (!state.lastNavigationNoticeFa.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 3.dp)
                                .clickable { viewModel.dismissNavigationNotice() }
                                .testTag("reader_navigation_notice_banner"),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.lastNavigationNoticeFa,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "بستن",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    // 2. Fixed-Height Page Body via HorizontalPager (NO vertical scroll)
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
                        canGoNextPage = safePageIndex < pages.size - 1 || isFrontMatter,
                        canGoPrevPage = safePageIndex > 0,
                        onNextPage = { viewModel.nextPage() },
                        onPrevPage = { viewModel.prevPage() },
                        canGoNextSection = isFrontMatter || state.currentSectionIndex < state.sections.size - 1,
                        canGoPrevSection = !isFrontMatter && state.currentSectionIndex > 0,
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
                        contentDescription = "پخش پیوسته فصل",
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
    section: SectionItem?,
    page: ReaderPage,
    darkTheme: Boolean,
    fontMultiplier: Float,
    modifier: Modifier = Modifier
) {
    val headerTitleFa = if (page.contentRegionId == ChapterRepository.FRONT_MATTER_REGION_ID) {
        page.subtitleFa ?: "پیش‌درآمد و مبانی شطرنج"
    } else {
        section?.titleFa ?: page.subtitleFa ?: ""
    }
    val sourcePdfBadgeText = page.sourcePdfPageDisplayLabelFa

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
            // Section/Page Title & Semantic Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = headerTitleFa,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Authoritative Physical PDF Page Pill
                if (!sourcePdfBadgeText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = sourcePdfBadgeText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("reader_source_pdf_page_badge")
                        )
                    }
                }

                // Slice / Section Page Counter Pill
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
    section: SectionItem?,
    state: ChapterUiState,
    viewModel: ChapterViewModel,
    fontMultiplier: Float,
    availableWidthDp: Dp = state.viewportWidthDp.dp,
    availableHeightDp: Dp = state.viewportHeightDp.dp
) {
    val hasDiagram = page.diagramBlockCount > 0
    val hasSourceImage = page.sourceImageBlockCount > 0
    val slot = section?.translationSlot
    val charsPerLine = ((availableWidthDp.value - 24f) / (8.5f * fontMultiplier)).toInt().coerceIn(18, 65)

    // Calculate actual height used by companion text blocks on this page (0.dp if there is no companion text)
    val companionHeightDp = page.orderedBlocks.fold(0.dp) { acc, block ->
        when (block) {
            is SectionContentBlock.ParagraphBlock -> {
                val resolved = slot?.resolveBlockText(block) ?: block.textFa
                val origId = ReaderPaginator.originalBlockId(block.blockId)
                if (!resolved.isNullOrBlank()) {
                    val lines = ReaderPaginator.estimateRawTextLines(resolved, charsPerLine).coerceAtLeast(1)
                    acc + (lines * 22f * fontMultiplier + 22f).dp
                } else if (slot?.shouldShowPlaceholderForBlock(origId) == true) {
                    acc + (76f * fontMultiplier).dp
                } else {
                    acc
                }
            }
            is SectionContentBlock.HeadingBlock -> {
                val resolved = slot?.resolveBlockText(block) ?: block.textFa
                if (!resolved.isNullOrBlank()) {
                    acc + (28f * fontMultiplier).dp
                } else if (slot?.shouldShowPlaceholderForBlock(block.blockId) == true) {
                    acc + (76f * fontMultiplier).dp
                } else {
                    acc
                }
            }
            is SectionContentBlock.FootnoteBlock -> {
                val resolved = slot?.resolveBlockText(block) ?: block.textFa
                if (!resolved.isNullOrBlank()) {
                    val lines = ReaderPaginator.estimateRawTextLines(resolved, charsPerLine).coerceAtLeast(1)
                    acc + (lines * 18f * fontMultiplier + 16f).dp
                } else {
                    acc
                }
            }
            is SectionContentBlock.OrderedListBlock -> {
                val lines = block.items.sumOf { ReaderPaginator.estimateRawTextLines(it, charsPerLine).coerceAtLeast(1) }
                acc + (lines * 20f * fontMultiplier + 28f).dp
            }
            is SectionContentBlock.TableBlock -> {
                acc + (block.rows.size * 24f * fontMultiplier + 28f).dp
            }
            else -> acc
        }
    }

    // Calculate only the real non-board chrome height required by this specific diagram type.
    val diagram = page.primaryDiagramId?.let { id -> state.diagrams.find { it.diagramId == id } }
    val pgnStepsForDiagram = diagram?.pgnRef?.let { state.pgnStates[it]?.totalSteps } ?: 0
    val hasPgnControls = diagram != null && diagram.pgnRef != null && pgnStepsForDiagram > 0 && diagram.auditStatus == AuditStatus.VALID
    val isBlockedDiagram = diagram?.auditStatus == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW
    val isInteractiveExercise = page.pageType == ReaderPageType.INTERACTIVE_EXERCISE ||
        page.orderedBlocks.any { it is SectionContentBlock.ExerciseBlock } ||
        state.exerciseDefinitions.values.any { it.diagramId == diagram?.diagramId }

    val diagramNonBoardChromeDp = when {
        !hasDiagram && !hasSourceImage -> 0.dp
        isInteractiveExercise -> 244.dp
        hasPgnControls -> 190.dp
        isBlockedDiagram -> 162.dp
        else -> 64.dp
    }

    val linksBarHeightDp = if (page.sourceLinks.isNotEmpty() &&
        page.orderedBlocks.none { it is SectionContentBlock.HeadingBlock && it.tocTargetSourcePdfPage != null }
    ) 36.dp else 0.dp

    val interBlockSpacingDp = if (companionHeightDp > 0.dp) 6.dp else 0.dp
    val remainingHeightForBoard = availableHeightDp - companionHeightDp - interBlockSpacingDp - diagramNonBoardChromeDp - linksBarHeightDp
    val maxBoardWidthFromPage = availableWidthDp - 16.dp
    val boardMaxHeight = minOf(maxBoardWidthFromPage, remainingHeightForBoard).coerceAtLeast(110.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_page_container_${page.pageNumber}"),
        verticalArrangement = if (hasDiagram || hasSourceImage) {
            Arrangement.spacedBy(6.dp, Alignment.Top)
        } else {
            Arrangement.spacedBy(8.dp, Alignment.Top)
        }
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

        // Render sourceLinks bar on pages that have internal/external source links (e.g., pages 4, 21)
        if (page.sourceLinks.isNotEmpty() &&
            page.orderedBlocks.none { it is SectionContentBlock.HeadingBlock && it.tocTargetSourcePdfPage != null }
        ) {
            ReaderSourceLinksRow(
                links = page.sourceLinks,
                onLinkClicked = { viewModel.openSourceLink(it) }
            )
        }
    }
}

@Composable
private fun ReaderSourceLinksRow(
    links: List<FrontMatterSourceLink>,
    onLinkClicked: (FrontMatterSourceLink) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("reader_page_source_links_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        links.forEach { link ->
            val labelFa = when {
                link.targetSourcePdfPage != null ->
                    "ارجاع به صفحه PDF ${ReaderPaginator.toPersianDigits(link.targetSourcePdfPage)}"
                !link.targetUri.isNullOrBlank() ->
                    "پیوند: ${link.sourceLabel}"
                else -> link.sourceLabel
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .clickable { onLinkClicked(link) }
                    .testTag("source_link_${link.sourcePdfPage}_${link.linkIndexOnPage}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = labelFa,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }
            }
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
    section: SectionItem?,
    state: ChapterUiState,
    viewModel: ChapterViewModel,
    fontMultiplier: Float,
    maxBoardHeight: Dp = 380.dp,
    compactMode: Boolean = false
) {
    val cardPadding = if (compactMode) 10.dp else 14.dp
    val originalId = ReaderPaginator.originalBlockId(block.blockId)
    val isActivelyNarrated = state.narrationState.isPlaying &&
        section != null &&
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
                val resolvedText = section?.translationSlot?.resolveBlockText(block) ?: block.textFa
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
                        Column(modifier = Modifier.padding(cardPadding)) {
                            Text(
                                text = resolvedText,
                                fontSize = (14.5f * fontMultiplier).sp,
                                lineHeight = (22f * fontMultiplier).sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (!block.sourceUrl.isNullOrBlank() || block.sourcePageReferences.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!block.sourceUrl.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .clickable { viewModel.openExternalUri(block.sourceUrl) }
                                                .testTag("paragraph_url_${block.blockId}")
                                        ) {
                                            Text(
                                                text = "وب‌سایت ناشر",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    block.sourcePageReferences.forEach { refPage ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier
                                                .clickable { viewModel.navigateToSourcePdfPage(refPage) }
                                                .testTag("paragraph_page_ref_${block.blockId}_$refPage")
                                        ) {
                                            Text(
                                                text = "صفحه PDF ${ReaderPaginator.toPersianDigits(refPage)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (section?.translationSlot?.shouldShowPlaceholderForBlock(originalId) == true) {
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
                val resolvedText = section?.translationSlot?.resolveBlockText(block) ?: block.textFa
                val tocTargetPage = block.tocTargetSourcePdfPage
                if (!resolvedText.isNullOrBlank()) {
                    if (tocTargetPage != null) {
                        val indentDp = ((block.level - 1).coerceIn(0, 3) * 12).dp
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = indentDp)
                                .clickable {
                                    viewModel.navigateToSourcePdfPage(tocTargetPage, block.chapterId)
                                }
                                .testTag("content_block_${block.blockId}"),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = resolvedText,
                                    fontSize = (13.5f * fontMultiplier).sp,
                                    fontWeight = if (block.level <= 1) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (block.level <= 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "صفحه ${ReaderPaginator.toPersianDigits(tocTargetPage)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
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
                    }
                } else if (section?.translationSlot?.shouldShowPlaceholderForBlock(block.blockId) == true) {
                    PendingTranslationSlotCard(
                        blockId = block.blockId,
                        sectionId = section.sectionId,
                        placeholderNotice = section.translationSlot.placeholderNotice,
                        fontMultiplier = fontMultiplier,
                        compactMode = compactMode
                    )
                }
            }

            is SectionContentBlock.SourceImageBlock -> {
                SourceImageCard(
                    block = block,
                    state = state,
                    fontMultiplier = fontMultiplier,
                    maxImageHeight = maxBoardHeight,
                    compactMode = compactMode
                )
            }

            is SectionContentBlock.OrderedListBlock -> {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("content_block_${block.blockId}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(cardPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!block.titleFa.isNullOrBlank()) {
                            Text(
                                text = block.titleFa,
                                fontSize = (14.5f * fontMultiplier).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        block.items.forEachIndexed { idx, itemText ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "${ReaderPaginator.toPersianDigits(idx + 1)}. ",
                                    fontSize = (13.5f * fontMultiplier).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = itemText,
                                    fontSize = (13.5f * fontMultiplier).sp,
                                    lineHeight = (20f * fontMultiplier).sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            is SectionContentBlock.TableBlock -> {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("content_block_${block.blockId}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(cardPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!block.titleFa.isNullOrBlank()) {
                            Text(
                                text = block.titleFa,
                                fontSize = (14f * fontMultiplier).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        block.rows.forEachIndexed { rowIdx, rowCells ->
                            if (rowIdx > 0) {
                                HorizontalDivider(
                                    thickness = 0.6.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowCells.forEachIndexed { cellIdx, cellText ->
                                    Text(
                                        text = cellText,
                                        fontSize = (12.5f * fontMultiplier).sp,
                                        lineHeight = (18f * fontMultiplier).sp,
                                        fontWeight = if (rowIdx == 0 || cellIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (rowIdx == 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                        modifier = Modifier.weight(if (cellIdx == 0) 0.35f else 0.65f)
                                    )
                                }
                            }
                        }
                    }
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
                            compactMode = compactMode,
                            exerciseId = block.exerciseId
                        )
                    }
                }
            }

            is SectionContentBlock.FootnoteBlock -> {
                val resolvedText = section?.translationSlot?.resolveBlockText(block) ?: block.textFa
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
                } else if (section?.translationSlot?.shouldShowPlaceholderForBlock(block.blockId) == true) {
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
                val resolvedTitle = section?.translationSlot?.resolveBlockText(block) ?: block.titleFa
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
private fun SourceImageCard(
    block: SectionContentBlock.SourceImageBlock,
    state: ChapterUiState,
    fontMultiplier: Float,
    maxImageHeight: Dp,
    compactMode: Boolean
) {
    val context = LocalContext.current
    val resolvedAsset = state.frontMatterPack?.sourceAssetsById?.get(block.assetId)
    val effectiveAssetPath = block.resolvedAssetPath.ifBlank { resolvedAsset?.resolvedAssetPath.orEmpty() }
    val imageBitmap = remember(effectiveAssetPath) {
        if (effectiveAssetPath.isBlank()) {
            null
        } else {
            runCatching {
                context.assets.open(effectiveAssetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val imgWidth = if (block.imageWidth > 0) block.imageWidth else (resolvedAsset?.width ?: 0)
    val imgHeight = if (block.imageHeight > 0) block.imageHeight else (resolvedAsset?.height ?: 0)
    val isSmallIcon = imgWidth in 1..320 && imgHeight in 1..320
    val targetMaxHeight = if (isSmallIcon) 110.dp else maxImageHeight

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("content_block_${block.blockId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compactMode) 8.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = block.assetId,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (block.sourcePdfPage != null) {
                    Text(
                        text = "صفحه PDF ${ReaderPaginator.toPersianDigits(block.sourcePdfPage)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = block.descriptionFa.ifBlank { block.assetId },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (isSmallIcon) 56.dp else 120.dp, max = targetMaxHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("source_image_${block.assetId}")
                )
            }

            if (block.descriptionFa.isNotBlank()) {
                Text(
                    text = block.descriptionFa,
                    fontSize = (11.5f * fontMultiplier).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    compactMode: Boolean = false,
    exerciseId: String? = null
) {
    val isBookmarked = state.bookmarks.contains(diagram.diagramId)
    val pgnState = diagram.pgnRef?.let { state.pgnStates[it] }
    val resolvedExerciseId = exerciseId
        ?: state.exerciseDefinitions.values.firstOrNull { it.diagramId == diagram.diagramId }?.exerciseId

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

            // Specific UI per Diagram Type (generalized ExerciseBlock / ExerciseDefinition dispatch)
            val exerciseUiState = resolvedExerciseId?.let { state.exerciseStateFor(it, diagram.diagramId) }
            if (resolvedExerciseId != null && exerciseUiState != null) {
                val exerciseDefinition = state.exerciseDefinitions[resolvedExerciseId]
                InteractiveExerciseView(
                    exerciseId = resolvedExerciseId,
                    state = exerciseUiState,
                    definition = exerciseDefinition,
                    onSquareClick = { file, rank -> viewModel.onExerciseSquareClicked(resolvedExerciseId, file, rank) },
                    onReset = { viewModel.resetExercise(resolvedExerciseId) },
                    onRetry = { viewModel.retryExercise(resolvedExerciseId) },
                    onToggleLegalMovesHint = { viewModel.toggleExerciseLegalMovesHint(resolvedExerciseId) },
                    onTransitionFinished = { transitionId -> viewModel.onExerciseTransitionFinished(resolvedExerciseId, transitionId) },
                    showMoveTrail = state.showMoveTrail,
                    onMoveTrailFinished = { eventId -> viewModel.onExerciseMoveTrailFinished(resolvedExerciseId, eventId) },
                    maxBoardHeight = maxBoardHeight
                )
            } else {
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
