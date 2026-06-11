package com.example.metrognome.usage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.metrognome.points.PointsManager
import com.example.metrognome.points.UsageDayTracker
import com.example.metrognome.practice.PracticeSessionManager
import com.example.metrognome.speedtrainer.SpeedTrainerPrefs
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemTracker

/**
 * Gathers every piece of the user's local profile into a single [ActivitySummary].
 *
 * This is the one place that knows where each counter, record, unlock and reward
 * lives, so the rest of the app never has to. [collect] is the read path; nothing
 * here writes. Running on the main thread is safe — all reads are in-process
 * SharedPreferences lookups.
 *
 * A few values are read straight from their owning store's SharedPreferences (with
 * the owner named in a comment) rather than constructing that store, to keep this a
 * lightweight, side-effect-free read.
 */
class ActivitySummaryLogger(context: Context) {

    private val tracker         = MetroItemTracker(context)
    private val usageDays       = UsageDayTracker(context)
    private val pointsManager   = PointsManager(context)
    private val practiceManager = PracticeSessionManager(context)
    private val speedTrainer    = SpeedTrainerPrefs(context)

    private val cosmeticsPrefs: SharedPreferences =
        context.getSharedPreferences("metro_cosmetics", Context.MODE_PRIVATE)
    private val rhythmPrefs: SharedPreferences =
        context.getSharedPreferences("rhythm_highscores", Context.MODE_PRIVATE)
    // Owned by RewardedAdManager (key "lifetime_gnotes") and RewardManager (key "ad_free_until_ms").
    private val rewardedAdPrefs: SharedPreferences =
        context.getSharedPreferences("rewarded_ad_manager", Context.MODE_PRIVATE)
    private val rewardPrefs: SharedPreferences =
        context.getSharedPreferences("points_rewards", Context.MODE_PRIVATE)

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
            performanceBonusPoints        = tracker.performanceBonusPoints(),
            rewardedAdGnotes              = rewardedAdPrefs.getInt("lifetime_gnotes", 0),
            bestPracticeStreak            = practiceManager.getBestStreak(),
            practicedEpochDays            = practiceManager.getPracticedEpochDays(),
            rhythmHighScores              = rhythmHighScores(),
            speedTrainerRecords           = speedTrainer.allReachedRecords(),
            unlockedItemIds               = tracker.unlockedIds(METRO_ITEM_REGISTRY),
            celebratedItemIds             = tracker.celebratedIds(),
            adFreeRewardUntilMs           = rewardPrefs.getLong("ad_free_until_ms", 0L),
            gnoteTotal                    = snapshot.total,
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
            appendLine("game       : ${s.gamesCompleted} rounds  score: ${s.totalGameScore}  highs: ${s.rhythmHighScores}")
            appendLine("practice   : ${s.practiceMinutesTotal}min  sessions: ${s.practiceSessionsCompleted}  best streak: ${s.bestPracticeStreak}  days: ${s.practicedEpochDays.size}")
            appendLine("speed      : ${s.speedTrainerSeconds}s  sessions: ${s.speedTrainerSessionsCompleted}  records: ${s.speedTrainerRecords.size}")
            appendLine("bonus pts  : timing ${s.performanceBonusPoints}  ad ${s.rewardedAdGnotes}")
            appendLine("rewards    : ad-free until ${s.adFreeRewardUntilMs}")
            appendLine("items      : ${s.unlockedItemCount} unlocked  ${s.celebratedItemIds.size} celebrated")
            appendLine("gnotes     : ${s.gnoteTotal}")
            append("=======================")
        })
    }

    private fun firstLaunchMs(): Long =
        cosmeticsPrefs.getLong("first_launch_ms", System.currentTimeMillis())

    /** Rhythm high scores keyed by difficulty name (the "hs_" prefix is stripped). */
    private fun rhythmHighScores(): Map<String, Int> =
        rhythmPrefs.all.entries
            .filter { it.key.startsWith("hs_") && it.value is Int }
            .associate { it.key.removePrefix("hs_") to (it.value as Int) }

    companion object {
        private const val TAG = "ActivitySummary"
    }
}
