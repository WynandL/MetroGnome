package com.example.metrognome.points

import com.example.metrognome.ui.components.metro_items.UnlockCondition

/**
 * Converts [UnlockCondition] values to their [PointsConfig.CURRENCY_NAME] equivalent.
 *
 * This is the bridge between the item unlock system and the points system.
 * No unlock logic changes — conditions still evaluate against raw counters.
 * These functions are purely for display: expressing "what you need to do"
 * in Gnotes terms so both systems feel unified to the user.
 *
 * When [PointsConfig] weights change, [pointsEquivalent] updates automatically.
 * When [PointsConfig.CURRENCY_NAME] changes, [pointsDisplayText] updates automatically.
 * When a new [UnlockCondition] variant is added, the compiler will flag the exhaustive
 * when-expressions here so this file stays in sync.
 */

/** The Gnotes value that satisfying this condition corresponds to. */
fun UnlockCondition.pointsEquivalent(): Int = when (this) {
    is UnlockCondition.MetronomeSeconds ->
        (required / 60L * PointsConfig.METRONOME_PER_MINUTE).toInt()

    is UnlockCondition.TunerSeconds ->
        (required / 60L * PointsConfig.TUNER_PER_MINUTE).toInt()

    is UnlockCondition.RhythmGamesCompleted ->
        required * (PointsConfig.GAME_SCORE_AVG_PER_GAME / PointsConfig.GAME_SCORE_DIVISOR)

    is UnlockCondition.DaysSinceFirstLaunch ->
        required * PointsConfig.LOYALTY_PER_DAY

    is UnlockCondition.LoyaltyDays ->
        required * PointsConfig.LOYALTY_PER_DAY

    is UnlockCondition.PracticeSessionsCompleted ->
        required * (PointsConfig.PRACTICE_MINUTES_AVG_PER_SESSION * PointsConfig.PER_PRACTICE_MINUTE)

    is UnlockCondition.TunerFeedbackGiven ->
        required * PointsConfig.PER_TUNER_FEEDBACK

    is UnlockCondition.SpeedTrainingSessionsCompleted ->
        required * PointsConfig.SPEED_TRAINER_MINUTES_AVG_PER_SESSION * PointsConfig.PER_SPEED_TRAINER_MINUTE

    // A one-off action, not a Gnotes-earning activity — no points equivalent.
    is UnlockCondition.MicChecksCompleted -> 0

    UnlockCondition.Always -> 0
}

/**
 * Human-readable Gnotes unlock requirement, e.g. "Earn 60 Gnotes from Metronome".
 *
 * Mirrors the source labels used in [EARN_RULES] so the item catalog and the
 * earn-rules dialog feel like one unified system to the user.
 */
fun UnlockCondition.pointsDisplayText(): String {
    val pts  = pointsEquivalent()
    val name = if (pts == 1) PointsConfig.CURRENCY_NAME_SINGULAR else PointsConfig.CURRENCY_NAME
    return when (this) {
        is UnlockCondition.MetronomeSeconds ->
            "Earn $pts $name from Metronome"

        is UnlockCondition.TunerSeconds ->
            "Earn $pts $name from Tuner"

        is UnlockCondition.RhythmGamesCompleted ->
            "Earn $pts $name from Rhythm Game"

        is UnlockCondition.DaysSinceFirstLaunch ->
            "Earn $pts $name from Loyalty"

        is UnlockCondition.LoyaltyDays ->
            "Earn $pts $name from Loyalty"

        is UnlockCondition.PracticeSessionsCompleted ->
            "Earn $pts $name from Practice Session"

        is UnlockCondition.TunerFeedbackGiven ->
            "Earn $pts $name from Tuner Feedback"

        is UnlockCondition.SpeedTrainingSessionsCompleted ->
            "Earn $pts $name from Speed Trainer"

        // Not Gnotes-framed — it is a single action, so name the action directly.
        is UnlockCondition.MicChecksCompleted ->
            if (required == 1) "Run a microphone Groove Check"
            else "Run $required microphone Groove Checks"

        UnlockCondition.Always -> "Always available"
    }
}
