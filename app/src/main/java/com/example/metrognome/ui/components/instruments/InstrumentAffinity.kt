package com.example.metrognome.ui.components.instruments

/**
 * Single source of truth mapping each metronome sound (by soundType index, as defined in
 * MetronomeEngine) to the instruments it is ideally suited for. Drives the affinity-icon
 * row in Settings: the matching instruments glow, the rest stay dim.
 *
 * Affinity is a soft nudge, not a restriction. Any sound works for any instrument. The
 * "Classic" default suits everyone, so it maps to the full set.
 *
 * To extend: add a sound to the map keyed by its soundType index. The row picks it up with
 * no UI changes.
 */
object InstrumentAffinity {

    private val BY_SOUND_TYPE: Map<Int, Set<Instrument>> = mapOf(
        0 to Instrument.entries.toSet(),                // 0 Classic (universal)
        1 to setOf(Instrument.DRUMS, Instrument.KEYS),  // 1 Hi-Hat
        2 to setOf(Instrument.KEYS),                    // 2 Wood
        3 to setOf(Instrument.VOICE),                   // 3 Warm
        4 to setOf(Instrument.KEYS, Instrument.VOICE),  // 4 Bell
        5 to setOf(Instrument.VOICE),                   // 5 Crystal Bowl
        6 to setOf(Instrument.GUITAR, Instrument.KEYS), // 6 Kalimba
        7 to setOf(Instrument.DRUMS),                   // 7 Cowbell
    )

    /** Instruments the given sound is ideal for. Empty for an unknown index. */
    fun instrumentsFor(soundType: Int): Set<Instrument> = BY_SOUND_TYPE[soundType].orEmpty()
}
