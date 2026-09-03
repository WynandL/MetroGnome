package com.example.metrognome.audio.drone

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a continuous tuning drone through the speaker.
 *
 * Everything musical lives in [DroneRenderer]; this class is the plumbing around it: one
 * AudioTrack in STREAM mode, one coroutine writing blocks into it, and the marshalling that
 * carries the UI's requests onto that thread.
 *
 * **Threading.** [DroneRenderer] is single-threaded by design. Every setter here writes a
 * volatile field (or an [AtomicReference] for the voice, which is a whole object), and the
 * audio loop applies them at block boundaries. So a note change from the UI thread lands at
 * the next block, at most one block late, and no lock is ever taken on the audio path.
 *
 * **Stopping.** [stop] does not tear anything down. It closes the renderer's gate, and the
 * loop keeps writing until the release envelope has finished and only then releases the
 * track, so stopping is a fade rather than a cut. [release] is the hard version, for when
 * the ViewModel is cleared and there is nobody left to hear the fade.
 */
class DroneEngine {

    /**
     * Ask the output device what rate it actually runs at and match it, rather than
     * assuming 44.1 kHz and letting the platform resample. A drone is a long, steady tone,
     * which is the worst case for a resampler and the easiest place to hear one.
     */
    private val sampleRate: Int = resolveSampleRate()

    private val renderer = DroneRenderer(sampleRate)

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Guards [job] and [runId] together.
     *
     * The subtle case this exists for: the loop finishes its release fade and decides to
     * exit at the same moment the user taps play again. Deciding to exit under the same
     * lock that [start] takes, and only while the gate is still shut, means one of the two
     * always wins cleanly. Without it there is a window where the loop is on its way out
     * but its Job still reports active, so [start] would hand the gate to a loop that is
     * about to leave and the drone would go silent with the button showing "playing".
     */
    private val lifecycle = Any()
    private var job: Job? = null

    /** Incremented per loop, so a departing loop can tell whether it is still the current one. */
    private var runId = 0

    private var track: AudioTrack? = null

    // Written from the UI thread, read by the audio loop once per block.
    @Volatile
    private var targetHz: Double = 220.0

    @Volatile
    private var targetVolume: Float = 0.75f

    @Volatile
    private var gateOpen: Boolean = false

    /** Set true by [release] so teardown cuts the buffer instead of playing it out. */
    @Volatile
    private var aborting: Boolean = false

    private val pendingVoice = AtomicReference<VoiceLayout?>(null)

    /** True from the moment [start] is called until the release fade has finished. */
    val isRunning: Boolean get() = synchronized(lifecycle) { job != null }

    /** Set the sounding pitch. Takes effect as a glide while playing, instantly while not. */
    fun setFrequency(hz: Double) {
        targetHz = hz
    }

