package com.example.metrognome.audio.metronome

import com.example.metrognome.audio.dsp.BiquadFilter
import com.example.metrognome.audio.dsp.FFT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The Metal Kick premium voice (soundType 8), read straight out of the engine's private
 * buffers: it must be a usable click on a phone, not just a deep one. A kick whose energy
 * is all at 50 Hz is inaudible on a phone speaker, so the test measures what is left
 * above 300 Hz, where the speaker actually works.
 */
class MetalKickTest {

    private val sr = 44_100

    private fun buffer(name: String): FloatArray {
        val engine = MetronomeEngine()
        val field = MetronomeEngine::class.java.getDeclaredField(name).apply { isAccessible = true }
        val pcm = field.get(engine) as ShortArray
        return FloatArray(pcm.size) { pcm[it] / 32768f }
    }

    private fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        var s = 0.0
        for (i in from until to) s += x[i].toDouble() * x[i]
        return sqrt(s / (to - from)).toFloat()
    }

    /** A phone speaker, roughly: two 2nd-order high-pass stages at 350 Hz. */
    private fun throughPhoneSpeaker(x: FloatArray): FloatArray {
        val a = BiquadFilter.highPass(sr, 350f)
        val b = BiquadFilter.highPass(sr, 350f)
        return FloatArray(x.size) { b.process(a.process(x[it])) }
    }

    /** Fraction of the spectral power above [hz], over the first 4096 samples (the hit). */
    private fun powerAbove(x: FloatArray, hz: Double): Double {
        val n = 4096
        val re = FloatArray(n) { if (it < x.size) x[it] else 0f }
        val im = FloatArray(n)
        FFT(n).transform(re, im, inverse = false)
        var total = 0.0; var above = 0.0
        val cut = (hz * n / sr).toInt()
        for (k in 1 until n / 2) {
            val p = re[k].toDouble() * re[k] + im[k].toDouble() * im[k]
            total += p
            if (k >= cut) above += p
        }
        return above / total
    }

    @Test
    fun theKickIsLoudCleanAndAudibleOnAPhone() {
        val kick = buffer("kickClick")
        assertEquals("420 ms", sr * 420 / 1000, kick.size)
        val peak = kick.maxOf { abs(it) }
        assertTrue("peaks at its volume, got $peak", peak in 0.85f..0.92f)
        val clipped = kick.count { abs(it) >= 0.99f }
        assertTrue("almost nothing pinned at full scale, got $clipped samples", clipped < kick.size / 200)
        // The doof: the hit is a sustained, saturated body, not a click with a tail, so
        // its RMS over the first 50 ms is a large fraction of its peak (a sine body gives
        // ~0.5, a click-forward voice ~0.2). This is what the dev asked for over the
        // phone-speaker loudness below, and the two pull against each other.
        val hitRms = rms(kick, 0, sr / 20)
        assertTrue("a thump, not a click: hit rms ${hitRms / peak} of peak", hitRms > 0.4f * peak)
        // Through a phone speaker (nothing below ~350 Hz) the hit still lands, by the
        // body's harmonics, the punch and the beater: what comes out of a 4th-order
        // high-pass peaks at over a quarter of the full hit's peak, and its first 20 ms
        // carry over 15% of the full hit's energy there. The dev chose thump over the
        // 40% a click-forward voice managed ("I understand low frequencies are soft").
        val through = throughPhoneSpeaker(kick)
        val throughPeak = through.maxOf { abs(it) }
        assertTrue("through a phone speaker the hit peaks at ${(throughPeak / peak * 100).toInt()}% of full", throughPeak > 0.25f * peak)
        val throughHit = rms(through, 0, sr / 50)
        val fullHit = rms(kick, 0, sr / 50)
        assertTrue("through a phone speaker the first 20 ms hold ${(throughHit / fullHit * 100).toInt()}% of the hit", throughHit > 0.15f * fullHit)
        // ...but it is still a kick: most of its power sits below 300 Hz.
        val above = powerAbove(kick, 300.0)
        assertTrue("still deep: ${(above * 100).toInt()}% above 300 Hz", above < 0.6)
        // The body decays out: the last 50 ms is a sixth of the hit or less. (At a tempo
        // faster than the voice is long, buildBeatBuffer fades the cut.)
        val tail = rms(kick, kick.size - sr / 20)
        assertTrue("decays out, hit $hitRms vs tail $tail", tail < hitRms * 0.16f)
    }

    @Test
    fun theAccentHitsHarder() {
        val kick = buffer("kickClick")
        val accent = buffer("kickAccent")
        assertTrue(accent.size > kick.size)
        assertTrue("accent rms ${rms(accent, 0, sr / 20)} vs ${rms(kick, 0, sr / 20)}", rms(accent, 0, sr / 20) > rms(kick, 0, sr / 20))
    }
}
