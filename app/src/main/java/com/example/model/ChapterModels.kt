package com.example.model

enum class PieceType(val whiteSymbol: String, val blackSymbol: String) {
    KING("♔", "♚"),
    QUEEN("♕", "♛"),
    ROOK("♖", "♜"),
    BISHOP("♗", "♝"),
    KNIGHT("♘", "♞"),
    PAWN("♙", "♟")
}

data class ChessPiece(
    val type: PieceType,
    val isWhite: Boolean
) {
    val symbol: String
        get() = if (isWhite) type.whiteSymbol else type.blackSymbol
}

enum class AuditStatus {
    VALID,
    BLOCKED_NEEDS_HUMAN_REVIEW
}

enum class TranslationStatus {
    PENDING,
    PARTIAL,
    COMPLETE;

    companion object {
        fun fromString(value: String?): TranslationStatus {
            return when (value?.trim()?.uppercase()) {
                "COMPLETE", "READY", "TRANSLATED", "DRAFT_TRANSLATION_REQUIRES_EDITORIAL_REVIEW" -> COMPLETE
                "PARTIAL", "IN_PROGRESS" -> PARTIAL
                else -> PENDING
            }
        }
    }
}

enum class SectionKind {
    OPENING,
    CONTENT,
    SUMMARY,
    REVIEW,
    CUSTOM;

    companion object {
        fun fromString(value: String?): SectionKind {
            return when (value?.trim()?.uppercase()) {
                "OPENING" -> OPENING
                "CONTENT" -> CONTENT
                "SUMMARY" -> SUMMARY
                "REVIEW" -> REVIEW
                "CUSTOM" -> CUSTOM
                else -> CONTENT
            }
        }

        fun fromSectionMetadata(
            sectionId: String,
            titleEn: String = "",
            titleFa: String = ""
        ): SectionKind {
            return when {
                sectionId.contains("SUMMARY", ignoreCase = true) ||
                    titleEn.contains("Summary", ignoreCase = true) ||
                    titleFa.contains("خلاصه") ||
                    titleFa.contains("جمع‌بندی") -> SUMMARY
                sectionId.contains("OPENING", ignoreCase = true) ||
                    titleEn.contains("opening", ignoreCase = true) -> OPENING
                else -> CONTENT
            }
        }
    }
}

data class LibraryCatalog(
    val schemaVersion: Int = 1,
    val defaultBookId: String,
    val books: List<BookCatalogEntry>
) {
    fun findBook(bookId: String = defaultBookId): BookCatalogEntry? {
        return books.find { it.bookId == bookId } ?: books.firstOrNull()
    }
}

data class BookCatalogEntry(
    val bookId: String,
    val titleEn: String,
    val titleFa: String,
    val authorEn: String,
    val authorFa: String,
    val chapters: List<ChapterCatalogEntry>,
    val frontMatter: FrontMatterCatalogEntry? = null
) {
    val enabledChapters: List<ChapterCatalogEntry>
        get() = chapters.filter { it.enabled }.sortedBy { it.chapterNumber }

    val firstEnabledChapter: ChapterCatalogEntry?
        get() = enabledChapters.firstOrNull()

    val hasEnabledFrontMatter: Boolean
        get() = frontMatter != null && frontMatter.enabled
}

data class ChapterCatalogEntry(
    val chapterId: String,
    val chapterNumber: Int,
    val titleEn: String,
    val titleFa: String,
    val manifestPath: String,
    val enabled: Boolean = true
)

data class SectionManifestEntry(
    val sectionId: String,
    val order: Int,
    val sectionKind: SectionKind,
    val titleEn: String,
    val titleFa: String,
    val pdfPages: List<Int>,
    val contentFile: String
)

data class ChapterManifest(
    val schemaVersion: Int = 1,
    val bookId: String,
    val chapterId: String,
    val chapterNumber: Int,
    val titleEn: String,
    val titleFa: String,
    val diagramsFile: String,
    val pgnFile: String,
    val exercisesFile: String,
    val narrationManifest: String,
    val sections: List<SectionManifestEntry>
)

/**
 * Domain-level phase for any interactive chess exercise.
 * Independent of UI composables and specific chapter/diagram IDs.
 */
