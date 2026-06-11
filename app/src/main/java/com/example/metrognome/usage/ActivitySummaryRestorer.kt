package com.example.metrognome.usage

import android.content.Context
import androidx.core.content.edit
import com.example.metrognome.practice.PracticeSessionManager
import kotlin.math.max
import kotlin.math.min

/**
 * The write-back counterpart to [ActivitySummaryLogger]: applies a saved
 * [ActivitySummary] back into the app's local storage.
 *
 * It MERGES rather than overwrites. Every value in an [ActivitySummary] is
 * append-only or monotonic, so a saved snapshot and the current local state always
 * reconcile cleanly by keeping the furthest progress: the larger number for every
 * counter and record, the union for every set, the earliest first-launch. A profile
 * can therefore be restored at any time without ever rolling a value backwards or
 * losing progress made since the snapshot was taken.
 *
 * Deliberately the single, self-contained place that holds the write path, kept
 * apart from the read path so the two are easy to track and reason about
 * independently. Like the logger, it reaches into each owning store's
 * SharedPreferences directly (owner named in a comment) so no store needs a public
 * setter that would otherwise let a counter be lowered.
 *
 * What it intentionally does NOT write:
 *  - [ActivitySummary.unlockedItemIds] — earned items re-derive automatically from the
 *    restored counters, and purchased items restore through Google Play; writing the
 *    raw set would blur the earned/purchased distinction.
 *  - Anything device-specific (mic calibration, ad-frequency counters, etc.) — those
 *    are not in [ActivitySummary] by design.
 *
 * Call this when no session is active (e.g. at app start, before the UI reads state).
 */
class ActivitySummaryRestorer(private val context: Context) {

    /**
     * Merge [incoming] with the current local profile, write the result, and return
     * the merged snapshot. Safe to call repeatedly — merging is idempotent.
     */
    fun restore(incoming: ActivitySummary): ActivitySummary {
        val local  = ActivitySummaryLogger(context).collect()
        val merged = mergeSummaries(local, incoming)
        apply(merged)
        return merged
    }

    // ── Apply the merged snapshot to each owning store ─────────────────────────

    private fun apply(s: ActivitySummary) {
        // metro_cosmetics — owned by MetroItemTracker.
        context.getSharedPreferences("metro_cosmetics", Context.MODE_PRIVATE).edit {
            putLong("metronome_seconds",        s.metronomeSeconds)
            putLong("tuner_seconds",            s.tunerSeconds)
            putLong("speed_trainer_seconds",    s.speedTrainerSeconds)
            putInt("tuner_notes_locked",        s.tunerNotesLocked)
            putInt("games_completed",           s.gamesCompleted)
            putInt("game_score_total",          s.totalGameScore)
            putInt("practice_minutes_total",    s.practiceMinutesTotal)
            putInt("tuner_feedback_count",      s.tunerFeedbackGiven)
            putInt("speed_training_sessions",   s.speedTrainerSessionsCompleted)
            putInt("mic_bonus_sessions",        s.micBonusSessions)
            putInt("performance_bonus_points",  s.performanceBonusPoints)
            putLong("first_launch_ms",          s.firstLaunchMs)
            putStringSet("celebrated_item_ids", s.celebratedItemIds)
        }

        // practice_sessions — owned by PracticeSessionManager (total_sessions mirrored by MetroItemTracker).
        // Streak and last-day are written non-lowering: the practiced-days set is pruned to ~14 days,
        // so a longer existing streak must never be shrunk by re-deriving from the (shorter) set.
        run {
            val days   = s.practicedEpochDays
            val prefs  = context.getSharedPreferences("practice_sessions", Context.MODE_PRIVATE)
            val streak = max(prefs.getInt("streak", 0), currentStreakFrom(days))
            val lastDay = max(prefs.getLong("last_day", -1L), days.maxOrNull() ?: -1L)
            prefs.edit {
                putInt("best_streak",    s.bestPracticeStreak)
                putInt("total_sessions", s.practiceSessionsCompleted)
                putString("practiced_days", days.sorted().joinToString(","))
                putInt("streak", streak)
                if (lastDay >= 0) putLong("last_day", lastDay)
            }
        }

        // rhythm_highscores — owned by RhythmGameViewModel ("hs_<difficulty>").
        context.getSharedPreferences("rhythm_highscores", Context.MODE_PRIVATE).edit {
            s.rhythmHighScores.forEach { (name, score) -> putInt("hs_$name", score) }
        }

        // speed_trainer — owned by SpeedTrainerPrefs ("reached_<start>_<target>").
        context.getSharedPreferences("speed_trainer", Context.MODE_PRIVATE).edit {
            s.speedTrainerRecords.forEach { (range, bpm) -> putInt("reached_$range", bpm) }
        }

        // points_rewards — owned by RewardManager.
        context.getSharedPreferences("points_rewards", Context.MODE_PRIVATE).edit {
            putLong("ad_free_until_ms", s.adFreeRewardUntilMs)
        }

        // rewarded_ad_manager — owned by RewardedAdManager.
        context.getSharedPreferences("rewarded_ad_manager", Context.MODE_PRIVATE).edit {
            putInt("lifetime_gnotes", s.rewardedAdGnotes)
        }

        // usage_days — owned by UsageDayTracker. Mark every passed milestone as already
        // announced so restoring a high day-count never replays old milestone banners.
        context.getSharedPreferences("usage_days", Context.MODE_PRIVATE).edit {
            putInt("count", s.distinctUsageDays)
            putStringSet(
                "milestones_announced",
                USAGE_MILESTONES.filter { it <= s.distinctUsageDays }.map { it.toString() }.toSet(),
            )
        }
    }

