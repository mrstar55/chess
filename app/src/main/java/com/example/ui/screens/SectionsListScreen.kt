package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ReaderPaginator
import com.example.ui.AppTab
import com.example.ui.ChapterUiState
import com.example.ui.ChapterViewModel
import com.example.ui.components.AudioSettingsDialog
import com.example.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionsListScreen(
    viewModel: ChapterViewModel,
    state: ChapterUiState,
    onOpenSettings: () -> Unit = { viewModel.openSettings() },
    modifier: Modifier = Modifier
) {
    val fontMultiplier = state.fontScale.scale
    val tocEntries = state.frontMatterPack?.tableOfContents?.entries.orEmpty()
    val frontMatterTopics = remember(tocEntries) {
        BookTocNavigationHelper.extractFrontMatterTopics(tocEntries)
    }
    val tocChapterGroups = remember(tocEntries) {
        BookTocNavigationHelper.extractChapterGroups(tocEntries)
    }
    val enabledChapterIds = remember(state.availableChapters) {
        state.availableChapters.map { it.chapterId }.toSet()
    }

    var frontMatterTopicsExpanded by remember { mutableStateOf(false) }
    var expandedTocChapterId by remember { mutableStateOf<String?>(null) }

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
                onDismiss = { viewModel.closeAudioSettings() }
            )
        }

        val activePdfPage = state.currentPage?.sourcePdfPage ?: state.lastReadFrontMatterSourcePdfPage
        val isCh01OnPage39 = state.isReadingFrontMatter && activePdfPage == 39
        val isActuallyFrontMatter = state.isReadingFrontMatter && !isCh01OnPage39

        val chapterDisplayName = when {
            isActuallyFrontMatter -> BookTocNavigationHelper.FRONT_MATTER_BADGE_LABEL_FA
            isCh01OnPage39 -> "فصل اول"
            else -> BookTocNavigationHelper.chapterIdToLabelFa(
                chapterId = state.activeChapterId,
                shortTitleFa = state.activeChapterShortTitleFa.ifBlank { state.activeChapterTitleFa }
            )
        }
        val toolbarTitle = "آموزش شطرنج — $chapterDisplayName"

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = toolbarTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("sections_list_title")
                        )
                    },
                    navigationIcon = {
                        if (state.hasMultipleChapters || state.hasFrontMatter) {
                            IconButton(
                                onClick = { viewModel.openChaptersList() },
                                modifier = Modifier.testTag("back_to_chapters_btn")
                            ) {
                                Icon(
                                    imageVector = AppIcons.ArrowBack,
                                    contentDescription = "بازگشت به فهرست فصل‌ها"
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.openAudioSettings() },
                            modifier = Modifier.testTag("nav_audio_settings_btn")
                        ) {
                            Icon(
                                imageVector = if (state.audioSettings.soundEnabled) AppIcons.VolumeUp else AppIcons.VolumeOff,
                                contentDescription = "تنظیمات صدا"
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("settings_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.Settings,
                                contentDescription = "تنظیمات"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = modifier.testTag("sections_list_screen")
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("sections_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 1. Single Full-Width "ادامه مطالعه" Button (Replacing the old large book info card)
                item(key = "resume_reading_action_item") {
                    Button(
                        onClick = { viewModel.resumeReading() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("resume_reading_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ادامه مطالعه",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!state.lastNavigationNoticeFa.isNullOrBlank()) {
                    item(key = "sections_navigation_notice_banner") {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("navigation_notice_banner"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.lastNavigationNoticeFa,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissNavigationNotice() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "بستن اعلان"
                                    )
                                }
                            }
                        }
                    }
                }

                // Unified Main Book Table of Contents (پیش‌درآمد و فصل‌های ۱ تا ۱۵)
                if (state.hasFrontMatter || tocChapterGroups.isNotEmpty()) {
                    item(key = "unified_book_toc_header") {
                        Text(
                            text = "فهرست اصلی کتاب",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .testTag("unified_book_toc_header")
                        )
                    }

                    if (state.hasFrontMatter) {
                        item(key = "sec_front_matter_unified_item") {
                            BookTocRowCard(
                                rowTestTag = "front_matter_region_card",
                                badgeLabelFa = BookTocNavigationHelper.FRONT_MATTER_BADGE_LABEL_FA,
                                titleFa = BookTocNavigationHelper.FRONT_MATTER_ROW_TITLE_FA,
                                startPdfPage = BookTocNavigationHelper.FRONT_MATTER_START_PDF_PAGE,
                                endPdfPage = BookTocNavigationHelper.FRONT_MATTER_END_PDF_PAGE,
                                isReady = true,
                                isActive = state.isReadingFrontMatter && state.lastReadFrontMatterSourcePdfPage in BookTocNavigationHelper.FRONT_MATTER_START_PDF_PAGE..BookTocNavigationHelper.FRONT_MATTER_END_PDF_PAGE,
                                isExpanded = frontMatterTopicsExpanded,
                                subtopics = frontMatterTopics,
                                fontMultiplier = fontMultiplier,
                                titleTestTag = "front_matter_nav_title_text",
                                pageRangeTestTag = "front_matter_page_range_badge",
                                statusBadgeTestTag = "front_matter_status_badge",
                                expandBtnTestTag = "front_matter_toggle_topics_btn",
                                subtopicTagPrefix = "front_matter_toc_topic",
                                onRowClick = {
                                    viewModel.navigateToSourcePdfPage(
                                        BookTocNavigationHelper.FRONT_MATTER_START_PDF_PAGE,
                                        null
                                    )
                                },
                                onToggleExpand = { frontMatterTopicsExpanded = !frontMatterTopicsExpanded },
                                onSubtopicClick = { topic ->
                                    viewModel.navigateToSourcePdfPage(topic.targetSourcePdfPage, null)
                                }
                            )
                        }

                        item(key = "sec_part_one_divider") {
                            BookTocPartDivider(
                                titleFa = BookTocNavigationHelper.PART_ONE_TITLE_FA,
                                startPdfPage = BookTocNavigationHelper.PART_ONE_START_PDF_PAGE,
                                endPdfPage = BookTocNavigationHelper.PART_ONE_END_PDF_PAGE,
                                testTag = "toc_part_divider_1",
                                onClick = {
                                    viewModel.navigateToSourcePdfPage(
                                        BookTocNavigationHelper.PART_ONE_START_PDF_PAGE,
                                        null
                                    )
                                }
                            )
                        }
                    }

                    tocChapterGroups.forEach { tocChapter ->
                        if (tocChapter.chapterId == "CH09") {
                            item(key = "sec_part_two_divider") {
                                BookTocPartDivider(
                                    titleFa = BookTocNavigationHelper.PART_TWO_TITLE_FA,
                                    startPdfPage = BookTocNavigationHelper.PART_TWO_START_PDF_PAGE,
                                    endPdfPage = BookTocNavigationHelper.PART_TWO_END_PDF_PAGE,
                                    testTag = "toc_part_divider_2",
                                    onClick = null
                                )
                            }
                        }

                        item(key = "sec_list_${tocChapter.chapterId}") {
                            val isReady = tocChapter.chapterId in enabledChapterIds
                            val isCurrentChapter = !state.isReadingFrontMatter &&
                                isReady &&
                                state.activeChapterId == tocChapter.chapterId
                            val isCh01OnPage39 = tocChapter.chapterId == "CH01" &&
                                isReady &&
                                state.isReadingFrontMatter &&
                                state.lastReadFrontMatterSourcePdfPage == 39
                            val isActive = isCurrentChapter || isCh01OnPage39

                            BookTocRowCard(
                                rowTestTag = "chapter_card_${tocChapter.chapterId}",
                                badgeLabelFa = tocChapter.chapterLabelFa,
                                titleFa = tocChapter.chapterTitleFa,
                                startPdfPage = tocChapter.startPdfPage,
                                endPdfPage = tocChapter.endPdfPage,
                                isReady = isReady,
                                isActive = isActive,
                                isExpanded = expandedTocChapterId == tocChapter.chapterId,
                                subtopics = tocChapter.subtopics,
                                fontMultiplier = fontMultiplier,
                                titleTestTag = "chapter_title_${tocChapter.chapterId}",
                                pageRangeTestTag = "chapter_page_range_${tocChapter.chapterId}",
                                statusBadgeTestTag = "chapter_status_badge_${tocChapter.chapterId}",
                                expandBtnTestTag = "toc_expand_btn_${tocChapter.chapterId}",
                                subtopicTagPrefix = "toc_subtopic_${tocChapter.chapterId}",
                                onRowClick = {
                                    viewModel.navigateToSourcePdfPage(tocChapter.startPdfPage, tocChapter.chapterId)
                                },
                                onToggleExpand = {
                                    expandedTocChapterId = if (expandedTocChapterId == tocChapter.chapterId) {
                                        null
                                    } else {
                                        tocChapter.chapterId
                                    }
                                },
                                onSubtopicClick = { topic ->
                                    viewModel.navigateToSourcePdfPage(topic.targetSourcePdfPage, tocChapter.chapterId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
