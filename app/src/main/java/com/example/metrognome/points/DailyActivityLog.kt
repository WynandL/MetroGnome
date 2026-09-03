package com.example.metrognome.points

import android.content.Context
import androidx.core.content.edit
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Raw activity counts for a single calendar day.
 * All values are the delta since midnight, not lifetime totals.
 */
data class DailyActivity(
    val metronomeSeconds: Long,
    val tunerNotesLocked: Int,
    val droneSecondsToday: Long,
    val gameScoreToday: Int,
    val practiceMinutesToday: Int,
    val practiceSessionsToday: Int,
    val speedTrainerSessionsCompleted: Int,
    val speedTrainerSecondsToday: Long,
    val tunerFeedbackGiven: Int,
    val performanceBonusToday: Int,
) {
    companion object {
        val EMPTY = DailyActivity(0L, 0, 0L, 0, 0, 0, 0, 0L, 0, 0)
    }
}

/**
 * Derives today's activity by comparing current lifetime counters against a
 * baseline snapshot taken at the start of the day.
 *
 * Baseline approach: on the first call of each calendar day, the current
 * lifetime values from [MetroItemTracker] are stored as the day's baseline.
 * "Today's activity" is then simply: current - baseline.
 *
 * No changes to existing call sites or ViewModels are required — this class
 * only reads from [MetroItemTracker] and never writes to it.
 */
class DailyActivityLog(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dateFormat.format(Date())

    /**
     * If the stored date is not today, snapshot the current lifetime counters
     * as the new baseline. Called lazily before any read so a single call to
     * [todayActivity] always produces a consistent result.
     */
    private fun resetIfNewDay(tracker: MetroItemTracker) {
        if (prefs.getString(KEY_DATE, null) == today()) return
        prefs.edit {
            putString(KEY_DATE, today())
            putLong(KEY_BASE_METRONOME,     tracker.metronomeSeconds())
            putInt(KEY_BASE_TUNER_NOTES,    tracker.tunerNotesLocked())
            putLong(KEY_BASE_DRONE,         tracker.droneSeconds())
            putInt(KEY_BASE_GAME_SCORE,         tracker.totalGameScore())
            putInt(KEY_BASE_PRACTICE_MINUTES,   tracker.totalPracticeMinutes())
            putInt(KEY_BASE_PRACTICE_SESSIONS,  tracker.practiceSessionsCompleted())
            putInt(KEY_BASE_SPEED_TRAINER,          tracker.speedTrainingSessionsCompleted())
            putLong(KEY_BASE_SPEED_TRAINER_SECONDS, tracker.speedTrainerSeconds())
            putInt(KEY_BASE_FEEDBACK,       tracker.tunerFeedbackGiven())
            putInt(KEY_BASE_PERFORMANCE_BONUS, tracker.performanceBonusPoints())
        }
    }

    /** Returns today's activity delta. Always safe to call; resets automatically at midnight. */
    fun todayActivity(tracker: MetroItemTracker): DailyActivity {
        resetIfNewDay(tracker)
        return DailyActivity(
            metronomeSeconds              = (tracker.metronomeSeconds()               - prefs.getLong(KEY_BASE_METRONOME,    0L)).coerceAtLeast(0L),
            tunerNotesLocked              = (tracker.tunerNotesLocked()               - prefs.getInt(KEY_BASE_TUNER_NOTES,  0)).coerceAtLeast(0),
            droneSecondsToday             = (tracker.droneSeconds()                   - prefs.getLong(KEY_BASE_DRONE,      0L)).coerceAtLeast(0L),
            gameScoreToday                = (tracker.totalGameScore()                 - prefs.getInt(KEY_BASE_GAME_SCORE,          0)).coerceAtLeast(0),
            practiceMinutesToday          = (tracker.totalPracticeMinutes()           - prefs.getInt(KEY_BASE_PRACTICE_MINUTES,    0)).coerceAtLeast(0),
            practiceSessionsToday         = (tracker.practiceSessionsCompleted()      - prefs.getInt(KEY_BASE_PRACTICE_SESSIONS,   0)).coerceAtLeast(0),
            speedTrainerSessionsCompleted = (tracker.speedTrainingSessionsCompleted() - prefs.getInt(KEY_BASE_SPEED_TRAINER,          0)).coerceAtLeast(0),
            speedTrainerSecondsToday      = (tracker.speedTrainerSeconds()             - prefs.getLong(KEY_BASE_SPEED_TRAINER_SECONDS, 0L)).coerceAtLeast(0L),
            tunerFeedbackGiven            = (tracker.tunerFeedbackGiven()             - prefs.getInt(KEY_BASE_FEEDBACK,      0)).coerceAtLeast(0),
            performanceBonusToday         = (tracker.performanceBonusPoints()        - prefs.getInt(KEY_BASE_PERFORMANCE_BONUS, 0)).coerceAtLeast(0),
        )
    }

    companion object {
        private const val PREFS_NAME            = "points_daily_log"
        private const val KEY_DATE              = "date"
        private const val KEY_BASE_METRONOME    = "base_metronome_sec"
        private const val KEY_BASE_TUNER_NOTES  = "base_tuner_notes"
        private const val KEY_BASE_DRONE        = "base_drone_sec"
        private const val KEY_BASE_GAME_SCORE       = "base_game_score"
        private const val KEY_BASE_PRACTICE_MINUTES  = "base_practice_min"
        private const val KEY_BASE_PRACTICE_SESSIONS = "base_practice_sessions"
        private const val KEY_BASE_SPEED_TRAINER         = "base_speed_trainer"
        private const val KEY_BASE_SPEED_TRAINER_SECONDS = "base_speed_trainer_sec"
        private const val KEY_BASE_FEEDBACK     = "base_feedback"
        private const val KEY_BASE_PERFORMANCE_BONUS = "base_performance_bonus"
    }
}
