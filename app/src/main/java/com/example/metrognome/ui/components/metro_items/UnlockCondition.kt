package com.example.metrognome.ui.components.metro_items

/**
 * Every unlock condition is expressed as one of these sealed types.
 * MetroItemTracker evaluates each against persisted counters.
 */
sealed class UnlockCondition {
    /** Total cumulative metronome play-time in seconds. */
    data class MetronomeSeconds(val required: Long) : UnlockCondition()

    /** Number of rhythm games fully completed (reached RESULT screen). */
    data class RhythmGamesCompleted(val required: Int) : UnlockCondition()

    /** Calendar days elapsed since the very first app launch. */
    data class DaysSinceFirstLaunch(val required: Int) : UnlockCondition()

    /** Always unlocked — used for developer preview / cheat mode. */
    object Always : UnlockCondition()
}
