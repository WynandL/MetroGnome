package com.example.metrognome.audio.dsp

import kotlin.math.sqrt

/**
 * Monophonic pitch detector — the **McLeod Pitch Method** (MPM).
 *
 * MPM is the autocorrelation-family algorithm of choice for instrument tuning:
 * it is far more robust against the octave errors that plague raw FFT peak
 * picking, and its normalised difference function yields a clean 0..1 "clarity"
 * figure that doubles as a confidence score.
 *
 * The chain:
 *
 *  1. **DC removal** — the window mean is subtracted; a constant offset would
 *     bias the autocorrelation.
 *  2. **Autocorrelation** via [FFT] — O(n log n). The window is zero-padded to
 *     twice its length so the result is the *linear* (not circular)
 *     autocorrelation r(τ).
 *  3. **NSDF** — McLeod's Normalised Square Difference Function,
 *     `nsdf(τ) = 2·r(τ) / Σ(x(j)² + x(j+τ)²)`, bounded to [-1, 1]. The
 *     denominator is computed from a prefix sum of squared samples, so the
 *     whole step is O(n).
 *  4. **Key-maximum peak picking** — the period is the *first* NSDF peak that
 *     clears [PEAK_PICK_RATIO] of the tallest peak. Choosing the first such
 *     peak (not simply the tallest) is precisely what rejects the
 *     octave-too-low error.
 *  5. **Parabolic interpolation** — the chosen integer lag is refined to a
 *     fractional lag, lifting timing resolution from one sample (~3 cents at
 *     the low end) to a small fraction of a cent.
 *
 * Pure DSP: it deals only in sample arrays — no time, no threading — so it is
 * deterministic and unit-testable. Scratch buffers are instance-owned and
 * reused, so it is **not** thread-safe: give each capture thread its own
 * instance.
 */
