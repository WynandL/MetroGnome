package com.example.metrognome.groove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GrooveScorer] - the decoupled length-bounded Gnotes bonus and the nearest-beat
 * folding helper. Session grading itself lives in (and is tested via) [SessionAnalyzer].
 */
class GrooveScorerTest {

    @Test
    fun bonusScalesWithMinutesNotQuality() {
        val fraction = 0.8f
        assertEquals(8, GrooveScorer.bonusGnotes(fraction, hitCount = 50, sessionMinutes = 10))
        assertEquals(4, GrooveScorer.bonusGnotes(fraction, hitCount = 50, sessionMinutes = 5))
    }

    @Test
    fun subMinuteQualifyingSessionEarnsAtLeastOne() {
        // A quick demo: good fraction but 0 whole minutes still pays 1, never a bare zero.
        assertEquals(1, GrooveScorer.bonusGnotes(0.9f, hitCount = 10, sessionMinutes = 0))
    }

    @Test
    fun unqualifiedOrZeroEarnsNothing() {
        assertEquals(0, GrooveScorer.bonusGnotes(0.9f, hitCount = 3, sessionMinutes = 5))   // too few hits
        assertEquals(0, GrooveScorer.bonusGnotes(0f, hitCount = 50, sessionMinutes = 5))     // graded zero
    }

    // ── nearestBeatDeviation: the nearest-beat folding both timing-bonus paths use ──────────────

    private val tol = 0.001f

    @Test
    fun clapJustAfterTheBeatStaysSmallPositive() {
        // 120 BPM -> 500 ms interval. A clap 30 ms behind the beat reads as +30.
        assertEquals(30f, GrooveScorer.nearestBeatDeviation(30f, 500f), tol)
    }

    @Test
    fun clapAnticipatingNextBeatReadsEarlyNotVeryLate() {
        // 120 BPM. A clap 30 ms BEFORE the next beat lands 470 ms after the last beat. Previous-beat
        // math logged +470 (a wildly late hit); folding gives the true -30 (early).
        assertEquals(-30f, GrooveScorer.nearestBeatDeviation(470f, 500f), tol)
    }

    @Test
    fun exactlyOnTheBeatIsZero() {
        assertEquals(0f, GrooveScorer.nearestBeatDeviation(0f, 500f), tol)
    }

    @Test
    fun earlyClapAtSlowTempoNoLongerRejected() {
        // 40 BPM -> 1500 ms interval. A clap 100 ms ahead of a beat lands 1400 ms after the prior
        // beat. Previous-beat math gave +1400, which the +/-500 ms accept gate discarded entirely.
        // Folding gives -100, comfortably inside the window.
        val folded = GrooveScorer.nearestBeatDeviation(1400f, 1500f)
        assertEquals(-100f, folded, tol)
        assertTrue("a 100 ms-early clap must be accepted at 40 BPM", kotlin.math.abs(folded) <= 500f)
    }

    @Test
    fun foldedDeviationNeverExceedsHalfTheInterval() {
        val interval = 500f
        var d = -2000f
        while (d <= 2000f) {
            val folded = GrooveScorer.nearestBeatDeviation(d, interval)
            assertTrue(
                "delta $d folded to $folded, outside +/- interval/2",
                kotlin.math.abs(folded) <= interval / 2f + tol,
            )
            d += 7f   // walk a non-divisor step so we sample many phases
        }
    }

    @Test
    fun nonPositiveIntervalIsSafeNoOp() {
        assertEquals(123f, GrooveScorer.nearestBeatDeviation(123f, 0f), tol)
        assertEquals(123f, GrooveScorer.nearestBeatDeviation(123f, -10f), tol)
    }
}
