package com.example.metrognome.audio.metronome

import com.example.metrognome.audio.dsp.FFT
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The Studio Hi-Hat ([HiHatVoice], premium soundType 9), proven on its own, plus how the engine
 * plays it and the free Hi-Hat (soundType 1) beside it.
 *
 * The central bar compares closed and open **normalised to the same peak** (a phone's
 * limiter's worst case) and demands they still differ by how long they ring.
 * [theFreeHiHatsAccentIsALowerNote] covers the free voice, whose accent was only louder
 * until 2026-11-21 and now also sits a note lower.
 */
class HiHatVoiceTest {

    private val sr = 44_100
    private val closed = toFloat(HiHatVoice.closed(sr))
    private val open = toFloat(HiHatVoice.open(sr))

    private fun toFloat(pcm: ShortArray) = FloatArray(pcm.size) { pcm[it] / 32768f }

    private fun peakNormalised(x: FloatArray): FloatArray {
        val p = x.maxOf { abs(it) }
        return FloatArray(x.size) { x[it] / p }
    }

    private fun rms(x: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (i in from until minOf(to, x.size)) s += x[i].toDouble() * x[i]
        return sqrt(s / (to - from))
    }

    /** Ms until the 5 ms RMS envelope falls 20 dB below its loudest window. */
    private fun ringMs(x: FloatArray): Double {
        val w = sr / 200
        val windows = (0 until x.size / w).map { rms(x, it * w, (it + 1) * w) }
        val top = windows.max()
        val last = windows.indexOfLast { it >= top * 0.1 }
        return (last + 1) * 5.0
    }

    /** Spectral power fractions over the first 4096 samples (zero-padded). */
    private fun spectrum(x: FloatArray): DoubleArray {
        val n = 4096
        val re = FloatArray(n) { if (it < x.size) x[it] else 0f }
        val im = FloatArray(n)
        FFT(n).transform(re, im, inverse = false)
        val p = DoubleArray(n / 2) { k -> re[k].toDouble() * re[k] + im[k].toDouble() * im[k] }
        val total = p.sum()
        return DoubleArray(p.size) { p[it] / total }
    }

    private fun bandFraction(x: FloatArray, loHz: Double, hiHz: Double): Double {
        val s = spectrum(x)
        val lo = (loHz * 4096 / sr).toInt()
        val hi = (hiHz * 4096 / sr).toInt()
        return (lo until hi).sumOf { s[it] }
    }

    /** A small phone speaker's top end, roughly: two one-pole low-passes at 10 kHz. */
    private fun throughSpeakerTop(x: FloatArray): FloatArray {
        val a = exp(-2.0 * Math.PI * 10_000 / sr).toFloat()
        var y1 = 0f; var y2 = 0f
        return FloatArray(x.size) { i ->
            y1 = (1 - a) * x[i] + a * y1
            y2 = (1 - a) * y1 + a * y2
            y2
        }
    }

    @Test
    fun lengthsLevelsAndCleanEnds() {
        assertEquals(sr * HiHatVoice.CLOSED_MS / 1000, closed.size)
        assertEquals(sr * HiHatVoice.OPEN_MS / 1000, open.size)
        assertEquals(HiHatVoice.CLOSED_VOLUME, closed.maxOf { abs(it) }, 0.01f)
        assertEquals(HiHatVoice.OPEN_VOLUME, open.maxOf { abs(it) }, 0.01f)
        for ((name, x) in listOf("closed" to closed, "open" to open)) {
            assertTrue("$name starts from nothing: ${abs(x[0])}", abs(x[0]) < 0.01f)
            val last = x.copyOfRange(x.size - sr / 200, x.size).maxOf { abs(it) }
            assertTrue("$name ends at silence, last 5 ms peak $last", last < 0.01f)
            val pinned = x.count { abs(it) >= 0.99f }
            assertEquals("$name never clips", 0, pinned)
        }
    }

    @Test
    fun itIsAHatNotATone() {
        // A sine puts nearly all its power in one or two bins; a hat spreads it.
        for ((name, x) in listOf("closed" to closed, "open" to open)) {
            val biggest = spectrum(x).max()
            println("$name: loudest bin ${"%.3f".format(biggest)} of power")
            assertTrue("$name: no single bin over 5% of the power, got $biggest", biggest < 0.05)
            val high = bandFraction(x, 3_000.0, sr / 2.0)
            println("$name: ${"%.0f".format(high * 100)}% of power above 3 kHz")
            assertTrue("$name: a hat lives up high, only ${high * 100}% above 3 kHz", high > 0.9)
        }
    }

    @Test
    fun itSitsWhereAPhoneSpeakerPlays() {
        for ((name, x) in listOf("closed" to closed, "open" to open)) {
            val band = bandFraction(x, 3_000.0, 10_000.0)
            println("$name: ${"%.0f".format(band * 100)}% of power in 3 to 10 kHz")
            assertTrue("$name: most of it in 3 to 10 kHz, got ${band * 100}%", band > 0.6)
            val through = throughSpeakerTop(x)
            val kept = rms(through, 0, through.size) / rms(x, 0, x.size)
            println("$name: keeps ${"%.0f".format(kept * 100)}% of its RMS through a 10 kHz top end")
            assertTrue("$name: survives a small speaker's top end, kept ${kept * 100}%", kept > 0.5)
        }
    }

    @Test
    fun theAccentIsDistinctEvenAtTheSamePeak() {
        val c = peakNormalised(closed)
        val o = peakNormalised(open)
        val ringC = ringMs(c)
        val ringO = ringMs(o)
        println("ring to -20 dB: closed $ringC ms, open $ringO ms")
        assertTrue("open rings at least 4x longer: $ringO vs $ringC ms", ringO >= 4 * ringC)
        // Energy at equal peak: the open hat carries far more sound after the first hit.
        val eC = rms(c, 0, c.size) * sqrt(c.size.toDouble())
        val eO = rms(o, 0, o.size) * sqrt(o.size.toDouble())
        val db = 20 * log10(eO / eC)
        println("energy at equal peak: open +${"%.1f".format(db)} dB over closed")
        assertTrue("open carries at least 6 dB more energy at equal peak, got $db", db >= 6.0)
    }

    @Test
    fun theAccentIsAlsoLouder() {
        assertTrue(rms(open, 0, sr / 50) > rms(closed, 0, sr / 50))
    }

    @Test
    fun theOpenHatFitsTheBeatAtUsualTempos() {
        // 200 BPM is a 300 ms beat: the accent plays whole there; faster, the engine's 20 ms fade cuts it.
        assertTrue(open.size <= sr * 300 / 1000)
    }

    @Test
    fun itIsDeterministic() {
        assertArrayEquals(HiHatVoice.closed(sr), HiHatVoice.closed(sr))
        assertArrayEquals(HiHatVoice.open(sr), HiHatVoice.open(sr))
    }

    private fun engineBuffer(name: String): FloatArray {
        val f = MetronomeEngine::class.java.getDeclaredField(name).apply { isAccessible = true }
        return toFloat(f.get(MetronomeEngine()) as ShortArray)
    }

    private fun peakHz(x: FloatArray): Double {
        val s = spectrum(x)
        return s.indices.maxBy { s[it] } * sr / 4096.0
    }

    /**
     * The free Hi-Hat keeps its 9 kHz tick, and its accent is now a lower note (7 kHz) as well
     * as longer and louder, so a phone's limiter cannot make the two sound alike.
     */
    @Test
    fun theFreeHiHatsAccentIsALowerNote() {
        val beat = peakHz(engineBuffer("hihatClick"))
        val accent = peakHz(engineBuffer("hihatAccent"))
        println("free Hi-Hat: beat ${"%.0f".format(beat)} Hz, accent ${"%.0f".format(accent)} Hz")
        assertEquals(9000.0, beat, 50.0)
        assertEquals(7000.0, accent, 50.0)
        // About four semitones; at these heights the ear needs a clear step, not a cent or two.
        assertTrue(accent / beat < 0.85)
    }

    private val engine = MetronomeEngine()

    private fun beatBuffer(voice: Int, accent: Boolean): ShortArray =
        engine.buildBeatBuffer(sr, accent, voice) // a 60 BPM beat

    /** soundType 9 plays this voice on both beat kinds; the free Hi-Hat (1) does not. */
    @Test
    fun theEnginePlaysTheStudioHiHatAsVoiceNine() {
        assertArrayEquals(HiHatVoice.closed(sr), beatBuffer(9, false).copyOf(HiHatVoice.closed(sr).size))
        assertArrayEquals(HiHatVoice.open(sr), beatBuffer(9, true).copyOf(HiHatVoice.open(sr).size))
        assertTrue(!HiHatVoice.closed(sr).contentEquals(beatBuffer(1, false).copyOf(HiHatVoice.closed(sr).size)))
    }

    /** The stopped preview is a bar with its accent first, so the open hat is heard before buying. */
    @Test
    fun thePreviewIsABarWithTheAccentFirst() {
        val build = MetronomeEngine::class.java.getDeclaredMethod("buildPreviewBuffer", Int::class.java)
            .apply { isAccessible = true }
        val preview = build.invoke(engine, 9) as ShortArray
        val beat = preview.size / 4
        val open = HiHatVoice.open(sr)
        val closed = HiHatVoice.closed(sr)
        assertArrayEquals(open, preview.copyOfRange(0, open.size))
        for (b in 1..3) assertArrayEquals(closed, preview.copyOfRange(b * beat, b * beat + closed.size))
    }
}
