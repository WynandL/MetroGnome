package com.example.metrognome.practice

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME   = "practice_sessions"
private const val KEY_STREAK   = "streak"
private const val KEY_LAST_DAY = "last_day"
private const val KEY_TOTAL    = "total_sessions"

class PracticeSessionManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTotalSessions(): Int = prefs.getInt(KEY_TOTAL, 0)

    /** Returns the current streak, or 0 if the streak has expired (no practice in 2+ days). */
    fun getCurrentStreak(): Int {
        val today = todayKey()
        val lastDay = prefs.getLong(KEY_LAST_DAY, -1L)
        return if (lastDay >= today - 1L) prefs.getInt(KEY_STREAK, 0) else 0
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
        val newTotal = if (lastDay == today) total else total + 1

        prefs.edit {
            putInt(KEY_STREAK, newStreak)
            putLong(KEY_LAST_DAY, today)
            putInt(KEY_TOTAL, newTotal)
        }
        return newStreak
    }

    fun debugClear() = prefs.edit {
        remove(KEY_STREAK); remove(KEY_LAST_DAY); remove(KEY_TOTAL)
    }

    // Days since Unix epoch (UTC) — changes at midnight UTC; good enough for streak tracking.
    private fun todayKey(): Long = System.currentTimeMillis() / 86_400_000L
}
