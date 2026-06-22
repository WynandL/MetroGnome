package com.example.metrognome.review

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory

private const val PREFS_NAME           = "app_review"
private const val KEY_LAST_OFFERED_DAY = "last_offered_day" // local-midnight epoch day we last launched the flow

// Loyalty counter (shared): distinct calendar days the app has been opened, owned by
// UsageDayTracker and also read by the ad tier system. Keys mirrored here (not imported)
// to keep the review package self-contained.
private const val USAGE_PREFS_NAME = "usage_days"
private const val USAGE_KEY_COUNT  = "count"

/** Loyalty days before the review prompt becomes eligible. */
private const val MIN_LOYALTY_DAYS = 3

/**
 * Manages the Google Play In-App Review prompt.
 *
 * Eligibility is driven by the shared loyalty counter (distinct calendar days the app
 * has been opened, the same counter that gates item unlocks, Gnotes, and the ad tier).
 * From [MIN_LOYALTY_DAYS] loyalty days onward the prompt is offered at most once per
 * calendar day.
 *
 * Why "once per day forever" rather than a fixed request limit: the Play API never
 * reports whether the user actually reviewed (by design), so we cannot stop on that
 * signal. Instead we offer daily and let Play's own quota (which throttles the sheet
 * heavily and suppresses it for users who have already engaged) taper it off
 * naturally. Most daily calls are silent no-ops that Play rate-limits; this does not
 * spam the user.
 *
 * Trigger placement is the caller's responsibility and MUST be an ad-free moment
 * (navigation to the Tuner/Settings tabs), guarded against recent ads, so a review
 * sheet can never stack with an interstitial. See [maybeRequestReview].
 */
class AppReviewManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val reviewManager = ReviewManagerFactory.create(context)

    /**
     * Offers the native Play in-app rating sheet if eligible today.
     * No-op when below [MIN_LOYALTY_DAYS], when already offered today, or when the
     * Play Store quota suppresses the sheet. Safe to call on every qualifying moment.
     */
    fun maybeRequestReview(activity: Activity) {
        if (!isEligibleToday()) return
        prefs.edit { putInt(KEY_LAST_OFFERED_DAY, todayEpochDay()) }
        reviewManager.requestReviewFlow()
            .addOnSuccessListener { reviewInfo ->
                reviewManager.launchReviewFlow(activity, reviewInfo)
            }
            .addOnFailureListener {
                // Play Store unavailable (emulator, sideload, no network). Skip silently.
            }
    }

    /** Loyalty day reached and not yet offered today. */
    private fun isEligibleToday(): Boolean {
        if (loyaltyDays() < MIN_LOYALTY_DAYS) return false
        return prefs.getInt(KEY_LAST_OFFERED_DAY, -1) != todayEpochDay()
    }

    private fun loyaltyDays(): Int =
        context.getSharedPreferences(USAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(USAGE_KEY_COUNT, 0)

    // Local-midnight epoch day, matching the day boundary used by the loyalty counter,
    // Gnotes caps, and ad frequency counter. A raw UTC day would reset mid-afternoon for
    // users with large negative UTC offsets.
    private fun todayEpochDay(): Int {
        val now = System.currentTimeMillis()
        return ((now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }

    /** Wipes all stored state. For debug use only. */
    fun debugReset() {
        prefs.edit { clear() }
    }
}
