package com.example.model

enum class FrontMatterPageKind {
    COVER,
    IMAGE_ONLY,
    TITLE_PAGE,
    COPYRIGHT_AND_CREDITS,
    TABLE_OF_CONTENTS,
    INTRODUCTION,
    EDUCATIONAL_CONTENT,
    PART_DIVIDER,
    PART_INTRODUCTION,
    INTERSTITIAL_IMAGE,
    OTHER;

    companion object {
        fun fromString(raw: String?): FrontMatterPageKind {
            return when (raw?.trim()?.uppercase()) {
                "COVER" -> COVER
                "IMAGE_ONLY" -> IMAGE_ONLY
                "TITLE_PAGE" -> TITLE_PAGE
                "COPYRIGHT_AND_CREDITS" -> COPYRIGHT_AND_CREDITS
                "TABLE_OF_CONTENTS" -> TABLE_OF_CONTENTS
                "INTRODUCTION" -> INTRODUCTION
                "EDUCATIONAL_CONTENT" -> EDUCATIONAL_CONTENT
                "PART_DIVIDER" -> PART_DIVIDER
                "PART_INTRODUCTION" -> PART_INTRODUCTION
                "INTERSTITIAL_IMAGE" -> INTERSTITIAL_IMAGE
                else -> OTHER
            }
        }
    }
}

data class FrontMatterCatalogEntry(
    val contentRegionId: String = REGION_ID_FRONT_MATTER,
    val titleFa: String,
    val navigationTitleFa: String = DEFAULT_NAVIGATION_TITLE_FA,
    val manifestPath: String,
    val sourcePdfPageStart: Int = 1,
    val sourcePdfPageEnd: Int = 39,
    val enabled: Boolean = true
) {
    companion object {
        const val REGION_ID_FRONT_MATTER = "FRONT_MATTER"
        const val DEFAULT_NAVIGATION_TITLE_FA = "پیش‌درآمد و مبانی شطرنج"
    }
}

data class FrontMatterPageRef(
    val sourcePdfPage: Int,
    val file: String,
    val pageKind: FrontMatterPageKind,
    val translationStatus: String
)

data class FrontMatterNextContentStart(
    val sourcePdfPage: Int,
    val titleFa: String
)

data class FrontMatterManifest(
    val schemaVersion: Int = 1,
    val bookId: String,
    val contentRegionId: String = FrontMatterCatalogEntry.REGION_ID_FRONT_MATTER,
    val titleFa: String,
    val navigationTitleFa: String = FrontMatterCatalogEntry.DEFAULT_NAVIGATION_TITLE_FA,
    val sourcePdfFile: String,
    val sourcePdfSha256: String,
    val sourcePdfTotalPages: Int,
    val sourcePdfPageStart: Int,
    val sourcePdfPageEnd: Int,
    val pageNumberAuthority: String,
    val pageBoundaryPolicy: String,
    val sourcePdfPages: List<FrontMatterPageRef>,
    val nextSourcePage: Int,
    val nextContentStartsAt: FrontMatterNextContentStart?,
    val sourcePage39NoteFa: String,
    val sourceAssetDirectory: String,
    val sourceLinksFile: String,
    val sourceAssetsFile: String,
    val tableOfContentsFile: String,
    val terminologyFile: String,
    val translationStatus: String
)

data class FrontMatterSourceAsset(
    val sourcePdfPage: Int,
    val pdfImageObjectIndex: Int,
    val pdfImageType: String,
    val pdfObjectId: String,
    val width: Int,
    val height: Int,
    val assetId: String,
    val file: String,
    val resolvedAssetPath: String,
    val format: String,
    val descriptionFa: String
)

data class FrontMatterSourceLink(
    val sourcePdfPage: Int,
    val linkIndexOnPage: Int,
    val sourceLabel: String,
    val linkKind: Int,
    val targetSourcePdfPage: Int? = null,
    val targetUri: String? = null,
    val chapterId: String? = null
) {
    val isInternalPageLink: Boolean
        get() = targetSourcePdfPage != null

    val isExternalUriLink: Boolean
        get() = !targetUri.isNullOrBlank()
}

data class FrontMatterTocEntry(
    val tocSourcePdfPage: Int,
    val sourceLabel: String,
    val labelFa: String,
    val targetSourcePdfPage: Int,
    val depth: Int,
    val entryKind: String,
    val chapterId: String? = null
)

data class FrontMatterTableOfContents(
    val schemaVersion: Int = 1,
    val bookId: String,
    val sourcePages: List<Int>,
    val entries: List<FrontMatterTocEntry>
)