enum class ExercisePhase {
    IDLE,
    PIECE_SELECTED,
    ANIMATING_CORRECT,
    SOLVED,
    ANIMATING_WRONG,
    WRONG_LEGAL_MOVE
}

/**
 * Domain-level state for any interactive chess exercise.
 * Each exercise instance in a chapter maintains its own independent [ExerciseUiState].
 */
data class ExerciseUiState(
    val currentFen: String = "",
    val selectedSquare: String? = null,
    val selectedFile: Int = -1,
    val selectedRank: Int = -1,
    val legalTargetSquares: List<String> = emptyList(),
    val showLegalMovesHint: Boolean = false,
    val illegalFlashSquares: List<String> = emptyList(),
    val isSolved: Boolean = false,
    val phase: ExercisePhase = if (isSolved) ExercisePhase.SOLVED else ExercisePhase.IDLE,
    val canRetry: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsSuccess: Boolean = false,
    val solutionPlayed: String? = null,
    val activeTransition: BoardTransition? = null,
    val activeMoveTrail: MoveTrailState? = null,
    val lastMoveDescriptionFa: String? = null
) {
    /**
     * By default (showLegalMovesHint == false), legal destination squares are hidden
     * so the puzzle solution is never given away upon selecting a piece.
     */
    val visibleTargetSquares: List<String>
        get() = if (showLegalMovesHint) legalTargetSquares else emptyList()

    val isBoardLocked: Boolean
        get() = isSolved ||
            phase == ExercisePhase.SOLVED ||
            phase == ExercisePhase.ANIMATING_CORRECT ||
            phase == ExercisePhase.ANIMATING_WRONG ||
            phase == ExercisePhase.WRONG_LEGAL_MOVE
}

/**
 * Complete data-driven definition of an interactive exercise loaded from `exercises.json`.
 */
data class ExerciseDefinition(
    val exerciseId: String,
    val diagramId: String,
    val handlerType: String,
    val acceptedSolutionPgnRefs: List<String>,
    val showLegalMovesHint: Boolean = true,
    val allowLegalMoveHints: Boolean = showLegalMovesHint,
    val initialFen: String? = null,
    val sideToMove: String? = null,
    val titleFa: String? = null,
    val promptFa: String? = null,
    val successFeedbackFa: String? = null,
    val wrongMoveFeedbackFa: String? = null,
    val illegalMoveFeedbackFa: String? = null,
    val hintFa: String? = null
)

/**
 * Represents an independent, ordered content block within a book section or front-matter page.
 * Allows paragraphs, headings, diagrams, exercises, footnotes, external references,
 * source images, ordered lists, and tables to be interleaved in the exact sequence of the original book pages.
 */
sealed class SectionContentBlock {
    abstract val blockId: String
    open val sourcePdfPage: Int? = null

    data class ParagraphBlock(
        override val blockId: String,
        val textFa: String? = null,
        val showPlaceholderWhenEmpty: Boolean = true,
        val isPaginatedSlice: Boolean = false,
        override val sourcePdfPage: Int? = null,
        val sourceVisibleText: String? = null,
        val sourceUrl: String? = null,
        val sourcePageReferences: List<Int> = emptyList()
    ) : SectionContentBlock()

    data class HeadingBlock(
        override val blockId: String,
        val textFa: String? = null,
        val level: Int = 2,
        val showPlaceholderWhenEmpty: Boolean = false,
        override val sourcePdfPage: Int? = null,
        val sourceVisibleText: String? = null,
        val tocTargetSourcePdfPage: Int? = null,
        val tocEntryKind: String? = null,
        val chapterId: String? = null
    ) : SectionContentBlock()

    data class DiagramBlock(
        override val blockId: String,
        val diagramId: String,
        override val sourcePdfPage: Int? = null
    ) : SectionContentBlock()

    data class ExerciseBlock(
        override val blockId: String,
        val diagramId: String,
        val exerciseId: String = defaultExerciseIdForDiagram(diagramId),
        override val sourcePdfPage: Int? = null
    ) : SectionContentBlock()

    data class FootnoteBlock(
        override val blockId: String,
        val textFa: String? = null,
        val showPlaceholderWhenEmpty: Boolean = false,
        override val sourcePdfPage: Int? = null
    ) : SectionContentBlock()

