package com.example.metrognome.audio.dsp

import com.example.metrognome.audio.chords.ChordTimbre
import com.example.metrognome.audio.chords.ChordVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * [OnsetDetector] against audio rendered by the app's own synthesis: a pluck is an onset,
 * a decaying or wobbling note is not, and the threshold holds from a hard pluck down to a
 * soft one and up through a noisy room.
 */
class OnsetDetectorTest {

    private val sr = ChordVoice.SAMPLE_RATE
    private val hop = OnsetDetector.DEFAULT_HOP
    private val win = OnsetDetector.DEFAULT_WINDOW

    /** Run the detector over [pcm] and return each reported onset as the time (ms) its peak frame ended. */
    private fun onsetsMs(pcm: FloatArray): List<Double> {
        val d = OnsetDetector(sr)
        val window = FloatArray(win)
        val out = ArrayList<Double>()
        var end = hop
        var call = 0
        while (end <= pcm.size) {
            for (i in 0 until win) {
                val idx = end - win + i
                window[i] = if (idx >= 0) pcm[idx] else 0f
            }
            if (d.process(window).onset) out += (end - d.reportDelayFrames * hop) * 1000.0 / sr
            end += hop
            call++
        }
        return out
    }

    private fun render(events: List<ChordVoice.ToneEvent>, timbre: ChordTimbre = ChordTimbre.LEGATO_TEST, tailMs: Int = 600): FloatArray {
        val pcm = ChordVoice.render(events, 440f, timbre, tailMs)
        return FloatArray(pcm.size) { pcm[it] / 32768f }
    }

    private fun withNoise(pcm: FloatArray, rms: Float, seed: Int = 1): FloatArray {
        val r = Random(seed)
        return FloatArray(pcm.size) { pcm[it] + (r.nextFloat() * 2f - 1f) * rms * 1.73f }
    }

    private fun assertOnsetNear(onsets: List<Double>, expectedMs: Int, label: String) {
        assertTrue("$label: no onset within 70 ms after $expectedMs ms, got $onsets",
            onsets.any { it >= expectedMs - 5 && it <= expectedMs + 70 })
    }

    @Test
    fun aSinglePluckIsExactlyOneOnset() {
        val pcm = render(listOf(ChordVoice.ToneEvent(60, 200, 900, 0.8f)))
        val onsets = onsetsMs(pcm)
        assertEquals("onsets $onsets", 1, onsets.size)
        assertOnsetNear(onsets, 200, "pluck")
    }

    @Test
    fun threeOverlappingNotesAreThreeOnsetsAtTheirStarts() {
        val pcm = render(listOf(
            ChordVoice.ToneEvent(60, 200, 900, 0.8f),
            ChordVoice.ToneEvent(64, 650, 900, 0.8f),
            ChordVoice.ToneEvent(67, 1100, 900, 0.8f),
        ))
        val onsets = onsetsMs(pcm)
        assertEquals("onsets $onsets", 3, onsets.size)
        assertOnsetNear(onsets, 200, "C4")
        assertOnsetNear(onsets, 650, "E4")
        assertOnsetNear(onsets, 1100, "G4")
    }

    @Test
    fun aNoteRingingOnUnderTheNextDoesNotMaskIt() {
        // No decay at all: the earlier note is as loud as the new one throughout.
        val pcm = render(listOf(
            ChordVoice.ToneEvent(60, 200, 1400, 0.8f),
            ChordVoice.ToneEvent(64, 650, 1400, 0.8f),
            ChordVoice.ToneEvent(67, 1100, 1400, 0.8f),
        ), ChordTimbre.TEST_TONE)
        val onsets = onsetsMs(pcm)
        assertEquals("onsets $onsets", 3, onsets.size)
        assertOnsetNear(onsets, 650, "E4 over C4")
        assertOnsetNear(onsets, 1100, "G4 over C4 E4")
    }

    @Test
    fun aSoftPluckStillCounts() {
        // About -40 dBFS peak.
        val pcm = render(listOf(ChordVoice.ToneEvent(60, 200, 900, 0.01f)))
        val onsets = onsetsMs(pcm)
        assertEquals("onsets $onsets", 1, onsets.size)
        assertOnsetNear(onsets, 200, "soft pluck")
    }

    @Test
    fun vibratoAndDecayAreNotOnsets() {
        // A held note with a 6 Hz, +-30 cent vibrato and a slow decay: one onset at the start, none after.
        val n = sr * 3
        val pcm = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sr - 0.3   // starts at 300 ms, once the detector has history
            if (t < 0) continue
            val cents = 30.0 * sin(2 * PI * 6.0 * t)
            val f = 261.63 * Math.pow(2.0, cents / 1200.0)
            phase += 2 * PI * f / sr
            val env = if (t < 0.01) t / 0.01 else Math.exp(-(t - 0.01) / 1.5)
            pcm[i] = (0.6 * env * (sin(phase) + 0.5 * sin(2 * phase) + 0.3 * sin(3 * phase))).toFloat()
        }
        val onsets = onsetsMs(pcm)
        assertEquals("onsets $onsets", 1, onsets.size)
        assertOnsetNear(onsets, 300, "start")
    }

    @Test
    fun roomNoiseAloneNeverFires() {
        val quiet = withNoise(FloatArray(sr * 3), rms = 0.003f)   // about -50 dBFS
        assertEquals("onsets on noise", 0, onsetsMs(quiet).size)
    }

    @Test
    fun plucksAreStillFoundOverRoomNoise() {
        val pcm = withNoise(render(listOf(
            ChordVoice.ToneEvent(55, 200, 900, 0.5f),
            ChordVoice.ToneEvent(59, 650, 900, 0.5f),
            ChordVoice.ToneEvent(62, 1100, 900, 0.5f),
        )), rms = 0.003f)
        val onsets = onsetsMs(pcm)
        assertEquals("onsets $onsets", 3, onsets.size)
        assertOnsetNear(onsets, 200, "G3")
        assertOnsetNear(onsets, 650, "B3")
        assertOnsetNear(onsets, 1100, "D4")
    }
}
