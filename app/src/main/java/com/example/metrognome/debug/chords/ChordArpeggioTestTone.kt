package com.example.metrognome.debug.chords

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.example.metrognome.audio.NoteNames
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * DEV ONLY: renders and plays chords through the speaker as arpeggios, so the Chord
 * Finder's mic path can be tried without an instrument in the room.
 *
 * Its one user is [ChordLoopDiagnostic], which plays one chord at a time through
 * [playChord] and watches what the finder makes of it. A plain "play the whole sequence"
 * button existed once and was removed: it played but recorded nothing, so the loop, which
 * does both, is the only entry point. Playback can be stopped mid-note through [stop].
 *
 * Each note is a harmonic-rich tone (fundamental plus four decaying partials, a spectrum the
 * pitch detector locks on to at once and a phone speaker can actually put out), held for
 * the timings' note length with a short attack and release and silence after it. The
 * sequence sits around middle C: a phone speaker reproduces almost nothing below about
 * 300 Hz, and the first cut an octave lower was faint at full volume. Each chord's bass is
 * below the previous one's, the finder's own cue to begin a new set, so the same sequence
 * could be played straight through without clearing the finder between chords.
 *
 * Not a shipped feature; lives in `debug/`.
 */
object ChordArpeggioTestTone {

    /** What plays, as MIDI notes low to high: C major, G7, E minor. */
    val CHORDS: List<List<Int>> = listOf(
        listOf(60, 64, 67),        // C4 E4 G4
        listOf(55, 59, 62, 65),    // G3 B3 D4 F4
        listOf(52, 55, 59),        // E3 G3 B3
    )

    /** What each chord should be named, for the closed loop to check against. */
    val EXPECTED_SYMBOLS = listOf("C", "G7", "Em")

    private const val SAMPLE_RATE = 44_100

    @Volatile private var stopRequested = false

    /** The sequence as note names, for the diagnostic overlay's "Sequence:" line. */
    val description: String
        get() = CHORDS.joinToString(", ") { chord -> chord.joinToString(" ") { NoteNames.labelOf(it) } }

    /** Cut a sounding chord within 50 ms; [ChordLoopDiagnostic.cancel] lands here. */
    fun stop() {
        stopRequested = true
    }

    /**
     * Render and play one chord, blocking until it has finished sounding or [stop] is
     * called. Returns the `elapsedRealtime` at which playback started, from which note i
     * starts at `i * (noteMs + gapMs)` plus the device's output latency (a few hundred ms
     * at most).
     */
    fun playChord(chord: List<Int>, timings: ChordTestTimings, referenceHz: Float): Long {
        stopRequested = false
        val pcm = render(chord, timings, referenceHz)
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
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        return try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val started = SystemClock.elapsedRealtime()
            val until = started + pcm.size * 1000L / SAMPLE_RATE + 100L
            // Sleep in slices so a stop request cuts the sound within 50 ms.
            while (SystemClock.elapsedRealtime() < until && !stopRequested && !Thread.currentThread().isInterrupted) {
                Thread.sleep(50)
            }
            started
        } finally {
            track.stop()
            track.release()
        }
    }

    /** One chord as 16-bit mono PCM: each note held, then silence for the gap. */
    private fun render(chord: List<Int>, timings: ChordTestTimings, referenceHz: Float): ShortArray {
        val noteSamples = SAMPLE_RATE * timings.noteMs / 1000
        val gapSamples = SAMPLE_RATE * timings.gapMs / 1000
        val out = ShortArray(chord.size * (noteSamples + gapSamples))
        val attack = SAMPLE_RATE * 25 / 1000
        val release = SAMPLE_RATE * 80 / 1000
        var pos = 0
        for (midi in chord) {
            val f = NoteNames.frequencyOf(midi + timings.octaveShift, referenceHz).toDouble()
            for (i in 0 until noteSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = min(1.0, min(i / attack.toDouble(), (noteSamples - i) / release.toDouble()))
                // Bright partials up to the fifth: a phone speaker is far louder above 1 kHz
                // than at a fundamental, and the pitch detector finds the period from the
                // partials' spacing just as well as from the fundamental.
                val v = sin(2 * PI * f * t) +
                    0.60 * sin(2 * PI * 2 * f * t) +
                    0.40 * sin(2 * PI * 3 * f * t) +
                    0.25 * sin(2 * PI * 4 * f * t) +
                    0.15 * sin(2 * PI * 5 * f * t)
                out[pos + i] = (v / 2.4 * env * timings.amplitude * Short.MAX_VALUE).toInt().toShort()
            }
            pos += noteSamples + gapSamples   // the gap is left at zero
        }
        return out
    }
}
