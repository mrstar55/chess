package com.example.data

import android.content.Context
import android.content.SharedPreferences

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

    companion object {
        private const val KEY_FONT_SCALE = "key_font_scale"
        private const val KEY_LAST_READ_SECTION = "key_last_read_section"
        private const val KEY_LAST_READ_PAGE = "key_last_read_page"
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

    fun saveReadingPosition(sectionIndex: Int, pageIndex: Int) {
        prefs.edit()
            .putInt(KEY_LAST_READ_SECTION, sectionIndex)
            .putInt(KEY_LAST_READ_PAGE, pageIndex)
            .apply()
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

    fun isBd032Solved(): Boolean {
        return prefs.getBoolean(KEY_EXERCISE_BD032, false)
    }

    fun setBd032Solved(solved: Boolean) {
        prefs.edit().putBoolean(KEY_EXERCISE_BD032, solved).apply()
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
            val name = prefs.getString(KEY_NARRATION_SPEED, com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X.name)
            return try {
                com.example.audio.NarrationPlaybackSpeed.valueOf(name ?: com.example.audio.NarrationPlaybackSpeed.SPEED_1_0X.name)
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

    fun saveNarrationResumeState(sectionId: String, blockId: String, positionMs: Int) {
        prefs.edit()
            .putString(KEY_NARRATION_LAST_SECTION_ID, sectionId)
            .putString(KEY_NARRATION_LAST_BLOCK_ID, blockId)
            .putInt(KEY_NARRATION_LAST_POSITION_MS, positionMs.coerceAtLeast(0))
            .apply()
    }

    fun clearNarrationResumeState() {
        prefs.edit()
            .remove(KEY_NARRATION_LAST_SECTION_ID)
            .remove(KEY_NARRATION_LAST_BLOCK_ID)
            .remove(KEY_NARRATION_LAST_POSITION_MS)
            .apply()
    }
}

