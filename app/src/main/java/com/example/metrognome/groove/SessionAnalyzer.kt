package com.example.metrognome.groove

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Post-session rhythm analysis: the "Stage 2" intelligence. Takes the COMPLETE set of accepted
 * onset timestamps from a finished session and decides, statistically, (a) whether the user was
 * actually clapping a rhythm at all, (b) which onsets are real inputs vs ambient noise, and (c)
 * how steady the inputs were - then grades that.
 *
 * ## Why batch, not real-time
 * The microphone path only has to do the unavoidable real-time job: capture audio and reject the
 * *known* sounds we generate (click/accent), emitting onset timestamps. Everything that needs to
 * see the WHOLE picture - the user's tempo, drift over a long session, a quiet minute, an outlier
 * bark - is far easier and more robust to compute once, offline, over the finished array. This
 * object is pure (no Android, no engine), so it is deterministic and unit-testable with synthetic
 * sessions, and could even be re-run on a recorded session.
 *
 * ## The statistics (directional / circular)
 * The user clapping is a roughly periodic point process. We estimate the user's OWN period from
 * their inter-onset intervals (NOT the metronome grid - a player who is rock-steady at 95 against
 * a 100 click has good time and must score well). Each onset's position within that period is a
 * phase angle. The **Rayleigh resultant length R** of those angles (0..1) is the principled
 * "is there a rhythm?" measure: R -> 1 when onsets lock to a phase (real clapping), R -> 0 when
 * they are uniformly scattered (noise). Onsets far from the locked phase are outliers (the dog).
 *
 * The session is processed in time WINDOWS so a tempo ramp, slow drift, or a silent span is handled
 * naturally: each window locks its own phase, and a window with no coherent rhythm simply
 * contributes little. Self-consistency is the robust spread of inlier phase residuals, pooled.
 *
 * Grid relationship (bias/jitter vs the actual beats) is computed separately, for diagnostics and
 * the plain-language read only - it never zeroes the grade.
 */
object SessionAnalyzer {

    /** Fewer coherent inputs than this and we cannot claim the user was clapping a rhythm. */
    const val MIN_INPUTS = GrooveScorer.MIN_HITS

    /** Analysis window. Long enough for a stable phase lock, short enough to track drift/ramps. */
    private const val WINDOW_MS = 8_000L
    /** Plausible human clap period bounds (~30..400 BPM) - clamps a noisy IOI estimate. */
    private const val MIN_PERIOD_MS = 150.0
    private const val MAX_PERIOD_MS = 2_000.0
    /** An onset is an inlier if its phase residual is within this of the locked phase. */
    private const val INLIER_TOL_MS = 150.0
    private const val INLIER_TOL_FRACTION = 0.30      // ...or this fraction of the period, whichever is tighter
    /** Rayleigh resultant at/above which a window's onsets count as a real (non-uniform) rhythm. */
    const val RHYTHM_STRENGTH_MIN = 0.45f
    /** Minimum share of onsets that must be inliers for the session to read as "the user clapping". */
    private const val MIN_COVERAGE = 0.40f

    data class Analysis(
        /** Every accepted onset handed in. */
        val totalOnsets: Int,
        /** Onsets that fit the detected rhythm (the rest are treated as noise/outliers). */
        val validInputs: Int,
        /** The user's own detected tempo (0 if no rhythm found). */
        val estimatedBpm: Float,
        /** Robust spread of inlier timing about the user's own pulse (ms). The consistency signal. */
        val selfConsistencyMs: Float,
        /** Rayleigh resultant 0..1, pooled across windows: confidence that a rhythm exists at all. */
        val rhythmStrength: Float,
        /** Mean signed deviation of inputs vs the nearest metronome beat (ms). Diagnostic only. */
        val gridBiasMs: Float,
        /** Std deviation of inputs vs the nearest beat (ms). Diagnostic only - does NOT drive the grade. */
        val gridJitterMs: Float,
        /** 0..100 grade for display. Driven by self-consistency, gated by rhythm confidence. */
        val grooveScore: Int,
        /** Same grade as 0..1; drives the Gnotes bonus via [GrooveScorer.bonusGnotes]. */
        val fraction: Float,
        /** True when we are statistically confident the user was clapping a rhythm. */
        val confident: Boolean,
        /** Short plain-language read. */
        val read: String,
    ) {
        companion object {
            val NONE = Analysis(0, 0, 0f, 0f, 0f, 0f, 0f, 0, 0f, false, "No clear rhythm detected")
        }
    }

