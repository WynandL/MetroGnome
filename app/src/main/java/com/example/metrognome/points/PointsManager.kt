package com.example.metrognome.points

import android.content.Context
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
 */
class PointsManager(context: Context) {

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
        daysSinceFirstLaunch = usageDays.distinctDaysCount(),
        installedDays        = tracker.daysSinceFirstLaunch(),
        today                = dailyLog.todayActivity(tracker),
    )
}
