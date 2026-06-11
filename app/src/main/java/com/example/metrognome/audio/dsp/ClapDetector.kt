package com.example.metrognome.audio.dsp

import kotlin.math.sqrt

/**
 * Onset detector that separates the metronome **click** from a player **clap**,
 * purpose-built for mic-accuracy scoring.
 *
 * ## Why not reuse [com.example.metrognome.audio.dsp.OnsetDetector] directly
 * That detector applies a single 75 ms refractory to *every* onset. Here the
 * click and the clap can land within a few ms of each other (a player claps on
 * the beat), and we must reject the click yet still time the clap. So detection
 * and refractory are decoupled: every transient is a *candidate*, candidates are
 * classified, and the refractory gates only *accepted claps*. A rejected click
 * therefore never blocks the clap behind it.
 *
 * ## How click and clap are told apart - spectral, not temporal
 * The Classic click is a 1100 Hz sine; a clap is broadband with strong 2-8 kHz
 * energy. Two band-pass followers run continuously:
 *  - a narrow band on the click fundamental (≈1100 Hz),
 *  - a wide band in the clap region (≈5 kHz).
 *
 * At a candidate onset, band energy is integrated over a short [CLASSIFY_HOPS]-hop
 * window and the ratio high/low decides: a sine click is low-band dominant, a clap
 * is high-band dominant. Integrating (rather than judging the onset hop alone) is
 * what keeps the click's broadband *attack* hop from outvoting the steady 1100 Hz
 * tone behind it - a hard-onset sine reads high-band-hot for its first ~6 ms, so a
 * single-hop decision misclassifies it as a clap. This is far more robust than the
 * time-gated click suppression it replaces - and it is the reason mic mode locks
 * the click to Classic (a 9 kHz hi-hat would sit *inside* the clap band and be
 * indistinguishable).
 *
 * Pure DSP over sample indices - deterministic and unit-testable, like the
 * detectors it sits beside.
 */
class ClapDetector(sampleRate: Int) {

    /**
     * A classified transient. [isClap] false = a click that was rejected.
     * [lowRms]/[highRms] are the window-integrated band levels (their ratio drives the
     * integrated decision); [peakRatio] is the largest single-hop high/low seen in the
     * window (the burst test). Both are surfaced for device tuning diagnostics.
     */
    data class Onset(
        val sampleIndex: Long,
        val isClap: Boolean,
        val lowRms: Double,
        val highRms: Double,
        val peakRatio: Double,
    )

    private val onsetBand = BiquadFilter.highPass(sampleRate, ONSET_HP_HZ)
    private val lowBand = BiquadFilter.bandPass(sampleRate, CLICK_BAND_HZ, q = CLICK_BAND_Q)
    private val highBand = BiquadFilter.bandPass(sampleRate, CLAP_BAND_HZ, q = CLAP_BAND_Q)

    private val calibrationHops =
        (CALIBRATION_MS.toLong() * sampleRate / 1000L / HOP_SIZE).toInt().coerceAtLeast(8)
    private val refractoryHops =
        (CLAP_REFRACTORY_MS.toLong() * sampleRate / 1000L / HOP_SIZE).toInt().coerceAtLeast(1)
    private val floorAlpha =
        (HOP_SIZE.toDouble() / (FLOOR_TIME_CONSTANT_MS / 1000.0 * sampleRate)).coerceIn(0.0001, 0.5)

    // Hop accumulation.
    private var onsetAccum = 0.0
    private var lowAccum = 0.0
    private var highAccum = 0.0
    private var hopFill = 0
    private var hopStartIndex = 0L
    private var processedSamples = 0L

    // Detection state.
    private var hopIndex = 0L
    private var lastClapHop = Long.MIN_VALUE / 2
    private var calibrationSeen = 0
    private val calibrationLevels = ArrayList<Double>()
    private var calibrating = true
    private var noiseFloor = MIN_FLOOR
    private var onsetGate = MIN_ONSET_LEVEL
    private var prevOnsetLevel = 0.0

    // Deferred spectral classification: a candidate opens a window over which low/high
    // band energy is integrated before the click-vs-clap call is made (and emitted).
    private var pendingHopsLeft = 0
    private var pendingOnsetIndex = 0L
    private var pendingOnsetHop = 0L
    private var pendingLowEnergy = 0.0
    private var pendingHighEnergy = 0.0
    // Largest single-hop high/low ratio seen in the window. DIAGNOSTIC ONLY: surfaced in
    // the dev report to show that a click's hard-onset attack hop is broadband (high burst
    // ratio) and overlaps a real clap, which is why classification uses the integrated
    // ratio rather than the peak. Not part of the clap/click decision.
    private var pendingPeakRatio = 0.0

