package com.example.metrognome.audio.chords

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plays one rendered buffer from [ChordVoice] through the speaker and can be cut short.
 *
 * The Android half of chord playback, the way [com.example.metrognome.audio.drone.DroneEngine]
 * is the Android half of the drone: a static [AudioTrack] per play, a blocking wait that
 * checks for [stop] every 50 ms, and a [playing] flow for the UI. The Chord Finder's
 * ViewModel owns one for "hear it" and the dev tools' test tone owns another; each is a
 * few fields, and the two never sound at once in practice.
 *
 * [play] blocks, so callers run it off the main thread (the ViewModel on `Dispatchers.IO`).
 */
class ChordPlayer {

    private val _playing = MutableStateFlow(false)
    /** True from the first sample to the last, or to [stop]. */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    @Volatile private var stopRequested = false

    /** Cut whatever is sounding within 50 ms. Safe when nothing is. */
    fun stop() {
        stopRequested = true
    }

    /**
     * Play [pcm] (16-bit mono at [ChordVoice.SAMPLE_RATE]) to the end or until [stop].
     * Returns the `elapsedRealtime` at which the track started, which the dev diagnostic
     * uses to place each note in time.
     */
    fun play(pcm: ShortArray): Long {
        if (pcm.isEmpty()) return SystemClock.elapsedRealtime()
        stopRequested = false
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ChordVoice.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        _playing.value = true
        return try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val started = SystemClock.elapsedRealtime()
            val until = started + pcm.size * 1000L / ChordVoice.SAMPLE_RATE + 100L
            // Sleep in slices so a stop request cuts the sound within 50 ms.
            while (SystemClock.elapsedRealtime() < until && !stopRequested && !Thread.currentThread().isInterrupted) {
                Thread.sleep(50)
            }
            started
        } finally {
            track.stop()
            track.release()
            _playing.value = false
        }
    }
}
