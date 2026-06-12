package com.example.metrognome.points

/**
 * Daily earning caps for each activity.
 *
 * Only the units earned within a single calendar day count toward Gnotes;
 * any activity beyond the cap is ignored for that day. Historical contributions
 * from previous days are unchanged, so caps only affect going forward.
 *
 * This is the single file to edit when adjusting fairness or testing balance.
 * All enforcement logic lives in [PointsCalculator]; nothing else should
 * import these constants directly.
 */
object PointsLimits {
    /** Minutes of metronome play that count per day. */
    const val METRONOME_MINUTES_PER_DAY: Int          = 45
    /** Individual note lock-ons that count per day. 18 × 5 pts = 90 pts max (equal to metronome). */
    const val TUNER_NOTES_PER_DAY: Int                = 18
    /** Maximum Gnotes earnable from the rhythm game per day. Reached after ~2-3 decent games. */
    const val RHYTHM_BEATS_PER_DAY: Int               = 30
    /** Minutes of completed practice that count per day. 45 × 2 pts = 90 pts max (equal to metronome). */
    const val PRACTICE_MINUTES_PER_DAY: Int           = 45
    /** Minutes of Speed Trainer that count per day. 45 × 2 pts = 90 pts max (equal to metronome/practice). */
    const val SPEED_TRAINER_MINUTES_PER_DAY: Int      = 45
    /** Tuner feedback submissions that count per day. */
    const val TUNER_FEEDBACK_PER_DAY: Int             = 2
    /** Graded timing-bonus Gnotes (from Practice + Speed Trainer) that count per day. */
    const val PERFORMANCE_BONUS_PER_DAY: Int          = 45
    // Loyalty is inherently one point per calendar day — no cap needed.
    /** Maximum Gnotes earnable from rewarded ads per day. 45 / 15 = 3 watches. */
    const val REWARDED_GNOTES_PER_DAY: Int            = 45
}
