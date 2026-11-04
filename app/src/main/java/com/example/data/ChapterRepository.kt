package com.example.data

import android.content.Context
import com.example.chess.ChessEngine
import com.example.chess.ExerciseHandlerRegistry
import com.example.chess.PgnSequence
import com.example.model.AuditStatus
import com.example.model.BookCatalogEntry
import com.example.model.ChapterCatalogEntry
import com.example.model.ChapterManifest
import com.example.model.ContentBlockValidator
import com.example.model.ContentValidationResult
import com.example.model.DiagramItem
import com.example.model.ExerciseDefinition
import com.example.model.FrontMatterCatalogEntry
import com.example.model.FrontMatterContentPack
import com.example.model.FrontMatterManifest
import com.example.model.FrontMatterNextContentStart
import com.example.model.FrontMatterPage
import com.example.model.FrontMatterPageKind
import com.example.model.FrontMatterPageRef
import com.example.model.FrontMatterSourceAsset
import com.example.model.FrontMatterSourceLink
import com.example.model.FrontMatterTableOfContents
import com.example.model.FrontMatterTerminologyEntry
import com.example.model.FrontMatterTocEntry
import com.example.model.FrontMatterValidationResult
import com.example.model.LibraryCatalog
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import com.example.model.SectionKind
import com.example.model.SectionManifestEntry
import com.example.model.TranslationSlot
import com.example.model.TranslationStatus
import org.json.JSONArray
import org.json.JSONObject

