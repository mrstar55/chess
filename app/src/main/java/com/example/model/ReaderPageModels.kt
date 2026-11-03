package com.example.model

import com.example.data.FontSizeScale
import com.example.ui.theme.SemanticBadgeCategory
import kotlin.math.ceil

/**
 * Page content classification derived deterministically from the actual blocks on the page
 * and the verified metadata of the referenced [DiagramItem].
 */
enum class ReaderPageType(val labelFa: String) {
    TEXT("متن آموزشی"),
    STATIC_DIAGRAM("مثال تصویری"),
    MOVE_REPLAY("بازپخش حرکات"),
    INTERACTIVE_EXERCISE("تمرین تعاملی"),
    POSITION_COMPARISON("مقایسه موقعیت"),
    SUMMARY("مرور و جمع‌بندی")
}

/**
 * Independent model representing a single fixed-viewport page in the book Reader.
 * Guarantees at most one diagram/exercise block per page while preserving the exact
 * sequence of [SectionContentBlock]s.
 */
data class ReaderPage(
    val pageId: String,
    val sectionId: String,
    val lessonId: String? = null,
    val pageType: ReaderPageType,
    val orderedBlocks: List<SectionContentBlock>,
    val pageNumber: Int,
    val totalPages: Int,
    val primaryDiagramId: String? = null,
    val semanticBadge: SemanticBadgeCategory = SemanticBadgeCategory.EDUCATIONAL_TEXT,
    val subtitleFa: String? = null
) {
    val diagramBlockCount: Int
        get() = orderedBlocks.count {
            it is SectionContentBlock.DiagramBlock || it is SectionContentBlock.ExerciseBlock
        }
}

/**
 * Deterministic pagination engine for converting a [SectionItem]'s [TranslationSlot.orderedContentBlocks]
 * into a sequence of [ReaderPage]s without vertical scrolling, content truncation, or block reordering.
 */
object ReaderPaginator {

    private const val SPLIT_PART_DELIMITER = "__part_"

    /**
     * Returns the original [SectionContentBlock.blockId] before any long-paragraph pagination split.
     */
    fun originalBlockId(blockId: String): String {
        val idx = blockId.indexOf(SPLIT_PART_DELIMITER)
        return if (idx >= 0) blockId.substring(0, idx) else blockId
    }