    // High-band (clap) tracking, for catching a clap that lands on a click's decaying
    // tail. There the broadband onset level can't "rise" (the click already raised it),
    // but the 5 kHz band still spikes - a click tail is a pure 1100 Hz sine with almost
    // no high-band energy, so a spike there can only be a clap. Calibrated like the onset
    // floor so the spike test is relative to this device's room and gain.
    private var prevHighRms = 0.0
    private var highFloor = MIN_HIGH_FLOOR
    private val calibrationHighLevels = ArrayList<Double>()
    private var clickTailUntilHop = Long.MIN_VALUE / 2

    /**
     * Feed one buffer of mono PCM-16. Returns every classified transient found
     * (clicks included, marked `isClap = false`, so the caller can score rejection).
     */
    fun process(buffer: ShortArray, count: Int): List<Onset> {
        var out: ArrayList<Onset>? = null
        for (i in 0 until count) {
            if (hopFill == 0) hopStartIndex = processedSamples + i

            val x = buffer[i].toFloat()
            val o = onsetBand.process(x)
            val l = lowBand.process(x)
            val h = highBand.process(x)
            onsetAccum += o.toDouble() * o
            lowAccum += l.toDouble() * l
            highAccum += h.toDouble() * h
            hopFill++

            if (hopFill >= HOP_SIZE) {
                val onset = evaluateHop(
                    onsetLevel = sqrt(onsetAccum / HOP_SIZE),
                    lowRms = sqrt(lowAccum / HOP_SIZE),
                    highRms = sqrt(highAccum / HOP_SIZE),
                )
                if (onset != null) (out ?: ArrayList<Onset>(2).also { out = it }).add(onset)
                onsetAccum = 0.0; lowAccum = 0.0; highAccum = 0.0; hopFill = 0
            }
        }
        processedSamples += count
        return out ?: emptyList()
    }

    private fun evaluateHop(onsetLevel: Double, lowRms: Double, highRms: Double): Onset? {
        val thisHop = hopIndex++

        if (calibrating) {
            calibrationLevels.add(onsetLevel)
            calibrationHighLevels.add(highRms)
            if (++calibrationSeen >= calibrationHops) {
                calibrationLevels.sort()
                calibrationHighLevels.sort()
                noiseFloor = calibrationLevels[calibrationLevels.size / 2].coerceAtLeast(MIN_FLOOR)
                onsetGate = maxOf(MIN_ONSET_LEVEL, noiseFloor * ONSET_GATE_FLOOR_RATIO)
                highFloor = calibrationHighLevels[calibrationHighLevels.size / 2].coerceAtLeast(MIN_HIGH_FLOOR)
                calibrating = false
            }
            prevOnsetLevel = onsetLevel
            prevHighRms = highRms
            return null
        }

        // A candidate is mid-classification: keep integrating its band energy across
        // the window, then let finalizePending make (and emit) the decision. New
        // candidates are not opened mid-window - the next few hops belong to this one.
        if (pendingHopsLeft > 0) {
            pendingLowEnergy += lowRms * lowRms
            pendingHighEnergy += highRms * highRms
            pendingPeakRatio = maxOf(pendingPeakRatio, highRms / maxOf(lowRms, MIN_FLOOR))
            prevOnsetLevel = onsetLevel
            prevHighRms = highRms
            return if (--pendingHopsLeft == 0) finalizePending() else null
        }

        if (onsetLevel < noiseFloor * FLOOR_FREEZE_RATIO) {
            noiseFloor = (noiseFloor * (1.0 - floorAlpha) + onsetLevel * floorAlpha)
                .coerceAtLeast(MIN_FLOOR)
        }
        if (highRms < highFloor * FLOOR_FREEZE_RATIO) {
            highFloor = (highFloor * (1.0 - floorAlpha) + highRms * floorAlpha)
                .coerceAtLeast(MIN_HIGH_FLOOR)
        }

        val loud = onsetLevel > noiseFloor * ONSET_LEVEL_RATIO && onsetLevel > onsetGate
        val rising = onsetLevel > maxOf(prevOnsetLevel, MIN_FLOOR) * ONSET_RISE_RATIO

        // Clap-on-click-tail recovery: inside a recently-rejected click's decay the onset
        // band can't rise, but a real clap still spikes the high band. Read that spike as a
        // candidate so the masked clap is not silently lost. Scoped to the click tail so it
        // cannot re-leak isolated clicks (their attack is classified the normal way first).
        val inClickTail = thisHop < clickTailUntilHop
        val highSpike = inClickTail &&
            highRms > highFloor * HIGH_RETRIGGER_RATIO &&
            highRms > maxOf(prevHighRms, MIN_HIGH_FLOOR) * HIGH_RISE_RATIO

        prevOnsetLevel = onsetLevel
        prevHighRms = highRms

        if (!(loud && rising) && !highSpike) return null

        // Candidate transient. Open a classification window starting at this hop and
        // seed it with this hop's band energy; the click-vs-clap call is deferred to
        // finalizePending once CLASSIFY_HOPS have accumulated. The onset *time* stays
        // pinned to this hop's start regardless of when the decision lands.
        pendingOnsetIndex = hopStartIndex
        pendingOnsetHop = thisHop
        pendingLowEnergy = lowRms * lowRms
        pendingHighEnergy = highRms * highRms
        pendingPeakRatio = highRms / maxOf(lowRms, MIN_FLOOR)
        pendingHopsLeft = CLASSIFY_HOPS - 1
        // Normally the decision is deferred to a later hop (pendingHopsLeft > 0). It only
        // lands here under the documented CLASSIFY_HOPS = 1 single-hop tuning, where no
        // further hop follows; with CLASSIFY_HOPS >= 2 this is a no-op. Suppress the
        // "always false" inspection so that 1-hop knob keeps working.
        @Suppress("KotlinConstantConditions")
        val classifyNow = pendingHopsLeft == 0
        return if (classifyNow) finalizePending() else null
    }