class PitchDetector(
    private val sampleRate: Int,
    /** Analysis window length in samples — must be a power of two. */
    val windowSize: Int,
) {
    companion object {
        /** Lowest fundamental reported (Hz) — below a 5-string bass low B (~31 Hz). */
        const val MIN_FREQUENCY = 25f

        /** Highest fundamental reported (Hz) — above the piccolo / violin range. */
        const val MAX_FREQUENCY = 4500f

        /**
         * The chosen NSDF peak must reach at least this fraction of the tallest
         * peak. Picking the *first* peak above the bar — rather than the tallest
         * — is what makes MPM resist reporting an octave too low.
         */
        private const val PEAK_PICK_RATIO = 0.9f

        /** Best NSDF peak below this → the signal is too noisy/unvoiced to trust. */
        private const val MIN_CLARITY = 0.5f

        /** RMS (samples normalised to ≈ ±1.0) below this → treated as silence. */
        private const val SILENCE_RMS = 0.005f

        /** Upper bound on key maxima collected per window — generous for any real signal. */
        private const val MAX_KEY_MAXIMA = 128
    }

    /** A successful detection. [clarity] is 0..1 — higher means a purer, more certain pitch. */
    data class Pitch(val frequency: Float, val clarity: Float)

    init {
        require(windowSize >= 256 && windowSize and (windowSize - 1) == 0) {
            "windowSize must be a power of two ≥ 256 (was $windowSize)"
        }
    }

    // FFT length: the smallest power of two ≥ 2·windowSize, so zero-padding
    // makes the autocorrelation linear rather than circular.
    private val fftSize = run {
        var s = 1
        while (s < windowSize * 2) s = s shl 1
        s
    }
    private val fft = FFT(fftSize)

    // Scratch — all overwritten every call, never read across calls.
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val work = FloatArray(windowSize)            // DC-removed copy of the input
    private val nsdf = FloatArray(windowSize / 2 + 1)
    private val powerPrefix = DoubleArray(windowSize + 1)
    private val keyLags = IntArray(MAX_KEY_MAXIMA)
    private val keyVals = FloatArray(MAX_KEY_MAXIMA)

    private val minLag = (sampleRate / MAX_FREQUENCY).toInt().coerceAtLeast(2)
    private val maxLag = (sampleRate / MIN_FREQUENCY).toInt().coerceAtMost(windowSize / 2)

    init {
        require(maxLag > minLag + 2) {
            "window of $windowSize is too short for $sampleRate Hz over the pitch range"
        }
    }

    /**
     * Analyse one window of mono PCM samples, normalised to roughly ±1.0.
     * [window] must be exactly [windowSize] long.
     *
     * @return the detected pitch, or null when the window holds no pitch the
     *         detector is confident about (silence, noise, unvoiced).
     */
    fun detect(window: FloatArray): Pitch? {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }

        // DC removal + energy, into the work buffer.
        var mean = 0.0
        for (v in window) mean += v
        val dc = (mean / windowSize).toFloat()

        var energy = 0.0
        for (i in 0 until windowSize) {
            val v = window[i] - dc
            work[i] = v
            energy += v.toDouble() * v
        }
        if (sqrt(energy / windowSize) < SILENCE_RMS) return null

        autocorrelate()
        buildNsdf()

        val lag = pickPeakLag() ?: return null
        val (refinedLag, refinedValue) = parabolicRefine(lag)
        if (refinedLag <= 0f) return null

        val frequency = sampleRate / refinedLag
        if (frequency !in MIN_FREQUENCY..MAX_FREQUENCY) return null

        return Pitch(frequency, refinedValue.coerceIn(0f, 1f))
    }

    /** Linear autocorrelation of [work] → r(τ) left in [re][0..windowSize]. */
    private fun autocorrelate() {
        for (i in 0 until windowSize) { re[i] = work[i]; im[i] = 0f }
        for (i in windowSize until fftSize) { re[i] = 0f; im[i] = 0f }   // zero-pad → linear

        fft.transform(re, im, inverse = false)
        for (i in 0 until fftSize) {
            re[i] = re[i] * re[i] + im[i] * im[i]   // power spectrum |X|²
            im[i] = 0f
        }
        fft.transform(re, im, inverse = true)
        // re[τ] now holds r(τ) = Σ work[n]·work[n+τ].
    }

    /** McLeod NSDF: nsdf(τ) = 2·r(τ) / Σ(work(j)² + work(j+τ)²), τ = 0..windowSize/2. */
    private fun buildNsdf() {
        powerPrefix[0] = 0.0
        for (n in 0 until windowSize) {
            powerPrefix[n + 1] = powerPrefix[n] + work[n].toDouble() * work[n]
        }
        val total = powerPrefix[windowSize]
        nsdf[0] = 1f
        for (tau in 1..windowSize / 2) {
            // Σx[j]² over [0, W-τ)  +  Σx[j]² over [τ, W)  — the two overlap sums.
            val m = powerPrefix[windowSize - tau] + total - powerPrefix[tau]
            nsdf[tau] = if (m > 1e-12) (2.0 * re[tau] / m).toFloat() else 0f
        }
    }

    /**
     * McLeod key-maximum peak picking. A "key maximum" is the tallest NSDF value
     * within one positive-going hump; the period is the first key maximum that
     * reaches [PEAK_PICK_RATIO] of the tallest one found.
     */
    private fun pickPeakLag(): Int? {
        var tau = minLag
        while (tau <= maxLag && nsdf[tau] > 0f) tau++   // skip the trivial τ≈0 hump

        var count = 0
        var highest = 0f
        while (tau <= maxLag && count < MAX_KEY_MAXIMA) {
            if (nsdf[tau] <= 0f) { tau++; continue }
            // Inside a positive hump — take its tallest point as the key maximum.
            var peakTau = tau
            var peakVal = nsdf[tau]
            while (tau <= maxLag && nsdf[tau] > 0f) {
                if (nsdf[tau] > peakVal) { peakVal = nsdf[tau]; peakTau = tau }
                tau++
            }
            keyLags[count] = peakTau
            keyVals[count] = peakVal
            if (peakVal > highest) highest = peakVal
            count++
        }
        if (count == 0 || highest < MIN_CLARITY) return null

        val threshold = highest * PEAK_PICK_RATIO
        repeat(count) { i -> if (keyVals[i] >= threshold) return keyLags[i] }
        return null
    }

    /**
     * Parabola through the chosen NSDF peak and its two neighbours, giving a
     * fractional lag. Returns (refinedLag, peakValue); falls back to the integer
     * lag at the search edges or when the three points are not concave.
     */
    private fun parabolicRefine(lag: Int): Pair<Float, Float> {
        if (lag !in (minLag + 1)..<maxLag) return lag.toFloat() to nsdf[lag]
        val y0 = nsdf[lag - 1]
        val y1 = nsdf[lag]
        val y2 = nsdf[lag + 1]
        val denom = y0 - 2f * y1 + y2
        if (denom >= 0f) return lag.toFloat() to y1   // not a concave peak
        val delta = (0.5f * (y0 - y2) / denom).coerceIn(-1f, 1f)
        val value = y1 - 0.25f * (y0 - y2) * delta
        return (lag + delta) to value
    }
}
