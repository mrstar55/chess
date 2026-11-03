package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.example.R
import com.example.model.BoardMoveType
import com.example.model.BoardTransition

enum class ChessSoundCategory(val labelFa: String) {
    MOVE("صدای حرکت مهره‌ها"),
    CAPTURE("صدای گرفتن مهره"),
    SPECIAL_MOVE("صدای حرکت‌های ویژه"),
    CHECK_CHECKMATE("صدای کیش و مات"),
    EXERCISE_FEEDBACK("صدای بازخورد تمرین")
}

enum class ChessSoundType(
    @RawRes val rawResId: Int,
    val category: ChessSoundCategory,
    val labelFa: String
) {
    MOVE_NORMAL(R.raw.snd_move_normal, ChessSoundCategory.MOVE, "حرکت عادی مهره"),
    CAPTURE(R.raw.snd_capture, ChessSoundCategory.CAPTURE, "گرفتن مهره"),
    CASTLING(R.raw.snd_castling, ChessSoundCategory.SPECIAL_MOVE, "قلعه‌رفتن"),
    EN_PASSANT(R.raw.snd_en_passant, ChessSoundCategory.SPECIAL_MOVE, "آن‌پاسان"),
    PROMOTION(R.raw.snd_promotion, ChessSoundCategory.SPECIAL_MOVE, "ارتقای پیاده"),
    CHECK(R.raw.snd_check, ChessSoundCategory.CHECK_CHECKMATE, "کیش"),
    CHECKMATE(R.raw.snd_checkmate, ChessSoundCategory.CHECK_CHECKMATE, "مات"),
    ILLEGAL_MOVE(R.raw.snd_illegal_move, ChessSoundCategory.EXERCISE_FEEDBACK, "حرکت غیرقانونی"),
    EXERCISE_WRONG(R.raw.snd_exercise_wrong, ChessSoundCategory.EXERCISE_FEEDBACK, "حرکت اشتباه تمرین"),
    EXERCISE_CORRECT(R.raw.snd_exercise_correct, ChessSoundCategory.EXERCISE_FEEDBACK, "حل صحیح تمرین"),
    EXERCISE_RESET(R.raw.snd_exercise_reset, ChessSoundCategory.EXERCISE_FEEDBACK, "شروع مجدد تمرین")
}

data class AudioSettingsState(
    val soundEnabled: Boolean = true,
    val volumePercent: Int = 70,
    val moveSoundEnabled: Boolean = true,
    val captureSoundEnabled: Boolean = true,
    val specialMoveSoundEnabled: Boolean = true,
    val checkMateSoundEnabled: Boolean = true,
    val exerciseFeedbackSoundEnabled: Boolean = true
) {
    val normalizedVolume: Float
        get() = (volumePercent.coerceIn(0, 100) / 100f)

    fun isCategoryEnabled(category: ChessSoundCategory): Boolean {
        return when (category) {
            ChessSoundCategory.MOVE -> moveSoundEnabled
            ChessSoundCategory.CAPTURE -> captureSoundEnabled
            ChessSoundCategory.SPECIAL_MOVE -> specialMoveSoundEnabled
            ChessSoundCategory.CHECK_CHECKMATE -> checkMateSoundEnabled
            ChessSoundCategory.EXERCISE_FEEDBACK -> exerciseFeedbackSoundEnabled
        }
    }

    fun canPlay(soundType: ChessSoundType): Boolean {
        return soundEnabled && volumePercent > 0 && isCategoryEnabled(soundType.category)
    }
}

data class ChessSoundEvent(
    val eventId: Long,
    val soundType: ChessSoundType,
    val sourceTag: String = ""
) {
    val category: ChessSoundCategory
        get() = soundType.category
}

data class SoundPlaybackRecord(
    val event: ChessSoundEvent,
    val played: Boolean,
    val effectiveVolume: Float,
    val suppressedReason: String? = null
)

/**
 * Low-latency local chess audio manager backed by Android [SoundPool] and preloaded
 * raw WAV resources in `res/raw/`.
 *
 * Strictly event-driven with monotonic [ChessSoundEvent.eventId] deduplication so that
 * UI recompositions, state restoration, or screenshot captures never replay an old sound.
 */
