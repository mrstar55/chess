package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import com.example.data.UserPreferencesManager
import com.example.model.ReaderPage
import com.example.model.ReaderPaginator
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

enum class NarrationPlaybackSpeed(val multiplier: Float, val labelFa: String) {
    SPEED_0_8X(0.8f, "0.8x"),
    SPEED_1_0X(1.0f, "1.0x"),
    SPEED_1_2X(1.2f, "1.2x");

    fun nextSpeed(): NarrationPlaybackSpeed = when (this) {
        SPEED_0_8X -> SPEED_1_0X
        SPEED_1_0X -> SPEED_1_2X
        SPEED_1_2X -> SPEED_0_8X
    }
}

enum class NarrationPlaybackMode(val labelFa: String) {
    CURRENT_PAGE("پخش همین صفحه"),
    CONTINUOUS_CHAPTER("پخش پیوسته فصل")
}

enum class NarrationStatus {
    IDLE,
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    UNAVAILABLE
}

data class NarrationBlockAssetEntry(
    val sectionId: String,
    val blockId: String,
    val assetRelativePath: String,
    val status: String = NarrationManifestValidator.STATUS_AVAILABLE,
    val durationMs: Int? = null
) {
    val compositeKey: String get() = "$sectionId:$blockId"
    val isAvailable: Boolean get() = status == NarrationManifestValidator.STATUS_AVAILABLE
}

data class NarrationManifest(
    val schemaVersion: Int = 1,
    val chapterId: String = "",
    val chapterIds: Set<String> = emptySet(),
    val declaredSectionIds: Set<String> = emptySet(),
    val entriesByCompositeKey: Map<String, NarrationBlockAssetEntry> = emptyMap(),
    val entriesByBlockId: Map<String, NarrationBlockAssetEntry> = emptyMap(),
    val rejectedEntryCount: Int = 0,
    val isValid: Boolean = true
) {
    fun resolveAssetPath(sectionId: String, blockId: String): String? {
        val normalizedBlockId = ReaderPaginator.originalBlockId(blockId)
        return entriesByCompositeKey["$sectionId:$normalizedBlockId"]?.assetRelativePath
            ?: entriesByBlockId[normalizedBlockId]?.assetRelativePath
    }
}

data class NarrationManifestValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val chapterIds: List<String> = emptyList(),
    val sectionIdsByChapter: Map<String, Set<String>> = emptyMap(),
    val validatedBlockCount: Int = 0
)

object NarrationManifestValidator {

    const val STATUS_AVAILABLE = "AVAILABLE"
    const val STATUS_UNAVAILABLE = "UNAVAILABLE"

    private val ALLOWED_STATUSES = setOf(STATUS_AVAILABLE, STATUS_UNAVAILABLE)
    private val ALLOWED_ROOT_KEYS = setOf("schemaVersion", "chapters")
    private val ALLOWED_CHAPTER_KEYS = setOf("chapterId", "sections")
    private val ALLOWED_SECTION_KEYS = setOf("sectionId", "blocks")
    private val ALLOWED_BLOCK_KEYS = setOf("blockId", "audioAssetPath", "status", "durationMs")

    private val VALID_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{1,128}$")
    private val SAFE_AUDIO_PATH_REGEX = Regex("^[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*\\.(mp3|wav|ogg|m4a|aac)$")
    private val DRIVE_LETTER_REGEX = Regex("^[A-Za-z]:.*")
    private val ALLOWED_EXTENSIONS = listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac")

