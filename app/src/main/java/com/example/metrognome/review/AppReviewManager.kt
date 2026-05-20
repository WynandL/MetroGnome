package com.example.metrognome.review

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory

private const val PREFS_NAME = "app_review"
private const val KEY_DISTINCT_DAYS = "distinct_days"
private const val KEY_REVIEW_REQUESTED = "review_requested"

/** Number of distinct calendar days the app must be opened before the prompt appears. */
private const val DAYS_THRESHOLD = 3

/**
 * Manages the Google Play In-App Review prompt.
 *
 * Tracks distinct calendar days the app is foregrounded. Once the user has opened
 * the app on [DAYS_THRESHOLD] or more separate days, the native Play rating sheet
 * is shown once and never again.
 *
 * Usage:
 *   1. Call [recordAppOpen] on every foreground entry (Activity.onResume / ON_RESUME).
 *   2. Call [maybeRequestReview] right after — it is a no-op until eligible.
 */
class AppReviewManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reviewManager = ReviewManagerFactory.create(context)

    /**
     * Marks today as a day the app was opened.
     * Safe to call multiple times per day — only the first call per day has any effect.
     */
    fun recordAppOpen() {
        if (hasBeenRequested()) return
        val today = todayKey()
        val days = prefs.getStringSet(KEY_DISTINCT_DAYS, emptySet())!!.toMutableSet()
        if (days.add(today)) {
            prefs.edit { putStringSet(KEY_DISTINCT_DAYS, days) }
        }
    }

    /** How many distinct calendar days the app has been opened so far. */
    fun distinctDaysOpened(): Int =
        prefs.getStringSet(KEY_DISTINCT_DAYS, emptySet())!!.size

    /**
     * Shows the native Play Store in-app rating sheet if the user qualifies.
     *
     * Exits immediately when:
     *  - fewer than [DAYS_THRESHOLD] distinct days have been recorded, or
     *  - the prompt has already been triggered once.
     *
     * All Play Store errors are swallowed so this never causes a crash.
     * The sheet is shown by the OS; the app cannot force a specific outcome.
     */
    fun maybeRequestReview(activity: Activity) {
        if (!isEligible()) return
        // Mark immediately — prevents re-triggering if the activity is recreated mid-flow.
        markRequested()
        reviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                reviewManager.launchReviewFlow(activity, reviewInfo)
            }
            .addOnFailureListener {
                // Play Store unavailable (emulator, sideload, no network). Skip silently.
            }
    }

    private fun isEligible(): Boolean =
        !hasBeenRequested() && distinctDaysOpened() >= DAYS_THRESHOLD

    private fun hasBeenRequested(): Boolean =
        prefs.getBoolean(KEY_REVIEW_REQUESTED, false)

    private fun markRequested() =
        prefs.edit { putBoolean(KEY_REVIEW_REQUESTED, true) }

    /** Days since Unix epoch (UTC) — changes at midnight UTC, suitable for day tracking. */
    private fun todayKey(): String = (System.currentTimeMillis() / 86_400_000L).toString()

    /** Wipes all stored state. For debug use only — call from a dev-tools menu. */
    fun debugReset() {
        prefs.edit {
            remove(KEY_DISTINCT_DAYS)
            remove(KEY_REVIEW_REQUESTED)
        }
    }
}
