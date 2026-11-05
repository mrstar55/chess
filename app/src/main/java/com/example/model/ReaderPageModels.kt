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
 * sequence of [SectionContentBlock]s and the authoritative [sourcePdfPage].
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
    val subtitleFa: String? = null,
    val contentRegionId: String? = null,
    val sourcePdfPage: Int? = null,
    val sliceIndexInSourcePage: Int = 1,
    val totalSlicesInSourcePage: Int = 1,
    val frontMatterPageKind: FrontMatterPageKind? = null,
    val pageNotesFa: String? = null,
    val sourceLinks: List<FrontMatterSourceLink> = emptyList()
) {
    val diagramBlockCount: Int
        get() = orderedBlocks.count {
            it is SectionContentBlock.DiagramBlock || it is SectionContentBlock.ExerciseBlock
        }

    val sourceImageBlockCount: Int
        get() = orderedBlocks.count { it is SectionContentBlock.SourceImageBlock }

    val sourcePdfPageDisplayLabelFa: String?
        get() {
            val pdfPage = sourcePdfPage ?: return null
            val pageFa = ReaderPaginator.toPersianDigits(pdfPage)
            return if (totalSlicesInSourcePage > 1) {
                val sliceFa = ReaderPaginator.toPersianDigits(sliceIndexInSourcePage)
                val totalFa = ReaderPaginator.toPersianDigits(totalSlicesInSourcePage)
                "صفحه PDF $pageFa — بخش $sliceFa از $totalFa"
            } else {
                "صفحه PDF $pageFa"
            }
        }
}

/**
 * Deterministic pagination engine for converting a [SectionItem]'s [TranslationSlot.orderedContentBlocks]
 * or a list of [FrontMatterPage]s into a sequence of [ReaderPage]s without vertical scrolling,
 * content truncation, block reordering, or merging across different [sourcePdfPage] boundaries.
 */
object ReaderPaginator {

    private const val SPLIT_PART_DELIMITER = "__part_"

