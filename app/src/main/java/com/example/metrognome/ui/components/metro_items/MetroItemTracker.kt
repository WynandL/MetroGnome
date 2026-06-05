package com.example.metrognome.ui.components.metro_items

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Persists usage counters and evaluates item unlock conditions.
 *
 * All counters are stored in SharedPreferences "metro_cosmetics".
 * Counters are append-only — nothing ever decrements them, so
 * there is no way to cheat by clearing and replaying.
 *
 * Thread-safety: addMetronomeSeconds / recordGameCompleted may be
 * called from any thread; SharedPreferences.edit() is thread-safe.
 */
class MetroItemTracker(context: Context) {

    private val prefs         = context.getSharedPreferences("metro_cosmetics", Context.MODE_PRIVATE)
    private val practicePrefs = context.getSharedPreferences("practice_sessions", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_METRONOME_SECONDS  = "metronome_seconds"
        private const val KEY_TUNER_SECONDS      = "tuner_seconds"
        private const val KEY_GAMES_COMPLETED    = "games_completed"
        private const val KEY_FIRST_LAUNCH_MS    = "first_launch_ms"
        private const val KEY_CHEAT_MODE          = "cheat_mode"
        private const val KEY_FORCE_UNLOCKED_IDS  = "force_unlocked_ids"
        private const val KEY_TUNER_NOTES_LOCKED      = "tuner_notes_locked"
        private const val KEY_GAME_SCORE_TOTAL         = "game_score_total"
        private const val KEY_PRACTICE_MINUTES_TOTAL   = "practice_minutes_total"
        private const val KEY_TUNER_FEEDBACK          = "tuner_feedback_count"
        private const val KEY_SPEED_TRAINING_SESSIONS = "speed_training_sessions"
        private const val KEY_SPEED_TRAINER_SECONDS   = "speed_trainer_seconds"
        private const val KEY_MIC_BONUS_SESSIONS      = "mic_bonus_sessions"

        // Mirrors PracticeSessionManager key — read-only here, written only by PracticeSessionManager
        private const val KEY_PRACTICE_TOTAL = "total_sessions"
    }

