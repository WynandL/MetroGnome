package com.example.metrognome.audio.tuner

import com.example.metrognome.audio.dsp.PitchDetector
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Decides whether the presence probe may confirm a held lock this frame.
 *
 * The probe measures the NSDF at the locked note's period. That is specific evidence
 * against an unrelated interferer (speech, another note a fifth away), but not against a
 * note at an integer multiple of the locked one: A5 alone also repeats every A4 period, so
 * presence reads ~1.0 for A4 whether A4 is still sounding or not, and a lock on A4 used
 * to survive for as long as A5 was played (review finding A02, reproduced on a phone).
 *
 * In exactly that case, global pitch a clear 2x/3x/4x of the lock, the probe's answer is
 * replaced by spectral evidence: the locked note's partials the new note cannot produce
 * (for an octave, f0, 3f0, 5f0...) must stand clearly above the local noise floor. A
 * fundamental that is still sounding keeps its lock even well below its own octave (a
 * strong-second-harmonic instrument), as long as one of those partials clears
 * [PARTIAL_PROMINENCE]; one that has stopped loses the presence confirmation, so the lock
 * is ridden out as a disturbance and released.
 *
 * Limits: below about 36 Hz (39 Hz at 48 kHz; a 5-string bass's low B) the spectrum
 * cannot resolve the partials and the probe's answer is kept, so the octave ambiguity
 * remains there. Energy from another source in a
 * searched band counts as evidence. The 10x bar was set against white noise and the phone
 * runs at A4 and above, not every coloured or tonal room.
 */
internal object HarmonicPresenceGuard {

    /** Integer multiples of the lock that make the presence probe ambiguous. */
    private val MULTIPLES = 2..4

    /** How close (cents) the global pitch must be to an exact multiple to count as one. */
    private const val MULTIPLE_CENTS = 30.0

    /**
     * Peak-to-local-floor magnitude ratio a non-shared partial must reach to count as
     * evidence the old note is still sounding: 10x, 20 dB above the surrounding spectrum.
     * Noise alone measured up to ~4; a fundamental 26 dB under its own multiple over a quiet
     * floor reads 120+, and 25+ in the worst case tested (bass E1 under its octave, whose
     * leakage lifts the floor). See PitchDetectorTest.
     */
    const val PARTIAL_PROMINENCE = 10f

    /** The integer multiple [pitch] is of [targetHz], or 0 if it is not a clear one. */
    fun multipleOf(pitch: PitchDetector.Pitch?, targetHz: Float): Int {
        if (pitch == null || pitch.clarity < AmbientDetector.HOLD_CLARITY || targetHz <= 0f) return 0
        val ratio = pitch.frequency / targetHz
        val k = ratio.roundToInt()
        if (k !in MULTIPLES) return 0
        return if (abs(1200.0 * log2(ratio / k.toDouble())) <= MULTIPLE_CENTS) k else 0
    }

    /**
     * [nearClarity] (the presence probe's value for [targetHz]) if it can be trusted this
     * frame, otherwise 0. Costs one extra FFT only on frames where the global pitch is a
     * multiple of the lock and the probe would otherwise have confirmed it.
     */
    fun gate(
        detector: PitchDetector,
        window: FloatArray,
        pitch: PitchDetector.Pitch?,
        targetHz: Float?,
        nearClarity: Float,
    ): Float {
        if (targetHz == null || nearClarity < AmbientDetector.HOLD_CLARITY) return nearClarity
        val k = multipleOf(pitch, targetHz)
        if (k == 0) return nearClarity
        val evidence = detector.partialEvidence(window, targetHz, k)
        // NaN: the note is too low (under about 36 Hz, 39 Hz at 48 kHz) to judge. Keep
        // the probe's answer there, i.e. the pre-guard behaviour with its octave ambiguity,
        // rather than release a fundamental that may well still be sounding.
        if (evidence.isNaN()) return nearClarity
        return if (evidence >= PARTIAL_PROMINENCE) nearClarity else 0f
    }
}
