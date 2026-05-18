package com.example.metrognome.audio.dsp

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
import kotlin.random.Random

/**
 * Unit tests for [PitchDetector] — verifies the McLeod Pitch Method detects
 * synthetic signals to within a fraction of a cent across the instrument range.
 */
class PitchDetectorTest {

    private val sampleRate = 44_100
    private val windowSize = 8192

    private fun detector() = PitchDetector(sampleRate, windowSize)

    /** A window built from one or more (frequencyHz, amplitude) partials. */
    private fun signal(vararg partials: Pair<Double, Double>): FloatArray =
        FloatArray(windowSize) { i ->
            var sum = 0.0
            for ((freq, amp) in partials) {
                sum += amp * sin(2.0 * PI * freq * i / sampleRate)
            }
            sum.toFloat()
        }

    /** Cents between a detected and an expected frequency. */
    private fun centsError(detected: Float, expected: Double): Double =
        1200.0 * log2(detected / expected)

    private fun assertDetectsWithin(expectedHz: Double) {
        val pitch = detector().detect(signal(expectedHz to 0.6))
        assertNotNull("expected a detection at $expectedHz Hz", pitch)
        val err = centsError(pitch!!.frequency, expectedHz)
        assertTrue(
            "detection at $expectedHz Hz was ${"%.3f".format(err)} cents off (limit 1.0)",
            abs(err) <= 1.0,
        )
    }

    @Test
    fun detectsPureTonesAcrossTheRange() {
        // Low bass through to the top of the treble staff — all within one cent.
        for (hz in listOf(55.0, 82.41, 110.0, 220.0, 440.0, 880.0, 1760.0)) {
            assertDetectsWithin(hz)
        }
    }

    @Test
    fun detectsConcertAExactly() {
        val pitch = detector().detect(signal(440.0 to 0.6))
        assertNotNull(pitch)
        // A pure 440 Hz tone should land extremely close after parabolic refinement.
        assertTrue(abs(centsError(pitch!!.frequency, 440.0)) < 0.5)
        assertTrue("a pure tone should report high clarity", pitch.clarity > 0.9f)
    }

    @Test
    fun locksFundamentalOfAHarmonicRichTone() {
        // A sawtooth-like tone: fundamental plus decaying harmonics. MPM must
        // report the fundamental, not an octave up or a harmonic.
        val f0 = 146.83   // D3
        val rich = signal(
            f0 to 1.0,
            f0 * 2 to 0.5,
            f0 * 3 to 0.33,
            f0 * 4 to 0.25,
            f0 * 5 to 0.20,
        )
        val pitch = detector().detect(rich)
        assertNotNull(pitch)
        assertTrue(
            "harmonic-rich tone resolved to ${pitch!!.frequency} Hz, expected ~$f0",
            abs(centsError(pitch.frequency, f0)) <= 3.0,
        )
    }

    @Test
    fun resistsOctaveErrorWithStrongSecondHarmonic() {
        // Second harmonic louder than the fundamental — a classic octave trap.
        val f0 = 196.0   // G3
        val pitch = detector().detect(signal(f0 to 0.4, f0 * 2 to 0.9))
        assertNotNull(pitch)
        assertTrue(
            "should report the fundamental ~$f0, got ${pitch!!.frequency}",
            abs(centsError(pitch.frequency, f0)) <= 5.0,
        )
    }

    @Test
    fun silenceReturnsNull() {
        assertNull(detector().detect(FloatArray(windowSize)))
    }

    @Test
    fun lowLevelNoiseReturnsNull() {
        val rng = Random(7)
        val noise = FloatArray(windowSize) { (rng.nextFloat() * 2f - 1f) * 0.002f }
        assertNull("faint noise should not yield a pitch", detector().detect(noise))
    }

    @Test
    fun referencePitchIsWindowSize() {
        // Guards the contract the calibrator and tuner rely on.
        assertTrue(detector().windowSize == windowSize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongWindowLength() {
        detector().detect(FloatArray(windowSize - 1))
    }
}