    data class ExternalReferenceBlock(
        override val blockId: String,
        val titleFa: String? = null,
        val url: String? = null,
        val isAuthorizedAndEnabled: Boolean = false,
        override val sourcePdfPage: Int? = null
    ) : SectionContentBlock()

    data class SourceImageBlock(
        override val blockId: String,
        val assetId: String,
        val descriptionFa: String = "",
        val preserveOriginal: Boolean = true,
        val resolvedAssetPath: String = "",
        val imageWidth: Int = 0,
        val imageHeight: Int = 0,
        override val sourcePdfPage: Int? = null,
        val isSynthesizedFromPageAsset: Boolean = false
    ) : SectionContentBlock()

    data class OrderedListBlock(
        override val blockId: String,
        val titleFa: String? = null,
        val items: List<String>,
        override val sourcePdfPage: Int? = null
    ) : SectionContentBlock()

    data class TableBlock(
        override val blockId: String,
        val titleFa: String? = null,
        val rows: List<List<String>>,
        override val sourcePdfPage: Int? = null
    ) : SectionContentBlock()

    companion object {
        /**
         * Generates a chapter-neutral fallback exerciseId when an ExerciseBlock does not
         * explicitly specify one. Explicit `exerciseId` in section JSON always takes precedence.
         */
        fun defaultExerciseIdForDiagram(diagramId: String): String {
            val normalized = diagramId.trim().replace("-", "_")
            return if (normalized.isEmpty()) "EX_UNKNOWN" else "EX_$normalized"
        }
    }
}

/**
 * Structured placeholder and sequential content container for Persian translation provided by the Project Manager.
 * Preserves sectionId keys while ordering paragraphs, diagrams, exercises,
 * headings, and footnotes via [orderedContentBlocks].
 */
