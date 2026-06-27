package com.example.metrognome.groove

import kotlin.math.roundToInt

/**
 * Shared Groove Check types, tuning constants, and the economy reward. The actual grading now
 * lives in [SessionAnalyzer], which analyses the COMPLETE session statistically (estimates the
 * player's own pulse, rejects ambient outliers, grades self-consistency) and packages its verdict
 * into the [Result] shape defined here so the bonus + result UI stay engine-agnostic.
 *
 * Philosophy (unchanged, see the long design discussion): the app grades skill universally and
 * silently. There is no beginner/intermediate/advanced choice. A skilled player is detected, not
 * declared, and simply earns more. The grade is driven by CONSISTENCY (how tightly the inputs
 * cluster about the player's own pulse), not raw closeness to the click, because consistency is the
 * better signal of timing skill and is fair to real hardware: even a perfectly calibrated device
 * leaves a residual latency that shifts every hit by the same amount.
 *
 * Two outputs, deliberately decoupled:
 *  - [Result.grooveScore]  0..100 quality grade. LENGTH-INDEPENDENT, so a tight 20-second demo
 *                          grades just as high as a tight 10-minute session. This is the number
 *                          to show the player.
 *  - [bonusGnotes]         the economy reward: a share of the session minutes set by the same
 *                          performance fraction. Stays small and proportionate (1 min ~ 1 Gnote);
 *                          the daily cap is applied later by the points pipeline.
 *
 * Deviation sign convention: positive = late (behind the beat), negative = early (ahead).
 */
object GrooveScorer {

    /** Fewer accepted hits than this and the timing is not meaningful; grade is unqualified. */
    const val MIN_HITS = 5

    /** Jitter (standard deviation) at/under this earns full consistency credit. */
    const val TIGHT_JITTER_MS = 25f
    /** Jitter at/over this earns no consistency credit. */
    const val LOOSE_JITTER_MS = 110f

    /** A systematic offset up to this still reads as "on the beat" in the plain-language verdict. */
    const val BIAS_GRACE_MS = 60f

    /**
     * Per-hit closeness (ms) that fires the celebratory firework in the Gnome canvas. Purely
     * visual, no scoring effect. Looser than the old 35ms so it is actually attainable on real
     * hardware.
     */
    const val GREAT_HIT_MS = 50f

    data class Result(
        /** 0..100 quality grade for display. Length-independent. */
        val grooveScore: Int,
        /** The same grade as a 0..1 fraction; drives [bonusGnotes]. */
        val fraction: Float,
        /** Mean signed deviation (ms): + behind the beat, - ahead. */
        val biasMs: Float,
        /** Standard deviation of the hits (ms): the consistency / jitter. */
        val jitterMs: Float,
        val hitCount: Int,
        /** True once there are enough hits and the grade is above zero. */
        val qualified: Boolean,
        /** Short plain-language read of the playing, produced by [SessionAnalyzer]. */
        val read: String,
    )

    /**
     * The economy reward in Gnotes: a share of [sessionMinutes] set by [fraction]. Returns 0 when
     * the session is unqualified or graded zero. A qualifying session always earns at least 1, even
     * if it ran under a minute, so a quick demo never shows a bare zero. The daily cap is applied
     * by the points pipeline, not here.
     */
    fun bonusGnotes(fraction: Float, hitCount: Int, sessionMinutes: Int): Int {
        if (hitCount < MIN_HITS || fraction <= 0f) return 0
        return (sessionMinutes * fraction).roundToInt().coerceAtLeast(1)
    }

    /**
     * Fold a raw "onset minus most-recent-beat" delta onto the NEAREST beat, returning a signed
     * deviation in [-interval/2, +interval/2] (+ = behind the beat, - = ahead).
     *
     * Both timing-bonus paths (Practice, Speed Trainer) stamp the last beat that fired and measure
     * each clap against it. Without folding, a clap that anticipates the beat (lands just before it)
     * is attributed to the *previous* beat as a large positive (very late) value - and at slow
     * tempos, where the beat interval exceeds the accept window, a genuine early clap is rejected
     * outright. Folding to the nearest beat makes an early clap read as early, not very late, so the
     * signed deviations are not skewed late (which would inflate jitter and bias).
     */
    fun nearestBeatDeviation(rawDeltaMs: Float, beatIntervalMs: Float): Float {
        if (beatIntervalMs <= 0f) return rawDeltaMs
        return rawDeltaMs - (rawDeltaMs / beatIntervalMs).roundToInt() * beatIntervalMs
    }
}
