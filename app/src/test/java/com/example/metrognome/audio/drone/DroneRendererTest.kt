package com.example.metrognome.audio.drone

import com.example.metrognome.audio.dsp.PitchDetector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DroneRenderer], run against the app's own [PitchDetector].
 *
 * The renderer is pure Kotlin precisely so this is possible: its output is fed to the same
 * MPM detector the tuner runs on a live microphone, and the detector is asked what note it
 * hears. A drone the app's own tuner disagrees with would be worse than no drone at all,
 * and this is the only way to find that out without a phone in the room.
 *
 * Everything here is deterministic. The renderer's start phases come from a fixed seed, so
 * a measured peak or RMS means the same thing on every run.
 */
class DroneRendererTest {

    // ── Pitch accuracy ──────────────────────────────────────────────────────────

    /**
     * The oscillator itself, measured exactly rather than detected.
     *
     * [measure] compares the phase of the tone across two halves of a two-second capture,
     * which pins the frequency to a millionth of a Hz and so leaves nowhere for a wavetable
     * rounding error, a phase-accumulator drift or an off-by-one in the increment to hide.
     * A hundredth of a cent is roughly a thousand times finer than the best ear.
     */
    @Test
    fun theOscillatorIsExactAcrossTheWholeRange() {
        listOf(65.41, 110.0, 220.0, 440.0, 987.77).forEach { hz ->
            val renderer = sounding(DroneTimbre.PURE, DroneBlend.ROOT, hz)
            val measured = measure(capture(renderer, RATE * 2).mono(), hz)
            assertEquals("the oscillator drifted at $hz Hz", 0.0, cents(measured, hz), 0.01)
        }
    }

    /**
     * The same check through the app's own tuner, on every timbre.
     *
     * The bar here is five cents rather than a hundredth of one, and deliberately so: this
     * measures [PitchDetector]'s *uncalibrated* accuracy as much as the drone's, and a
     * couple of cents of detector bias is exactly what the tuner's calibration exists to
     * remove. What it proves is the part calibration cannot fix: that the harmonics and the
     * detuned strands do not pull the perceived note off its fundamental.
     */
    @Test
    fun theAppsOwnTunerAgreesWithEveryTimbre() {
        DroneTimbre.entries.forEach { timbre ->
            val renderer = sounding(timbre, DroneBlend.ROOT, hz = 196.0)
            val detected = detect(capture(renderer, WINDOW).mono())
            assertEquals("${timbre.name} moved the note", 0.0, cents(detected, 196.0), 5.0)
        }
    }

    /**
     * Both tones of a blend are exactly where they should be, and the interval between
     * them is the just ratio rather than the tempered one.
     *
     * A blend is deliberately not asked to survive [PitchDetector]: root plus a 3:2 fifth
     * repeats at half the root's frequency, so any period-based detector will correctly
     * report the octave below. That is the missing fundamental the ear hears too, and it is
     * a property of the interval rather than a fault in the tone, which is why each
     * component is measured on its own frequency instead.
     */
    @Test
    fun blendsSoundBothTonesExactlyAndAtJustRatios() {
        DroneBlend.entries.forEach { blend ->
            val samples = capture(sounding(DroneTimbre.PURE, blend, 196.0), RATE * 2).mono()
            blend.tones.forEach { tone ->
                val expected = 196.0 * tone.ratio
                assertEquals(
                    "${blend.name} is off at ${tone.ratio}x",
                    0.0, cents(measure(samples, expected), expected), 0.01,
                )
            }
        }
    }

    @Test
    fun theReferencePitchCarriesStraightThrough() {
        // A3 with A4 anchored at 442 Hz is 221 Hz, and that is what should come out.
        val hz = DroneState(midi = 57).frequencyHz(442f).toDouble()
        val renderer = sounding(DroneTimbre.PURE, DroneBlend.ROOT, hz = hz)
        assertEquals(0.0, cents(measure(capture(renderer, RATE * 2).mono(), 221.0), 221.0), 0.01)
    }

