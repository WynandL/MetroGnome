package com.example.metrognome.ui.components.metro_items

/**
 * Every unlock condition is expressed as one of these sealed types.
 * MetroItemTracker evaluates each against persisted counters.
 *
 * All conditions are monotonically increasing so an unlocked item can never become
 * locked again (except via dev reset). Do NOT add conditions that can decrease
 * (e.g. current streak) — broken streaks would silently hide earned items.
 *
 * ## Adding a variant: four files, and two of them will not remind you
 *
 * A new entry here needs a counter in `MetroItemTracker` (writer, reader, an `isUnlocked`
 * branch and a line in `resetAllProgress`), a branch in [displayText] below, and then:
 *
 *  - `points/ConditionPoints.kt` — `pointsEquivalent()` and `pointsDisplayText()`
 *  - `ui/components/metro_items/UnlockProgress.kt` — `unlockProgress()` and `progressLabel()`
 *
 * **`UnlockProgress.kt` is the one that gets missed** (it was, when `DroneSeconds` was
 * added). Nothing on the feature side references it, so it never turns up in a search for
 * the thing being built; it drives the progress bars in the collection, which is a screen
 * away from whatever you are working on. Both files are exhaustive `when` blocks on this
 * sealed class, so **the compiler is the safety net** and the build will fail until each is
 * handled. If you are adding a condition, expect those two errors and welcome them.
 */
sealed class UnlockCondition {
    /** Total cumulative metronome play-time in seconds. */
    data class MetronomeSeconds(val required: Long) : UnlockCondition()

    /** Number of rhythm games fully completed (reached RESULT screen). */
    data class RhythmGamesCompleted(val required: Int) : UnlockCondition()

    /** Calendar days elapsed since the very first app launch. */
    data class DaysSinceFirstLaunch(val required: Int) : UnlockCondition()

    /** Distinct calendar days on which the user actually opened the app (loyalty days). */
    data class LoyaltyDays(val required: Int) : UnlockCondition()

    /**
     * Total number of practice sessions ever completed (requires Practice Mode purchased).
     * Monotonically increasing — safe for permanent item unlocks.
     * Use this, not streak, for items: a broken streak would hide an earned item.
     */
    data class PracticeSessionsCompleted(val required: Int) : UnlockCondition()

    /**
     * Number of thumbs-up tuner feedback submissions.
     * Only positive feedback counts — the reward is for helping improve the tuner.
     */
    data class TunerFeedbackGiven(val required: Int) : UnlockCondition()

    /** Total cumulative tuner listening time in seconds (mic active). */
    data class TunerSeconds(val required: Long) : UnlockCondition()

    /** Total cumulative time the tuning drone has been sounding, in seconds. */
    data class DroneSeconds(val required: Long) : UnlockCondition()

    /**
     * Number of Speed Trainer sessions fully completed (reached the result screen).
     * Monotonically increasing — safe for permanent item unlocks.
     */
    data class SpeedTrainingSessionsCompleted(val required: Int) : UnlockCondition()

    /**
     * Number of microphone "Groove Check" runs that resolved to an outcome (any verdict
     * — PASS, FAIL, or fixable ABORT). Rewards the player for trying the check at all,
     * which also surfaces the otherwise-buried feature. Monotonically increasing.
     */
    data class MicChecksCompleted(val required: Int) : UnlockCondition()

    /** Always unlocked — used for developer preview / cheat mode. */
    object Always : UnlockCondition()
}

/**
 * Human-readable unlock requirement derived directly from the condition data.
 *
 * This is the single source of truth for "how to unlock" text shown in the
 * UnlockCelebrationOverlay and the Settings dev rules dialog. It cannot drift
 * from the registry because it IS computed from the registry value — if you
 * change the threshold in the registry, the displayed text updates automatically.
 *
 * Do NOT put unlock requirement text in MetroItem. The unlockCondition property
 * was removed from that interface precisely to prevent two sources diverging.
 */
fun UnlockCondition.displayText(): String = when (this) {
    is UnlockCondition.MetronomeSeconds -> {
        val mins = required / 60
        val hrs  = mins / 60
        if (hrs >= 1L && mins % 60 == 0L)
            "$hrs hour${if (hrs > 1L) "s" else ""} of metronome use"
        else
            "$mins minutes of metronome use"
    }
    is UnlockCondition.TunerSeconds -> {
        val mins = required / 60
        val hrs  = mins / 60
        if (hrs >= 1L && mins % 60 == 0L)
            "$hrs hour${if (hrs > 1L) "s" else ""} of tuner use"
        else
            "$mins minutes of tuner use"
    }
    is UnlockCondition.DroneSeconds -> {
        val mins = required / 60
        val hrs  = mins / 60
        if (hrs >= 1L && mins % 60 == 0L)
            "$hrs hour${if (hrs > 1L) "s" else ""} of drone"
        else
            "$mins minutes of drone"
    }
    is UnlockCondition.RhythmGamesCompleted ->
        "Complete $required rhythm game${if (required != 1) "s" else ""}"
    is UnlockCondition.DaysSinceFirstLaunch ->
        "Use the app for $required day${if (required != 1) "s" else ""}"
    is UnlockCondition.LoyaltyDays ->
        "Open the app on $required different day${if (required != 1) "s" else ""}"
    is UnlockCondition.PracticeSessionsCompleted ->
        "Complete $required practice session${if (required != 1) "s" else ""}"
    is UnlockCondition.TunerFeedbackGiven ->
        if (required == 1) "Submit a tuner feedback rating"
        else "Submit $required tuner feedback ratings"
    is UnlockCondition.SpeedTrainingSessionsCompleted ->
        "Complete $required speed training session${if (required != 1) "s" else ""}"
    is UnlockCondition.MicChecksCompleted ->
        if (required == 1) "Run a microphone Groove Check"
        else "Run $required microphone Groove Checks"
    UnlockCondition.Always -> "Always available"
}