data class FrontMatterPage(
    val schemaVersion: Int = 1,
    val bookId: String,
    val contentRegionId: String = FrontMatterCatalogEntry.REGION_ID_FRONT_MATTER,
    val sourcePdfPage: Int,
    val sourcePageNumbering: String,
    val pageKind: FrontMatterPageKind,
    val pageTitleFa: String,
    val translationStatus: String,
    val contentCompleteness: String,
    val orderedContentBlocks: List<SectionContentBlock>,
    val notesFa: String? = null,
    val sourceAssetIds: List<String> = emptyList(),
    val sourceLinks: List<FrontMatterSourceLink> = emptyList(),
    val sourceAssets: List<FrontMatterSourceAsset> = emptyList()
) {
    /**
     * Returns the visual blocks to render on this source PDF page, preserving
     * `orderedContentBlocks` in exact order while attaching page-level master images
     * (`IMAGE_ONLY`, `COVER`, `TITLE_PAGE`, `PART_DIVIDER`, `PART_INTRODUCTION`, etc.)
     * that appear in `sourceAssets` when not already referenced by an explicit `SourceImageBlock`.
     */
    fun buildRenderableBlocks(): List<SectionContentBlock> {
        val explicitAssetIds = orderedContentBlocks
            .filterIsInstance<SectionContentBlock.SourceImageBlock>()
            .map { it.assetId }
            .toSet()

        // Filter primary visual assets (exclude alpha/soft-mask objects `pdfImageType == "smask"`)
        val unreferencedVisualAssets = sourceAssets.filter { asset ->
            asset.assetId !in explicitAssetIds &&
                !asset.pdfImageType.equals("smask", ignoreCase = true)
        }

        return when (pageKind) {
            FrontMatterPageKind.IMAGE_ONLY,
            FrontMatterPageKind.INTERSTITIAL_IMAGE -> {
                if (orderedContentBlocks.isNotEmpty()) {
                    orderedContentBlocks
                } else {
                    unreferencedVisualAssets.map { asset ->
                        SectionContentBlock.SourceImageBlock(
                            blockId = "FM_P${sourcePdfPage.toString().padStart(3, '0')}_ASSET_${asset.assetId}",
                            assetId = asset.assetId,
                            descriptionFa = asset.descriptionFa,
                            preserveOriginal = true,
                            resolvedAssetPath = asset.resolvedAssetPath,
                            imageWidth = asset.width,
                            imageHeight = asset.height,
                            sourcePdfPage = sourcePdfPage,
                            isSynthesizedFromPageAsset = true
                        )
                    }
                }
            }

            FrontMatterPageKind.COVER,
            FrontMatterPageKind.TITLE_PAGE,
            FrontMatterPageKind.PART_DIVIDER -> {
                // Master visual asset first (unmodified), followed by the separate Persian UI text blocks
                val masterImageBlocks = unreferencedVisualAssets.map { asset ->
                    SectionContentBlock.SourceImageBlock(
                        blockId = "FM_P${sourcePdfPage.toString().padStart(3, '0')}_ASSET_${asset.assetId}",
                        assetId = asset.assetId,
                        descriptionFa = asset.descriptionFa,
                        preserveOriginal = true,
                        resolvedAssetPath = asset.resolvedAssetPath,
                        imageWidth = asset.width,
                        imageHeight = asset.height,
                        sourcePdfPage = sourcePdfPage,
                        isSynthesizedFromPageAsset = true
                    )
                }
                masterImageBlocks + orderedContentBlocks
            }

            FrontMatterPageKind.PART_INTRODUCTION,
            FrontMatterPageKind.COPYRIGHT_AND_CREDITS,
            FrontMatterPageKind.TABLE_OF_CONTENTS -> {
                // Keep header/emblem image asset on the same source page without displacing text blocks
                val primaryHeaderAsset = unreferencedVisualAssets.firstOrNull()
                val headerBlock = if (primaryHeaderAsset != null) {
                    listOf(
                        SectionContentBlock.SourceImageBlock(
                            blockId = "FM_P${sourcePdfPage.toString().padStart(3, '0')}_ASSET_${primaryHeaderAsset.assetId}",
                            assetId = primaryHeaderAsset.assetId,
                            descriptionFa = primaryHeaderAsset.descriptionFa,
                            preserveOriginal = true,
                            resolvedAssetPath = primaryHeaderAsset.resolvedAssetPath,
                            imageWidth = primaryHeaderAsset.width,
                            imageHeight = primaryHeaderAsset.height,
                            sourcePdfPage = sourcePdfPage,
                            isSynthesizedFromPageAsset = true
                        )
                    )
                } else {
                    emptyList()
                }
                headerBlock + orderedContentBlocks
            }

            else -> orderedContentBlocks
        }
    }
}

data class FrontMatterTerminologyEntry(
    val englishTerm: String,
    val persianTerm: String,
    val translationNote: String
)

data class FrontMatterContentPack(
    val manifest: FrontMatterManifest,
    val pages: List<FrontMatterPage>,
    val tableOfContents: FrontMatterTableOfContents,
    val sourceLinks: List<FrontMatterSourceLink>,
    val sourceAssetsById: Map<String, FrontMatterSourceAsset>,
    val terminologyGlossary: List<FrontMatterTerminologyEntry>
) {
    val pagesBySourcePdfPage: Map<Int, FrontMatterPage> = pages.associateBy { it.sourcePdfPage }

    fun pageForSourcePdfPage(pdfPage: Int): FrontMatterPage? = pagesBySourcePdfPage[pdfPage]
}

data class FrontMatterValidationResult(
    val isValid: Boolean,
    val pageCount: Int,
    val resolvedImageAssetCount: Int,
    val resolvedLinkCount: Int,
    val tocEntryCount: Int,
    val errorMessages: List<String> = emptyList()
)
