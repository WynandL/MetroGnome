package com.example.metrognome.audio.tuner

import com.example.metrognome.audio.dsp.PitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Review finding A02: the presence probe cannot tell a held note from a note at an integer
 * multiple of it, so a lock on A4 survived for as long as A5 was played. These run the real
 * [PitchDetector] and [AmbientDetector] over continuous synthetic audio, hop by hop in the
 * order [Tuner]'s capture loop uses (MAX ambient level: denoised presence, 2x hold), so the
 * window overlap, profiling and ride-outs are all the production ones.
 *
 * The behaviour these pin down: **a lock follows the note that is physically sounding.**
 * It is kept while the locked note is still there, however weak beside its own octave (a
 * strong-second-harmonic instrument), and released once only the multiple remains.
 */
class HarmonicPresenceTest {

    private var rate = 44_100
    private val window = 8192
    private val hop = 4096

    /** One stretch of sound: partials as (Hz, amplitude), optionally decaying. */
    private class Seg(val seconds: Double, val partials: List<Pair<Double, Double>>, val decay: Double? = null)

    private fun harmonic(f0: Double, amp: Double = 0.5) =
        listOf(f0 to amp, 2 * f0 to amp * 0.5, 3 * f0 to amp * 0.35, 4 * f0 to amp * 0.2)

    /** Phase-continuous render (each partial follows absolute time) over a quiet noise floor. */
    private fun render(vararg segs: Seg): FloatArray {
        val total = segs.sumOf { (it.seconds * rate).toInt() }
        val out = FloatArray(total)
        val rng = Random(7)
        var n0 = 0
        for (s in segs) {
            val len = (s.seconds * rate).toInt()
            for (i in 0 until len) {
                val t = (n0 + i).toDouble() / rate
                val env = s.decay?.let { exp(-(i.toDouble() / rate) / it) } ?: 1.0
                var v = 0.0
                for ((hz, a) in s.partials) v += a * env * sin(2 * PI * hz * t)
                out[n0 + i] = v.toFloat()
            }
            n0 += len
        }
        for (i in out.indices) out[i] += (rng.nextFloat() * 2f - 1f) * 0.002f * sqrt(3f)
        return out
    }

    /** Tuner's per-hop order. Returns (time of the window's last sample, report) per hop. */
    private fun run(signal: FloatArray, guard: Boolean = true): List<Pair<Double, AmbientReport>> {
        val detector = PitchDetector(rate, window).also { it.denoisePresence = true }
        val ambient = AmbientDetector(hop * 1000.0 / rate).also { it.maxHoldScale = 2f }
        var prev = AmbientReport.Idle
        var noiseFinalized = false
        val buf = FloatArray(window)
        val out = ArrayList<Pair<Double, AmbientReport>>()
        var start = 0
        while (start + window <= signal.size) {
            System.arraycopy(signal, start, buf, 0, window)
            val pitch = detector.detect(buf)
            var sq = 0.0
            for (v in buf) sq += v.toDouble() * v
            val rms = sqrt(sq / window).toFloat()
            val target = if (prev.locked) prev.candidateHz else null
            var near = if (target != null) detector.presenceAt(buf, target) else 0f
            if (guard) near = HarmonicPresenceGuard.gate(detector, buf, pitch, target, near)
            val report = ambient.observe(pitch, rms, near)
            if (report.state == ListeningState.PROFILING) detector.learnNoise(buf)
            else if (!noiseFinalized) { detector.finalizeNoise(); noiseFinalized = true }
            prev = report
            out.add((start + window).toDouble() / rate to report)
            start += hop
        }
        return out
    }

    private val quiet = Seg(1.5, emptyList())

    private fun AmbientReport.lockedOn(hz: Double) =
        locked && candidateHz != null && abs(1200 * log2(candidateHz / hz)) < 50

    private fun lastAt(r: List<Pair<Double, AmbientReport>>, t: Double) = r.last { it.first <= t }.second

    @Test
    fun aNoteReplacedByItsMultipleReleasesTheOldLock() {
        for (k in 2..4) {
            val r = run(render(quiet, Seg(2.5, harmonic(220.0)), Seg(4.0, harmonic(220.0 * k))))
            assertTrue("x$k: should be locked on 220 first", lastAt(r, 4.0).lockedOn(220.0))
            // Released within the disturbance ride-out (0.8 s, up to 2x) plus the window.
            val heldUntil = r.filter { it.second.lockedOn(220.0) }.maxOf { it.first }
            assertTrue("x$k: 220 lock held until ${"%.2f".format(heldUntil)} s, switch at 4.0 s",
                heldUntil < 4.0 + 2.2)
            assertTrue("x$k: should end locked on ${220 * k}", r.last().second.lockedOn(220.0 * k))
        }
    }