    /**
     * Analyse a finished session. [onsetTimesMs] are the accepted user onsets (claps) and
     * [beatTimesMs] are the metronome beats that fired; both on the same monotonic clock. Beats are
     * optional and used only for the grid-relation diagnostics.
     */
    fun analyze(onsetTimesMs: List<Long>, beatTimesMs: List<Long> = emptyList()): Analysis {
        val onsets = onsetTimesMs.sorted()
        if (onsets.size < MIN_INPUTS) return Analysis.NONE.copy(totalOnsets = onsets.size)

        val pooledResiduals = ArrayList<Float>()
        var inliers = 0
        var rNumer = 0.0
        var rDenom = 0.0
        var periodNumer = 0.0
        var periodDenom = 0.0

        var windowStart = onsets.first()
        val end = onsets.last()
        while (windowStart <= end) {
            val windowEnd = windowStart + WINDOW_MS
            val w = onsets.filter { it in windowStart until windowEnd }
            windowStart = windowEnd
            if (w.size < 3) continue

            val period = estimatePeriod(w) ?: continue
            val fit = phaseFit(w, period)
            pooledResiduals += fit.inlierResiduals
            inliers += fit.inlierResiduals.size
            // Weight each window's rhythm strength and tempo by how many onsets informed it.
            rNumer += fit.resultant * w.size; rDenom += w.size
            periodNumer += period * w.size; periodDenom += w.size
        }

        if (inliers < MIN_INPUTS || pooledResiduals.isEmpty() || rDenom == 0.0) {
            return Analysis.NONE.copy(totalOnsets = onsets.size)
        }

        val rhythmStrength = (rNumer / rDenom).toFloat()
        val estPeriod = periodNumer / periodDenom
        val estBpm = (60_000.0 / estPeriod).toFloat()
        val selfConsistency = robustSpread(pooledResiduals)
        val coverage = inliers.toFloat() / onsets.size
        val (gridBias, gridJitter) = gridStats(onsets, beatTimesMs)

        val confident = rhythmStrength >= RHYTHM_STRENGTH_MIN && coverage >= MIN_COVERAGE
        // Consistency curve (reused from GrooveScorer) applied to the user's OWN pulse spread.
        val consistency = rampDown(selfConsistency)
        val fraction = if (confident) consistency.coerceIn(0f, 1f) else 0f

        return Analysis(
            totalOnsets = onsets.size,
            validInputs = inliers,
            estimatedBpm = estBpm,
            selfConsistencyMs = selfConsistency,
            rhythmStrength = rhythmStrength,
            gridBiasMs = gridBias,
            gridJitterMs = gridJitter,
            grooveScore = (fraction * 100).roundToInt(),
            fraction = fraction,
            confident = confident,
            read = read(confident, selfConsistency, gridBias, gridJitter),
        )
    }

    // ── internals ───────────────────────────────────────────────────────────────

    private class Fit(val resultant: Double, val inlierResiduals: List<Float>)

