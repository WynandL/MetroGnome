package com.example.metrognome.practice

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME      = "practice_sessions"
private const val KEY_STREAK      = "streak"
private const val KEY_LAST_DAY    = "last_day"
private const val KEY_TOTAL       = "total_sessions"
private const val KEY_BEST        = "best_streak"
private const val KEY_PRACTICED   = "practiced_days"

class PracticeSessionManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTotalSessions(): Int = prefs.getInt(KEY_TOTAL, 0)

    /** Current streak, or 0 if no practice in 2+ days. */
    fun getCurrentStreak(): Int {
        val today = todayKey()
        val lastDay = prefs.getLong(KEY_LAST_DAY, -1L)
        return if (lastDay >= today - 1L) prefs.getInt(KEY_STREAK, 0) else 0
    }

    /** All-time best streak. Monotonically non-decreasing. */
    fun getBestStreak(): Int = prefs.getInt(KEY_BEST, 0)

    /**
     * Epoch days (2 AM local rollover — see [currentEpochDay]) on which the user
     * completed a practice session. Pruned to the last 14 days so the set stays small.
     */
    fun getPracticedEpochDays(): Set<Long> {
        val raw = prefs.getString(KEY_PRACTICED, "") ?: ""
        return if (raw.isEmpty()) emptySet()
        else raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    /** Records a completed session. Returns the updated streak count. */
    fun recordSession(): Int {
        val today   = todayKey()
        val lastDay = prefs.getLong(KEY_LAST_DAY, -1L)
        val streak  = prefs.getInt(KEY_STREAK, 0)
        val total   = prefs.getInt(KEY_TOTAL, 0)

        val newStreak = when (lastDay) {
            today     -> streak      // already practised today — don't increment
            today - 1 -> streak + 1  // consecutive day — extend streak
            else      -> 1           // first session ever, or streak broken
        }
        val newTotal  = if (lastDay == today) total else total + 1
        val newBest   = maxOf(prefs.getInt(KEY_BEST, 0), newStreak)

        val days = getPracticedEpochDays().toMutableSet().also {
            it.add(today)
            it.removeAll { day -> day < today - 14 }
        }

        prefs.edit {
            putInt(KEY_STREAK, newStreak)
            putLong(KEY_LAST_DAY, today)
            putInt(KEY_TOTAL, newTotal)
            putInt(KEY_BEST, newBest)
            putString(KEY_PRACTICED, days.joinToString(","))
        }
        return newStreak
    }

    fun debugClear() = prefs.edit {
        remove(KEY_STREAK); remove(KEY_LAST_DAY); remove(KEY_TOTAL)
        remove(KEY_BEST);   remove(KEY_PRACTICED)
    }

    private fun todayKey(): Long = currentEpochDay()

    companion object {
        /**
         * The app-wide practice-day definition: a local epoch day with a 2 AM rollover,
         * so sessions before 2 AM count toward the previous day. Shift back 2 hours, add
         * the local timezone offset, floor to days — a 2 AM local rollover for every
         * timezone without a separate lookup.
         *
         * This is the single source of truth for "what practice day is it"; any UI that
         * compares against [getPracticedEpochDays] MUST use this, not a raw UTC epoch day.
         */
        fun currentEpochDay(): Long {
            val shiftedMs = System.currentTimeMillis() - 2L * 60 * 60 * 1000
            val tz        = java.util.TimeZone.getDefault()
            return (shiftedMs + tz.getOffset(shiftedMs)) / 86_400_000L
        }
    }
}
