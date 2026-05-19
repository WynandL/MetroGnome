package com.example.metrognome.audio.tuner

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.dsp.PitchDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Real-time instrument tuner.
 *
 * Listens on the microphone, runs [PitchDetector] (McLeod Pitch Method) over
 * overlapping analysis windows, and publishes a stable, note-mapped [Reading].
 *
 * ## Capture
 * Audio source is [MediaRecorder.AudioSource.MIC] — raw, with no voice
 * processing. Echo cancellation and noise suppression are intentionally NOT
 * applied: a tuner never plays a click to cancel, and a noise suppressor can
 * attack a sustained note (it looks like stationary noise). A one-pole DC
 * blocker removes the mic's DC offset, which would otherwise bias the pitch
 * detector's autocorrelation.
 *
 * ## Accuracy
 * Three things carry the accuracy:
 *  - a long [WINDOW_SIZE] window — many periods even for low notes, and fine
 *    frequency resolution;
 *  - parabolic interpolation inside [PitchDetector] — sub-cent fractional lag;
 *  - a short running median plus per-note EMA here — the median rejects stray
 *    octave blips, the EMA calms the needle without lagging a real note change.
 *
 * Residual *systematic* bias (detector + signal chain) can be measured and
 * removed with [TunerCalibrator]; feed its result into [calibrationFactor].
 *
 * ## What counts as a note
 * Raw pitch detection fires on any periodic sound — including speech. An
 * [AmbientDetector] sits between detection and [reading]: it profiles the room,
 * then only lets a *steady, sustained* tone reach the needle, so the display
 * never chases a voice or background noise. Its running commentary — what it
 * hears and what it is doing — is published on [ambient].
 *
 * ## Threading
 * One capture coroutine on [Dispatchers.IO] does both the blocking read and the
 * (sub-millisecond) FFT analysis. All public state is exposed as thread-safe
 * [StateFlow]s.
 */
class Tuner {

    companion object {
        /** Tried in order until one initialises. 44.1 kHz first — universally supported. */
        private val SAMPLE_RATE_CANDIDATES = intArrayOf(44_100, 48_000, 22_050, 16_000)

        /** Samples per AudioRecord.read. */
        private const val READ_CHUNK = 1024

        /** Analysis window — power of two, ~170–190 ms. Long, for low-note stability + resolution. */
        private const val WINDOW_SIZE = 8192

        /** New samples between analyses — 50 % overlap → ~11 readings/second. */
        private const val HOP_SIZE = 4096

        /** DC-blocker pole — removes mic DC offset; ~7 Hz corner, negligible above ~25 Hz. */
        private const val DC_BLOCK_R = 0.999f

        /** Readings kept for the running median — odd and small: rejects blips, stays responsive. */
        private const val MEDIAN_TAPS = 5

        /** EMA factor applied while the note holds steady — calms the needle. */
        private const val SMOOTHING_ALPHA = 0.25f

        /** Raw-RMS → 0..1 visualizer scaling, matched to the rhythm engine's meter. */
        private const val AMPLITUDE_GAIN = 1f / 8000f
    }

    /**
     * A stabilised, note-mapped tuning reading.
     *
     * @property frequency smoothed, calibration-corrected fundamental, in Hz
     * @property noteName  nearest note, e.g. "A" or "F#"
     * @property octave    scientific-pitch-notation octave (A4 = 440 Hz)
     * @property cents     signed offset from the nearest note, −50..+50
     * @property clarity   0..1 detection confidence from the latest window
     * @property inTune    true when |cents| is within [inTuneCents]
     */
    data class Reading(
        val frequency: Float,
        val noteName: String,
        val octave: Int,
        val cents: Float,
        val clarity: Float,
        val inTune: Boolean,
    )

    private val _reading = MutableStateFlow<Reading?>(null)
    /** Current tuning reading, or null when nothing is being played. */
    val reading: StateFlow<Reading?> = _reading.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    /** Normalised input level 0..1 — for a level meter. */
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile
    private var _listening = false

    private val _ambient = MutableStateFlow(AmbientReport.Idle)
    /** Live environment analysis — what the tuner hears and what it is doing about it. */
    val ambient: StateFlow<AmbientReport> = _ambient.asStateFlow()

    /** Reference pitch for A4, in Hz. Standard concert pitch is 440. */
    @Volatile
    var referenceHz: Float = 440f

    /** Half-width of the "in tune" band, in cents. */
    @Volatile
    var inTuneCents: Float = 1.5f

    /**
     * Multiplicative correction applied to every detected frequency — supply the
     * value from [TunerCalibrator.Result.correctionFactor]. 1.0 = uncalibrated.
     */
    @Volatile
    var calibrationFactor: Float = 1f

    private var record: AudioRecord? = null
    private var scope: CoroutineScope? = null

