package com.example.metrognome.audio.rhythm

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import com.example.metrognome.audio.dsp.ClapDetector
import com.example.metrognome.audio.dsp.OnsetDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Listens via the microphone and emits accurately-timed beat (onset) detections.
 *
 * ## Capture
 * Audio source is [MediaRecorder.AudioSource.MIC] — raw, with no automatic
 * voice processing. NoiseSuppressor is intentionally NOT applied: it filters
 * the very short transients (claps) we exist to detect. AcousticEchoCanceler
 * (AEC) IS applied when available — it knows what the speaker is playing (the
 * metronome click) and subtracts it from the mic, so the click never scores.
 *
 * ## Timing — the heart of the engine
 * Detection accuracy is dominated by *when* an onset is timestamped:
 *
 *  - Onsets are localised to ~6 ms inside each buffer by [OnsetDetector],
 *    not quantised to the 20–80 ms audio buffer.
 *  - Each buffer's frames are mapped to real time by [FrameClock] using
 *    [AudioRecord.getTimestamp], which reports the precise capture instant of
 *    a known frame — removing the device-dependent input latency a naïve
 *    `now()` would bake in. [FrameClock] also tracks buffer overruns (which
 *    silently drop frames) so the mapping cannot drift mid-session.
 *  - All emitted timestamps are on the [SystemClock.elapsedRealtime] timebase —
 *    the exact clock the game loop uses — so there is zero cross-clock drift.
 *    (The old design mixed wall-clock time, which can jump on NTP/DST changes.)
 *
 * ## Click rejection — two modes
 * Default ([classifyClaps] = false): an amplitude [OnsetDetector] reports every
 * transient and [suppressUntilMs] gates the metronome click temporally — a wide
 * window when AEC is unavailable, a narrow one when AEC is active. Used by the
 * timing-bonus path (Speed Trainer, Practice).
 *
 * Spectral ([classifyClaps] = true): a [ClapDetector] tells the click apart from a
 * clap by its 1100 Hz signature and emits **only claps**, so a leaked click never
 * scores even when AEC misses it — and an on-beat clap, which a time window would
 * have wrongly suppressed, still lands. [suppressUntilMs] is ignored in this mode.
 * Used by the Rhythm Game.
 */
class RhythmDetector(private val classifyClaps: Boolean = false) {

    companion object {
        /** Tried in order until one initialises. 44.1 kHz first — universally supported. */
        private val SAMPLE_RATE_CANDIDATES = intArrayOf(44_100, 48_000, 22_050, 16_000)

        /** Samples per AudioRecord.read — ~23 ms at 44.1 kHz. Small = low event latency. */
        private const val READ_CHUNK = 1024

        /** Raw-RMS → 0..1 visualizer scaling. Tuned so claps swing the meter fully. */
        private const val AMPLITUDE_GAIN = 1f / 8000f
    }

    /**
     * Detected onset timestamps, on the [SystemClock.elapsedRealtime] timebase.
     */
    private val _detections = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val detections: SharedFlow<Long> = _detections

    /**
     * Normalised RMS amplitude 0..1, updated on every buffer read. Emitted
     * regardless of suppression or calibration — used only by the visualizer.
     */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile
    private var _capturing = false

    /**
     * Drop onsets before this [SystemClock.elapsedRealtime] ms. Set per-beat by
     * the game to gate the metronome click — the game narrows the window when
     * [echoCancellationActive] is true.
     */
    @Volatile
    var suppressUntilMs: Long = 0L

    /**
     * Debug-only callback fired for onsets that were dropped by [suppressUntilMs].
     * Null in production — set by the caller in debug builds to feed [MicDiagnosticsBuffer].
     * Delete this property and the [debugOnSuppressed]?.invoke() call below to remove.
     */
    @Volatile
    var debugOnSuppressed: ((onsetMs: Long) -> Unit)? = null

    /**
     * Debug-only callback fired for transients the spectral classifier judged to be the
     * metronome click (classifyClaps mode) and therefore dropped. Null in production — set in
     * debug builds to feed [MicDiagnosticsBuffer]. Carries the [ClapDetector.Onset] so the log
     * can show the click-vs-clap band margin that justified the rejection.
     */
    @Volatile
    var debugOnClickRejected: ((onset: ClapDetector.Onset, onsetMs: Long) -> Unit)? = null

    /** Whether hardware echo cancellation is running. When true, suppression is moot. */
    var echoCancellationActive: Boolean = false
        private set

    private var record: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var scope: CoroutineScope? = null

