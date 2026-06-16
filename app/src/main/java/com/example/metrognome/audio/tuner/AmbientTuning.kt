package com.example.metrognome.audio.tuner

/**
 * Runtime-selectable strength for the tuner's *experimental* noise-fighting layers.
 *
 * The always-on, proven improvements (lenient hold clarity, the presence-probe hold, the
 * anti-hijack reading gate, robust acquire spread) are NOT controlled here — they are part
 * of the engine in every mode. This switch only scales the two experimental layers:
 *
 *  - Tier 5: how far live background noise may stretch the lock's hold/ride-out windows
 *    ([AmbientDetector.maxHoldScale]). 1.0 disables the stretch.
 *  - Tier 6: whether the presence probe subtracts the learned room-noise spectrum
 *    ([PitchDetector.denoisePresence]) — the most aggressive "lift the note out of the
 *    noise floor" behaviour.
 *
 * None of these affect the displayed pitch or its accuracy — they only change how
 * stubbornly a lock is held in a noisy room. Kept here, apart from the engines, so a
 * single value can be flipped live from a dev tool (and, later, a user-facing Advanced
 * control) without restarting capture.
 */
object AmbientTuning {

    /**
     * Ambient-suppression strength. The split reflects how proven each layer is. Note that
     * even [STANDARD] is NOT "off" — the proven layers (lenient hold clarity, presence-probe
     * hold, anti-hijack gate, robust spread) are always active, so the needle never simply
     * chases the room. The levels only add the *experimental* extras on top:
     *
     *  - [STANDARD] proven layers only; no experimental noise-fighting. A clear, low-risk
     *               improvement on the original behaviour.
     *  - [ENHANCED] adds Tier 5: the live noise estimate stretches the lock's hold windows
     *               (a gentle 1.5x). Lower-risk experimental layer; no spectral subtraction.
     *  - [MAX]      adds Tier 6 on top: maximum hold stretch (2.0x) plus spectral subtraction
     *               of the learned room noise — the most aggressive (and least proven) layer.
     */
    enum class Level(
        val label: String,
        /** Upper bound on the noise-driven hold-window stretch (Tier 5); 1.0 = no stretch. */
        val maxHoldScale: Float,
        /** Whether the presence probe applies spectral subtraction (Tier 6). */
        val denoise: Boolean,
    ) {
        STANDARD("Standard", 1.0f, false),
        ENHANCED("Enhanced", 1.5f, false),
        MAX("Max", 2.0f, true);

        fun next(): Level = entries[(ordinal + 1) % entries.size]
    }

    /** Current strength. Default [Level.MAX] = the full stack (the behaviour tested so far). */
    @Volatile
    var level: Level = Level.MAX
}
