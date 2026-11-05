package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FrontMatterTocEntry
import com.example.model.ReaderPaginator
import com.example.ui.ChapterUiState
import com.example.ui.theme.AppIcons

/**
 * Grouping of a book chapter from `front_matter/table_of_contents.json` (Chapters 1..15).
 */
data class BookTocChapterGroup(
    val chapterId: String,
    val chapterNumber: Int,
    val chapterLabelFa: String,
    val chapterTitleFa: String,
    val chapterTitleEn: String,
    val startPdfPage: Int,
    val endPdfPage: Int,
    val subtopics: List<FrontMatterTocEntry>
)

object BookTocNavigationHelper {

    const val FRONT_MATTER_ROW_TITLE_FA = "پیش‌درآمد و مبانی شطرنج"
    const val FRONT_MATTER_BADGE_LABEL_FA = "پیش‌درآمد"
    const val FRONT_MATTER_START_PDF_PAGE = 11
    const val FRONT_MATTER_END_PDF_PAGE = 36

    const val PART_ONE_TITLE_FA = "بخش اول — سطح مقدماتی"
    const val PART_ONE_START_PDF_PAGE = 37
    const val PART_ONE_END_PDF_PAGE = 38

    const val PART_TWO_TITLE_FA = "بخش دوم"
    const val PART_TWO_START_PDF_PAGE = 261
    const val PART_TWO_END_PDF_PAGE = 262

    /**
     * Authoritative physical PDF page ranges for all 15 chapters of the book.
     * Pages 37..38 are Part One divider; Pages 261..262 are Part Two divider.
     */
    val AUTHORITATIVE_CHAPTER_RANGES: Map<String, IntRange> = mapOf(
        "CH01" to (39..50),
        "CH02" to (51..85),
        "CH03" to (86..105),
        "CH04" to (106..134),
        "CH05" to (135..164),
        "CH06" to (165..184),
        "CH07" to (185..231),
        "CH08" to (232..260),
        "CH09" to (263..277),
        "CH10" to (278..302),
        "CH11" to (303..330),
        "CH12" to (331..376),
        "CH13" to (377..433),
        "CH14" to (434..474),
        "CH15" to (475..570)
    )

    /**
     * Extracts the 5 Front Matter subtopics from `front_matter/table_of_contents.json`
     * in their exact source order (pages 11..36).
     */
    fun extractFrontMatterTopics(entries: List<FrontMatterTocEntry>): List<FrontMatterTocEntry> {
        return entries.filter { entry ->
            entry.chapterId == null &&
                entry.entryKind.equals("topic", ignoreCase = true) &&
                entry.targetSourcePdfPage in FRONT_MATTER_START_PDF_PAGE..FRONT_MATTER_END_PDF_PAGE
        }
    }

