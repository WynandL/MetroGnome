package com.example.metrognome.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Unit tests for [FFT] — the radix-2 transform underpinning pitch detection.
 */
class FFTTest {

    @Test
    fun forwardThenInverse_recoversInput() {
        val n = 1024
        val fft = FFT(n)
        val rng = Random(42)

        val re = FloatArray(n) { rng.nextFloat() * 2f - 1f }
        val im = FloatArray(n) { rng.nextFloat() * 2f - 1f }
        val reOriginal = re.copyOf()
        val imOriginal = im.copyOf()

        fft.transform(re, im, inverse = false)
        fft.transform(re, im, inverse = true)

        for (i in 0 until n) {
            assertEquals("re[$i] round-trip", reOriginal[i], re[i], 1e-3f)
            assertEquals("im[$i] round-trip", imOriginal[i], im[i], 1e-3f)
        }
    }

    @Test
    fun constantSignal_yieldsOnlyDcBin() {
        val n = 256
        val fft = FFT(n)
        val re = FloatArray(n) { 1f }
        val im = FloatArray(n)

        fft.transform(re, im, inverse = false)

        // A constant signal has all its energy in bin 0; that bin equals the sum.
        assertEquals(n.toFloat(), re[0], 1e-2f)
        assertEquals(0f, im[0], 1e-2f)
        for (i in 1 until n) {
            assertTrue("bin $i should be ~0", kotlin.math.abs(re[i]) < 1e-2f)
        }
    }

    @Test
    fun pureSine_putsEnergyAtItsBin() {
        val n = 512
        val fft = FFT(n)
        val bin = 17
        val re = FloatArray(n) { i -> sin(2.0 * PI * bin * i / n).toFloat() }
        val im = FloatArray(n)

        fft.transform(re, im, inverse = false)

        // A real sine of integer bin k has its energy split across bins k and n-k.
        val magAtBin = kotlin.math.hypot(re[bin], im[bin])
        for (i in 1 until n / 2) {
            if (i == bin) continue
            val mag = kotlin.math.hypot(re[i], im[i])
            assertTrue("bin $i ($mag) should be far below bin $bin ($magAtBin)", mag < magAtBin * 0.01f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPowerOfTwoSize() {
        FFT(1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedArrayLength() {
        FFT(64).transform(FloatArray(64), FloatArray(32), inverse = false)
    }
}
