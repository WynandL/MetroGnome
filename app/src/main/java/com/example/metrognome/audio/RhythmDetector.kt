package com.example.metrognome.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Listens via microphone and emits detected beat timestamps.
 * Uses amplitude threshold detection to find percussion hits (claps, drums, etc.).
 *
 * Requires android.permission.RECORD_AUDIO at runtime.
 */
class RhythmDetector {

    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    // Minimum ms between two consecutive detections (prevents double-counting a single hit)
    var minIntervalMs: Long = 80L

    private val _detections = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val detections: SharedFlow<Long> = _detections

    private var record: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Start microphone listening. Caller must hold RECORD_AUDIO permission. */
    fun start() {
        if (job?.isActive == true) return
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        record?.startRecording()

        job = scope.launch {
            val buf = ShortArray(bufferSize / 2)
            var noiseFloor = 1000.0         // calibrated ambient RMS
            var calibrationSamples = 0
            var lastDetectMs = 0L

            while (isActive) {
                val read = record?.read(buf, 0, buf.size) ?: break
                if (read <= 0) continue

                val rms = rms(buf, read)

                // Calibrate noise floor during the first ~0.5 s
                if (calibrationSamples < 20) {
                    noiseFloor = (noiseFloor * calibrationSamples + rms) / (calibrationSamples + 1)
                    calibrationSamples++
                    continue
                }

                val threshold = (noiseFloor * 3.5).coerceAtLeast(500.0)
                val nowMs = System.currentTimeMillis()

                if (rms > threshold && nowMs - lastDetectMs > minIntervalMs) {
                    lastDetectMs = nowMs
                    _detections.tryEmit(nowMs)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        record?.stop()
        record?.release()
        record = null
    }

    fun isRunning() = job?.isActive == true

    private fun rms(buf: ShortArray, len: Int): Double {
        if (len == 0) return 0.0
        var sum = 0.0
        for (i in 0 until len) sum += (buf[i].toDouble()).let { it * it }
        return sqrt(sum / len)
    }
}