    // ── Nothing clicks ──────────────────────────────────────────────────────────

    /**
     * A click is a discontinuity, so look for one directly.
     *
     * The bar is the steepest sample-to-sample step either voice takes while simply
     * sitting there sounding: a transition that stays under that cannot contain a jump the
     * steady tone does not already contain. Every transition the panel can ask for is put
     * through it, because these are the four moments a synth like this normally clicks.
     */
    @Test
    fun noTransitionProducesADiscontinuity() {
        val from = buildVoice(DroneTimbre.WARM, DroneBlend.ROOT)
        val to = buildVoice(DroneTimbre.REED, DroneBlend.FIFTH)
        val steadiest = maxOf(
            largestStep(capture(sounding(DroneTimbre.WARM, DroneBlend.ROOT, 220.0), RATE)),
            largestStep(capture(sounding(DroneTimbre.REED, DroneBlend.FIFTH, 330.0), RATE)),
        )

        val renderer = DroneRenderer(RATE)
        renderer.setVoice(from)
        renderer.setFrequency(220.0)
        renderer.setVolume(1f)

        renderer.open()
        val attack = capture(renderer, RATE / 2)                 // start from silence
        renderer.setFrequency(330.0)
        val glide = capture(renderer, RATE / 2)                  // slide to another note
        renderer.setVoice(to)
        val swap = capture(renderer, RATE / 2)                   // crossfade to another voice
        renderer.close()
        val release = capture(renderer, RATE / 2)                // fade out

        listOf("attack" to attack, "glide" to glide, "voice change" to swap, "release" to release)
            .forEach { (name, samples) ->
                val step = largestStep(samples)
                assertTrue(
                    "the $name stepped by $step, past the steady tone's own $steadiest",
                    step <= steadiest * 1.05f,
                )
            }
    }

    @Test
    fun theAttackStartsFromSilenceAndTheReleaseReturnsToIt() {
        val renderer = DroneRenderer(RATE)
        renderer.setVoice(buildVoice(DroneTimbre.REED, DroneBlend.FIFTH))
        renderer.setFrequency(146.83)
        renderer.setVolume(1f)

        assertTrue("nothing should sound before the gate opens", renderer.silent)
        assertEquals(0f, peak(capture(renderer, BLOCK).left), 0f)

        renderer.open()
        assertTrue("the attack must ease in, not step up", peak(capture(renderer, 64).left) < 0.02f)

        capture(renderer, RATE / 2)
        renderer.close()
        // The release is 340 ms; give it 500 ms to finish in.
        val tail = capture(renderer, RATE / 2)
        assertTrue("the release never reached silence", renderer.silent)
        assertTrue("the tail did not end quiet", peak(tail.left.tail(64)) < 1e-4f)
    }

    @Test
    fun reopeningDuringTheReleaseSwellsBackInsteadOfRestarting() {
        val renderer = sounding(DroneTimbre.WARM, DroneBlend.ROOT, 220.0)
        renderer.close()
        capture(renderer, BLOCK * 4)   // part-way through the fade
        assertFalse("the fade should still be running", renderer.silent)

        renderer.open()
        assertTrue("re-opening should bring the tone back", rms(capture(renderer, RATE / 2).left) > 0.1f)
    }

    // ── Level ───────────────────────────────────────────────────────────────────

