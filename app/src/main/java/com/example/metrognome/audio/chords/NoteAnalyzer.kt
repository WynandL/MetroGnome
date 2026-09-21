package com.example.metrognome.audio.chords

import com.example.metrognome.audio.dsp.OnsetDetector
import com.example.metrognome.audio.dsp.PitchDetector
import kotlin.math.sqrt

/**
 * The onset engine's whole analysis chain, samples in and [NoteTracker.Observation]s out,
 * with no Android in it: [NoteCapture] wraps it around a microphone, and the JVM tests
 * run it over buffers rendered by [ChordVoice].
 *
 * Every [HOP] samples (11.6 ms at 44.1 kHz) it runs three things over a ring of recent
 * audio:
 *
 *  - [OnsetDetector] over the last [ONSET_WINDOW] samples: short, for time resolution;
 *  - [PitchDetector] (MPM) over the last [PITCH_WINDOW] samples: long enough for a low
 *    E's period several times over, short enough that a fresh note fills it quickly.
 *    The tuner uses twice this for cent-level accuracy on low strings; naming a note
 *    needs the nearest semitone, and the shorter window is most of why this engine
 *    answers in a fraction of the tuner's time;
 *  - on each onset, the magnitude spectrum of the window that ended just *before* it is
 *    kept as a reference, and for the tracker's evidence span the pitch is also read
 *    from the residual above that reference ([PitchDetector.detectAbove]), which is how
 *    a note is heard on its own while the previous one still rings. An onset whose
 *    residual holds almost no power ([NEW_POWER_MIN]) is not passed to the tracker at
 *    all: the rise brought nothing new, and the residual's pitch would be the ghost of
 *    what was already sounding.
 *
 * A one-pole DC blocker runs on the way in, as in the tuner.
 */
class NoteAnalyzer(val sampleRate: Int) {

    companion object {
        const val HOP = 512
        const val PITCH_WINDOW = 4096
        const val ONSET_WINDOW = 2048

        /** Ring length: the pitch window plus enough history to reach back before an onset. */
        private const val RING = 8192

        /**
         * How many hops before the *reported* onset the reference window ends. The report
         * lags the flux peak by one frame and the flux compares against the frame two hops
         * earlier, so four hops back is the last window wholly before the rise.
         */
        private const val REFERENCE_BACK_HOPS = 4

        private const val DC_BLOCK_R = 0.999f

        /**
         * An onset counts only if at least this fraction of the window's spectral power is
         * left once the pre-onset spectrum is subtracted. The subtraction floor alone
         * leaves about 1%; a note plucked 13 dB under a ringing chord leaves 5%. Measured
         * 2026-09-21 on a phone at high volume: the limiter re-triggers the onset detector
         * a few hundred ms into every sustained sound, and on a ringing chord that onset
         * decided the chord's missing fundamental (D#2 under C E G, G1 under G B D) from
         * the residual's ghost, loud and clear.
         */
        private const val NEW_POWER_MIN = 0.05f
    }

    /** Wall-clock length of one hop, in milliseconds. */
    val hopMillis: Double = HOP * 1000.0 / sampleRate

    /** Everything one hop computed, for a diagnostic log: the inputs the tracker saw and what it made of them. */
    data class Trace(
        val hop: Long,
        val rms: Float,
        val flux: Float,
        val onset: Boolean,
        val pitch: PitchDetector.Pitch?,
        val residual: PitchDetector.Pitch?,
        val observation: NoteTracker.Observation,
        /** On a detector onset: the fraction of power the pre-onset spectrum did not explain; NaN otherwise. */
        val newFraction: Float,
    )

    /** Called once per hop from the feeding thread when set. Null (the default) costs nothing. */
    @Volatile var trace: ((Trace) -> Unit)? = null

    private val onsets = OnsetDetector(sampleRate, ONSET_WINDOW, HOP)
    private val pitch = PitchDetector(sampleRate, PITCH_WINDOW)
    val tracker = NoteTracker(hopMillis)

    private val ring = FloatArray(RING)
    private var ringPos = 0          // next write index; the oldest sample sits here too
    private var filled = 0
    private var sinceHop = 0
    private var hopIndex = 0L

    private val onsetWindow = FloatArray(ONSET_WINDOW)
    private val pitchWindow = FloatArray(PITCH_WINDOW)
    private val referenceWindow = FloatArray(PITCH_WINDOW)
    private val reference = FloatArray(pitch.spectrumSize)
    private var residualUntil = -1L   // hop index up to which a residual reading is taken

    private var dcX1 = 0f
    private var dcY1 = 0f

    /** Start over, as after a capture restart. Keeps nothing. */
    fun reset() {
        ring.fill(0f)
        ringPos = 0; filled = 0; sinceHop = 0; hopIndex = 0
        residualUntil = -1
        dcX1 = 0f; dcY1 = 0f
        onsets.reset()
        tracker.forget()
    }

    /**
     * Push [count] samples of [samples] (normalised to about +-1) and call [sink] once per
     * completed hop, in order.
     */
    fun feed(samples: FloatArray, count: Int, sink: (NoteTracker.Observation) -> Unit) {
        for (i in 0 until count) {
            val x = samples[i]
            val y = x - dcX1 + DC_BLOCK_R * dcY1
            dcX1 = x
            dcY1 = y
            ring[ringPos] = y
            ringPos = (ringPos + 1) % RING
            if (filled < RING) filled++
            if (++sinceHop < HOP) continue
            sinceHop = 0
            if (filled < PITCH_WINDOW) continue
            sink(analyseHop())
        }
    }

    private fun analyseHop(): NoteTracker.Observation {
        copyLast(onsetWindow, endHopsBack = 0)
        copyLast(pitchWindow, endHopsBack = 0)

        val onset = onsets.process(onsetWindow)
        val plain = pitch.detect(pitchWindow)

        var isNew = onset.onset
        var newFraction = Float.NaN
        if (onset.onset && filled >= PITCH_WINDOW + REFERENCE_BACK_HOPS * HOP) {
            copyLast(referenceWindow, endHopsBack = REFERENCE_BACK_HOPS)
            pitch.magnitudeSpectrum(referenceWindow, reference)
            // Is anything actually new? A limiter or the player's hand can produce a rise
            // with nothing new in it; the reference then explains the whole window, and the
            // residual is only the floor's ghost of what was already ringing.
            pitch.detectAbove(pitchWindow, reference)
            newFraction = pitch.lastResidualPowerFraction
            isNew = newFraction >= NEW_POWER_MIN
            if (isNew) residualUntil = hopIndex + tracker.evidenceSpanFrames
        }
        val residual = if (hopIndex <= residualUntil) pitch.detectAbove(pitchWindow, reference) else null

        var sum = 0.0
        for (v in pitchWindow) sum += v.toDouble() * v
        val rms = sqrt(sum / PITCH_WINDOW).toFloat()

        val hop = hopIndex++
        val observation = tracker.observe(NoteTracker.Frame(isNew, plain, residual, rms))
        trace?.invoke(Trace(hop, rms, onset.flux, onset.onset, plain, residual, observation, newFraction))
        return observation
    }

    /** Copy the [out].size samples that ended [endHopsBack] hops ago, oldest first. */
    private fun copyLast(out: FloatArray, endHopsBack: Int) {
        val start = ringPos - endHopsBack * HOP - out.size
        for (i in out.indices) out[i] = ring[Math.floorMod(start + i, RING)]
    }
}
