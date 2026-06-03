package com.example.metrognome.review

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory

private const val PREFS_NAME = "app_review"
private const val KEY_DISTINCT_DAYS    = "distinct_days"
private const val KEY_REVIEW_REQUESTED = "review_requested" // legacy boolean — kept for migration
private const val KEY_REQUEST_COUNT    = "request_count"

private const val DAYS_THRESHOLD_FIRST  = 7   // first ask: one week of real use
private const val DAYS_THRESHOLD_SECOND = 30  // second ask: one month of real use
private const val MAX_REQUESTS = 2

/**
 * Manages the Google Play In-App Review prompt.
 *
 * Tracks distinct calendar days the app is foregrounded. Two review opportunities:
 *  1. After [DAYS_THRESHOLD_FIRST] distinct days — early engaged user.
 *  2. After [DAYS_THRESHOLD_SECOND] distinct days — established daily user.
 *
 * The Play Store's own quota manages whether the sheet actually appears after
 * [launchReviewFlow] is called — calling it twice is safe and expected.
 *
 * Migration: users who already have the legacy [KEY_REVIEW_REQUESTED]=true boolean
 * are treated as having had one request, and remain eligible for the second at day 30.
 *
 * Usage:
 *   1. Call [recordAppOpen] on every foreground entry.
 *   2. Call [maybeRequestReview] at natural earned moments — it is a no-op until eligible.
 */
class AppReviewManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reviewManager = ReviewManagerFactory.create(context)

    /**
     * Marks today as a day the app was opened.
     * Safe to call multiple times per day — only the first call per day has any effect.
     */
    fun recordAppOpen() {
        if (requestCount() >= MAX_REQUESTS) return
        val today = todayKey()
        val days = prefs.getStringSet(KEY_DISTINCT_DAYS, emptySet())!!.toMutableSet()
        if (days.add(today)) {
            prefs.edit { putStringSet(KEY_DISTINCT_DAYS, days) }
        }
    }

    /** How many distinct calendar days the app has been opened so far. */
    fun distinctDaysOpened(): Int =
        prefs.getStringSet(KEY_DISTINCT_DAYS, emptySet())!!.size

    /** How many times the review prompt has been requested. Max [MAX_REQUESTS]. */
    fun requestCount(): Int =
        if (prefs.getBoolean(KEY_REVIEW_REQUESTED, false))
            prefs.getInt(KEY_REQUEST_COUNT, 1)
        else
            prefs.getInt(KEY_REQUEST_COUNT, 0)

    /**
     * Shows the native Play Store in-app rating sheet if the user qualifies.
     * No-op when ineligible or when the Play Store quota suppresses the sheet.
     */
    fun maybeRequestReview(activity: Activity) {
        if (!isEligible()) return
        markRequested()
        reviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                reviewManager.launchReviewFlow(activity, reviewInfo)
            }
            .addOnFailureListener {
                // Play Store unavailable (emulator, sideload, no network). Skip silently.
            }
    }

    private fun isEligible(): Boolean {
        val count = requestCount()
        if (count >= MAX_REQUESTS) return false
        val days = distinctDaysOpened()
        return when (count) {
            0    -> days >= DAYS_THRESHOLD_FIRST
            1    -> days >= DAYS_THRESHOLD_SECOND
            else -> false
        }
    }

    private fun markRequested() {
        prefs.edit {
            putBoolean(KEY_REVIEW_REQUESTED, true)
            putInt(KEY_REQUEST_COUNT, requestCount() + 1)
        }
    }

    /** Days since Unix epoch (UTC) — changes at midnight UTC, suitable for day tracking. */
    private fun todayKey(): String = (System.currentTimeMillis() / 86_400_000L).toString()

    /** Wipes all stored state. For debug use only. */
    fun debugReset() {
        prefs.edit {
            remove(KEY_DISTINCT_DAYS)
            remove(KEY_REVIEW_REQUESTED)
            remove(KEY_REQUEST_COUNT)
        }
    }
}
