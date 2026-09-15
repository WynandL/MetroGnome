package com.example.metrognome.debug.chords

import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.chords.ChordPlayer
import com.example.metrognome.audio.chords.ChordTimbre
import com.example.metrognome.audio.chords.ChordVoice

/**
 * DEV ONLY: renders and plays chords through the speaker as arpeggios, so the Chord
 * Finder's mic path can be tried without an instrument in the room.
 *
 * Its one user is [ChordLoopDiagnostic], which plays one chord at a time through
 * [playChord] and watches what the finder makes of it. A plain "play the whole sequence"
 * button existed once and was removed: it played but recorded nothing, so the loop, which
 * does both, is the only entry point. Playback can be stopped mid-note through [stop].
 *
 * The sound is [ChordVoice] with [ChordTimbre.TEST_TONE] (partials bright up to the fifth,
 * a spectrum the pitch detector locks on to at once and a phone speaker can actually put
 * out), each note held for the timings' note length with silence after it, played through
 * this object's own [ChordPlayer]. The sequence sits around middle C: a phone speaker
 * reproduces almost nothing below about 300 Hz, and the first cut an octave lower was
 * faint at full volume. Each chord's bass is below the previous one's, the finder's own
 * cue to begin a new set, so the same sequence could be played straight through without
 * clearing the finder between chords.
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

    private val player = ChordPlayer()

    /** The sequence as note names, for the diagnostic overlay's "Sequence:" line. */
    val description: String
        get() = CHORDS.joinToString(", ") { chord -> chord.joinToString(" ") { NoteNames.labelOf(it) } }

    /** Cut a sounding chord within 50 ms; [ChordLoopDiagnostic.cancel] lands here. */
    fun stop() = player.stop()

    /**
     * Render and play one chord, blocking until it has finished sounding or [stop] is
     * called. Returns the `elapsedRealtime` at which playback started, from which note i
     * starts at `i * (noteMs + gapMs)` plus the device's output latency (a few hundred ms
     * at most).
     */
    fun playChord(chord: List<Int>, timings: ChordTestTimings, referenceHz: Float): Long {
        val events = chord.mapIndexed { i, midi ->
            ChordVoice.ToneEvent(
                midi = midi + timings.octaveShift,
                startMs = i * (timings.noteMs + timings.gapMs),
                holdMs = timings.noteMs,
                gain = timings.amplitude,
            )
        }
        // The tail is the gap after the last note, so the buffer is exactly one chord's slot.
        return player.play(ChordVoice.render(events, referenceHz, ChordTimbre.TEST_TONE, tailMs = timings.gapMs))
    }
}
