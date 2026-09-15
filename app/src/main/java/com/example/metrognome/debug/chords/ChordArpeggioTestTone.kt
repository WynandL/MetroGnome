package com.example.metrognome.debug.chords

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.metrognome.audio.NoteNames
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * DEV ONLY: plays a few chords through the speaker as arpeggios, after a delay long enough
 * to walk from the dev tools to the Chords tab, so the Chord Finder's mic path can be tried
 * without an instrument in the room.
 *
 * Each note is a harmonic-rich tone (fundamental plus four decaying partials, the kind of
 * spectrum the pitch detector locks on to at once and a phone speaker can actually put out), held [NOTE_MS] with a short attack and
 * release and a [NOTE_GAP_MS] silence after it, so the tuner's ambient gate sees a steady
 * note, then silence, then the next. Chords are [CHORD_GAP_MS] apart; each starts below
 * the previous bass, which is the finder's own cue to begin a new set, so the three should
 * appear one after another with no Clear tap.
 *
 * Rendered in one go into a static AudioTrack on a plain thread, so it keeps playing when
 * the Settings screen (and its composition) is left. Not a shipped feature; lives in
 * `debug/` and is reachable only from [com.example.metrognome.debug.settings.DevToolsSection].
 */
object ChordArpeggioTestTone {

    /**
     * What plays, as MIDI notes low to high: C major, G7, E minor, around middle C. Each
     * chord's bass is below the previous one's, so the finder's "a note below the bass
     * starts a new chord" rule splits them with no Clear tap and no gap logic. The first
     * cut sat an octave lower (C3, G2, A2) and came out faint even at full volume: a phone
     * speaker reproduces almost nothing below about 300 Hz, so most of a 98 Hz G2 never
     * left the phone. The finder does not care which octave a chord is in.
     */
    private val CHORDS = listOf(
        listOf(60, 64, 67),        // C4 E4 G4
        listOf(55, 59, 62, 65),    // G3 B3 D4 F4
        listOf(52, 55, 59),        // E3 G3 B3
    )

    const val START_DELAY_MS = 3_000L
    private const val NOTE_MS = 900
    private const val NOTE_GAP_MS = 250
    private const val CHORD_GAP_MS = 1_500
    private const val SAMPLE_RATE = 44_100
    private const val AMPLITUDE = 0.90f

    @Volatile private var playing = false

    /** Human-readable description of the sequence, for the dev button's caption. */
    val description: String
        get() = CHORDS.joinToString(", ") { chord -> chord.joinToString(" ") { NoteNames.labelOf(it) } }

    /**
     * Start after [START_DELAY_MS] unless already playing. Returns false if a run is in
     * progress. [referenceHz] should be the tuner's, so the tones land where the finder
     * expects them.
     */
    fun playAfterDelay(referenceHz: Float = 440f): Boolean {
        if (playing) return false
        playing = true
        thread(name = "ChordArpeggioTestTone", isDaemon = true) {
            try {
                Thread.sleep(START_DELAY_MS)
                val pcm = render(referenceHz)
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
                try {
                    track.write(pcm, 0, pcm.size)
                    track.play()
                    Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 200L)
                } finally {
                    track.stop()
                    track.release()
                }
            } catch (_: Exception) {
                // A dev tool: fail silently rather than crash the app under test.
            } finally {
                playing = false
            }
        }
        return true
    }

    /** The whole sequence as 16-bit mono PCM. */
    private fun render(referenceHz: Float): ShortArray {
        val noteSamples = SAMPLE_RATE * NOTE_MS / 1000
        val gapSamples = SAMPLE_RATE * NOTE_GAP_MS / 1000
        val chordGapSamples = SAMPLE_RATE * CHORD_GAP_MS / 1000
        val total = CHORDS.sumOf { it.size * (noteSamples + gapSamples) } + (CHORDS.size - 1) * chordGapSamples
        val out = ShortArray(total)
        var pos = 0
        val attack = SAMPLE_RATE * 25 / 1000
        val release = SAMPLE_RATE * 80 / 1000

        CHORDS.forEachIndexed { chordIndex, chord ->
            for (midi in chord) {
                val f = NoteNames.frequencyOf(midi, referenceHz).toDouble()
                for (i in 0 until noteSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = min(1.0, min(i / attack.toDouble(), (noteSamples - i) / release.toDouble()))
                    // Bright partials up to the fifth: a phone speaker is far louder above
                    // 1 kHz than at a fundamental, and the pitch detector finds the period
                    // from the partials' spacing just as well as from the fundamental.
                    val v = sin(2 * PI * f * t) +
                        0.60 * sin(2 * PI * 2 * f * t) +
                        0.40 * sin(2 * PI * 3 * f * t) +
                        0.25 * sin(2 * PI * 4 * f * t) +
                        0.15 * sin(2 * PI * 5 * f * t)
                    out[pos + i] = (v / 2.4 * env * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
                }
                pos += noteSamples + gapSamples   // the gap is left at zero
            }
            if (chordIndex < CHORDS.lastIndex) pos += chordGapSamples
        }
        return out
    }
}
