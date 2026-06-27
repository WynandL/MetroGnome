package com.example.metrognome.audio.dsp

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Onset detector that separates the metronome **click** from a player **clap**,
 * purpose-built for mic-accuracy scoring.
 *
 * ## Why a dedicated detector, not a plain amplitude onset detector
 * A plain amplitude onset detector applies a single 75 ms refractory to *every*
 * onset. Here the click and the clap can land within a few ms of each other (a
 * player claps on the beat), and we must reject the click yet still time the clap.
 * So detection and refractory are decoupled: every transient is a *candidate*,
 * candidates are classified, and the refractory gates only *accepted claps*. A
 * rejected click therefore never blocks the clap behind it.
 *
 * ## How click and clap are told apart - spectral, not temporal
 * The Classic click is a 1100 Hz sine and its downbeat accent is an 1800 Hz sine;
 * a clap is broadband with strong 2-8 kHz energy. Three band-pass followers run
 * continuously:
 *  - a narrowband on the click fundamental (≈1100 Hz),
 *  - a narrowband on the accent fundamental (≈1800 Hz),
 *  - a wideband in the clap region (≈5 kHz).
 *
 * At a candidate onset, band energy is integrated over a short [CLASSIFY_HOPS]-hop
 * window and the high band is compared against the *louder* of the two tonal bands:
 * a transient is a clap only if the clap band dominates BOTH the click and the
 * accent fundamental. A 1100 Hz click loads the click band, an 1800 Hz accent loads
 * the accent band, and either way the tonal energy collapses the ratio so it is
 * rejected; only a genuinely broadband clap clears the high band over both narrow
 * tonal bands at once. The accent band is what lets the metronome keep its normal,
 * audibly distinct 1800 Hz downbeat in mic mode: the detector filters that accent
 * out *narrowly* rather than forcing the engine to reshape it.
 *
 * Integrating (rather than judging the onset hop alone) is what keeps a click's
 * broadband *attack* hop from outvoting the steady tone behind it - a hard-onset
 * sine reads high-band-hot for its first ~6 ms, so a single-hop decision misclassifies
 * it as a clap. This is far more robust than the time-gated click suppression it
 * replaces - and it is the reason mic mode locks the click to Classic (a 9 kHz hi-hat
 * would sit *inside* the clap band and be indistinguishable).
 *
 * ## Why the high/tonal ratio is not enough on its own - spectral flatness
 * The ratio test leans on the clap band being *wide*: a bright clap floods 2-8 kHz and
 * clears the narrow tonal bands. But a real **cupped** hand clap has a resonant hump near
 * ~1 kHz and weak highs (Repp 1987's P1 clap), so its energy lands in the *click* band and
 * the high band stays quiet - the ratio collapses and the clap is wrongly rejected as a click.
 * That was a real field bug (finger snaps, which are sharp and HF-rich, scored fine; claps did
 * not). So a second, independent signal runs alongside: **spectral flatness** over a log-spaced
 * [FLATNESS_BAND_HZ] bank. A clap spreads energy across the bank (flat), a click/accent dump it
 * into one band (peaky), and flatness separates them on a feature that does *not* collide with the
 * clap's own 1 kHz hump. A transient is a clap if it is flat enough OR clap-band dominant; a pure
 * click/accent fails both, so neither path re-leaks it.
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
        val accentRms: Double = 0.0,
        /**
         * Spectral flatness of the transient across [FLATNESS_BAND_HZ] (geometric mean /
         * arithmetic mean of the band energies, 0..1). A tonal click/accent concentrates its
         * energy in one band → low flatness; a broadband clap spreads it → high flatness. This
         * is the feature that accepts a clap regardless of where its resonant peak sits, so a
         * cupped clap (≈1 kHz hump, weak highs) is no longer mistaken for the 1100 Hz click.
         */
        val flatness: Double = 0.0,
    )

    private val onsetBand = BiquadFilter.highPass(sampleRate, ONSET_HP_HZ)
    private val lowBand = BiquadFilter.bandPass(sampleRate, CLICK_BAND_HZ, q = CLICK_BAND_Q)
    private val accentBand = BiquadFilter.bandPass(sampleRate, ACCENT_BAND_HZ, q = ACCENT_BAND_Q)
    private val highBand = BiquadFilter.bandPass(sampleRate, CLAP_BAND_HZ, q = CLAP_BAND_Q)

    // Spectral-flatness band bank. A clap is broadband — its energy spreads across the bank — while
    // the click and accent are tonal, loading one band. Flatness over this bank separates them on a
    // feature that does NOT collide with a clap's own ~1 kHz resonance the way the click band does.
    // Roughly log-spaced and contiguous (each band uniform 0 dB peak gain, so levels are comparable).
    private val flatnessBands = FLATNESS_BAND_HZ.map { BiquadFilter.bandPass(sampleRate, it, q = FLATNESS_BAND_Q) }
    private val flatHopAccum = DoubleArray(FLATNESS_BAND_HZ.size)
    private val flatHopRms = DoubleArray(FLATNESS_BAND_HZ.size)
    private val pendingFlatEnergy = DoubleArray(FLATNESS_BAND_HZ.size)

    private val calibrationHops =
        (CALIBRATION_MS.toLong() * sampleRate / 1000L / HOP_SIZE).toInt().coerceAtLeast(8)
    private val refractoryHops =
        (CLAP_REFRACTORY_MS.toLong() * sampleRate / 1000L / HOP_SIZE).toInt().coerceAtLeast(1)
    private val floorAlpha =
        (HOP_SIZE.toDouble() / (FLOOR_TIME_CONSTANT_MS / 1000.0 * sampleRate)).coerceIn(0.0001, 0.5)

    // Hop accumulation.
    private var onsetAccum = 0.0
    private var lowAccum = 0.0
    private var accentAccum = 0.0
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
    private var pendingAccentEnergy = 0.0
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
            val a = accentBand.process(x)
            val h = highBand.process(x)
            onsetAccum += o.toDouble() * o
            lowAccum += l.toDouble() * l
            accentAccum += a.toDouble() * a
            highAccum += h.toDouble() * h
            for (b in flatnessBands.indices) {
                val f = flatnessBands[b].process(x)
                flatHopAccum[b] += f.toDouble() * f
            }
            hopFill++

            if (hopFill >= HOP_SIZE) {
                for (b in flatHopRms.indices) {
                    flatHopRms[b] = sqrt(flatHopAccum[b] / HOP_SIZE)
                    flatHopAccum[b] = 0.0
                }
                val onset = evaluateHop(
                    onsetLevel = sqrt(onsetAccum / HOP_SIZE),
                    lowRms = sqrt(lowAccum / HOP_SIZE),
                    accentRms = sqrt(accentAccum / HOP_SIZE),
                    highRms = sqrt(highAccum / HOP_SIZE),
                    flatBandRms = flatHopRms,
                )
                if (onset != null) (out ?: ArrayList<Onset>(2).also { out = it }).add(onset)
                onsetAccum = 0.0; lowAccum = 0.0; accentAccum = 0.0; highAccum = 0.0; hopFill = 0
            }
        }
        processedSamples += count
        return out ?: emptyList()
    }

    private fun evaluateHop(
        onsetLevel: Double,
        lowRms: Double,
        accentRms: Double,
        highRms: Double,
        flatBandRms: DoubleArray,
    ): Onset? {
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
            pendingAccentEnergy += accentRms * accentRms
            pendingHighEnergy += highRms * highRms
            for (b in pendingFlatEnergy.indices) pendingFlatEnergy[b] += flatBandRms[b] * flatBandRms[b]
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
        pendingAccentEnergy = accentRms * accentRms
        pendingHighEnergy = highRms * highRms
        for (b in pendingFlatEnergy.indices) pendingFlatEnergy[b] = flatBandRms[b] * flatBandRms[b]
        pendingPeakRatio = highRms / maxOf(lowRms, MIN_FLOOR)
        pendingHopsLeft = CLASSIFY_HOPS - 1
        // Normally the decision is deferred to a later hop (pendingHopsLeft > 0). It only lands
        // here under the documented CLASSIFY_HOPS = 1 single-hop tuning, where no further hop
        // follows; with CLASSIFY_HOPS >= 2 the condition is always false, hence the @Suppress on
        // this statement so the 1-hop knob keeps working without an "always false" inspection.
        @Suppress("KotlinConstantConditions")
        return if (pendingHopsLeft == 0) finalizePending() else null
    }

    /**
     * Decide click vs clap from band energy integrated across the classification
     * window, and emit the transient at its original onset time. A rejected click is
     * still reported (for rejection-rate scoring) but does NOT arm the clap
     * refractory, so a clap just behind it can still score.
     */
    private fun finalizePending(): Onset? {
        val low = sqrt(pendingLowEnergy)
        val accent = sqrt(pendingAccentEnergy)
        val high = sqrt(pendingHighEnergy)
        // Classify on the window-integrated ratio ALONE. A single-hop burst rescue was tried
        // for the on-beat clap, but real-device reports showed a click's hard-onset attack
        // hop is itself broadband (burst ratio up to ~9), overlapping a real clap's burst -
        // so peak ratio cannot discriminate. The integrated ratio separates cleanly because
        // a tonal click collapses its ratio while a clap stays high; the margin is wide and
        // device-portable. peakRatio is diagnostic only.
        //
        // The high band must beat the LOUDER of the two tonal bands: the 1100 Hz click loads
        // `low`, the 1800 Hz downbeat accent loads `accent`, and either alone is enough tonal
        // energy to reject the transient. This is the narrow accent filter - it lets the engine
        // keep its normal, audibly distinct 1800 Hz accent instead of reshaping it: the pure
        // accent sine sits in `accent` (off the wide clap band's centre), so high never clears
        // it, while a broadband clap dwarfs both narrow bands at once.
        val tonal = maxOf(low, accent)

        // Spectral flatness over the band bank: geometric mean / arithmetic mean of the band
        // energies. A tonal click/accent dumps its energy into one band → flatness collapses
        // toward 0; a clap spreads energy across the bank → flatness stays high even when its
        // loudest band is the ~1 kHz hump that overlaps the click. This is the discriminator
        // that the high/tonal ratio cannot be: that ratio fails a cupped clap because its hump
        // loads `low` and its highs are weak, but flatness still reads it as broadband.
        val flatness = spectralFlatness()

        // A clap is broadband (flat) OR clap-band dominant. The legacy ratio still catches a
        // bright, HF-rich clap; flatness adds the cupped/low-hump clap the ratio misses. A pure
        // click/accent fails BOTH (low flatness, low ratio), so neither path re-leaks it.
        val isClap = flatness >= CLAP_FLATNESS_MIN || high >= tonal * CLAP_BAND_RATIO

        if (!isClap) {
            // A rejected click/accent opens a tail window: a clap masked by its decay
            // can't rise on the onset band, so the high-band re-trigger covers it there.
            clickTailUntilHop = pendingOnsetHop + CLICK_TAIL_HOPS
            return Onset(pendingOnsetIndex, isClap = false, lowRms = low, highRms = high, peakRatio = pendingPeakRatio, accentRms = accent, flatness = flatness)
        }
        if (pendingOnsetHop - lastClapHop < refractoryHops) return null
        lastClapHop = pendingOnsetHop
        return Onset(pendingOnsetIndex, isClap = true, lowRms = low, highRms = high, peakRatio = pendingPeakRatio, accentRms = accent, flatness = flatness)
    }

    /**
     * Spectral flatness of the pending transient: geometric mean / arithmetic mean of the
     * window-integrated band energies, in (0, 1]. ~1 for an ideally flat (broadband) spectrum,
     * → 0 as energy concentrates into a single band (a pure tone). A small per-band floor keeps
     * the geometric mean finite and stops a single near-silent band from forcing flatness to 0.
     */
    private fun spectralFlatness(): Double {
        val n = pendingFlatEnergy.size
        var logSum = 0.0
        var linSum = 0.0
        for (e in pendingFlatEnergy) {
            val v = sqrt(e).coerceAtLeast(FLATNESS_FLOOR)
            logSum += ln(v)
            linSum += v
        }
        return exp(logSum / n) / (linSum / n)
    }

    companion object {
        private const val HOP_SIZE = 256              // ~5.8 ms at 44.1 kHz

        private const val ONSET_HP_HZ = 1500f         // onset-detection high-pass band
        private const val CLICK_BAND_HZ = 1100f       // Classic click fundamental
        private const val CLICK_BAND_Q = 4.0f         // narrow - isolate the sine
        private const val ACCENT_BAND_HZ = 1800f      // Classic downbeat-accent fundamental
        private const val ACCENT_BAND_Q = 4.0f        // narrow - isolate the accent sine
        private const val CLAP_BAND_HZ = 5000f        // centre of the clap's energy
        private const val CLAP_BAND_Q = 0.707f        // wide - capture the broadband burst

        /** Integrated high/low band ratio above which a transient is a clap, not a click. Public for tuning diagnostics. */
        const val CLAP_BAND_RATIO = 1.5

        // ── Spectral-flatness clap detection ──
        /**
         * Band-bank centres (Hz) for the flatness measure, roughly log-spaced and spanning the
         * clap's energy range. The tonal voices live in single bands (1100 Hz click ≈ the 1050
         * band, 1800 Hz accent between 1050/2700); a clap lights several at once.
         */
        private val FLATNESS_BAND_HZ = floatArrayOf(650f, 1050f, 1700f, 2700f, 4400f, 7000f)
        /**
         * Narrow enough that a pure tone collapses into a single band (so the click/accent read as
         * decisively tonal); the bands deliberately sample the spectrum rather than tile it, which
         * is all flatness needs. Too broad and a tone leaks into neighbours and reads as "flat".
         */
        private const val FLATNESS_BAND_Q = 6.0f
        /**
         * Flatness at or above which a transient is broadband enough to be a clap. Tuned against
         * synthesized signals (see ClapDetectorTest.printSpectraForTuning): the tonal voices read
         * ≈0.51 (1100 Hz click) / ≈0.60 (1800 Hz accent), cupped and bright claps read ≈0.92-0.93,
         * so this midpoint clears both with ~0.18 margin each side. The on-device recorder + Mic
         * Timing Log surface this value per-onset so it can be refined against real hardware.
         * Public for tuning diagnostics.
         */
        const val CLAP_FLATNESS_MIN = 0.78
        /** Per-band RMS floor (PCM units) keeping the geometric mean finite. Negligible vs a real transient. */
        private const val FLATNESS_FLOOR = 1.0
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