    @Test
    fun aWeakFundamentalUnderAStrongOctaveKeepsItsLock() {
        // Codex's counterexample: global pitch reads 440 at high clarity and presence at 220
        // is ~1.0 whether or not 220 is there. With 220 continuously present, the lock must
        // survive well past the longest ride-out (1.6 s).
        for (low in listOf(0.025, 0.05, 0.1)) {
            val r = run(render(quiet, Seg(2.5, harmonic(220.0)),
                Seg(6.0, listOf(220.0 to low, 440.0 to 0.5))))
            val after = r.filter { it.first in 4.2..10.0 }
            assertTrue("220 at $low: lock dropped", after.all { it.second.lockedOn(220.0) })
        }
    }

    @Test
    fun bassNotesAboveTheResolutionLimitKeepAWeakFundamentalAndReleaseAReplacedOne() {
        // Codex round 2: the first floor band needed ~60 Hz, so at E1/A1 every partial was
        // skipped, "no evidence" read as "absent", and a weak real fundamental lost its lock
        // to the octave. Both outcomes must hold down to the limit, at both capture rates.
        for (r in listOf(44_100, 48_000)) {
            rate = r
            for (f0 in listOf(41.2, 55.0)) {
                val weak = run(render(quiet, Seg(2.5, harmonic(f0)),
                    Seg(6.0, listOf(f0 to 0.025, 2 * f0 to 0.5))))
                assertTrue("$r Hz, $f0: weak fundamental lost its lock",
                    weak.filter { it.first in 4.2..10.0 }.all { it.second.lockedOn(f0) })
                val gone = run(render(quiet, Seg(2.5, harmonic(f0)), Seg(4.0, harmonic(2 * f0))))
                assertTrue("$r Hz, $f0: replaced note should release", gone.last().second.lockedOn(2 * f0))
            }
        }
    }

    @Test
    fun belowTheResolutionLimitTheOldBehaviourIsKept() {
        // At 30 Hz the spectrum cannot judge (partialEvidence is NaN), so the probe's answer
        // stands: a weak fundamental keeps its lock (and, stated limit, so would a replaced one).
        val r = run(render(quiet, Seg(2.5, harmonic(30.0)),
            Seg(6.0, listOf(30.0 to 0.025, 60.0 to 0.5))))
        assertTrue(r.filter { it.first in 4.2..10.0 }.all { it.second.lockedOn(30.0) })
    }

    @Test
    fun aBriefBurstOfTheOctaveNeitherDropsNorStealsTheLock() {
        val r = run(render(quiet, Seg(2.5, harmonic(220.0)), Seg(0.4, harmonic(440.0)), Seg(2.5, harmonic(220.0))))
        assertTrue(r.last().second.lockedOn(220.0))
        assertFalse("must never lock the burst", r.any { it.second.lockedOn(440.0) })
    }

    @Test
    fun theGuardChangesNothingWhenNoMultipleIsHeard() {
        // A louder unrelated tone over a continuing note, then silence and the note again:
        // the guard must be inert, so the reports match the unguarded pipeline exactly.
        val signal = render(quiet, Seg(2.5, harmonic(220.0, 0.3)),
            Seg(3.0, harmonic(220.0, 0.3) + listOf(311.1 to 0.5)), Seg(0.3, emptyList()),
            Seg(2.0, harmonic(220.0, 0.3)))
        val guarded = run(signal, guard = true).map { it.second.state to it.second.candidateHz }
        val plain = run(signal, guard = false).map { it.second.state to it.second.candidateHz }
        assertEquals(plain, guarded)
    }

    @Test
    fun theOldNoteIsFoundAgainAfterTheMultipleStops() {
        val r = run(render(quiet, Seg(2.5, harmonic(220.0)), Seg(3.0, harmonic(880.0)), Seg(2.5, harmonic(220.0))))
        assertTrue("should lock 880 while it plays", r.any { it.second.lockedOn(880.0) })
        assertTrue(r.last().second.lockedOn(220.0))
    }

    @Test
    fun aRingingNoteUnderItsOctaveIsHeldUntilItDiesAway() {
        // Stated expectation for the overlap case: 220 is still physically sounding as 440
        // starts, so the lock stays on 220 while it rings and moves to 440 once it is gone.
        val ring = Seg(9.0, harmonic(220.0), decay = 0.8)
        val signal = render(quiet, Seg(9.0, emptyList()))
        val a = render(quiet, ring)
        val b = render(quiet, Seg(1.5, emptyList()), Seg(7.5, harmonic(440.0)))
        for (i in signal.indices) signal[i] = a[i] + b[i]
        val r = run(signal)
        assertTrue("should hold 220 just after 440 starts", lastAt(r, 3.6).lockedOn(220.0))
        assertTrue("should end on 440 once 220 has died away", r.last().second.lockedOn(440.0))
    }
}