    /**
     * Validates a chapterId, sectionId, or blockId:
     * - Non-blank, alphanumeric + underscore/hyphen (1..128 chars)
     * - Never allows pagination slice suffix (`__part_`) as a manifest key
     */
    fun isValidBlockOrSectionId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        if (id.contains("__part_")) return false
        return VALID_ID_REGEX.matches(id)
    }

    /**
     * Validates `status` field of a narration block (`"AVAILABLE"` or `"UNAVAILABLE"`).
     */
    fun isValidStatus(status: String?): Boolean {
        return status != null && status in ALLOWED_STATUSES
    }

    /**
     * Strictly validates that `audioAssetPath` inside `manifest.json` is a safe relative path
     * under `assets/narration/`:
     * - No absolute paths (starting with `/` or `\` or drive letter)
     * - No URLs (`http://`, `https://`, `file://`, `content://`, or any `://`)
     * - No path traversal (`..` or `.` segments)
     * - Must match `^[A-Za-z0-9_\-]+(/[A-Za-z0-9_\-]+)*\.(mp3|wav|ogg|m4a|aac)$`
     */
    fun isValidRelativeAssetPath(assetPath: String?): Boolean {
        if (assetPath.isNullOrBlank()) return false
        val trimmed = assetPath.trim()
        if (trimmed != assetPath) return false
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.contains("\\")) return false
        if (DRIVE_LETTER_REGEX.matches(trimmed)) return false
        val lower = trimmed.lowercase()
        if (lower.contains("://") ||
            lower.startsWith("http:") ||
            lower.startsWith("https:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("asset:") ||
            lower.startsWith("data:") ||
            lower.startsWith("javascript:")
        ) {
            return false
        }
        if (trimmed.contains("//")) return false
        val segments = trimmed.split("/")
        if (segments.isEmpty()) return false
        for (seg in segments) {
            if (seg.isEmpty() || seg == "." || seg == ".." || seg.contains("..")) {
                return false
            }
        }
        val fileName = segments.last().lowercase()
        if (ALLOWED_EXTENSIONS.none { fileName.endsWith(it) && fileName.length > it.length }) {
            return false
        }
        return SAFE_AUDIO_PATH_REGEX.matches(trimmed)
    }

    /**
     * Resolves an `audioAssetPath` strictly under `<assetsDir>/narration/<audioAssetPath>`,
     * matching Android Runtime `appContext.assets.open("narration/$audioAssetPath")` and
     * Gradle `validateContentPacks`. Never falls back to `<assetsDir>/<audioAssetPath>` or
     * chapter directories, and verifies canonical path containment under `<assetsDir>/narration/`.
     */
    fun resolveAudioFileInAssetsDir(assetsDir: java.io.File, audioAssetPath: String): java.io.File? {
        if (!isValidRelativeAssetPath(audioAssetPath)) return null
        return try {
            val narrationRootCanonical = java.io.File(assetsDir, "narration").canonicalFile
            val candidateCanonical = java.io.File(assetsDir, "narration/$audioAssetPath").canonicalFile
            val rootPrefix = narrationRootCanonical.path + java.io.File.separator
            if (!candidateCanonical.path.startsWith(rootPrefix)) {
                null
            } else {
                candidateCanonical.takeIf { it.exists() && it.isFile }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractOptionalDurationMs(obj: JSONObject): Pair<Boolean, Int?> {
        if (!obj.has("durationMs")) {
            return true to null
        }
        if (obj.isNull("durationMs")) {
            return false to null
        }
        val raw = obj.opt("durationMs")
        val value = when (raw) {
            is Int -> raw
            is Long -> if (raw in 0..Int.MAX_VALUE) raw.toInt() else -1
            else -> -1
        }
        return if (value >= 0) (true to value) else (false to null)
    }

    private fun JSONObject.keySetList(): List<String> {
        val list = mutableListOf<String>()
        val it = keys()
        while (it.hasNext()) {
            list.add(it.next())
        }
        return list
    }

    /**
     * Strictly validates the nested `chapters -> sections -> blocks` structure of a Narration Manifest JSON
     * against the single unified contract (`content_schemas/narration_manifest.schema.json` & Gradle `validateContentPacks`):
     * - Root object must have only `schemaVersion` (const `1`) and `chapters` (array; empty `[]` is allowed).
     * - Each Chapter object must have only `chapterId` (valid, unique) and `sections` (array).
     * - When [expectedChapterId] is provided and `chapters` is non-empty, [expectedChapterId] must match the manifest's chapter(s).
     * - Each Section object must have only `sectionId` (valid, unique in chapter, matching real sections when provided) and `blocks` (array).
     * - Each Block object must have only `blockId` (valid, unique in section/chapter, matching real blocks when provided),
     *   `audioAssetPath` (safe relative path with supported audio extension), `status` (`"AVAILABLE"` or `"UNAVAILABLE"`),
     *   and optional `durationMs` (integer `>= 0`).
     * - When `status == "AVAILABLE"` and [assetExistsPredicate] is provided, the physical audio file must exist.
     * - When `status == "UNAVAILABLE"`, a missing audio file is permitted.
     */
    fun validateManifestStructure(
        jsonString: String,
        expectedChapterId: String? = null,
        validSectionIds: Set<String> = emptySet(),
        validSectionIdsByChapter: Map<String, Set<String>> = emptyMap(),
        validBlockIdsBySection: Map<String, Set<String>> = emptyMap(),
        assetExistsPredicate: ((String) -> Boolean)? = null
    ): NarrationManifestValidationResult {
        val errors = mutableListOf<String>()
        val seenChapterIds = linkedSetOf<String>()
        val sectionIdsByChapter = linkedMapOf<String, Set<String>>()
        var blockCount = 0

        try {
            val root = JSONObject(jsonString)

            for (key in root.keySetList()) {
                if (key !in ALLOWED_ROOT_KEYS) {
                    errors.add("Unsupported root property in narration manifest: '$key'")
                }
            }

            val rawSchemaVer = root.opt("schemaVersion")
            val schemaVersion = when (rawSchemaVer) {
                is Int -> rawSchemaVer
                is Long -> rawSchemaVer.toInt()
                else -> -1
            }
            if (!root.has("schemaVersion") || schemaVersion != 1) {
                errors.add("Invalid or missing schemaVersion: '$rawSchemaVer' (expected integer 1)")
            }

            if (!root.has("chapters") || root.opt("chapters") !is org.json.JSONArray) {
                errors.add("Manifest must contain a required 'chapters' array")
            } else {
                val chaptersArr = root.getJSONArray("chapters")
                for (c in 0 until chaptersArr.length()) {
                    val chapterObj = chaptersArr.optJSONObject(c)
                    if (chapterObj == null) {
                        errors.add("Chapter entry at index $c is not a JSON object")
                        continue
                    }

                    for (key in chapterObj.keySetList()) {
                        if (key !in ALLOWED_CHAPTER_KEYS) {
                            errors.add("Unsupported property '$key' in chapter entry at index $c")
                        }
                    }

                    val rawChapterId = chapterObj.opt("chapterId")
                    val chapterId = if (rawChapterId is String) rawChapterId else ""
                    if (!chapterObj.has("chapterId") || !isValidBlockOrSectionId(chapterId)) {
                        errors.add("Invalid or empty chapterId at index $c: '$chapterId'")
                        continue
                    }
                    if (!seenChapterIds.add(chapterId)) {
                        errors.add("Duplicate chapterId in narration manifest: '$chapterId'")
                    }

                    val expectedSectionsForChapter: Set<String> =
                        validSectionIdsByChapter[chapterId]
                            ?: if (expectedChapterId == null || expectedChapterId == chapterId) validSectionIds else emptySet()

                    if (!chapterObj.has("sections") || chapterObj.opt("sections") !is org.json.JSONArray) {
                        errors.add("Missing or invalid 'sections' array in chapter '$chapterId'")
                        continue
                    }
                    val sectionsArr = chapterObj.getJSONArray("sections")
                    val seenSections = linkedSetOf<String>()
                    val seenBlocksInChapter = linkedSetOf<String>()

                    for (s in 0 until sectionsArr.length()) {
                        val secObj = sectionsArr.optJSONObject(s)
                        if (secObj == null) {
                            errors.add("Section entry at index $s in chapter '$chapterId' is not a JSON object")
                            continue
                        }

                        for (key in secObj.keySetList()) {
                            if (key !in ALLOWED_SECTION_KEYS) {
                                errors.add("Unsupported property '$key' in section at index $s of chapter '$chapterId'")
                            }
                        }

                        val rawSectionId = secObj.opt("sectionId")
                        val sectionId = if (rawSectionId is String) rawSectionId else ""
                        if (!secObj.has("sectionId") || !isValidBlockOrSectionId(sectionId)) {
                            errors.add("Invalid or empty sectionId '$sectionId' in chapter '$chapterId'")
                            continue
                        }
                        if (!seenSections.add(sectionId)) {
                            errors.add("Duplicate sectionId '$sectionId' in chapter '$chapterId'")
                        }
                        if (expectedSectionsForChapter.isNotEmpty() && sectionId !in expectedSectionsForChapter) {
                            errors.add("Section '$sectionId' in narration manifest does not belong to chapter '$chapterId'")
                        }

                        if (!secObj.has("blocks") || secObj.opt("blocks") !is org.json.JSONArray) {
                            errors.add("Missing or invalid 'blocks' array in section '$sectionId' of chapter '$chapterId'")
                            continue
                        }
                        val blocksArr = secObj.getJSONArray("blocks")
                        val seenBlocksInSection = linkedSetOf<String>()
                        val expectedBlocksForSection = validBlockIdsBySection[sectionId] ?: emptySet()

                        for (b in 0 until blocksArr.length()) {
                            val blockObj = blocksArr.optJSONObject(b)
                            if (blockObj == null) {
                                errors.add("Block entry at index $b in section '$sectionId' is not a JSON object")
                                continue
                            }

                            val rawBlockId = blockObj.opt("blockId")
                            val blockId = if (rawBlockId is String) rawBlockId else ""

                            for (key in blockObj.keySetList()) {
                                if (key !in ALLOWED_BLOCK_KEYS) {
                                    errors.add("Unsupported property '$key' in block '$blockId' of section '$sectionId' (chapter '$chapterId')")
                                }
                            }

                            if (!blockObj.has("blockId") || !isValidBlockOrSectionId(blockId)) {
                                errors.add("Invalid or empty blockId '$blockId' in section '$sectionId' of chapter '$chapterId'")
                            } else {
                                if (!seenBlocksInSection.add(blockId) || !seenBlocksInChapter.add(blockId)) {
                                    errors.add("Duplicate blockId '$blockId' in section '$sectionId' of chapter '$chapterId'")
                                }
                                if (expectedBlocksForSection.isNotEmpty() && blockId !in expectedBlocksForSection) {
                                    errors.add("Block '$blockId' in narration manifest does not exist in section '$sectionId' of chapter '$chapterId'")
                                }
                            }

                            val (durOk, _) = extractOptionalDurationMs(blockObj)
                            if (!durOk) {
                                errors.add("Invalid durationMs for block '$blockId' in section '$sectionId' of chapter '$chapterId'")
                            }

                            val rawPath = blockObj.opt("audioAssetPath")
                            val audioAssetPath = if (rawPath is String) rawPath else ""
                            if (!blockObj.has("audioAssetPath") || !isValidRelativeAssetPath(audioAssetPath)) {
                                errors.add("Invalid or missing audioAssetPath '$audioAssetPath' for block '$blockId' in section '$sectionId' of chapter '$chapterId'")
                            }

                            val rawStatus = blockObj.opt("status")
                            val status = if (rawStatus is String) rawStatus else ""
                            if (!blockObj.has("status") || !isValidStatus(status)) {
                                errors.add("Invalid or missing status '$status' for block '$blockId' in section '$sectionId' of chapter '$chapterId' (expected AVAILABLE or UNAVAILABLE)")
                            } else if (status == STATUS_AVAILABLE &&
                                isValidRelativeAssetPath(audioAssetPath) &&
                                assetExistsPredicate != null &&
                                !assetExistsPredicate(audioAssetPath)
                            ) {
                                errors.add("Audio asset '$audioAssetPath' for AVAILABLE block '$blockId' in section '$sectionId' of chapter '$chapterId' does not exist")
                            }

                            blockCount++
                        }
                    }
                    sectionIdsByChapter[chapterId] = seenSections
                }

                if (!expectedChapterId.isNullOrBlank() && seenChapterIds.isNotEmpty()) {
                    if (seenChapterIds.size == 1 && seenChapterIds.first() != expectedChapterId) {
                        errors.add("Manifest chapterId '${seenChapterIds.first()}' does not match expected chapterId '$expectedChapterId'")
                    } else if (expectedChapterId !in seenChapterIds) {
                        errors.add("Expected chapterId '$expectedChapterId' not found in narration manifest chapters: $seenChapterIds")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Malformed JSON in narration manifest: ${e.message}")
        }

        return NarrationManifestValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            chapterIds = seenChapterIds.toList(),
            sectionIdsByChapter = sectionIdsByChapter,
            validatedBlockCount = blockCount
        )
    }

    fun parseManifestJson(
        jsonString: String,
        expectedChapterId: String = ""
    ): NarrationManifest {
        return try {
            val root = JSONObject(jsonString)

            for (key in root.keySetList()) {
                if (key !in ALLOWED_ROOT_KEYS) {
                    return NarrationManifest(isValid = false)
                }
            }

            val rawSchemaVer = root.opt("schemaVersion")
            val schemaVersion = when (rawSchemaVer) {
                is Int -> rawSchemaVer
                is Long -> rawSchemaVer.toInt()
                else -> -1
            }
            if (!root.has("schemaVersion") || schemaVersion != 1) {
                return NarrationManifest(schemaVersion = schemaVersion, isValid = false)
            }

            if (!root.has("chapters") || root.opt("chapters") !is org.json.JSONArray) {
                return NarrationManifest(schemaVersion = schemaVersion, isValid = false)
            }

            val chaptersArr = root.getJSONArray("chapters")
            val declaredChapters = linkedSetOf<String>()
            val declaredSections = linkedSetOf<String>()
            val byComposite = linkedMapOf<String, NarrationBlockAssetEntry>()
            val byBlock = linkedMapOf<String, NarrationBlockAssetEntry>()
            var rejected = 0
            var primaryChapterId = ""
            var chapterStructureValid = true

            for (c in 0 until chaptersArr.length()) {
                val chapterObj = chaptersArr.optJSONObject(c)
                if (chapterObj == null) {
                    chapterStructureValid = false
                    continue
                }
                if (chapterObj.keySetList().any { it !in ALLOWED_CHAPTER_KEYS }) {
                    chapterStructureValid = false
                    continue
                }
                val rawChapterId = chapterObj.opt("chapterId") as? String ?: ""
                if (!isValidBlockOrSectionId(rawChapterId) || !declaredChapters.add(rawChapterId)) {
                    chapterStructureValid = false
                    rejected++
                    continue
                }
                if (primaryChapterId.isBlank() || rawChapterId == expectedChapterId) {
                    primaryChapterId = rawChapterId
                }
                val shouldLoadEntriesForChapter = expectedChapterId.isBlank() || rawChapterId == expectedChapterId

                val sectionsArr = chapterObj.opt("sections") as? org.json.JSONArray
                if (sectionsArr == null) {
                    chapterStructureValid = false
                    continue
                }
                val seenSectionsInChapter = linkedSetOf<String>()
                val seenBlocksInChapter = linkedSetOf<String>()
                for (s in 0 until sectionsArr.length()) {
                    val secObj = sectionsArr.optJSONObject(s)
                    if (secObj == null || secObj.keySetList().any { it !in ALLOWED_SECTION_KEYS }) {
                        rejected++
                        continue
                    }
                    val sectionId = secObj.opt("sectionId") as? String ?: ""
                    if (!isValidBlockOrSectionId(sectionId) || !seenSectionsInChapter.add(sectionId)) {
                        rejected++
                        continue
                    }
                    if (shouldLoadEntriesForChapter) {
                        declaredSections.add(sectionId)
                    }
                    val blocksArr = secObj.opt("blocks") as? org.json.JSONArray ?: continue
                    val seenBlocksInSection = linkedSetOf<String>()
                    for (b in 0 until blocksArr.length()) {
                        val blockObj = blocksArr.optJSONObject(b)
                        if (blockObj == null || blockObj.keySetList().any { it !in ALLOWED_BLOCK_KEYS }) {
                            rejected++
                            continue
                        }
                        val blockId = blockObj.opt("blockId") as? String ?: ""
                        val audioAssetPath = blockObj.opt("audioAssetPath") as? String ?: ""
                        val status = blockObj.opt("status") as? String ?: ""
                        val (durOk, durationMs) = extractOptionalDurationMs(blockObj)

                        if (!isValidBlockOrSectionId(blockId) ||
                            !seenBlocksInSection.add(blockId) ||
                            !seenBlocksInChapter.add(blockId) ||
                            !durOk ||
                            !isValidStatus(status) ||
                            !isValidRelativeAssetPath(audioAssetPath)
                        ) {
                            rejected++
                            continue
                        }
                        if (shouldLoadEntriesForChapter && status == STATUS_AVAILABLE) {
                            val entry = NarrationBlockAssetEntry(
                                sectionId = sectionId,
                                blockId = blockId,
                                assetRelativePath = audioAssetPath,
                                status = status,
                                durationMs = durationMs
                            )
                            byComposite[entry.compositeKey] = entry
                            byBlock[blockId] = entry
                        }
                    }
                }
            }

            if (expectedChapterId.isNotBlank() && declaredChapters.isNotEmpty() && expectedChapterId !in declaredChapters) {
                chapterStructureValid = false
            }

            val hasValidChapterId = primaryChapterId.isBlank() || isValidBlockOrSectionId(primaryChapterId)
            NarrationManifest(
                schemaVersion = schemaVersion,
                chapterId = primaryChapterId,
                chapterIds = declaredChapters,
                declaredSectionIds = declaredSections,
                entriesByCompositeKey = byComposite,
                entriesByBlockId = byBlock,
                rejectedEntryCount = rejected,
                isValid = hasValidChapterId && chapterStructureValid
            )
        } catch (_: Exception) {
            NarrationManifest(isValid = false)
        }
    }
}

data class NarrationQueueItem(
    val sectionIndex: Int,
    val sectionId: String,
    val blockId: String,
    val assetRelativePath: String?,
    val isAssetAvailable: Boolean
) {
    val compositeKey: String get() = "$sectionId:$blockId"
}

data class NarrationQueueBuildResult(
    val playableItems: List<NarrationQueueItem>,
    val totalEligibleBlocks: Int,
    val missingBlocksCount: Int
) {
    val hasAnyPlayable: Boolean get() = playableItems.isNotEmpty()
    val hasPartialMissing: Boolean get() = playableItems.isNotEmpty() && missingBlocksCount > 0
}

data class NarrationUiState(
    val status: NarrationStatus = NarrationStatus.IDLE,
    val mode: NarrationPlaybackMode = NarrationPlaybackMode.CURRENT_PAGE,
    val narrationEnabled: Boolean = true,
    val speed: NarrationPlaybackSpeed = NarrationPlaybackSpeed.SPEED_1_0X,
    val volumePercent: Int = 85,
    val activeSectionIndex: Int? = null,
    val activeSectionId: String? = null,
    val activeBlockId: String? = null,
    val activeAssetPath: String? = null,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val statusMessageFa: String? = null,
    val hasPartialMissingInQueue: Boolean = false,
    val skippedBlocksCount: Int = 0,
    val canPlayCurrentPage: Boolean = false,
    val canPlayChapter: Boolean = false,
    val canResumeSavedPosition: Boolean = false,
    val savedSectionId: String? = null,
    val savedBlockId: String? = null,
    val savedPositionMs: Int = 0
) {
    val isPlaying: Boolean get() = status == NarrationStatus.PLAYING
    val isPaused: Boolean get() = status == NarrationStatus.PAUSED
    val progressFraction: Float
        get() = if (durationMs > 0) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val remainingMs: Int
        get() = (durationMs - currentPositionMs).coerceAtLeast(0)
}

/**
 * Pluggable playback engine abstraction for long-form book narration.
 * Completely independent from [ChessSoundManager] and [android.media.SoundPool].
 */
interface NarrationPlaybackEngine {
    val isPlaying: Boolean
    val currentPositionMs: Int
    val durationMs: Int

    fun assetExists(assetRelativePath: String): Boolean

    fun prepareAndPlay(
        assetRelativePath: String,
        startPositionMs: Int = 0,
        speed: Float = 1.0f,
        volume: Float = 1.0f,
        onPrepared: (durationMs: Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (reason: String) -> Unit
    ): Boolean

    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Int)
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun release()
}

/**
 * Production Android [MediaPlayer]-backed narration engine with AudioFocus and lifecycle safety.
 */
class MediaPlayerNarrationEngine(context: Context) : NarrationPlaybackEngine {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var isPrepared = false
    private var currentSpeed = 1.0f
    private var currentVolume = 0.85f

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setVolume((currentVolume * 0.35f).coerceIn(0f, 1f))
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                setVolume(currentVolume)
            }
        }
    }

    override val isPlaying: Boolean
        get() = try {
            isPrepared && mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }

    override val currentPositionMs: Int
        get() = try {
            if (isPrepared) (mediaPlayer?.currentPosition ?: 0).coerceAtLeast(0) else 0
        } catch (_: Exception) {
            0
        }

    override val durationMs: Int
        get() = try {
            if (isPrepared) (mediaPlayer?.duration ?: 0).coerceAtLeast(0) else 0
        } catch (_: Exception) {
            0
        }

    override fun assetExists(assetRelativePath: String): Boolean {
        if (!NarrationManifestValidator.isValidRelativeAssetPath(assetRelativePath)) return false
        return try {
            appContext.assets.open("narration/$assetRelativePath").use { true }
        } catch (_: Exception) {
            false
        }
    }

    override fun prepareAndPlay(
        assetRelativePath: String,
        startPositionMs: Int,
        speed: Float,
        volume: Float,
        onPrepared: (durationMs: Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (reason: String) -> Unit
    ): Boolean {
        if (!NarrationManifestValidator.isValidRelativeAssetPath(assetRelativePath)) {
            onError("INVALID_ASSET_PATH")
            return false
        }
        stopInternal()
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        currentVolume = volume.coerceIn(0f, 1f)

        return try {
            appContext.assets.openFd("narration/$assetRelativePath").use { afd ->
                requestAudioFocus()
                val mp = MediaPlayer()
                mediaPlayer = mp
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mp.setOnErrorListener { _, _, _ ->
                    isPrepared = false
                    onError("MEDIA_PLAYER_ERROR")
                    true
                }
                mp.setOnCompletionListener {
                    onCompletion()
                }
                mp.prepare()
                isPrepared = true
                mp.setVolume(currentVolume, currentVolume)
                applyPlaybackSpeedInternal(mp, currentSpeed)
                if (startPositionMs > 0 && startPositionMs < mp.duration) {
                    mp.seekTo(startPositionMs)
                }
                mp.start()
                onPrepared(mp.duration.coerceAtLeast(0))
                true
            }
        } catch (_: Exception) {
            isPrepared = false
            stopInternal()
            onError("ASSET_NOT_FOUND_OR_UNREADABLE")
            false
        }
    }

    override fun pause() {
        try {
            if (isPrepared && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {
        }
    }

    override fun resume() {
        try {
            if (isPrepared && mediaPlayer?.isPlaying == false) {
                requestAudioFocus()
                applyPlaybackSpeedInternal(mediaPlayer, currentSpeed)
                mediaPlayer?.start()
            }
        } catch (_: Exception) {
        }
    }

    override fun stop() {
        stopInternal()
        abandonAudioFocus()
    }

    override fun seekTo(positionMs: Int) {
        try {
            if (isPrepared) {
                val target = positionMs.coerceIn(0, durationMs.coerceAtLeast(0))
                mediaPlayer?.seekTo(target)
            }
        } catch (_: Exception) {
        }
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
        if (isPrepared && mediaPlayer?.isPlaying == true) {
            applyPlaybackSpeedInternal(mediaPlayer, currentSpeed)
        }
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            if (isPrepared) {
                mediaPlayer?.setVolume(currentVolume, currentVolume)
            }
        } catch (_: Exception) {
        }
    }

    override fun release() {
        stopInternal()
        abandonAudioFocus()
    }

    private fun stopInternal() {
        isPrepared = false
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
            } catch (_: Exception) {
            }
            try {
                mp.reset()
            } catch (_: Exception) {
            }
            try {
                mp.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun applyPlaybackSpeedInternal(mp: MediaPlayer?, speed: Float) {
        if (mp == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = mp.playbackParams ?: PlaybackParams()
                params.speed = speed
                mp.playbackParams = params
            } catch (_: Exception) {
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Deterministic in-memory Fake Playback Engine for testing Narration queue ordering,
 * pagination deduplication, skip behavior, Play/Pause/Resume/Stop/Seek/Speed, and
 * lifecycle cleanup without generating or playing any synthetic audio.
 */
class FakeNarrationPlaybackEngine : NarrationPlaybackEngine {

    private val availableAssets = linkedMapOf<String, Int>()
    private var _isPlaying = false
    private var _currentPositionMs = 0
    private var _durationMs = 0
    private var _completionCallback: (() -> Unit)? = null

    val playedAssetHistory = mutableListOf<String>()
    var pauseCount = 0
        private set
    var resumeCount = 0
        private set
    var stopCount = 0
        private set
    var releaseCount = 0
        private set
    var currentSpeed = 1.0f
        private set
    var currentVolume = 0.85f
        private set
    var activeAssetPath: String? = null
        private set

    override val isPlaying: Boolean get() = _isPlaying
    override val currentPositionMs: Int get() = _currentPositionMs
    override val durationMs: Int get() = _durationMs

    fun registerFakeAsset(assetRelativePath: String, durationMs: Int = 12_000) {
        if (NarrationManifestValidator.isValidRelativeAssetPath(assetRelativePath)) {
            availableAssets[assetRelativePath] = durationMs.coerceAtLeast(100)
        }
    }

    fun clearFakeAssets() {
        availableAssets.clear()
    }

    override fun assetExists(assetRelativePath: String): Boolean {
        if (!NarrationManifestValidator.isValidRelativeAssetPath(assetRelativePath)) return false
        return availableAssets.containsKey(assetRelativePath)
    }

    override fun prepareAndPlay(
        assetRelativePath: String,
        startPositionMs: Int,
        speed: Float,
        volume: Float,
        onPrepared: (durationMs: Int) -> Unit,
        onCompletion: () -> Unit,
        onError: (reason: String) -> Unit
    ): Boolean {
        if (!NarrationManifestValidator.isValidRelativeAssetPath(assetRelativePath)) {
            _isPlaying = false
            onError("INVALID_ASSET_PATH")
            return false
        }
        val dur = availableAssets[assetRelativePath]
        if (dur == null) {
            _isPlaying = false
            onError("ASSET_MISSING")
            return false
        }
        activeAssetPath = assetRelativePath
        _durationMs = dur
        _currentPositionMs = startPositionMs.coerceIn(0, dur)
        currentSpeed = speed
        currentVolume = volume
        _isPlaying = true
        _completionCallback = onCompletion
        playedAssetHistory.add(assetRelativePath)
        onPrepared(dur)
        return true
    }

    override fun pause() {
        if (_isPlaying) {
            _isPlaying = false
            pauseCount++
        }
    }

    override fun resume() {
        if (!_isPlaying && activeAssetPath != null) {
            _isPlaying = true
            resumeCount++
        }
    }

    override fun stop() {
        _isPlaying = false
        _currentPositionMs = 0
        _durationMs = 0
        activeAssetPath = null
        _completionCallback = null
        stopCount++
    }

    override fun seekTo(positionMs: Int) {
        _currentPositionMs = positionMs.coerceIn(0, _durationMs.coerceAtLeast(0))
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
    }

    override fun setVolume(volume: Float) {
        currentVolume = volume
    }

    override fun release() {
        stop()
        releaseCount++
    }

    fun simulateCompletion() {
        val cb = _completionCallback
        _isPlaying = false
        _currentPositionMs = _durationMs
        cb?.invoke()
    }
}

/**
 * Independent Book Narration Manager (`BookNarrationManager`).
 *
 * Completely isolated from [ChessSoundManager]:
 * - Never touches [ChessSoundManager] or [android.media.SoundPool].
 * - Loads each chapter's own narration manifest via [switchChapterManifest] without any hardcoded chapter ID.
 * - Normalizes paginated block IDs via [ReaderPaginator.originalBlockId] so split paragraphs
 *   are never queued or read multiple times.
 * - Supports Mode A (Play Current Page) and Mode B (Continuous Chapter Playback across all sections of the active chapter).
 */
class BookNarrationManager(
    private val context: Context,
    private val prefs: UserPreferencesManager,
    engineOverride: NarrationPlaybackEngine? = null
) {
    companion object {
        const val MSG_PAGE_AUDIO_NOT_READY = "فایل صوتی این صفحه هنوز آماده نشده است"
        const val MSG_CHAPTER_AUDIO_NOT_READY = "فایل صوتی این فصل هنوز آماده نشده است"
        const val MSG_PARTIAL_BLOCKS_SKIPPED = "برخی بلوک‌های فاقد فایل صوتی رد شدند"
        const val MSG_CHAPTER_COMPLETED = "پخش صوتی این فصل به پایان رسید"
    }

    var activeBookId: String = ""
        private set

    var activeChapterId: String = ""
        private set

    var activeManifestAssetPath: String = ""
        private set

    var engine: NarrationPlaybackEngine = engineOverride ?: MediaPlayerNarrationEngine(context)
        private set

    var manifest: NarrationManifest = NarrationManifest(isValid = false)
        private set

    private var activeQueue: List<NarrationQueueItem> = emptyList()
    private var activeQueueIndex: Int = -1
    private var cachedSections: List<SectionItem> = emptyList()
    private var onAutoNavigateToBlock: ((sectionIndex: Int, blockId: String) -> Unit)? = null
    private var onStateChanged: ((NarrationUiState) -> Unit)? = null

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<NarrationUiState> = _state.asStateFlow()

    private inline fun updateState(transform: (NarrationUiState) -> NarrationUiState) {
        _state.update(transform)
        onStateChanged?.invoke(_state.value)
    }

    fun setOnStateChangedListener(listener: ((NarrationUiState) -> Unit)?) {
        onStateChanged = listener
        listener?.invoke(_state.value)
    }

    private fun createInitialState(): NarrationUiState {
        return NarrationUiState(
            status = NarrationStatus.IDLE,
            mode = NarrationPlaybackMode.CURRENT_PAGE,
            narrationEnabled = prefs.narrationEnabled,
            speed = prefs.narrationSpeed,
            volumePercent = prefs.narrationVolumePercent,
            savedSectionId = null,
            savedBlockId = null,
            savedPositionMs = 0,
            canResumeSavedPosition = false
        )
    }

    fun loadManifestFromAssets(
        manifestAssetPath: String = activeManifestAssetPath,
        assetReaderOverride: ((String) -> String)? = null,
        expectedChapterId: String = activeChapterId
    ): NarrationManifest {
        if (manifestAssetPath.isBlank()) {
            return NarrationManifest(chapterId = expectedChapterId, isValid = false)
        }
        return try {
            val json = if (assetReaderOverride != null) {
                assetReaderOverride(manifestAssetPath)
            } else {
                context.assets.open(manifestAssetPath).bufferedReader().use { it.readText() }
            }
            NarrationManifestValidator.parseManifestJson(json, expectedChapterId = expectedChapterId)
        } catch (_: Exception) {
            NarrationManifest(chapterId = expectedChapterId, isValid = false)
        }
    }

    /**
     * Switches the narration manager to a new chapter, stopping any active playback,
     * releasing previous resources, and loading the chapter's own narration manifest.
     * Never falls back to another chapter's manifest if the target manifest is missing or invalid.
     */
    fun switchChapterManifest(
        chapterId: String,
        manifestAssetPath: String,
        sections: List<SectionItem>,
        currentPage: ReaderPage?,
        bookId: String = activeBookId,
        assetReaderOverride: ((String) -> String)? = null
    ) {
        stop()
        engine.release()
        activeBookId = bookId
        activeChapterId = chapterId
        activeManifestAssetPath = manifestAssetPath
        manifest = if (manifestAssetPath.isBlank()) {
            NarrationManifest(chapterId = chapterId, isValid = false)
        } else {
            loadManifestFromAssets(
                manifestAssetPath = manifestAssetPath,
                assetReaderOverride = assetReaderOverride,
                expectedChapterId = chapterId
            )
        }
        cachedSections = sections
        refreshAvailability(sections, currentPage)
    }

    /**
     * Allows replacing the manifest or playback engine in unit/Robolectric tests.
     */
    fun overrideForTesting(
        customManifest: NarrationManifest,
        customEngine: NarrationPlaybackEngine,
        sections: List<SectionItem> = cachedSections,
        currentPage: ReaderPage? = null,
        chapterId: String = customManifest.chapterId.ifBlank { activeChapterId },
        bookId: String = activeBookId
    ) {
        engine.release()
        activeBookId = bookId
        activeChapterId = chapterId
        manifest = customManifest
        engine = customEngine
        cachedSections = sections
        refreshAvailability(sections, currentPage)
    }

    fun setAutoNavigationCallback(callback: ((sectionIndex: Int, blockId: String) -> Unit)?) {
        onAutoNavigateToBlock = callback
    }

    /**
     * Extracts the ordered, deduplicated list of original block IDs from [blocks].
     * - Strips pagination suffixes (`__part_2`, etc.) via [ReaderPaginator.originalBlockId].
     * - Excludes [SectionContentBlock.ExternalReferenceBlock] unless `isAuthorizedAndEnabled == true`.
     */
    fun extractOrderedOriginalBlockIds(blocks: List<SectionContentBlock>): List<String> {
        val seen = linkedSetOf<String>()
        for (block in blocks) {
            when (block) {
                is SectionContentBlock.ExternalReferenceBlock -> {
                    if (block.isAuthorizedAndEnabled) {
                        val origId = ReaderPaginator.originalBlockId(block.blockId)
                        if (NarrationManifestValidator.isValidBlockOrSectionId(origId)) {
                            seen.add(origId)
                        }
                    }
                }
                is SectionContentBlock.ParagraphBlock,
                is SectionContentBlock.HeadingBlock,
                is SectionContentBlock.DiagramBlock,
                is SectionContentBlock.ExerciseBlock,
                is SectionContentBlock.FootnoteBlock,
                is SectionContentBlock.SourceImageBlock,
                is SectionContentBlock.OrderedListBlock,
                is SectionContentBlock.TableBlock -> {
                    val origId = ReaderPaginator.originalBlockId(block.blockId)
                    if (NarrationManifestValidator.isValidBlockOrSectionId(origId)) {
                        seen.add(origId)
                    }
                }
            }
        }
        return seen.toList()
    }

    /**
     * Builds the deduplicated playback queue for a single [ReaderPage] (Mode A).
     */
    fun buildPageQueue(
        sectionIndex: Int,
        section: SectionItem,
        page: ReaderPage
    ): NarrationQueueBuildResult {
        val origIds = extractOrderedOriginalBlockIds(page.orderedBlocks)
        val playable = mutableListOf<NarrationQueueItem>()
        var missingCount = 0

        for (blockId in origIds) {
            val assetPath = if (manifest.isValid) {
                manifest.resolveAssetPath(section.sectionId, blockId)
            } else {
                null
            }
            if (assetPath != null && engine.assetExists(assetPath)) {
                playable.add(
                    NarrationQueueItem(
                        sectionIndex = sectionIndex,
                        sectionId = section.sectionId,
                        blockId = blockId,
                        assetRelativePath = assetPath,
                        isAssetAvailable = true
                    )
                )
            } else {
                missingCount++
            }
        }

        return NarrationQueueBuildResult(
            playableItems = playable,
            totalEligibleBlocks = origIds.size,
            missingBlocksCount = missingCount
        )
    }

    /**
     * Builds the canonical chapter-wide playback queue across all [SectionItem]s of the active chapter (Mode B),
     * using each section's unsplit `orderedContentBlocks` so paginated paragraphs are never repeated.
     */
    fun buildContinuousChapterQueue(
        sections: List<SectionItem>,
        startSectionIndex: Int = 0,
        startBlockId: String? = null
    ): NarrationQueueBuildResult {
        if (sections.isEmpty()) {
            return NarrationQueueBuildResult(emptyList(), 0, 0)
        }
        val safeStartSec = startSectionIndex.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
        val normalizedStartBlock = startBlockId?.let { ReaderPaginator.originalBlockId(it) }
        val playable = mutableListOf<NarrationQueueItem>()
        var totalEligible = 0
        var missingCount = 0
        var reachedStartBlock = normalizedStartBlock == null

        for (secIdx in safeStartSec until sections.size) {
            val section = sections[secIdx]
            val origIds = extractOrderedOriginalBlockIds(section.translationSlot.orderedContentBlocks)
            for (blockId in origIds) {
                if (!reachedStartBlock) {
                    if (secIdx == safeStartSec && blockId == normalizedStartBlock) {
                        reachedStartBlock = true
                    } else {
                        continue
                    }
                }
                totalEligible++
                val assetPath = if (manifest.isValid) {
                    manifest.resolveAssetPath(section.sectionId, blockId)
                } else {
                    null
                }
                if (assetPath != null && engine.assetExists(assetPath)) {
                    playable.add(
                        NarrationQueueItem(
                            sectionIndex = secIdx,
                            sectionId = section.sectionId,
                            blockId = blockId,
                            assetRelativePath = assetPath,
                            isAssetAvailable = true
                        )
                    )
                } else {
                    missingCount++
                }
            }
            // If startBlockId was not found in safeStartSec, start from the beginning of the next section
            reachedStartBlock = true
        }

        return NarrationQueueBuildResult(
            playableItems = playable,
            totalEligibleBlocks = totalEligible,
            missingBlocksCount = missingCount
        )
    }

    fun refreshAvailability(sections: List<SectionItem>, currentPage: ReaderPage?) {
        cachedSections = sections
        val currentSecIdx = sections.indexOfFirst { it.sectionId == currentPage?.sectionId }.coerceAtLeast(0)
        val currentSec = sections.getOrNull(currentSecIdx)
        val pageQueue = if (currentSec != null && currentPage != null) {
            buildPageQueue(currentSecIdx, currentSec, currentPage)
        } else {
            NarrationQueueBuildResult(emptyList(), 0, 0)
        }
        val chapterQueue = buildContinuousChapterQueue(sections, startSectionIndex = 0)

        val savedResume = prefs.getSavedNarrationResumeState(
            chapterId = activeChapterId,
            bookId = activeBookId
        )
        val savedSecId = savedResume.sectionId
        val savedBlkId = savedResume.blockId
        val savedAsset = if (manifest.isValid && savedSecId != null && savedBlkId != null) {
            manifest.resolveAssetPath(savedSecId, savedBlkId)
        } else {
            null
        }
        val canResumeSaved = savedAsset != null && engine.assetExists(savedAsset)

        updateState { st ->
            st.copy(
                canPlayCurrentPage = st.narrationEnabled && pageQueue.hasAnyPlayable,
                canPlayChapter = st.narrationEnabled && chapterQueue.hasAnyPlayable,
                canResumeSavedPosition = st.narrationEnabled && canResumeSaved,
                savedSectionId = savedSecId,
                savedBlockId = savedBlkId,
                savedPositionMs = savedResume.positionMs
            )
        }
    }

    /**
     * Mode A: Starts playing the audio blocks of the current [page] in order.
     */
    fun playCurrentPage(
        sections: List<SectionItem>,
        sectionIndex: Int,
        page: ReaderPage
    ): Boolean {
        cachedSections = sections
        if (!_state.value.narrationEnabled) return false
        val section = sections.getOrNull(sectionIndex) ?: return false
        val result = buildPageQueue(sectionIndex, section, page)
        if (!result.hasAnyPlayable) {
            engine.stop()
            activeQueue = emptyList()
            activeQueueIndex = -1
            updateState {
                it.copy(
                    status = NarrationStatus.UNAVAILABLE,
                    mode = NarrationPlaybackMode.CURRENT_PAGE,
                    activeSectionIndex = null,
                    activeSectionId = null,
                    activeBlockId = null,
                    activeAssetPath = null,
                    currentPositionMs = 0,
                    durationMs = 0,
                    statusMessageFa = MSG_PAGE_AUDIO_NOT_READY,
                    hasPartialMissingInQueue = false,
                    skippedBlocksCount = result.missingBlocksCount,
                    canPlayCurrentPage = false
                )
            }
            return false
        }

        activeQueue = result.playableItems
        activeQueueIndex = 0
        val partialMsg = if (result.hasPartialMissing) MSG_PARTIAL_BLOCKS_SKIPPED else null
        return startQueueItemAt(
            index = 0,
            mode = NarrationPlaybackMode.CURRENT_PAGE,
            startPositionMs = 0,
            hasPartialMissing = result.hasPartialMissing,
            skippedCount = result.missingBlocksCount,
            initialStatusMessage = partialMsg,
            triggerAutoNavigation = false
        )
    }

    /**
     * Mode B: Starts continuous chapter playback across all sections of the active chapter.
     */
    fun playContinuousChapter(
        sections: List<SectionItem>,
        startSectionIndex: Int = 0,
        startBlockId: String? = null,
        startPositionMs: Int = 0
    ): Boolean {
        cachedSections = sections
        if (!_state.value.narrationEnabled) return false
        val result = buildContinuousChapterQueue(
            sections = sections,
            startSectionIndex = startSectionIndex,
            startBlockId = startBlockId
        )
        if (!result.hasAnyPlayable) {
            engine.stop()
            activeQueue = emptyList()
            activeQueueIndex = -1
            updateState {
                it.copy(
                    status = NarrationStatus.UNAVAILABLE,
                    mode = NarrationPlaybackMode.CONTINUOUS_CHAPTER,
                    activeSectionIndex = null,
                    activeSectionId = null,
                    activeBlockId = null,
                    activeAssetPath = null,
                    currentPositionMs = 0,
                    durationMs = 0,
                    statusMessageFa = MSG_CHAPTER_AUDIO_NOT_READY,
                    hasPartialMissingInQueue = false,
                    skippedBlocksCount = result.missingBlocksCount,
                    canPlayChapter = false
                )
            }
            return false
        }

        activeQueue = result.playableItems
        activeQueueIndex = 0
        val partialMsg = if (result.hasPartialMissing) MSG_PARTIAL_BLOCKS_SKIPPED else null
        return startQueueItemAt(
            index = 0,
            mode = NarrationPlaybackMode.CONTINUOUS_CHAPTER,
            startPositionMs = startPositionMs,
            hasPartialMissing = result.hasPartialMissing,
            skippedCount = result.missingBlocksCount,
            initialStatusMessage = partialMsg,
            triggerAutoNavigation = true
        )
    }

    private fun startQueueItemAt(
        index: Int,
        mode: NarrationPlaybackMode,
        startPositionMs: Int,
        hasPartialMissing: Boolean,
        skippedCount: Int,
        initialStatusMessage: String?,
        triggerAutoNavigation: Boolean
    ): Boolean {
        val item = activeQueue.getOrNull(index) ?: return false
        val assetPath = item.assetRelativePath ?: return false
        activeQueueIndex = index

        if (triggerAutoNavigation && mode == NarrationPlaybackMode.CONTINUOUS_CHAPTER) {
            onAutoNavigateToBlock?.invoke(item.sectionIndex, item.blockId)
        }

        val currentSt = _state.value
        val volume = (currentSt.volumePercent.coerceIn(0, 100) / 100f)
        val started = engine.prepareAndPlay(
            assetRelativePath = assetPath,
            startPositionMs = startPositionMs,
            speed = currentSt.speed.multiplier,
            volume = volume,
            onPrepared = { durMs ->
                prefs.saveNarrationResumeState(
                    sectionId = item.sectionId,
                    blockId = item.blockId,
                    positionMs = startPositionMs.coerceIn(0, durMs),
                    chapterId = activeChapterId,
                    bookId = activeBookId
                )
                updateState {
                    it.copy(
                        status = NarrationStatus.PLAYING,
                        mode = mode,
                        activeSectionIndex = item.sectionIndex,
                        activeSectionId = item.sectionId,
                        activeBlockId = item.blockId,
                        activeAssetPath = assetPath,
                        currentPositionMs = startPositionMs.coerceIn(0, durMs),
                        durationMs = durMs,
                        statusMessageFa = initialStatusMessage,
                        hasPartialMissingInQueue = hasPartialMissing,
                        skippedBlocksCount = skippedCount,
                        savedSectionId = item.sectionId,
                        savedBlockId = item.blockId,
                        savedPositionMs = startPositionMs.coerceIn(0, durMs),
                        canResumeSavedPosition = true
                    )
                }
            },
            onCompletion = {
                advanceToNextQueueItem()
            },
            onError = {
                advanceToNextQueueItem()
            }
        )
        if (!started) {
            updateState {
                it.copy(
                    status = NarrationStatus.UNAVAILABLE,
                    statusMessageFa = MSG_PAGE_AUDIO_NOT_READY
                )
            }
        }
        return started
    }

    private fun advanceToNextQueueItem() {
        val nextIndex = activeQueueIndex + 1
        val currentSt = _state.value
        if (nextIndex in activeQueue.indices) {
            startQueueItemAt(
                index = nextIndex,
                mode = currentSt.mode,
                startPositionMs = 0,
                hasPartialMissing = currentSt.hasPartialMissingInQueue,
                skippedCount = currentSt.skippedBlocksCount,
                initialStatusMessage = if (currentSt.hasPartialMissingInQueue) MSG_PARTIAL_BLOCKS_SKIPPED else null,
                triggerAutoNavigation = currentSt.mode == NarrationPlaybackMode.CONTINUOUS_CHAPTER
            )
        } else {
            val completedMsg = if (currentSt.mode == NarrationPlaybackMode.CONTINUOUS_CHAPTER) {
                MSG_CHAPTER_COMPLETED
            } else {
                null
            }
            engine.stop()
            activeQueueIndex = -1
            updateState {
                it.copy(
                    status = NarrationStatus.COMPLETED,
                    activeSectionIndex = null,
                    activeSectionId = null,
                    activeBlockId = null,
                    activeAssetPath = null,
                    currentPositionMs = 0,
                    durationMs = 0,
                    statusMessageFa = completedMsg
                )
            }
        }
    }

    fun pause() {
        val st = _state.value
        if (st.status != NarrationStatus.PLAYING) return
        val pos = engine.currentPositionMs
        engine.pause()
        val secId = st.activeSectionId
        val blkId = st.activeBlockId
        if (secId != null && blkId != null) {
            prefs.saveNarrationResumeState(
                sectionId = secId,
                blockId = blkId,
                positionMs = pos,
                chapterId = activeChapterId,
                bookId = activeBookId
            )
        }
        updateState {
            it.copy(
                status = NarrationStatus.PAUSED,
                currentPositionMs = pos,
                savedSectionId = secId ?: it.savedSectionId,
                savedBlockId = blkId ?: it.savedBlockId,
                savedPositionMs = pos,
                canResumeSavedPosition = true
            )
        }
    }

    fun resume(sections: List<SectionItem> = cachedSections): Boolean {
        cachedSections = sections
        val st = _state.value
        if (!st.narrationEnabled) return false

        if (st.status == NarrationStatus.PAUSED && activeQueueIndex in activeQueue.indices) {
            engine.resume()
            updateState {
                it.copy(
                    status = NarrationStatus.PLAYING,
                    currentPositionMs = engine.currentPositionMs
                )
            }
            return true
        }

        // Resume from persisted saved state in UserPreferencesManager for the active book & chapter
        val savedResume = prefs.getSavedNarrationResumeState(
            chapterId = activeChapterId,
            bookId = activeBookId
        )
        val savedSecId = savedResume.sectionId ?: return false
        val savedBlkId = savedResume.blockId ?: return false
        val savedPos = savedResume.positionMs.coerceAtLeast(0)
        if (!manifest.isValid) return false
        val assetPath = manifest.resolveAssetPath(savedSecId, savedBlkId) ?: return false
        if (!engine.assetExists(assetPath)) return false

        val secIdx = sections.indexOfFirst { it.sectionId == savedSecId }.coerceAtLeast(0)
        return playContinuousChapter(
            sections = sections,
            startSectionIndex = secIdx,
            startBlockId = savedBlkId,
            startPositionMs = savedPos
        )
    }

    fun stop() {
        val st = _state.value
        val pos = engine.currentPositionMs
        val secId = st.activeSectionId
        val blkId = st.activeBlockId
        if (secId != null && blkId != null) {
            prefs.saveNarrationResumeState(
                sectionId = secId,
                blockId = blkId,
                positionMs = pos,
                chapterId = activeChapterId,
                bookId = activeBookId
            )
        }
        engine.stop()
        activeQueue = emptyList()
        activeQueueIndex = -1
        updateState {
            it.copy(
                status = NarrationStatus.STOPPED,
                activeSectionIndex = null,
                activeSectionId = null,
                activeBlockId = null,
                activeAssetPath = null,
                currentPositionMs = 0,
                durationMs = 0,
                statusMessageFa = null,
                savedSectionId = secId ?: it.savedSectionId,
                savedBlockId = blkId ?: it.savedBlockId,
                savedPositionMs = pos
            )
        }
    }

    fun seekTo(positionMs: Int) {
        val st = _state.value
        if (st.status != NarrationStatus.PLAYING && st.status != NarrationStatus.PAUSED) return
        val clamped = positionMs.coerceIn(0, st.durationMs.coerceAtLeast(0))
        engine.seekTo(clamped)
        val secId = st.activeSectionId
        val blkId = st.activeBlockId
        if (secId != null && blkId != null) {
            prefs.saveNarrationResumeState(
                sectionId = secId,
                blockId = blkId,
                positionMs = clamped,
                chapterId = activeChapterId,
                bookId = activeBookId
            )
        }
        updateState {
            it.copy(
                currentPositionMs = clamped,
                savedPositionMs = clamped
            )
        }
    }

    fun setSpeed(speed: NarrationPlaybackSpeed) {
        prefs.narrationSpeed = speed
        engine.setSpeed(speed.multiplier)
        updateState { it.copy(speed = speed) }
    }

    fun cycleSpeed(): NarrationPlaybackSpeed {
        val next = _state.value.speed.nextSpeed()
        setSpeed(next)
        return next
    }

    fun setVolumePercent(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        prefs.narrationVolumePercent = clamped
        engine.setVolume(clamped / 100f)
        updateState { it.copy(volumePercent = clamped) }
    }

    fun setNarrationEnabled(enabled: Boolean) {
        prefs.narrationEnabled = enabled
        if (!enabled && (_state.value.isPlaying || _state.value.isPaused)) {
            stop()
        }
        updateState { it.copy(narrationEnabled = enabled) }
    }

    /**
     * Called when the user manually navigates to a different page or section:
     * - In `CURRENT_PAGE` mode: stops playback and saves the last playback position.
     * - In `CONTINUOUS_CHAPTER` mode: pauses playback so the visible text and audio never desynchronize.
     */
    fun onManualPageOrSectionChanged(sections: List<SectionItem>, newPage: ReaderPage?) {
        val st = _state.value
        if (st.status == NarrationStatus.PLAYING) {
            if (st.mode == NarrationPlaybackMode.CURRENT_PAGE) {
                stop()
            } else {
                pause()
            }
        } else if (st.status == NarrationStatus.UNAVAILABLE || st.status == NarrationStatus.COMPLETED) {
            updateState { it.copy(status = NarrationStatus.IDLE, statusMessageFa = null) }
        }
        refreshAvailability(sections, newPage)
    }

    fun release() {
        val st = _state.value
        if (st.status == NarrationStatus.PLAYING || st.status == NarrationStatus.PAUSED) {
            val pos = engine.currentPositionMs
            val secId = st.activeSectionId
            val blkId = st.activeBlockId
            if (secId != null && blkId != null) {
                prefs.saveNarrationResumeState(
                    sectionId = secId,
                    blockId = blkId,
                    positionMs = pos,
                    chapterId = activeChapterId,
                    bookId = activeBookId
                )
            }
        }
        engine.release()
        activeQueue = emptyList()
        activeQueueIndex = -1
        updateState {
            it.copy(
                status = NarrationStatus.IDLE,
                activeSectionIndex = null,
                activeSectionId = null,
                activeBlockId = null,
                activeAssetPath = null,
                currentPositionMs = 0,
                durationMs = 0
            )
        }
    }
}
