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

        val chapterDisplayName = state.activeChapterShortTitleFa.ifBlank { state.activeChapterTitleFa }
        val totalDiagramsCount = state.diagrams.size
        val validDiagramsCount = state.diagrams.count { it.auditStatus == com.example.model.AuditStatus.VALID }
        val blockedDiagramsCount = state.diagrams.count { it.auditStatus == com.example.model.AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "آموزش شطرنج — $chapterDisplayName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
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
                // Book Info Header Card
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("♔", fontSize = 18.sp, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "کتاب تعاملی آموزش شطرنج — $chapterDisplayName",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "کتاب‌خوان تعاملی فارسی — ${state.activeChapterTitleFa}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Stats row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$totalDiagramsCount دیاگرام رندر شده از FEN", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "$validDiagramsCount تأیید | $blockedDiagramsCount نیازمند بازبینی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (blockedDiagramsCount == 0) Color(0xFF16A34A) else Color(0xFFD97706),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.resumeReading() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("resume_reading_btn")
                                ) {
                                    Icon(AppIcons.AutoStories, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ادامه مطالعه", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { viewModel.setTab(AppTab.AUDIT_REPORT) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("view_audit_btn")
                                ) {
                                    Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("گزارش ممیزی", fontSize = 12.sp)
                                }
                            }
                        }
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

                // Direct source PDF page navigation strip (Pages 40..50 from source_page_index.json)
                val sourcePages = state.chapterSourcePageIndex?.allSourcePdfPages.orEmpty()
                if (sourcePages.isNotEmpty()) {
                    item {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chapter_pdf_pages_index_card"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "ناوبری مستقیم صفحات PDF فصل (${ReaderPaginator.toPersianDigits(sourcePages.first())} تا ${ReaderPaginator.toPersianDigits(sourcePages.last())})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    sourcePages.forEach { pdfPage ->
                                        val isCurrentPdf = !state.isReadingFrontMatter &&
                                            state.currentPage?.sourcePdfPage == pdfPage
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrentPdf) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.navigateToSourcePdfPage(pdfPage) }
                                                .testTag("chapter_pdf_page_chip_$pdfPage")
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = ReaderPaginator.toPersianDigits(pdfPage),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrentPdf) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6 sections list of active chapter
                itemsIndexed(state.sections) { index, section ->
                    val isCurrent = !state.isReadingFrontMatter && index == state.currentSectionIndex
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSection(index) }
                            .testTag("section_item_$index"),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.titleFa,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "صفحات ${section.pdfPages.joinToString("، ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (section.diagramIds.isNotEmpty()) {
                                    Text(
                                        text = "${section.diagramIds.size} دیاگرام (${section.diagramIds.joinToString(", ")})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Icon(
                                imageVector = AppIcons.ChevronLeft,
                                contentDescription = "مشاهده بخش",
                                tint = MaterialTheme.colorScheme.outline
                            )
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
                            modifier = Modifier.padding(top = 6.dp)
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
