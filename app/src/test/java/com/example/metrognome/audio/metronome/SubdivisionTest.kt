package com.example.metrognome.audio.metronome

import com.example.metrognome.audio.metronome.subdivision.Subdivision
import com.example.metrognome.audio.metronome.subdivision.SubdivisionGrid
import com.example.metrognome.audio.metronome.subdivision.SubdivisionLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Subdivision: the grid, the per-voice limits, and the engine guard that turning it on moves
 * nothing that was already there (the beat's frame, its first samples, the buffer's length).
 */
class SubdivisionTest {

    private val sr = 44_100
    private val names = listOf(
        "Classic", "Hi-Hat", "Wood", "Warm", "Bell", "Crystal Bowl",
        "Kalimba", "Cowbell", "Metal Kick", "Studio Hi-Hat",
    )

    private fun voice(engine: MetronomeEngine, v: Int, accent: Boolean): ShortArray {
        val m = MetronomeEngine::class.java.getDeclaredMethod(
            "voiceFor", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return m.invoke(engine, v, accent) as ShortArray
    }

    /** The pre-subdivision beat writer, verbatim, as the reference for "Off changed nothing". */
    private fun legacyBeat(click: ShortArray, samplesPerBeat: Int, vol: Float): ShortArray {
        val buf = ShortArray(samplesPerBeat)
        val len = minOf(click.size, samplesPerBeat)
        val fade = if (len < click.size) (sr * 20 / 1000).coerceAtMost(len) else 0
        for (i in 0 until len) {
            val g = if (fade > 0 && i >= len - fade) (len - i).toFloat() / fade else 1f
            buf[i] = (click[i] * vol * g).toInt().toShort()
        }
        return buf
    }

    @Test
    fun ticksDivideTheBeatWithinHalfAFrame() {
        for (spb in listOf(8_820, 19_313, 19_314, 26_460, 132_300)) {
            for (n in 2..4) {
                assertEquals(0, SubdivisionGrid.offset(0, spb, n))
                assertEquals("the last window ends on the beat", spb, SubdivisionGrid.offset(n, spb, n))
                for (k in 1 until n) {
                    val ideal = k * spb.toDouble() / n
                    assertTrue("tick $k of $n at $spb", abs(SubdivisionGrid.offset(k, spb, n) - ideal) <= 0.5)
                }
            }
        }
    }

    @Test
    fun theCycleVisitsEverySettingAndComesBack() {
        assertEquals(listOf(Subdivision.TWO, Subdivision.THREE, Subdivision.FOUR, Subdivision.OFF),
            Subdivision.entries.map { it.next() })
        assertEquals(Subdivision.OFF, Subdivision.fromClicks(7))
    }

    @Test
    fun limitsRespectTheBuzzFloorAndTheCeiling() {
        assertEquals(300, SubdivisionLimits.maxBpm(10.0, 1))
        assertEquals(300, SubdivisionLimits.maxBpm(10.0, 2))      // 60 ms floor: 500 BPM, capped at 300
        assertEquals(250, SubdivisionLimits.maxBpm(10.0, 4))      // 4 x 60 ms = 240 ms a beat
        assertEquals(120, SubdivisionLimits.maxBpm(250.0, 2))     // a long body sets its own limit
    }

    /** Prints the table for the dev and checks it is ordered: more ticks never allow more speed. */
    @Test
    fun perVoiceLimitTable() {
        val engine = MetronomeEngine()
        println("voice            body   /2   /3   /4")
        for (v in 0 until MetronomeEngine.VOICE_COUNT) {
            val body = maxOf(
                SubdivisionLimits.bodyMs(voice(engine, v, false), sr),
                SubdivisionLimits.bodyMs(voice(engine, v, true), sr),
            )
            val limits = (2..4).map { engine.subdivisionMaxBpm(v, it) }
            println("%-15s %4.0fms %4d %4d %4d".format(names[v], body, limits[0], limits[1], limits[2]))
            assertTrue(names[v], limits[0] >= limits[1] && limits[1] >= limits[2])
            assertTrue(names[v], limits[2] >= 20)
            assertEquals(1, engine.soundingClicks(v, limits[2] + 1, 4))
            assertEquals(4, engine.soundingClicks(v, limits[2], 4))
        }
    }

    @Test
    fun offIsTheOldBeatExactly() {
        val engine = MetronomeEngine().apply { volume = 0.85f }
        for (v in 0 until MetronomeEngine.VOICE_COUNT) for (bpm in listOf(40, 120, 300)) for (accent in listOf(true, false)) {
            val spb = (sr * 60.0 / bpm).toInt()
            assertArrayEquals("${names[v]} at $bpm",
                legacyBeat(voice(engine, v, accent), spb, 0.85f),
                engine.buildBeatBuffer(spb, accent, v, clicks = 1))
        }
    }

    @Test
    fun subTicksLandOnTheirFramesAndNeverMoveTheBeat() {
        val engine = MetronomeEngine().apply { volume = 1f }
        for (v in 0 until MetronomeEngine.VOICE_COUNT) for (n in 2..4) {
            val bpm = minOf(engine.subdivisionMaxBpm(v, n), 90)
            val spb = (sr * 60.0 / bpm).toInt()
            val off = engine.buildBeatBuffer(spb, true, v, 1)
            val on = engine.buildBeatBuffer(spb, true, v, n)
            assertEquals(spb, on.size)
            val first = SubdivisionGrid.offset(1, spb, n)
            // Untouched until the beat's own window ends (it may be faded at its end).
            val clean = first - sr * 20 / 1000
            for (i in 0 until clean) assertEquals("${names[v]} /$n frame $i", off[i], on[i])
            val sub = voice(engine, v, false)
            for (k in 1 until n) {
                val start = SubdivisionGrid.offset(k, spb, n)
                val end = SubdivisionGrid.offset(k + 1, spb, n)
                for (i in 0 until 200) assertEquals("${names[v]} /$n tick $k frame $i is the beat voice at the sub level",
                    (sub[i] * MetronomeEngine.SUB_TICK_GAIN * 1f).toInt().toShort(), on[start + i])
                assertTrue("${names[v]} /$n tick $k reaches silence before the next", abs(on[end - 1].toInt()) <= 64)
            }
        }
    }
}
