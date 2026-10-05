package com.example.metrognome.audio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
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

        // ── Presence probe / noise whitening (see [presenceAt], [learnNoise]) ──────

        /** Half-width (cents) of the lag band [presenceAt] scans around the target. */
        private const val PRESENCE_TOLERANCE_CENTS = 60f

        /** Spectral-subtraction over-subtraction factor — >1 removes a margin of noise. */
        private const val NOISE_OVERSUBTRACT = 1.5f

        /** Spectral floor: never attenuate a bin below this fraction of its own magnitude,
         *  which keeps the subtraction from punching holes ("musical noise") into the tone. */
        private const val NOISE_SPECTRAL_FLOOR = 0.10f

        /**
         * Over-subtraction for [detectAbove]. Higher than [NOISE_OVERSUBTRACT] because the
         * reference is taken a few frames before the onset and the ringing partials it holds
         * have barely decayed by the time the residual is measured, so a margin of 1.0 would
         * leave a sliver of each old partial standing; a note plucked over a sustained chord
         * is still far above what this removes.
         */
        private const val RESIDUAL_OVERSUBTRACT = 2.0f

        // ── Partial evidence (see [partialEvidence]) ─────────────────────────────────

        /** Partials of the old note examined: f0..8f0, skipping those the multiple shares. */
        private const val EVIDENCE_PARTIALS = 8

        /** Half-width (cents) searched for a partial's peak; vibrato and calibration drift. */
        private const val EVIDENCE_PEAK_CENTS = 30.0

        /** Floor band half-width cap as a fraction of f0: neighbouring shared partials are f0 away. */
        private const val EVIDENCE_FLOOR_SPAN = 0.6f

        /** Fewest floor bins a median is taken over; fewer and the partial is not judged. */
        private const val EVIDENCE_MIN_FLOOR_BINS = 4
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
    private val hann = FloatArray(windowSize) { 0.5f - 0.5f * cos(2.0 * PI * it / (windowSize - 1)).toFloat() }
    private val evidenceMag = FloatArray(fftSize / 2 + 1)
    private val evidenceFloor = FloatArray(fftSize / 2 + 1)

    private val minLag = (sampleRate / MAX_FREQUENCY).toInt().coerceAtLeast(2)
    private val maxLag = (sampleRate / MIN_FREQUENCY).toInt().coerceAtMost(windowSize / 2)

    // ── Learned room-noise spectrum (for the presence probe only) ─────────────────
    private val noiseAccum = DoubleArray(fftSize / 2 + 1)   // running sum during learning
    private val noiseMag   = FloatArray(fftSize / 2 + 1)    // frozen average magnitude
    private var noiseFrames = 0
    private var noiseReady = false

    /**
     * When true, [presenceAt] subtracts the learned room-noise spectrum before measuring,
     * lifting a buried tone above stationary background. Has no effect until a noise
     * profile has been learned ([learnNoise] + [finalizeNoise]). Never touches [detect],
     * so the displayed pitch and its accuracy are unaffected either way. Runtime-toggleable
     * so the benefit can be A/B compared on a real device.
     */
    var denoisePresence: Boolean = true

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
        if (!loadWork(window)) return null   // DC-removed into work; false if silent

        autocorrelate()
        buildNsdf()

        val lag = pickPeakLag() ?: return null
        val (refinedLag, refinedValue) = parabolicRefine(lag)
        if (refinedLag <= 0f) return null

        val frequency = sampleRate / refinedLag
        if (frequency !in MIN_FREQUENCY..MAX_FREQUENCY) return null

        return Pitch(frequency, refinedValue.coerceIn(0f, 1f))
    }

    /**
     * Presence probe for an *already-known* note: the NSDF strength (0..1) at the lag
     * for [targetHz], i.e. "is this exact note still here?" — regardless of whether it
     * is the globally tallest pitch. This is the signal a lock-hold layer needs to ride
     * out a louder interferer or broadband noise that would otherwise defeat [detect]'s
     * single-best peak pick.
     *
     * A small ±[PRESENCE_TOLERANCE_CENTS] band is scanned so the note may drift a little
     * (vibrato, a slow tuning glide) without the probe collapsing. When [denoisePresence]
     * is on and a room-noise profile has been learned, the noise spectrum is subtracted
     * first, which lifts a buried tone above stationary background.
     *
     * This never influences the displayed pitch — it is a gate signal only. Returns 0
     * when the window is silent or [targetHz] is out of range.
     */
    fun presenceAt(window: FloatArray, targetHz: Float): Float {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }
        if (targetHz !in MIN_FREQUENCY..MAX_FREQUENCY) return 0f
        if (!loadWork(window)) return 0f
        if (denoisePresence && noiseReady) whitenWork()
        autocorrelate()
        buildNsdf()
        return peakNear(targetHz)
    }

    /**
     * Fold one window's magnitude spectrum into the room-noise estimate. Call only on
     * background-only frames (e.g. during ambient profiling, before any note is played).
     * Silent frames are ignored. [finalizeNoise] freezes the average for [presenceAt].
     */
    fun learnNoise(window: FloatArray) {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }
        if (!loadWork(window)) return
        forwardFft()
        val half = fftSize / 2
        for (i in 0..half) noiseAccum[i] += sqrt((re[i] * re[i] + im[i] * im[i]).toDouble())
        noiseFrames++
    }

    /** Freeze the averaged noise spectrum so [presenceAt] can subtract it. No-op if nothing learned. */
    fun finalizeNoise() {
        if (noiseFrames <= 0) return
        val half = fftSize / 2
        for (i in 0..half) noiseMag[i] = (noiseAccum[i] / noiseFrames).toFloat()
        noiseReady = true
    }

    // â”€â”€ Onset residual: the pitch of what is *new* (see [detectAbove]) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Length of the magnitude-spectrum arrays [magnitudeSpectrum] fills and [detectAbove] reads. */
    val spectrumSize: Int get() = fftSize / 2 + 1

    /**
     * Magnitude spectrum of [window] at this detector's own FFT resolution, written into
     * [out] (length [spectrumSize]). Meant to be taken on the frames just *before* a note
     * onset, as the reference [detectAbove] subtracts. Silent windows write zeros.
     */
    fun magnitudeSpectrum(window: FloatArray, out: FloatArray) {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }
        require(out.size == spectrumSize) { "expected $spectrumSize bins, got ${out.size}" }
        if (!loadWork(window)) { out.fill(0f); return }
        forwardFft()
        for (i in 0 until spectrumSize) out[i] = sqrt(re[i] * re[i] + im[i] * im[i])
    }

    /**
     * Detect the pitch of what is new in [window] relative to [reference], a spectrum from
     * [magnitudeSpectrum] taken just before an onset: the reference is spectrally
     * subtracted (Boll's method, the same step [presenceAt] uses against room noise) and
     * MPM runs on the residual.
     *
     * This exists because MPM is monophonic. When a note is plucked while the previous one
     * still rings, the two together repeat at their *common* period, so the tallest NSDF
     * peak sits at the missing fundamental of the pair (C4 over a ringing G4 reads as C3)
     * and the new note's own peak is often not tall enough for [PEAK_PICK_RATIO]. The
     * ringing note's partials are steady across the onset, so subtracting the spectrum
     * from just before it removes them and leaves the new note alone. On the first note
     * after silence the reference is empty and this is exactly [detect].
     *
     * Returns null when nothing periodic is left once the reference is removed.
     */
    fun detectAbove(window: FloatArray, reference: FloatArray): Pitch? {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }
        require(reference.size == spectrumSize) { "expected $spectrumSize bins, got ${reference.size}" }
        if (!loadWork(window)) return null
        subtractSpectrum(reference, RESIDUAL_OVERSUBTRACT)
        autocorrelate()
        buildNsdf()
        val lag = pickPeakLag() ?: return null
        val (refinedLag, refinedValue) = parabolicRefine(lag)
        if (refinedLag <= 0f) return null
        val frequency = sampleRate / refinedLag
        if (frequency !in MIN_FREQUENCY..MAX_FREQUENCY) return null
        return Pitch(frequency, refinedValue.coerceIn(0f, 1f))
    }

    /**
     * How clearly a note at [f0] is still physically sounding in [window], judged only by
     * the partials a note at [multiple]·[f0] cannot produce (for an octave: f0, 3f0, 5f0...).
     *
     * [presenceAt] cannot answer this: a note at an integer multiple repeats at f0's period
     * too, so the NSDF there reads ~1.0 whether f0 is present or not. The non-shared
     * partials can: a note at the multiple puts no energy there, so energy found there is
     * evidence of the old note (or of some other source in that band; this is a narrow
     * test for one ambiguity, not source separation). Returns the largest
     * peak-to-local-floor magnitude ratio among them (Hann-windowed spectrum, peak within
     * ±[EVIDENCE_PEAK_CENTS] (at least one bin); floor = median of the band
     * outside that search plus a main lobe, and short of the neighbouring shared partials'
     * main lobes), so ~1
     * means nothing there and the value scales with how far the partial stands above the
     * room. 0 for a silent window.
     *
     * Returns NaN when the spectrum cannot resolve the question: when f0 is so low that no
     * partial has room for a floor band between its own main lobe and its neighbours' (about
     * 36 Hz at 44.1 kHz and 39 Hz at 48 kHz with an 8192 window). That is "unknown", not
     * "absent", and callers must not treat it as absence.
     */
    fun partialEvidence(window: FloatArray, f0: Float, multiple: Int): Float {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }
        if (multiple < 2 || f0 <= 0f || !loadWork(window)) return 0f
        for (i in 0 until windowSize) { re[i] = work[i] * hann[i]; im[i] = 0f }
        for (i in windowSize until fftSize) { re[i] = 0f; im[i] = 0f }
        fft.transform(re, im, inverse = false)
        val half = fftSize / 2
        for (i in 0..half) evidenceMag[i] = sqrt(re[i] * re[i] + im[i] * im[i])

        val binHz = sampleRate.toFloat() / fftSize
        // Hann main lobe is ±2 bins of the unpadded window, ±4 of this 2x-padded FFT.
        val peakHalfMin = 2 * fftSize / windowSize
        val mainLobeHz = peakHalfMin * binHz
        // The floor band ends a bin short of where the neighbouring shared partial's main
        // lobe begins (f0 away), and never wider than EVIDENCE_FLOOR_SPAN of f0.
        val floorHalf = (minOf(f0 * EVIDENCE_FLOOR_SPAN, f0 - mainLobeHz) / binHz).toInt() - 1
        var best = Float.NaN
        for (m in 1..EVIDENCE_PARTIALS) {
            if (m % multiple == 0) continue
            val hz = m * f0
            if (hz > sampleRate * 0.45f) break
            val centre = (hz / binHz).roundToInt()
            // A partial's peak sits at its own frequency, so the search only needs the
            // ±EVIDENCE_PEAK_CENTS tolerance (searching the whole main lobe let the largest of
            // several noise bins pose as a peak); the floor excludes the main lobe around it.
            val searchHalf = maxOf(1, (centre * (2.0.pow(EVIDENCE_PEAK_CENTS / 1200.0) - 1.0)).roundToInt())
            val exclude = searchHalf + peakHalfMin
            var peak = 0f
            for (i in (centre - searchHalf).coerceAtLeast(0)..(centre + searchHalf).coerceAtMost(half))
                if (evidenceMag[i] > peak) peak = evidenceMag[i]
            var n = 0
            for (i in (centre - floorHalf).coerceAtLeast(0)..(centre + floorHalf).coerceAtMost(half)) {
                if (abs(i - centre) <= exclude) continue
                evidenceFloor[n++] = evidenceMag[i]
            }
            if (n < EVIDENCE_MIN_FLOOR_BINS) continue   // no room for a floor at this resolution
            java.util.Arrays.sort(evidenceFloor, 0, n)
            val floor = evidenceFloor[n / 2].coerceAtLeast(1e-9f)
            val ratio = peak / floor
            if (best.isNaN() || ratio > best) best = ratio
        }
        return best
    }

    /** DC-remove [window] into [work]; returns false if the window is below the silence floor. */
    private fun loadWork(window: FloatArray): Boolean {
        var mean = 0.0
        for (v in window) mean += v
        val dc = (mean / windowSize).toFloat()
        var energy = 0.0
        for (i in 0 until windowSize) {
            val v = window[i] - dc
            work[i] = v
            energy += v.toDouble() * v
        }
        return sqrt(energy / windowSize) >= SILENCE_RMS
    }

    /** Forward FFT of the zero-padded [work] buffer into [re]/[im]. */
    private fun forwardFft() {
        for (i in 0 until windowSize) { re[i] = work[i]; im[i] = 0f }
        for (i in windowSize until fftSize) { re[i] = 0f; im[i] = 0f }
        fft.transform(re, im, inverse = false)
    }

    /**
     * Spectral subtraction of the learned noise magnitude, applied in place to [work].
     * A global inverse-FFT scale would cancel in the NSDF, so only the *shape* change
     * (noise removed) matters; the tone's period peak survives, its noise floor drops.
     */
    private fun whitenWork() = subtractSpectrum(noiseMag, NOISE_OVERSUBTRACT)

    /**
     * Subtract [reference] magnitudes (times [overSubtract]) from [work]'s spectrum, in
     * place, keeping the phase; each bin is floored at [NOISE_SPECTRAL_FLOOR] of itself so
     * the subtraction cannot punch holes into what remains.
     */
    private fun subtractSpectrum(reference: FloatArray, overSubtract: Float) {
        forwardFft()
        val half = fftSize / 2
        var before = 0.0
        var after = 0.0
        for (i in 0 until fftSize) {
            val ni = if (i <= half) i else fftSize - i   // magnitude spectrum is symmetric
            val mag = sqrt(re[i] * re[i] + im[i] * im[i])
            if (mag > 1e-9f) {
                val clean = (mag - overSubtract * reference[ni])
                    .coerceAtLeast(NOISE_SPECTRAL_FLOOR * mag)
                val g = clean / mag
                re[i] *= g
                im[i] *= g
                before += mag.toDouble() * mag
                after += clean.toDouble() * clean
            }
        }
        lastResidualPowerFraction = if (before > 0.0) (after / before).toFloat() else 0f
        fft.transform(re, im, inverse = true)
        for (i in 0 until windowSize) work[i] = re[i]
    }

    /**
     * After [detectAbove]: the fraction of the window's spectral power that survived the
     * subtraction. What the reference explains is removed; what is new stays. The floor
     * alone leaves about [NOISE_SPECTRAL_FLOOR]² (1%), so a value near that means nothing
     * new arrived and the residual's pitch is only the ghost of what was already sounding,
     * which MPM, being level-blind, reads as confidently as the real thing.
     */
    var lastResidualPowerFraction: Float = 0f
        private set

    /** Highest NSDF value within ±[PRESENCE_TOLERANCE_CENTS] of the lag for [targetHz]. */
    private fun peakNear(targetHz: Float): Float {
        val centreLag = sampleRate / targetHz
        val widen = 2.0.pow(PRESENCE_TOLERANCE_CENTS / 1200.0)
        val loLag = (centreLag / widen).toInt().coerceAtLeast(minLag)   // sharper note → shorter lag
        val hiLag = (centreLag * widen).toInt().coerceAtMost(maxLag)
        if (hiLag < loLag) return 0f
        var best = 0f
        for (tau in loLag..hiLag) if (nsdf[tau] > best) best = nsdf[tau]
        return best.coerceIn(0f, 1f)
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
        // Skip the trivial τ≈0 hump from lag 1, not from minLag: for a high note
        // (4 kHz at 44.1 kHz has a period of 11 lags, minLag is 9) the zero-lag hump
        // ends well before minLag, and starting there would skip the fundamental's
        // own hump as if it were the trivial one, reporting the octave below.
        var tau = 1
        while (tau <= maxLag && nsdf[tau] > 0f) tau++

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
            // A hump peaking under minLag is above MAX_FREQUENCY: not a candidate.
            if (peakTau < minLag) continue
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