    /**
     * Every timbre and blend arrives at the same loudness, and none reaches full scale.
     * Measured over four seconds so the strands have time to beat in and out of phase with
     * each other, which is when a voice would clip if it were ever going to.
     */
    @Test
    fun noVoiceClipsAndEveryVoiceLandsOnTheSameLevel() {
        DroneTimbre.entries.forEach { timbre ->
            DroneBlend.entries.forEach { blend ->
                val samples = capture(sounding(timbre, blend, 220.0), RATE * 4)
                val loudest = maxOf(peak(samples.left), peak(samples.right))
                assertTrue("$timbre/$blend peaked at $loudest", loudest < 1.0f)
                assertEquals(
                    "$timbre/$blend is off the target level in the left channel",
                    TARGET_RMS.toFloat(), rms(samples.left), 0.02f,
                )
                assertEquals(
                    "$timbre/$blend is off the target level in the right channel",
                    TARGET_RMS.toFloat(), rms(samples.right), 0.02f,
                )
            }
        }
    }

    @Test
    fun theLevelControlScalesTheOutput() {
        val loud = sounding(DroneTimbre.WARM, DroneBlend.ROOT, 220.0, volume = 1f)
        val quiet = sounding(DroneTimbre.WARM, DroneBlend.ROOT, 220.0, volume = 0.5f)
        assertEquals(
            rms(capture(loud, WINDOW).left) / 2f,
            rms(capture(quiet, WINDOW).left),
            0.01f,
        )
    }

    // ── Stereo ──────────────────────────────────────────────────────────────────

    @Test
    fun pureIsCentredAndTheLayeredVoicesAreWide() {
        val pure = capture(sounding(DroneTimbre.PURE, DroneBlend.ROOT, 220.0), BLOCK)
        for (i in 0 until BLOCK) {
            assertEquals("a single centred strand must sit dead centre", pure.left[i], pure.right[i], 1e-7f)
        }

        val warm = capture(sounding(DroneTimbre.WARM, DroneBlend.ROOT, 220.0), BLOCK)
        val spread = (0 until BLOCK).maxOf { abs(warm.left[it] - warm.right[it]) }
        assertTrue("layered voices should spread across the stereo field", spread > 0.01f)
    }

    // ── Glide ───────────────────────────────────────────────────────────────────

    @Test
    fun aNoteChangeGlidesAndSettlesOnTheNewNote() {
        val renderer = sounding(DroneTimbre.PURE, DroneBlend.ROOT, 220.0)
        renderer.setFrequency(440.0)

        // The glide is exponential, so "finished" means settled rather than arrived: two
        // seconds in it is inside a billionth of a cent, and the note must then be as exact
        // as one that was never glided to.
        capture(renderer, RATE * 2)
        assertEquals(0.0, cents(measure(capture(renderer, RATE * 2).mono(), 440.0), 440.0), 0.01)
    }

    @Test
    fun aNoteChangedWhileSilentTakesEffectWithoutGliding() {
        val renderer = DroneRenderer(RATE)
        renderer.setVoice(buildVoice(DroneTimbre.PURE, DroneBlend.ROOT))
        renderer.setVolume(1f)
        renderer.setFrequency(220.0)
        renderer.setFrequency(440.0)   // still silent, so this simply becomes the note
        renderer.open()
        capture(renderer, RATE / 2)
        assertEquals(0.0, cents(measure(capture(renderer, RATE * 2).mono(), 440.0), 440.0), 0.01)
    }

    // ── Aliasing ────────────────────────────────────────────────────────────────