    init {
        // Record first launch once, permanently
        if (!prefs.contains(KEY_FIRST_LAUNCH_MS)) {
            prefs.edit { putLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis()) }
        }
    }

    // ── Writers (called by engine / game) ─────────────────────────────────────

    /** Add [seconds] to the cumulative metronome play-time. */
    fun addMetronomeSeconds(seconds: Long) {
        val current = prefs.getLong(KEY_METRONOME_SECONDS, 0L)
        prefs.edit { putLong(KEY_METRONOME_SECONDS, current + seconds) }
    }

    /** Add [seconds] to the cumulative tuner listening time. */
    fun addTunerSeconds(seconds: Long) {
        val current = prefs.getLong(KEY_TUNER_SECONDS, 0L)
        prefs.edit { putLong(KEY_TUNER_SECONDS, current + seconds) }
    }

    /** Increment the count of individual notes the tuner has locked on to. */
    fun recordTunerNoteLocked() {
        val current = prefs.getInt(KEY_TUNER_NOTES_LOCKED, 0)
        prefs.edit { putInt(KEY_TUNER_NOTES_LOCKED, current + 1) }
    }

    /** Increment the completed-games counter by 1. */
    fun recordGameCompleted() {
        val current = prefs.getInt(KEY_GAMES_COMPLETED, 0)
        prefs.edit { putInt(KEY_GAMES_COMPLETED, current + 1) }
    }

    /** Accumulate [score] into the lifetime rhythm-game score total (used for Beats points). */
    fun addGameScore(score: Int) {
        val current = prefs.getInt(KEY_GAME_SCORE_TOTAL, 0)
        prefs.edit { putInt(KEY_GAME_SCORE_TOTAL, current + score) }
    }

    /** Accumulate [minutes] into the lifetime practice time total (used for Beats points). */
    fun addPracticeMinutes(minutes: Int) {
        val current = prefs.getInt(KEY_PRACTICE_MINUTES_TOTAL, 0)
        prefs.edit { putInt(KEY_PRACTICE_MINUTES_TOTAL, current + minutes) }
    }

    /** Increment the thumbs-up tuner feedback counter by 1. */
    fun recordTunerFeedback() {
        val current = prefs.getInt(KEY_TUNER_FEEDBACK, 0)
        prefs.edit { putInt(KEY_TUNER_FEEDBACK, current + 1) }
    }

    /** Increment the Speed Trainer completed-sessions counter by 1. */
    fun recordSpeedTrainingCompleted() {
        val current = prefs.getInt(KEY_SPEED_TRAINING_SESSIONS, 0)
        prefs.edit { putInt(KEY_SPEED_TRAINING_SESSIONS, current + 1) }
    }

    /** Accumulate [seconds] into the lifetime speed trainer time total (used for Beats points). */
    fun addSpeedTrainerSeconds(seconds: Long) {
        val current = prefs.getLong(KEY_SPEED_TRAINER_SECONDS, 0L)
        prefs.edit { putLong(KEY_SPEED_TRAINER_SECONDS, current + seconds) }
    }

    /** Increment the mic-accuracy bonus sessions counter by 1. */
    fun recordMicBonusSession() {
        val current = prefs.getInt(KEY_MIC_BONUS_SESSIONS, 0)
        prefs.edit { putInt(KEY_MIC_BONUS_SESSIONS, current + 1) }
    }

    // ── Readers ───────────────────────────────────────────────────────────────

    fun metronomeSeconds(): Long    = prefs.getLong(KEY_METRONOME_SECONDS, 0L)
    fun tunerSeconds(): Long        = prefs.getLong(KEY_TUNER_SECONDS, 0L)
    fun tunerNotesLocked(): Int     = prefs.getInt(KEY_TUNER_NOTES_LOCKED, 0)
    fun gamesCompleted(): Int       = prefs.getInt(KEY_GAMES_COMPLETED, 0)
    fun totalGameScore(): Int       = prefs.getInt(KEY_GAME_SCORE_TOTAL, 0)
    fun totalPracticeMinutes(): Int = prefs.getInt(KEY_PRACTICE_MINUTES_TOTAL, 0)
    fun tunerFeedbackGiven(): Int           = prefs.getInt(KEY_TUNER_FEEDBACK, 0)
    fun speedTrainingSessionsCompleted(): Int = prefs.getInt(KEY_SPEED_TRAINING_SESSIONS, 0)
    fun speedTrainerSeconds(): Long           = prefs.getLong(KEY_SPEED_TRAINER_SECONDS, 0L)
    fun micBonusSessions(): Int              = prefs.getInt(KEY_MIC_BONUS_SESSIONS, 0)
    fun practiceSessionsCompleted(): Int = practicePrefs.getInt(KEY_PRACTICE_TOTAL, 0)
    fun daysSinceFirstLaunch(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val firstMs  = prefs.getLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis())
        val firstDay = sdf.parse(sdf.format(Date(firstMs))) ?: return 0
        val today    = sdf.parse(sdf.format(Date())) ?: return 0
        return TimeUnit.MILLISECONDS.toDays(today.time - firstDay.time).toInt() + 1
    }

    // ── Cheat / developer mode ────────────────────────────────────────────────

    fun isCheatModeEnabled(): Boolean = prefs.getBoolean(KEY_CHEAT_MODE, false)

    /** Toggle developer cheat-mode. All items appear unlocked while active. */
    fun toggleCheatMode() {
        prefs.edit { putBoolean(KEY_CHEAT_MODE, !isCheatModeEnabled()) }
    }

    // ── Purchase-based unlock override ────────────────────────────────────────

    /**
     * Permanently mark [itemId] as unlocked regardless of its time/game condition.
     * Called by the ViewModel when a purchase is confirmed or restored.
     * Idempotent — safe to call on every app launch during purchase restore.
     */
    fun forceUnlock(itemId: String) {
        val current = prefs.getStringSet(KEY_FORCE_UNLOCKED_IDS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
        if (itemId !in current) {
            current.add(itemId)
            prefs.edit { putStringSet(KEY_FORCE_UNLOCKED_IDS, current) }
        }
    }

    /** DEV: clear purchase-based unlocks (does not touch time counters or celebrations). */
    fun debugClearItemPurchases() {
        prefs.edit { remove(KEY_FORCE_UNLOCKED_IDS) }
    }

    // ── Unlock evaluation ─────────────────────────────────────────────────────

    fun isUnlocked(condition: UnlockCondition): Boolean {
        if (isCheatModeEnabled()) return true
        return when (condition) {
            is UnlockCondition.MetronomeSeconds        -> metronomeSeconds() >= condition.required
            is UnlockCondition.TunerSeconds            -> tunerSeconds() >= condition.required
            is UnlockCondition.RhythmGamesCompleted    -> gamesCompleted() >= condition.required
            is UnlockCondition.DaysSinceFirstLaunch    -> daysSinceFirstLaunch() >= condition.required
            is UnlockCondition.PracticeSessionsCompleted -> practiceSessionsCompleted() >= condition.required
            is UnlockCondition.TunerFeedbackGiven              -> tunerFeedbackGiven() >= condition.required
            is UnlockCondition.SpeedTrainingSessionsCompleted  -> speedTrainingSessionsCompleted() >= condition.required
            UnlockCondition.Always                             -> true
        }
    }

    /** Returns the set of item IDs that are currently unlocked (by time OR by purchase). */
    fun unlockedIds(registry: List<MetroItemEntry>): Set<String> {
        val forced = prefs.getStringSet(KEY_FORCE_UNLOCKED_IDS, emptySet()) ?: emptySet()
        return registry.filter { entry ->
            entry.item.id in forced || isUnlocked(entry.condition)
        }.map { it.item.id }.toSet()
    }

    // ── Celebration tracking ──────────────────────────────────────────────────

    private val KEY_CELEBRATED_IDS = "celebrated_item_ids"

    /** Mark an item's unlock popup as already shown so it never appears again. */
    fun markCelebrated(id: String) {
        val current = celebratedIds()
        prefs.edit { putStringSet(KEY_CELEBRATED_IDS, current + id) }
    }

    fun celebratedIds(): Set<String> =
        prefs.getStringSet(KEY_CELEBRATED_IDS, emptySet()) ?: emptySet()

    /** DEV: wipe all progress counters and celebrations — simulates a clean installation. */
    fun resetAllProgress() {
        prefs.edit {
            remove(KEY_METRONOME_SECONDS)
            remove(KEY_TUNER_SECONDS)
            remove(KEY_TUNER_NOTES_LOCKED)
            remove(KEY_GAMES_COMPLETED)
            remove(KEY_GAME_SCORE_TOTAL)
            remove(KEY_PRACTICE_MINUTES_TOTAL)
            remove(KEY_FIRST_LAUNCH_MS)
            remove(KEY_CELEBRATED_IDS)
            remove(KEY_TUNER_FEEDBACK)
            remove(KEY_SPEED_TRAINING_SESSIONS)
            remove(KEY_SPEED_TRAINER_SECONDS)
            remove(KEY_MIC_BONUS_SESSIONS)
            // KEY_FORCE_UNLOCKED_IDS is intentionally preserved — it reflects real purchases
        }
    }
}
