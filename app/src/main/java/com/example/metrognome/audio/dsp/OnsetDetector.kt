package com.example.metrognome.audio.dsp

import kotlin.math.sqrt

/**
 * Transient onset detector for hand claps and other percussive sounds.
 *
 * Implements the standard onset-detection chain, tuned for a rhythm game:
 *
 *   1. **High-pass filter** — a clap is broadband and snappy; the human voice
 *      and room rumble are concentrated low. Filtering first lets steps 3–4
 *      reject talking and background noise far better than raw wideband level.
 *   2. **Per-hop RMS** — the signal is measured in short ~6 ms hops, giving
 *      fine onset-time resolution (the old whole-buffer approach quantised to
 *      40–80 ms).
 *   3. **Adaptive noise floor** — a slow exponential average that tracks the
 *      ambient level, updated *only* on quiet hops so a clap can never drag it
 *      up, and so a clap landing during calibration cannot poison it for good.
 *   4. **Peak-pick** — a hop is an onset when it is both *loud* (well above the
 *      adaptive floor and an absolute gate) AND a *sharp rise* (a multiple of
 *      the previous hop), and a refractory gap has elapsed since the last one.
 *
 * The class is pure DSP — it deals only in sample indices, never wall-clock
 * time — so it is deterministic and unit-testable. [process] returns the
 * absolute sample index of each detected onset; the caller maps indices to
 * time using an accurate audio clock.
 *
 * All thresholds are deliberately named constants — tune them against real
 * device recordings rather than guessing.
 */
class OnsetDetector(sampleRate: Int) {

    companion object {
        /** Hop length in samples — ~5.8 ms at 44.1 kHz. Smaller = finer timing. */
        private const val HOP_SIZE = 256

        /** Claps keep plenty of energy above this; voice/rumble mostly sit below. */
        private const val HIGH_PASS_HZ = 1500f

        /** Ambient profiling window before detection begins. */
        private const val CALIBRATION_MS = 500

        /** Minimum gap between two reported onsets (debounces one clap's tail). */
        private const val REFRACTORY_MS = 75

        /** Noise-floor adaptation speed — larger = slower to follow ambient drift. */
        private const val FLOOR_TIME_CONSTANT_MS = 2000.0

        /** Onset must exceed the adaptive floor by at least this factor. */
        private const val ONSET_LEVEL_RATIO = 3.0

        /** Onset must exceed the *previous hop* by at least this factor (sharp rise). */
        private const val ONSET_RISE_RATIO = 2.0

        /** Above this multiple of the floor a hop is "loud" — don't adapt the floor. */
        private const val FLOOR_FREEZE_RATIO = 2.0

        /** The floor never collapses below this, so dead silence can't over-sensitise. */
        private const val MIN_FLOOR = 35.0

        /**
         * Absolute loudness floor (post-filter RMS) for the onset gate. The gate
         * applied live is the larger of this and a multiple of the ambient level
         * measured at calibration (see [ONSET_GATE_FLOOR_RATIO]), so it adapts to
         * the device's mic gain instead of assuming one fixed level. This bound
         * only binds on quiet, low-gain mics, where genuine claps are themselves
         * faint.
         */
        private const val MIN_ONSET_LEVEL = 220.0

        /**
         * The onset gate is also held to at least this multiple of the ambient
         * level measured during calibration. On a hot-gain mic — where ambient
         * speech alone can clear a fixed gate — this raises the bar so only a
         * genuinely loud clap scores.
         */
        private const val ONSET_GATE_FLOOR_RATIO = 4.0
    }

    private val highPass = BiquadFilter.highPass(sampleRate, HIGH_PASS_HZ)