data class TranslationSlot(
    val sectionId: String,
    val orderedContentBlocks: List<SectionContentBlock> = emptyList(),
    val placeholderNotice: String = DEFAULT_PLACEHOLDER_NOTICE,
    val translationStatus: TranslationStatus = TranslationStatus.PENDING,
    val translationsByBlockId: Map<String, String> = emptyMap(),
    val isPending: Boolean = translationStatus != TranslationStatus.COMPLETE
) {
    /**
     * Resolves the Persian text for a given [blockId], prioritizing [translationsByBlockId]
     * and falling back to [inlineTextFa].
     */
    fun resolveBlockText(blockId: String, inlineTextFa: String?): String? {
        val mapped = translationsByBlockId[blockId]
        return when {
            !mapped.isNullOrBlank() -> mapped
            !inlineTextFa.isNullOrBlank() -> inlineTextFa
            else -> null
        }
    }

    /**
     * Unified text/title resolution for any textual [SectionContentBlock].
     */
    fun resolveBlockText(block: SectionContentBlock): String? {
        return when (block) {
            is SectionContentBlock.ParagraphBlock ->
                if (block.isPaginatedSlice) block.textFa else resolveBlockText(block.blockId, block.textFa)
            is SectionContentBlock.HeadingBlock -> resolveBlockText(block.blockId, block.textFa)
            is SectionContentBlock.FootnoteBlock -> resolveBlockText(block.blockId, block.textFa)
            is SectionContentBlock.ExternalReferenceBlock -> resolveBlockText(block.blockId, block.titleFa)
            is SectionContentBlock.OrderedListBlock -> {
                val itemsText = block.items.mapIndexed { idx, item -> "${idx + 1}. $item" }.joinToString("\n")
                if (!block.titleFa.isNullOrBlank()) "${block.titleFa}\n$itemsText" else itemsText
            }
            is SectionContentBlock.TableBlock -> {
                val rowsText = block.rows.joinToString("\n") { row -> row.joinToString(" : ") }
                if (!block.titleFa.isNullOrBlank()) "${block.titleFa}\n$rowsText" else rowsText
            }
            is SectionContentBlock.SourceImageBlock,
            is SectionContentBlock.DiagramBlock,
            is SectionContentBlock.ExerciseBlock -> null
        }
    }

    /**
     * Returns the [SectionContentBlock.blockId] of the first untranslated textual block
     * in [orderedContentBlocks] that has `showPlaceholderWhenEmpty == true`, or `null`
     * if [translationStatus] is [TranslationStatus.COMPLETE] or all such blocks already
     * have translated text.
     */
    fun firstPendingPlaceholderBlockId(): String? {
        if (translationStatus == TranslationStatus.COMPLETE) return null
        for (block in orderedContentBlocks) {
            val eligibleAndEmpty = when (block) {
                is SectionContentBlock.ParagraphBlock ->
                    block.showPlaceholderWhenEmpty && resolveBlockText(block).isNullOrBlank()
                is SectionContentBlock.HeadingBlock ->
                    block.showPlaceholderWhenEmpty && resolveBlockText(block).isNullOrBlank()
                is SectionContentBlock.FootnoteBlock ->
                    block.showPlaceholderWhenEmpty && resolveBlockText(block).isNullOrBlank()
                is SectionContentBlock.DiagramBlock,
                is SectionContentBlock.ExerciseBlock,
                is SectionContentBlock.ExternalReferenceBlock,
                is SectionContentBlock.SourceImageBlock,
                is SectionContentBlock.OrderedListBlock,
                is SectionContentBlock.TableBlock -> false
            }
            if (eligibleAndEmpty) {
                return block.blockId
            }
        }
        return null
    }

    /**
     * Returns `true` if and only if [blockId] is the single designated block in this section
     * that should display the pending translation placeholder notice.
     */
    fun shouldShowPlaceholderForBlock(blockId: String): Boolean {
        return firstPendingPlaceholderBlockId() == blockId
    }

    /**
     * Returns a copy of this [TranslationSlot] with injected translations by [blockId]
     * without altering the sequence of [orderedContentBlocks].
     */
    fun withBlockTranslations(
        translations: Map<String, String>,
        status: TranslationStatus = TranslationStatus.PARTIAL
    ): TranslationSlot {
        return copy(
            translationsByBlockId = translationsByBlockId + translations,
            translationStatus = status,
            isPending = status != TranslationStatus.COMPLETE
        )
    }

    companion object {
        const val DEFAULT_PLACEHOLDER_NOTICE = "محتوای این بخش در حال آماده‌سازی است."

        /**
         * Retained strictly for legacy v1..v17 preference migration and historical CH01 tests.
         * General chapter validation uses each chapter's own `chapter.json` section list.
         */
        val REQUIRED_SECTION_SLOT_IDS = listOf(
            "SEC_CH01_01_OPENING",
            "SEC_CH01_02_CHECKMATE",
            "SEC_CH01_03_RESIGNATION",
            "SEC_CH01_04_TIMING_OUT",
            "SEC_CH01_05_ABANDONMENT",
            "SEC_CH01_06_SUMMARY"
        )
    }
}

data class ContentValidationResult(
    val isValid: Boolean,
    val duplicateBlockIds: Set<String>,
    val invalidDiagramBlockIds: List<String>,
    val missingSectionSlotIds: List<String>,
    val duplicateSectionIds: Set<String> = emptySet(),
    val duplicateDiagramIds: Set<String> = emptySet(),
    val duplicateExerciseIds: Set<String> = emptySet(),
    val invalidExerciseIds: List<String> = emptyList(),
    val invalidPgnRefs: List<String> = emptyList(),
    val invalidFenDiagramIds: List<String> = emptyList(),
    val invalidRelativePaths: List<String> = emptyList(),
    val errorMessages: List<String> = emptyList()
)

object ContentBlockValidator {
    val SUPPORTED_BLOCK_TYPES: Set<String> = setOf(
        "paragraph",
        "heading",
        "diagram",
        "exercise",
        "footnote",
        "externalReference",
        "sourceImage",
        "orderedList",
        "table"
    )

