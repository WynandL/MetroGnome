package com.example.metrognome.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Note-onset detector: **SuperFlux** (Böck & Widmer, DAFx 2013) with online peak picking.
 *
 * An onset detection function measures how much *new* energy arrives in each frame; a
 * plucked or struck note is a sharp rise across many bands at once, which no sustained
 * tone, decay or room noise produces. This is the boundary a note tracker segments on
 * (Bello et al., "A Tutorial on Onset Detection in Music Signals", 2005), and spectral
 * flux, the half-wave-rectified frame-to-frame rise of the magnitude spectrum, is the
 * simple function that does best at it (Dixon, "Onset Detection Revisited", 2006).
 *
 * SuperFlux adds two things to plain spectral flux, both kept here:
 *
 *  1. **A log-frequency filterbank** with quarter-tone bands, so a rise counts once per
 *     semitone-ish region rather than once per FFT bin, and low notes (many bins per
 *     band up high, one bin per band down low) weigh the same as high ones.
 *  2. **A maximum filter across neighbouring bands of the previous frame** before the
 *     difference is taken. A note whose pitch wobbles (vibrato, a bend, a slide) moves
 *     energy into the next band over and would otherwise read as a stream of small
 *     onsets; comparing against the max of the neighbours cancels that entirely while a
 *     genuinely new note, arriving where nothing was, still shows in full.
 *
 * Magnitudes are log-compressed (`log10(1 + λ·m)`) so a soft pluck and a hard one both
 * clear the threshold and the loud one does not swamp the local mean for its neighbours.
 *
 * Peak picking follows madmom's online rule: frame m is an onset when its flux is the
 * maximum of the last [PRE_MAX_MS] plus one frame of lookahead, stands out from the mean
 * of the last [PRE_AVG_MS] (by [threshold] added, or [RELATIVE_RATIO] multiplied, see
 * there), and lies at least [COMBINE_MS] after the previous onset. The one-frame
 * lookahead means an onset is reported on the frame *after* its peak
 * ([reportDelayFrames]); callers place it in time accordingly.
 *
 * Pure DSP over sample arrays, no Android, allocation-free per frame after construction,
 * not thread-safe (one per capture thread).
 *
 * @param windowSize STFT window (a short one: onsets are about time resolution, not pitch)
 * @param hopSize    samples between successive [process] calls, used only to turn the
 *                   millisecond constants into frame counts
 */