    /**
     * Lock a phase to the window's onsets at [period] and return the Rayleigh resultant plus the
     * signed inlier residuals (ms from the locked phase). The resultant measures how tightly the
     * onsets cluster in phase - high for a real pulse, ~0 for uniform noise.
     */
    private fun phaseFit(window: List<Long>, period: Double): Fit {
        val t0 = window.first()
        var c = 0.0
        var s = 0.0
        for (t in window) {
            val angle = TWO_PI * (((t - t0) % period.toLong()).toDouble() / period)
            c += cos(angle); s += sin(angle)
        }
        val n = window.size
        val resultant = hypot(c, s) / n
        val meanAngle = atan2(s, c)
        val tol = minOf(INLIER_TOL_MS, period * INLIER_TOL_FRACTION)
        val residuals = ArrayList<Float>(n)
        for (t in window) {
            val angle = TWO_PI * (((t - t0) % period.toLong()).toDouble() / period)
            var d = angle - meanAngle
            while (d > Math.PI) d -= TWO_PI
            while (d < -Math.PI) d += TWO_PI
            val residualMs = (d / TWO_PI * period).toFloat()
            if (abs(residualMs) <= tol) residuals += residualMs
        }
        return Fit(resultant, residuals)
    }

    /** The user's own period: robust (median) inter-onset interval, clamped to a plausible range. */
    private fun estimatePeriod(window: List<Long>): Double? {
        val iois = window.zipWithNext { a, b -> (b - a).toDouble() }.filter { it > 0.0 }
        if (iois.size < 2) return null
        val med = medianD(iois)
        if (med !in MIN_PERIOD_MS..MAX_PERIOD_MS) return null
        return med
    }

    /** Inputs vs nearest beat: (mean signed deviation, std deviation). Diagnostic only. */
    private fun gridStats(onsets: List<Long>, beats: List<Long>): Pair<Float, Float> {
        if (beats.size < 2) return 0f to 0f
        val sortedBeats = beats.sorted()
        val devs = onsets.mapNotNull { nearestBeatDeviationMs(it, sortedBeats) }
        if (devs.isEmpty()) return 0f to 0f
        val bias = devs.average()
        val variance = devs.map { (it - bias) * (it - bias) }.average()
        return bias.toFloat() to kotlin.math.sqrt(variance).toFloat()
    }

    /** Signed ms from an onset to its nearest beat (+ late, - early); null if no beats. */
    private fun nearestBeatDeviationMs(onset: Long, sortedBeats: List<Long>): Double? {
        if (sortedBeats.isEmpty()) return null
        // Binary search for the closest beat.
        var lo = 0; var hi = sortedBeats.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sortedBeats[mid] < onset) lo = mid + 1 else hi = mid
        }
        val candidates = buildList {
            add(sortedBeats[lo])
            if (lo > 0) add(sortedBeats[lo - 1])
        }
        return candidates.minByOrNull { abs(it - onset) }?.let { (onset - it).toDouble() }
    }

    /** Robust spread as a sigma estimate: scaled median absolute deviation. */
    private fun robustSpread(xs: List<Float>): Float {
        if (xs.isEmpty()) return 0f
        val med = medianF(xs)
        val mad = medianF(xs.map { abs(it - med) })
        return mad * 1.4826f
    }

    private fun rampDown(x: Float): Float =
        ((GrooveScorer.LOOSE_JITTER_MS - x) /
            (GrooveScorer.LOOSE_JITTER_MS - GrooveScorer.TIGHT_JITTER_MS)).coerceIn(0f, 1f)

    private fun read(confident: Boolean, selfConsistency: Float, gridBias: Float, gridJitter: Float): String {
        if (!confident) return "No clear rhythm detected"
        val tight = selfConsistency <= GrooveScorer.TIGHT_JITTER_MS
        val loose = selfConsistency >= GrooveScorer.LOOSE_JITTER_MS
        val onGrid = gridJitter in 0.1f..GrooveScorer.LOOSE_JITTER_MS && abs(gridBias) <= GrooveScorer.BIAS_GRACE_MS
        return when {
            loose -> "Clapping detected, loose timing"
            tight && onGrid -> "Locked into a steady pulse, on the beat"
            tight -> "Locked into a steady pulse"
            onGrid -> "Steady, close to the beat"
            else -> "Steady pulse, off the click"
        }
    }

    private fun medianD(xs: List<Double>): Double {
        val s = xs.sorted(); val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    private fun medianF(xs: List<Float>): Float {
        val s = xs.sorted(); val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
    }

    private const val TWO_PI = 2.0 * Math.PI
}
