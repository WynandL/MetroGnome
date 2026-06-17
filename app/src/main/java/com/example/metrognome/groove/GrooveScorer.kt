package com.example.metrognome.groove

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Adaptive Groove Check scorer: the single source of truth for turning a session's mic timing
 * into a performance grade and a Gnotes bonus.
 *
 * Philosophy (see the long design discussion): the app grades skill universally and silently.
 * There is no beginner/intermediate/advanced choice. A skilled player is detected, not declared,
 * and simply earns more. The grade is driven primarily by CONSISTENCY (how tightly the hits
 * cluster), not raw closeness to the beat, because consistency is the better signal of timing
 * skill and is fair to real hardware: even a perfectly calibrated device leaves a residual
 * latency that shifts every hit by the same amount. A player who is tight but sitting behind the
 * beat has good time; the old "absolute deviation from the beat" curve scored them ~zero.
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

    /** A systematic offset up to this is forgiven as feel/latency (no penalty). */
    const val BIAS_GRACE_MS = 60f
    /** Offset at/over this takes the maximum penalty. */
    const val BIAS_FLOOR_MS = 200f
    /** The bias penalty never drops a consistent player below this factor (so "steady but off"
     *  still scores well, but "consistently on the wrong subdivision" cannot score full marks). */
    const val BIAS_MIN_FACTOR = 0.5f

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
        /** Short plain-language read of the playing, e.g. "Locked in", "Steady but behind the beat". */
        val read: String,
    ) {
        companion object {
            val NONE = Result(0, 0f, 0f, 0f, 0, qualified = false, read = "Not enough hits yet")
        }
    }

    /**
     * Grade a session from its signed per-hit deviations (ms). Consistency is the main driver;
     * a steady offset is mostly forgiven, a gross offset is tapered (never to zero).
     */
    fun score(signedDeviationsMs: List<Float>): Result {
        val n = signedDeviationsMs.size
        if (n < MIN_HITS) return Result.NONE.copy(hitCount = n)

        val bias = signedDeviationsMs.average().toFloat()
        val variance = signedDeviationsMs.map { (it - bias) * (it - bias) }.average().toFloat()
        val jitter = sqrt(variance)

        val consistency = rampDown(jitter)
        val biasFactor = biasFactor(abs(bias))
        val fraction = (consistency * biasFactor).coerceIn(0f, 1f)

        return Result(
            grooveScore = (fraction * 100).roundToInt(),
            fraction = fraction,
            biasMs = bias,
            jitterMs = jitter,
            hitCount = n,
            qualified = fraction > 0f,
            read = read(jitter, bias),
        )
    }

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

    // --- internals ---

    private fun rampDown(x: Float): Float =
        ((LOOSE_JITTER_MS - x) / (LOOSE_JITTER_MS - TIGHT_JITTER_MS)).coerceIn(0f, 1f)

    /** 1f within the grace band, tapering to [BIAS_MIN_FACTOR] at [BIAS_FLOOR_MS]. */
    private fun biasFactor(absBias: Float): Float {
        if (absBias <= BIAS_GRACE_MS) return 1f
        val t = ((absBias - BIAS_GRACE_MS) / (BIAS_FLOOR_MS - BIAS_GRACE_MS)).coerceIn(0f, 1f)
        return 1f - t * (1f - BIAS_MIN_FACTOR)
    }

    private fun read(jitter: Float, bias: Float): String {
        val tight = jitter <= TIGHT_JITTER_MS
        val loose = jitter >= LOOSE_JITTER_MS
        val offBeat = abs(bias) > BIAS_GRACE_MS
        return when {
            loose -> "Loose timing"
            tight && offBeat -> if (bias > 0) "Steady but behind the beat" else "Steady but ahead of the beat"
            tight -> "Locked in"
            offBeat -> if (bias > 0) "A touch behind" else "A touch ahead"
            else -> "Solid"
        }
    }
}
