package com.example.metrognome.usage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.metrognome.points.PointsManager
import com.example.metrognome.points.UsageDayTracker
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemTracker

/**
 * Collects all usage counters into an [ActivitySummary] and writes it to logcat.
 *
 * Intended call site: every app foreground (via [com.example.metrognome.viewmodel.MetronomeViewModel.recordUsageDay]).
 * Running on the main thread is safe — all reads are in-process SharedPreferences lookups.
 *
 * Future: replace or extend [log] with an upload function when cross-device sync is
 * introduced. The [collect] function and [ActivitySummary] structure stay unchanged.
 */
class ActivitySummaryLogger(context: Context) {

    private val tracker       = MetroItemTracker(context)
    private val usageDays     = UsageDayTracker(context)
    private val pointsManager = PointsManager(context)
    private val cosmeticsPrefs: SharedPreferences =
        context.getSharedPreferences("metro_cosmetics", Context.MODE_PRIVATE)

    fun collect(): ActivitySummary {
        val snapshot = pointsManager.getSnapshot()
        return ActivitySummary(
            capturedAtMs                  = System.currentTimeMillis(),
            firstLaunchMs                 = firstLaunchMs(),
            distinctUsageDays             = usageDays.distinctDaysCount(),
            daysSinceInstall              = tracker.daysSinceFirstLaunch(),
            metronomeSeconds              = tracker.metronomeSeconds(),
            tunerSeconds                  = tracker.tunerSeconds(),
            speedTrainerSeconds           = tracker.speedTrainerSeconds(),
            tunerNotesLocked              = tracker.tunerNotesLocked(),
            tunerFeedbackGiven            = tracker.tunerFeedbackGiven(),
            gamesCompleted                = tracker.gamesCompleted(),
            totalGameScore                = tracker.totalGameScore(),
            practiceMinutesTotal          = tracker.totalPracticeMinutes(),
            practiceSessionsCompleted     = tracker.practiceSessionsCompleted(),
            speedTrainerSessionsCompleted = tracker.speedTrainingSessionsCompleted(),
            micBonusSessions              = tracker.micBonusSessions(),
            gnoteTotal                    = snapshot.total,
            unlockedItemCount             = tracker.unlockedIds(METRO_ITEM_REGISTRY).size,
        )
    }

    fun log() {
        val s = collect()
        Log.d(TAG, buildString {
            appendLine("=== Activity Summary ===")
            appendLine("captured   : ${s.capturedAtMs}")
            appendLine("usage days : ${s.distinctUsageDays}  (install days: ${s.daysSinceInstall})")
            appendLine("metronome  : ${s.metronomeSeconds}s")
            appendLine("tuner      : ${s.tunerSeconds}s  notes: ${s.tunerNotesLocked}  feedback: ${s.tunerFeedbackGiven}")
            appendLine("game       : ${s.gamesCompleted} rounds  score: ${s.totalGameScore}")
            appendLine("practice   : ${s.practiceMinutesTotal}min  sessions: ${s.practiceSessionsCompleted}")
            appendLine("speed      : ${s.speedTrainerSeconds}s  sessions: ${s.speedTrainerSessionsCompleted}  mic bonus: ${s.micBonusSessions}")
            appendLine("gnotes     : ${s.gnoteTotal}  items: ${s.unlockedItemCount}")
            append("=======================")
        })
    }

    private fun firstLaunchMs(): Long =
        cosmeticsPrefs.getLong("first_launch_ms", System.currentTimeMillis())

    companion object {
        private const val TAG = "ActivitySummary"
    }
}
