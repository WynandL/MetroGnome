package com.example.metrognome.audio.metronome

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BeatFrameClockTest {

    @Test
    fun everySelectableTempoStaysOnTheIdealGridForAnHour() {
        // Rounding each beat on its own lost ~160 ms/hour at 137 BPM; with the carry the
        // running total must stay within one frame of the ideal at every BPM the engine allows.
        for (rate in listOf(44_100, 48_000)) {
            for (bpm in 20..300) {
                val clock = BeatFrameClock(rate)
                var total = 0L
                val beats = bpm * 60
                repeat(beats) { total += clock.next(bpm) }
                val ideal = rate * 60.0 / bpm * beats
                assertTrue(
                    "$bpm BPM at $rate Hz drifted ${total - ideal} frames in an hour",
                    abs(total - ideal) <= 1.0,
                )
            }
        }
    }

    @Test
    fun aTempoChangeKeepsThePhaseContinuous() {
        // Half an hour at 137 then half at 91: the total must equal the sum of both ideals.
        val clock = BeatFrameClock(44_100)
        var total = 0L
        repeat(137 * 30) { total += clock.next(137) }
        repeat(91 * 30) { total += clock.next(91) }
        val ideal = 44_100 * 60.0 / 137 * (137 * 30) + 44_100 * 60.0 / 91 * (91 * 30)
        assertTrue("drifted ${total - ideal} frames across a tempo change", abs(total - ideal) <= 1.0)
    }

    @Test
    fun beatLengthsDifferByAtMostOneFrame() {
        val clock = BeatFrameClock(44_100)
        val lengths = List(1000) { clock.next(137) }.toSet()
        assertTrue("lengths were $lengths", lengths == setOf(19313, 19314))
    }
}
