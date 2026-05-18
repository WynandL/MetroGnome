package com.example.metrognome.audio

import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Frequency ↔ note-name conversion for the equal-tempered 12-tone scale.
 *
 * A note name is just a human label for a frequency. Every note's frequency is
 *
 *     f = referenceHz × 2^(n / 12)
 *
 * where `n` is the number of semitones away from A4. An octave is an exact
 * doubling of frequency; each octave is split into 12 equal semitone *ratios*
 * of 2^(1/12) ≈ 1.0595 — so pitch is geometric in frequency, the way decibels
 * are for amplitude.
 *
 * MIDI numbers are used as the integer index: MIDI 69 = A4, MIDI 60 = C4.
 */
object NoteNames {

    private val NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
    )

    /** Note name without octave for a MIDI number, e.g. 69 → "A". */
    fun nameOf(midi: Int): String = NAMES[((midi % 12) + 12) % 12]

    /** Scientific-pitch-notation octave for a MIDI number, e.g. 60 → 4. */
    fun octaveOf(midi: Int): Int = midi / 12 - 1

    /** Nearest MIDI number for [frequencyHz], given the A4 anchor [referenceHz]. */
    fun nearestMidi(frequencyHz: Float, referenceHz: Float = 440f): Int =
        (69.0 + 12.0 * log2(frequencyHz / referenceHz)).roundToInt()

    /** Nearest note label for [frequencyHz], e.g. "A4" or "F#3"; "—" if non-positive. */
    fun label(frequencyHz: Float, referenceHz: Float = 440f): String {
        if (frequencyHz <= 0f) return "—"
        val midi = nearestMidi(frequencyHz, referenceHz)
        return "${nameOf(midi)}${octaveOf(midi)}"
    }
}
