package com.example.metrognome.ui.components.metro_items

import android.content.Context
import androidx.core.content.edit
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

    private val prefs = context.getSharedPreferences("metro_cosmetics", Context.MODE_PRIVATE)

    // ── Counter keys ──────────────────────────────────────────────────────────

    companion object {
        private const val KEY_METRONOME_SECONDS  = "metronome_seconds"
        private const val KEY_GAMES_COMPLETED    = "games_completed"
        private const val KEY_FIRST_LAUNCH_MS    = "first_launch_ms"
        private const val KEY_CHEAT_MODE         = "cheat_mode"
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

    /** Increment the completed-games counter by 1. */
    fun recordGameCompleted() {
        val current = prefs.getInt(KEY_GAMES_COMPLETED, 0)
        prefs.edit { putInt(KEY_GAMES_COMPLETED, current + 1) }
    }

    // ── Readers ───────────────────────────────────────────────────────────────

    fun metronomeSeconds(): Long = prefs.getLong(KEY_METRONOME_SECONDS, 0L)
    fun gamesCompleted(): Int    = prefs.getInt(KEY_GAMES_COMPLETED, 0)
    fun daysSinceFirstLaunch(): Int {
        val firstMs = prefs.getLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis())
        val elapsedMs = System.currentTimeMillis() - firstMs
        return TimeUnit.MILLISECONDS.toDays(elapsedMs).toInt()
    }

    // ── Cheat / developer mode ────────────────────────────────────────────────

    fun isCheatModeEnabled(): Boolean = prefs.getBoolean(KEY_CHEAT_MODE, false)

    /** Toggle developer cheat-mode. All items appear unlocked while active. */
    fun toggleCheatMode() {
        prefs.edit { putBoolean(KEY_CHEAT_MODE, !isCheatModeEnabled()) }
    }

    // ── Unlock evaluation ─────────────────────────────────────────────────────

    fun isUnlocked(condition: UnlockCondition): Boolean {
        if (isCheatModeEnabled()) return true
        return when (condition) {
            is UnlockCondition.MetronomeSeconds    -> metronomeSeconds() >= condition.required
            is UnlockCondition.RhythmGamesCompleted -> gamesCompleted() >= condition.required
            is UnlockCondition.DaysSinceFirstLaunch -> daysSinceFirstLaunch() >= condition.required
            UnlockCondition.Always                  -> true
        }
    }

    /** Returns the set of item IDs that are currently unlocked. */
    fun unlockedIds(registry: List<MetroItemEntry>): Set<String> =
        registry.filter { isUnlocked(it.condition) }.map { it.item.id }.toSet()

    // ── Celebration tracking ──────────────────────────────────────────────────

    private val KEY_CELEBRATED_IDS = "celebrated_item_ids"

    /** Mark an item's unlock popup as already shown so it never appears again. */
    fun markCelebrated(id: String) {
        val current = celebratedIds()
        prefs.edit { putStringSet(KEY_CELEBRATED_IDS, current + id) }
    }

    fun celebratedIds(): Set<String> =
        prefs.getStringSet(KEY_CELEBRATED_IDS, emptySet()) ?: emptySet()

    /** DEV: wipe all progress counters and celebrations — simulates a clean install. */
    fun resetAllProgress() {
        prefs.edit {
            remove(KEY_METRONOME_SECONDS)
            remove(KEY_GAMES_COMPLETED)
            remove(KEY_FIRST_LAUNCH_MS)
            remove(KEY_CELEBRATED_IDS)
        }
    }
}
