package com.example.metrognome.usage

/**
 * A complete, portable snapshot of one user's local profile: every counter, record,
 * unlock and earned reward that represents their progress in the app.
 *
 * Design rules that keep this snapshot safe to capture and restore as a single unit:
 *
 *  - **Append-only or monotonic.** Counters only ever grow; records only ever rise;
 *    sets only ever gain members. Nothing here is a value that can flip back and
 *    forth, so two snapshots of the same profile always reconcile to "the larger /
 *    the union" with no ambiguity.
 *  - **Inputs, not outputs.** Everything needed to re-derive the user's Gnotes score
 *    is present as a raw counter; [gnoteTotal] is stored only for convenience and is
 *    always recomputable from the fields here (see [com.example.metrognome.points.PointsCalculator]).
 *  - **Profile state only.** Device-specific things that must NOT travel with a profile
 *    (microphone latency calibration, the audio route, dev toggles, ad-frequency
 *    counters, review-prompt state) are deliberately excluded.
 *
 * When a new piece of user progress is tracked anywhere in the app, add it here too,
 * and keep [ActivitySummaryLogger.collect] as the single place that gathers it.
 *
 * KNOWN GAP - DAILY CAPS / CROSS-DEVICE CHEAT (must address before profiles ship):
 * This snapshot carries only *lifetime* counters, not today's per-activity breakdown
 * (that lives in DailyActivityLog and is not synced). The lifetime counters merge by
 * "take the larger", which is correct for total progress but means a user active on
 * two devices on the SAME day gets a fresh daily Gnotes allowance on each - the daily
 * caps in PointsLimits are enforced per device, not per profile. With a real backend
 * this becomes an exploitable way to farm Gnotes past the intended daily limit (run
 * the cap on phone A, run it again on phone B, merge). Closing it means syncing
 * today's DailyActivity (keyed by calendar day) alongside the lifetime counters and
 * capping against the merged per-day totals, not the local ones. Do NOT forget this
 * when wiring profile sync.
 */
data class ActivitySummary(

    /** Unix ms when this snapshot was taken. */
    val capturedAtMs: Long,

    /** Unix ms of the very first app launch on this device. */
    val firstLaunchMs: Long,

    // ── Time-based counters (seconds; merge = take the larger) ─────────────────

    /** Calendar days the app was actually opened, per [com.example.metrognome.points.UsageDayTracker]. */
    val distinctUsageDays: Int,

    /** Raw calendar days since first install — used for item unlock conditions, not loyalty points. */
    val daysSinceInstall: Int,

    /** Cumulative seconds the metronome engine was running. */
    val metronomeSeconds: Long,

    /** Cumulative seconds the tuner was locked on a pitch. */
    val tunerSeconds: Long,

    /** Cumulative seconds spent in Speed Trainer sessions. */
    val speedTrainerSeconds: Long,

    // ── Event counters (merge = take the larger) ───────────────────────────────

    /** Number of individual notes the tuner has successfully locked on to. */
    val tunerNotesLocked: Int,

    /** Number of thumbs-up tuner feedback ratings submitted. */
    val tunerFeedbackGiven: Int,

    /** Number of rhythm game rounds completed. */
    val gamesCompleted: Int,

    /** Cumulative rhythm game score across all rounds. */
    val totalGameScore: Int,

    /** Total minutes of completed practice timer sessions. */
    val practiceMinutesTotal: Int,

    /** Number of practice timer sessions completed. */
    val practiceSessionsCompleted: Int,

    /** Number of Speed Trainer ramps completed end-to-end. */
    val speedTrainerSessionsCompleted: Int,

    /** Number of Speed Trainer sessions where mic accuracy earned a bonus. */
    val micBonusSessions: Int,

    /** Lifetime graded timing-bonus points (raw input to the "Timing Bonus" Gnotes). */
    val performanceBonusPoints: Int,

    /** Lifetime Gnotes earned from rewarded ads, already daily-capped at earn time
     *  (raw input to the "Ad Bonus" Gnotes). */
    val rewardedAdGnotes: Int,

    // ── Streak & personal records (merge = take the larger / the union) ────────

    /** All-time best practice streak. Monotonically non-decreasing. */
    val bestPracticeStreak: Int,

    /** Practice-day epoch days (2 AM local rollover), last 14 days. The current streak
     *  is re-derivable from this, so it is not stored separately. */
    val practicedEpochDays: Set<Long>,

    /** Rhythm game high score per difficulty name. Merge = max per key. */
    val rhythmHighScores: Map<String, Int>,

    /** Speed Trainer reached-BPM personal best per "start_target" range. Merge = max per key. */
    val speedTrainerRecords: Map<String, Int>,

    // ── Items (merge = the union) ──────────────────────────────────────────────

    /** Exact set of currently-unlocked cosmetic item IDs (earned OR purchased). */
    val unlockedItemIds: Set<String>,

    /** Item IDs whose unlock celebration popup has already been dismissed, so a
     *  restored profile never re-shows a reward the user has already seen. */
    val celebratedItemIds: Set<String>,

    // ── Earned rewards (merge = take the larger) ───────────────────────────────

    /** Unix ms until which the earned ad-free reward is active (0 = none). */
    val adFreeRewardUntilMs: Long,

    // ── Derived convenience (always recomputable from the counters above) ──────

    /** Current Gnote total — derived, included for convenience only. */
    val gnoteTotal: Int,
) {
    /** Number of cosmetic items currently unlocked. */
    val unlockedItemCount: Int get() = unlockedItemIds.size
}
