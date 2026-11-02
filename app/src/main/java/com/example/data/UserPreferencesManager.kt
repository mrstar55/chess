package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class FontSizeScale(val scale: Float, val labelFa: String) {
    SMALL(0.85f, "کوچک"),
    NORMAL(1.0f, "استاندارد"),
    LARGE(1.25f, "بزرگ")
}

class UserPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("chess_chapter_1_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FONT_SCALE = "key_font_scale"
        private const val KEY_LAST_READ_SECTION = "key_last_read_section"
        private const val KEY_BOOKMARKS = "key_bookmarks"
        private const val KEY_EXERCISE_BD032 = "key_exercise_bd032"
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
}
