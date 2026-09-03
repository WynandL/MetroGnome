package com.example.metrognome.audio.drone

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns a [VoiceLayout] into an endless stereo signal, one block at a time.
 *
 * Pure Kotlin: no Android, no threads, no clock. [DroneEngine] owns the AudioTrack and
 * calls [render] in a loop; everything that decides what the tone *sounds* like happens
 * here, which is what makes it testable against the app's own [PitchDetector] on the JVM.
 *
 * The four things that separate this from "play a sine in a loop", all of which exist
 * because the alternative is audible:
 *
 *  1. **Nothing restarts.** There is no loop point and no buffer being replayed. Every
 *     oscillator is a phase accumulator running in double precision, so the tone is
 *     mathematically continuous for as long as it sounds. A looped buffer, however long,
 *     puts a periodic seam in a signal whose entire job is to be steady.
 *  2. **Every change is a ramp.** Starting and stopping are raised-cosine envelopes;
 *     a new note glides; the level follows a one-pole; a new timbre or blend crossfades
 *     into the old one. Nothing in the audio path ever steps, so nothing ever clicks.
 *  3. **The pitch stays exact through all of it.** Glide is interpolated in the log-frequency
 *     domain (musically linear, so a glide across an octave is even), and the centre strand
 *     of every voice is undetuned and unmoving, so the tone remains a reference even while
 *     the texture around it breathes.
 *  4. **Phases start decorrelated.** All oscillators starting at phase zero would align at
 *     the attack and produce a peak equal to the sum of every amplitude, which is where a
 *     synth like this normally clips. Seeded random start phases make the sum behave like
 *     the incoherent sum the normalisation assumes, and keep the run deterministic.
 *
 * Not thread-safe. All calls, including the setters, must come from the thread that calls
 * [render] (the engine marshals the UI's requests across at block boundaries).
 */
class DroneRenderer(
    private val sampleRate: Int,
    seed: Long = DEFAULT_SEED,
) {
    private val random = Random(seed)
    private val nyquistGuard = sampleRate * 0.45

    private var current: Live? = null
    private var outgoing: Live? = null

    /** 0 while the outgoing voice is still at full level, 1 once the new one has replaced it. */
    private var crossfade = 1.0

    private var logHz = ln(220.0)
    private var targetLogHz = logHz

    private var gateOpen = false

    /** Linear 0..1 envelope position; [render] shapes it with a raised cosine. */
    private var envelope = 0.0

    private var smoothedVolume = 0f
    private var targetVolume = 1f

    private var levelCurrent = FloatArray(0)
    private var levelOutgoing = FloatArray(0)

    /** True once a closed gate has fully faded out, so the engine knows it may tear down. */
    val silent: Boolean get() = !gateOpen && envelope <= 0.0

    /** True while the tone is audible or on its way there. */
    val sounding: Boolean get() = gateOpen || envelope > 0.0

    /**
     * Target frequency of the note. Set while sounding, the tone glides; set while silent,
     * it takes effect immediately, so starting a new note never swoops up from the last one.
     */
    fun setFrequency(hz: Double) {
        if (hz <= 0.0) return
        targetLogHz = ln(hz)
        if (!sounding) logHz = targetLogHz
    }

    /** User level, 0..1. Followed by a one-pole so dragging the slider cannot zipper. */
    fun setVolume(volume: Float) {
        targetVolume = volume.coerceIn(0f, 1f)
        if (!sounding) smoothedVolume = targetVolume
    }

    /**
     * Swap in a new timbre or blend. While sounding, the old voice is kept alive and the
     * two are crossfaded; while silent, it simply replaces the old one.
     *
     * A second change during a crossfade drops the already-fading voice rather than
     * stacking a third. The alternative is an unbounded chain of voices for a user who
     * taps quickly through the chips, and by that point the oldest is inaudible anyway.
     */
    fun setVoice(layout: VoiceLayout) {
        val compiled = Live(layout, random)
        if (!sounding || current == null) {
            current = compiled
            outgoing = null
            crossfade = 1.0
            return
        }
        outgoing = current
        current = compiled
        crossfade = 0.0
    }

    /** Begin (or resume) sounding. Silent until [setVoice] has supplied a voice. */
    fun open() {
        if (!sounding) {
            logHz = targetLogHz
            smoothedVolume = targetVolume
        }
        gateOpen = true
    }

    /** Begin fading out. [silent] turns true once the release has finished. */
    fun close() {
        gateOpen = false
    }

    /**
     * Fill [frames] of [left] and [right] with the next block of the tone.
     *
     * The arrays are overwritten, not accumulated into, and must be at least [frames] long.
     */
    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        java.util.Arrays.fill(left, 0, frames, 0f)
        java.util.Arrays.fill(right, 0, frames, 0f)

        val cur = current ?: return
        if (!sounding) return

        advanceGlide(frames)
        val baseHz = exp(logHz)
        cur.tune(baseHz, frames, sampleRate, nyquistGuard)
        outgoing?.tune(baseHz, frames, sampleRate, nyquistGuard)

        ensureScratch(frames)
        buildLevels(frames, cur.normalisation, outgoing?.normalisation ?: 0f)

        addVoice(cur, left, right, frames, levelCurrent)
        outgoing?.let { addVoice(it, left, right, frames, levelOutgoing) }

        if (crossfade >= 1.0) outgoing = null
    }

    // ── Per-sample control signals ───────────────────────────────────────────────

    /**
     * Precompute the gain applied to each voice at each sample: the shaped envelope, the
     * smoothed user level, the voice's own normalisation, and the crossfade weight.
     *
     * The crossfade is **linear**, not the usual equal-power square root. Equal power is
     * the right law for two unrelated signals, whose powers add; these two share a
     * fundamental and are strongly correlated, so their amplitudes add instead and a
     * square-root law would bulge audibly in the middle of every timbre change.
     */
    private fun buildLevels(frames: Int, normCurrent: Float, normOutgoing: Float) {
        val attackStep = 1.0 / (ATTACK_MS / 1000.0 * sampleRate)
        val releaseStep = 1.0 / (RELEASE_MS / 1000.0 * sampleRate)
        val fadeStep = 1.0 / (CROSSFADE_MS / 1000.0 * sampleRate)
        val volumeCoefficient = 1.0f - exp(-1.0f / (VOLUME_TAU_S * sampleRate))

        for (i in 0 until frames) {
            envelope = if (gateOpen) {
                (envelope + attackStep).coerceAtMost(1.0)
            } else {
                (envelope - releaseStep).coerceAtLeast(0.0)
            }
            // Raised cosine: zero slope at both ends, so neither the attack nor the release
            // has a corner for the ear to hear as a click.
            val shaped = 0.5 - 0.5 * cos(PI * envelope)

            smoothedVolume += (targetVolume - smoothedVolume) * volumeCoefficient
            val level = (shaped * smoothedVolume).toFloat()

            if (crossfade < 1.0) crossfade = (crossfade + fadeStep).coerceAtMost(1.0)
            levelCurrent[i] = level * normCurrent * crossfade.toFloat()
            levelOutgoing[i] = level * normOutgoing * (1.0 - crossfade).toFloat()
        }
    }

    /**
     * Move the sounding frequency one block closer to the target, interpolating in the
     * log domain so the glide is even in pitch rather than in Hz.
     */
    private fun advanceGlide(frames: Int) {
        if (logHz == targetLogHz) return
        val k = 1.0 - exp(-frames.toDouble() / (GLIDE_TAU_S * sampleRate))
        logHz += (targetLogHz - logHz) * k
        // Snap once inside a thousandth of a cent, so a target that is never quite reached
        // does not leave the oscillators re-tuning by imperceptible amounts forever.
        if (abs(targetLogHz - logHz) < 1e-8) logHz = targetLogHz
    }

    private fun ensureScratch(frames: Int) {
        if (levelCurrent.size < frames) {
            levelCurrent = FloatArray(frames)
            levelOutgoing = FloatArray(frames)
        }
    }

    // ── Oscillator bank ──────────────────────────────────────────────────────────

    /**
     * Sum one voice into the output.
     *
     * Partial-outer, sample-inner: each partial's phase and increment stay in registers for
     * the whole block, and the only memory the inner loop touches repeatedly is the sine
     * table and the three float arrays, all of which stay in cache.
     */
    private fun addVoice(
        voice: Live,
        left: FloatArray,
        right: FloatArray,
        frames: Int,
        level: FloatArray,
    ) {
        for (strand in voice.strands) {
            for (p in strand.increments.indices) {
                if (!strand.voiced[p]) continue
                var phase = strand.phases[p]
                val increment = strand.increments[p]
                val ampL = strand.gainL * strand.amplitudes[p]
                val ampR = strand.gainR * strand.amplitudes[p]
                for (i in 0 until frames) {
                    val sample = sine(phase) * level[i]
                    left[i] += sample * ampL
                    right[i] += sample * ampR
                    phase += increment
                    if (phase >= 1.0) phase -= 1.0
                }
                strand.phases[p] = phase
            }
        }
    }

    /** A compiled voice: flat arrays of oscillator state, allocated once per voice change. */
    private class Live(layout: VoiceLayout, random: Random) {
        val normalisation = layout.normalisation
        val strands = Array(layout.strands.size) { LiveStrand(layout.strands[it], random) }

        fun tune(baseHz: Double, frames: Int, sampleRate: Int, nyquistGuard: Double) {
            for (strand in strands) strand.tune(baseHz, frames, sampleRate, nyquistGuard)
        }
    }

    private class LiveStrand(spec: VoiceStrand, random: Random) {
        private val ratios = DoubleArray(spec.partials.size) { spec.partials[it].ratio }
        private val detuneCents = spec.detuneCents
        private val moveCents = spec.moveCents
        private val moveRateHz = spec.moveRateHz
        private val frequencyRatio = spec.frequencyRatio

        val amplitudes = FloatArray(spec.partials.size) { spec.partials[it].amp.toFloat() }
        val increments = DoubleArray(spec.partials.size)
        val voiced = BooleanArray(spec.partials.size)
        val gainL = spec.gainL
        val gainR = spec.gainR
        val phases = DoubleArray(spec.partials.size) { random.nextDouble() }

        /** Where this strand's slow detune breathing has got to, in cycles. */
        private var movePhase = random.nextDouble()

        fun tune(baseHz: Double, frames: Int, sampleRate: Int, nyquistGuard: Double) {
            if (moveRateHz > 0.0) {
                movePhase += moveRateHz * frames / sampleRate
                if (movePhase >= 1.0) movePhase -= movePhase.toInt().toDouble()
            }
            val cents = detuneCents + moveCents * sin(2.0 * PI * movePhase)
            val strandHz = baseHz * frequencyRatio * centsRatio(cents)
            for (p in ratios.indices) {
                val partialHz = strandHz * ratios[p]
                // Above the guard a partial would fold back down the spectrum as an
                // inharmonic tone, which on a sustained drone is unmistakable. Drop it
                // instead; the ones that reach the guard are the weakest in the series.
                voiced[p] = partialHz > 0.0 && partialHz < nyquistGuard
                increments[p] = partialHz / sampleRate
            }
        }
    }

    companion object {
        /** Fixed so a run is reproducible; the point is decorrelation, not randomness. */
        private const val DEFAULT_SEED = 0x51DE5EEDL

        private const val ATTACK_MS = 280.0
        private const val RELEASE_MS = 340.0
        private const val CROSSFADE_MS = 200.0

        /** Time constant of the note glide, in seconds. */
        private const val GLIDE_TAU_S = 0.09

        /** Time constant of the level smoother, in seconds. */
        private const val VOLUME_TAU_S = 0.04f

        /**
         * Sine lookup table with one guard entry past the end, so the interpolation of the
         * last cell needs no wrap test in the inner loop. 4096 points with linear
         * interpolation puts the error around 100 dB below the tone, well under the noise
         * floor of any phone speaker, for a fraction of the cost of a sin() per sample.
         */
        private const val TABLE_SIZE = 4096
        private val TABLE = FloatArray(TABLE_SIZE + 1) {
            sin(2.0 * PI * it / TABLE_SIZE).toFloat()
        }

        /** Interpolated sine of a phase in cycles, 0..1. */
        private fun sine(phase: Double): Float {
            val x = phase * TABLE_SIZE
            val index = x.toInt()
            val fraction = (x - index).toFloat()
            val a = TABLE[index]
            return a + (TABLE[index + 1] - a) * fraction
        }
    }
}
