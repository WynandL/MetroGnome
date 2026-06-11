package com.example.metrognome.points

import kotlin.math.roundToInt

/**
 * Converts a session's mic timing into a graded Gnotes bonus.
 *
 * The reward after a mic-enabled Practice or Speed Trainer session is a portion of a
 * per-session maximum equal to the session length in minutes: a 5-minute session can
 * award up to 5, a 45-minute session up to 45. The portion is set by how well the
 * player kept time.
 *
 * Scoring is per-hit and deliberately lenient: each hit earns credit in [0,1] from how
 * close it landed ([hitScore]), and the session fraction is the MEAN of those per-hit
 * scores ([sessionScore]). A wild hit scores 0 and counts as one weak hit among many -
 * it never drags the result down the way a raw mean deviation does, where a single
 * +300ms outlier (often environmental or hardware, not the player) could sink an
 * otherwise-good session. The player is credited for the good hits rather than
 * penalised for the bad. Misses, rejected and suppressed onsets are not counted at all.
 *
 * A complete misfire (no hit cleared the floor, so the fraction is 0) returns 0; the
 * caller hides a zero. The daily total is capped separately by [PointsLimits].
 *
 * Single source of truth for the curve: both Practice (MetronomeViewModel) and Speed
 * Trainer (SpeedTrainerViewModel) call [sessionScore] + [award], so the two can never
 * drift; the dev Mic Timing Log shows the same numbers.
 */
object PerformanceBonus {

    /** Absolute deviation at or below this earns full per-hit credit. */
    const val TIGHT_MS = 20f
    /** Absolute deviation at or above this earns no per-hit credit. */
    const val LOOSE_MS = 80f
    /** Minimum mic hits for the timing to be meaningful; fewer earns nothing. */
    const val MIN_HITS = 5

    /** One hit's timing credit in [0,1]: full at/under [TIGHT_MS], zero at/over [LOOSE_MS]. */
    fun hitScore(absDeviationMs: Float): Float =
        ((LOOSE_MS - absDeviationMs) / (LOOSE_MS - TIGHT_MS)).coerceIn(0f, 1f)

    /**
     * Session performance fraction in [0,1]: the mean of every hit's [hitScore].
     * Outlier-robust by construction - a wild hit contributes 0, never a negative pull.
     * An empty list returns 0.
     */
    fun sessionScore(absDeviationsMs: List<Float>): Float =
        if (absDeviationsMs.isEmpty()) 0f
        else absDeviationsMs.map { hitScore(it) }.average().toFloat()

    /**
     * The raw bonus (Gnotes) for a session: a portion of [sessionMinutes] set by
     * [performanceFraction] (from [sessionScore]). Returns 0 with fewer than [MIN_HITS]
     * hits or a zero fraction (a misfire) - the caller hides a zero. The daily cap is
     * applied later by the points pipeline, not here.
     *
     * A qualifying session (>= [MIN_HITS] hits, fraction > 0) always earns at least 1,
     * even when it ran under a minute or the rounded portion would otherwise be 0 - a
     * player who kept decent time should never walk away with nothing. [sessionMinutes]
     * still sets the upper bound for longer sessions.
     */
    fun award(performanceFraction: Float, hitCount: Int, sessionMinutes: Int): Int {
        if (hitCount < MIN_HITS) return 0
        if (performanceFraction <= 0f) return 0
        return (sessionMinutes * performanceFraction).roundToInt().coerceAtLeast(1)
    }
}