    /**
     * Decide click vs clap from band energy integrated across the classification
     * window, and emit the transient at its original onset time. A rejected click is
     * still reported (for rejection-rate scoring) but does NOT arm the clap
     * refractory, so a clap just behind it can still score.
     */
    private fun finalizePending(): Onset? {
        val low = sqrt(pendingLowEnergy)
        val high = sqrt(pendingHighEnergy)
        // Classify on the window-integrated ratio ALONE. A single-hop burst rescue was tried
        // for the on-beat clap, but real-device reports showed a click's hard-onset attack
        // hop is itself broadband (burst ratio up to ~9), overlapping a real clap's burst -
        // so peak ratio cannot discriminate. The integrated ratio separates cleanly because
        // the click's sustained 1100 Hz tone collapses its ratio (~0.4) while a clap stays
        // high (~5-8); the margin is wide and device-portable. peakRatio is diagnostic only.
        val isClap = high >= low * CLAP_BAND_RATIO

        if (!isClap) {
            // A rejected click opens a tail window: a clap masked by this click's decay
            // can't rise on the onset band, so the high-band re-trigger covers it there.
            clickTailUntilHop = pendingOnsetHop + CLICK_TAIL_HOPS
            return Onset(pendingOnsetIndex, isClap = false, lowRms = low, highRms = high, peakRatio = pendingPeakRatio)
        }
        if (pendingOnsetHop - lastClapHop < refractoryHops) return null
        lastClapHop = pendingOnsetHop
        return Onset(pendingOnsetIndex, isClap = true, lowRms = low, highRms = high, peakRatio = pendingPeakRatio)
    }

    companion object {
        private const val HOP_SIZE = 256              // ~5.8 ms at 44.1 kHz

        private const val ONSET_HP_HZ = 1500f         // onset-detection band (matches OnsetDetector)
        private const val CLICK_BAND_HZ = 1100f       // Classic click fundamental
        private const val CLICK_BAND_Q = 4.0f         // narrow - isolate the sine
        private const val CLAP_BAND_HZ = 5000f        // centre of the clap's energy
        private const val CLAP_BAND_Q = 0.707f        // wide - capture the broadband burst

        /** Integrated high/low band ratio above which a transient is a clap, not a click. Public for tuning diagnostics. */
        const val CLAP_BAND_RATIO = 1.5
        /**
         * Hops of band energy integrated before classifying (≈3 × 5.8 ms ≈ 17 ms).
         * Long enough to dilute the click's broadband attack hop with the steady tone
         * behind it, short enough not to swallow a clap landing ~20 ms later. Set to 1
         * to recover the original single-hop behaviour.
         */
        private const val CLASSIFY_HOPS = 3

        private const val CALIBRATION_MS = 400
        private const val CLAP_REFRACTORY_MS = 75
        private const val FLOOR_TIME_CONSTANT_MS = 2000.0

        private const val ONSET_LEVEL_RATIO = 3.0
        private const val ONSET_RISE_RATIO = 2.0
        private const val FLOOR_FREEZE_RATIO = 2.0
        private const val MIN_FLOOR = 35.0
        private const val MIN_ONSET_LEVEL = 220.0
        private const val ONSET_GATE_FLOOR_RATIO = 4.0

        // ── Clap-on-click-tail recovery (high band) ──
        private const val MIN_HIGH_FLOOR = 20.0
        /** High-band level (vs its learned floor) that marks a real broadband burst. */
        private const val HIGH_RETRIGGER_RATIO = 4.0
        /** ...and it must be rising versus the previous hop to count as an onset. */
        private const val HIGH_RISE_RATIO = 2.0
        /** Hops after a rejected click during which a high-band spike reads as a masked clap (≈40 ms). */
        private const val CLICK_TAIL_HOPS = 7
    }
}