class ChessSoundManager(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIdByType: MutableMap<ChessSoundType, Int> = mutableMapOf()
    private val loadedSoundIds: MutableSet<Int> = mutableSetOf()
    private val processedEventIds: LinkedHashSet<Long> = linkedSetOf()
    private val _playbackHistory: MutableList<SoundPlaybackRecord> = mutableListOf()
    private var isReleased: Boolean = false

    val playbackHistory: List<SoundPlaybackRecord>
        get() = _playbackHistory.toList()

    val playedEvents: List<ChessSoundEvent>
        get() = _playbackHistory.filter { it.played }.map { it.event }

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
            }
        }
        preloadAllSounds()
    }

    private fun preloadAllSounds() {
        ChessSoundType.values().forEach { type ->
            val id = try {
                soundPool.load(appContext, type.rawResId, 1)
            } catch (e: Exception) {
                0
            }
            if (id != 0) {
                soundIdByType[type] = id
            }
        }
    }

    fun isPreloaded(type: ChessSoundType): Boolean {
        return (soundIdByType[type] ?: 0) != 0
    }

    fun preloadedSoundCount(): Int = soundIdByType.size

    /**
     * Plays [event] once if its [ChessSoundEvent.eventId] has not been processed yet and
     * [settings] permits playback for the event's category and master volume.
     *
     * Returns `true` if the sound was played, or `false` if suppressed.
     */
    fun handleEvent(event: ChessSoundEvent, settings: AudioSettingsState): Boolean {
        if (isReleased) return false

        if (processedEventIds.contains(event.eventId)) {
            _playbackHistory.add(
                SoundPlaybackRecord(
                    event = event,
                    played = false,
                    effectiveVolume = 0f,
                    suppressedReason = "DUPLICATE_EVENT_ID"
                )
            )
            return false
        }

        processedEventIds.add(event.eventId)
        while (processedEventIds.size > MAX_TRACKED_EVENT_IDS) {
            val first = processedEventIds.iterator().next()
            processedEventIds.remove(first)
        }

        if (!settings.soundEnabled) {
            _playbackHistory.add(
                SoundPlaybackRecord(
                    event = event,
                    played = false,
                    effectiveVolume = 0f,
                    suppressedReason = "MASTER_DISABLED"
                )
            )
            return false
        }

        val clampedVolume = settings.normalizedVolume
        if (clampedVolume <= 0f) {
            _playbackHistory.add(
                SoundPlaybackRecord(
                    event = event,
                    played = false,
                    effectiveVolume = 0f,
                    suppressedReason = "ZERO_VOLUME"
                )
            )
            return false
        }

        if (!settings.isCategoryEnabled(event.category)) {
            _playbackHistory.add(
                SoundPlaybackRecord(
                    event = event,
                    played = false,
                    effectiveVolume = 0f,
                    suppressedReason = "CATEGORY_DISABLED"
                )
            )
            return false
        }

        val soundId = soundIdByType[event.soundType] ?: 0
        if (soundId != 0) {
            try {
                // When checkmate or exercise-correct occurs, pause any prior stream so sounds never clash
                if (event.soundType == ChessSoundType.CHECKMATE ||
                    event.soundType == ChessSoundType.EXERCISE_CORRECT
                ) {
                    soundPool.autoPause()
                }
                val streamVol = if (event.soundType == ChessSoundType.EXERCISE_CORRECT) {
                    (clampedVolume * 0.85f).coerceIn(0f, 1f)
                } else {
                    clampedVolume
                }
                soundPool.play(soundId, streamVol, streamVol, 1, 0, 1.0f)
            } catch (_: Exception) {
                // Guard against audio hardware issues on headless test environments
            }
        }

        _playbackHistory.add(
            SoundPlaybackRecord(
                event = event,
                played = true,
                effectiveVolume = clampedVolume,
                suppressedReason = null
            )
        )
        return true
    }

    fun clearHistoryForTesting() {
        _playbackHistory.clear()
        processedEventIds.clear()
    }

    fun release() {
        if (!isReleased) {
            isReleased = true
            try {
                soundPool.release()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val MAX_TRACKED_EVENT_IDS = 256

        /**
         * Determines the exact [ChessSoundType] for a verified [BoardTransition].
         *
         * - When [preferCaptureOverCheck] is `false` (default for PGN and standard play):
         *   Checkmate is dominant (`CHECKMATE`), followed by `CHECK`, `CASTLING`, `EN_PASSANT`,
         *   `PROMOTION`, `CAPTURE`, and `MOVE_NORMAL`.
         * - When [preferCaptureOverCheck] is `true` (used for non-checkmate moves in exercises so
         *   that a wrong capture move like `Qxg5+` plays the `CAPTURE` sound during the capture
         *   animation before the post-animation `EXERCISE_WRONG` feedback sound):
         *   Capture/special move types take precedence over non-mating `isCheck`.
         */
        fun resolveSoundForTransition(
            transition: BoardTransition,
            preferCaptureOverCheck: Boolean = false
        ): ChessSoundType {
            if (!transition.isReverse && transition.isCheckmate) {
                return ChessSoundType.CHECKMATE
            }
            if (!transition.isReverse && transition.isCheck && !preferCaptureOverCheck) {
                return ChessSoundType.CHECK
            }
            return when (transition.moveType) {
                BoardMoveType.CASTLING_KINGSIDE,
                BoardMoveType.CASTLING_QUEENSIDE -> ChessSoundType.CASTLING
                BoardMoveType.EN_PASSANT -> ChessSoundType.EN_PASSANT
                BoardMoveType.PROMOTION,
                BoardMoveType.PROMOTION_CAPTURE -> ChessSoundType.PROMOTION
                BoardMoveType.CAPTURE -> ChessSoundType.CAPTURE
                BoardMoveType.NORMAL -> {
                    if (!transition.isReverse && transition.isCheck) {
                        ChessSoundType.CHECK
                    } else {
                        ChessSoundType.MOVE_NORMAL
                    }
                }
            }
        }
    }
}
