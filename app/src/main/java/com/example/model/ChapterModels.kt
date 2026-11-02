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
    COMPLETE
}

/**
 * Represents an independent, ordered content block within a book section.
 * Allows paragraphs, headings, diagrams, exercises, footnotes, and external references
 * to be interleaved in the exact sequence of the original book pages.
 */
sealed class SectionContentBlock {
    abstract val blockId: String

    data class ParagraphBlock(
        override val blockId: String,
        val textFa: String? = null,
        val showPlaceholderWhenEmpty: Boolean = true
    ) : SectionContentBlock()

    data class HeadingBlock(
        override val blockId: String,
        val textFa: String? = null,
        val level: Int = 2,
        val showPlaceholderWhenEmpty: Boolean = false
    ) : SectionContentBlock()

    data class DiagramBlock(
        override val blockId: String,
        val diagramId: String
    ) : SectionContentBlock()

    data class ExerciseBlock(
        override val blockId: String,
        val diagramId: String
    ) : SectionContentBlock()

    data class FootnoteBlock(
        override val blockId: String,
        val textFa: String? = null,
        val showPlaceholderWhenEmpty: Boolean = false
    ) : SectionContentBlock()

    data class ExternalReferenceBlock(
        override val blockId: String,
        val titleFa: String? = null,
        val url: String? = null,
        val isAuthorizedAndEnabled: Boolean = false
    ) : SectionContentBlock()
}

/**
 * Structured placeholder and sequential content container for Persian translation provided by the Project Manager.
 * Preserves the 6 fixed sectionId keys while ordering paragraphs, diagrams, exercises,
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
     * Unified text/title resolution for any textual [SectionContentBlock]
     * ([SectionContentBlock.ParagraphBlock], [SectionContentBlock.HeadingBlock],
     * [SectionContentBlock.FootnoteBlock], and [SectionContentBlock.ExternalReferenceBlock]),
     * prioritizing [translationsByBlockId] over inline properties.
     */
    fun resolveBlockText(block: SectionContentBlock): String? {
        return when (block) {
            is SectionContentBlock.ParagraphBlock -> resolveBlockText(block.blockId, block.textFa)
            is SectionContentBlock.HeadingBlock -> resolveBlockText(block.blockId, block.textFa)
            is SectionContentBlock.FootnoteBlock -> resolveBlockText(block.blockId, block.textFa)
            is SectionContentBlock.ExternalReferenceBlock -> resolveBlockText(block.blockId, block.titleFa)
            is SectionContentBlock.DiagramBlock,
            is SectionContentBlock.ExerciseBlock -> null
        }
    }

    /**
     * Returns the [SectionContentBlock.blockId] of the first untranslated textual block
     * in [orderedContentBlocks] that has `showPlaceholderWhenEmpty == true`, or `null`
     * if [translationStatus] is [TranslationStatus.COMPLETE] or all such blocks already
     * have translated text.
     *
     * This guarantees that the pending translation placeholder notice is shown at most once
     * per section at the position of the first untranslated text block, and automatically
     * shifts to the next untranslated text block as earlier blocks receive translations.
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
                is SectionContentBlock.ExternalReferenceBlock -> false
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
    val missingSectionSlotIds: List<String>
)

object ContentBlockValidator {

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
        diagrams: List<DiagramItem>
    ): ContentValidationResult {
        val validDiagramIds = diagrams.map { it.diagramId }.toSet()
        val duplicateBlockIds = findDuplicateBlockIds(sections)
        val invalidDiagramBlockIds = findInvalidDiagramBlocks(sections, validDiagramIds)
        val presentSlotIds = sections.map { it.translationSlot.sectionId }.toSet()
        val missingSlotIds = TranslationSlot.REQUIRED_SECTION_SLOT_IDS.filter { it !in presentSlotIds }
        val slotMismatch = sections.any { it.sectionId != it.translationSlot.sectionId }

        val isValid = duplicateBlockIds.isEmpty() &&
            invalidDiagramBlockIds.isEmpty() &&
            missingSlotIds.isEmpty() &&
            !slotMismatch

        return ContentValidationResult(
            isValid = isValid,
            duplicateBlockIds = duplicateBlockIds,
            invalidDiagramBlockIds = invalidDiagramBlockIds,
            missingSectionSlotIds = missingSlotIds
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
    val translationSlot: TranslationSlot
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