    /**
     * Deterministically paginates [section] given [diagrams], [fontScale], and viewport dimensions.
     *
     * Invariants enforced:
     * 1. Every [ReaderPage] has at most 1 [SectionContentBlock.DiagramBlock] or [SectionContentBlock.ExerciseBlock].
     * 2. The exact order of [TranslationSlot.orderedContentBlocks] is preserved.
     * 3. No paragraph or text is dropped; overly long paragraphs are split cleanly at sentence/word boundaries.
     * 4. A short explanatory paragraph immediately preceding a diagram stays with its diagram on the same page.
     * 5. Increasing [fontScale] reduces per-page character/line capacity and deterministically produces more pages when text is long.
     */
    fun paginateSection(
        section: SectionItem,
        diagrams: List<DiagramItem>,
        fontScale: FontSizeScale = FontSizeScale.NORMAL,
        viewportWidthDp: Int = 380,
        viewportHeightDp: Int = 640
    ): List<ReaderPage> {
        val slot = section.translationSlot
        val diagramsById = diagrams.associateBy { it.diagramId }

        // Compute deterministic capacity budgets from actual content viewport and fontScale
        val heightFactor = (viewportHeightDp.coerceAtLeast(200) / 640f)
        val widthFactor = (viewportWidthDp.coerceAtLeast(200) / 380f)
        val fontFactor = fontScale.scale * fontScale.scale
        val effectiveScale = (heightFactor * widthFactor) / fontFactor

        val maxCharsPerTextPage = (520f * effectiveScale).toInt().coerceIn(120, 1200)
        val maxLinesPerTextPage = (14f * (heightFactor / fontScale.scale)).toInt().coerceIn(4, 28)
        val charsPerLine = (36f * (widthFactor / fontScale.scale)).toInt().coerceIn(15, 65)
        val maxCompanionCharsOnDiagramPage = (180f * effectiveScale).toInt().coerceIn(50, 320)
        val maxCompanionLinesOnDiagramPage = (5f * (heightFactor / fontScale.scale)).toInt().coerceIn(4, 7)

        // Helper to check if a block is a diagram or exercise
        fun isDiagramOrExercise(b: SectionContentBlock): Boolean =
            b is SectionContentBlock.DiagramBlock || b is SectionContentBlock.ExerciseBlock

        // Helper to get visible text of a textual block (or placeholder notice if it's the active placeholder)
        fun visibleTextOf(b: SectionContentBlock): String? {
            return when (b) {
                is SectionContentBlock.ParagraphBlock -> {
                    val text = slot.resolveBlockText(b)
                    if (!text.isNullOrEmpty()) text
                    else if (slot.shouldShowPlaceholderForBlock(originalBlockId(b.blockId))) slot.placeholderNotice
                    else null
                }
                is SectionContentBlock.HeadingBlock -> {
                    val text = slot.resolveBlockText(b)
                    if (!text.isNullOrEmpty()) text
                    else if (slot.shouldShowPlaceholderForBlock(b.blockId)) slot.placeholderNotice
                    else null
                }
                is SectionContentBlock.FootnoteBlock -> {
                    val text = slot.resolveBlockText(b)
                    if (!text.isNullOrEmpty()) text
                    else if (slot.shouldShowPlaceholderForBlock(b.blockId)) slot.placeholderNotice
                    else null
                }
                is SectionContentBlock.ExternalReferenceBlock -> {
                    val text = slot.resolveBlockText(b)
                    if (b.isAuthorizedAndEnabled && !text.isNullOrEmpty()) text else null
                }
                is SectionContentBlock.DiagramBlock,
                is SectionContentBlock.ExerciseBlock -> null
            }
        }

        fun isVisibleTextBlock(b: SectionContentBlock): Boolean = visibleTextOf(b) != null

        fun estimateBlockLines(b: SectionContentBlock): Int {
            val text = visibleTextOf(b) ?: return 0
            val textLines = estimateRawTextLines(text, charsPerLine).coerceAtLeast(1)
            val chromeLines = when (b) {
                is SectionContentBlock.HeadingBlock -> 1
                else -> 2
            }
            return textLines + chromeLines
        }

        // Step 1: Expand ParagraphBlocks that exceed either character capacity or line capacity
        // into sequential deterministic chunks while keeping every block and character in exact sequence.
        val maxRawLinesPerFullPage = (maxLinesPerTextPage - 2).coerceAtLeast(1)
        val expandedBlocks = mutableListOf<SectionContentBlock>()
        var pendingHeadingLines = 0
        var pendingHeadingChars = 0

        for (block in slot.orderedContentBlocks) {
            when (block) {
                is SectionContentBlock.HeadingBlock -> {
                    val hLines = estimateBlockLines(block)
                    val hChars = visibleTextOf(block)?.length ?: 0
                    if (hLines > 0) {
                        pendingHeadingLines += hLines
                        pendingHeadingChars += hChars
                    }
                    expandedBlocks.add(block)
                }
                is SectionContentBlock.ParagraphBlock -> {
                    val resolved = slot.resolveBlockText(block)
                    if (!resolved.isNullOrEmpty()) {
                        val firstMaxLines = (maxLinesPerTextPage - pendingHeadingLines - 2).coerceAtLeast(1)
                        val firstMaxChars = (maxCharsPerTextPage - pendingHeadingChars).coerceAtLeast(40)
                        val rawLines = estimateRawTextLines(resolved, charsPerLine)
                        if (resolved.length > firstMaxChars || rawLines > firstMaxLines) {
                            val chunks = splitLongParagraphText(
                                text = resolved,
                                maxChunkChars = maxCharsPerTextPage,
                                maxChunkLines = maxRawLinesPerFullPage,
                                charsPerLine = charsPerLine,
                                firstChunkMaxChars = firstMaxChars,
                                firstChunkMaxLines = firstMaxLines
                            )
                            chunks.forEachIndexed { index, chunkText ->
                                val chunkId = if (index == 0) {
                                    block.blockId
                                } else {
                                    "${block.blockId}${SPLIT_PART_DELIMITER}${index + 1}"
                                }
                                expandedBlocks.add(
                                    block.copy(
                                        blockId = chunkId,
                                        textFa = chunkText,
                                        isPaginatedSlice = true
                                    )
                                )
                            }
                        } else {
                            expandedBlocks.add(block)
                        }
                        pendingHeadingLines = 0
                        pendingHeadingChars = 0
                    } else {
                        if (isVisibleTextBlock(block)) {
                            pendingHeadingLines = 0
                            pendingHeadingChars = 0
                        }
                        expandedBlocks.add(block)
                    }
                }
                else -> {
                    if (isDiagramOrExercise(block) || isVisibleTextBlock(block)) {
                        pendingHeadingLines = 0
                        pendingHeadingChars = 0
                    }
                    expandedBlocks.add(block)
                }
            }
        }

        if (expandedBlocks.isEmpty()) {
            val defaultType = if (isSummarySection(section)) ReaderPageType.SUMMARY else ReaderPageType.TEXT
            return listOf(
                ReaderPage(
                    pageId = "${section.sectionId}_PAGE_1",
                    sectionId = section.sectionId,
                    lessonId = section.sectionId,
                    pageType = defaultType,
                    orderedBlocks = emptyList(),
                    pageNumber = 1,
                    totalPages = 1,
                    primaryDiagramId = null,
                    semanticBadge = SemanticBadgeCategory.EDUCATIONAL_TEXT,
                    subtitleFa = section.titleFa
                )
            )
        }

        // Step 2: Group expandedBlocks into raw page block lists
        val rawPages = mutableListOf<MutableList<SectionContentBlock>>()
        var currentBlocks = mutableListOf<SectionContentBlock>()
        var currentLines = 0
        var currentChars = 0
        var currentHasDiagram = false

        fun flushCurrentPage() {
            if (currentBlocks.isNotEmpty()) {
                rawPages.add(currentBlocks)
                currentBlocks = mutableListOf()
                currentLines = 0
                currentChars = 0
                currentHasDiagram = false
            }
        }

        var i = 0
        while (i < expandedBlocks.size) {
            val block = expandedBlocks[i]

            if (isDiagramOrExercise(block)) {
                if (currentHasDiagram) {
                    // Never allow 2 diagrams on the same page
                    flushCurrentPage()
                }

                // Check what text blocks are currently accumulated on currentBlocks before this diagram
                val visibleTextIndices = currentBlocks.indices.filter { idx ->
                    isVisibleTextBlock(currentBlocks[idx])
                }

                if (visibleTextIndices.size >= 2) {
                    // Keep the immediately preceding companion paragraph (and any trailing zero-height blocks after it)
                    // with this diagram if it fits within companion char & line budgets; flush earlier paragraphs to previous page.
                    val lastVisibleIdx = visibleTextIndices.last()
                    val companionBlock = currentBlocks[lastVisibleIdx]
                    val companionLen = visibleTextOf(companionBlock)?.length ?: 0
                    val companionLines = estimateBlockLines(companionBlock)

                    // Also include a HeadingBlock right before companionBlock if present
                    val splitStartIdx = if (
                        lastVisibleIdx > 0 &&
                        currentBlocks[lastVisibleIdx - 1] is SectionContentBlock.HeadingBlock &&
                        visibleTextIndices.size > 2
                    ) {
                        lastVisibleIdx - 1
                    } else {
                        lastVisibleIdx
                    }

                    if (companionLen <= maxCompanionCharsOnDiagramPage &&
                        companionLines <= maxCompanionLinesOnDiagramPage
                    ) {
                        val earlierBlocks = currentBlocks.subList(0, splitStartIdx).toMutableList()
                        val companionSlice = currentBlocks.subList(splitStartIdx, currentBlocks.size).toMutableList()
                        if (earlierBlocks.isNotEmpty()) {
                            rawPages.add(earlierBlocks)
                        }
                        currentBlocks = companionSlice
                    } else {
                        flushCurrentPage()
                    }
                } else if (visibleTextIndices.size == 1) {
                    val onlyVisibleBlock = currentBlocks[visibleTextIndices.first()]
                    val len = visibleTextOf(onlyVisibleBlock)?.length ?: 0
                    val lines = estimateBlockLines(onlyVisibleBlock)
                    if (len > maxCompanionCharsOnDiagramPage || lines > maxCompanionLinesOnDiagramPage) {
                        // Paragraph is too long to share the viewport with a chessboard; put it on its own page first
                        flushCurrentPage()
                    }
                }

                // Add the diagram to currentBlocks
                currentBlocks.add(block)
                currentHasDiagram = true

                // Attach any immediately following zero-height (empty non-placeholder) blocks so order is preserved
                while (i + 1 < expandedBlocks.size &&
                    !isDiagramOrExercise(expandedBlocks[i + 1]) &&
                    !isVisibleTextBlock(expandedBlocks[i + 1])
                ) {
                    i++
                    currentBlocks.add(expandedBlocks[i])
                }

                // Check if there is a short trailing explanation right after the diagram
                // when there was no visible text before the diagram AND the next block is not followed by another diagram
                val hadVisibleTextBeforeDiagram = currentBlocks.any { !isDiagramOrExercise(it) && isVisibleTextBlock(it) }
                if (!hadVisibleTextBeforeDiagram && i + 1 < expandedBlocks.size) {
                    val nextBlock = expandedBlocks[i + 1]
                    val blockAfterNext = expandedBlocks.getOrNull(i + 2)
                    val nextLen = visibleTextOf(nextBlock)?.length ?: 0
                    val nextLines = estimateBlockLines(nextBlock)
                    val nextIsIntroToAnotherDiagram = blockAfterNext != null && isDiagramOrExercise(blockAfterNext)
                    if (!isDiagramOrExercise(nextBlock) &&
                        isVisibleTextBlock(nextBlock) &&
                        nextLen <= maxCompanionCharsOnDiagramPage &&
                        nextLines <= maxCompanionLinesOnDiagramPage &&
                        !nextIsIntroToAnotherDiagram
                    ) {
                        i++
                        currentBlocks.add(nextBlock)
                    }
                }

                // Close the diagram page so no additional diagram or long text crowds the board
                flushCurrentPage()
                i++
                continue
            }

            // Textual block (Paragraph, Heading, Footnote, ExternalReference)
            if (currentHasDiagram) {
                flushCurrentPage()
            }

            val blockLines = estimateBlockLines(block)
            val blockChars = visibleTextOf(block)?.length ?: 0

            if (blockLines == 0) {
                // Zero-height block (e.g. non-placeholder empty block); keep in sequence without consuming budget
                currentBlocks.add(block)
                i++
                continue
            }

            val visibleBlocksSoFar = currentBlocks.filter { isVisibleTextBlock(it) }
            val wouldOverflowLines = (currentLines + blockLines) > maxLinesPerTextPage
            val wouldOverflowChars = (currentChars + blockChars) > maxCharsPerTextPage

            if (visibleBlocksSoFar.isNotEmpty() && (wouldOverflowLines || wouldOverflowChars)) {
                flushCurrentPage()
            }

            currentBlocks.add(block)
            currentLines += blockLines
            currentChars += blockChars
            i++
        }

        flushCurrentPage()

        if (rawPages.isEmpty()) {
            rawPages.add(expandedBlocks.toMutableList())
        }

        // If any trailing page ended up with ONLY zero-height blocks and there is a previous page,
        // merge those zero-height blocks into the previous page so we don't create a blank page.
        if (rawPages.size > 1) {
            val lastPage = rawPages.last()
            val lastHasVisibleOrDiagram = lastPage.any { isDiagramOrExercise(it) || isVisibleTextBlock(it) }
            if (!lastHasVisibleOrDiagram) {
                val removed = rawPages.removeAt(rawPages.lastIndex)
                rawPages.last().addAll(removed)
            }
        }

        val totalPages = rawPages.size
        return rawPages.mapIndexed { index, blocks ->
            val pageNum = index + 1
            val (pageType, badge, diagramId) = classifyPage(section, blocks, diagramsById)
            val headingSubtitle = blocks.filterIsInstance<SectionContentBlock.HeadingBlock>()
                .firstNotNullOfOrNull { slot.resolveBlockText(it) }
                ?: diagramId?.let { "نمودار $it" }
                ?: section.titleFa

            ReaderPage(
                pageId = "${section.sectionId}_PAGE_$pageNum",
                sectionId = section.sectionId,
                lessonId = section.sectionId,
                pageType = pageType,
                orderedBlocks = blocks.toList(),
                pageNumber = pageNum,
                totalPages = totalPages,
                primaryDiagramId = diagramId,
                semanticBadge = badge,
                subtitleFa = headingSubtitle
            )
        }
    }