    fun toPersianDigits(number: Int): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return number.toString().map { ch ->
            if (ch in '0'..'9') persianDigits[ch - '0'] else ch
        }.joinToString("")
    }

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
                    val text = slot.resolveBlockText(b) ?: b.url
                    if (!text.isNullOrEmpty()) text else null
                }
                is SectionContentBlock.OrderedListBlock,
                is SectionContentBlock.TableBlock -> {
                    val text = slot.resolveBlockText(b)
                    if (!text.isNullOrEmpty()) text else null
                }
                is SectionContentBlock.SourceImageBlock,
                is SectionContentBlock.DiagramBlock,
                is SectionContentBlock.ExerciseBlock -> null
            }
        }

        fun effectivePdfPageOf(b: SectionContentBlock): Int? = b.sourcePdfPage

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
        var lastExpandedPdfPage: Int? = null

        for (block in slot.orderedContentBlocks) {
            val blockPdfPage = effectivePdfPageOf(block)
            if (lastExpandedPdfPage != null && blockPdfPage != lastExpandedPdfPage) {
                pendingHeadingLines = 0
                pendingHeadingChars = 0
            }
            lastExpandedPdfPage = blockPdfPage

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
                                        isPaginatedSlice = true,
                                        sourcePdfPage = blockPdfPage
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
                    subtitleFa = section.titleFa,
                    sourcePdfPage = section.pdfPages.firstOrNull()
                )
            )
        }

        // Step 2: Group expandedBlocks into raw page block lists while strictly enforcing sourcePdfPage boundaries
        val rawPages = mutableListOf<MutableList<SectionContentBlock>>()
        var currentBlocks = mutableListOf<SectionContentBlock>()
        var currentLines = 0
        var currentChars = 0
        var currentHasDiagram = false
        var currentPdfPage: Int? = null

        fun flushCurrentPage() {
            if (currentBlocks.isNotEmpty()) {
                rawPages.add(currentBlocks)
                currentBlocks = mutableListOf()
                currentLines = 0
                currentChars = 0
                currentHasDiagram = false
                currentPdfPage = null
            }
        }

        var i = 0
        while (i < expandedBlocks.size) {
            val block = expandedBlocks[i]
            val blockPdfPage = effectivePdfPageOf(block)

            // Never mix blocks from two different sourcePdfPage values on the same ReaderPage
            if (currentBlocks.isNotEmpty() && blockPdfPage != currentPdfPage) {
                flushCurrentPage()
            }
            if (currentBlocks.isEmpty()) {
                currentPdfPage = blockPdfPage
            }

            if (isDiagramOrExercise(block)) {
                if (currentHasDiagram) {
                    // Never allow 2 diagrams on the same page
                    flushCurrentPage()
                    currentPdfPage = blockPdfPage
                }

                // Check what text blocks are currently accumulated on currentBlocks before this diagram
                val visibleTextIndices = currentBlocks.indices.filter { idx ->
                    isVisibleTextBlock(currentBlocks[idx])
                }

                if (block is SectionContentBlock.ExerciseBlock) {
                    // Interactive exercises render their own title, prompt, move bar, feedback, and action buttons,
                    // so flush any preceding text blocks to keep the exercise board at full standard size.
                    if (visibleTextIndices.isNotEmpty()) {
                        flushCurrentPage()
                        currentPdfPage = blockPdfPage
                    }
                } else if (visibleTextIndices.size >= 2) {
                    val lastVisibleIdx = visibleTextIndices.last()
                    val companionBlock = currentBlocks[lastVisibleIdx]
                    val companionLen = visibleTextOf(companionBlock)?.length ?: 0
                    val companionLines = estimateBlockLines(companionBlock)

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
                        currentPdfPage = blockPdfPage
                    } else {
                        flushCurrentPage()
                        currentPdfPage = blockPdfPage
                    }
                } else if (visibleTextIndices.size == 1) {
                    val onlyVisibleBlock = currentBlocks[visibleTextIndices.first()]
                    val len = visibleTextOf(onlyVisibleBlock)?.length ?: 0
                    val lines = estimateBlockLines(onlyVisibleBlock)
                    if (len > maxCompanionCharsOnDiagramPage || lines > maxCompanionLinesOnDiagramPage) {
                        flushCurrentPage()
                        currentPdfPage = blockPdfPage
                    }
                }

                // Add the diagram to currentBlocks
                currentBlocks.add(block)
                currentHasDiagram = true
                currentPdfPage = blockPdfPage

                // Attach any immediately following zero-height (empty non-placeholder) blocks on the SAME sourcePdfPage
                while (i + 1 < expandedBlocks.size &&
                    effectivePdfPageOf(expandedBlocks[i + 1]) == blockPdfPage &&
                    !isDiagramOrExercise(expandedBlocks[i + 1]) &&
                    !isVisibleTextBlock(expandedBlocks[i + 1])
                ) {
                    i++
                    currentBlocks.add(expandedBlocks[i])
                }

                // Check if there is a short trailing explanation right after a non-exercise diagram on the SAME sourcePdfPage
                val hadVisibleTextBeforeDiagram = currentBlocks.any { !isDiagramOrExercise(it) && isVisibleTextBlock(it) }
                if (block !is SectionContentBlock.ExerciseBlock && !hadVisibleTextBeforeDiagram && i + 1 < expandedBlocks.size) {
                    val nextBlock = expandedBlocks[i + 1]
                    val nextBlockPdfPage = effectivePdfPageOf(nextBlock)
                    val blockAfterNext = expandedBlocks.getOrNull(i + 2)
                    val nextLen = visibleTextOf(nextBlock)?.length ?: 0
                    val nextLines = estimateBlockLines(nextBlock)
                    val nextIsIntroToAnotherDiagram = blockAfterNext != null && isDiagramOrExercise(blockAfterNext)
                    if (nextBlockPdfPage == blockPdfPage &&
                        !isDiagramOrExercise(nextBlock) &&
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

            // Textual block (Paragraph, Heading, Footnote, ExternalReference, OrderedList, Table)
            if (currentHasDiagram) {
                flushCurrentPage()
                currentPdfPage = blockPdfPage
            }

            val blockLines = estimateBlockLines(block)
            val blockChars = visibleTextOf(block)?.length ?: 0

            if (blockLines == 0 && block !is SectionContentBlock.SourceImageBlock) {
                currentBlocks.add(block)
                i++
                continue
            }

            val visibleBlocksSoFar = currentBlocks.filter { isVisibleTextBlock(it) }
            val wouldOverflowLines = (currentLines + blockLines) > maxLinesPerTextPage
            val wouldOverflowChars = (currentChars + blockChars) > maxCharsPerTextPage

            if (visibleBlocksSoFar.isNotEmpty() && (wouldOverflowLines || wouldOverflowChars)) {
                flushCurrentPage()
                currentPdfPage = blockPdfPage
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

        // If any trailing page ended up with ONLY zero-height blocks on the SAME sourcePdfPage as the previous page,
        // merge those zero-height blocks into the previous page so we don't create a blank page.
        if (rawPages.size > 1) {
            val lastPage = rawPages.last()
            val prevPage = rawPages[rawPages.lastIndex - 1]
            val lastHasVisibleOrDiagram = lastPage.any {
                isDiagramOrExercise(it) || it is SectionContentBlock.SourceImageBlock || isVisibleTextBlock(it)
            }
            val lastPdf = lastPage.firstNotNullOfOrNull { effectivePdfPageOf(it) }
            val prevPdf = prevPage.firstNotNullOfOrNull { effectivePdfPageOf(it) }
            if (!lastHasVisibleOrDiagram && (lastPdf == null || lastPdf == prevPdf)) {
                val removed = rawPages.removeAt(rawPages.lastIndex)
                rawPages.last().addAll(removed)
            }
        }

        val totalPages = rawPages.size
        val resolvedPdfPages = rawPages.map { blocks ->
            blocks.firstNotNullOfOrNull { it.sourcePdfPage }
                ?: run {
                    val diagId = blocks.filterIsInstance<SectionContentBlock.ExerciseBlock>().firstOrNull()?.diagramId
                        ?: blocks.filterIsInstance<SectionContentBlock.DiagramBlock>().firstOrNull()?.diagramId
                    diagId?.let { diagramsById[it]?.pdfPage }
                }
                ?: section.pdfPages.firstOrNull()
        }
        val countsByPdfPage = resolvedPdfPages.groupingBy { it }.eachCount()
        val seenByPdfPage = mutableMapOf<Int?, Int>()

        return rawPages.mapIndexed { index, blocks ->
            val pageNum = index + 1
            val (pageType, badge, diagramId) = classifyPage(section, blocks, diagramsById)
            val headingSubtitle = blocks.filterIsInstance<SectionContentBlock.HeadingBlock>()
                .firstNotNullOfOrNull { slot.resolveBlockText(it) }
                ?: diagramId?.let { "نمودار $it" }
                ?: section.titleFa

            val pdfPage = resolvedPdfPages[index]
            val sliceIdx = (seenByPdfPage[pdfPage] ?: 0) + 1
            seenByPdfPage[pdfPage] = sliceIdx
            val totalSlices = countsByPdfPage[pdfPage] ?: 1

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
                subtitleFa = headingSubtitle,
                sourcePdfPage = pdfPage,
                sliceIndexInSourcePage = sliceIdx,
                totalSlicesInSourcePage = totalSlices
            )
        }
    }

    /**
     * Paginates all blocks belonging to a single physical PDF page ([pageEntry.sourcePdfPage])
     * in exact [ChapterSourcePageEntry.orderedBlockIds] order across all sections participating
     * in that page (`pageEntry.sectionIds`), guaranteeing that shared boundary pages (`40, 45, 46, 48`)
     * present their complete PDF page content in order without mixing blocks from adjacent PDF pages.
     */
    fun paginateChapterSourcePdfPage(
        pageEntry: ChapterSourcePageEntry,
        sections: List<SectionItem>,
        diagrams: List<DiagramItem>,
        fontScale: FontSizeScale = FontSizeScale.NORMAL,
        viewportWidthDp: Int = 380,
        viewportHeightDp: Int = 640
    ): List<ReaderPage> {
        val pdfPage = pageEntry.sourcePdfPage
        val allBlocksById = sections
            .flatMap { it.translationSlot.orderedContentBlocks }
            .associateBy { it.blockId }
        val pageBlocks = pageEntry.orderedBlockIds.mapNotNull { blockId ->
            allBlocksById[blockId]?.takeIf { it.sourcePdfPage == null || it.sourcePdfPage == pdfPage }
        }.ifEmpty {
            sections.flatMap { it.translationSlot.orderedContentBlocks }
                .filter { it.sourcePdfPage == pdfPage }
        }

        val primarySectionId = pageEntry.sectionIds.firstOrNull()
            ?: sections.firstOrNull { pdfPage in it.pdfPages }?.sectionId
            ?: sections.firstOrNull()?.sectionId
            ?: "CH01"
        val participatingSections = pageEntry.sectionIds.mapNotNull { secId ->
            sections.find { it.sectionId == secId }
        }.ifEmpty {
            sections.filter { pdfPage in it.pdfPages }
        }
        val primarySection = participatingSections.firstOrNull() ?: sections.first()
        val combinedSubtitleFa = if (participatingSections.size > 1) {
            participatingSections.joinToString(" • ") { it.titleFa }
        } else {
            primarySection.titleFa
        }
        val combinedTranslations = mutableMapOf<String, String>()
        for (sec in participatingSections) {
            combinedTranslations.putAll(sec.translationSlot.translationsByBlockId)
        }
        val diagramIds = pageBlocks.mapNotNull { block ->
            when (block) {
                is SectionContentBlock.DiagramBlock -> block.diagramId
                is SectionContentBlock.ExerciseBlock -> block.diagramId
                else -> null
            }
        }

        val syntheticSection = SectionItem(
            sectionId = primarySectionId,
            index = primarySection.index,
            titleEn = primarySection.titleEn,
            titleFa = combinedSubtitleFa,
            pdfPages = listOf(pdfPage),
            diagramIds = diagramIds,
            translationSlot = TranslationSlot(
                sectionId = primarySectionId,
                orderedContentBlocks = pageBlocks,
                placeholderNotice = primarySection.translationSlot.placeholderNotice,
                translationStatus = primarySection.translationSlot.translationStatus,
                translationsByBlockId = combinedTranslations
            ),
            sectionKind = primarySection.sectionKind
        )

        val slices = paginateSection(
            section = syntheticSection,
            diagrams = diagrams,
            fontScale = fontScale,
            viewportWidthDp = viewportWidthDp,
            viewportHeightDp = viewportHeightDp
        )
        val total = slices.size
        return slices.mapIndexed { idx, slice ->
            slice.copy(
                pageId = "PDF_P${pdfPage.toString().padStart(3, '0')}_SLICE_${idx + 1}",
                sectionId = primarySectionId,
                lessonId = primarySectionId,
                sourcePdfPage = pdfPage,
                sliceIndexInSourcePage = idx + 1,
                totalSlicesInSourcePage = total
            )
        }
    }

    /**
     * Deterministically paginates a single [FrontMatterPage] into one or more [ReaderPage] slices.
     *
     * Authoritative `sourcePdfPage` contract:
     * - Every slice retains `sourcePdfPage == fmPage.sourcePdfPage`.
     * - Every block inside every slice retains `sourcePdfPage == fmPage.sourcePdfPage`.
     * - Content from two different `sourcePdfPage` pages is never merged.
     * - Image-only pages (`2, 5, 10, 39`) always produce an independent page with their `SourceImageBlock`.
     * - `PART_DIVIDER` (page 37), `COVER` (page 1), and `TITLE_PAGE` (page 3) keep their master image
     *   and their Persian UI text layer on the same page.
     */
    fun paginateFrontMatterPage(
        fmPage: FrontMatterPage,
        fontScale: FontSizeScale = FontSizeScale.NORMAL,
        viewportWidthDp: Int = 380,
        viewportHeightDp: Int = 640
    ): List<ReaderPage> {
        val pdfPage = fmPage.sourcePdfPage
        val sectionId = "FM_PAGE_${pdfPage.toString().padStart(3, '0')}"
        val renderableBlocks = fmPage.buildRenderableBlocks()

        // Single-page visual divider / cover / title / interstitial / image-only pages stay atomic
        val keepAtomicSinglePage = when (fmPage.pageKind) {
            FrontMatterPageKind.COVER,
            FrontMatterPageKind.IMAGE_ONLY,
            FrontMatterPageKind.TITLE_PAGE,
            FrontMatterPageKind.PART_DIVIDER,
            FrontMatterPageKind.INTERSTITIAL_IMAGE -> true
            else -> false
        }

        if (keepAtomicSinglePage) {
            val hasImage = renderableBlocks.any { it is SectionContentBlock.SourceImageBlock }
            val pageType = if (hasImage) ReaderPageType.STATIC_DIAGRAM else ReaderPageType.TEXT
            val badge = if (hasImage) SemanticBadgeCategory.VISUAL_EXAMPLE else SemanticBadgeCategory.EDUCATIONAL_TEXT
            return listOf(
                ReaderPage(
                    pageId = "${sectionId}_SLICE_1",
                    sectionId = sectionId,
                    lessonId = sectionId,
                    pageType = pageType,
                    orderedBlocks = renderableBlocks,
                    pageNumber = 1,
                    totalPages = 1,
                    primaryDiagramId = null,
                    semanticBadge = badge,
                    subtitleFa = fmPage.pageTitleFa,
                    contentRegionId = fmPage.contentRegionId,
                    sourcePdfPage = pdfPage,
                    sliceIndexInSourcePage = 1,
                    totalSlicesInSourcePage = 1,
                    frontMatterPageKind = fmPage.pageKind,
                    pageNotesFa = fmPage.notesFa,
                    sourceLinks = fmPage.sourceLinks
                )
            )
        }

        val heightFactor = (viewportHeightDp.coerceAtLeast(200) / 640f)
        val widthFactor = (viewportWidthDp.coerceAtLeast(200) / 380f)
        val fontFactor = fontScale.scale * fontScale.scale
        val effectiveScale = (heightFactor * widthFactor) / fontFactor

        val isTocOrPartIntro = fmPage.pageKind == FrontMatterPageKind.TABLE_OF_CONTENTS ||
            fmPage.pageKind == FrontMatterPageKind.PART_INTRODUCTION
        val maxCharsPerTextPage = ((if (isTocOrPartIntro) 840f else 820f) * effectiveScale).toInt().coerceIn(220, 1600)
        val maxLinesPerTextPage = ((if (isTocOrPartIntro) 24f else 24f) * (heightFactor / fontScale.scale)).toInt().coerceIn(8, 36)
        val charsPerLine = (36f * (widthFactor / fontScale.scale)).toInt().coerceIn(15, 65)
        val maxCompanionCharsWithSourceImage = (760f * effectiveScale).toInt().coerceIn(200, 1100)
        val maxCompanionLinesWithSourceImage = (22f * (heightFactor / fontScale.scale)).toInt().coerceIn(6, 28)

        fun blockTextOf(b: SectionContentBlock): String? {
            return when (b) {
                is SectionContentBlock.ParagraphBlock -> b.textFa
                is SectionContentBlock.HeadingBlock -> b.textFa
                is SectionContentBlock.FootnoteBlock -> b.textFa
                is SectionContentBlock.ExternalReferenceBlock -> b.titleFa
                is SectionContentBlock.OrderedListBlock -> {
                    val itemsText = b.items.mapIndexed { idx, item -> "${idx + 1}. $item" }.joinToString("\n")
                    if (!b.titleFa.isNullOrBlank()) "${b.titleFa}\n$itemsText" else itemsText
                }
                is SectionContentBlock.TableBlock -> {
                    val rowsText = b.rows.joinToString("\n") { row -> row.joinToString(" : ") }
                    if (!b.titleFa.isNullOrBlank()) "${b.titleFa}\n$rowsText" else rowsText
                }
                is SectionContentBlock.SourceImageBlock,
                is SectionContentBlock.DiagramBlock,
                is SectionContentBlock.ExerciseBlock -> null
            }
        }

        fun estimateLines(b: SectionContentBlock): Int {
            return when (b) {
                is SectionContentBlock.SourceImageBlock -> if (b.isSynthesizedFromPageAsset) 2 else 6
                is SectionContentBlock.OrderedListBlock -> (b.items.size + 2).coerceAtLeast(3)
                is SectionContentBlock.TableBlock -> (b.rows.size + 2).coerceAtLeast(3)
                is SectionContentBlock.HeadingBlock -> {
                    val txt = b.textFa.orEmpty()
                    estimateRawTextLines(txt, charsPerLine).coerceAtLeast(1) + 1
                }
                else -> {
                    val txt = blockTextOf(b) ?: return 0
                    estimateRawTextLines(txt, charsPerLine).coerceAtLeast(1) + 2
                }
            }
        }

        // Expand overly long ParagraphBlocks while preserving sourcePdfPage on every chunk
        val maxRawLinesPerFullPage = (maxLinesPerTextPage - 2).coerceAtLeast(2)
        val expandedBlocks = mutableListOf<SectionContentBlock>()
        for (block in renderableBlocks) {
            if (block is SectionContentBlock.ParagraphBlock && !block.textFa.isNullOrEmpty()) {
                val resolved = block.textFa
                val rawLines = estimateRawTextLines(resolved, charsPerLine)
                if (resolved.length > maxCharsPerTextPage || rawLines > maxRawLinesPerFullPage) {
                    val chunks = splitLongParagraphText(
                        text = resolved,
                        maxChunkChars = maxCharsPerTextPage,
                        maxChunkLines = maxRawLinesPerFullPage,
                        charsPerLine = charsPerLine
                    )
                    chunks.forEachIndexed { idx, chunkText ->
                        val chunkId = if (idx == 0) block.blockId else "${block.blockId}${SPLIT_PART_DELIMITER}${idx + 1}"
                        expandedBlocks.add(
                            block.copy(
                                blockId = chunkId,
                                textFa = chunkText,
                                isPaginatedSlice = true,
                                sourcePdfPage = pdfPage
                            )
                        )
                    }
                } else {
                    expandedBlocks.add(block)
                }
            } else {
                expandedBlocks.add(block)
            }
        }

        val rawSlices = mutableListOf<MutableList<SectionContentBlock>>()
        var currentBlocks = mutableListOf<SectionContentBlock>()
        var currentLines = 0
        var currentChars = 0
        var currentHasMajorSourceImage = false

        fun flushSlice() {
            if (currentBlocks.isNotEmpty()) {
                rawSlices.add(currentBlocks)
                currentBlocks = mutableListOf()
                currentLines = 0
                currentChars = 0
                currentHasMajorSourceImage = false
            }
        }

        var idx = 0
        while (idx < expandedBlocks.size) {
            val block = expandedBlocks[idx]
            if (block is SectionContentBlock.SourceImageBlock && !block.isSynthesizedFromPageAsset) {
                val assetMeta = fmPage.sourceAssets.find { it.assetId == block.assetId }
                val isSmallPieceIcon = assetMeta != null && assetMeta.width <= 320 && assetMeta.height <= 320
                if (currentHasMajorSourceImage && !isSmallPieceIcon) {
                    flushSlice()
                } else if (currentBlocks.any { it !is SectionContentBlock.SourceImageBlock && it !is SectionContentBlock.HeadingBlock }) {
                    // Flush preceding text paragraphs before starting a new diagram/piece image section
                    flushSlice()
                }

                currentBlocks.add(block)
                if (!isSmallPieceIcon) {
                    currentHasMajorSourceImage = true
                }
                currentLines += estimateLines(block)

                // If another small piece icon immediately follows (e.g., pages 15, 16, 17, 19), keep them together
                while (idx + 1 < expandedBlocks.size) {
                    val nextBlk = expandedBlocks[idx + 1]
                    if (nextBlk is SectionContentBlock.SourceImageBlock && !nextBlk.isSynthesizedFromPageAsset) {
                        val nextMeta = fmPage.sourceAssets.find { it.assetId == nextBlk.assetId }
                        if (nextMeta != null && nextMeta.width <= 320 && nextMeta.height <= 320) {
                            idx++
                            currentBlocks.add(nextBlk)
                            currentLines += 2
                            continue
                        }
                    }
                    break
                }

                // Attach companion paragraph(s) right after the image if they fit
                var companionCharsSoFar = 0
                var companionLinesSoFar = 0
                while (idx + 1 < expandedBlocks.size) {
                    val nextBlk = expandedBlocks[idx + 1]
                    if (nextBlk !is SectionContentBlock.SourceImageBlock && nextBlk !is SectionContentBlock.HeadingBlock) {
                        val nextTxt = blockTextOf(nextBlk).orEmpty()
                        val nextLines = estimateRawTextLines(nextTxt, charsPerLine)
                        if (companionCharsSoFar + nextTxt.length <= maxCompanionCharsWithSourceImage &&
                            companionLinesSoFar + nextLines <= maxCompanionLinesWithSourceImage
                        ) {
                            idx++
                            currentBlocks.add(nextBlk)
                            companionCharsSoFar += nextTxt.length
                            companionLinesSoFar += nextLines
                            continue
                        }
                    }
                    break
                }
                flushSlice()
                idx++
                continue
            }

            val bLines = estimateLines(block)
            val bChars = blockTextOf(block)?.length ?: 0

            val hasNonHeaderContent = currentBlocks.any {
                !(it is SectionContentBlock.SourceImageBlock && it.isSynthesizedFromPageAsset)
            }
            val wouldOverflowLines = (currentLines + bLines) > maxLinesPerTextPage
            val wouldOverflowChars = (currentChars + bChars) > maxCharsPerTextPage

            if (hasNonHeaderContent && (wouldOverflowLines || wouldOverflowChars)) {
                flushSlice()
            }

            currentBlocks.add(block)
            currentLines += bLines
            currentChars += bChars
            idx++
        }

        flushSlice()

        if (rawSlices.isEmpty()) {
            rawSlices.add(expandedBlocks.toMutableList())
        }

        val totalSlices = rawSlices.size
        return rawSlices.mapIndexed { sIdx, sliceBlocks ->
            val sliceNum = sIdx + 1
            val hasSourceImg = sliceBlocks.any { it is SectionContentBlock.SourceImageBlock }
            val pageType = if (hasSourceImg) ReaderPageType.STATIC_DIAGRAM else ReaderPageType.TEXT
            val badge = if (hasSourceImg) SemanticBadgeCategory.VISUAL_EXAMPLE else SemanticBadgeCategory.EDUCATIONAL_TEXT
            val subtitle = sliceBlocks.filterIsInstance<SectionContentBlock.HeadingBlock>()
                .firstNotNullOfOrNull { it.textFa }
                ?: fmPage.pageTitleFa

            ReaderPage(
                pageId = "${sectionId}_SLICE_$sliceNum",
                sectionId = sectionId,
                lessonId = sectionId,
                pageType = pageType,
                orderedBlocks = sliceBlocks.toList(),
                pageNumber = sliceNum,
                totalPages = totalSlices,
                primaryDiagramId = null,
                semanticBadge = badge,
                subtitleFa = subtitle,
                contentRegionId = fmPage.contentRegionId,
                sourcePdfPage = pdfPage,
                sliceIndexInSourcePage = sliceNum,
                totalSlicesInSourcePage = totalSlices,
                frontMatterPageKind = fmPage.pageKind,
                pageNotesFa = fmPage.notesFa,
                sourceLinks = fmPage.sourceLinks
            )
        }
    }

    /**
     * Paginates all 39 [FrontMatterPage]s (`1..39`) in strict `sourcePdfPage` order into a unified
     * list of [ReaderPage]s for the `FRONT_MATTER` content region while preserving each slice's
     * authoritative `sourcePdfPage`, `sliceIndexInSourcePage`, and `totalSlicesInSourcePage`.
     */
    fun paginateFrontMatterPages(
        frontMatterPages: List<FrontMatterPage>,
        fontScale: FontSizeScale = FontSizeScale.NORMAL,
        viewportWidthDp: Int = 380,
        viewportHeightDp: Int = 640
    ): List<ReaderPage> {
        val sortedPages = frontMatterPages.sortedBy { it.sourcePdfPage }
        val allSlices = sortedPages.flatMap { fmPage ->
            paginateFrontMatterPage(
                fmPage = fmPage,
                fontScale = fontScale,
                viewportWidthDp = viewportWidthDp,
                viewportHeightDp = viewportHeightDp
            )
        }
        val total = allSlices.size
        return allSlices.mapIndexed { idx, slice ->
            slice.copy(
                pageNumber = idx + 1,
                totalPages = total
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
        val sourceImageBlock = blocks.filterIsInstance<SectionContentBlock.SourceImageBlock>().firstOrNull()

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

        if (sourceImageBlock != null) {
            return Triple(ReaderPageType.STATIC_DIAGRAM, SemanticBadgeCategory.VISUAL_EXAMPLE, null)
        }

        val isSummary = isSummarySection(section)
        val pageType = if (isSummary) ReaderPageType.SUMMARY else ReaderPageType.TEXT
        return Triple(pageType, SemanticBadgeCategory.EDUCATIONAL_TEXT, null)
    }

    fun isSummarySection(section: SectionItem): Boolean {
        return section.sectionKind == SectionKind.SUMMARY
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
