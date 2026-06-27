package com.example.metrognome.audio.rhythm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the *sustained* background level of a mic session from the visualizer amplitude that
 * [RhythmDetector.amplitude] already emits, and decides when the room is too noisy for reliable
 * clap timing.
 *
 * This is deliberately a passive observer of the detector's public amplitude flow - it never
 * touches capture or detection, so it can only ever *inform*, never reject a real clap. It backs a
 * single small UI cue: the live room-noise indicator shown in the three mic surfaces (see
 * RoomNoiseIndicator), the live complement to the enable-time self-test, which only checks the room
 * once, at calibration.
 *
 * ## Telling a clap from a noisy room
 * A clap is a brief, loud spike; a noisy room is a sustained raised level. The floor is an
 * asymmetric EMA of the amplitude: it falls fast (snapping to the quiet gaps *between* claps) and
 * rises slowly, so a brief clap barely lifts it before the next gap pulls it back, while sustained
 * noise - which has no quiet gaps - drags it up within a couple of seconds. The room is then judged
 * noisy with hysteresis so the advisory does not flicker around the threshold.
 *
 * Amplitudes are on [RhythmDetector]'s 0..1 scale (raw RMS / 8000). Thresholds are advisory-only;
 * the reported floors exist precisely so they can be retuned from field data later.
 */
class RoomNoiseMonitor {

    private val _noisy = MutableStateFlow(false)
    /** True while the sustained room level is above the noisy threshold (with hysteresis). */
    val noisy: StateFlow<Boolean> = _noisy.asStateFlow()

    // Current sustained-noise floor estimate (0..1); the EMA state behind the noisy decision.
    private var floor = 0f
    private var seeded = false

    /** Feed one amplitude sample (one per detector buffer read, ~20-65 ms apart). */
    fun update(amplitude: Float) {
        val a = amplitude.coerceIn(0f, 1f)
        if (!seeded) {
            floor = a
            seeded = true
            return
        }
        // Asymmetric EMA: fast down to the quiet gaps between claps, slow up so a brief clap can't
        // drag the floor with it but sustained noise eventually does.
        val alpha = if (a < floor) FALL_ALPHA else RISE_ALPHA
        floor += alpha * (a - floor)

        val noisyNow = when {
            floor >= NOISY_ON -> true
            floor <= NOISY_OFF -> false
            else -> _noisy.value   // inside the hysteresis band: hold the current state
        }
        if (noisyNow != _noisy.value) _noisy.value = noisyNow
    }

    /** Clear all state so the same instance can be reused for a fresh session. */
    fun reset() {
        seeded = false
        floor = 0f
        _noisy.value = false
    }

    companion object {
        /** Snap-down speed toward a quiet gap (per sample). Fast: a real gap should reset the floor. */
        private const val FALL_ALPHA = 0.30f
        /**
         * Rise speed toward a higher level (per sample). Deliberately slow (~5 s) so only genuinely
         * SUSTAINED noise lifts the floor; brief claps and transient bumps barely move it. Kept low
         * so the indicator stays calm rather than twitching at every passing sound.
         */
        private const val RISE_ALPHA = 0.006f
        /** Sustained floor at/above which the room is called noisy. Set high so it only trips on a
         *  room that is clearly, persistently loud - not on ordinary playing. */
        private const val NOISY_ON = 0.16f
        /** ...and below which it is called quiet again (wide hysteresis gap avoids any flicker). */
        private const val NOISY_OFF = 0.09f
    }
}