    /**
     * Classifies a page's [ReaderPageType] and [SemanticBadgeCategory] strictly from its blocks
     * and the actual [DiagramItem] metadata.
     */
    fun classifyPage(
        section: SectionItem,
        blocks: List<SectionContentBlock>,
        diagramsById: Map<String, DiagramItem>
    ): Triple<ReaderPageType, SemanticBadgeCategory, String?> {
        val exerciseBlock = blocks.filterIsInstance<SectionContentBlock.ExerciseBlock>().firstOrNull()
        val diagramBlock = blocks.filterIsInstance<SectionContentBlock.DiagramBlock>().firstOrNull()

        val diagramId = exerciseBlock?.diagramId ?: diagramBlock?.diagramId
        val diagram = diagramId?.let { diagramsById[it] }

        if (exerciseBlock != null ||
            (diagram != null && (
                diagram.recommendedInteraction.contains("exercise", ignoreCase = true) ||
                diagram.diagramKind.contains("exercise", ignoreCase = true) ||
                diagram.solutionPgnRefs.isNotEmpty()
            ))
        ) {
            val badge = if (diagram?.auditStatus == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW) {
                SemanticBadgeCategory.NEEDS_REVIEW
            } else {
                SemanticBadgeCategory.INTERACTIVE_EXERCISE
            }
            return Triple(ReaderPageType.INTERACTIVE_EXERCISE, badge, diagramId)
        }

        if (diagram != null) {
            val isComparison = diagram.diagramKind.contains("comparison", ignoreCase = true) ||
                diagram.recommendedInteraction.contains("comparison", ignoreCase = true)
            val isMoveReplay = !diagram.pgnRef.isNullOrBlank()

            val pageType = when {
                isComparison -> ReaderPageType.POSITION_COMPARISON
                isMoveReplay -> ReaderPageType.MOVE_REPLAY
                else -> ReaderPageType.STATIC_DIAGRAM
            }

            val badge = when {
                diagram.auditStatus == AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW ->
                    SemanticBadgeCategory.NEEDS_REVIEW
                pageType == ReaderPageType.POSITION_COMPARISON ->
                    SemanticBadgeCategory.POSITION_COMPARISON
                pageType == ReaderPageType.MOVE_REPLAY ->
                    SemanticBadgeCategory.MOVE_REPLAY
                else ->
                    SemanticBadgeCategory.VISUAL_EXAMPLE
            }

            return Triple(pageType, badge, diagram.diagramId)
        }

        val isSummary = isSummarySection(section)
        val pageType = if (isSummary) ReaderPageType.SUMMARY else ReaderPageType.TEXT
        return Triple(pageType, SemanticBadgeCategory.EDUCATIONAL_TEXT, null)
    }

