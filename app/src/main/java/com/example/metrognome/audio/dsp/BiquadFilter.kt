package com.example.metrognome.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A second-order IIR (biquad) filter — Transposed Direct Form II.
 *
 * A small, reusable DSP primitive. Coefficients follow the Audio EQ Cookbook
 * (Robert Bristow-Johnson). Construct one via a factory ([highPass]), then
 * stream samples through [process] one at a time. [reset] clears the internal
 * delay line so the same instance can be reused for a new signal.
 *
 * Transposed Direct Form II is used for its good floating-point numerical
 * behaviour and minimal state (two registers).
 */
class BiquadFilter private constructor(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float,
) {
    private var s1 = 0f
    private var s2 = 0f

    /** Process a single sample, returning the filtered output. */
    fun process(x: Float): Float {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    /** Clear the delay line — call before reusing the filter on a fresh signal. */
    fun reset() {
        s1 = 0f
        s2 = 0f
    }

    companion object {
        /**
         * A high-pass filter passing frequencies above [cutoffHz].
         *
         * [q] sets the resonance at the cutoff; 0.707 gives a maximally flat
         * (Butterworth) passband — the right default for clean transient work.
         */
        fun highPass(sampleRate: Int, cutoffHz: Float, q: Float = 0.707f): BiquadFilter {
            val w0 = 2.0 * PI * cutoffHz / sampleRate
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b0 = (1.0 + cosW0) / 2.0
            val b1 = -(1.0 + cosW0)
            val b2 = (1.0 + cosW0) / 2.0
            val a0 = 1.0 + alpha
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha

            return BiquadFilter(
                b0 = (b0 / a0).toFloat(),
                b1 = (b1 / a0).toFloat(),
                b2 = (b2 / a0).toFloat(),
                a1 = (a1 / a0).toFloat(),
                a2 = (a2 / a0).toFloat(),
            )
        }

    }
}