    /**
     * A partial past Nyquist must be dropped, not folded back down the spectrum. Forced
     * here with a 16 kHz renderer, where the octave blend's upper partials genuinely run
     * out of room; a folded partial would land inharmonically and pull the detected pitch
     * off the note.
     */
    @Test
    fun partialsPastNyquistAreDroppedRatherThanFoldedBack() {
        val renderer = sounding(DroneTimbre.REED, DroneBlend.OCTAVE, 880.0, rate = NARROW_RATE)
        val detected = detect(capture(renderer, WINDOW).mono(), NARROW_RATE)
        assertEquals("an aliased partial pulled the pitch off", 0.0, cents(detected, 880.0), 5.0)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** A rendered stretch of stereo audio. */
    private class Capture(val left: FloatArray, val right: FloatArray) {
        fun mono() = FloatArray(left.size) { (left[it] + right[it]) / 2f }
    }

    /** A renderer already at full level on [hz], with the attack finished. */
    private fun sounding(
        timbre: DroneTimbre,
        blend: DroneBlend,
        hz: Double,
        volume: Float = 1f,
        rate: Int = RATE,
    ): DroneRenderer {
        val renderer = DroneRenderer(rate)
        renderer.setVoice(buildVoice(timbre, blend))
        renderer.setFrequency(hz)
        renderer.setVolume(volume)
        renderer.open()
        capture(renderer, rate / 2)   // the attack is 280 ms; half a second clears it
        return renderer
    }

    private fun capture(renderer: DroneRenderer, frames: Int): Capture {
        val chunkL = FloatArray(BLOCK)
        val chunkR = FloatArray(BLOCK)
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        var written = 0
        while (written < frames) {
            val count = minOf(BLOCK, frames - written)
            renderer.render(chunkL, chunkR, count)
            System.arraycopy(chunkL, 0, left, written, count)
            System.arraycopy(chunkR, 0, right, written, count)
            written += count
        }
        return Capture(left, right)
    }

    private fun detect(samples: FloatArray, rate: Int = RATE): Double {
        val detector = PitchDetector(rate, WINDOW)
        val pitch = detector.detect(samples.copyOfRange(samples.size - WINDOW, samples.size))
        assertNotNull("the detector heard no pitch at all", pitch)
        return pitch!!.frequency.toDouble()
    }

    /**
     * The exact frequency of the component near [targetHz], from how far its phase has
     * advanced between the first and second half of [samples].
     *
     * Correlating against a reference oscillator anchored to absolute sample index means a
     * signal exactly at [targetHz] shows no phase difference at all, and anything else
     * shows one proportional to the error. Unambiguous within half a bin of the half-window
     * length, which for a two-second capture is a quarter of a Hz: thousands of times
     * wider than the errors being looked for.
     */
    private fun measure(samples: FloatArray, targetHz: Double, rate: Int = RATE): Double {
        val half = samples.size / 2
        var difference = phaseAt(samples, half, half, targetHz, rate) -
            phaseAt(samples, 0, half, targetHz, rate)
        while (difference > PI) difference -= 2.0 * PI
        while (difference <= -PI) difference += 2.0 * PI
        return targetHz + difference / (2.0 * PI) * rate / half
    }

    private fun phaseAt(samples: FloatArray, offset: Int, count: Int, hz: Double, rate: Int): Double {
        var real = 0.0
        var imaginary = 0.0
        for (i in 0 until count) {
            val angle = 2.0 * PI * hz * (offset + i) / rate
            real += samples[offset + i] * cos(angle)
            imaginary -= samples[offset + i] * sin(angle)
        }
        return atan2(imaginary, real)
    }

    private fun cents(a: Double, b: Double) = 1200.0 * log2(a / b)

    private fun peak(samples: FloatArray) = samples.maxOf { abs(it) }

    private fun largestStep(capture: Capture): Float = largestStep(capture.left)

    private fun largestStep(samples: FloatArray): Float {
        var largest = 0f
        for (i in 1 until samples.size) largest = maxOf(largest, abs(samples[i] - samples[i - 1]))
        return largest
    }

    private fun rms(samples: FloatArray): Float {
        var sum = 0.0
        samples.forEach { sum += it.toDouble() * it }
        return sqrt(sum / samples.size).toFloat()
    }

    private fun FloatArray.tail(count: Int) = copyOfRange(size - count, size)

    private companion object {
        const val RATE = 44_100

        /** Low enough that the upper partials of a high note genuinely run past Nyquist. */
        const val NARROW_RATE = 16_000

        const val BLOCK = 512

        /** Analysis window for [PitchDetector]; must be a power of two. */
        const val WINDOW = 4096
    }
}
