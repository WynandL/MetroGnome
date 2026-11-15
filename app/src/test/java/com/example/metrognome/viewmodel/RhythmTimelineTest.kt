package com.example.metrognome.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmTimelineTest {

    @Test
    fun beatLengthIsExactNotWholeMilliseconds() {
        assertEquals(461.538, RhythmTimeline(130).beatMs, 0.001)
    }

    @Test
    fun notesDoNotDriftAgainstTheExactGrid() {
        // The old whole-ms interval put beat 32 of a 130 BPM game 17 ms early.
        val t = RhythmTimeline(130)
        t.observe(0, 10_000)
        val exact = 10_000 + 32 * 60_000.0 / 130
        assertEquals(exact, t.beatTimeMs(32).toDouble(), 0.5)
        for (k in 0..48) {
            assertEquals(10_000 + k * 60_000.0 / 130, t.beatTimeMs(k).toDouble(), 0.5)
        }
    }

    @Test
    fun earlyQueueEstimatesAreReplacedByTheHardwareTimestamp() {
        // Beats 0 and 1 come from the queue fallback and read 80 ms early (the output
        // latency it leaves out). Beat 2 onward carry the real timestamp.
        val t = RhythmTimeline(120)   // 500 ms
        t.observe(0, 1_000)
        t.observe(1, 1_500)
        assertEquals(2_000, t.beatTimeMs(2))
        t.observe(2, 2_080)
        t.observe(3, 2_580)
        assertEquals(1_080, t.beatTimeMs(0))
        assertEquals(5_080, t.beatTimeMs(8))
    }

    @Test
    fun aLateEarlyEstimateNeverMovesTheGridBack() {
        val t = RhythmTimeline(120)
        t.observe(2, 2_080)
        t.observe(3, 2_500)   // 80 ms early: ignored
        assertEquals(3_080, t.beatTimeMs(4))
    }

    @Test
    fun firstBeatAtOrAfterLeavesTheFullLeadIn() {
        val t = RhythmTimeline(60)    // 1000 ms
        t.observe(0, 500)
        assertEquals(3, t.firstBeatAtOrAfter(3_500))   // exactly on a beat counts
        assertEquals(4, t.firstBeatAtOrAfter(3_501))
        assertEquals(0, t.firstBeatAtOrAfter(-9_000))  // never negative
        assertTrue(t.beatTimeMs(t.firstBeatAtOrAfter(5_250)) >= 5_250)
    }

    @Test
    fun anchoredOnlyAfterABeatIsSeen() {
        val t = RhythmTimeline(100)
        assertFalse(t.anchored)
        t.observe(0, 42)
        assertTrue(t.anchored)
    }
}
