package com.example.metrognome.ui.meter

import com.example.metrognome.theory.Meter
import com.example.metrognome.theory.MeterTheory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterLayoutTest {

    private fun accents(top: Int, bottom: Int) = MeterTheory.defaultAccents(Meter(top, bottom))

    @Test
    fun fourFourSitsAtItsFullPitchCentred() {
        val l = MeterLayout.line(4, accents(4, 4), available = 300f, maxPitch = 22f)
        assertEquals(22f, l.pitch, 0.001f)
        assertEquals(4 * 22f, l.width, 0.001f)
        assertEquals(11f, l.centres[0], 0.001f)
        assertEquals(11f + 3 * 22f, l.centres[3], 0.001f)
    }

    @Test
    fun sevenEightOpensGapsBeforeEachGroup() {
        val l = MeterLayout.line(7, accents(7, 8), available = 300f, maxPitch = 20f)
        val steps = (1 until 7).map { l.centres[it] - l.centres[it - 1] }
        // 2+2+3: wider steps into beats 3 and 5 (indices 2 and 4), plain ones elsewhere.
        assertEquals(listOf(20f, 31f, 20f, 31f, 20f, 20f), steps.map { Math.round(it).toFloat() })
    }

    @Test
    fun sixteenBeatsFitAPhoneWidth() {
        for (accents in listOf(accents(16, 8), (0 until 16).toSet(), emptySet())) {
            val l = MeterLayout.line(16, accents, available = 300f, maxPitch = 22f)
            assertTrue("fits: ${l.width}", l.width <= 300.001f)
            assertTrue("first cell starts at 0", l.centres.first() - l.pitch / 2 >= -0.001f)
            assertTrue("last cell ends inside", l.centres.last() + l.pitch / 2 <= l.width + 0.001f)
            assertTrue("still a usable pitch: ${l.pitch}", l.pitch >= 11f)
        }
    }

    @Test
    fun edgeCasesNeverBreak() {
        assertEquals(1, MeterLayout.line(1, setOf(0), 300f, 22f).centres.size)
        assertEquals(1, MeterLayout.line(0, emptySet(), 300f, 22f).centres.size)
        // An accent left over from a longer bar is ignored.
        val l = MeterLayout.line(3, setOf(0, 9), 300f, 22f)
        assertEquals(3 * 22f, l.width, 0.001f)
        assertEquals(16, MeterLayout.line(40, emptySet(), 300f, 22f).centres.size)
    }

    @Test
    fun theRingStartsAtTheTopAndKeepsGroupsAllTheWayRound() {
        val r = MeterLayout.ring(6, accents(6, 8))
        assertEquals(0f, r[0], 0.0001f)
        // 3+3 with a gap before each group, the bar's wrap included: symmetric halves.
        assertEquals(0.5f, r[3], 0.0001f)
        assertTrue(r.toList().zipWithNext().all { (a, b) -> b > a })
        assertTrue(r.last() < 1f)
        // One group: even spacing, beats at 12, 3, 6 and 9 o'clock.
        val four = MeterLayout.ring(4, accents(4, 4))
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f), four.toList())
        val one = MeterLayout.ring(1, setOf(0))
        assertEquals(0f, one[0], 0.0001f)
    }
}