class ChapterRepository @JvmOverloads constructor(
    private val context: Context,
    private val libraryAssetPath: String = DEFAULT_LIBRARY_ASSET_PATH,
    val assetReaderOverride: ((String) -> String)? = null
) {

    private val cachedDiagramsByChapter = mutableMapOf<String, List<DiagramItem>>()
    private val cachedSectionsByChapter = mutableMapOf<String, List<SectionItem>>()
    private val cachedPgnSequencesByChapter = mutableMapOf<String, Map<String, PgnSequence>>()
    private val cachedExercisesByChapter = mutableMapOf<String, List<ExerciseDefinition>>()
    private val cachedFrontMatterByBook = mutableMapOf<String, FrontMatterContentPack>()

    private fun readAssetText(assetPath: String): String {
        require(ContentBlockValidator.isSafeRelativeAssetPath(assetPath)) {
            "Unsafe or invalid relative asset path: $assetPath"
        }
        if (assetReaderOverride != null) {
            return assetReaderOverride.invoke(assetPath)
        }
        return context.assets.open(assetPath).bufferedReader().use { it.readText() }
    }

    fun assetExists(assetPath: String): Boolean {
        if (!ContentBlockValidator.isSafeRelativeAssetPath(assetPath)) return false
        if (assetReaderOverride != null) {
            return try {
                assetReaderOverride.invoke(assetPath)
                true
            } catch (_: Exception) {
                false
            }
        }
        return try {
            context.assets.open(assetPath).use { true }
        } catch (_: Exception) {
            false
        }
    }

    fun getLibraryCatalog(): LibraryCatalog {
        val root = JSONObject(readAssetText(libraryAssetPath))
        val schemaVersion = root.optInt("schemaVersion", 1)
        val defaultBookId = root.getString("defaultBookId")
        val booksArr = root.getJSONArray("books")
        val books = mutableListOf<BookCatalogEntry>()

        for (i in 0 until booksArr.length()) {
            val bObj = booksArr.getJSONObject(i)
            val frontMatterObj = bObj.optJSONObject("frontMatter")
            val frontMatterEntry = if (frontMatterObj != null) {
                FrontMatterCatalogEntry(
                    contentRegionId = frontMatterObj.optString("contentRegionId", FRONT_MATTER_REGION_ID).trim(),
                    titleFa = frontMatterObj.optString("titleFa", "پیش‌درآمد، مقدمه و آموزش مبانی شطرنج"),
                    navigationTitleFa = frontMatterObj.optString("navigationTitleFa", "پیش‌درآمد و مبانی شطرنج"),
                    manifestPath = frontMatterObj.getString("manifestPath").trim(),
                    sourcePdfPageStart = frontMatterObj.optInt("sourcePdfPageStart", 1),
                    sourcePdfPageEnd = frontMatterObj.optInt("sourcePdfPageEnd", 39),
                    enabled = frontMatterObj.optBoolean("enabled", true)
                )
            } else {
                null
            }

            val chaptersArr = bObj.getJSONArray("chapters")
            val chapters = mutableListOf<ChapterCatalogEntry>()
            for (j in 0 until chaptersArr.length()) {
                val cObj = chaptersArr.getJSONObject(j)
                chapters.add(
                    ChapterCatalogEntry(
                        chapterId = cObj.getString("chapterId").trim(),
                        chapterNumber = cObj.getInt("chapterNumber"),
                        titleEn = cObj.getString("titleEn"),
                        titleFa = cObj.getString("titleFa"),
                        manifestPath = cObj.getString("manifestPath").trim(),
                        enabled = cObj.optBoolean("enabled", true)
                    )
                )
            }
            books.add(
                BookCatalogEntry(
                    bookId = bObj.getString("bookId").trim(),
                    titleEn = bObj.getString("titleEn"),
                    titleFa = bObj.getString("titleFa"),
                    authorEn = bObj.optString("authorEn", ""),
                    authorFa = bObj.optString("authorFa", ""),
                    frontMatter = frontMatterEntry,
                    chapters = chapters
                )
            )
        }

        return LibraryCatalog(
            schemaVersion = schemaVersion,
            defaultBookId = defaultBookId,
            books = books
        )
    }

    fun resolveDefaultBookId(): String {
        val catalog = getLibraryCatalog()
        return catalog.defaultBookId.ifBlank { catalog.books.firstOrNull()?.bookId ?: DEFAULT_BOOK_ID }
    }

    fun resolveInitialChapterEntry(
        preferredBookId: String? = null,
        preferredChapterId: String? = null
    ): Pair<BookCatalogEntry, ChapterCatalogEntry> {
        val catalog = getLibraryCatalog()
        val book = if (!preferredBookId.isNullOrBlank()) {
            catalog.findBook(preferredBookId) ?: catalog.books.first()
        } else {
            catalog.findBook(catalog.defaultBookId) ?: catalog.books.first()
        }
        val enabled = book.enabledChapters
        val chapter = if (!preferredChapterId.isNullOrBlank()) {
            enabled.find { it.chapterId == preferredChapterId }
                ?: enabled.firstOrNull()
                ?: book.chapters.first()
        } else {
            enabled.firstOrNull() ?: book.chapters.first()
        }
        return book to chapter
    }

    fun resolveDefaultChapterId(bookId: String = resolveDefaultBookId()): String {
        return resolveInitialChapterEntry(preferredBookId = bookId).second.chapterId
    }

    fun getBooks(): List<BookCatalogEntry> = getLibraryCatalog().books

    @JvmOverloads
    fun getChapters(bookId: String = resolveDefaultBookId()): List<ChapterCatalogEntry> {
        val catalog = getLibraryCatalog()
        val book = catalog.findBook(bookId) ?: return emptyList()
        return book.enabledChapters
    }

    private fun resolveBookRelativePath(bookBaseDir: String, rawPath: String): String {
        val trimmed = rawPath.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        if (bookBaseDir.isEmpty() || trimmed.startsWith("books/") || trimmed.startsWith("$bookBaseDir/")) {
            return trimmed
        }
        return "$bookBaseDir/$trimmed"
    }

    @JvmOverloads
    fun getFrontMatterCatalogEntry(bookId: String = resolveDefaultBookId()): FrontMatterCatalogEntry? {
        val catalog = getLibraryCatalog()
        val book = catalog.findBook(bookId) ?: return null
        return book.frontMatter?.takeIf { it.enabled }
    }

    @JvmOverloads
    fun getFrontMatterManifest(bookId: String = resolveDefaultBookId()): FrontMatterManifest? {
        val entry = getFrontMatterCatalogEntry(bookId) ?: return null
        val bookBaseDir = "books/$bookId"
        val root = JSONObject(readAssetText(entry.manifestPath))
        val pagesArr = root.getJSONArray("sourcePdfPages")
        val pageRefs = mutableListOf<FrontMatterPageRef>()
        for (i in 0 until pagesArr.length()) {
            val pObj = pagesArr.getJSONObject(i)
            pageRefs.add(
                FrontMatterPageRef(
                    sourcePdfPage = pObj.getInt("sourcePdfPage"),
                    file = resolveBookRelativePath(bookBaseDir, pObj.getString("file")),
                    pageKind = FrontMatterPageKind.fromString(pObj.optString("pageKind", "EDUCATIONAL_CONTENT")),
                    translationStatus = pObj.optString("translationStatus", "")
                )
            )
        }
        val nextContentObj = root.optJSONObject("nextContentStartsAt")
        val nextContentStartsAt = if (nextContentObj != null) {
            FrontMatterNextContentStart(
                sourcePdfPage = nextContentObj.getInt("sourcePdfPage"),
                titleFa = nextContentObj.getString("titleFa")
            )
        } else {
            null
        }

        val rawAssetDir = root.optString("sourceAssetDirectory", "assets/pdf_embedded_objects/")
        val resolvedAssetDir = resolveBookRelativePath(bookBaseDir, rawAssetDir)

        return FrontMatterManifest(
            schemaVersion = root.optInt("schemaVersion", 1),
            bookId = root.optString("bookId", bookId).trim(),
            contentRegionId = root.optString("contentRegionId", entry.contentRegionId).trim(),
            titleFa = root.optString("titleFa", entry.titleFa),
            navigationTitleFa = entry.navigationTitleFa,
            sourcePdfFile = root.optString("sourcePdfFile", ""),
            sourcePdfSha256 = root.optString("sourcePdfSha256", ""),
            sourcePdfTotalPages = root.optInt("sourcePdfTotalPages", 591),
            sourcePdfPageStart = root.optInt("sourcePdfPageStart", entry.sourcePdfPageStart),
            sourcePdfPageEnd = root.optInt("sourcePdfPageEnd", entry.sourcePdfPageEnd),
            pageNumberAuthority = root.optString("pageNumberAuthority", ""),
            pageBoundaryPolicy = root.optString("pageBoundaryPolicy", ""),
            sourcePdfPages = pageRefs.sortedBy { it.sourcePdfPage },
            nextSourcePage = root.optInt("nextSourcePage", 40),
            nextContentStartsAt = nextContentStartsAt,
            sourcePage39NoteFa = root.optString("sourcePage39NoteFa", ""),
            sourceAssetDirectory = resolvedAssetDir,
            sourceLinksFile = resolveBookRelativePath(
                bookBaseDir,
                root.optString("sourceLinksFile", "source_links/source_links_pages_001_039.json")
            ),
            sourceAssetsFile = resolveBookRelativePath(
                bookBaseDir,
                root.optString("sourceAssetsFile", "source_metadata/source_image_placements_pages_001_039.csv")
            ),
            tableOfContentsFile = resolveBookRelativePath(
                bookBaseDir,
                root.optString("tableOfContentsFile", "front_matter/table_of_contents.json")
            ),
            terminologyFile = resolveBookRelativePath(
                bookBaseDir,
                root.optString("terminologyFile", "terminology/terminology_glossary_fa.csv")
            ),
            translationStatus = root.optString("translationStatus", "")
        )
    }

    @JvmOverloads
    fun getFrontMatterSourceAssets(bookId: String = resolveDefaultBookId()): List<FrontMatterSourceAsset> {
        val bookBaseDir = "books/$bookId"
        val imageManifestPath = "$bookBaseDir/source_metadata/source_image_manifest.json"
        val root = JSONObject(readAssetText(imageManifestPath))
        val assetsArr = root.getJSONArray("assets")
        val list = mutableListOf<FrontMatterSourceAsset>()
        for (i in 0 until assetsArr.length()) {
            list.add(parseFrontMatterSourceAssetJson(assetsArr.getJSONObject(i), bookBaseDir))
        }
        return list
    }

    @JvmOverloads
    fun getFrontMatterImagePlacements(bookId: String = resolveDefaultBookId()): List<FrontMatterSourceAsset> {
        val manifest = getFrontMatterManifest(bookId) ?: return emptyList()
        val bookBaseDir = "books/$bookId"
        val csvText = readAssetText(manifest.sourceAssetsFile)
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return emptyList()
        val result = mutableListOf<FrontMatterSourceAsset>()
        for (idx in 1 until lines.size) {
            val cols = parseCsvLine(lines[idx])
            if (cols.size >= 10) {
                val rawFile = cols[7].trim()
                result.add(
                    FrontMatterSourceAsset(
                        sourcePdfPage = cols[0].trim().toInt(),
                        pdfImageObjectIndex = cols[1].trim().toInt(),
                        pdfImageType = cols[2].trim(),
                        pdfObjectId = cols[3].trim(),
                        width = cols[4].trim().toInt(),
                        height = cols[5].trim().toInt(),
                        assetId = cols[6].trim(),
                        file = rawFile,
                        resolvedAssetPath = resolveBookRelativePath(bookBaseDir, rawFile),
                        format = cols[8].trim(),
                        descriptionFa = cols[9].trim()
                    )
                )
            }
        }
        return result
    }

    @JvmOverloads
    fun getFrontMatterSourceLinks(bookId: String = resolveDefaultBookId()): List<FrontMatterSourceLink> {
        val manifest = getFrontMatterManifest(bookId) ?: return emptyList()
        val root = JSONObject(readAssetText(manifest.sourceLinksFile))
        val linksArr = root.getJSONArray("links")
        val result = mutableListOf<FrontMatterSourceLink>()
        for (i in 0 until linksArr.length()) {
            result.add(parseFrontMatterSourceLinkJson(linksArr.getJSONObject(i)))
        }
        return result
    }

    @JvmOverloads
    fun getFrontMatterTableOfContents(bookId: String = resolveDefaultBookId()): FrontMatterTableOfContents {
        val manifest = getFrontMatterManifest(bookId)
            ?: throw IllegalArgumentException("Front matter manifest not found for book: $bookId")
        val root = JSONObject(readAssetText(manifest.tableOfContentsFile))
        val sourcePagesArr = root.getJSONArray("sourcePages")
        val sourcePages = (0 until sourcePagesArr.length()).map { sourcePagesArr.getInt(it) }
        val entriesArr = root.getJSONArray("entries")
        val entries = mutableListOf<FrontMatterTocEntry>()
        for (i in 0 until entriesArr.length()) {
            val eObj = entriesArr.getJSONObject(i)
            val chapterId = if (eObj.isNull("chapterId")) null else eObj.optString("chapterId").takeIf { it.isNotBlank() }
            entries.add(
                FrontMatterTocEntry(
                    tocSourcePdfPage = eObj.getInt("tocSourcePdfPage"),
                    sourceLabel = eObj.getString("sourceLabel"),
                    labelFa = eObj.getString("labelFa"),
                    targetSourcePdfPage = eObj.getInt("targetSourcePdfPage"),
                    depth = eObj.optInt("depth", 0),
                    entryKind = eObj.optString("entryKind", "topic"),
                    chapterId = chapterId
                )
            )
        }
        return FrontMatterTableOfContents(
            schemaVersion = root.optInt("schemaVersion", 1),
            bookId = root.optString("bookId", bookId),
            sourcePages = sourcePages,
            entries = entries
        )
    }

    @JvmOverloads
    fun getFrontMatterTerminologyGlossary(bookId: String = resolveDefaultBookId()): List<FrontMatterTerminologyEntry> {
        val manifest = getFrontMatterManifest(bookId) ?: return emptyList()
        val csvText = readAssetText(manifest.terminologyFile)
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return emptyList()
        val entries = mutableListOf<FrontMatterTerminologyEntry>()
        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.size >= 3) {
                entries.add(
                    FrontMatterTerminologyEntry(
                        englishTerm = cols[0].trim(),
                        persianTerm = cols[1].trim(),
                        translationNote = cols[2].trim()
                    )
                )
            }
        }
        return entries
    }

    @JvmOverloads
    fun getFrontMatterPages(bookId: String = resolveDefaultBookId()): List<FrontMatterPage> {
        return getFrontMatterContentPack(bookId)?.pages ?: emptyList()
    }

    @JvmOverloads
    fun getFrontMatterContentPack(bookId: String = resolveDefaultBookId()): FrontMatterContentPack? {
        cachedFrontMatterByBook[bookId]?.let { return it }
        val manifest = getFrontMatterManifest(bookId) ?: return null
        val bookBaseDir = "books/$bookId"
        val sourceAssetsList = getFrontMatterSourceAssets(bookId)
        val sourceAssetsById = sourceAssetsList.associateBy { it.assetId }
        val pages = manifest.sourcePdfPages.map { pageRef ->
            parseFrontMatterPageJson(
                pageJsonText = readAssetText(pageRef.file),
                expectedSourcePdfPage = pageRef.sourcePdfPage,
                bookBaseDir = bookBaseDir,
                globalAssetsById = sourceAssetsById
            )
        }
        val toc = getFrontMatterTableOfContents(bookId)
        val allLinks = getFrontMatterSourceLinks(bookId)
        val glossary = getFrontMatterTerminologyGlossary(bookId)

        val pack = FrontMatterContentPack(
            manifest = manifest,
            pages = pages,
            tableOfContents = toc,
            sourceLinks = allLinks,
            sourceAssetsById = sourceAssetsById,
            terminologyGlossary = glossary
        )
        cachedFrontMatterByBook[bookId] = pack
        return pack
    }

    private fun parseFrontMatterSourceAssetJson(
        aObj: JSONObject,
        bookBaseDir: String
    ): FrontMatterSourceAsset {
        val rawFile = aObj.getString("file").trim()
        return FrontMatterSourceAsset(
            sourcePdfPage = aObj.getInt("sourcePdfPage"),
            pdfImageObjectIndex = aObj.getInt("pdfImageObjectIndex"),
            pdfImageType = aObj.optString("pdfImageType", "image"),
            pdfObjectId = aObj.optString("pdfObjectId", "no"),
            width = aObj.getInt("width"),
            height = aObj.getInt("height"),
            assetId = aObj.getString("assetId").trim(),
            file = rawFile,
            resolvedAssetPath = resolveBookRelativePath(bookBaseDir, rawFile),
            format = aObj.optString("format", "jpg"),
            descriptionFa = aObj.optString("descriptionFa", "")
        )
    }

    private fun parseFrontMatterSourceLinkJson(lObj: JSONObject): FrontMatterSourceLink {
        val targetPage = if (lObj.has("targetSourcePdfPage") && !lObj.isNull("targetSourcePdfPage")) {
            lObj.getInt("targetSourcePdfPage")
        } else {
            null
        }
        val targetUri = if (lObj.has("targetUri") && !lObj.isNull("targetUri")) {
            lObj.optString("targetUri").takeIf { it.isNotBlank() }
        } else {
            null
        }
        val chapterId = if (lObj.has("chapterId") && !lObj.isNull("chapterId")) {
            lObj.optString("chapterId").takeIf { it.isNotBlank() }
        } else {
            null
        }
        return FrontMatterSourceLink(
            sourcePdfPage = lObj.getInt("sourcePdfPage"),
            linkIndexOnPage = lObj.getInt("linkIndexOnPage"),
            sourceLabel = lObj.getString("sourceLabel"),
            linkKind = lObj.getInt("linkKind"),
            targetSourcePdfPage = targetPage,
            targetUri = targetUri,
            chapterId = chapterId
        )
    }

    fun parseFrontMatterPageJson(
        pageJsonText: String,
        expectedSourcePdfPage: Int? = null,
        bookBaseDir: String = "books/$DEFAULT_BOOK_ID",
        globalAssetsById: Map<String, FrontMatterSourceAsset> = emptyMap()
    ): FrontMatterPage {
        val root = JSONObject(pageJsonText)
        val sourcePdfPage = root.getInt("sourcePdfPage")
        if (expectedSourcePdfPage != null && sourcePdfPage != expectedSourcePdfPage) {
            throw IllegalArgumentException(
                "sourcePdfPage mismatch: expected $expectedSourcePdfPage, found $sourcePdfPage"
            )
        }

        val pageAssetsArr = root.optJSONArray("sourceAssets") ?: JSONArray()
        val pageAssets = mutableListOf<FrontMatterSourceAsset>()
        for (i in 0 until pageAssetsArr.length()) {
            pageAssets.add(parseFrontMatterSourceAssetJson(pageAssetsArr.getJSONObject(i), bookBaseDir))
        }
        val localAssetsById = globalAssetsById + pageAssets.associateBy { it.assetId }

        val assetIdsArr = root.optJSONArray("sourceAssetIds") ?: JSONArray()
        val sourceAssetIds = (0 until assetIdsArr.length()).map { assetIdsArr.getString(it).trim() }

        val linksArr = root.optJSONArray("sourceLinks") ?: JSONArray()
        val sourceLinks = (0 until linksArr.length()).map {
            parseFrontMatterSourceLinkJson(linksArr.getJSONObject(it))
        }

        val blocksArr = root.getJSONArray("orderedContentBlocks")
        val orderedBlocks = parseContentBlocksArray(
            blocksArr = blocksArr,
            sectionId = "FM_P${sourcePdfPage.toString().padStart(3, '0')}",
            defaultSourcePdfPage = sourcePdfPage,
            assetsById = localAssetsById
        )

        return FrontMatterPage(
            schemaVersion = root.optInt("schemaVersion", 1),
            bookId = root.optString("bookId", DEFAULT_BOOK_ID).trim(),
            contentRegionId = root.optString("contentRegionId", FRONT_MATTER_REGION_ID).trim(),
            sourcePdfPage = sourcePdfPage,
            sourcePageNumbering = root.optString("sourcePageNumbering", ""),
            pageKind = FrontMatterPageKind.fromString(root.optString("pageKind", "EDUCATIONAL_CONTENT")),
            pageTitleFa = root.optString("pageTitleFa", "صفحه PDF $sourcePdfPage"),
            translationStatus = root.optString("translationStatus", ""),
            contentCompleteness = root.optString("contentCompleteness", ""),
            orderedContentBlocks = orderedBlocks,
            notesFa = root.optString("notesFa", ""),
            sourceAssetIds = sourceAssetIds,
            sourceLinks = sourceLinks,
            sourceAssets = pageAssets
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    /**
     * Validates the complete Front Matter Content Pack (PDF pages 1..39, 70 image objects,
     * 126 links, TOC entries, terminology rules, and non-omission of page 39).
     */
    @JvmOverloads
    fun validateFrontMatterPack(bookId: String = resolveDefaultBookId()): FrontMatterValidationResult {
        val errors = mutableListOf<String>()
        return try {
            val pack = getFrontMatterContentPack(bookId)
            if (pack == null) {
                return FrontMatterValidationResult(
                    isValid = false,
                    pageCount = 0,
                    resolvedImageAssetCount = 0,
                    resolvedLinkCount = 0,
                    tocEntryCount = 0,
                    errorMessages = listOf("Front matter content pack is missing for book '$bookId'")
                )
            }

            val manifest = pack.manifest
            if (manifest.contentRegionId != FRONT_MATTER_REGION_ID) {
                errors.add("Expected contentRegionId '$FRONT_MATTER_REGION_ID', found '${manifest.contentRegionId}'")
            }
            if (manifest.sourcePdfPageStart != 1 || manifest.sourcePdfPageEnd != 39) {
                errors.add("Front matter page range must be 1..39, found ${manifest.sourcePdfPageStart}..${manifest.sourcePdfPageEnd}")
            }
            if (manifest.nextSourcePage != 40 || manifest.nextContentStartsAt?.sourcePdfPage != 40) {
                errors.add("Front matter must declare nextContentStartsAt sourcePdfPage = 40")
            }

            val expectedPages = (1..39).toList()
            val actualPages = pack.pages.map { it.sourcePdfPage }
            if (actualPages != expectedPages) {
                errors.add("Front matter pages must be strictly 1..39 in order, found $actualPages")
            }

            val seenBlockIds = mutableSetOf<String>()
            for (page in pack.pages) {
                if (page.contentRegionId != FRONT_MATTER_REGION_ID) {
                    errors.add("Page ${page.sourcePdfPage} has invalid contentRegionId '${page.contentRegionId}'")
                }
                for (block in page.orderedContentBlocks) {
                    if (!seenBlockIds.add(block.blockId)) {
                        errors.add("Duplicate blockId '${block.blockId}' in front matter page ${page.sourcePdfPage}")
                    }
                    if (block.sourcePdfPage != page.sourcePdfPage) {
                        errors.add(
                            "Block '${block.blockId}' has sourcePdfPage=${block.sourcePdfPage} on page ${page.sourcePdfPage}"
                        )
                    }
                    if (block is SectionContentBlock.SourceImageBlock) {
                        val asset = pack.sourceAssetsById[block.assetId]
                        if (asset == null) {
                            errors.add("SourceImageBlock '${block.blockId}' references unknown assetId '${block.assetId}'")
                        } else if (!assetExists(asset.resolvedAssetPath)) {
                            errors.add("Missing image asset file '${asset.resolvedAssetPath}' for '${block.assetId}'")
                        }
                    }
                }
                for (assetId in page.sourceAssetIds) {
                    val asset = pack.sourceAssetsById[assetId]
                    if (asset == null) {
                        errors.add("Page ${page.sourcePdfPage} references unknown sourceAssetId '$assetId'")
                    } else if (asset.sourcePdfPage != page.sourcePdfPage) {
                        errors.add("Asset '$assetId' has sourcePdfPage=${asset.sourcePdfPage}, expected ${page.sourcePdfPage}")
                    }
                }
                for (link in page.sourceLinks) {
                    if (link.sourcePdfPage != page.sourcePdfPage) {
                        errors.add("Link on page ${page.sourcePdfPage} has mismatched sourcePdfPage=${link.sourcePdfPage}")
                    }
                }
            }

            // Validate all 70 image objects and placements CSV match
            val placements = getFrontMatterImagePlacements(bookId)
            if (pack.sourceAssetsById.size != 70) {
                errors.add("Expected 70 source image assets (PDFOBJ_000..PDFOBJ_069), found ${pack.sourceAssetsById.size}")
            }
            if (placements.size != 70) {
                errors.add("Expected 70 rows in source_image_placements_pages_001_039.csv, found ${placements.size}")
            }
            var existingAssetFiles = 0
            for (asset in pack.sourceAssetsById.values) {
                if (assetExists(asset.resolvedAssetPath)) {
                    existingAssetFiles++
                } else {
                    errors.add("Asset file does not exist: ${asset.resolvedAssetPath} (${asset.assetId})")
                }
            }

            // Validate all 126 source links
            if (pack.sourceLinks.size != 126) {
                errors.add("Expected 126 source links in source_links_pages_001_039.json, found ${pack.sourceLinks.size}")
            }
            val pageLinksTotal = pack.pages.sumOf { it.sourceLinks.size }
            if (pageLinksTotal != 126) {
                errors.add("Sum of page.sourceLinks across pages 1..39 must be 126, found $pageLinksTotal")
            }

            // Validate Page 6 TOC links for CHAPTER ONE -> 39 and HOW TO (LITERALLY) WIN AT CHESS -> 40
            val page6 = pack.pageForSourcePdfPage(6)
            val ch01Link = page6?.sourceLinks?.find { it.sourceLabel == "CHAPTER ONE" }
            val ch01TitleLink = page6?.sourceLinks?.find { it.sourceLabel == "HOW TO (LITERALLY) WIN AT CHESS" }
            if (ch01Link?.targetSourcePdfPage != 39) {
                errors.add("Page 6 TOC link 'CHAPTER ONE' must target sourcePdfPage 39, found ${ch01Link?.targetSourcePdfPage}")
            }
            if (ch01TitleLink?.targetSourcePdfPage != 40) {
                errors.add("Page 6 TOC link 'HOW TO (LITERALLY) WIN AT CHESS' must target sourcePdfPage 40, found ${ch01TitleLink?.targetSourcePdfPage}")
            }

            // Validate Page 21 internal links to pages 52 and 86
            val page21 = pack.pageForSourcePdfPage(21)
            val page21Targets = page21?.sourceLinks?.mapNotNull { it.targetSourcePdfPage } ?: emptyList()
            if (page21Targets != listOf(52, 86)) {
                errors.add("Page 21 sourceLinks must target PDF pages [52, 86], found $page21Targets")
            }

            // Validate Page 37 Part Divider has PDFOBJ_067 and independent text blocks
            val page37 = pack.pageForSourcePdfPage(37)
            if (page37 == null || !page37.sourceAssetIds.contains("PDFOBJ_067")) {
                errors.add("Page 37 must reference PDFOBJ_067")
            }

            // Validate Page 38 Part Introduction has 6 learning objectives and uses "میانه‌بازی" (never "وسط‌بازی")
            val page38 = pack.pageForSourcePdfPage(38)
            val listBlock38 = page38?.orderedContentBlocks
                ?.filterIsInstance<SectionContentBlock.OrderedListBlock>()
                ?.firstOrNull { it.blockId == "FM_P038_L001" }
            if (listBlock38 == null || listBlock38.items.size != 6) {
                errors.add("Page 38 block FM_P038_L001 must have 6 learning objective items")
            } else {
                val combined38 = listBlock38.items.joinToString(" ")
                if (!combined38.contains("میانه‌بازی")) {
                    errors.add("Page 38 block FM_P038_L001 must contain 'میانه‌بازی'")
                }
                if (combined38.contains("وسط‌بازی")) {
                    errors.add("Page 38 block FM_P038_L001 must NOT contain forbidden term 'وسط‌بازی'")
                }
            }

            // Validate Page 39 Interstitial Image has PDFOBJ_069
            val page39 = pack.pageForSourcePdfPage(39)
            val imageBlock39 = page39?.orderedContentBlocks
                ?.filterIsInstance<SectionContentBlock.SourceImageBlock>()
                ?.firstOrNull { it.blockId == "FM_P039_I001" }
            if (imageBlock39 == null || imageBlock39.assetId != "PDFOBJ_069") {
                errors.add("Page 39 must contain SourceImageBlock FM_P039_I001 with assetId 'PDFOBJ_069'")
            }

            FrontMatterValidationResult(
                isValid = errors.isEmpty(),
                pageCount = pack.pages.size,
                resolvedImageAssetCount = existingAssetFiles,
                resolvedLinkCount = pack.sourceLinks.size,
                tocEntryCount = pack.tableOfContents.entries.size,
                errorMessages = errors
            )
        } catch (e: Exception) {
            errors.add(e.message ?: e.javaClass.simpleName)
            FrontMatterValidationResult(
                isValid = false,
                pageCount = 0,
                resolvedImageAssetCount = 0,
                resolvedLinkCount = 0,
                tocEntryCount = 0,
                errorMessages = errors
            )
        }
    }

    @JvmOverloads
    fun getChapterManifest(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): ChapterManifest {
        val catalog = getLibraryCatalog()
        val book = catalog.findBook(bookId)
            ?: throw IllegalArgumentException("Book not found: $bookId")
        val chapterEntry = book.chapters.find { it.chapterId == chapterId }
            ?: throw IllegalArgumentException("Chapter not found: $chapterId in book $bookId")
        val chapterBaseDir = chapterEntry.manifestPath.substringBeforeLast('/', "")
        return parseChapterManifest(
            manifestJsonText = readAssetText(chapterEntry.manifestPath),
            expectedChapterId = chapterId,
            chapterBaseDir = chapterBaseDir
        )
    }

    private fun resolveChapterRelativePath(chapterBaseDir: String, rawPath: String): String {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) return ""
        if (chapterBaseDir.isEmpty() || trimmed.startsWith("books/") || trimmed.startsWith("$chapterBaseDir/")) {
            return trimmed
        }
        return "$chapterBaseDir/$trimmed"
    }

    fun parseChapterManifest(
        manifestJsonText: String,
        expectedChapterId: String? = null,
        chapterBaseDir: String = ""
    ): ChapterManifest {
        val root = JSONObject(manifestJsonText)
        val chapterId = root.getString("chapterId").trim()
        val bookId = root.optString("bookId", DEFAULT_BOOK_ID).trim()
        val effectiveBaseDir = chapterBaseDir.ifEmpty { "books/$bookId/$chapterId" }
        if (expectedChapterId != null && chapterId != expectedChapterId) {
            throw IllegalArgumentException(
                "Chapter ID mismatch in chapter.json: expected $expectedChapterId, found $chapterId"
            )
        }
        val sectionsArr = root.getJSONArray("sections")
        val sections = mutableListOf<SectionManifestEntry>()
        for (i in 0 until sectionsArr.length()) {
            val sObj = sectionsArr.getJSONObject(i)
            val pagesArr = sObj.getJSONArray("pdfPages")
            val pages = (0 until pagesArr.length()).map { pagesArr.getInt(it) }
            val sectionId = sObj.getString("sectionId").trim()
            val titleEn = sObj.getString("titleEn")
            val titleFa = sObj.getString("titleFa")
            val kindStr = sObj.optString("sectionKind", "")
            val kind = if (kindStr.isNotBlank()) {
                SectionKind.fromString(kindStr)
            } else {
                SectionKind.fromSectionMetadata(sectionId, titleEn, titleFa)
            }
            val rawSectionFile = sObj.optString("file", "").ifBlank { sObj.optString("contentFile", "") }
            sections.add(
                SectionManifestEntry(
                    sectionId = sectionId,
                    order = sObj.optInt("order", i + 1),
                    sectionKind = kind,
                    titleEn = titleEn,
                    titleFa = titleFa,
                    pdfPages = pages,
                    contentFile = resolveChapterRelativePath(effectiveBaseDir, rawSectionFile)
                )
            )
        }

        val rawDiagramsFile = root.optString("diagramsFile", "diagrams.json")
        val rawPgnFile = root.optString("pgnSequencesFile", "").ifBlank { root.optString("pgnFile", "pgn_sequences.json") }
        val rawExercisesFile = root.optString("exercisesFile", "exercises.json")
        val rawNarrationFile = root.optString("narrationManifestFile", "").ifBlank {
            root.optString("narrationManifest", "")
        }

        return ChapterManifest(
            schemaVersion = root.optInt("schemaVersion", 1),
            bookId = bookId,
            chapterId = chapterId,
            chapterNumber = root.optInt("chapterNumber", 1),
            titleEn = root.getString("titleEn"),
            titleFa = root.getString("titleFa"),
            diagramsFile = resolveChapterRelativePath(effectiveBaseDir, rawDiagramsFile),
            pgnFile = resolveChapterRelativePath(effectiveBaseDir, rawPgnFile),
            exercisesFile = resolveChapterRelativePath(effectiveBaseDir, rawExercisesFile),
            narrationManifest = resolveChapterRelativePath(effectiveBaseDir, rawNarrationFile),
            sections = sections.sortedBy { it.order }
        )
    }

    @JvmOverloads
    fun getDiagrams(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): List<DiagramItem> {
        val cacheKey = "$bookId:$chapterId"
        cachedDiagramsByChapter[cacheKey]?.let { return it }
        val loaded = loadDiagramsFromAssets(chapterId = chapterId, bookId = bookId)
        cachedDiagramsByChapter[cacheKey] = loaded
        return loaded
    }

    @JvmOverloads
    fun getSections(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): List<SectionItem> {
        val cacheKey = "$bookId:$chapterId"
        cachedSectionsByChapter[cacheKey]?.let { return it }
        val loaded = loadSectionsFromAssets(chapterId = chapterId, bookId = bookId)
        cachedSectionsByChapter[cacheKey] = loaded
        return loaded
    }

    @JvmOverloads
    fun getPgnSequence(
        pgnRef: String,
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): PgnSequence? {
        return getAllPgnSequences(chapterId = chapterId, bookId = bookId)[pgnRef]
    }

    @JvmOverloads
    fun getAllPgnSequences(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): Map<String, PgnSequence> {
        val cacheKey = "$bookId:$chapterId"
        cachedPgnSequencesByChapter[cacheKey]?.let { return it }
        val loaded = loadPgnSequencesFromAssets(chapterId = chapterId, bookId = bookId)
        cachedPgnSequencesByChapter[cacheKey] = loaded
        return loaded
    }

    @JvmOverloads
    fun getExercises(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): List<ExerciseDefinition> {
        val cacheKey = "$bookId:$chapterId"
        cachedExercisesByChapter[cacheKey]?.let { return it }
        val loaded = loadExercisesFromAssets(chapterId = chapterId, bookId = bookId)
        cachedExercisesByChapter[cacheKey] = loaded
        return loaded
    }

    /**
     * Validates a chapter content pack including manifest relative paths, section files,
     * unique block IDs, diagram FENs, exercise definitions, handler types, and full PGN move replays.
     */
    @JvmOverloads
    fun validateChapterPack(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): ContentValidationResult {
        val errors = mutableListOf<String>()
        val invalidRelativePaths = mutableListOf<String>()
        val invalidPgnRefs = mutableListOf<String>()

        return try {
            val manifest = getChapterManifest(chapterId = chapterId, bookId = bookId)

            val pathsToCheck = buildList {
                add(manifest.diagramsFile)
                add(manifest.pgnFile)
                if (manifest.exercisesFile.isNotBlank()) add(manifest.exercisesFile)
                if (manifest.narrationManifest.isNotBlank()) add(manifest.narrationManifest)
                manifest.sections.forEach { add(it.contentFile) }
            }
            for (path in pathsToCheck) {
                if (!ContentBlockValidator.isSafeRelativeAssetPath(path)) {
                    invalidRelativePaths.add(path)
                    errors.add("Invalid relative asset path: $path")
                }
            }

            if (manifest.sections.isEmpty()) {
                errors.add("Chapter $chapterId has no sections")
            }

            val sections = getSections(chapterId = chapterId, bookId = bookId)
            val diagrams = getDiagrams(chapterId = chapterId, bookId = bookId)
            val allSequences = getAllPgnSequences(chapterId = chapterId, bookId = bookId)
            val exercises = getExercises(chapterId = chapterId, bookId = bookId)

            val blockValidation = ContentBlockValidator.validateSectionsAndBlocks(
                sections = sections,
                diagrams = diagrams,
                expectedSectionSlotIds = manifest.sections.map { it.sectionId },
                exercises = exercises
            )
            errors.addAll(blockValidation.errorMessages)

            val diagramsById = diagrams.associateBy { it.diagramId }

            for (exercise in exercises) {
                if (!ExerciseHandlerRegistry.isSupportedHandlerType(exercise.handlerType)) {
                    errors.add("Unsupported exercise handlerType '${exercise.handlerType}' in exercise '${exercise.exerciseId}'")
                }
                val diagram = diagramsById[exercise.diagramId]
                if (diagram != null) {
                    for (solRef in exercise.acceptedSolutionPgnRefs) {
                        val solSeq = allSequences[solRef]
                        if (solSeq == null) {
                            invalidPgnRefs.add(solRef)
                            errors.add("Missing solution PGN '$solRef' for exercise '${exercise.exerciseId}'")
                        } else {
                            if (!solSeq.validateIntegrity(expectedStartFen = diagram.initialFen)) {
                                invalidPgnRefs.add(solRef)
                                errors.add("Solution PGN '$solRef' failed integrity check from diagram '${diagram.diagramId}' initialFen")
                            }
                            if (exercise.handlerType.equals("MATE_IN_ONE", ignoreCase = true)) {
                                if (solSeq.moves.size != 1) {
                                    invalidPgnRefs.add(solRef)
                                    errors.add("MATE_IN_ONE solution PGN '$solRef' must have 1 move, found ${solSeq.moves.size}")
                                }
                                val finalFen = solSeq.fens.lastOrNull()
                                if (finalFen == null || !ChessEngine(finalFen).isCheckmate()) {
                                    invalidPgnRefs.add(solRef)
                                    errors.add("MATE_IN_ONE solution PGN '$solRef' does not end in checkmate")
                                }
                            }
                        }
                    }
                }
            }

            for (diagram in diagrams) {
                val pgnRef = diagram.pgnRef
                if (pgnRef != null) {
                    val seq = allSequences[pgnRef]
                    if (seq == null) {
                        invalidPgnRefs.add(pgnRef)
                        errors.add("Diagram '${diagram.diagramId}' references missing pgnRef '$pgnRef'")
                    } else {
                        val startFen = seq.fens.firstOrNull() ?: ChessEngine.STANDARD_START_FEN
                        if (!seq.validateIntegrity(
                                expectedStartFen = startFen,
                                expectedFinalFen = diagram.initialFen
                            )
                        ) {
                            invalidPgnRefs.add(pgnRef)
                            errors.add("Diagram '${diagram.diagramId}' PGN '$pgnRef' failed replay integrity")
                        }
                    }
                }

                for (solRef in diagram.solutionPgnRefs) {
                    val solSeq = allSequences[solRef]
                    if (solSeq == null) {
                        invalidPgnRefs.add(solRef)
                        errors.add("Diagram '${diagram.diagramId}' references missing solution_pgn_ref '$solRef'")
                    } else if (!solSeq.validateIntegrity(expectedStartFen = diagram.initialFen)) {
                        invalidPgnRefs.add(solRef)
                        errors.add("Diagram '${diagram.diagramId}' solution_pgn_ref '$solRef' failed replay integrity")
                    }
                }
            }

            blockValidation.copy(
                isValid = blockValidation.isValid &&
                    invalidRelativePaths.isEmpty() &&
                    invalidPgnRefs.isEmpty() &&
                    errors.isEmpty(),
                invalidPgnRefs = invalidPgnRefs,
                invalidRelativePaths = invalidRelativePaths,
                errorMessages = errors
            )
        } catch (e: Exception) {
            errors.add(e.message ?: e.javaClass.simpleName)
            ContentValidationResult(
                isValid = false,
                duplicateBlockIds = emptySet(),
                invalidDiagramBlockIds = emptyList(),
                missingSectionSlotIds = emptyList(),
                invalidPgnRefs = invalidPgnRefs,
                invalidRelativePaths = invalidRelativePaths,
                errorMessages = errors
            )
        }
    }

    @JvmOverloads
    fun validateAllReferencesAndReplays(
        chapterId: String = resolveDefaultChapterId(),
        bookId: String = resolveDefaultBookId()
    ): Boolean {
        return validateChapterPack(chapterId = chapterId, bookId = bookId).isValid
    }

    private fun loadDiagramsFromAssets(
        chapterId: String,
        bookId: String
    ): List<DiagramItem> {
        val manifest = getChapterManifest(chapterId = chapterId, bookId = bookId)
        val jsonString = readAssetText(manifest.diagramsFile)
        val root = JSONObject(jsonString)
        val diagramsArray = root.getJSONArray("diagrams")
        val result = mutableListOf<DiagramItem>()

        for (i in 0 until diagramsArray.length()) {
            val obj = diagramsArray.getJSONObject(i)
            val id = obj.getString("diagram_id").trim()
            val page = obj.getInt("pdf_page")
            val section = obj.optString("section", "")
            val bookContext = obj.optString("book_context", "")
            val diagramKind = obj.optString("diagram_kind", "")
            val interaction = obj.optString("recommended_interaction", "")
            val fen = obj.getString("fen").trim()
            val sideToMove = obj.optString("side_to_move", "white")
            val moveHistory = obj.optString("move_history", "")
            val pgnRef = if (obj.isNull("pgn_ref")) null else obj.optString("pgn_ref").takeIf { it.isNotBlank() }

            val solutionRefs = mutableListOf<String>()
            val solutionRefsArray = obj.optJSONArray("solution_pgn_refs")
            if (solutionRefsArray != null) {
                for (s in 0 until solutionRefsArray.length()) {
                    solutionRefs.add(solutionRefsArray.getString(s))
                }
            }

            val highlights = mutableListOf<String>()
            val highlightsArray = obj.optJSONArray("highlight_squares")
            if (highlightsArray != null) {
                for (h in 0 until highlightsArray.length()) {
                    highlights.add(highlightsArray.getString(h))
                }
            }

            val related = mutableListOf<String>()
            val relatedArray = obj.optJSONArray("related_diagram_ids")
            if (relatedArray != null) {
                for (r in 0 until relatedArray.length()) {
                    related.add(relatedArray.getString(r))
                }
            }

            val visualConf = obj.optString("visual_confidence", "high")
            val notes = obj.optString("implementation_notes", "")

            val rawAuditStatus = if (obj.isNull("audit_status")) "" else obj.optString("audit_status", "")
            val rawAuditWarning = when {
                !obj.isNull("audit_warning_fa") && obj.has("audit_warning_fa") ->
                    obj.optString("audit_warning_fa").takeIf { it.isNotBlank() }
                !obj.isNull("audit_warning") && obj.has("audit_warning") ->
                    obj.optString("audit_warning").takeIf { it.isNotBlank() }
                else -> null
            }

            val (auditStatus, auditWarning) = when (rawAuditStatus.trim().uppercase()) {
                "BLOCKED_NEEDS_HUMAN_REVIEW" -> {
                    Pair(
                        AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW,
                        rawAuditWarning
                            ?: "⚠️ اخطار ممیزی شطرنج: این دیاگرام مسدود شده و نیازمند بازبینی انسانی است."
                    )
                }
                else -> {
                    Pair(AuditStatus.VALID, rawAuditWarning)
                }
            }

            result.add(
                DiagramItem(
                    diagramId = id,
                    pdfPage = page,
                    sectionName = section,
                    bookContext = bookContext,
                    diagramKind = diagramKind,
                    recommendedInteraction = interaction,
                    initialFen = fen,
                    currentFen = fen,
                    sideToMove = sideToMove,
                    moveHistory = moveHistory,
                    pgnRef = pgnRef,
                    solutionPgnRefs = solutionRefs,
                    highlightSquares = highlights,
                    relatedDiagramIds = related,
                    visualConfidence = visualConf,
                    implementationNotes = notes,
                    auditStatus = auditStatus,
                    auditWarning = auditWarning
                )
            )
        }

        return result
    }

    private fun loadSectionsFromAssets(
        chapterId: String,
        bookId: String
    ): List<SectionItem> {
        val manifest = getChapterManifest(chapterId = chapterId, bookId = bookId)
        return manifest.sections.mapIndexed { idx, entry ->
            val sectionJson = JSONObject(readAssetText(entry.contentFile))
            val fileSectionId = sectionJson.getString("sectionId").trim()
            require(fileSectionId == entry.sectionId) {
                "Section ID mismatch in ${entry.contentFile}: expected ${entry.sectionId}, found $fileSectionId"
            }

            val placeholderNoticeFa = sectionJson.optString(
                "placeholderNoticeFa",
                TranslationSlot.DEFAULT_PLACEHOLDER_NOTICE
            )
            val translationStatus = TranslationStatus.fromString(
                sectionJson.optString("translationStatus", "PENDING")
            )
            val blocksArr = sectionJson.optJSONArray("orderedContentBlocks")
                ?: sectionJson.getJSONArray("orderedBlocks")
            val blocks = parseContentBlocksArray(
                blocksArr = blocksArr,
                sectionId = entry.sectionId,
                defaultSourcePdfPage = entry.pdfPages.firstOrNull()
            )

            val diagramIds = blocks.mapNotNull { block ->
                when (block) {
                    is SectionContentBlock.DiagramBlock -> block.diagramId
                    is SectionContentBlock.ExerciseBlock -> block.diagramId
                    else -> null
                }
            }

            SectionItem(
                sectionId = entry.sectionId,
                index = idx,
                titleEn = entry.titleEn,
                titleFa = entry.titleFa,
                pdfPages = entry.pdfPages,
                diagramIds = diagramIds,
                translationSlot = TranslationSlot(
                    sectionId = entry.sectionId,
                    orderedContentBlocks = blocks,
                    placeholderNotice = placeholderNoticeFa,
                    translationStatus = translationStatus
                ),
                sectionKind = entry.sectionKind
            )
        }
    }

    @JvmOverloads
    fun parseContentBlocksArray(
        blocksArr: JSONArray,
        sectionId: String = "",
        defaultSourcePdfPage: Int? = null,
        assetsById: Map<String, FrontMatterSourceAsset> = emptyMap()
    ): List<SectionContentBlock> {
        val blocks = mutableListOf<SectionContentBlock>()
        for (i in 0 until blocksArr.length()) {
            val bObj = blocksArr.getJSONObject(i)
            val type = bObj.getString("type").trim()
            val blockId = bObj.getString("blockId").trim()
            val textFa = if (bObj.isNull("textFa")) null else bObj.optString("textFa").takeIf { it.isNotEmpty() }
            val titleFa = if (bObj.isNull("titleFa")) null else bObj.optString("titleFa").takeIf { it.isNotEmpty() }
            val url = if (bObj.isNull("url")) null else bObj.optString("url").takeIf { it.isNotEmpty() }
            val sourcePdfPage = if (bObj.has("sourcePdfPage") && !bObj.isNull("sourcePdfPage")) {
                bObj.getInt("sourcePdfPage")
            } else {
                defaultSourcePdfPage
            }
            val sourceVisibleText = if (bObj.has("sourceVisibleText") && !bObj.isNull("sourceVisibleText")) {
                bObj.optString("sourceVisibleText").takeIf { it.isNotBlank() }
            } else {
                null
            }
            val sourceUrl = if (bObj.has("sourceUrl") && !bObj.isNull("sourceUrl")) {
                bObj.optString("sourceUrl").takeIf { it.isNotBlank() }
            } else {
                null
            }
            val sourcePageRefsArr = bObj.optJSONArray("sourcePageReferences")
            val sourcePageReferences = if (sourcePageRefsArr != null) {
                (0 until sourcePageRefsArr.length()).map { sourcePageRefsArr.getInt(it) }
            } else {
                emptyList()
            }

            val block = when (type.lowercase()) {
                "paragraph" -> SectionContentBlock.ParagraphBlock(
                    blockId = blockId,
                    textFa = textFa,
                    showPlaceholderWhenEmpty = bObj.optBoolean("showPlaceholderWhenEmpty", true),
                    sourceVisibleText = sourceVisibleText,
                    sourceUrl = sourceUrl,
                    sourcePageReferences = sourcePageReferences,
                    sourcePdfPage = sourcePdfPage
                )
                "heading" -> {
                    val tocTarget = if (bObj.has("tocTargetSourcePdfPage") && !bObj.isNull("tocTargetSourcePdfPage")) {
                        bObj.getInt("tocTargetSourcePdfPage")
                    } else {
                        null
                    }
                    val tocKind = if (bObj.has("tocEntryKind") && !bObj.isNull("tocEntryKind")) {
                        bObj.optString("tocEntryKind").takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    val chId = if (bObj.has("chapterId") && !bObj.isNull("chapterId")) {
                        bObj.optString("chapterId").takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    SectionContentBlock.HeadingBlock(
                        blockId = blockId,
                        textFa = textFa,
                        level = bObj.optInt("level", 2),
                        showPlaceholderWhenEmpty = bObj.optBoolean("showPlaceholderWhenEmpty", false),
                        sourceVisibleText = sourceVisibleText,
                        tocTargetSourcePdfPage = tocTarget,
                        tocEntryKind = tocKind,
                        chapterId = chId,
                        sourcePdfPage = sourcePdfPage
                    )
                }
                "diagram" -> SectionContentBlock.DiagramBlock(
                    blockId = blockId,
                    diagramId = bObj.getString("diagramId").trim(),
                    sourcePdfPage = sourcePdfPage
                )
                "exercise" -> {
                    val diagramId = bObj.getString("diagramId").trim()
                    val exerciseId = bObj.optString(
                        "exerciseId",
                        SectionContentBlock.defaultExerciseIdForDiagram(diagramId)
                    ).trim()
                    SectionContentBlock.ExerciseBlock(
                        blockId = blockId,
                        diagramId = diagramId,
                        exerciseId = exerciseId,
                        sourcePdfPage = sourcePdfPage
                    )
                }
                "footnote" -> SectionContentBlock.FootnoteBlock(
                    blockId = blockId,
                    textFa = textFa,
                    showPlaceholderWhenEmpty = bObj.optBoolean("showPlaceholderWhenEmpty", false),
                    sourcePdfPage = sourcePdfPage
                )
                "externalreference", "external_reference" -> SectionContentBlock.ExternalReferenceBlock(
                    blockId = blockId,
                    titleFa = titleFa,
                    url = url,
                    isAuthorizedAndEnabled = bObj.optBoolean("isAuthorizedAndEnabled", false),
                    sourcePdfPage = sourcePdfPage
                )
                "sourceimage", "source_image" -> {
                    val assetId = bObj.getString("assetId").trim()
                    val asset = assetsById[assetId]
                    SectionContentBlock.SourceImageBlock(
                        blockId = blockId,
                        assetId = assetId,
                        descriptionFa = bObj.optString("descriptionFa", asset?.descriptionFa ?: ""),
                        preserveOriginal = bObj.optBoolean("preserveOriginal", true),
                        resolvedAssetPath = asset?.resolvedAssetPath ?: "",
                        imageWidth = asset?.width ?: 0,
                        imageHeight = asset?.height ?: 0,
                        sourcePdfPage = sourcePdfPage
                    )
                }
                "orderedlist", "ordered_list" -> {
                    val itemsArr = bObj.getJSONArray("items")
                    val items = (0 until itemsArr.length()).map { itemsArr.getString(it) }
                    SectionContentBlock.OrderedListBlock(
                        blockId = blockId,
                        titleFa = titleFa,
                        items = items,
                        sourcePdfPage = sourcePdfPage
                    )
                }
                "table" -> {
                    val rowsArr = bObj.getJSONArray("rows")
                    val rows = (0 until rowsArr.length()).map { rIdx ->
                        val rowArr = rowsArr.getJSONArray(rIdx)
                        (0 until rowArr.length()).map { cIdx -> rowArr.getString(cIdx) }
                    }
                    SectionContentBlock.TableBlock(
                        blockId = blockId,
                        titleFa = titleFa,
                        rows = rows,
                        sourcePdfPage = sourcePdfPage
                    )
                }
                else -> throw IllegalArgumentException(
                    "Unsupported content block type '$type' in section '$sectionId' (blockId='$blockId')"
                )
            }
            blocks.add(block)
        }
        return blocks
    }

    private fun loadPgnSequencesFromAssets(
        chapterId: String,
        bookId: String
    ): Map<String, PgnSequence> {
        val manifest = getChapterManifest(chapterId = chapterId, bookId = bookId)
        val jsonString = readAssetText(manifest.pgnFile)
        val root = JSONObject(jsonString)
        val map = mutableMapOf<String, PgnSequence>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = root.optJSONObject(key) ?: continue
            if (!obj.has("moves") || !obj.has("fens")) continue
            val id = obj.optString("id", key)
            val desc = obj.optString("description", "")
            val movesJson = obj.getJSONArray("moves")
            val fensJson = obj.getJSONArray("fens")

            val moves = mutableListOf<String>()
            for (m in 0 until movesJson.length()) {
                moves.add(movesJson.getString(m))
            }

            val fens = mutableListOf<String>()
            for (f in 0 until fensJson.length()) {
                fens.add(fensJson.getString(f))
            }

            map[key] = PgnSequence(id, desc, moves, fens)
        }
        return map
    }

    private fun loadExercisesFromAssets(
        chapterId: String,
        bookId: String
    ): List<ExerciseDefinition> {
        val manifest = getChapterManifest(chapterId = chapterId, bookId = bookId)
        if (manifest.exercisesFile.isBlank()) return emptyList()
        val root = JSONObject(readAssetText(manifest.exercisesFile))
        val fileChapterId = root.optString("chapterId", chapterId).trim()
        require(fileChapterId == chapterId) {
            "Chapter ID mismatch in ${manifest.exercisesFile}: expected $chapterId, found $fileChapterId"
        }
        val arr = root.optJSONArray("exercises") ?: return emptyList()
        val result = mutableListOf<ExerciseDefinition>()
        for (i in 0 until arr.length()) {
            val eObj = arr.getJSONObject(i)
            val refsArr = eObj.optJSONArray("acceptedSolutionIds")
                ?: eObj.getJSONArray("acceptedSolutionPgnRefs")
            val refs = (0 until refsArr.length()).map { refsArr.getString(it) }
            val handlerType = eObj.optString("exerciseType", "").ifBlank {
                eObj.getString("handlerType")
            }.trim()
            val allowHints = if (eObj.has("allowLegalMoveHints")) {
                eObj.optBoolean("allowLegalMoveHints", true)
            } else {
                eObj.optBoolean("showLegalMovesHint", true)
            }
            val initialFen = if (eObj.isNull("initialFen")) null else eObj.optString("initialFen", "").trim().takeIf { it.isNotEmpty() }
            val sideToMove = if (eObj.isNull("sideToMove")) null else eObj.optString("sideToMove", "").trim().takeIf { it.isNotEmpty() }
            val titleFa = if (eObj.isNull("titleFa")) null else eObj.optString("titleFa", "").trim().takeIf { it.isNotEmpty() }
            val promptFa = if (eObj.isNull("promptFa")) null else eObj.optString("promptFa", "").trim().takeIf { it.isNotEmpty() }
            val successFeedbackFa = if (eObj.isNull("successFeedbackFa")) null else eObj.optString("successFeedbackFa", "").trim().takeIf { it.isNotEmpty() }
            val wrongMoveFeedbackFa = if (eObj.isNull("wrongMoveFeedbackFa")) null else eObj.optString("wrongMoveFeedbackFa", "").trim().takeIf { it.isNotEmpty() }
            val illegalMoveFeedbackFa = if (eObj.isNull("illegalMoveFeedbackFa")) null else eObj.optString("illegalMoveFeedbackFa", "").trim().takeIf { it.isNotEmpty() }
            val hintFa = if (eObj.isNull("hintFa")) null else eObj.optString("hintFa", "").trim().takeIf { it.isNotEmpty() }

            result.add(
                ExerciseDefinition(
                    exerciseId = eObj.getString("exerciseId").trim(),
                    diagramId = eObj.getString("diagramId").trim(),
                    handlerType = handlerType,
                    acceptedSolutionPgnRefs = refs,
                    showLegalMovesHint = allowHints,
                    allowLegalMoveHints = allowHints,
                    initialFen = initialFen,
                    sideToMove = sideToMove,
                    titleFa = titleFa,
                    promptFa = promptFa,
                    successFeedbackFa = successFeedbackFa,
                    wrongMoveFeedbackFa = wrongMoveFeedbackFa,
                    illegalMoveFeedbackFa = illegalMoveFeedbackFa,
                    hintFa = hintFa
                )
            )
        }
        return result
    }

    companion object {
        const val DEFAULT_LIBRARY_ASSET_PATH = "books/library.json"
        const val DEFAULT_BOOK_ID = "how_to_win_at_chess"
        const val DEFAULT_CHAPTER_ID = "CH01"
        const val FRONT_MATTER_REGION_ID = "FRONT_MATTER"
    }
}
