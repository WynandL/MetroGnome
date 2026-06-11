package com.example.metrognome.audio.tuner

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.example.metrognome.audio.dsp.PitchDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loopback self-calibration for the [Tuner].
 *
 * It plays a sequence of precisely-generated reference sine tones out the
 * speaker, recaptures them through the microphone, and measures how the
 * detected frequency differs from what was generated. The result is a single
 * [Result.correctionFactor] you can feed into [Tuner.calibrationFactor].
 *
 * ## What this can and cannot do — read this
 * The recaptured frequency differs from the generated one by the ratio of the
 * playback clock to the capture clock, *times* any systematic bias in the
 * detection chain. Two honest caveats follow from that:
 *
 *  - **It cannot fix absolute pitch error.** On almost every phone the DAC and
 *    ADC share one audio-codec crystal, so the clock ratio is exactly 1.0 and
 *    carries no information about how far that crystal sits from a true second.
 *    Correcting absolute error needs an *external* reference — a tuning fork —
 *    which is exactly what [calibrateFromReference] uses. A phone's audio
 *    crystal is typically within ±30 ppm ≈ ±0.05 cents of nominal anyway — far
 *    below human perception.
 *  - **What it genuinely does:** verify the whole capture→detect chain end to
 *    end, measure and cancel any *systematic algorithmic bias* in the detector
 *    (that bias is real and does transfer to a real instrument), correct a
 *    DAC/ADC clock split on the rare devices that have one, and — most usefully
 *    — report a measured [Result.accuracyCents] so the user *knows* the tuner is
 *    performing to spec.
 *
 * Treat it as a precision self-test that also trims residual bias, not as a
 * magic absolute calibrator.
 *
 * ## Conditions
 * Echo cancellation is deliberately not used (it would cancel the very tone we
 * are measuring) and media volume must be up. A run with the speaker muted or a
 * very noisy room returns a [Result] with [Result.confident] = false; the caller
 * should then keep [Tuner.calibrationFactor] at 1.0 and ask the user to retry.
 */
class TunerCalibrator {

    companion object {
        private val SAMPLE_RATE_CANDIDATES = intArrayOf(44_100, 48_000)
        private const val READ_CHUNK = 1024
        private const val WINDOW_SIZE = 8192

        /**
         * Reference tones swept through the loopback (Hz). Kept mid-range so any
         * phone speaker reproduces them cleanly — the correction is a broadband
         * clock/algorithm ratio, so mid-range tones characterise it fully.
         */
        private val REFERENCE_TONES = doubleArrayOf(196.0, 330.0, 523.0, 784.0, 1175.0)

        /** 16-bit amplitude of the generated sine — half-scale: audible, no clipping/break-up. */
        private const val TONE_AMPLITUDE = 16_000.0

        /** Each tone is played slightly longer than it is captured, to bracket the capture. */
        private const val TONE_MS = 1_100L
        private const val CAPTURE_MS = 1_000L

        /** Fraction of each capture discarded as system latency + attack + room ring-up. */
        private const val SETTLE_FRACTION = 0.45

        /** A loopback tone is a pure sine — clarity below this means the capture was contaminated. */
        private const val MIN_LOOPBACK_CLARITY = 0.95f

        /** Reject the run if the median ratio strays this far from 1.0 — that is a detection fault. */
        private const val MAX_RATIO_DEVIATION = 0.01

        /** Residual per-tone spread (cents) above which the measurement is too noisy to trust. */
        private const val MAX_RESIDUAL_CENTS = 3f

        // ── Tuning-fork (absolute reference) calibration ──────────────────────────
        /** How long to listen to the struck fork. */
        private const val REFERENCE_LISTEN_MS = 4_000L
        /** Initial slice skipped — the metallic strike attack before the fork settles to a pure tone. */
        private const val REFERENCE_SETTLE_MS = 400L
        /** Detections kept only within ±this fraction of the stated pitch — rejects harmonics/noise. */
        private const val REFERENCE_BAND = 0.04
        /** A struck fork is nearly a pure sine; detections below this clarity are discarded. */
        private const val REFERENCE_MIN_CLARITY = 0.9f
        /** Minimum stable detections for a trustworthy result. */
        private const val REFERENCE_MIN_SAMPLES = 12
        /**
         * Early finish: once this many clean in-band detections already form a
         * confident, tight result there is nothing more to learn, so the listen
         * stops short instead of waiting out [REFERENCE_LISTEN_MS]. A comfortable
         * margin above [REFERENCE_MIN_SAMPLES] guards against stopping on a brief
         * clean patch that then drifts.
         */
        private const val REFERENCE_EARLY_SAMPLES = 18
        /**
         * Once the result is in, hold briefly before returning so the progress bar's
         * 300 ms fill animation can sweep to 100 % rather than being cut off when the
         * dialog swaps in the result. Matches the bar's tween in CalibrationDialog.
         */
        private const val PROGRESS_SETTLE_MS = 320L
        /** Measurement spread (cents) above which the fork was not ringing cleanly enough. */
        private const val REFERENCE_MAX_SPREAD_CENTS = 4f
        /** |correction − 1| beyond this means the wrong fork was struck — not a device error. */
        private const val MAX_REFERENCE_DEVIATION = 0.03
    }