    /** Set the user level, 0..1. */
    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
    }

    /** Choose the voice. Crossfades into the current one if the drone is already sounding. */
    fun setVoice(timbre: DroneTimbre, blend: DroneBlend) {
        pendingVoice.set(buildVoice(timbre, blend))
    }

    /** Start sounding, or cancel an in-progress fade-out and swell back up. */
    fun start() {
        aborting = false
        gateOpen = true
        synchronized(lifecycle) {
            if (job != null) return    // a loop is live; it picks the open gate up next block
            runId++
            val id = runId
            job = scope.launch { runLoop(id) }
        }
    }

    /** Fade out and then tear down. Safe to call when not playing. */
    fun stop() {
        gateOpen = false
    }

    /** Stop immediately, without the fade. For [androidx.lifecycle.ViewModel.onCleared]. */
    fun release() {
        aborting = true
        gateOpen = false
        synchronized(lifecycle) {
            job?.cancel()
            job = null
        }
    }

    // ── Audio loop ───────────────────────────────────────────────────────────────

    private fun CoroutineScope.runLoop(id: Int) {
        val output = buildTrack() ?: run { retire(id); return }
        track = output

        val left = FloatArray(BLOCK_FRAMES)
        val right = FloatArray(BLOCK_FRAMES)
        val pcm = ShortArray(BLOCK_FRAMES * CHANNELS)

        try {
            output.play()
            while (isActive) {
                pendingVoice.getAndSet(null)?.let { renderer.setVoice(it) }
                renderer.setFrequency(targetHz)
                renderer.setVolume(targetVolume)
                if (gateOpen) renderer.open() else renderer.close()

                renderer.render(left, right, BLOCK_FRAMES)
                interleave(left, right, pcm, BLOCK_FRAMES)

                // STREAM mode: this blocks until the ring buffer accepts the block, which
                // is what paces the loop. No timer is involved and none is wanted; the
                // hardware's own consumption rate is the clock.
                val written = try {
                    output.write(pcm, 0, pcm.size)
                } catch (_: IllegalStateException) {
                    break   // track released underneath us
                }
                if (written < 0) break

                // The gate is shut and the release ramp has run its course: this block was
                // the last of the fade, so it is now safe to leave. Claiming the exit under
                // the lock is what makes a stop/start race resolve one way or the other.
                if (!gateOpen && renderer.silent && claimExit(id)) break
            }
        } finally {
            retire(id)
            teardown(output)
        }
    }

    /** Take ownership of the exit, unless [start] re-opened the gate first. */
    private fun claimExit(id: Int): Boolean = synchronized(lifecycle) {
        if (gateOpen || runId != id) return false
        job = null
        true
    }

    /** Drop this loop's claim on [job], unless a newer loop has already taken it. */
    private fun retire(id: Int) = synchronized(lifecycle) {
        if (runId == id) job = null
    }

    private fun teardown(output: AudioTrack) {
        try {
            if (aborting) {
                // Nobody is listening for a graceful ending; drop whatever is still queued
                // rather than playing out a buffer's worth of stale tone.
                output.pause()
                output.flush()
            } else {
                output.stop()
            }
        } catch (_: IllegalStateException) {
            // Already uninitialised; nothing to wind down.
        }
        output.release()
        if (track === output) track = null
        aborting = false
    }

    private fun buildTrack(): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        val bufferBytes = maxOf(minBuffer, BLOCK_FRAMES * BUFFER_BLOCKS * CHANNELS * BYTES_PER_SAMPLE)

        val output = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: IllegalStateException) {
            return null
        } catch (_: UnsupportedOperationException) {
            return null
        }

        if (output.state != AudioTrack.STATE_INITIALIZED) {
            output.release()
            return null
        }
        return output
    }

    /** Pack the two float channels into one interleaved 16-bit buffer, clamping on the way. */
    private fun interleave(left: FloatArray, right: FloatArray, out: ShortArray, frames: Int) {
        var index = 0
        for (i in 0 until frames) {
            out[index++] = toPcm(left[i])
            out[index++] = toPcm(right[i])
        }
    }

    private fun toPcm(value: Float): Short {
        val scaled = value * Short.MAX_VALUE
        return when {
            scaled >= Short.MAX_VALUE.toFloat() -> Short.MAX_VALUE
            scaled <= Short.MIN_VALUE.toFloat() -> Short.MIN_VALUE
            else -> scaled.toInt().toShort()
        }
    }

    private companion object {
        /**
         * Frames per write. About 12 ms at 44.1 kHz: short enough that a note or level
         * change lands the moment it is asked for, long enough that the per-block work
         * (re-tuning every oscillator) stays a rounding error next to the per-sample work.
         */
        const val BLOCK_FRAMES = 512

        /** Blocks of slack in the ring buffer, so a scheduling hiccup cannot underrun it. */
        const val BUFFER_BLOCKS = 8

        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2

        fun resolveSampleRate(): Int {
            val native = try {
                AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            } catch (_: Exception) {
                0
            }
            return if (native in 8_000..192_000) native else 44_100
        }
    }
}
