package com.example.metrognome.audio.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    private val deepClick = generateDeepClick(frequency = 350.0, durationMs = 130)
    private val deepAccent = generateDeepClick(frequency = 440.0, durationMs = 150)
    // Premium (index 4)
    private val bellClick  = generateBellClick(frequency = 880.0,  durationMs = 200, volume = 0.80f)
    private val bellAccent = generateBellClick(frequency = 1109.0, durationMs = 240, volume = 0.95f)
    // Premium (index 5)
    private val bowlClick  = generateBowlClick(frequency = 261.0,  durationMs = 220, volume = 0.80f)
    private val bowlAccent = generateBowlClick(frequency = 330.0,  durationMs = 260, volume = 0.94f)
    // Premium (index 6): kalimba, normal C5 / accent G5 (a calm perfect fifth above)
    private val kalimbaClick  = generateKalimbaClick(frequency = 523.25, durationMs = 220, volume = 0.80f)
    private val kalimbaAccent = generateKalimbaClick(frequency = 783.99, durationMs = 250, volume = 0.94f)
    // Premium (index 7): cowbell, voiced to cut through a loud kit (built for drummers)
    private val cowbellClick  = generateCowbellClick(baseFrequency = 540.0, durationMs = 150, volume = 0.82f)
    private val cowbellAccent = generateCowbellClick(baseFrequency = 660.0, durationMs = 170, volume = 0.96f)

    // Mutable settings — read from audio thread, written from main thread (volatile)
    @Volatile
    var bpm: Int = 120
    @Volatile
    var timeSignature: Int = 4
    // 0-based pulse indices that should be accented. Always reassigned (never mutated in
    // place) so the audio thread reads a consistent snapshot via the volatile reference.
    // Empty = no accents. Derived from the meter's beat grouping (see MeterTheory).
    @Volatile
    var accentBeats: Set<Int> = setOf(0)
    @Volatile
    var soundType: Int = 0      // 0=click, 1=hihat, 2=woodblock, 3=warm, 4=bell, 5=bowl, 6=kalimba, 7=cowbell (4-7 premium)
    @Volatile
    var volume: Float = 1.0f
    @Volatile
    var muted: Boolean = false

    var onBeat: ((beat: Int) -> Unit)? = null

    private var audioTrack: AudioTrack? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var previewTrack: AudioTrack? = null

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
                val isAccent = beat in accentBeats
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
                } catch (_: IllegalStateException) {
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
        previewTrack?.stop()
        previewTrack?.release()
        previewTrack = null
    }

    /**
     * Plays a short preview of [soundTypeIndex] (4 beats at 100 BPM) on a separate
     * one-shot AudioTrack. Safe to call while the metronome is running.
     */
    fun playPreview(soundTypeIndex: Int) {
        scope.launch {
            previewTrack?.stop()
            previewTrack?.release()
            previewTrack = null

            val buffer = buildPreviewBuffer(soundTypeIndex)
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
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

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return@launch
            }
            previewTrack = track
            track.play()
            // STREAM mode: write blocks until the hardware ring buffer accepts data,
            // so loop until all samples are submitted, then wait for playback to drain.
            var offset = 0
            while (isActive && offset < buffer.size) {
                try {
                    val written = track.write(buffer, offset, buffer.size - offset)
                    if (written < 0) break
                    offset += written
                } catch (_: IllegalStateException) {
                    break
                }
            }
            val remainingMs = (buffer.size - offset).toLong() * 1000L / sampleRate
            delay((remainingMs + 300L).milliseconds)
            previewTrack?.stop()
            previewTrack?.release()
            previewTrack = null
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Fill buffer: click samples at index 0, silence for the rest */
    private fun buildBeatBuffer(samplesPerBeat: Int, isAccent: Boolean): ShortArray {
        if (muted) return ShortArray(samplesPerBeat)
        val click = when (soundType) {
            1 -> if (isAccent) hihatAccent else hihatClick
            2 -> if (isAccent) woodAccent else woodClick
            3 -> if (isAccent) deepAccent else deepClick
            4 -> if (isAccent) bellAccent else bellClick
            5 -> if (isAccent) bowlAccent else bowlClick
            6 -> if (isAccent) kalimbaAccent else kalimbaClick
            7 -> if (isAccent) cowbellAccent else cowbellClick
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

    private fun buildPreviewBuffer(soundTypeIndex: Int): ShortArray {
        val numBeats = 4
        val bpm = 100
        val samplesPerBeat = (sampleRate * 60.0 / bpm).toInt()
        val result = ShortArray(samplesPerBeat * numBeats)
        val click = when (soundTypeIndex) {
            1 -> hihatClick
            2 -> woodClick
            3 -> deepClick
            4 -> bellClick
            5 -> bowlClick
            6 -> kalimbaClick
            7 -> cowbellClick
            else -> normalClick
        }
        val vol = volume.coerceIn(0f, 1f)
        for (beat in 0 until numBeats) {
            val offset = beat * samplesPerBeat
            val len = minOf(click.size, samplesPerBeat)
            for (i in 0 until len) {
                result[offset + i] = (click[i] * vol).toInt().toShort()
            }
        }
        return result
    }

    /**
     * Low-frequency click with simulated room reverb via early reflections.
     * Three delayed copies at decreasing amplitudes give warmth without a
     * dedicated reverb unit — inaudible as distinct echoes at typical BPM.
     */
    private fun generateDeepClick(frequency: Double, durationMs: Int): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val dry = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = (1.0 - i.toDouble() / numSamples).pow(2.0)
            (envelope * sin(2.0 * PI * frequency * t)).toFloat()
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

    /**
     * Bell-like click using inharmonic additive synthesis.
     * Four partials at Chladni ratios (1x, 2.756x, 5.404x, 8x) each with
     * progressively faster decay — gives the characteristic bell ring without
     * sustained sustain that would blur fast tempos.
     */
    private fun generateBellClick(frequency: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val wet = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val p = i.toDouble() / numSamples
            val f1 = sin(2.0 * PI * frequency         * t) * (1.0 - p).pow(2.0) * 0.70
            val f2 = sin(2.0 * PI * frequency * 2.756 * t) * (1.0 - p).pow(3.5) * 0.45
            val f3 = sin(2.0 * PI * frequency * 5.404 * t) * (1.0 - p).pow(5.5) * 0.25
            val f4 = sin(2.0 * PI * frequency * 8.0   * t) * (1.0 - p).pow(8.0) * 0.10
            ((f1 + f2 + f3 + f4) * volume).toFloat()
        }
        val peak = wet.maxOf { abs(it) }
        val scale = if (peak > 0.99f) 0.99f / peak else 1f
        return ShortArray(numSamples) { i ->
            (wet[i] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Kalimba (thumb-piano) synthesis: a warm plucked tine.
     * The first three partials are near-harmonic (with a touch of stretch on the upper two
     * for tine character) and decay progressively faster, giving the round, woody body.
     * A faint high inharmonic partial (6.4x) with a very fast decay supplies the bright
     * "pluck" shimmer at the attack without ringing long enough to blur fast tempos.
     * A 4 ms raised-cosine fade-in softens the onset so it reads as a pluck, not a click.
     */
    private fun generateKalimbaClick(frequency: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val attack = (0.004 * sampleRate).toInt().coerceAtLeast(1)
        val wet = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val p = i.toDouble() / numSamples
            val f1   = sin(2.0 * PI * frequency         * t) * (1.0 - p).pow(1.6)  * 0.62
            val f2   = sin(2.0 * PI * frequency * 2.01  * t) * (1.0 - p).pow(2.8)  * 0.30
            val f3   = sin(2.0 * PI * frequency * 3.04  * t) * (1.0 - p).pow(4.0)  * 0.14
            val ping = sin(2.0 * PI * frequency * 6.4   * t) * (1.0 - p).pow(12.0) * 0.10
            val onset = if (i < attack) 0.5 * (1.0 - cos(PI * i / attack)) else 1.0
            ((f1 + f2 + f3 + ping) * onset * volume).toFloat()
        }
        val peak = wet.maxOf { abs(it) }
        val scale = if (peak > 0.99f) 0.99f / peak else 1f
        return ShortArray(numSamples) { i ->
            (wet[i] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Cowbell synthesis, voiced to cut through a loud acoustic kit (built for drummers).
     * Two inharmonic metal tones at the classic 808 ratio (base and 1.48x base), each
     * carrying odd harmonics (3x, 5x) that mimic the bright square-wave spectrum of struck
     * metal, so the click reads clearly over cymbals and snare. A 1 ms attack and a fast
     * decay keep the transient sharp and stop it ringing into the next beat.
     */
    private fun generateCowbellClick(baseFrequency: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val attack = (0.001 * sampleRate).toInt().coerceAtLeast(1)
        val fB = baseFrequency * 1.48
        val wet = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val p = i.toDouble() / numSamples
            val voiceA = (sin(2.0 * PI * baseFrequency * t)
                    + sin(2.0 * PI * baseFrequency * 3.0 * t) * 0.32
                    + sin(2.0 * PI * baseFrequency * 5.0 * t) * 0.14) * (1.0 - p).pow(3.0) * 0.55
            val voiceB = (sin(2.0 * PI * fB * t)
                    + sin(2.0 * PI * fB * 3.0 * t) * 0.32
                    + sin(2.0 * PI * fB * 5.0 * t) * 0.14) * (1.0 - p).pow(3.4) * 0.45
            val onset = if (i < attack) i.toDouble() / attack else 1.0
            ((voiceA + voiceB) * onset * volume).toFloat()
        }
        val peak = wet.maxOf { abs(it) }
        val scale = if (peak > 0.99f) 0.99f / peak else 1f
        return ShortArray(numSamples) { i ->
            (wet[i] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Crystal singing-bowl synthesis.
     * Three near-harmonic partials: the 2nd partial is detuned ~2.2% above the octave,
     * producing a ~5 Hz shimmer beat between partials — the defining quality of a real bowl ring.
     * Normal click pitched to middle C (261 Hz); accent a major third higher (330 Hz).
     */
    private fun generateBowlClick(frequency: Double, durationMs: Int, volume: Float): ShortArray {
        val numSamples = sampleRate * durationMs / 1000
        val wet = FloatArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val p = i.toDouble() / numSamples
            val f1 = sin(2.0 * PI * frequency         * t) * (1.0 - p).pow(1.8) * 0.65
            val f2 = sin(2.0 * PI * frequency * 2.022 * t) * (1.0 - p).pow(2.8) * 0.28
            val f3 = sin(2.0 * PI * frequency * 3.0   * t) * (1.0 - p).pow(4.5) * 0.09
            ((f1 + f2 + f3) * volume).toFloat()
        }
        val peak = wet.maxOf { abs(it) }
        val scale = if (peak > 0.99f) 0.99f / peak else 1f
        return ShortArray(numSamples) { i ->
            (wet[i] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