    /**
     * Outcome of a calibration run.
     *
     * @property correctionFactor multiply [Tuner] detections by this to remove
     *           the measured systematic bias (≈ 1.0 on a healthy device)
     * @property accuracyCents    largest residual error across the reference
     *           tones *after* correction — the honest "how good is it" figure
     * @property confident        true when the loopback was clean enough that
     *           [correctionFactor] is worth applying
     * @property perTone          per-tone diagnostics, for display
     */
    data class Result(
        val correctionFactor: Float,
        val accuracyCents: Float,
        val confident: Boolean,
        val perTone: List<ToneError>,
    )

    /** Per-tone diagnostic: the raw (pre-correction) error of one reference tone. */
    data class ToneError(val toneHz: Float, val rawErrorCents: Float, val clarity: Float)

    /**
     * Outcome of a tuning-fork (absolute) calibration run.
     *
     * Unlike the loopback run, this measures against a *physical* reference, so
     * [correctionFactor] corrects genuine absolute pitch error — not just
     * detector bias.
     *
     * @property correctionFactor multiplicative correction for `Tuner.calibrationFactor`
     * @property referenceHz      the fork's stated true frequency
     * @property measuredHz       the (uncorrected) frequency the device detected; NaN if none
     * @property spreadCents      consistency of the detections — the accuracy figure
     * @property sampleCount      how many stable detections were gathered
     * @property confident        true when the run was clean enough to apply
     */
    data class ReferenceResult(
        val correctionFactor: Float,
        val referenceHz: Float,
        val measuredHz: Float,
        val spreadCents: Float,
        val sampleCount: Int,
        val confident: Boolean,
    )

    private data class Measurement(
        val toneHz: Double,
        val detectedHz: Float?,
        val clarity: Float,
    )

