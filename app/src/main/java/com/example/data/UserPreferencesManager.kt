package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.TranslationSlot

enum class FontSizeScale(val scale: Float, val labelFa: String) {
    SMALL(0.85f, "کوچک"),
    NORMAL(1.0f, "استاندارد"),
    LARGE(1.25f, "بزرگ")
}

enum class EducationalPlaybackSpeed(
    val moveDurationMs: Int,
    val holdDurationMs: Int,
    val fadeDurationMs: Int,
    val labelFa: String
) {
    SLOW(moveDurationMs = 600, holdDurationMs = 900, fadeDurationMs = 340, labelFa = "آرام"),
    NORMAL(moveDurationMs = 450, holdDurationMs = 800, fadeDurationMs = 320, labelFa = "معمولی"),
    FAST(moveDurationMs = 300, holdDurationMs = 700, fadeDurationMs = 300, labelFa = "سریع")
}

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("chess_chapter_1_prefs", Context.MODE_PRIVATE)

    init {
        migrateLegacyPreferencesIfNeeded()
    }

    private fun migrateLegacyPreferencesIfNeeded() {
        val migratedV18 = prefs.getBoolean(KEY_MIGRATED_V18_CONTENT_PACK, false)
        if (!migratedV18) {
            val editor = prefs.edit()

            // 1. Migrate legacy BD-032 exercise solved key -> EX_CH01_BD032
            if (prefs.contains(KEY_EXERCISE_BD032) && !prefs.contains(exerciseSolvedKey(DEFAULT_CH01_EXERCISE_ID))) {
                val solved = prefs.getBoolean(KEY_EXERCISE_BD032, false)
                editor.putBoolean(exerciseSolvedKey(DEFAULT_CH01_EXERCISE_ID), solved)
            }

            // 2. Migrate legacy sectionIndex/pageIndex -> stable CH01 sectionId & pageIndex
            if (prefs.contains(KEY_LAST_READ_SECTION) && !prefs.contains(chapterSectionIdKey(ChapterRepository.DEFAULT_CHAPTER_ID))) {
                val legacyIndex = prefs.getInt(KEY_LAST_READ_SECTION, 0)
                val mappedSectionId = TranslationSlot.REQUIRED_SECTION_SLOT_IDS.getOrElse(legacyIndex) {
                    TranslationSlot.REQUIRED_SECTION_SLOT_IDS.first()
                }
                val legacyPageIndex = prefs.getInt(KEY_LAST_READ_PAGE, 0).coerceAtLeast(0)
                editor.putString(KEY_ACTIVE_BOOK_ID, ChapterRepository.DEFAULT_BOOK_ID)
                editor.putString(KEY_ACTIVE_CHAPTER_ID, ChapterRepository.DEFAULT_CHAPTER_ID)
                editor.putString(KEY_LAST_READ_SECTION_ID, mappedSectionId)
                editor.putString(chapterSectionIdKey(ChapterRepository.DEFAULT_CHAPTER_ID), mappedSectionId)
                editor.putInt(chapterSectionIndexKey(ChapterRepository.DEFAULT_CHAPTER_ID), legacyIndex.coerceAtLeast(0))
                editor.putInt(chapterPageIndexKey(ChapterRepository.DEFAULT_CHAPTER_ID), legacyPageIndex)
            }

            editor.putBoolean(KEY_MIGRATED_V18_CONTENT_PACK, true)
            editor.apply()
        }
    }

    companion object {
        const val DEFAULT_CH01_EXERCISE_ID = "EX_CH01_BD032"
        private const val KEY_MIGRATED_V18_CONTENT_PACK = "key_migrated_v18_content_pack"
        private const val KEY_FONT_SCALE = "key_font_scale"
        private const val KEY_LAST_READ_SECTION = "key_last_read_section"
        private const val KEY_LAST_READ_PAGE = "key_last_read_page"
        private const val KEY_LAST_READ_SECTION_ID = "key_last_read_section_id"
        private const val KEY_LAST_READ_PAGE_ID = "key_last_read_page_id"
        private const val KEY_ACTIVE_BOOK_ID = "key_active_book_id"
        private const val KEY_ACTIVE_CHAPTER_ID = "key_active_chapter_id"
        private const val KEY_BOOKMARKS = "key_bookmarks"
        private const val KEY_EXERCISE_BD032 = "key_exercise_bd032"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_SOUND_VOLUME_PERCENT = "key_sound_volume_percent"
        private const val KEY_SOUND_CAT_MOVE = "key_sound_cat_move"
        private const val KEY_SOUND_CAT_CAPTURE = "key_sound_cat_capture"
        private const val KEY_SOUND_CAT_SPECIAL = "key_sound_cat_special"
        private const val KEY_SOUND_CAT_CHECKMATE = "key_sound_cat_checkmate"
        private const val KEY_SOUND_CAT_EXERCISE = "key_sound_cat_exercise"
        private const val KEY_SHOW_MOVE_TRAIL = "key_show_move_trail"
        private const val KEY_PLAYBACK_SPEED = "key_educational_playback_speed"

        // Independent Book Narration Settings (completely separate from ChessSoundManager)
        private const val KEY_NARRATION_ENABLED = "key_narration_enabled"
        private const val KEY_NARRATION_SPEED = "key_narration_speed"
        private const val KEY_NARRATION_VOLUME_PERCENT = "key_narration_volume_percent"
        private const val KEY_NARRATION_LAST_SECTION_ID = "key_narration_last_section_id"
        private const val KEY_NARRATION_LAST_BLOCK_ID = "key_narration_last_block_id"
        private const val KEY_NARRATION_LAST_POSITION_MS = "key_narration_last_position_ms"
        private const val KEY_NARRATION_LAST_CHAPTER_ID = "key_narration_last_chapter_id"
        private const val KEY_NARRATION_LAST_BOOK_ID = "key_narration_last_book_id"
    }

    var fontScale: FontSizeScale
        get() {
            val name = prefs.getString(KEY_FONT_SCALE, FontSizeScale.NORMAL.name)
            return try {
                FontSizeScale.valueOf(name ?: FontSizeScale.NORMAL.name)
            } catch (e: Exception) {
                FontSizeScale.NORMAL
            }
        }
        set(value) {
            prefs.edit().putString(KEY_FONT_SCALE, value.name).apply()
        }

    var lastReadSectionIndex: Int
        get() = prefs.getInt(KEY_LAST_READ_SECTION, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_READ_SECTION, value).apply()
        }

    var lastReadPageIndex: Int
        get() = prefs.getInt(KEY_LAST_READ_PAGE, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_READ_PAGE, value).apply()
        }

    var lastReadSectionId: String?
        get() = prefs.getString(KEY_LAST_READ_SECTION_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_READ_SECTION_ID, value).apply()
        }

    var lastReadPageId: String?
        get() = prefs.getString(KEY_LAST_READ_PAGE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_READ_PAGE_ID, value).apply()
        }

    fun getSavedActiveBookIdOrNull(): String? {
        return prefs.getString(KEY_ACTIVE_BOOK_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun getSavedActiveChapterIdOrNull(): String? {
        return prefs.getString(KEY_ACTIVE_CHAPTER_ID, null)?.takeIf { it.isNotBlank() }
    }

    var activeBookId: String
        get() = prefs.getString(KEY_ACTIVE_BOOK_ID, ChapterRepository.DEFAULT_BOOK_ID)
            ?: ChapterRepository.DEFAULT_BOOK_ID
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_BOOK_ID, value).apply()
        }

    var activeChapterId: String
        get() = prefs.getString(KEY_ACTIVE_CHAPTER_ID, ChapterRepository.DEFAULT_CHAPTER_ID)
            ?: ChapterRepository.DEFAULT_CHAPTER_ID
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_CHAPTER_ID, value).apply()
        }

    data class SavedReadingPosition(
        val bookId: String,
        val chapterId: String,
        val sectionId: String?,
        val sectionIndexFallback: Int,
        val pageIndex: Int,
        val pageId: String?,
        val sourcePdfPage: Int? = null,
        val blockId: String? = null
    )

    fun saveReadingPosition(sectionIndex: Int, pageIndex: Int) {
        val chapterId = activeChapterId
        val mappedSecId = if (chapterId == ChapterRepository.DEFAULT_CHAPTER_ID) {
            TranslationSlot.REQUIRED_SECTION_SLOT_IDS.getOrNull(sectionIndex)
        } else {
            null
        }
        val editor = prefs.edit()
            .putInt(KEY_LAST_READ_SECTION, sectionIndex)
            .putInt(KEY_LAST_READ_PAGE, pageIndex)
            .putInt(chapterSectionIndexKey(chapterId), sectionIndex)
            .putInt(chapterPageIndexKey(chapterId), pageIndex)
        if (mappedSecId != null) {
            editor.putString(KEY_LAST_READ_SECTION_ID, mappedSecId)
            editor.putString(chapterSectionIdKey(chapterId), mappedSecId)
        }
        editor.apply()
    }

    @JvmOverloads
    fun saveReadingPosition(
        bookId: String,
        chapterId: String,
        sectionId: String,
        sectionIndex: Int,
        pageIndex: Int,
        pageId: String? = null,
        sourcePdfPage: Int? = null,
        blockId: String? = null
    ) {
        val safeSecIdx = sectionIndex.coerceAtLeast(0)
        val safePageIdx = pageIndex.coerceAtLeast(0)
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_BOOK_ID, bookId)
            .putString(KEY_ACTIVE_CHAPTER_ID, chapterId)
            .putString(KEY_LAST_READ_SECTION_ID, sectionId)
            .putString(KEY_LAST_READ_PAGE_ID, pageId)
            .putInt(KEY_LAST_READ_SECTION, safeSecIdx)
            .putInt(KEY_LAST_READ_PAGE, safePageIdx)
            .putString(chapterSectionIdKey(chapterId), sectionId)
            .putInt(chapterSectionIndexKey(chapterId), safeSecIdx)
            .putInt(chapterPageIndexKey(chapterId), safePageIdx)
            .putString(chapterPageIdKey(chapterId), pageId)
            .putString(chapterBlockIdKey(chapterId), blockId)
        if (sourcePdfPage != null) {
            editor.putInt(chapterSourcePdfPageKey(chapterId), sourcePdfPage)
        } else {
            editor.remove(chapterSourcePdfPageKey(chapterId))
        }
        editor.apply()
    }

    fun getSavedReadingPosition(
        chapterId: String = activeChapterId,
        bookId: String = activeBookId
    ): SavedReadingPosition {
        val sectionId = if (prefs.contains(chapterSectionIdKey(chapterId))) {
            prefs.getString(chapterSectionIdKey(chapterId), null)
        } else if (chapterId == ChapterRepository.DEFAULT_CHAPTER_ID && prefs.contains(KEY_LAST_READ_SECTION)) {
            val legacyIdx = prefs.getInt(KEY_LAST_READ_SECTION, 0)
            TranslationSlot.REQUIRED_SECTION_SLOT_IDS.getOrNull(legacyIdx)
        } else {
            null
        }

        val sectionIndexFallback = if (prefs.contains(chapterSectionIndexKey(chapterId))) {
            prefs.getInt(chapterSectionIndexKey(chapterId), 0).coerceAtLeast(0)
        } else if (chapterId == ChapterRepository.DEFAULT_CHAPTER_ID) {
            prefs.getInt(KEY_LAST_READ_SECTION, 0).coerceAtLeast(0)
        } else {
            0
        }

        val pageIndex = if (prefs.contains(chapterPageIndexKey(chapterId))) {
            prefs.getInt(chapterPageIndexKey(chapterId), 0).coerceAtLeast(0)
        } else if (chapterId == ChapterRepository.DEFAULT_CHAPTER_ID) {
            prefs.getInt(KEY_LAST_READ_PAGE, 0).coerceAtLeast(0)
        } else {
            0
        }

        val pageId = prefs.getString(chapterPageIdKey(chapterId), null)
            ?: if (chapterId == ChapterRepository.DEFAULT_CHAPTER_ID) {
                prefs.getString(KEY_LAST_READ_PAGE_ID, null)
            } else {
                null
            }

        val sourcePdfPage = if (prefs.contains(chapterSourcePdfPageKey(chapterId))) {
            prefs.getInt(chapterSourcePdfPageKey(chapterId), 0).takeIf { it > 0 }
        } else {
            null
        }
        val blockId = prefs.getString(chapterBlockIdKey(chapterId), null)?.takeIf { it.isNotBlank() }

        return SavedReadingPosition(
            bookId = bookId,
            chapterId = chapterId,
            sectionId = sectionId,
            sectionIndexFallback = sectionIndexFallback,
            pageIndex = pageIndex,
            pageId = pageId,
            sourcePdfPage = sourcePdfPage,
            blockId = blockId
        )
    }

    data class SavedFrontMatterPosition(
        val bookId: String,
        val sourcePdfPage: Int,
        val pageIndex: Int,
        val pageId: String?
    )

    fun saveFrontMatterReadingPosition(
        bookId: String = activeBookId,
        sourcePdfPage: Int,
        pageIndex: Int,
        pageId: String? = null
    ) {
        val safePdfPage = sourcePdfPage.coerceIn(1, 39)
        val safePageIdx = pageIndex.coerceAtLeast(0)
        prefs.edit()
            .putInt(frontMatterSourcePdfPageKey(bookId), safePdfPage)
            .putInt(frontMatterPageIndexKey(bookId), safePageIdx)
            .putString(frontMatterPageIdKey(bookId), pageId)
            .apply()
    }

    fun getSavedFrontMatterReadingPosition(
        bookId: String = activeBookId
    ): SavedFrontMatterPosition {
        val sourcePdfPage = prefs.getInt(frontMatterSourcePdfPageKey(bookId), 1).coerceIn(1, 39)
        val pageIndex = prefs.getInt(frontMatterPageIndexKey(bookId), 0).coerceAtLeast(0)
        val pageId = prefs.getString(frontMatterPageIdKey(bookId), null)
        return SavedFrontMatterPosition(
            bookId = bookId,
            sourcePdfPage = sourcePdfPage,
            pageIndex = pageIndex,
            pageId = pageId
        )
    }

    fun isBookmarked(diagramId: String): Boolean {
        val set = prefs.getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
        return set.contains(diagramId)
    }

    fun toggleBookmark(diagramId: String): Boolean {
        val set = prefs.getStringSet(KEY_BOOKMARKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newState = if (set.contains(diagramId)) {
            set.remove(diagramId)
            false
        } else {
            set.add(diagramId)
            true
        }
        prefs.edit().putStringSet(KEY_BOOKMARKS, set).apply()
        return newState
    }

    fun getBookmarks(): Set<String> {
        return prefs.getStringSet(KEY_BOOKMARKS, emptySet()) ?: emptySet()
    }

    fun isExerciseSolved(exerciseId: String): Boolean {
        val key = exerciseSolvedKey(exerciseId)
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false)
        }
        if (exerciseId == DEFAULT_CH01_EXERCISE_ID) {
            return prefs.getBoolean(KEY_EXERCISE_BD032, false)
        }
        return false
    }

    fun setExerciseSolved(exerciseId: String, solved: Boolean) {
        val editor = prefs.edit().putBoolean(exerciseSolvedKey(exerciseId), solved)
        if (exerciseId == DEFAULT_CH01_EXERCISE_ID) {
            editor.putBoolean(KEY_EXERCISE_BD032, solved)
        }
        editor.apply()
    }

    fun isBd032Solved(): Boolean {
        return isExerciseSolved(DEFAULT_CH01_EXERCISE_ID)
    }

    fun setBd032Solved(solved: Boolean) {
        setExerciseSolved(DEFAULT_CH01_EXERCISE_ID, solved)
    }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
        }

    var soundVolumePercent: Int
        get() = prefs.getInt(KEY_SOUND_VOLUME_PERCENT, 70).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_SOUND_VOLUME_PERCENT, value.coerceIn(0, 100)).apply()
        }

    var moveSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_CAT_MOVE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_CAT_MOVE, value).apply()
        }

    var captureSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_CAT_CAPTURE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_CAT_CAPTURE, value).apply()
        }

    var specialMoveSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_CAT_SPECIAL, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_CAT_SPECIAL, value).apply()
        }

    var checkMateSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_CAT_CHECKMATE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_CAT_CHECKMATE, value).apply()
        }

    var exerciseFeedbackSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_CAT_EXERCISE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_CAT_EXERCISE, value).apply()
        }

    fun loadAudioSettings(): com.example.audio.AudioSettingsState {
        return com.example.audio.AudioSettingsState(
            soundEnabled = soundEnabled,
            volumePercent = soundVolumePercent,
            moveSoundEnabled = moveSoundEnabled,
            captureSoundEnabled = captureSoundEnabled,
            specialMoveSoundEnabled = specialMoveSoundEnabled,
            checkMateSoundEnabled = checkMateSoundEnabled,
            exerciseFeedbackSoundEnabled = exerciseFeedbackSoundEnabled
        )
    }

    fun saveAudioSettings(settings: com.example.audio.AudioSettingsState) {
        prefs.edit()
            .putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
            .putInt(KEY_SOUND_VOLUME_PERCENT, settings.volumePercent.coerceIn(0, 100))
            .putBoolean(KEY_SOUND_CAT_MOVE, settings.moveSoundEnabled)
            .putBoolean(KEY_SOUND_CAT_CAPTURE, settings.captureSoundEnabled)
            .putBoolean(KEY_SOUND_CAT_SPECIAL, settings.specialMoveSoundEnabled)
            .putBoolean(KEY_SOUND_CAT_CHECKMATE, settings.checkMateSoundEnabled)
            .putBoolean(KEY_SOUND_CAT_EXERCISE, settings.exerciseFeedbackSoundEnabled)
            .apply()
    }

    var showMoveTrail: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MOVE_TRAIL, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_MOVE_TRAIL, value).apply()
        }

    var playbackSpeed: EducationalPlaybackSpeed
        get() {
            val name = prefs.getString(KEY_PLAYBACK_SPEED, EducationalPlaybackSpeed.NORMAL.name)
            return try {
                EducationalPlaybackSpeed.valueOf(name ?: EducationalPlaybackSpeed.NORMAL.name)
            } catch (e: Exception) {
                EducationalPlaybackSpeed.NORMAL
            }
        }
        set(value) {
            prefs.edit().putString(KEY_PLAYBACK_SPEED, value.name).apply()
        }

    var narrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_NARRATION_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NARRATION_ENABLED, value).apply()
        }

    var narrationSpeed: com.example.audio.NarrationPlaybackSpeed
        get() {
            val name = prefs.getString(
                KEY_NARRATION_SPEED,
                com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X.name
            )
            return try {
                com.example.audio.NarrationPlaybackSpeed.valueOf(
                    name ?: com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X.name
                )
            } catch (e: Exception) {
                com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X
            }
        }
        set(value) {
            prefs.edit().putString(KEY_NARRATION_SPEED, value.name).apply()
        }

    var narrationVolumePercent: Int
        get() = prefs.getInt(KEY_NARRATION_VOLUME_PERCENT, 85).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_NARRATION_VOLUME_PERCENT, value.coerceIn(0, 100)).apply()
        }

    var narrationLastChapterId: String?
        get() = prefs.getString(KEY_NARRATION_LAST_CHAPTER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_NARRATION_LAST_CHAPTER_ID, value).apply()
        }

    var narrationLastBookId: String?
        get() = prefs.getString(KEY_NARRATION_LAST_BOOK_ID, null)
        set(value) {
            prefs.edit().putString(KEY_NARRATION_LAST_BOOK_ID, value).apply()
        }

    var narrationLastSectionId: String?
        get() = prefs.getString(KEY_NARRATION_LAST_SECTION_ID, null)
        set(value) {
            prefs.edit().putString(KEY_NARRATION_LAST_SECTION_ID, value).apply()
        }

    var narrationLastBlockId: String?
        get() = prefs.getString(KEY_NARRATION_LAST_BLOCK_ID, null)
        set(value) {
            prefs.edit().putString(KEY_NARRATION_LAST_BLOCK_ID, value).apply()
        }

    var narrationLastPositionMs: Int
        get() = prefs.getInt(KEY_NARRATION_LAST_POSITION_MS, 0).coerceAtLeast(0)
        set(value) {
            prefs.edit().putInt(KEY_NARRATION_LAST_POSITION_MS, value.coerceAtLeast(0)).apply()
        }

    data class SavedNarrationPosition(
        val bookId: String,
        val chapterId: String,
        val sectionId: String?,
        val blockId: String?,
        val positionMs: Int
    )

    fun saveNarrationResumeState(
        sectionId: String,
        blockId: String,
        positionMs: Int,
        chapterId: String = activeChapterId,
        bookId: String = activeBookId
    ) {
        val clampedPos = positionMs.coerceAtLeast(0)
        prefs.edit()
            .putString(KEY_NARRATION_LAST_BOOK_ID, bookId)
            .putString(KEY_NARRATION_LAST_CHAPTER_ID, chapterId)
            .putString(KEY_NARRATION_LAST_SECTION_ID, sectionId)
            .putString(KEY_NARRATION_LAST_BLOCK_ID, blockId)
            .putInt(KEY_NARRATION_LAST_POSITION_MS, clampedPos)
            .putString(narrationSectionIdKey(bookId, chapterId), sectionId)
            .putString(narrationBlockIdKey(bookId, chapterId), blockId)
            .putInt(narrationPositionMsKey(bookId, chapterId), clampedPos)
            .apply()
    }

    fun getSavedNarrationResumeState(
        chapterId: String,
        bookId: String = activeBookId
    ): SavedNarrationPosition {
        val scopedSecKey = narrationSectionIdKey(bookId, chapterId)
        val scopedBlkKey = narrationBlockIdKey(bookId, chapterId)
        val scopedPosKey = narrationPositionMsKey(bookId, chapterId)

        if (prefs.contains(scopedSecKey) || prefs.contains(scopedBlkKey)) {
            return SavedNarrationPosition(
                bookId = bookId,
                chapterId = chapterId,
                sectionId = prefs.getString(scopedSecKey, null),
                blockId = prefs.getString(scopedBlkKey, null),
                positionMs = prefs.getInt(scopedPosKey, 0).coerceAtLeast(0)
            )
        }

        // Legacy fallback only if global narration resume belonged to the same chapter (or had no chapter set for CH01)
        val lastChap = prefs.getString(KEY_NARRATION_LAST_CHAPTER_ID, null)
        val lastBook = prefs.getString(KEY_NARRATION_LAST_BOOK_ID, null)
        val matchesLegacy = (lastChap == chapterId && (lastBook == null || lastBook == bookId)) ||
            (lastChap == null && chapterId == ChapterRepository.DEFAULT_CHAPTER_ID)
        return if (matchesLegacy) {
            SavedNarrationPosition(
                bookId = bookId,
                chapterId = chapterId,
                sectionId = prefs.getString(KEY_NARRATION_LAST_SECTION_ID, null),
                blockId = prefs.getString(KEY_NARRATION_LAST_BLOCK_ID, null),
                positionMs = prefs.getInt(KEY_NARRATION_LAST_POSITION_MS, 0).coerceAtLeast(0)
            )
        } else {
            SavedNarrationPosition(
                bookId = bookId,
                chapterId = chapterId,
                sectionId = null,
                blockId = null,
                positionMs = 0
            )
        }
    }

    fun clearNarrationResumeState(
        chapterId: String? = null,
        bookId: String = activeBookId
    ) {
        val editor = prefs.edit()
        if (chapterId != null) {
            editor.remove(narrationSectionIdKey(bookId, chapterId))
                .remove(narrationBlockIdKey(bookId, chapterId))
                .remove(narrationPositionMsKey(bookId, chapterId))
            if (prefs.getString(KEY_NARRATION_LAST_CHAPTER_ID, null) == chapterId) {
                editor.remove(KEY_NARRATION_LAST_BOOK_ID)
                    .remove(KEY_NARRATION_LAST_CHAPTER_ID)
                    .remove(KEY_NARRATION_LAST_SECTION_ID)
                    .remove(KEY_NARRATION_LAST_BLOCK_ID)
                    .remove(KEY_NARRATION_LAST_POSITION_MS)
            }
        } else {
            prefs.all.keys
                .filter { it.startsWith("key_narration_last_") }
                .forEach { editor.remove(it) }
        }
        editor.apply()
    }

    private fun exerciseSolvedKey(exerciseId: String): String = "key_exercise_solved_$exerciseId"
    private fun chapterSectionIdKey(chapterId: String): String = "key_last_read_section_id_$chapterId"
    private fun chapterSectionIndexKey(chapterId: String): String = "key_last_read_section_idx_$chapterId"
    private fun chapterPageIndexKey(chapterId: String): String = "key_last_read_page_idx_$chapterId"
    private fun chapterPageIdKey(chapterId: String): String = "key_last_read_page_id_$chapterId"
    private fun chapterSourcePdfPageKey(chapterId: String): String = "key_last_read_pdf_page_$chapterId"
    private fun chapterBlockIdKey(chapterId: String): String = "key_last_read_block_id_$chapterId"
    private fun narrationSectionIdKey(bookId: String, chapterId: String): String =
        "key_narration_last_section_id_${bookId}_$chapterId"
    private fun narrationBlockIdKey(bookId: String, chapterId: String): String =
        "key_narration_last_block_id_${bookId}_$chapterId"
    private fun narrationPositionMsKey(bookId: String, chapterId: String): String =
        "key_narration_last_pos_ms_${bookId}_$chapterId"
    private fun frontMatterSourcePdfPageKey(bookId: String): String =
        "key_front_matter_last_pdf_page_$bookId"
    private fun frontMatterPageIndexKey(bookId: String): String =
        "key_front_matter_last_page_idx_$bookId"
    private fun frontMatterPageIdKey(bookId: String): String =
        "key_front_matter_last_page_id_$bookId"
}