    private fun isSummarySection(section: SectionItem): Boolean {
        return section.sectionId.contains("SUMMARY", ignoreCase = true) ||
            section.titleFa.contains("خلاصه") ||
            section.titleEn.contains("Summary", ignoreCase = true)
    }

    /**
     * Estimates how many visual lines [text] occupies given [charsPerLine], accounting for
     * explicit newline characters (`\n`) as well as line wrapping.
     */
    fun estimateRawTextLines(text: String, charsPerLine: Int): Int {
        if (text.isEmpty()) return 0
        val effCharsPerLine = charsPerLine.coerceAtLeast(1)
        var lines = 1
        var currentLineChars = 0
        var nextCharStartsNewLine = false
        for (ch in text) {
            if (nextCharStartsNewLine) {
                lines++
                currentLineChars = 0
                nextCharStartsNewLine = false
            }
            if (ch == '\n') {
                nextCharStartsNewLine = true
            } else {
                if (currentLineChars >= effCharsPerLine) {
                    lines++
                    currentLineChars = 1
                } else {
                    currentLineChars++
                }
            }
        }
        return lines
    }

    private fun maxAllowedPrefixLength(
        text: String,
        maxChars: Int,
        maxLines: Int,
        charsPerLine: Int
    ): Int {
        if (text.isEmpty()) return 0
        val effMaxChars = maxChars.coerceAtLeast(1)
        val effMaxLines = maxLines.coerceAtLeast(1)
        val effCharsPerLine = charsPerLine.coerceAtLeast(1)

        var lines = 1
        var currentLineChars = 0
        var nextCharStartsNewLine = false
        var index = 0

        while (index < text.length && index < effMaxChars) {
            val ch = text[index]
            val projectedLines = when {
                nextCharStartsNewLine -> lines + 1
                ch != '\n' && currentLineChars >= effCharsPerLine -> lines + 1
                else -> lines
            }
            if (projectedLines > effMaxLines) {
                break
            }
            if (nextCharStartsNewLine) {
                lines++
                currentLineChars = 0
                nextCharStartsNewLine = false
            }
            if (ch == '\n') {
                nextCharStartsNewLine = true
            } else {
                if (currentLineChars >= effCharsPerLine) {
                    lines++
                    currentLineChars = 1
                } else {
                    currentLineChars++
                }
            }
            index++
        }
        return index.coerceAtLeast(1).coerceAtMost(text.length)
    }