    /**
     * Run the full loopback sweep. Suspends for roughly six seconds.
     *
     * @param onProgress invoked 0.0 → 1.0 as tones complete, for a progress bar
     * @return a [Result], or null only when the loopback could not run at all
     *         (microphone or speaker unavailable). A run that ran but was too
     *         dirty to trust returns a [Result] with [Result.confident] = false.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun calibrate(
        onProgress: (Float) -> Unit = {},
        onToneStart: (Double) -> Unit = {},
    ): Result? = withContext(Dispatchers.IO) {
        val opened = openRecord() ?: return@withContext null
        val rec = opened.first
        val sampleRate = opened.second
        val track = openTrack(sampleRate) ?: run {
            rec.release()
            return@withContext null
        }
        val detector = PitchDetector(sampleRate, WINDOW_SIZE)

        val measurements = ArrayList<Measurement>(REFERENCE_TONES.size)
        try {
            rec.startRecording()
            track.setVolume(1f)
            for ((index, tone) in REFERENCE_TONES.withIndex()) {
                onToneStart(tone)
                measurements += measureTone(rec, track, detector, sampleRate, tone)
                onProgress((index + 1f) / REFERENCE_TONES.size)
            }
        } catch (_: Exception) {
            return@withContext null
        } finally {
            try { rec.stop() } catch (_: Exception) { /* already stopped */ }
            rec.release()
            try { track.stop() } catch (_: Exception) { /* already stopped */ }
            track.release()
        }
        analyse(measurements)
    }

    /**
     * Calibrate against a struck tuning fork — the one path to *absolute*
     * accuracy. The app plays nothing; the user strikes a fork of known pitch
     * [referenceHz] and holds it to the microphone while this listens.
     *
     * Detection runs *while* audio arrives: the moment enough clean detections
     * confirm a confident, tight result the run finishes early rather than waiting
     * out the full window. A faint or noisy fork keeps the run going for the full
     * [REFERENCE_LISTEN_MS] and reports whatever it managed to gather. Either way
     * the caller receives the same [ReferenceResult] shape.
     *
     * @param referenceHz the fork's true frequency (e.g. 440.0 for an A440 fork)
     * @param onProgress  invoked 0.0 → 1.0 as the listening window elapses; snapped
     *                    to 1.0 on an early finish so a progress bar completes cleanly
     * @return a [ReferenceResult]; [ReferenceResult.confident] is false when the
     *         fork tone was not heard steadily. Null only if the mic was unavailable.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun calibrateFromReference(
        referenceHz: Double,
        onProgress: (Float) -> Unit = {},
    ): ReferenceResult? = withContext(Dispatchers.IO) {
        val opened = openRecord() ?: return@withContext null
        val rec = opened.first
        val sampleRate = opened.second
        val detector = PitchDetector(sampleRate, WINDOW_SIZE)

        try {
            rec.startRecording()
            listenForFork(rec, detector, sampleRate, referenceHz, onProgress)
        } catch (_: Exception) {
            null
        } finally {
            try { rec.stop() } catch (_: Exception) { /* already stopped */ }
            rec.release()
        }
    }

    // ── Per-tone measurement ─────────────────────────────────────────────────────

    /** Play one tone while capturing it; returns the pitch detection result. */
    private suspend fun measureTone(
        rec: AudioRecord,
        track: AudioTrack,
        detector: PitchDetector,
        sampleRate: Int,
        toneHz: Double,
    ): Measurement = coroutineScope {
        val player = launch { playTone(track, sampleRate, toneHz) }
        val captured = capture(rec, sampleRate)
        player.join()
        analyseTone(captured, detector, toneHz)
    }

    /** Generate a phase-continuous sine and stream it to the speaker for [TONE_MS]. */
    private fun playTone(track: AudioTrack, sampleRate: Int, toneHz: Double) {
        val totalSamples = (sampleRate * TONE_MS / 1000L).toInt()
        val chunk = ShortArray(READ_CHUNK)
        val phaseInc = 2.0 * PI * toneHz / sampleRate
        var phase = 0.0
        var written = 0

        track.play()
        while (written < totalSamples) {
            val n = minOf(chunk.size, totalSamples - written)
            for (i in 0 until n) {
                chunk[i] = (sin(phase) * TONE_AMPLITUDE).toInt().toShort()
                phase += phaseInc
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
            }
            if (track.write(chunk, 0, n) <= 0) break
            written += n
        }
        try { track.stop() } catch (_: Exception) { /* drained */ }
    }

    /** Record [CAPTURE_MS] of microphone audio, normalised to ±1.0. */
    private fun capture(rec: AudioRecord, sampleRate: Int): FloatArray {
        val total = (sampleRate * CAPTURE_MS / 1000L).toInt()
        val out = FloatArray(total)
        val buf = ShortArray(READ_CHUNK)
        var got = 0
        while (got < total) {
            val n = rec.read(buf, 0, minOf(buf.size, total - got))
            if (n <= 0) break
            for (i in 0 until n) out[got + i] = buf[i] / 32768f
            got += n
        }
        return if (got == total) out else out.copyOf(got)
    }

    /** Detect the pitch across the steady portion of one capture; return the median. */
    private fun analyseTone(
        samples: FloatArray,
        detector: PitchDetector,
        toneHz: Double,
    ): Measurement {
        val w = detector.windowSize
        if (samples.size < w) return Measurement(toneHz, null, 0f)

        val start = (samples.size * SETTLE_FRACTION).toInt()   // discard latency + attack + ring-up
        val hop = w / 2
        val buf = FloatArray(w)
        val freqs = ArrayList<Float>()
        var claritySum = 0.0

        var pos = start
        while (pos + w <= samples.size) {
            System.arraycopy(samples, pos, buf, 0, w)
            val pitch = detector.detect(buf)
            if (pitch != null) {
                freqs.add(pitch.frequency)
                claritySum += pitch.clarity
            }
            pos += hop
        }
        if (freqs.isEmpty()) return Measurement(toneHz, null, 0f)
        freqs.sort()
        return Measurement(toneHz, freqs[freqs.size / 2], (claritySum / freqs.size).toFloat())
    }

    // ── Tuning-fork measurement ──────────────────────────────────────────────────

    /**
     * Listen to the struck fork, detecting incrementally as audio arrives.
     *
     * Each time a fresh analysis window fills, its pitch is tested against the
     * fork band and kept if clean. As soon as a comfortable margin of clean
     * detections ([REFERENCE_EARLY_SAMPLES]) already forms a confident result the
     * run returns early; otherwise it listens for the full [REFERENCE_LISTEN_MS]
     * and reports whatever it gathered. Because the early and full paths feed the
     * same [evaluateReference], both report identical figures.
     */
    private suspend fun listenForFork(
        rec: AudioRecord,
        detector: PitchDetector,
        sampleRate: Int,
        referenceHz: Double,
        onProgress: (Float) -> Unit,
    ): ReferenceResult {
        val total = (sampleRate * REFERENCE_LISTEN_MS / 1000L).toInt()
        val w = detector.windowSize
        val settle = (sampleRate * REFERENCE_SETTLE_MS / 1000L).toInt()
        val hop = w / 2
        val lo = (referenceHz * (1.0 - REFERENCE_BAND)).toFloat()
        val hi = (referenceHz * (1.0 + REFERENCE_BAND)).toFloat()

        val out = FloatArray(total)
        val buf = ShortArray(READ_CHUNK)
        val window = FloatArray(w)
        val freqs = ArrayList<Float>()

        var got = 0
        var nextWindow = settle   // first window starts after the strike attack settles
        while (got < total) {
            val n = rec.read(buf, 0, minOf(buf.size, total - got))
            if (n <= 0) break
            for (i in 0 until n) out[got + i] = buf[i] / 32768f
            got += n
            onProgress(got.toFloat() / total)

            // Detect across every window that newly became complete this read.
            while (nextWindow + w <= got) {
                System.arraycopy(out, nextWindow, window, 0, w)
                val pitch = detector.detect(window)
                if (pitch != null && pitch.clarity >= REFERENCE_MIN_CLARITY &&
                    pitch.frequency in lo..hi
                ) {
                    freqs.add(pitch.frequency)
                }
                nextWindow += hop
            }

            // Early finish: enough clean detections that already agree tightly.
            if (freqs.size >= REFERENCE_EARLY_SAMPLES) {
                val result = evaluateReference(freqs, referenceHz)
                if (result.confident) return finish(result, onProgress)
            }
        }
        return finish(evaluateReference(freqs, referenceHz), onProgress)
    }

    /**
     * Fill the progress bar to full and let its animation settle before handing
     * back the result, so the dialog does not cut the bar off mid-sweep. See
     * [PROGRESS_SETTLE_MS].
     */
    private suspend fun finish(result: ReferenceResult, onProgress: (Float) -> Unit): ReferenceResult {
        onProgress(1f)
        delay(PROGRESS_SETTLE_MS.milliseconds)
        return result
    }

    /**
     * Reduce a set of clean, in-band fork detections to a correction factor
     * against the known [referenceHz]. Shared by the early-finish check and the
     * full-window fallback so both produce the same [ReferenceResult].
     */
    private fun evaluateReference(freqs: List<Float>, referenceHz: Double): ReferenceResult {
        if (freqs.size < REFERENCE_MIN_SAMPLES) {
            return ReferenceResult(
                correctionFactor = 1f,
                referenceHz = referenceHz.toFloat(),
                measuredHz = Float.NaN,
                spreadCents = Float.NaN,
                sampleCount = freqs.size,
                confident = false,
            )
        }

        // Robust core: drop the outer 20 % each side before measuring.
        val sorted = freqs.sorted()
        val drop = sorted.size / 5
        val core = sorted.subList(drop, sorted.size - drop)
        val median = core[core.size / 2]
        val spreadCents =
            (1200.0 * log2(core.last().toDouble() / core.first().toDouble())).toFloat()
        val correction = referenceHz / median

        val confident = spreadCents <= REFERENCE_MAX_SPREAD_CENTS &&
                abs(correction - 1.0) <= MAX_REFERENCE_DEVIATION

        return ReferenceResult(
            correctionFactor = correction.toFloat(),
            referenceHz = referenceHz.toFloat(),
            measuredHz = median,
            spreadCents = spreadCents,
            sampleCount = freqs.size,
            confident = confident,
        )
    }

    // ── Aggregation ──────────────────────────────────────────────────────────────

    /** Reduce the measurement set to a correction factor via median ratio of all valid tones. */
    private fun analyse(measurements: List<Measurement>): Result {
        val perTone = measurements.map { m ->
            val errCents =
                if (m.detectedHz != null) (1200.0 * log2(m.detectedHz / m.toneHz)).toFloat()
                else Float.NaN
            ToneError(m.toneHz.toFloat(), errCents, m.clarity)
        }

        val valid = measurements.filter {
            it.detectedHz != null && it.clarity >= MIN_LOOPBACK_CLARITY
        }
        if (valid.size < (measurements.size + 2) / 2) {
            return Result(1f, Float.NaN, confident = false, perTone = perTone)
        }

        val ratios = valid.map { it.detectedHz!! / it.toneHz }.sorted()
        val medianRatio = ratios[ratios.size / 2]
        val correction = 1.0 / medianRatio

        val accuracyCents = valid.maxOf { m ->
            abs(1200.0 * log2((m.detectedHz!! * correction) / m.toneHz))
        }.toFloat()

        val confident = abs(medianRatio - 1.0) <= MAX_RATIO_DEVIATION &&
                accuracyCents <= MAX_RESIDUAL_CENTS

        return Result(correction.toFloat(), accuracyCents, confident, perTone)
    }

    // ── Device setup ─────────────────────────────────────────────────────────────

    /** Open an AudioRecord on the raw mic, trying each candidate rate. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openRecord(): Pair<AudioRecord, Int>? {
        for (rate in SAMPLE_RATE_CANDIDATES) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue

            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(READ_CHUNK * 8),
                )
            } catch (_: Exception) {
                null
            }

            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) return rec to rate
            rec?.release()
        }
        return null
    }

    /** Open a mono streaming AudioTrack on the media output at [sampleRate]. */
    private fun openTrack(sampleRate: Int): AudioTrack? {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return null

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (_: Exception) {
            null
        }
    }
}
