package com.example.metrognome.points

/**
 * Weight table for Gnotes point calculation.
 * All tuning lives here — change values to adjust rates without touching
 * calculation or UI logic.
 *
 * Design intent:
 *   - Metronome and Tuner reward sustained time investment (per-minute weights)
 *   - Tuner earns 2× because it requires active skill (locking on pitch), not just running
 *   - Sessions (practice, speed trainer) reward intentional structured work
 *   - Loyalty rewards longevity without inflating the score for inactive users
 */
object PointsConfig {
    /** Display name for the points currency (plural). Change here to rename everywhere. */
    const val CURRENCY_NAME: String = "Gnotes"
    /** Singular form — used when the count is exactly 1 ("1 Gnote"). */
    const val CURRENCY_NAME_SINGULAR: String = "Gnote"

    const val METRONOME_PER_MINUTE: Int        = 2
    const val TUNER_PER_MINUTE: Int            = 2  // kept for ConditionPoints item-unlock display only
    const val PER_TUNER_NOTE: Int              = 5  // points per individual note locked on
    /**
     * Half the metronome's rate, and deliberately the lowest in the app.
     *
     * The drone is the only activity that asks nothing of the user once started, and it can
     * sound at the same time as the metronome, which is already earning. Matching the
     * metronome's rate would have made "start both and walk away" the best-paying thing in
     * the app, which is the opposite of what any of these weights are for.
     */
    const val DRONE_PER_MINUTE: Int            = 1
    const val GAME_SCORE_DIVISOR: Int          = 110  // every 110 game-score points = 1 Gnote (near-perfect beginner game ~14 Gnotes)
    const val GAME_SCORE_AVG_PER_GAME: Int     = 1000  // typical casual-game score; used only in ConditionPoints display
    const val PER_PRACTICE_MINUTE: Int         = 2   // points per minute of completed practice
    const val PRACTICE_MINUTES_AVG_PER_SESSION: Int = 15  // estimate for ConditionPoints display only
    const val PER_SPEED_TRAINER_MINUTE: Int     = 2   // points per minute of a completed speed trainer session
    const val SPEED_TRAINER_MINUTES_AVG_PER_SESSION: Int = 8  // estimate for ConditionPoints display only
    const val PER_TUNER_FEEDBACK: Int          = 5
    const val LOYALTY_PER_DAY: Int             = 10
    /** Bonus per calendar day the app has been installed — rewards long-term users retroactively. */
    const val INSTALLED_DAY_BONUS: Int         = 1
    /** Graded timing bonus is already in points (1 Gnote per earned bonus point). */
    const val PER_PERFORMANCE_BONUS: Int = 1
    /** Gnotes granted for watching one rewarded ad. */
    const val REWARDED_GNOTES_PER_WATCH: Int = 15
}
