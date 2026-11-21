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
 * The noise Hi-Hat ([HiHatVoice]), proven on its own before the engine plays it.
 *
 * The bug it fixes: the old sine pair's accent was only louder, and a phone limiting at
 * high volume levels peaks. So the central bar compares closed and open **normalised to
 * the same peak** (a limiter's worst case) and demands they still differ by how long they
 * ring. [theOldSinePairFailsTheSameBar] shows that bar catches the old voice; delete it
 * with the old pair once [HiHatVoice.LIVE] ships.
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

    /** The same equal-peak bar, on the old sine pair still in the engine: it must fail it. */
    @Test
    fun theOldSinePairFailsTheSameBar() {
        fun engineBuffer(name: String): FloatArray {
            val f = MetronomeEngine::class.java.getDeclaredField(name).apply { isAccessible = true }
            return toFloat(f.get(MetronomeEngine()) as ShortArray)
        }
        val c = peakNormalised(engineBuffer("hihatClick"))
        val o = peakNormalised(engineBuffer("hihatAccent"))
        val ratio = ringMs(o) / ringMs(c)
        println("old pair ring ratio at equal peak: ${"%.2f".format(ratio)}")
        assertTrue("the old accent is not distinct by length, ratio $ratio", ratio < 2.0)
        assertTrue("the old voice is a tone", spectrum(c).max() > 0.1)
    }

    /** The switch: the engine plays this voice only when told to, on both beat kinds. */
    @Test
    fun theEngineSwitchSelectsTheVoice() {
        val engine = MetronomeEngine()
        val build = MetronomeEngine::class.java.getDeclaredMethod(
            "buildBeatBuffer", Int::class.java, Boolean::class.java, Int::class.java,
        ).apply { isAccessible = true }
        val beat = sr // a 60 BPM beat, long enough for either hit
        fun play(accent: Boolean) = (build.invoke(engine, beat, accent, 1) as ShortArray).copyOf(
            if (accent) HiHatVoice.open(sr).size else HiHatVoice.closed(sr).size,
        )
        engine.noiseHiHat = true
        assertArrayEquals(HiHatVoice.closed(sr), play(false))
        assertArrayEquals(HiHatVoice.open(sr), play(true))
        engine.noiseHiHat = false
        assertTrue(!HiHatVoice.closed(sr).contentEquals(play(false)))
    }
}
