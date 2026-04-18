package com.example.metrognome.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * AudioTrack-based metronome engine.
 * Timing is driven by audio hardware frames — sample-accurate, drift-free.
 * Call [start] to begin playback, [stop] to halt.
 */
class MetronomeEngine {

    private val sampleRate = 44100

    // Pre-generated click buffers (populated once at init)
    private val normalClick = generateClick(frequency = 1100.0, durationMs = 38, volume = 0.72f)
    private val accentClick = generateClick(frequency = 1800.0, durationMs = 50, volume = 0.92f)
    private val hihatClick = generateClick(frequency = 9000.0, durationMs = 20, volume = 0.65f)
    private val woodClick = generateClick(frequency = 600.0, durationMs = 45, volume = 0.80f)
    private val hihatAccent = generateClick(frequency = 9000.0, durationMs = 30, volume = 0.90f)
    private val woodAccent = generateClick(frequency = 800.0, durationMs = 55, volume = 0.95f)
    private val deepClick = generateDeepClick(frequency = 350.0, durationMs = 130, volume = 1.0f)
    private val deepAccent = generateDeepClick(frequency = 440.0, durationMs = 150, volume = 1.0f)

    // Mutable settings — read from audio thread, written from main thread (volatile)
    @Volatile
    var bpm: Int = 120
    @Volatile
    var timeSignature: Int = 4
    @Volatile
    var accentBeat: Int = 0   // 0-based beat index; -1 = no accent
    @Volatile
    var soundType: Int = 0      // 0=click, 1=hihat, 2=woodblock, 3=warm
    @Volatile
    var volume: Float = 1.0f
    @Volatile
    var muted: Boolean = false

    var onBeat: ((beat: Int) -> Unit)? = null

    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start() {
        if (job?.isActive == true) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack?.release()
            audioTrack = null
            return
        }
        audioTrack?.play()

        job = scope.launch {
            var beat = 0
            while (isActive) {
                val currentBpm = bpm.coerceIn(20, 300)
                val samplesPerBeat = (sampleRate * 60.0 / currentBpm).toInt()
                val isAccent = accentBeat >= 0 && beat == accentBeat
                val buffer = buildBeatBuffer(samplesPerBeat, isAccent)

                // Notify the UI BEFORE writing audio data.
                //
                // AudioTrack.write() in STREAM mode is blocking — it returns only after
                // the entire beat buffer (click + silence) has been accepted into the
                // hardware ring buffer, which takes ~one full beat of wall-clock time.
                // Calling onBeat() after write() would mean the visual update fires
                // almost a full beat AFTER the click was heard.
                //
                // By notifying first, the UI update is queued at the same instant the
                // audio data is handed to the driver. The hardware buffer latency (~23 ms)
                // and the Compose frame latency (~16 ms) are close enough that audio and
                // visuals land within one frame of each other.
                onBeat?.invoke(beat)
                beat = (beat + 1) % timeSignature

                // Guard against the track being released between job cancellation and
                // the next write() call — AudioTrack.write() throws IllegalStateException
                // when the track is in STATE_UNINITIALIZED (i.e. after release()).
                try {
                    val written = audioTrack?.write(buffer, 0, buffer.size) ?: break
                    if (written < 0) break  // AudioTrack.ERROR_* — exit cleanly
                } catch (e: IllegalStateException) {
                    break  // track was released, exit cleanly
                }
                if (!isActive) break
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun isPlaying() = job?.isActive == true

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Fill buffer: click samples at index 0, silence for the rest */
    private fun buildBeatBuffer(samplesPerBeat: Int, isAccent: Boolean): ShortArray {
        if (muted) return ShortArray(samplesPerBeat)
        val click = when (soundType) {
            1 -> if (isAccent) hihatAccent else hihatClick
            2 -> if (isAccent) woodAccent else woodClick
            3 -> if (isAccent) deepAccent else deepClick
            else -> if (isAccent) accentClick else normalClick
        }
        val buf = ShortArray(samplesPerBeat)
        val len = minOf(click.size, samplesPerBeat)
        val vol = volume.coerceIn(0f, 1f)
        for (i in 0 until len) {
            buf[i] = (click[i] * vol).toInt().toShort()
        }
        return buf
    }

    /**
     * Low-frequency click with simulated room reverb via early reflections.
     * Three delayed copies at decreasing amplitudes give warmth without a
     * dedicated reverb unit — inaudible as distinct echoes at typical BPM.
     */
    private fun generateDeepClick(frequency: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val dry = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = (1.0 - i.toDouble() / numSamples).pow(2.0)
            (envelope * sin(2.0 * PI * frequency * t) * volume).toFloat()
        }
        val r1 = (0.028 * sampleRate).toInt()
        val r2 = (0.052 * sampleRate).toInt()
        val r3 = (0.080 * sampleRate).toInt()
        val wet = FloatArray(numSamples) { i ->
            dry[i] +
            (if (i >= r1) dry[i - r1] * 0.28f else 0f) +
            (if (i >= r2) dry[i - r2] * 0.14f else 0f) +
            (if (i >= r3) dry[i - r3] * 0.06f else 0f)
        }
        val peak = wet.maxOf { abs(it) }
        val scale = if (peak > 0.99f) 0.99f / peak else 1f
        return ShortArray(numSamples) { i ->
            (wet[i] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Generates a short sine-wave click with exponential decay envelope.
     * [frequency] in Hz, [durationMs] in milliseconds, [volume] 0..1.
     */
    private fun generateClick(frequency: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val buf = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = (1.0 - i.toDouble() / numSamples).pow(1.5)
            val sample = envelope * sin(2.0 * PI * frequency * t) * Short.MAX_VALUE * volume
            buf[i] =
                sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buf
    }
}
