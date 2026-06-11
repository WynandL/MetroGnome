package com.example.metrognome.points

import com.example.metrognome.analytics.AnalyticsTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What the Gnotes banner displays after an activity is completed.
 *
 * [pointsEarned] is 0 when the daily limit was already exceeded before this
 * completion. [limitJustReached] is true when this completion was the exact
 * unit that hit the daily cap (a special message is shown in that case).
 */
data class PointsBannerData(
    val pointsEarned: Int,
    val activityLabel: String,
    val todayCount: Int,
    val dailyLimit: Int,
    val limitJustReached: Boolean,
)

/**
 * App-wide bus for Gnotes earned notifications.
 *
 * ViewModels call [postActivity] immediately after recording a completion.
 * The UI collects [events] and shows a transient slide-in banner.
 * No Android context required — safe to call from any coroutine or thread.
 */
object PointsBannerQueue {

    private val _events = MutableSharedFlow<PointsBannerData>(extraBufferCapacity = 3)
    val events: SharedFlow<PointsBannerData> = _events.asSharedFlow()

    private val _milestones = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val milestones: SharedFlow<Int> = _milestones.asSharedFlow()

    fun postMilestone(days: Int) {
        AnalyticsTracker.logLoyaltyMilestone(days)
        _milestones.tryEmit(days)
    }

    /**
     * Build and post a banner event.
     *
     * @param label        Display name for the activity ("Rhythm Game", "Practice", etc.)
     * @param pointsPerUnit Points awarded for one unit of this activity (from [PointsConfig])
     * @param todayCount   How many units of this activity were completed today, INCLUDING
     *                     the one that just triggered this call.
     * @param dailyLimit   The cap for this activity from [PointsLimits].
     */
    fun postActivity(label: String, pointsPerUnit: Int, todayCount: Int, dailyLimit: Int) {
        post(
            PointsBannerData(
                pointsEarned     = if (todayCount > dailyLimit) 0 else pointsPerUnit,
                activityLabel    = label,
                todayCount       = todayCount.coerceAtMost(dailyLimit),
                dailyLimit       = dailyLimit,
                limitJustReached = todayCount == dailyLimit,
            )
        )
    }

    /**
     * Post a fully-constructed banner — use when earning is delta-based (e.g. per-minute activities).
     * Central choke point for every Gnotes-earning banner, so analytics is logged once here.
     */
    fun post(data: PointsBannerData) {
        AnalyticsTracker.logGnotesEarned(
            activity     = data.activityLabel,
            amount       = data.pointsEarned,
            todayTotal   = data.todayCount,
            dailyLimit   = data.dailyLimit,
            limitReached = data.limitJustReached,
        )
        _events.tryEmit(data)
    }
}
