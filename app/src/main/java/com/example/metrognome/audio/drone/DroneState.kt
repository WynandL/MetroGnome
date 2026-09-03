package com.example.metrognome.audio.drone

import com.example.metrognome.audio.NoteNames

/**
 * Everything the drone is currently set to, as one immutable value.
 *
 * The ViewModel owns one of these and persists it; the panel renders it; the engine is fed
 * from it. Note choice is stored as a MIDI number rather than a frequency so the tone is
 * defined musically, and the actual Hz falls out of the tuner's own reference pitch at the
 * moment it is asked for. Move A4 to 442 and the drone follows, because there is no second
 * copy of the pitch to forget to update.
 */
data class DroneState(
    val playing: Boolean = false,
    val midi: Int = DEFAULT_MIDI,
    val timbre: DroneTimbre = DroneTimbre.WARM,
    val blend: DroneBlend = DroneBlend.ROOT,
    val volume: Float = DEFAULT_VOLUME,
) {
    /** Note label with octave, e.g. "A3". */
    val noteLabel: String get() = NoteNames.labelOf(midi)

    /** 0 = C through 11 = B; what the keyboard highlights. */
    val pitchClass: Int get() = ((midi % 12) + 12) % 12

    val octave: Int get() = NoteNames.octaveOf(midi)

    /** Sounding frequency for the tuner's current A4 anchor. */
    fun frequencyHz(referenceHz: Float): Float = NoteNames.frequencyOf(midi, referenceHz)

    /** Keep the octave, change the note. Out-of-range requests are ignored, not clamped. */
    fun withPitchClass(pitchClass: Int): DroneState {
        val candidate = (octave + 1) * 12 + pitchClass.coerceIn(0, 11)
        return if (candidate in MIN_MIDI..MAX_MIDI) copy(midi = candidate) else this
    }

    /**
     * Keep the note, change the octave.
     *
     * A request past either end is ignored rather than clamped: clamping would silently
     * change which note is sounding, and a drone that answers "octave up" by moving to a
     * different note is worse than one that does nothing.
     */
    fun shiftedOctave(delta: Int): DroneState {
        val candidate = midi + 12 * delta
        return if (candidate in MIN_MIDI..MAX_MIDI) copy(midi = candidate) else this
    }

    val canOctaveUp: Boolean get() = midi + 12 <= MAX_MIDI
    val canOctaveDown: Boolean get() = midi - 12 >= MIN_MIDI

    companion object {
        /**
         * C2 through B5: four whole octaves, from below a bass guitar's open E to above a
         * violin's open E. Whole octaves, so every note is reachable at every octave and
         * the keyboard never has to grey a key out.
         */
        const val MIN_MIDI = 36
        const val MAX_MIDI = 83

        /** A3 (220 Hz at standard pitch): comfortably mid-range for most instruments. */
        const val DEFAULT_MIDI = 57

        const val DEFAULT_VOLUME = 0.75f

        val OCTAVES = (MIN_MIDI / 12 - 1)..(MAX_MIDI / 12 - 1)
    }
}
