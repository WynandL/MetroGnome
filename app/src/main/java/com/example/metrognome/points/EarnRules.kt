package com.example.metrognome.points

/**
 * A single way to earn [PointsConfig.CURRENCY_NAME].
 *
 * All numeric fields reference [PointsConfig] and [PointsLimits] constants so
 * the dialog that displays this list never needs to be updated when weights or
 * caps change — it just reads the values at runtime.
 *
 * [inherentlyDaily] marks activities that are capped at exactly once per
 * calendar day by design (Loyalty), so the UI can say "Once daily" instead
 * of showing a numeric limit that would be confusing ("1 day / day").
 */
data class EarnRule(
    val label: String,
    val description: String,
    val iconKey: String,
    val pointsPerUnit: Int,
    val rateUnit: String,           // e.g. "min", "game", "session", "day"
    val dailyLimit: Int,
    val limitUnit: String,          // e.g. "min", "games", "sessions"
    val inherentlyDaily: Boolean = false,
    val hidden: Boolean = false,
) {
    val maxPerDay: Int get() = pointsPerUnit * dailyLimit
    val ptLabel:   String  get() = if (pointsPerUnit == 1) "pt"  else "pts"
    val maxPtLabel: String get() = if (maxPerDay     == 1) "pt"  else "pts"
}

/**
 * Single source of truth for every earn opportunity in the app.
 * Add a new entry here whenever a new tracked activity is introduced.
 * The order controls display order in the UI.
 */
val EARN_RULES: List<EarnRule> = listOf(
    EarnRule(
        label         = "Metronome",
        description   = "Earns while the metronome is running",
        iconKey       = "metronome",
        pointsPerUnit = PointsConfig.METRONOME_PER_MINUTE,
        rateUnit      = "min",
        dailyLimit    = PointsLimits.METRONOME_MINUTES_PER_DAY,
        limitUnit     = "min",
    ),
    EarnRule(
        label         = "Tuner",
        description   = "Earned each time the tuner locks on to a note",
        iconKey       = "tuner",
        pointsPerUnit = PointsConfig.PER_TUNER_NOTE,
        rateUnit      = "note",
        dailyLimit    = PointsLimits.TUNER_NOTES_PER_DAY,
        limitUnit     = "notes",
    ),
    EarnRule(
        label         = "Rhythm Game",
        description   = "Your timing is the reward here: tighter hits score higher, and every ${PointsConfig.GAME_SCORE_DIVISOR} score points earns 1 ${PointsConfig.CURRENCY_NAME_SINGULAR}.",
        iconKey       = "game",
        pointsPerUnit = 1,
        rateUnit      = "${PointsConfig.GAME_SCORE_DIVISOR} score",
        dailyLimit    = PointsLimits.RHYTHM_BEATS_PER_DAY,
        limitUnit     = PointsConfig.CURRENCY_NAME,
    ),
    EarnRule(
        label         = "Practice",
        description   = "Earned per minute of completed practice timer session",
        iconKey       = "practice",
        pointsPerUnit = PointsConfig.PER_PRACTICE_MINUTE,
        rateUnit      = "min",
        dailyLimit    = PointsLimits.PRACTICE_MINUTES_PER_DAY,
        limitUnit     = "min",
    ),
    EarnRule(
        label         = "Speed Trainer",
        description   = "Earned per minute of a completed Speed Trainer ramp",
        iconKey       = "speed",
        pointsPerUnit = PointsConfig.PER_SPEED_TRAINER_MINUTE,
        rateUnit      = "min",
        dailyLimit    = PointsLimits.SPEED_TRAINER_MINUTES_PER_DAY,
        limitUnit     = "min",
    ),
    EarnRule(
        label         = "Tuner Feedback",
        description   = "Earned when you rate how well the tuner worked",
        iconKey       = "feedback",
        pointsPerUnit = PointsConfig.PER_TUNER_FEEDBACK,
        rateUnit      = "rating",
        dailyLimit    = PointsLimits.TUNER_FEEDBACK_PER_DAY,
        limitUnit     = "ratings",
    ),
    EarnRule(
        label         = "Timing Bonus",
        description   = "Play in time with the mic on during Practice or Speed Trainer. You earn a share of the session length, set by how well you keep time.",
        iconKey       = "timing",
        pointsPerUnit = PointsConfig.PER_PERFORMANCE_BONUS,
        rateUnit      = "min in time",
        dailyLimit    = PointsLimits.PERFORMANCE_BONUS_PER_DAY,
        limitUnit     = PointsConfig.CURRENCY_NAME,
    ),
    EarnRule(
        label            = "Loyalty",
        description      = "Awarded once for each new calendar day you open the app",
        iconKey          = "loyalty",
        pointsPerUnit    = PointsConfig.LOYALTY_PER_DAY,
        rateUnit         = "day",
        dailyLimit       = 1,
        limitUnit        = "day",
        inherentlyDaily  = true,
    ),
    EarnRule(
        label            = "Installed",
        description      = "1 bonus point for every day the app has been installed - rewards long-term users",
        iconKey          = "loyalty",
        pointsPerUnit    = PointsConfig.INSTALLED_DAY_BONUS,
        rateUnit         = "day",
        dailyLimit       = 1,
        limitUnit        = "day",
        inherentlyDaily  = true,
    ),
)
