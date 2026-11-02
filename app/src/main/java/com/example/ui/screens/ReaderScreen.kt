package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FontSizeScale
import com.example.model.AuditStatus
import com.example.model.DiagramItem
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import com.example.ui.AppTab
import com.example.ui.ChapterUiState
import com.example.ui.ChapterViewModel
import com.example.ui.components.AuditBadge
import com.example.ui.components.Bd032ExerciseView
import com.example.ui.components.ChessBoardView
import com.example.ui.components.PgnControlsView
import com.example.ui.theme.AppIcons

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

    // Enforce RTL for Persian book reader UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "کتاب تعاملی شطرنج — فصل اول",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentSection != null) {
                                Text(
                                    text = "بخش ${state.currentSectionIndex + 1}: ${currentSection.titleFa}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                        // Audit Report button
                        IconButton(
                            onClick = { viewModel.setTab(AppTab.AUDIT_REPORT) },
                            modifier = Modifier.testTag("nav_audit_btn")
                        ) {
                            Icon(AppIcons.Shield, contentDescription = "گزارش ممیزی")
                        }

                        // Font size scale selector
                        Box {
                            IconButton(onClick = { showFontMenu = true }) {
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
                                        }
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

            val orderedBlocks = currentSection.translationSlot.orderedContentBlocks

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .testTag("reader_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section Header (English subtitle hidden in UI; preserved internally in SectionItem.titleEn)
                item(key = "section_header_${currentSection.sectionId}") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSection.titleFa,
                        fontSize = (22 * fontMultiplier).sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "صفحات در فایل PDF: ${currentSection.pdfPages.joinToString("، ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Strictly sequential rendering of content blocks (Paragraphs, Headings, Diagrams, Exercises, Footnotes, External References)
                items(
                    items = orderedBlocks,
                    key = { block -> block.blockId }
                ) { block ->
                    SectionContentBlockItem(
                        block = block,
                        section = currentSection,
                        state = state,
                        viewModel = viewModel,
                        fontMultiplier = fontMultiplier
                    )
                }

                // Navigation controls between sections
                item(key = "section_nav_footer_${currentSection.sectionId}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.prevSection() },
                            enabled = state.currentSectionIndex > 0,
                            modifier = Modifier.testTag("btn_prev_section")
                        ) {
                            Icon(AppIcons.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بخش قبلی")
                        }

                        Text(
                            text = "${state.currentSectionIndex + 1} از ${state.sections.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )

                        FilledTonalButton(
                            onClick = { viewModel.nextSection() },
                            enabled = state.currentSectionIndex < state.sections.size - 1,
                            modifier = Modifier.testTag("btn_next_section")
                        ) {
                            Text("بخش بعدی")
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(AppIcons.ArrowForward, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
    fontMultiplier: Float
) {
    when (block) {
        is SectionContentBlock.ParagraphBlock -> {
            val resolvedText = section.translationSlot.resolveBlockText(block)
            if (!resolvedText.isNullOrBlank()) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("content_block_${block.blockId}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = resolvedText,
                        fontSize = (15 * fontMultiplier).sp,
                        lineHeight = (24 * fontMultiplier).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else if (section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)) {
                PendingTranslationSlotCard(
                    blockId = block.blockId,
                    sectionId = section.sectionId,
                    placeholderNotice = section.translationSlot.placeholderNotice,
                    fontMultiplier = fontMultiplier
                )
            }
        }

        is SectionContentBlock.HeadingBlock -> {
            val resolvedText = section.translationSlot.resolveBlockText(block)
            if (!resolvedText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("content_block_${block.blockId}")
                ) {
                    Text(
                        text = resolvedText,
                        fontSize = (18 * fontMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            } else if (section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)) {
                PendingTranslationSlotCard(
                    blockId = block.blockId,
                    sectionId = section.sectionId,
                    placeholderNotice = section.translationSlot.placeholderNotice,
                    fontMultiplier = fontMultiplier
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
                        fontMultiplier = fontMultiplier
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
                        fontMultiplier = fontMultiplier
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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = resolvedText,
                        fontSize = (12 * fontMultiplier).sp,
                        lineHeight = (18 * fontMultiplier).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)) {
                PendingTranslationSlotCard(
                    blockId = block.blockId,
                    sectionId = section.sectionId,
                    placeholderNotice = section.translationSlot.placeholderNotice,
                    fontMultiplier = fontMultiplier
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
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = resolvedTitle,
                        fontSize = (13 * fontMultiplier).sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
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
    fontMultiplier: Float
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("content_block_$blockId"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        modifier = Modifier.padding(end = 6.dp)
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = placeholderNotice,
                fontSize = (13 * fontMultiplier).sp,
                lineHeight = (20 * fontMultiplier).sp,
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
    fontMultiplier: Float
) {
    val isBookmarked = state.bookmarks.contains(diagram.diagramId)
    val pgnState = diagram.pgnRef?.let { state.pgnStates[it] }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagram_card_${diagram.diagramId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "صفحه ${diagram.pdfPage} کتاب",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = { viewModel.toggleBookmark(diagram.diagramId) }) {
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
                        onReset = { viewModel.resetBd032() }
                    )
                }
                else -> {
                    val boardFen = pgnState?.currentFen ?: diagram.initialFen
                    ChessBoardView(
                        fen = boardFen,
                        highlightSquares = diagram.highlightSquares
                    )

                    if (pgnState != null && diagram.auditStatus == AuditStatus.VALID) {
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
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
