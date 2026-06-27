package com.example.metrognome.groove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Unit tests for [SessionAnalyzer] - the post-session rhythm analysis. Each test builds a synthetic
 * session (onset timestamps) standing in for a real scenario the engine must survive: a steady
 * player on and OFF the grid, a jittery player, a dog bark mid-session, a silent minute, and pure
 * noise. The point the design rests on: a player who is steady but not phase-locked to the click
 * still scores well, while uniform noise scores nothing.
 */
class SessionAnalyzerTest {

    /** Claps every [periodMs] for [count] hits from [startMs], with uniform +/- [jitterMs] noise. */
    private fun steadyClaps(periodMs: Long, count: Int, startMs: Long = 10_000, jitterMs: Int = 0, seed: Int = 1): List<Long> {
        val rng = Random(seed)
        return (0 until count).map { i ->
            val j = if (jitterMs == 0) 0 else rng.nextInt(-jitterMs, jitterMs + 1)
            startMs + i * periodMs + j
        }
    }

    /** A regular metronome grid (beats) at [periodMs]. */
    private fun beats(periodMs: Long, count: Int, startMs: Long = 10_000): List<Long> =
        (0 until count).map { startMs + it * periodMs }

    @Test
    fun steadyOnGridScoresHighAndOnBeat() {
        val claps = steadyClaps(periodMs = 500, count = 60)
        val a = SessionAnalyzer.analyze(claps, beats(500, 70))
        assertTrue("should be confident, got $a", a.confident)
        assertTrue("groove should be high, got ${a.grooveScore}", a.grooveScore >= 80)
        assertTrue("self-consistency should be tight, got ${a.selfConsistencyMs}", a.selfConsistencyMs < 25f)
        assertTrue("valid inputs should be ~all, got ${a.validInputs}", a.validInputs >= 55)
        assertEquals("tempo ~120 BPM", 120f, a.estimatedBpm, 4f)
        assertTrue("grid jitter should be low on-grid, got ${a.gridJitterMs}", a.gridJitterMs < 30f)
    }

    @Test
    fun steadyOffGridStillScoresHigh() {
        // The crux: claps are rock-steady at 520 ms while the click runs at 500 ms. Phase drifts
        // against the grid (high grid jitter) but the player's OWN pulse is tight, so the grade is
        // high. This is the case the old grid-only scorer zeroed.
        val claps = steadyClaps(periodMs = 520, count = 60)
        val a = SessionAnalyzer.analyze(claps, beats(500, 75))
        assertTrue("should be confident, got $a", a.confident)
        assertTrue("groove should stay high off-grid, got ${a.grooveScore}", a.grooveScore >= 80)
        assertTrue("self-consistency should be tight, got ${a.selfConsistencyMs}", a.selfConsistencyMs < 25f)
        assertEquals("tempo ~115 BPM (their own)", 115f, a.estimatedBpm, 4f)
        assertTrue("grid jitter should be high (off the click), got ${a.gridJitterMs}", a.gridJitterMs > 100f)
    }

    @Test
    fun jitteryButRhythmicScoresMid() {
        val claps = steadyClaps(periodMs = 500, count = 60, jitterMs = 80, seed = 7)
        val a = SessionAnalyzer.analyze(claps, beats(500, 70))
        assertTrue("should be confident, got $a", a.confident)
        assertTrue("groove should be a middling score, got ${a.grooveScore}", a.grooveScore in 25..85)
    }

    @Test
    fun dogBarkOutliersAreRejected() {
        // Steady claps plus 5 "bark" onsets placed half a period off the pulse (the anti-phase), so
        // they are unambiguously not part of the rhythm and must be rejected. (A bark that randomly
        // lands ON the beat phase is indistinguishable from a clap by timing alone - we neither can
        // nor should reject that; this test pins the off-phase rejection that we CAN guarantee.)
        val claps = steadyClaps(periodMs = 500, count = 50).toMutableList()
        listOf(5, 15, 25, 35, 45).forEach { i -> claps += 10_000L + i * 500L + 250L }
        val a = SessionAnalyzer.analyze(claps.sorted(), beats(500, 70))
        assertTrue("should still be confident, got $a", a.confident)
        assertTrue("groove should remain high, got ${a.grooveScore}", a.grooveScore >= 80)
        assertEquals("total onsets", 55, a.totalOnsets)
        assertTrue("off-phase barks must be rejected (valid ~= the 50 claps), got ${a.validInputs}",
            a.validInputs <= 50)
    }

    @Test
    fun silentGapDoesNotBreakIt() {
        // Steady for 25 s, silent for 60 s (people talking), then steady again.
        val first = steadyClaps(periodMs = 500, count = 40, startMs = 10_000)
        val second = steadyClaps(periodMs = 500, count = 40, startMs = 10_000 + 40 * 500 + 60_000)
        val a = SessionAnalyzer.analyze(first + second, beats(500, 250))
        assertTrue("should be confident across the gap, got $a", a.confident)
        assertTrue("groove should stay high, got ${a.grooveScore}", a.grooveScore >= 80)
    }

    @Test
    fun pureNoiseScoresZero() {
        val rng = Random(42)
        val noise = (0 until 60).map { 10_000L + rng.nextLong(0, 30_000) }.sorted()
        val a = SessionAnalyzer.analyze(noise, beats(500, 80))
        assertFalse("uniform noise must not read as a rhythm, got $a", a.confident)
        assertEquals("no grade for noise", 0, a.grooveScore)
        assertTrue("rhythm strength should be weak, got ${a.rhythmStrength}",
            a.rhythmStrength < SessionAnalyzer.RHYTHM_STRENGTH_MIN)
    }

    @Test
    fun tooFewInputsIsNone() {
        val a = SessionAnalyzer.analyze(listOf(10_000L, 10_500L, 11_000L, 11_500L))
        assertFalse(a.confident)
        assertEquals(0, a.validInputs)
        assertEquals(0, a.grooveScore)
    }
}
