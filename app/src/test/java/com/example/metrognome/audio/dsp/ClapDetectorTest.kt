package com.example.metrognome.audio.dsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Unit tests for [ClapDetector]'s click-vs-clap discrimination.
 *
 * These also pin down the mic-mode accent design. The detector rejects BOTH tonal metronome
 * voices narrowly: the 1100 Hz Classic click ([classicClickIsRejected]) and its normal 1800 Hz
 * downbeat accent ([accentClickIsRejected]). Because the accent is filtered out by its own band,
 * the engine keeps its original, audibly distinct 1800 Hz accent in mic mode instead of reshaping
 * it. A genuinely broadband clap still clears both narrow bands ([realClapIsAccepted]). If a retune
 * ever lets the accent through as a phantom on-beat clap, [accentClickIsRejected] flips.
 */
class ClapDetectorTest {

    private val sampleRate = 44_100

    /** Mirrors MetronomeEngine.generateClick: a decaying sine, the exact buffer the engine plays. */
    private fun click(frequencyHz: Double, durationMs: Int, volume: Double): ShortArray {
        val n = sampleRate * durationMs / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val env = (1.0 - i.toDouble() / n).pow(1.5)
            (env * sin(2.0 * PI * frequencyHz * t) * Short.MAX_VALUE * volume).toInt().toShort()
        }
    }

    /** A broadband percussive burst with a fast decay - stands in for a bright, HF-rich hand clap. */
    private fun clapBurst(durationMs: Int, seed: Int): ShortArray {
        val n = sampleRate * durationMs / 1000
        val rng = Random(seed)
        return ShortArray(n) { i ->
            val env = (1.0 - i.toDouble() / n).pow(2.0)
            (env * (rng.nextDouble() * 2.0 - 1.0) * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
    }

    /**
     * A **cupped** hand clap: broadband but with a strong resonant hump around 1 kHz and weak
     * high-frequency content (the real cupped-palm "P1" clap of Repp 1987). This is the case the
     * field bug failed on: the 1 kHz hump loads the detector's 1100 Hz *click* band while the highs
     * stay quiet, so the high/tonal ratio collapses and the old detector files it as a click. The
     * energy is genuinely spread across the low-mids (several bands), so spectral flatness still
     * reads it as a clap.
     */
    private fun cuppedClap(durationMs: Int, seed: Int): ShortArray {
        val n = sampleRate * durationMs / 1000
        val rng = Random(seed)
        // Resonant cluster 700..1400 Hz builds the hump; a small broadband floor keeps it a real
        // (spread) burst rather than a tone, including a little HF presence.
        val r1 = BiquadFilter.bandPass(sampleRate, 700f, q = 2.5f)
        val r2 = BiquadFilter.bandPass(sampleRate, 1000f, q = 2.5f)
        val r3 = BiquadFilter.bandPass(sampleRate, 1400f, q = 2.5f)
        return ShortArray(n) { i ->
            val env = (1.0 - i.toDouble() / n).pow(2.0)
            val w = (rng.nextDouble() * 2.0 - 1.0).toFloat()
            val hump = r1.process(w) + r2.process(w) + r3.process(w)
            val sig = hump + 0.10f * w           // broadband floor (some highs, but weak)
            (env * sig * Short.MAX_VALUE * 0.45).toInt().toShort()
        }
    }

    private fun silence(durationMs: Int) = ShortArray(sampleRate * durationMs / 1000)

    /**
     * Calibrate on quiet, then feed one transient (with trailing silence so the classification
     * window can finalize), and return every classified onset.
     */
    private fun classify(transient: ShortArray): List<ClapDetector.Onset> {
        val det = ClapDetector(sampleRate)
        det.process(silence(600), silence(600).size)            // > CALIBRATION_MS of ambient
        val tail = transient + silence(40)
        return det.process(tail, tail.size)
    }

    @Test
    fun classicClickIsRejected() {
        // The 1100 Hz Classic click is the only sound the detector promises to reject. Every onset
        // it produces from that click must be marked NOT a clap.
        val onsets = classify(click(frequencyHz = 1100.0, durationMs = 38, volume = 0.72))
        assertTrue("the click should register as a transient at all", onsets.isNotEmpty())
        assertTrue(
            "every onset from the 1100 Hz click must be classified as a click, got $onsets",
            onsets.none { it.isClap },
        )
    }

    @Test
    fun realClapIsAccepted() {
        val onsets = classify(clapBurst(durationMs = 40, seed = 7))
        assertTrue("a broadband clap must produce at least one accepted clap, got $onsets",
            onsets.any { it.isClap })
    }

    @Test
    fun accentClickIsRejected() {
        // The normal 1800 Hz downbeat accent (the engine's real, audibly distinct voice) must be
        // rejected, NOT taken for a phantom on-beat clap. The dedicated 1800 Hz accent band loads
        // its `accentRms` so the high band can't clear the louder tonal band. This is the narrow
        // accent filter that lets the engine keep its original accent in mic mode.
        val onsets = classify(click(frequencyHz = 1800.0, durationMs = 50, volume = 0.92))
        assertTrue("the accent click should register as a transient", onsets.isNotEmpty())
        assertTrue(
            "the 1800 Hz accent click must be classified as a click, got $onsets",
            onsets.none { it.isClap },
        )
        // Confirm the mechanism: the accent band carries the tonal energy that defeats the clap test.
        val onset = onsets.first()
        assertTrue(
            "accent energy should dominate the high band (accent >= high / ratio), got $onset",
            onset.accentRms >= onset.highRms / ClapDetector.CLAP_BAND_RATIO,
        )
    }

    @Test
    fun cuppedClapIsAccepted() {
        // Regression for the field bug: a cupped clap (≈1 kHz hump, weak highs) must score. Before
        // the spectral-flatness path it was rejected as a click because its hump loaded the 1100 Hz
        // band and its high band was too quiet to clear the high/tonal ratio.
        val onsets = classify(cuppedClap(durationMs = 45, seed = 11))
        assertTrue("the cupped clap should register as a transient at all", onsets.isNotEmpty())
        assertTrue(
            "a cupped (1 kHz-hump) clap must produce at least one accepted clap, got $onsets",
            onsets.any { it.isClap },
        )
    }

    @Test
    fun silenceProducesNothing() {
        assertTrue(classify(silence(60)).none { it.isClap })
    }

    /**
     * Locks the flatness margin the threshold relies on: both tonal voices must read clearly below
     * [ClapDetector.CLAP_FLATNESS_MIN] and both claps clearly above, so a future band/Q retune can't
     * silently erase the separation. Also prints the readings (run and read stdout) for tuning.
     */
    @Test
    fun flatnessSeparatesTonalFromClaps() {
        fun flatnessOf(sig: ShortArray): Double {
            val o = classify(sig).firstOrNull()
            requireNotNull(o) { "expected a transient" }
            println("flatness=%.3f isClap=%s low=%.0f accent=%.0f high=%.0f"
                .format(o.flatness, o.isClap, o.lowRms, o.accentRms, o.highRms))
            return o.flatness
        }
        val min = ClapDetector.CLAP_FLATNESS_MIN
        val margin = 0.08
        println("CLAP_FLATNESS_MIN = $min")
        assertTrue("1100 Hz click must read tonal (low flatness)", flatnessOf(click(1100.0, 38, 0.72)) < min - margin)
        assertTrue("1800 Hz accent must read tonal (low flatness)", flatnessOf(click(1800.0, 50, 0.92)) < min - margin)
        assertTrue("cupped clap must read broadband (high flatness)", flatnessOf(cuppedClap(45, 11)) > min + margin)
        assertTrue("bright clap must read broadband (high flatness)", flatnessOf(clapBurst(40, 7)) > min + margin)
    }
}