    /**
     * Extracts all 15 chapters (CH01..CH15) from `front_matter/table_of_contents.json`.
     * Each chapter's `startPdfPage` is taken strictly from `entryKind = "chapter"`
     * (never from `chapterTitle`), and `endPdfPage` comes from [AUTHORITATIVE_CHAPTER_RANGES].
     */
    fun extractChapterGroups(entries: List<FrontMatterTocEntry>): List<BookTocChapterGroup> {
        if (entries.isEmpty()) return emptyList()
        val byChapterId = entries.filter { !it.chapterId.isNullOrBlank() }.groupBy { it.chapterId!! }
        val sortedChapterIds = byChapterId.keys.sortedBy {
            it.removePrefix("CH").toIntOrNull() ?: 999
        }
        return sortedChapterIds.mapIndexed { idx, chId ->
            val chEntries = byChapterId[chId].orEmpty()
            val chNum = chId.removePrefix("CH").toIntOrNull() ?: (idx + 1)
            val headerEntry = chEntries.firstOrNull {
                it.entryKind.equals("chapter", ignoreCase = true)
            }
            val titleEntry = chEntries.firstOrNull {
                it.entryKind.equals("chapterTitle", ignoreCase = true) ||
                    it.entryKind.equals("CHAPTER_TITLE", ignoreCase = true)
            }
            val subtopics = chEntries.filter {
                it.entryKind.equals("topic", ignoreCase = true) ||
                    it.entryKind.equals("section", ignoreCase = true)
            }
            val authoritativeRange = AUTHORITATIVE_CHAPTER_RANGES[chId]
            val startPage = headerEntry?.targetSourcePdfPage
                ?: authoritativeRange?.first
                ?: subtopics.firstOrNull()?.targetSourcePdfPage
                ?: 39
            val endPage = authoritativeRange?.last
                ?: subtopics.maxOfOrNull { it.targetSourcePdfPage }
                ?: startPage

            BookTocChapterGroup(
                chapterId = chId,
                chapterNumber = chNum,
                chapterLabelFa = headerEntry?.labelFa ?: "فصل ${ReaderPaginator.toPersianDigits(chNum)}",
                chapterTitleFa = titleEntry?.labelFa ?: headerEntry?.labelFa ?: chId,
                chapterTitleEn = titleEntry?.sourceLabel ?: headerEntry?.sourceLabel ?: chId,
                startPdfPage = startPage,
                endPdfPage = endPage,
                subtopics = subtopics
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersListScreen(
    state: ChapterUiState,
    onSelectChapter: (String) -> Unit,
    onBackToIntroduction: () -> Unit,
    onStartFrontMatter: (Int) -> Unit = {},
    onResumeFrontMatter: () -> Unit = {},
    onNavigateToPdfPage: (Int, String?) -> Unit = { pdfPage, chapterId ->
        if (pdfPage in 1..39 && chapterId == null) {
            onStartFrontMatter(pdfPage)
        } else if (chapterId != null && state.availableChapters.any { it.chapterId == chapterId }) {
            onSelectChapter(chapterId)
        }
    },
    onDismissNavigationNotice: () -> Unit = {},
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

    var frontMatterExpanded by remember { mutableStateOf(true) }
    var expandedChapterId by remember { mutableStateOf<String?>("CH01") }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "فهرست فصل‌های کتاب",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("chapters_list_title")
                            )
                            Text(
                                text = state.activeBookTitleFa,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackToIntroduction,
                            modifier = Modifier.testTag("back_to_intro_from_chapters_btn")
                        ) {
                            Icon(
                                imageVector = AppIcons.ArrowBack,
                                contentDescription = "بازگشت به معرفی کتاب"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = modifier.testTag("chapters_list_screen")
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .testTag("chapters_lazy_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (!state.lastNavigationNoticeFa.isNullOrBlank()) {
                    item(key = "navigation_notice_banner") {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("navigation_notice_banner"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    onClick = onDismissNavigationNotice,
                                    modifier = Modifier.size(28.dp)
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

                // 1. Row 1: پیش‌درآمد و مبانی شطرنج (PDF Pages 11 to 36 — identical row design, no fake chapter number)
                if (state.hasFrontMatter) {
                    item(key = "FRONT_MATTER_UNIFIED_ITEM") {
                        BookTocRowCard(
                            rowTestTag = "front_matter_region_card",
                            badgeLabelFa = BookTocNavigationHelper.FRONT_MATTER_BADGE_LABEL_FA,
                            titleFa = BookTocNavigationHelper.FRONT_MATTER_ROW_TITLE_FA,
                            startPdfPage = BookTocNavigationHelper.FRONT_MATTER_START_PDF_PAGE,
                            endPdfPage = BookTocNavigationHelper.FRONT_MATTER_END_PDF_PAGE,
                            isReady = true,
                            isActive = state.isReadingFrontMatter,
                            isExpanded = frontMatterExpanded,
                            subtopics = frontMatterTopics,
                            fontMultiplier = fontMultiplier,
                            titleTestTag = "front_matter_nav_title_text",
                            pageRangeTestTag = "front_matter_page_range_badge",
                            statusBadgeTestTag = "front_matter_status_badge",
                            expandBtnTestTag = "front_matter_toggle_topics_btn",
                            subtopicTagPrefix = "front_matter_toc_topic",
                            onRowClick = {
                                onNavigateToPdfPage(BookTocNavigationHelper.FRONT_MATTER_START_PDF_PAGE, null)
                            },
                            onToggleExpand = { frontMatterExpanded = !frontMatterExpanded },
                            onSubtopicClick = { topic ->
                                onNavigateToPdfPage(topic.targetSourcePdfPage, null)
                            }
                        )
                    }

                    // 2. Subtle Part One Divider (PDF Pages 37 to 38: بخش اول — سطح مقدماتی)
                    item(key = "PART_ONE_DIVIDER") {
                        BookTocPartDivider(
                            titleFa = BookTocNavigationHelper.PART_ONE_TITLE_FA,
                            startPdfPage = BookTocNavigationHelper.PART_ONE_START_PDF_PAGE,
                            endPdfPage = BookTocNavigationHelper.PART_ONE_END_PDF_PAGE,
                            testTag = "toc_part_divider_1",
                            onClick = {
                                onNavigateToPdfPage(BookTocNavigationHelper.PART_ONE_START_PDF_PAGE, null)
                            }
                        )
                    }
                }

                if (tocChapterGroups.isNotEmpty()) {
                    // 3. Chapters 1 to 15 (with Part Two Divider before CH09)
                    tocChapterGroups.forEach { tocChapter ->
                        if (tocChapter.chapterId == "CH09") {
                            item(key = "PART_TWO_DIVIDER") {
                                BookTocPartDivider(
                                    titleFa = BookTocNavigationHelper.PART_TWO_TITLE_FA,
                                    startPdfPage = BookTocNavigationHelper.PART_TWO_START_PDF_PAGE,
                                    endPdfPage = BookTocNavigationHelper.PART_TWO_END_PDF_PAGE,
                                    testTag = "toc_part_divider_2",
                                    onClick = {
                                        onNavigateToPdfPage(BookTocNavigationHelper.PART_TWO_START_PDF_PAGE, null)
                                    }
                                )
                            }
                        }

                        item(key = tocChapter.chapterId) {
                            val isReady = tocChapter.chapterId in enabledChapterIds
                            val isActive = !state.isReadingFrontMatter &&
                                isReady &&
                                state.activeChapterId == tocChapter.chapterId

                            BookTocRowCard(
                                rowTestTag = "chapter_card_${tocChapter.chapterId}",
                                badgeLabelFa = tocChapter.chapterLabelFa,
                                titleFa = tocChapter.chapterTitleFa,
                                startPdfPage = tocChapter.startPdfPage,
                                endPdfPage = tocChapter.endPdfPage,
                                isReady = isReady,
                                isActive = isActive,
                                isExpanded = expandedChapterId == tocChapter.chapterId,
                                subtopics = tocChapter.subtopics,
                                fontMultiplier = fontMultiplier,
                                titleTestTag = "chapter_title_${tocChapter.chapterId}",
                                pageRangeTestTag = "chapter_page_range_${tocChapter.chapterId}",
                                statusBadgeTestTag = "chapter_status_badge_${tocChapter.chapterId}",
                                expandBtnTestTag = "toc_expand_btn_${tocChapter.chapterId}",
                                subtopicTagPrefix = "toc_subtopic_${tocChapter.chapterId}",
                                onRowClick = {
                                    onNavigateToPdfPage(tocChapter.startPdfPage, tocChapter.chapterId)
                                },
                                onToggleExpand = {
                                    expandedChapterId = if (expandedChapterId == tocChapter.chapterId) {
                                        null
                                    } else {
                                        tocChapter.chapterId
                                    }
                                },
                                onSubtopicClick = { topic ->
                                    onNavigateToPdfPage(topic.targetSourcePdfPage, tocChapter.chapterId)
                                }
                            )
                        }
                    }
                } else {
                    // Fallback for synthetic multi-chapter tests without front_matter/table_of_contents.json
                    items(state.availableChapters, key = { it.chapterId }) { chapter ->
                        val authoritativeRange = BookTocNavigationHelper.AUTHORITATIVE_CHAPTER_RANGES[chapter.chapterId]
                        BookTocRowCard(
                            rowTestTag = "chapter_card_${chapter.chapterId}",
                            badgeLabelFa = "فصل ${ReaderPaginator.toPersianDigits(chapter.chapterNumber)}",
                            titleFa = chapter.titleFa,
                            startPdfPage = authoritativeRange?.first ?: 39,
                            endPdfPage = authoritativeRange?.last ?: 50,
                            isReady = true,
                            isActive = !state.isReadingFrontMatter && state.activeChapterId == chapter.chapterId,
                            isExpanded = false,
                            subtopics = emptyList(),
                            fontMultiplier = fontMultiplier,
                            titleTestTag = "chapter_title_${chapter.chapterId}",
                            pageRangeTestTag = "chapter_page_range_${chapter.chapterId}",
                            statusBadgeTestTag = "chapter_status_badge_${chapter.chapterId}",
                            expandBtnTestTag = "toc_expand_btn_${chapter.chapterId}",
                            subtopicTagPrefix = "toc_subtopic_${chapter.chapterId}",
                            onRowClick = { onSelectChapter(chapter.chapterId) },
                            onToggleExpand = {},
                            onSubtopicClick = {}
                        )
                    }
                }
            }
        }
    }
}

/**
 * Subtle section divider for Part One (pages 37–38) and Part Two (pages 261–262).
 */
@Composable
fun BookTocPartDivider(
    titleFa: String,
    startPdfPage: Int,
    endPdfPage: Int,
    testTag: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val startFa = ReaderPaginator.toPersianDigits(startPdfPage)
    val endFa = ReaderPaginator.toPersianDigits(endPdfPage)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(0.25f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = titleFa,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "صفحات PDF $startFa تا $endFa",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * Single unified Composable row card used for Front Matter ("پیش‌درآمد و مبانی شطرنج")
 * and all 15 chapters ("فصل اول" through "فصل پانزدهم").
 * Guarantees identical card shape, padding, spacing, title layout, and page range placement.
 */
@Composable
fun BookTocRowCard(
    rowTestTag: String,
    badgeLabelFa: String,
    titleFa: String,
    startPdfPage: Int,
    endPdfPage: Int,
    isReady: Boolean,
    isActive: Boolean,
    isExpanded: Boolean,
    subtopics: List<FrontMatterTocEntry>,
    fontMultiplier: Float,
    titleTestTag: String? = null,
    pageRangeTestTag: String? = null,
    statusBadgeTestTag: String? = null,
    expandBtnTestTag: String,
    subtopicTagPrefix: String,
    onRowClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onSubtopicClick: (FrontMatterTocEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val startFa = ReaderPaginator.toPersianDigits(startPdfPage)
    val endFa = ReaderPaginator.toPersianDigits(endPdfPage)
    val rangeTextFa = "صفحات PDF $startFa تا $endFa"

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedCard(
            onClick = onRowClick,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = if (isActive) 1.5.dp else 1.dp,
                color = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isReady -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
            ),
            colors = CardDefaults.outlinedCardColors(
                containerColor = when {
                    isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    isReady -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(rowTestTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Row badge ("پیش‌درآمد" or "فصل اول" .. "فصل پانزدهم")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReady) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = badgeLabelFa,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isReady) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleFa,
                        fontWeight = FontWeight.Bold,
                        fontSize = (14f * fontMultiplier).sp,
                        color = if (isReady) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                        modifier = if (titleTestTag != null) {
                            Modifier.testTag(titleTestTag)
                        } else {
                            Modifier
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isReady) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = rangeTextFa,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isReady) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .then(
                                        if (pageRangeTestTag != null) {
                                            Modifier.testTag(pageRangeTestTag)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }

                        if (!isReady) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "محتوا در حال آماده‌سازی",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .then(
                                            if (statusBadgeTestTag != null) {
                                                Modifier.testTag(statusBadgeTestTag)
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                        } else if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "در حال مطالعه",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .then(
                                            if (statusBadgeTestTag != null) {
                                                Modifier.testTag(statusBadgeTestTag)
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                        }
                    }
                }

                if (subtopics.isNotEmpty()) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag(expandBtnTestTag)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) AppIcons.ChevronRight else AppIcons.ChevronLeft,
                            contentDescription = if (isExpanded) "بستن زیرموضوعات" else "نمایش زیرموضوعات",
                            tint = if (isReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        if (isExpanded && subtopics.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 8.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                subtopics.forEachIndexed { idx, topic ->
                    val pageFa = ReaderPaginator.toPersianDigits(topic.targetSourcePdfPage)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubtopicClick(topic) }
                            .testTag("${subtopicTagPrefix}_$idx")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = topic.labelFa,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isReady) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Text(
                                    text = "صفحه $pageFa",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isReady) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