    /** Start microphone listening. Caller must hold RECORD_AUDIO permission. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (_capturing) return

        val opened = openRecord() ?: run {
            // Mic unavailable (busy, unsupported format, permission race). Stay silent
            // and leave capturing=false so callers can surface it — never crash.
            _capturing = false
            return
        }
        val rec = opened.first
        val sampleRate = opened.second
        record = rec

        echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true }
        } else null
        echoCancellationActive = echoCanceler?.enabled == true

        val onsetDetector = if (classifyClaps) null else OnsetDetector(sampleRate)
        val clapDetector = if (classifyClaps) ClapDetector(sampleRate) else null
        rec.startRecording()
        _capturing = true

        val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = captureScope
        captureScope.launch {
            val buf = ShortArray(READ_CHUNK)
            val timestamp = AudioTimestamp()
            val clock = FrameClock(sampleRate)
            var appFrames = 0L   // total frames read — the OnsetDetector's index space

            try {
                while (isActive) {
                    val read = rec.read(buf, 0, READ_CHUNK)
                    if (read <= 0) {
                        if (read < 0) break else continue   // error → stop; 0 → retry
                    }

                    // Visualizer amplitude — raw wideband RMS over this chunk.
                    _amplitude.value = (rawRms(buf, read) * AMPLITUDE_GAIN).coerceIn(0f, 1f)

                    // Re-anchor the frame→time clock against the hardware audio
                    // timestamp, folding in any overrun frame-drops since last buffer.
                    appFrames += read
                    clock.update(rec, timestamp, appFrames)

                    if (clapDetector != null) {
                        // Spectral mode: reject the metronome click by its signature and emit
                        // only claps. No time window, so an on-beat clap still scores.
                        for (onset in clapDetector.process(buf, read)) {
                            val onsetMs = clock.onsetMs(onset.sampleIndex)
                            if (onset.isClap) _detections.tryEmit(onsetMs)
                            else debugOnClickRejected?.invoke(onset, onsetMs)
                        }
                    } else {
                        for (onsetIndex in onsetDetector!!.process(buf, read)) {
                            val onsetMs = clock.onsetMs(onsetIndex)
                            if (onsetMs >= suppressUntilMs) {
                                _detections.tryEmit(onsetMs)
                            } else {
                                debugOnSuppressed?.invoke(onsetMs)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // AudioRecord released mid-read during stop() — expected, end quietly.
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        echoCanceler?.release()
        echoCanceler = null
        echoCancellationActive = false
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped / uninitialised — nothing to do.
        }
        record?.release()
        record = null
        _capturing = false
        _amplitude.value = 0f
    }

    // ── Internals ───────────────────────────────────────────────────────────────

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

    /**
     * Maps an [OnsetDetector] sample index to a timestamp on the
     * [SystemClock.elapsedRealtime] timebase, anchored each buffer to the
     * hardware audio clock.
     *
     * The anchor is [AudioRecord.getTimestamp] on the BOOTTIME timebase
     * (== elapsedRealtime base): it pins a known captured frame to its true
     * capture instant, cancelling device input latency. If the timestamp is
     * unavailable, the chunk's final frame is assumed captured ≈ now.
     *
     * ### Overrun drift
     * [OnsetDetector] indices count only the frames the app actually read. When
     * the capture buffer overruns, the hardware discards frames the app never
     * sees, so that count falls behind the hardware frame position — and a
     * naïve mapping would then skew every later onset by the lost span.
     *
     * [streamDrift] absorbs it: the backlog between the hardware position and
     * the app's read count can exceed its smallest-ever value only by frames an
     * overrun dropped — normal read-cadence wobble stays within
     * [DRIFT_MARGIN_FRAMES]. That surplus is folded into a running, monotone
     * drift (dropped frames are never recovered), realigning onset indices for
     * the rest of the session. The estimate may undershoot a small overrun, but
     * it never false-corrects under normal jitter, so it cannot itself add skew.
     */
    private class FrameClock(private val sampleRate: Int) {

        companion object {
            /**
             * Backlog surplus, over the smallest backlog ever seen, still
             * attributable to ordinary read-cadence wobble rather than dropped
             * frames. Deliberately generous: too small risks a false, permanent
             * drift correction; too large merely leaves a tiny overrun
             * uncorrected. ≈ 93 ms at 44.1 kHz.
             */
            private const val DRIFT_MARGIN_FRAMES = 4096L

            /** Buffers skipped before trusting drift maths, letting the backlog floor settle. */
            private const val WARMUP_BUFFERS = 48
        }

        /** Frames dropped by buffer overrun so far. Monotone — drops never un-happen. */
        private var streamDrift = 0L
        /** Smallest hardware-vs-app backlog observed — the irreducible buffering. */
        private var minBacklog = Long.MAX_VALUE
        private var warmupLeft = WARMUP_BUFFERS

        private var anchorFrame = 0L   // hardware (stream) frame index of the anchor
        private var anchorMs = 0.0     // elapsedRealtime ms at the anchor frame

        /**
         * Re-anchor against the current capture state. Call once per buffer,
         * after the [AudioRecord.read] that brought the app total to [appFramesRead].
         */
        fun update(rec: AudioRecord, ts: AudioTimestamp, appFramesRead: Long) {
            val haveTimestamp = try {
                rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS &&
                    ts.nanoTime > 0L
            } catch (_: Exception) {
                false
            }

            if (haveTimestamp) {
                val backlog = ts.framePosition - appFramesRead
                if (backlog in 0 until minBacklog) minBacklog = backlog
                if (warmupLeft > 0) {
                    warmupLeft--
                } else {
                    // Backlog beyond normal wobble over its floor == dropped frames.
                    val dropped = backlog - minBacklog - DRIFT_MARGIN_FRAMES
                    if (dropped > streamDrift) streamDrift = dropped
                }
                anchorFrame = ts.framePosition
                anchorMs = ts.nanoTime / 1_000_000.0   // BOOTTIME == elapsedRealtime base
            } else {
                // No hardware timestamp: pin the chunk end to ≈ now.
                // streamDrift cancels out of onsetMs() on this path.
                anchorFrame = appFramesRead + streamDrift
                anchorMs = SystemClock.elapsedRealtime().toDouble()
            }
        }

        /** Map an [OnsetDetector] sample index to elapsedRealtime ms. */
        fun onsetMs(appIndex: Long): Long {
            val streamFrame = appIndex + streamDrift
            return (anchorMs + (streamFrame - anchorFrame) * 1000.0 / sampleRate).toLong()
        }
    }

    private fun rawRms(buf: ShortArray, len: Int): Float {
        if (len == 0) return 0f
        var sum = 0.0
        for (i in 0 until len) {
            val s = buf[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / len).toFloat()
    }
}