    private val calibrationHops =
        (CALIBRATION_MS.toLong() * sampleRate / 1000L / HOP_SIZE).toInt().coerceAtLeast(8)
    private val refractoryHops =
        (REFRACTORY_MS.toLong() * sampleRate / 1000L / HOP_SIZE).toInt().coerceAtLeast(1)
    private val floorAlpha =
        (HOP_SIZE.toDouble() / (FLOOR_TIME_CONSTANT_MS / 1000.0 * sampleRate)).coerceIn(0.0001, 0.5)

    // Hop accumulation state.
    private var hopAccum = 0.0
    private var hopFill = 0
    private var hopStartIndex = 0L      // absolute sample index of the current hop's first sample
    private var processedSamples = 0L  // total samples fed since construction / reset

    // Detection state.
    private var hopIndex = 0L
    private var lastOnsetHop = Long.MIN_VALUE / 2
    private var calibrationHopsSeen = 0
    private val calibrationLevels = ArrayList<Double>()
    private var calibrating = true
    private var noiseFloor = MIN_FLOOR
    /** Absolute onset gate, finalised from the ambient level at end of calibration. */
    private var onsetLevelGate = MIN_ONSET_LEVEL
    private var prevLevel = 0.0

    /**
     * Feed one buffer of mono PCM-16 samples.
     *
     * @return the absolute sample index of every onset found in this buffer
     *         (usually empty). Index 0 is the first sample ever fed.
     */
    fun process(buffer: ShortArray, count: Int): LongArray {
        var onsets: ArrayList<Long>? = null

        for (i in 0 until count) {
            if (hopFill == 0) hopStartIndex = processedSamples + i

            val filtered = highPass.process(buffer[i].toFloat())
            hopAccum += filtered.toDouble() * filtered.toDouble()
            hopFill++

            if (hopFill >= HOP_SIZE) {
                val onsetIndex = evaluateHop(sqrt(hopAccum / HOP_SIZE))
                if (onsetIndex >= 0L) {
                    (onsets ?: ArrayList<Long>(2).also { onsets = it }).add(onsetIndex)
                }
                hopAccum = 0.0
                hopFill = 0
            }
        }

        processedSamples += count
        return onsets?.toLongArray() ?: EMPTY
    }

    /** Evaluate one completed hop. Returns its onset sample index, or -1 if none. */
    private fun evaluateHop(level: Double): Long {
        val thisHop = hopIndex++

        // ── Calibration: profile the ambient floor, detect nothing yet ──────────
        if (calibrating) {
            calibrationLevels.add(level)
            if (++calibrationHopsSeen >= calibrationHops) {
                calibrationLevels.sort()
                // Median is robust: a stray loud hop during calibration can't skew it.
                noiseFloor = calibrationLevels[calibrationLevels.size / 2].coerceAtLeast(MIN_FLOOR)
                // Lift the absolute gate to suit this device's mic gain: on a hot
                // mic the calibrated ambient is high, so the gate rises with it.
                onsetLevelGate = maxOf(MIN_ONSET_LEVEL, noiseFloor * ONSET_GATE_FLOOR_RATIO)
                calibrating = false
            }
            prevLevel = level
            return -1L
        }

        // ── Adapt the floor on quiet hops only — claps never raise it ───────────
        if (level < noiseFloor * FLOOR_FREEZE_RATIO) {
            noiseFloor = (noiseFloor * (1.0 - floorAlpha) + level * floorAlpha)
                .coerceAtLeast(MIN_FLOOR)
        }

        // ── Peak-pick: loud AND a sharp rise AND past the refractory gap ────────
        val loud = level > noiseFloor * ONSET_LEVEL_RATIO && level > onsetLevelGate
        val rising = level > maxOf(prevLevel, MIN_FLOOR) * ONSET_RISE_RATIO
        val pastRefractory = thisHop - lastOnsetHop >= refractoryHops

        prevLevel = level

        return if (loud && rising && pastRefractory) {
            lastOnsetHop = thisHop
            hopStartIndex   // localised to the hop's first sample (~6 ms resolution)
        } else {
            -1L
        }
    }
}

private val EMPTY = LongArray(0)
