package com.example.metrognome.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Listens via microphone and emits detected beat timestamps.
 *
 * Audio source: MIC (raw, no automatic voice processing).
 * NoiseSuppressor is intentionally NOT applied — it aggressively filters
 * short transients like claps, which is exactly what we need to detect.
 *
 * AcousticEchoCanceler (AEC) IS applied when available. AEC knows what
 * the speaker is playing (the metronome click) and subtracts it from the
 * microphone signal, so the click doesn't auto-score as a hit.
 *
 * [suppressUntilMs] is a short software safety net for devices where AEC
 * is not available or is weak. Set it to now + 60 on each beat to cover
 * the ~46 ms window in which the click propagates through air to the mic.
 * 60 ms is well below the minimum human reaction time (~150 ms).
 */
class RhythmDetector {

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(2048)   // smaller buffer = lower latency detection

    /** Minimum ms between two consecutive detections (prevents double-counting one hit). */
    var minIntervalMs: Long = 100L

    /** Drop detections until this wall-clock ms. Set per-beat to block click pickup. */
    @Volatile
    var suppressUntilMs: Long = 0L

    private val _detections = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val detections: SharedFlow<Long> = _detections

    /**
     * Normalised RMS amplitude 0..1, updated on every audio buffer read.
     * Emitted regardless of suppression or calibration — used by the visualizer.
     */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private var record: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Start microphone listening. Caller must hold RECORD_AUDIO permission. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (job?.isActive == true) return

        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,   // raw audio — no automatic voice processing
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // AEC: cancel the metronome speaker output from the mic input.
        // NoiseSuppressor is intentionally skipped — it kills clap transients.
        val sessionId = record!!.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        }

        record?.startRecording()

        job = scope.launch {
            val buf = ShortArray(bufferSize / 2)
            var noiseFloor = 300.0
            var calibFrames = 0
            val calibTarget = 20         // ~0.5 s of ambient calibration
            var lastDetectMs = 0L
            var prevRms = 0.0        // previous frame RMS — used for onset detection

            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read <= 0) continue

                val rms = rms(buf, read)
                val nowMs = System.currentTimeMillis()

                // Always emit amplitude for the visualizer
                _amplitude.value = (rms / 32768.0).toFloat().coerceIn(0f, 1f)

                // Calibrate noise floor from the first ~0.5 s
                if (calibFrames < calibTarget) {
                    noiseFloor = (noiseFloor * calibFrames + rms) / (calibFrames + 1)
                    prevRms = rms
                    calibFrames++
                    continue
                }

                // Suppression window (metronome click guard)
                if (nowMs < suppressUntilMs) {
                    prevRms = rms; continue
                }

                val threshold = (noiseFloor * 3.5).coerceAtLeast(500.0)

                // ONSET detection: require both an absolute level AND a sharp upward
                // spike relative to the previous frame.
                // A clap: prevRms ~200, rms ~8000 → ratio ~40× — easily passes.
                // Sustained talking: frames stay similar in level (ratio ≈ 1–2×) → rejected.
                // Minimum ratio of 2.5× means the current frame must be 2.5× louder
                // than the previous one; tune up if speech still bleeds through.
                val isOnset = rms > threshold && rms > prevRms * 2.5

                if (isOnset && nowMs - lastDetectMs > minIntervalMs) {
                    lastDetectMs = nowMs
                    _detections.tryEmit(nowMs)
                }

                prevRms = rms
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        echoCanceler?.release()
        echoCanceler = null
        record?.stop()
        record?.release()
        record = null
        _amplitude.value = 0f
    }

    fun isRunning() = job?.isActive == true

    private fun rms(buf: ShortArray, len: Int): Double {
        if (len == 0) return 0.0
        var sum = 0.0
        for (i in 0 until len) {
            val s = buf[i].toDouble(); sum += s * s
        }
        return sqrt(sum / len)
    }
}