    /**
     * Deterministically splits a long paragraph into chunks of at most [maxChunkChars] characters
     * and at most [maxChunkLines] visual lines (at [charsPerLine] characters per line) at natural
     * sentence, punctuation, or word boundaries WITHOUT trimming, adding, or deleting any character.
     *
     * Mathematical invariant: `splitLongParagraphText(text, ...).joinToString("") == text`.
     */
    fun splitLongParagraphText(
        text: String,
        maxChunkChars: Int,
        maxChunkLines: Int = Int.MAX_VALUE,
        charsPerLine: Int = 36,
        firstChunkMaxChars: Int = maxChunkChars,
        firstChunkMaxLines: Int = maxChunkLines
    ): List<String> {
        if (text.isEmpty()) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text
        var isFirstChunk = true

        while (remaining.isNotEmpty()) {
            val activeMaxChars = (if (isFirstChunk) firstChunkMaxChars else maxChunkChars).coerceAtLeast(1)
            val activeMaxLines = (if (isFirstChunk) firstChunkMaxLines else maxChunkLines).coerceAtLeast(1)
            isFirstChunk = false

            val allowedLen = maxAllowedPrefixLength(
                text = remaining,
                maxChars = activeMaxChars,
                maxLines = activeMaxLines,
                charsPerLine = charsPerLine
            )

            if (allowedLen >= remaining.length) {
                chunks.add(remaining)
                break
            }

            val window = remaining.substring(0, allowedLen)
            val minAcceptableIndex = (allowedLen * 0.45).toInt()

            // Prefer newline or sentence-ending punctuation (. / ۔ / ؟ / ! / ؛), then comma (،), then space
            val newlineBreak = window.lastIndexOf('\n')
            val sentenceBreak = window.lastIndexOfAny(charArrayOf('.', '۔', '؟', '!', '؛'))
            val commaBreak = window.lastIndexOf('،')
            val spaceBreak = window.lastIndexOf(' ')

            val rawSplitPos = when {
                newlineBreak >= minAcceptableIndex -> newlineBreak + 1
                sentenceBreak >= minAcceptableIndex -> {
                    var pos = sentenceBreak + 1
                    if (pos < allowedLen && remaining[pos] == ' ') pos++
                    pos
                }
                commaBreak >= minAcceptableIndex -> {
                    var pos = commaBreak + 1
                    if (pos < allowedLen && remaining[pos] == ' ') pos++
                    pos
                }
                spaceBreak >= 0 -> spaceBreak + 1
                newlineBreak >= 0 -> newlineBreak + 1
                else -> allowedLen
            }.coerceIn(1, allowedLen)

            val part = remaining.substring(0, rawSplitPos)
            chunks.add(part)
            remaining = remaining.substring(rawSplitPos)
        }

        return chunks
    }
}
