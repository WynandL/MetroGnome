package com.example.metrognome.usage

/**
 * Immutable snapshot of all raw usage counters for a single device installation.
 *
 * Design rule: only append-only counters live here — no derived scores, no booleans
 * that can flip. The score ([gnoteTotal]) is included as a convenience but is always
 * re-derivable from the counters alone, matching the event-sourcing model used by
 * [com.example.metrognome.points.PointsCalculator].
 *
 * Every field maps 1-to-1 to a SharedPreferences key owned by one of the tracker
 * classes. When a new tracking counter is added anywhere in the app, add it here too.
 *
 * Future: this class is the payload for cross-device sync. When a remote store is
 * introduced, [ActivitySummaryLogger.collect] becomes the read path and a new writer
 * handles the upload. Nothing in the points or unlock systems needs to change.
 */
data class ActivitySummary(

    /** Unix ms when this snapshot was taken. */
    val capturedAtMs: Long,

    /** Unix ms of the very first app launch on this device. */
    val firstLaunchMs: Long,

    // ── Time-based counters ───────────────────────────────────────────────────

    /** Calendar days the app was actually opened (foregrounded), per [com.example.metrognome.points.UsageDayTracker]. */
    val distinctUsageDays: Int,

    /** Raw calendar days since first install — used for item unlock conditions, not loyalty points. */
    val daysSinceInstall: Int,

    /** Cumulative seconds the metronome engine was running. */
    val metronomeSeconds: Long,

    /** Cumulative seconds the tuner was locked on a pitch. */
    val tunerSeconds: Long,

    /** Cumulative seconds spent in Speed Trainer sessions. */
    val speedTrainerSeconds: Long,

    // ── Event counters ────────────────────────────────────────────────────────

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

    // ── Derived ───────────────────────────────────────────────────────────────

    /** Current Gnote total — derived from counters above, included for convenience. */
    val gnoteTotal: Int,

    /** Number of Metro cosmetic items currently unlocked. */
    val unlockedItemCount: Int,
)
