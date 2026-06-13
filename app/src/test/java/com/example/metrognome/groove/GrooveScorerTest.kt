package com.example.metrognome.groove

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GrooveScorer] - verifies the consistency-first, bias-lenient grading and the
 * decoupled length-bounded Gnotes bonus.
 */
class GrooveScorerTest {

    @Test
    fun tooFewHitsIsUnqualified() {
        val r = GrooveScorer.score(listOf(0f, 5f, 2f))   // 3 < MIN_HITS
        assertFalse(r.qualified)
        assertEquals(0, r.grooveScore)
        assertEquals(3, r.hitCount)
    }

    @Test
    fun tightOnTheBeatScoresNearPerfect() {
        val r = GrooveScorer.score(listOf(2f, -3f, 1f, 0f, -2f, 3f))
        assertTrue(r.qualified)
        assertTrue("expected high score, got ${r.grooveScore}", r.grooveScore >= 95)
        assertEquals("Locked in", r.read)
    }

    @Test
    fun consistentButLateStillScoresWell() {
        // The "friend's case": tightly clustered but ~140ms behind the beat. The old absolute
        // curve gave ~0; consistency-first grading should credit the steadiness.
        val r = GrooveScorer.score(listOf(135f, 140f, 145f, 138f, 142f, 140f))
        assertTrue(r.qualified)
        assertTrue("expected a solid score for steady-but-late, got ${r.grooveScore}", r.grooveScore in 60..80)
        assertEquals("Steady but behind the beat", r.read)
        assertTrue(r.biasMs > 100f)
        assertTrue("jitter should be small", r.jitterMs < 10f)
    }

    @Test
    fun scatteredTimingScoresLow() {
        val r = GrooveScorer.score(listOf(-150f, 150f, -120f, 130f, -140f, 160f))
        assertTrue("expected a low score for scattered hits, got ${r.grooveScore}", r.grooveScore <= 25)
        assertEquals("Loose timing", r.read)
    }

    @Test
    fun aheadOfTheBeatReadsAhead() {
        val r = GrooveScorer.score(listOf(-130f, -140f, -135f, -138f, -142f, -136f))
        assertEquals("Steady but ahead of the beat", r.read)
        assertTrue(r.biasMs < 0f)
    }

    @Test
    fun grooveScoreIsLengthIndependent() {
        // The same playing quality grades identically regardless of how many hits / how long.
        val short = GrooveScorer.score(List(6) { 5f })
        val long = GrooveScorer.score(List(200) { 5f })
        assertEquals(short.grooveScore, long.grooveScore)
    }

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
}