class OnsetDetector(
    sampleRate: Int,
    val windowSize: Int = DEFAULT_WINDOW,
    val hopSize: Int = DEFAULT_HOP,
    private val threshold: Float = DEFAULT_THRESHOLD,
) {
    companion object {
        const val DEFAULT_WINDOW = 2048
        const val DEFAULT_HOP = 512

        /**
         * Rise of the flux above its local mean that counts as an onset outright. Set
         * against the log compression below: a moderate note clears it, steady room noise,
         * a decaying note and a vibrato do not (see `OnsetDetectorTest`).
         */
        const val DEFAULT_THRESHOLD = 1.2f

        /**
         * The second way in, for soft playing: a peak that stands [RELATIVE_RATIO] times
         * above the local mean and at least [RELATIVE_MIN] high is an onset even if the
         * additive bar is out of reach. Below about -40 dBFS the log compression is
         * nearly linear and the additive threshold would scale the whole detector to the
         * player's volume; a multiplicative bar (aubio's picker works on the local median
         * this way) does not. [RELATIVE_MIN] keeps the quietest fluctuations of a silent
         * room from ever qualifying, whatever their ratio.
         */
        private const val RELATIVE_RATIO = 3f
        private const val RELATIVE_MIN = 0.35f

        /** Log-compression strength: `log10(1 + LAMBDA * magnitude)`, magnitudes with a full-scale sine at 1. */
        private const val LAMBDA = 100f

        /** Filterbank range and density: quarter-tone bands from the bottom of a bass to well above any fundamental's partials. */
        private const val BAND_MIN_HZ = 55f
        private const val BAND_MAX_HZ = 11_000f
        private const val BANDS_PER_OCTAVE = 24

        /** Half-width, in bands, of the maximum filter on the previous frame (3 bands wide, the paper's value). */
        private const val MAX_FILTER_HALF = 1

        /** The difference is taken against the frame this many windows back, spanning about half a window. */
        private const val DIFF_SPAN_RATIO = 0.5f

        // Peak picking (madmom OnsetPeakPickingProcessor, online mode with one frame of lookahead).
        private const val PRE_MAX_MS = 30.0
        private const val PRE_AVG_MS = 100.0
        private const val COMBINE_MS = 50.0
        private const val POST_MAX_FRAMES = 1
    }

    /** What one frame yielded: its flux (the detection function) and whether an onset is reported. */
    data class Frame(val flux: Float, val onset: Boolean)

    init {
        require(windowSize >= 256 && windowSize and (windowSize - 1) == 0) { "windowSize must be a power of two >= 256" }
        require(hopSize in 1..windowSize) { "hopSize must be 1..windowSize" }
    }

    /** Frames between an onset's own frame and the [process] call that reports it. */
    val reportDelayFrames: Int get() = POST_MAX_FRAMES

    private val fft = FFT(windowSize)
    private val re = FloatArray(windowSize)
    private val im = FloatArray(windowSize)
    private val hann = FloatArray(windowSize) { 0.5f * (1f - cos(2.0 * PI * it / windowSize).toFloat()) }
    private val magScale = 2f / hann.sum()   // full-scale sine -> magnitude 1 in its bin
    private val bins = windowSize / 2 + 1
    private val mag = FloatArray(bins)

    // Filterbank: triangular quarter-tone bands, each normalised to unit area.
    private val bandStart: IntArray
    private val bandWeights: Array<FloatArray>
    private val bandCount: Int

    init {
        val nyquist = sampleRate / 2f
        val top = min(BAND_MAX_HZ, nyquist * 0.95f)
        // Centre bins, unique: down low, several quarter-tone centres fall in one bin and
        // would make empty or duplicate filters, so only the first centre per bin is kept.
        val centres = ArrayList<Int>()
        var k = 0
        while (true) {
            val f = BAND_MIN_HZ * 2.0.pow(k.toDouble() / BANDS_PER_OCTAVE).toFloat()
            if (f > top) break
            val bin = (f * windowSize / sampleRate).roundToInt()
            if (centres.isEmpty() || bin > centres.last()) centres += bin
            k++
        }
        bandCount = centres.size - 2
        require(bandCount >= 8) { "sample rate $sampleRate too low for the onset filterbank" }
        bandStart = IntArray(bandCount)
        bandWeights = Array(bandCount) { b ->
            val lo = centres[b]; val c = centres[b + 1]; val hi = centres[b + 2]
            bandStart[b] = lo
            val w = FloatArray(hi - lo + 1)
            for (i in lo..hi) {
                w[i - lo] = when {
                    i < c -> (i - lo).toFloat() / (c - lo)
                    i > c -> (hi - i).toFloat() / (hi - c)
                    else -> 1f
                }
            }
            val sum = w.sum()
            if (sum > 0f) for (i in w.indices) w[i] /= sum
            w
        }
    }

    // Frame history for the difference and the peak picker.
    private val diffFrames = max(1, (windowSize * DIFF_SPAN_RATIO / hopSize).roundToInt())
    private val logBands = Array(diffFrames + 1) { FloatArray(bandCount) }   // ring of recent log-band frames
    private var frameIndex = 0L                                              // frames processed so far
    private val hopMs = hopSize * 1000.0 / sampleRate
    private val preMaxFrames = max(1, (PRE_MAX_MS / hopMs).roundToInt())
    private val preAvgFrames = max(1, (PRE_AVG_MS / hopMs).roundToInt())
    private val combineFrames = max(1, (COMBINE_MS / hopMs).roundToInt())
    private val fluxRing = FloatArray(max(preAvgFrames, preMaxFrames) + POST_MAX_FRAMES + 2)
    private var lastOnsetFrame = Long.MIN_VALUE / 2

    /** Forget all history, as after a capture restart. */
    fun reset() {
        for (f in logBands) f.fill(0f)
        fluxRing.fill(0f)
        frameIndex = 0
        lastOnsetFrame = Long.MIN_VALUE / 2
    }

    /**
     * Analyse the most recent [windowSize] samples (normalised to about +-1), one call per
     * [hopSize] new samples. The returned [Frame.onset] refers to the frame [reportDelayFrames]
     * calls ago.
     */
    fun process(window: FloatArray): Frame {
        require(window.size == windowSize) { "expected $windowSize samples, got ${window.size}" }

        // Windowed magnitude spectrum.
        var mean = 0f
        for (v in window) mean += v
        mean /= windowSize
        for (i in 0 until windowSize) { re[i] = (window[i] - mean) * hann[i]; im[i] = 0f }
        fft.transform(re, im, inverse = false)
        for (i in 0 until bins) mag[i] = sqrt(re[i] * re[i] + im[i] * im[i]) * magScale

        // Filterbank + log compression into this frame's slot of the ring.
        val slot = (frameIndex % (diffFrames + 1)).toInt()
        val cur = logBands[slot]
        for (b in 0 until bandCount) {
            val w = bandWeights[b]
            val start = bandStart[b]
            var acc = 0f
            for (i in w.indices) acc += w[i] * mag[start + i]
            cur[b] = log10(1f + LAMBDA * acc)
        }

        // SuperFlux: positive difference against the max-filtered frame diffFrames back.
        var flux = 0f
        if (frameIndex >= diffFrames) {
            val prev = logBands[((frameIndex - diffFrames) % (diffFrames + 1)).toInt()]
            for (b in 0 until bandCount) {
                var m = prev[b]
                for (d in 1..MAX_FILTER_HALF) {
                    if (b - d >= 0) m = max(m, prev[b - d])
                    if (b + d < bandCount) m = max(m, prev[b + d])
                }
                val rise = cur[b] - m
                if (rise > 0f) flux += rise
            }
        }
        fluxRing[(frameIndex % fluxRing.size).toInt()] = flux

        // Peak picking on the frame POST_MAX_FRAMES back, now that its lookahead exists.
        val m = frameIndex - POST_MAX_FRAMES
        var onset = false
        if (m >= preAvgFrames) {
            val fm = fluxAt(m)
            var isMax = true
            for (j in (m - preMaxFrames)..(m + POST_MAX_FRAMES)) {
                if (j != m && fluxAt(j) > fm) { isMax = false; break }
            }
            if (isMax) {
                var sum = 0f
                for (j in (m - preAvgFrames)..m) sum += fluxAt(j)
                val meanFlux = sum / (preAvgFrames + 1)
                val clears = fm >= meanFlux + threshold ||
                    (fm >= RELATIVE_RATIO * meanFlux && fm >= RELATIVE_MIN)
                if (clears && m - lastOnsetFrame >= combineFrames) {
                    onset = true
                    lastOnsetFrame = m
                }
            }
        }
        frameIndex++
        return Frame(flux, onset)
    }

    private fun fluxAt(frame: Long): Float =
        if (frame < 0) 0f else fluxRing[(frame % fluxRing.size).toInt()]
}
