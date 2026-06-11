package com.example.metrognome.points

import android.content.Context
import com.example.metrognome.ads.RewardedAdManager
import com.example.metrognome.ui.components.metro_items.MetroItemTracker

/**
 * Reads all usage counters from [MetroItemTracker]'s SharedPreferences, derives
 * today's activity from [DailyActivityLog], and returns a freshly computed
 * [PointsSnapshot] with daily caps applied.
 *
 * Never caches — every call reads live prefs so the displayed total is always
 * current. All reads are synchronous in-process SharedPrefs lookups.
 *
 * Future extension: replace [getSnapshot] with a suspend function that merges
 * local prefs with a remote counter document before calculating, maintaining
 * the same contract without touching the calculator or UI layer.
 *
 * [rewardedAds] defaults to a fresh, lightweight instance (its constructor only
 * opens SharedPreferences — no ad loading) so the rewarded-ad "Ad Bonus" is always
 * included in the total. A caller that already has a preloaded manager can pass it,
 * but no caller can accidentally omit it and under-count the score.
 */
class PointsManager(
    context: Context,
    private val rewardedAds: RewardedAdManager = RewardedAdManager(context),
) {

    private val tracker   = MetroItemTracker(context)
    private val dailyLog  = DailyActivityLog(context)
    private val usageDays = UsageDayTracker(context)

    fun getSnapshot(): PointsSnapshot = PointsCalculator.calculate(
        metronomeSeconds     = tracker.metronomeSeconds(),
        tunerNotesLocked     = tracker.tunerNotesLocked(),
        totalGameScore       = tracker.totalGameScore(),
        totalPracticeMinutes = tracker.totalPracticeMinutes(),
        speedTrainerSeconds  = tracker.speedTrainerSeconds(),
        tunerFeedbackGiven   = tracker.tunerFeedbackGiven(),
        micBonusSessions     = tracker.micBonusSessions(),
        performanceBonusPoints = tracker.performanceBonusPoints(),
        loyaltyActiveDays = usageDays.distinctDaysCount(),
        installedDays     = tracker.daysSinceFirstLaunch(),
        today                = dailyLog.todayActivity(tracker),
        rewardedAdGnotes     = rewardedAds.lifetimeGnotes(),
    )
}
