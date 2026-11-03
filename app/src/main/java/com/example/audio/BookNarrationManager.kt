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
    CONTINUOUS_CHAPTER("پخش پیوسته فصل اول")
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
    val assetRelativePath: String
) {
    val compositeKey: String get() = "$sectionId:$blockId"
}

data class NarrationManifest(
    val schemaVersion: Int = 1,
    val chapterId: String = "CH01",
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

object NarrationManifestValidator {

    private val VALID_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{1,128}$")
    private val DRIVE_LETTER_REGEX = Regex("^[A-Za-z]:.*")
    private val ALLOWED_EXTENSIONS = listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac")

    /**
     * Validates a sectionId or blockId:
     * - Non-blank, alphanumeric + underscore/hyphen
     * - Never allows pagination slice suffix (`__part_`) as a manifest key
     */
    fun isValidBlockOrSectionId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        if (id.contains("__part_")) return false
        return VALID_ID_REGEX.matches(id)
    }

    /**
     * Strictly validates that an asset path inside `manifest.json` is a safe relative path
     * under `assets/narration/`:
     * - No absolute paths (starting with `/` or `\` or drive letter)
     * - No URLs (`http://`, `https://`, `file://`, `content://`, or any `://`)
     * - No path traversal (`..` or `.` segments)
     * - Must end with a supported audio extension
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
        return true
    }

    fun parseManifestJson(jsonString: String): NarrationManifest {
        return try {
            val root = JSONObject(jsonString)
            val schemaVersion = root.optInt("schemaVersion", 1)
            if (schemaVersion < 1) {
                return NarrationManifest(schemaVersion = schemaVersion, isValid = false)
            }
            val chaptersArr = root.optJSONArray("chapters")
                ?: return NarrationManifest(schemaVersion = schemaVersion, isValid = true)

            val declaredSections = linkedSetOf<String>()
            val byComposite = linkedMapOf<String, NarrationBlockAssetEntry>()
            val byBlock = linkedMapOf<String, NarrationBlockAssetEntry>()
            var rejected = 0
            var primaryChapterId = "CH01"

            for (c in 0 until chaptersArr.length()) {
                val chapterObj = chaptersArr.optJSONObject(c) ?: continue
                val chapterId = chapterObj.optString("chapterId", "CH01")
                if (c == 0 && isValidBlockOrSectionId(chapterId)) {
                    primaryChapterId = chapterId
                }
                val sectionsArr = chapterObj.optJSONArray("sections") ?: continue
                for (s in 0 until sectionsArr.length()) {
                    val secObj = sectionsArr.optJSONObject(s) ?: continue
                    val sectionId = secObj.optString("sectionId", "")
                    if (!isValidBlockOrSectionId(sectionId)) {
                        rejected++
                        continue
                    }
                    declaredSections.add(sectionId)
                    val blocksArr = secObj.optJSONArray("blocks") ?: continue
                    for (b in 0 until blocksArr.length()) {
                        val blockObj = blocksArr.optJSONObject(b) ?: continue
                        val blockId = blockObj.optString("blockId", "")
                        val asset = blockObj.optString("asset", "")
                        if (!isValidBlockOrSectionId(blockId) || !isValidRelativeAssetPath(asset)) {
                            rejected++
                            continue
                        }
                        val entry = NarrationBlockAssetEntry(
                            sectionId = sectionId,
                            blockId = blockId,
                            assetRelativePath = asset
                        )
                        byComposite[entry.compositeKey] = entry
                        byBlock[blockId] = entry
                    }
                }
            }

            NarrationManifest(
                schemaVersion = schemaVersion,
                chapterId = primaryChapterId,
                declaredSectionIds = declaredSections,
                entriesByCompositeKey = byComposite,
                entriesByBlockId = byBlock,
                rejectedEntryCount = rejected,
                isValid = true
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
 * Completely independent from [ChessSoundManager] and [ android.media.SoundPool ].
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
 * - Loads `assets/narration/manifest.json` and validates all block IDs and asset paths.
 * - Normalizes paginated block IDs via [ReaderPaginator.originalBlockId] so split paragraphs
 *   are never queued or read multiple times.
 * - Supports Mode A (Play Current Page) and Mode B (Continuous Chapter 1 Playback).
 */
class BookNarrationManager(
    private val context: Context,
    private val prefs: UserPreferencesManager,
    engineOverride: NarrationPlaybackEngine? = null
) {
    companion object {
        const val MANIFEST_ASSET_PATH = "narration/manifest.json"
        const val MSG_PAGE_AUDIO_NOT_READY = "فایل صوتی این صفحه هنوز آماده نشده است"
        const val MSG_CHAPTER_AUDIO_NOT_READY = "فایل صوتی فصل اول هنوز آماده نشده است"
        const val MSG_PARTIAL_BLOCKS_SKIPPED = "برخی بلوک‌های فاقد فایل صوتی رد شدند"
        const val MSG_CHAPTER_COMPLETED = "پخش صوتی فصل اول به پایان رسید"
    }

    var engine: NarrationPlaybackEngine = engineOverride ?: MediaPlayerNarrationEngine(context)
        private set

    var manifest: NarrationManifest = loadManifestFromAssets()
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
        val savedSectionId = prefs.narrationLastSectionId
        val savedBlockId = prefs.narrationLastBlockId
        val savedPosMs = prefs.narrationLastPositionMs
        return NarrationUiState(
            status = NarrationStatus.IDLE,
            mode = NarrationPlaybackMode.CURRENT_PAGE,
            narrationEnabled = prefs.narrationEnabled,
            speed = prefs.narrationSpeed,
            volumePercent = prefs.narrationVolumePercent,
            savedSectionId = savedSectionId,
            savedBlockId = savedBlockId,
            savedPositionMs = savedPosMs,
            canResumeSavedPosition = false
        )
    }

    fun loadManifestFromAssets(): NarrationManifest {
        return try {
            val json = context.assets.open(MANIFEST_ASSET_PATH).bufferedReader().use { it.readText() }
            NarrationManifestValidator.parseManifestJson(json)
        } catch (_: Exception) {
            NarrationManifest(isValid = false)
        }
    }

    /**
     * Allows replacing the manifest or playback engine in unit/Robolectric tests.
     */
    fun overrideForTesting(
        customManifest: NarrationManifest,
        customEngine: NarrationPlaybackEngine,
        sections: List<SectionItem> = cachedSections,
        currentPage: ReaderPage? = null
    ) {
        engine.release()
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
                is SectionContentBlock.FootnoteBlock -> {
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
            val assetPath = manifest.resolveAssetPath(section.sectionId, blockId)
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
     * Builds the canonical chapter-wide playback queue across all 6 [SectionItem]s (Mode B),
     * using each section's unsplit `orderedContentBlocks` so paginated paragraphs are never repeated.
     */
    fun buildContinuousChapterQueue(
        sections: List<SectionItem>,
        startSectionIndex: Int = 0,
        startBlockId: String? = null
    ): NarrationQueueBuildResult {
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
                val assetPath = manifest.resolveAssetPath(section.sectionId, blockId)
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

        val savedSecId = prefs.narrationLastSectionId
        val savedBlkId = prefs.narrationLastBlockId
        val savedAsset = if (savedSecId != null && savedBlkId != null) {
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
                savedPositionMs = prefs.narrationLastPositionMs
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
     * Mode B: Starts continuous chapter playback across all 6 sections of Chapter 1.
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
                    positionMs = startPositionMs.coerceIn(0, durMs)
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
            prefs.saveNarrationResumeState(secId, blkId, pos)
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

        // Resume from persisted saved state in UserPreferencesManager if valid asset exists
        val savedSecId = prefs.narrationLastSectionId ?: return false
        val savedBlkId = prefs.narrationLastBlockId ?: return false
        val savedPos = prefs.narrationLastPositionMs.coerceAtLeast(0)
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
            prefs.saveNarrationResumeState(secId, blkId, pos)
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
            prefs.saveNarrationResumeState(secId, blkId, clamped)
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
                prefs.saveNarrationResumeState(secId, blkId, pos)
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
