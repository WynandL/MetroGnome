package com.example.metrognome.ui.components.instruments

/**
 * The instrument families a metronome sound can be well suited to.
 *
 * Enum order defines the fixed left-to-right order of the affinity row, so it stays stable
 * across screens and sizes. The [label] is for accessibility only (contentDescription); the
 * affinity nudge is purely visual and never shows text. See [InstrumentAffinityRow].
 */
enum class Instrument(val label: String) {
    DRUMS("Drums"),
    KEYS("Piano and keys"),
    GUITAR("Guitar"),
    VOICE("Voice"),
}
