package com.example.metrognome.audio.selftest

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The emission side of the acoustic loopback.
 *
 * Builds a single static PCM buffer containing the test stimuli at *known frame
 * offsets*, plays it once, and - crucially - anchors those frames to the
 * [SystemClock.elapsedRealtime] timebase using [AudioTrack.getTimestamp]. That
 * hardware anchor reports the true DAC presentation instant of a known frame, so
 * we learn exactly *when each stimulus left the speaker* rather than when we
 * asked for it. This is the same trick [com.example.metrognome.audio.rhythm.RhythmDetector]
 * uses on the capture side; pairing them lets the round-trip be measured
 * hardware-to-hardware, with the software write/callback jitter removed from
 * both ends.
 *
 * Two stimuli are synthesised:
 *  - **Classic click** - a faithful copy of the metronome's default click
 *    (1100 Hz sine, 38 ms, ^1.5 decay). Mic-accuracy mode locks the click to
 *    this sound precisely so the self-test validates the real production signal.
 *  - **Clap** - a short broadband transient standing in for the player's input:
 *    a sharp-attack noise burst tilted toward the 2-8 kHz band where real claps
 *    live, so the click-vs-clap discriminator is exercised exactly as it will be
 *    in the field.
 */
class LoopbackStimulus(private val sampleRate: Int) {

    /** A stimulus placed at an absolute frame offset within the static buffer. */
    enum class Kind { CLICK, CLAP }
    data class Event(val frame: Int, val kind: Kind)

    /** Frame→time anchor from the output hardware clock, on the BOOTTIME base. */
    data class Anchor(val frame: Long, val bootNanos: Long)

    private val click = generateClassicClick()
    private val clap = generateClap()

    private var track: AudioTrack? = null
    private var writer: Thread? = null
    @Volatile private var playing = false
    private var totalFrames = 0

    /** Length (frames) of the longest stimulus - callers leave this much tail room. */
    val maxStimulusFrames: Int get() = maxOf(click.size, clap.size)

    fun framesForMs(ms: Float): Int = (ms * sampleRate / 1000f).toInt()

    /**
     * Render [events] into one buffer of [totalFrames] frames and start playback.
     * Overlapping stimuli are summed (with clipping guard) - this is intentional:
     * the scoring sweep deliberately places a clap close to a click.
     *
     * @return true if playback started.
     */
    fun playPlan(events: List<Event>, totalFrames: Int): Boolean {
        release()
        this.totalFrames = totalFrames
        val buf = ShortArray(totalFrames)

        for (ev in events) {
            val src = if (ev.kind == Kind.CLICK) click else clap
            for (i in src.indices) {
                val idx = ev.frame + i
                if (idx in buf.indices) {
                    val sum = buf[idx].toInt() + src[i].toInt()
                    buf[idx] = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }

        // MODE_STREAM, not MODE_STATIC: getTimestamp() - the hardware anchor this whole
        // measurement relies on - is widely unsupported for static-buffer tracks, so a real
        // device returns "no output timestamp". A streamed track reports timestamps
        // reliably; a background thread feeds the buffer with blocking writes.
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        val t = AudioTrack.Builder()
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

        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return false
        }
        track = t
        playing = true
        t.play()
        writer = Thread {
            var off = 0
            try {
                while (off < buf.size && playing) {
                    val n = minOf(WRITE_CHUNK, buf.size - off)
                    val written = t.write(buf, off, n)   // blocking — paces playback
                    if (written <= 0) break
                    off += written
                }
            } catch (_: Exception) { /* track released mid-write */ }
        }.apply { isDaemon = true; start() }
        return true
    }

    /**
     * Try to read a fresh output anchor. Returns null until the pipeline has
     * primed enough for [AudioTrack.getTimestamp] to report a real frame.
     *
     * The hardware timestamp's nanoTime is on CLOCK_MONOTONIC; we fold in the
     * constant MONOTONIC→BOOTTIME offset (stable across a multi-second test with
     * no deep sleep) so emission and capture share one timebase.
     */
    fun tryAnchor(): Anchor? {
        val t = track ?: return null
        val ts = AudioTimestamp()
        if (!t.getTimestamp(ts) || ts.framePosition <= 0L) return null
        val bootNanos = ts.nanoTime + monotonicToBootOffsetNanos()
        return Anchor(ts.framePosition, bootNanos)
    }

    /** Emission instant (BOOTTIME ms, fractional) of [frame], given an [anchor]. */
    fun emissionBootMs(frame: Int, anchor: Anchor): Double =
        (anchor.bootNanos + (frame - anchor.frame) * 1_000_000_000.0 / sampleRate) / 1_000_000.0

    /** Total playback duration in ms, for the orchestrator to wait out. */
    fun durationMs(): Long = (totalFrames.toLong() * 1000L / sampleRate)

    fun release() {
        playing = false
        writer?.let { runCatching { it.join(200) } }   // let the feeder exit before we release
        writer = null
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    // ── Stimulus synthesis ──────────────────────────────────────────────────────

    /**
     * Classic click - mirrors `MetronomeEngine.generateClick(1100, 38, 0.72)`.
     * Kept as a local copy (rather than coupling the self-test to the metronome's
     * private synthesis) but deliberately identical so we validate the real sound.
     */
    private fun generateClassicClick(): ShortArray {
        val n = sampleRate * CLICK_MS / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = (1.0 - i.toDouble() / n).pow(1.5)
            (env * sin(2.0 * PI * CLICK_HZ * t) * Short.MAX_VALUE * CLICK_VOL)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /**
     * Broadband clap surrogate: a sharp-attack noise burst with a fast
     * exponential decay, pre-emphasised to tilt energy upward into the 2-8 kHz
     * band a real clap occupies. Deterministic (fixed seed) so every run emits an
     * identical stimulus and residuals are reproducible.
     */
    private fun generateClap(): ShortArray {
        val n = sampleRate * CLAP_MS / 1000
        val rng = Random(CLAP_SEED)
        val out = FloatArray(n)
        var prev = 0f
        for (i in 0 until n) {
            val white = rng.nextFloat() * 2f - 1f
            // First-difference pre-emphasis (y = x - k·x[-1]) lifts the high band.
            val emph = white - CLAP_PREEMPHASIS * prev
            prev = white
            val env = exp(-i.toDouble() / (CLAP_DECAY_MS * sampleRate / 1000.0)).toFloat()
            out[i] = emph * env
        }
        val peak = out.maxOf { kotlin.math.abs(it) }.takeIf { it > 0f } ?: 1f
        val scale = CLAP_VOL / peak
        return ShortArray(n) { i ->
            (out[i] * scale * Short.MAX_VALUE)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun monotonicToBootOffsetNanos(): Long =
        SystemClock.elapsedRealtimeNanos() - System.nanoTime()

    companion object {
        // Classic click - identical to the metronome default.
        private const val CLICK_HZ = 1100.0
        private const val CLICK_MS = 38
        private const val CLICK_VOL = 0.72

        // Clap surrogate.
        private const val CLAP_MS = 12
        private const val CLAP_DECAY_MS = 4.0
        private const val CLAP_PREEMPHASIS = 0.90f
        private const val CLAP_VOL = 0.85f
        private const val CLAP_SEED = 0x5EEDL

        /** Chunk size for streaming the stimulus buffer to the track. */
        private const val WRITE_CHUNK = 1024
    }
}