    /** Start listening. Caller must hold RECORD_AUDIO permission. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_listening) return

        val opened = openRecord() ?: run {
            // Mic unavailable (busy, unsupported, permission race) — stay silent, never crash.
            _listening = false
            return
        }
        val rec = opened.first
        val sampleRate = opened.second
        record = rec

        val detector = PitchDetector(sampleRate, WINDOW_SIZE)
        val ambientDetector = AmbientDetector(HOP_SIZE * 1000.0 / sampleRate)
        rec.startRecording()
        _listening = true

        val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = captureScope
        captureScope.launch {
            val buf = ShortArray(READ_CHUNK)

            // Sliding-window ring buffer of the most recent WINDOW_SIZE samples.
            val ring = FloatArray(WINDOW_SIZE)
            var ringPos = 0
            var filled = 0
            var sinceAnalysis = 0
            val analysis = FloatArray(WINDOW_SIZE)

            // One-pole DC blocker state.
            var dcX1 = 0f
            var dcY1 = 0f

            // Smoothing state.
            val recent = FloatArray(MEDIAN_TAPS)
            var writeIdx = 0
            var recentCount = 0
            var emaFreq = 0f
            var emaMidi = Int.MIN_VALUE

            try {
                while (isActive) {
                    val read = rec.read(buf, 0, READ_CHUNK)
                    if (read <= 0) {
                        if (read < 0) break else continue   // error → stop; 0 → retry
                    }

                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val raw = buf[i].toFloat()
                        sumSq += raw.toDouble() * raw

                        // DC-block, normalise to ±1.0, push into the ring.
                        val x = raw / 32768f
                        val y = x - dcX1 + DC_BLOCK_R * dcY1
                        dcX1 = x
                        dcY1 = y
                        ring[ringPos] = y
                        ringPos = (ringPos + 1) % WINDOW_SIZE
                    }
                    _amplitude.value =
                        (sqrt(sumSq / read).toFloat() * AMPLITUDE_GAIN).coerceIn(0f, 1f)

                    if (filled < WINDOW_SIZE) filled += read
                    sinceAnalysis += read
                    if (filled < WINDOW_SIZE || sinceAnalysis < HOP_SIZE) continue
                    sinceAnalysis = 0

                    // Copy the ring out in chronological order — oldest sample sits at ringPos.
                    for (i in 0 until WINDOW_SIZE) analysis[i] = ring[(ringPos + i) % WINDOW_SIZE]

                    val pitch = detector.detect(analysis)
                    _ambient.value = ambientDetector.observe(pitch, windowRms(analysis))

                    if (!_ambient.value.locked) {
                        // Not a confident instrument note — park the needle, reset smoothing.
                        _reading.value = null
                        recentCount = 0
                        writeIdx = 0
                        emaMidi = Int.MIN_VALUE
                        continue
                    }
                    // Locked, but this frame carried no fresh pitch — hold the last reading.
                    if (pitch == null) continue

                    val corrected = pitch.frequency * calibrationFactor

                    // Running median — rejects a stray octave-halved/doubled blip.
                    recent[writeIdx] = corrected
                    writeIdx = (writeIdx + 1) % MEDIAN_TAPS
                    if (recentCount < MEDIAN_TAPS) recentCount++
                    val median = recent.copyOf(recentCount).also { it.sort() }[recentCount / 2]

                    // Per-note EMA: snap on a note change, glide while the note holds.
                    val nearestOfMedian =
                        (69.0 + 12.0 * log2(median / referenceHz)).roundToInt()
                    if (nearestOfMedian != emaMidi) {
                        emaFreq = median
                        emaMidi = nearestOfMedian
                    } else {
                        emaFreq += SMOOTHING_ALPHA * (median - emaFreq)
                    }

                    _reading.value = toReading(emaFreq, pitch.clarity)
                }
            } catch (_: Exception) {
                // AudioRecord released mid-read during stop() — expected, end quietly.
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped / uninitialised — nothing to do.
        }
        record?.release()
        record = null
        _listening = false
        _amplitude.value = 0f
        _reading.value = null
        _ambient.value = AmbientReport.Idle
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    /** RMS of one analysis window — the level handed to the ambient detector. */
    private fun windowRms(buf: FloatArray): Float {
        var sum = 0.0
        for (v in buf) sum += v.toDouble() * v
        return sqrt(sum / buf.size).toFloat()
    }

    /** Map a frequency to the nearest note, octave and cents offset. */
    private fun toReading(frequency: Float, clarity: Float): Reading {
        val midiExact = 69.0 + 12.0 * log2(frequency / referenceHz)
        val nearest = midiExact.roundToInt()
        val cents = ((midiExact - nearest) * 100.0).toFloat()
        return Reading(
            frequency = frequency,
            noteName = NoteNames.nameOf(nearest),
            octave = NoteNames.octaveOf(nearest),
            cents = cents,
            clarity = clarity,
            inTune = abs(cents) <= inTuneCents,
        )
    }

    /** Open an AudioRecord, trying each candidate rate until one initialises cleanly. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openRecord(): Pair<AudioRecord, Int>? {
        for (rate in SAMPLE_RATE_CANDIDATES) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue   // rate unsupported on this device

            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(READ_CHUNK * 8),   // headroom against overrun
                )
            } catch (_: Exception) {
                null
            }

            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) return rec to rate
            rec?.release()
        }
        return null
    }
}
