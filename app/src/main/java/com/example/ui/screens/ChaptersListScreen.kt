package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChapterCatalogEntry
import com.example.ui.ChapterUiState
import com.example.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersListScreen(
    state: ChapterUiState,
    onSelectChapter: (String) -> Unit,
    onBackToIntroduction: () -> Unit,
    onStartFrontMatter: (Int) -> Unit = {},
    onResumeFrontMatter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val fontMultiplier = state.fontScale.scale

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "انتخاب فصل برای مطالعه",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (15 * fontMultiplier).sp
                                )
                                Text(
                                    text = "${state.availableChapters.size} فصل فعال در کتابخانه موجود است",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (state.hasFrontMatter) {
                    item {
                        FrontMatterRegionCard(
                            navigationTitleFa = state.frontMatterNavigationTitleFa,
                            fullTitleFa = state.frontMatterFullTitleFa,
                            lastReadPdfPage = state.lastReadFrontMatterSourcePdfPage,
                            isActiveRegion = state.isReadingFrontMatter,
                            onStartFromPage1 = { onStartFrontMatter(1) },
                            onResumeFrontMatter = onResumeFrontMatter,
                            onOpenIntroPage11 = { onStartFrontMatter(11) }
                        )
                    }
                }

                items(state.availableChapters, key = { it.chapterId }) { chapter ->
                    ChapterItemCard(
                        chapter = chapter,
                        isSelected = !state.isReadingFrontMatter && chapter.chapterId == state.activeChapterId,
                        fontMultiplier = fontMultiplier,
                        onClick = { onSelectChapter(chapter.chapterId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterItemCard(
    chapter: ChapterCatalogEntry,
    isSelected: Boolean,
    fontMultiplier: Float,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_card_${chapter.chapterId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${chapter.chapterNumber}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.titleFa,
                    fontWeight = FontWeight.Bold,
                    fontSize = (16 * fontMultiplier).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = chapter.titleEn,
                    fontSize = (13 * fontMultiplier).sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                imageVector = AppIcons.ChevronLeft,
                contentDescription = "مشاهده بخش‌های فصل",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
