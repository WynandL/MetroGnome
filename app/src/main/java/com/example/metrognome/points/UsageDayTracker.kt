package com.example.metrognome.points

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Counts distinct calendar days on which the user actually opened the app.
 *
 * Call [recordDay] on every Activity resume (including from Android's app cache).
 * Only the first call per calendar day increments the counter — subsequent resumes
 * that day are no-ops.
 *
 * This is used for loyalty Gnote points instead of raw days-since-install, so that
 * users who installed the app long ago but rarely use it don't accumulate unearned loyalty.
 */
class UsageDayTracker(context: Context) {

    private val prefs      = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Record today as a usage day. Safe to call multiple times per day — idempotent. */
    fun recordDay() {
        val today = dateFormat.format(Date())
        if (prefs.getString(KEY_LAST_DATE, null) == today) return
        val newCount   = prefs.getInt(KEY_COUNT, 0) + 1
        val announced  = prefs.getStringSet(KEY_MILESTONES_ANNOUNCED, emptySet()) ?: emptySet()
        val isMilestone = newCount in MILESTONES && newCount.toString() !in announced
        prefs.edit {
            putString(KEY_LAST_DATE, today)
            putInt(KEY_COUNT, newCount)
            if (isMilestone) putStringSet(KEY_MILESTONES_ANNOUNCED, announced + newCount.toString())
        }
        if (isMilestone) PointsBannerQueue.postMilestone(newCount)
    }

    /** Total number of distinct calendar days the app has been opened. */
    fun distinctDaysCount(): Int = prefs.getInt(KEY_COUNT, 0)

    companion object {
        private const val PREFS_NAME               = "usage_days"
        private const val KEY_LAST_DATE            = "last_date"
        private const val KEY_COUNT                = "count"
        private const val KEY_MILESTONES_ANNOUNCED = "milestones_announced"
        private val MILESTONES = setOf(7, 30, 60, 100, 365)
    }
}