    fun isSafeRelativeAssetPath(path: String): Boolean {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) return false
        if (trimmed.contains("..")) return false
        if (trimmed.contains(":")) return false
        return true
    }

    fun findDuplicateBlockIdsInBlocks(blocks: List<SectionContentBlock>): Set<String> {
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        for (block in blocks) {
            if (block.blockId.isBlank() || !seen.add(block.blockId)) {
                duplicates.add(block.blockId)
            }
        }
        return duplicates
    }

    fun findDuplicateBlockIds(sections: List<SectionItem>): Set<String> {
        val allBlocks = sections.flatMap { it.translationSlot.orderedContentBlocks }
        return findDuplicateBlockIdsInBlocks(allBlocks)
    }

    fun findInvalidDiagramBlocksInList(
        blocks: List<SectionContentBlock>,
        validDiagramIds: Set<String>
    ): List<String> {
        val invalidBlockIds = mutableListOf<String>()
        for (block in blocks) {
            when (block) {
                is SectionContentBlock.DiagramBlock -> {
                    if (block.diagramId.isBlank() || block.diagramId !in validDiagramIds) {
                        invalidBlockIds.add(block.blockId)
                    }
                }
                is SectionContentBlock.ExerciseBlock -> {
                    if (block.diagramId.isBlank() || block.diagramId !in validDiagramIds) {
                        invalidBlockIds.add(block.blockId)
                    }
                }
                else -> Unit
            }
        }
        return invalidBlockIds
    }

    fun findInvalidDiagramBlocks(
        sections: List<SectionItem>,
        validDiagramIds: Set<String>
    ): List<String> {
        val allBlocks = sections.flatMap { it.translationSlot.orderedContentBlocks }
        return findInvalidDiagramBlocksInList(allBlocks, validDiagramIds)
    }

    fun validateSectionsAndBlocks(
        sections: List<SectionItem>,
        diagrams: List<DiagramItem>,
        expectedSectionSlotIds: List<String> = sections.map { it.sectionId },
        exercises: List<ExerciseDefinition> = emptyList()
    ): ContentValidationResult {
        val errors = mutableListOf<String>()
        val seenSections = mutableSetOf<String>()
        val duplicateSectionIds = mutableSetOf<String>()
        for (sec in sections) {
            if (sec.sectionId.isBlank() || !seenSections.add(sec.sectionId)) {
                duplicateSectionIds.add(sec.sectionId)
                errors.add("Duplicate or blank sectionId: '${sec.sectionId}'")
            }
        }

        val seenDiagrams = mutableSetOf<String>()
        val duplicateDiagramIds = mutableSetOf<String>()
        val invalidFenDiagramIds = mutableListOf<String>()
        val diagramsById = mutableMapOf<String, DiagramItem>()
        for (diag in diagrams) {
            if (diag.diagramId.isBlank() || !seenDiagrams.add(diag.diagramId)) {
                duplicateDiagramIds.add(diag.diagramId)
                errors.add("Duplicate or blank diagramId: '${diag.diagramId}'")
            } else {
                diagramsById[diag.diagramId] = diag
            }
            val isFenValid = diag.initialFen.isNotBlank() &&
                runCatching { com.example.chess.FenParser.parse(diag.initialFen) }.isSuccess
            if (!isFenValid) {
                invalidFenDiagramIds.add(diag.diagramId)
                errors.add("Invalid FEN in diagram '${diag.diagramId}': '${diag.initialFen}'")
            }
        }

        val validDiagramIds = diagrams.map { it.diagramId }.filter { it.isNotBlank() }.toSet()
        val duplicateBlockIds = findDuplicateBlockIds(sections)
        if (duplicateBlockIds.isNotEmpty()) {
            errors.add("Duplicate or blank blockIds: $duplicateBlockIds")
        }
        val invalidDiagramBlockIds = findInvalidDiagramBlocks(sections, validDiagramIds)
        if (invalidDiagramBlockIds.isNotEmpty()) {
            errors.add("Invalid diagram references in blocks: $invalidDiagramBlockIds")
        }

        val presentSlotIds = sections.map { it.translationSlot.sectionId }.toSet()
        val missingSlotIds = expectedSectionSlotIds.filter { it !in presentSlotIds }
        val slotMismatch = sections.any { it.sectionId != it.translationSlot.sectionId }
        if (slotMismatch) {
            errors.add("Section sectionId does not match translationSlot.sectionId")
        }

        val seenExercises = mutableSetOf<String>()
        val duplicateExerciseIds = mutableSetOf<String>()
        val invalidExerciseIds = mutableListOf<String>()
        if (exercises.isNotEmpty()) {
            val exercisesById = mutableMapOf<String, ExerciseDefinition>()
            for (ex in exercises) {
                if (ex.exerciseId.isBlank() || !seenExercises.add(ex.exerciseId)) {
                    duplicateExerciseIds.add(ex.exerciseId)
                    errors.add("Duplicate or blank exerciseId: '${ex.exerciseId}'")
                } else {
                    exercisesById[ex.exerciseId] = ex
                }
                val targetDiagram = diagramsById[ex.diagramId]
                if (targetDiagram == null) {
                    invalidExerciseIds.add(ex.exerciseId)
                    errors.add("Exercise '${ex.exerciseId}' references missing diagram '${ex.diagramId}'")
                } else if (!ex.initialFen.isNullOrBlank() && ex.initialFen.trim() != targetDiagram.initialFen.trim()) {
                    invalidExerciseIds.add(ex.exerciseId)
                    errors.add(
                        "Exercise '${ex.exerciseId}' initialFen '${ex.initialFen}' does not match diagram '${ex.diagramId}' initialFen '${targetDiagram.initialFen}'"
                    )
                }
                if (ex.acceptedSolutionPgnRefs.isEmpty()) {
                    invalidExerciseIds.add(ex.exerciseId)
                    errors.add("Exercise '${ex.exerciseId}' has empty acceptedSolutionPgnRefs")
                }
            }
            for (sec in sections) {
                for (block in sec.translationSlot.orderedContentBlocks) {
                    if (block is SectionContentBlock.ExerciseBlock) {
                        val exDef = exercisesById[block.exerciseId]
                        if (exDef == null) {
                            invalidExerciseIds.add(block.blockId)
                            errors.add("ExerciseBlock '${block.blockId}' references unknown exerciseId '${block.exerciseId}'")
                        } else if (exDef.diagramId != block.diagramId) {
                            invalidExerciseIds.add(block.blockId)
                            errors.add("ExerciseBlock '${block.blockId}' diagramId '${block.diagramId}' does not match ExerciseDefinition diagramId '${exDef.diagramId}'")
                        }
                    }
                }
            }
        }

        val isValid = duplicateBlockIds.isEmpty() &&
            invalidDiagramBlockIds.isEmpty() &&
            missingSlotIds.isEmpty() &&
            !slotMismatch &&
            duplicateSectionIds.isEmpty() &&
            duplicateDiagramIds.isEmpty() &&
            duplicateExerciseIds.isEmpty() &&
            invalidExerciseIds.isEmpty() &&
            invalidFenDiagramIds.isEmpty() &&
            errors.isEmpty()

        return ContentValidationResult(
            isValid = isValid,
            duplicateBlockIds = duplicateBlockIds,
            invalidDiagramBlockIds = invalidDiagramBlockIds,
            missingSectionSlotIds = missingSlotIds,
            duplicateSectionIds = duplicateSectionIds,
            duplicateDiagramIds = duplicateDiagramIds,
            duplicateExerciseIds = duplicateExerciseIds,
            invalidExerciseIds = invalidExerciseIds,
            invalidFenDiagramIds = invalidFenDiagramIds,
            errorMessages = errors
        )
    }
}

data class SectionItem(
    val sectionId: String,
    val index: Int,
    val titleEn: String,
    val titleFa: String,
    val pdfPages: List<Int>,
    val diagramIds: List<String>,
    val translationSlot: TranslationSlot,
    val sectionKind: SectionKind = SectionKind.fromSectionMetadata(sectionId, titleEn, titleFa)
)

data class DiagramItem(
    val diagramId: String,
    val pdfPage: Int,
    val sectionName: String,
    val bookContext: String,
    val diagramKind: String,
    val recommendedInteraction: String,
    val initialFen: String,
    val currentFen: String,
    val sideToMove: String,
    val moveHistory: String,
    val pgnRef: String?,
    val solutionPgnRefs: List<String>,
    val highlightSquares: List<String>,
    val relatedDiagramIds: List<String>,
    val visualConfidence: String,
    val implementationNotes: String,
    val auditStatus: AuditStatus,
    val auditWarning: String? = null
)
