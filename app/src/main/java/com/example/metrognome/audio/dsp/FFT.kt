package com.example.metrognome.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley–Tukey FFT.
 *
 * A small, reusable DSP primitive. Construct one for a fixed power-of-two
 * [size]; the twiddle-factor and bit-reversal tables are built once up front,
 * so [transform] is allocation-free and can run on every analysis window.
 *
 * [PitchDetector] uses it to compute autocorrelation in O(n log n) via the
 * Wiener–Khinchin route: the autocorrelation of a signal is the inverse FFT of
 * its power spectrum.
 */
class FFT(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) {
            "FFT size must be a power of two ≥ 2 (was $size)"
        }
    }

    private val cosTable = FloatArray(size / 2)
    private val sinTable = FloatArray(size / 2)
    private val bitReversal = IntArray(size)

    init {
        for (i in 0 until size / 2) {
            val angle = -2.0 * PI * i / size
            cosTable[i] = cos(angle).toFloat()
            sinTable[i] = sin(angle).toFloat()
        }
        // Incremental bit-reversal permutation table (Gold–Rader).
        var j = 0
        for (i in 0 until size) {
            bitReversal[i] = j
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
        }
    }

    /**
     * Transform [re]/[im] in place; both arrays must be exactly [size] long.
     *
     * [inverse] = true performs the IFFT, including the 1/n normalisation — so a
     * forward transform followed by an inverse transform round-trips back to the
     * original input.
     */
    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        require(re.size == size && im.size == size) { "arrays must each be size $size" }

        for (i in 0 until size) {
            val j = bitReversal[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= size) {
            val half = len shr 1
            val twiddleStep = size / len
            var blockStart = 0
            while (blockStart < size) {
                var k = 0
                var twiddle = 0
                while (k < half) {
                    val c = cosTable[twiddle]
                    val s = if (inverse) -sinTable[twiddle] else sinTable[twiddle]
                    val a = blockStart + k
                    val b = a + half
                    val tr = re[b] * c - im[b] * s
                    val ti = re[b] * s + im[b] * c
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    k++
                    twiddle += twiddleStep
                }
                blockStart += len
            }
            len = len shl 1
        }

        if (inverse) {
            val norm = 1f / size
            for (i in 0 until size) {
                re[i] *= norm
                im[i] *= norm
            }
        }
    }
}
