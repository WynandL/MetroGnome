package com.example.metrognome.debug.chords

import android.content.Context
import androidx.core.content.edit

/**
 * DEV ONLY: how the test chords are played through the speaker. Tuned per device by
 * [ChordLoopDiagnostic] and persisted, so once a phone has found timings its mic path
 * captures reliably, every later run uses them.
 *
 * The defaults are chosen against the tuner's own ambient gate, not by taste: a lock rides
 * out up to 800 ms of "disturbance" (a new note is one) and 360 ms of silence, both up to
 * doubled by the ambient-suppression scale, then needs 320 ms to acquire and the finder
 * needs three more readings (~270 ms). So a note must sound well over a second and the
 * silence between notes must outlast the hold, or the next note is heard as an interferer
 * over the last one and never captured. The first cut (900 ms notes, 250 ms gaps) failed
 * for exactly that reason.
 */
data class ChordTestTimings(
    /** How long each note sounds. */
    val noteMs: Int = 1_500,
    /** Silence after each note, long enough for the tuner to drop its lock. */
    val gapMs: Int = 900,
    /** Silence between chords (the finder is cleared during it). */
    val chordGapMs: Int = 1_500,
    /** Peak level, 0..1 of full scale. */
    val amplitude: Float = 0.9f,
    /** Semitones the whole sequence is shifted up, for a speaker that cannot voice the low notes. */
    val octaveShift: Int = 0,
) {
    fun clamped() = copy(
        noteMs = noteMs.coerceIn(600, 4_000),
        gapMs = gapMs.coerceIn(300, 2_500),
        chordGapMs = chordGapMs.coerceIn(800, 4_000),
        amplitude = amplitude.coerceIn(0.4f, 1.0f),
        octaveShift = octaveShift.coerceIn(0, 12),
    )

    override fun toString() =
        "note ${noteMs}ms, gap ${gapMs}ms, chord gap ${chordGapMs}ms, level ${(amplitude * 100).toInt()}%" +
            if (octaveShift > 0) ", +$octaveShift st" else ""

    companion object {
        val DEFAULT = ChordTestTimings()
    }
}

/** Where the tuned timings live. Cleared by the diagnostic's "Reset timings". */
class ChordTestTimingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("chord_test_prefs", Context.MODE_PRIVATE)

    val isTuned: Boolean get() = prefs.contains(KEY_NOTE)

    fun load(): ChordTestTimings = if (!isTuned) ChordTestTimings.DEFAULT else ChordTestTimings(
        noteMs = prefs.getInt(KEY_NOTE, ChordTestTimings.DEFAULT.noteMs),
        gapMs = prefs.getInt(KEY_GAP, ChordTestTimings.DEFAULT.gapMs),
        chordGapMs = prefs.getInt(KEY_CHORD_GAP, ChordTestTimings.DEFAULT.chordGapMs),
        amplitude = prefs.getFloat(KEY_AMPLITUDE, ChordTestTimings.DEFAULT.amplitude),
        octaveShift = prefs.getInt(KEY_OCTAVE, ChordTestTimings.DEFAULT.octaveShift),
    ).clamped()

    fun save(t: ChordTestTimings) = prefs.edit {
        putInt(KEY_NOTE, t.noteMs)
        putInt(KEY_GAP, t.gapMs)
        putInt(KEY_CHORD_GAP, t.chordGapMs)
        putFloat(KEY_AMPLITUDE, t.amplitude)
        putInt(KEY_OCTAVE, t.octaveShift)
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_NOTE = "note_ms"
        const val KEY_GAP = "gap_ms"
        const val KEY_CHORD_GAP = "chord_gap_ms"
        const val KEY_AMPLITUDE = "amplitude"
        const val KEY_OCTAVE = "octave_shift"
    }
}
