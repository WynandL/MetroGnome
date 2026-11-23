package com.example.metrognome.audio.metronome.subdivision

/**
 * Subdivision: extra, quieter ticks evenly spaced inside every beat, so the player hears the
 * beat split into [clicks] (2 = eighths over a quarter beat, 3 = triplets, 4 = sixteenths).
 *
 * It is not a second engine. `MetronomeEngine`'s one play loop still writes one buffer per
 * beat, timed by `BeatFrameClock`; a subdivided beat is the same buffer with the sub-ticks
 * placed inside it by [SubdivisionGrid]. Beat timing, `onBeat` / `onBeatTimed` and the mic
 * reference do not change. The parts:
 * - [SubdivisionGrid]: where each tick falls in a beat (pure).
 * - [SubdivisionLimits]: the fastest tempo each voice can be subdivided at (pure, measured
 *   from the voice's own buffers).
 * - `MetronomeEngine.buildBeatBuffer`: writes the ticks, one window each, never layered.
 *
 * Named by clicks per beat rather than note value, because the beat's value depends on the
 * meter (in 6/8 the beat is a dotted quarter). The website's widget uses the same Off/2/3/4.
 */
enum class Subdivision(val clicks: Int, val slug: String) {
    OFF(1, "off"),
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4");

    /** The player's words: "Off", "2 per beat". */
    val label: String get() = if (this == OFF) "Off" else "$clicks per beat"

    /** The next setting round the cycle Off, 2, 3, 4, Off (the Home key). */
    fun next(): Subdivision = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromClicks(clicks: Int): Subdivision = entries.firstOrNull { it.clicks == clicks } ?: OFF
    }
}

/** Where the ticks of a subdivided beat fall, in frames from the beat's first frame. */
object SubdivisionGrid {

    /**
     * Frame of tick [k] (0 until [clicks]) in a beat of [samplesPerBeat] frames: k/clicks of
     * the beat, rounded to the nearest frame. No carry is needed: `BeatFrameClock` already
     * keeps every beat's start within a frame of the ideal grid, and each tick is placed
     * against its own beat, so the error is under one frame and never accumulates.
     */
    fun offset(k: Int, samplesPerBeat: Int, clicks: Int): Int =
        ((2L * k * samplesPerBeat + clicks) / (2L * clicks)).toInt()
}
