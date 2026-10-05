package com.example.metrognome.audio.metronome

/**
 * Hands out whole-frame beat lengths whose running total tracks the ideal tempo.
 *
 * A beat at most tempos is not a whole number of frames (137 BPM at 44.1 kHz is
 * 19313.87). Rounding each beat on its own loses the same fraction every beat, so the
 * metronome ran ~160 ms per hour fast at 137 BPM. Carrying the remainder instead makes
 * the lengths alternate (19313, 19314, ...) and keeps the click within one frame of
 * the ideal grid indefinitely. The carry survives a tempo change, so a BPM edit
 * mid-play does not jolt the phase either.
 */
internal class BeatFrameClock(private val sampleRate: Int) {
    private var carry = 0.0

    /** Frames for the next beat at [bpm]. */
    fun next(bpm: Int): Int {
        val ideal = sampleRate * 60.0 / bpm + carry
        val frames = ideal.toInt()
        carry = ideal - frames
        return frames
    }
}