    private companion object {
        // Mirrors UsageDayTracker.MILESTONES (private there). Keep in sync.
        val USAGE_MILESTONES = listOf(7, 30, 60, 100, 365)
    }
}

/**
 * Pure reconciliation of two profile snapshots into one that dominates both: the
 * furthest progress wins for every field. No Android dependencies, so it is trivially
 * testable and could run anywhere the same [ActivitySummary] type is available.
 */
internal fun mergeSummaries(local: ActivitySummary, incoming: ActivitySummary): ActivitySummary =
    ActivitySummary(
        capturedAtMs                  = max(local.capturedAtMs, incoming.capturedAtMs),
        firstLaunchMs                 = min(local.firstLaunchMs, incoming.firstLaunchMs),
        distinctUsageDays             = max(local.distinctUsageDays, incoming.distinctUsageDays),
        daysSinceInstall              = max(local.daysSinceInstall, incoming.daysSinceInstall),
        metronomeSeconds              = max(local.metronomeSeconds, incoming.metronomeSeconds),
        tunerSeconds                  = max(local.tunerSeconds, incoming.tunerSeconds),
        speedTrainerSeconds           = max(local.speedTrainerSeconds, incoming.speedTrainerSeconds),
        tunerNotesLocked              = max(local.tunerNotesLocked, incoming.tunerNotesLocked),
        tunerFeedbackGiven            = max(local.tunerFeedbackGiven, incoming.tunerFeedbackGiven),
        gamesCompleted                = max(local.gamesCompleted, incoming.gamesCompleted),
        totalGameScore                = max(local.totalGameScore, incoming.totalGameScore),
        practiceMinutesTotal          = max(local.practiceMinutesTotal, incoming.practiceMinutesTotal),
        practiceSessionsCompleted     = max(local.practiceSessionsCompleted, incoming.practiceSessionsCompleted),
        speedTrainerSessionsCompleted = max(local.speedTrainerSessionsCompleted, incoming.speedTrainerSessionsCompleted),
        micBonusSessions              = max(local.micBonusSessions, incoming.micBonusSessions),
        performanceBonusPoints        = max(local.performanceBonusPoints, incoming.performanceBonusPoints),
        rewardedAdGnotes              = max(local.rewardedAdGnotes, incoming.rewardedAdGnotes),
        bestPracticeStreak            = max(local.bestPracticeStreak, incoming.bestPracticeStreak),
        practicedEpochDays            = local.practicedEpochDays + incoming.practicedEpochDays,
        rhythmHighScores              = mergeMaxByKey(local.rhythmHighScores, incoming.rhythmHighScores),
        speedTrainerRecords           = mergeMaxByKey(local.speedTrainerRecords, incoming.speedTrainerRecords),
        unlockedItemIds               = local.unlockedItemIds + incoming.unlockedItemIds,
        celebratedItemIds             = local.celebratedItemIds + incoming.celebratedItemIds,
        adFreeRewardUntilMs           = max(local.adFreeRewardUntilMs, incoming.adFreeRewardUntilMs),
        gnoteTotal                    = max(local.gnoteTotal, incoming.gnoteTotal),
    )

/** Per-key maximum over the union of both maps' keys. */
private fun mergeMaxByKey(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> =
    (a.keys + b.keys).associateWith { key -> max(a[key] ?: 0, b[key] ?: 0) }

/**
 * The current practice streak implied by a set of practiced epoch days: consecutive
 * days counting back from today (or yesterday, if today has no session yet). Matches
 * [PracticeSessionManager.getCurrentStreak] so a restored streak reads correctly.
 */
private fun currentStreakFrom(days: Set<Long>): Int {
    if (days.isEmpty()) return 0
    val today = PracticeSessionManager.currentEpochDay()
    var anchor = when {
        today in days     -> today
        today - 1 in days -> today - 1
        else              -> return 0
    }
    var streak = 0
    while (anchor in days) {
        streak++
        anchor--
    }
    return streak
}
