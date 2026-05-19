package com.example.metrognome.audio.tuner

import com.example.metrognome.audio.dsp.PitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AmbientDetector] — verifies it locks onto a steady tone,
 * never locks onto a wandering one (voice), and rides out brief gaps.
 *
 * Frame timing is fixed at 93 ms — the tuner's real hop at 44.1 kHz.
 */
class AmbientDetectorTest {

    private fun detector() = AmbientDetector(hopMillis = 93.0)

    /** Feed the ~0.9 s of quiet profiling the detector needs before it decides anything. */
    private fun AmbientDetector.profileQuiet() {
        repeat(PROFILE_FRAMES) { observe(null, 0.0008f) }
    }

    private fun AmbientDetector.tone(hz: Float, level: Float = 0.06f, clarity: Float = 0.95f) =
        observe(PitchDetector.Pitch(hz, clarity), level)

    private fun AmbientDetector.quiet() = observe(null, 0.0008f)
    private fun AmbientDetector.noise(level: Float = 0.06f) = observe(null, level)

    @Test
    fun firstFrameIsProfiling() {
        assertEquals(ListeningState.PROFILING, detector().observe(null, 0.001f).state)
    }

    @Test
    fun silenceStaysQuietAndUnlocked() {
        val d = detector()
        d.profileQuiet()
        val r = d.quiet()
        assertEquals(ListeningState.QUIET, r.state)
        assertFalse(r.locked)
    }

    @Test
    fun loudBroadbandSoundReportsNoise() {
        val d = detector()
        d.profileQuiet()
        var r = d.noise()
        repeat(7) { r = d.noise() }
        assertEquals(ListeningState.NOISE, r.state)
        assertFalse(r.locked)
    }

    @Test
    fun steadyToneLocks() {
        val d = detector()
        d.profileQuiet()
        var r = d.tone(220f)
        repeat(20) { r = d.tone(220f) }
        assertEquals(ListeningState.LOCKED, r.state)
        assertTrue(r.locked)
    }

    @Test
    fun acquiringPrecedesLock() {
        val d = detector()
        d.profileQuiet()
        val states = (1..20).map { d.tone(330f).state }
        val firstAcquire = states.indexOf(ListeningState.ACQUIRING)
        val firstLock = states.indexOf(ListeningState.LOCKED)
        assertTrue("should pass through ACQUIRING", firstAcquire >= 0)
        assertTrue("should reach LOCKED", firstLock >= 0)
        assertTrue("ACQUIRING must come before LOCKED", firstAcquire < firstLock)
    }

    @Test
    fun jumpingPitchNeverLocks() {
        // Alternating an octave and a half apart — the signature of a voice.
        val d = detector()
        d.profileQuiet()
        val states = (0 until 24).map { i -> d.tone(if (i % 2 == 0) 200f else 500f).state }
        assertFalse("a wandering pitch must never lock", states.any { it == ListeningState.LOCKED })
    }

    @Test
    fun lockRidesABriefGap() {
        val d = detector()
        d.profileQuiet()
        repeat(12) { d.tone(330f) }            // lock on
        val gap = (1..3).map { d.noise().state }   // 3 frames of noise, no tone
        assertTrue("a short gap must not break the lock", gap.all { it == ListeningState.LOCKED })
    }

    @Test
    fun lockDropsAfterASustainedGap() {
        val d = detector()
        d.profileQuiet()
        repeat(12) { d.tone(330f) }
        var r = d.noise()
        repeat(8) { r = d.noise() }
        assertFalse("a long gap must release the lock", r.locked)
    }

    @Test
    fun lockSurvivesASlowTuningGlide() {
        // A string being tuned glides smoothly — the lock must follow it.
        val d = detector()
        d.profileQuiet()
        repeat(12) { d.tone(220f) }
        var r = d.tone(220f)
        var hz = 220f
        repeat(15) {
            hz *= 1.004f                       // ≈ 7 cents per frame — a gentle glide
            r = d.tone(hz)
        }
        assertTrue("a slow glide must stay locked", r.locked)
    }

    @Test
    fun steadyRoomToneIsNotMistakenForANote() {
        // A constant hum present from the very start is part of the room, not a note.
        val d = detector()
        repeat(PROFILE_FRAMES) { d.observe(PitchDetector.Pitch(120f, 0.95f), 0.02f) }
        var r = d.observe(PitchDetector.Pitch(120f, 0.95f), 0.02f)
        repeat(20) { r = d.observe(PitchDetector.Pitch(120f, 0.95f), 0.02f) }
        assertFalse("a steady room hum must never lock", r.locked)
    }

    private companion object {
        /** ceil(PROFILE_MS / hopMs) for the 900 ms profile at a 93 ms hop. */
        const val PROFILE_FRAMES = 10
    }
}
