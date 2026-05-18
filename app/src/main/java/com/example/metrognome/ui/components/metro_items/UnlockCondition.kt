package com.example.metrognome.ui.components.metro_items

/**
 * Every unlock condition is expressed as one of these sealed types.
 * MetroItemTracker evaluates each against persisted counters.
 *
 * All conditions are monotonically increasing so an unlocked item can never become
 * locked again (except via dev reset). Do NOT add conditions that can decrease
 * (e.g. current streak) — broken streaks would silently hide earned items.
 */
sealed class UnlockCondition {
    /** Total cumulative metronome play-time in seconds. */
    data class MetronomeSeconds(val required: Long) : UnlockCondition()

    /** Number of rhythm games fully completed (reached RESULT screen). */
    data class RhythmGamesCompleted(val required: Int) : UnlockCondition()

    /** Calendar days elapsed since the very first app launch. */
    data class DaysSinceFirstLaunch(val required: Int) : UnlockCondition()

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

    /** Always unlocked — used for developer preview / cheat mode. */
    object Always : UnlockCondition()
}
