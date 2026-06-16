package com.example.metrognome.debug.tuner

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton capture buffer for raw tuner readings during a known-truth accuracy test.
 *
 * Workflow: turn [recording] on from the dev tools, play the generated test-tone file
 * (tools/tuner_test_tones) into the mic, then open the reading-log viewer. Because each
 * note in that file is played flat / in-tune / sharp, the MEAN cents reported for a note
 * approximates the detector+calibration bias, and min/max approximate +/- the offset.
 * Run once uncalibrated and once calibrated to see the bias move toward zero.
 *
 * Written from the [com.example.metrognome.audio.tuner.Tuner] capture thread, read from
 * the Compose main thread; [MutableStateFlow] is thread-safe by construction. The capture
 * is gated by [recording] (default off), so it costs nothing until a developer enables it.
 *
 * Debug-only. Delete this file, its dev call site, and the Tuner hook to remove entirely.
 */
object TunerReadingLog {

    private const val MAX_SAMPLES = 6000

    /**
     * One settled reading.
     *
     * @property elapsedMs  ms since the recording session started
     * @property note       detected note + octave, e.g. "A4"
     * @property frequencyHz displayed (calibrated, smoothed) frequency
     * @property cents       signed offset from the nearest note
     * @property clarity     detector confidence 0..1
     * @property calFactor   calibration factor in force when the sample was taken (1 = none)
     * @property ambient     learned room band at the time (QUIET/MODERATE/NOISY)
     */
    data class Sample(
        val elapsedMs: Long,
        val note: String,
        val frequencyHz: Float,
        val cents: Float,
        val clarity: Float,
        val calFactor: Float,
        val ambient: String,
    )

    /** Aggregate over all samples of one detected note. */
    data class NoteStats(
        val note: String,
        val count: Int,
        val meanCents: Float,
        val minCents: Float,
        val maxCents: Float,
        val meanHz: Float,
        val meanClarity: Float,
    ) {
        /**
         * Whether [meanCents] can be trusted as a detector/calibration bias.
         *
         * The mean only reflects bias when the played offsets straddled zero. A thin
         * sample, or a one-sided sweep (e.g. a soft low note where only the sharp-played
         * moments survived the gate), forces a nonzero mean no matter how accurate the
         * detector is — so we flag it rather than colour it like a real bias.
         */
        val reliable: Boolean
            get() = count >= MIN_RELIABLE_SAMPLES &&
                    minCents <= -COVERAGE_CENTS && maxCents >= COVERAGE_CENTS

        companion object {
            /** Below this many samples a note's mean is too thin to trust. */
            const val MIN_RELIABLE_SAMPLES = 30
            /** The sweep must reach at least this far flat AND sharp to straddle zero. */
            const val COVERAGE_CENTS = 5f
        }
    }

    @Volatile
    var recording: Boolean = false
        private set

    private val _samples = MutableStateFlow<List<Sample>>(emptyList())
    val samples: StateFlow<List<Sample>> = _samples.asStateFlow()

    private var sessionStartMs = 0L

    /** Begin a fresh recording session (clears prior samples). */
    fun start() {
        _samples.value = emptyList()
        sessionStartMs = SystemClock.elapsedRealtime()
        recording = true
    }

    fun stop() {
        recording = false
    }

    fun clear() {
        _samples.value = emptyList()
    }

    /** Record one reading. No-op unless [recording]. Called from the capture thread. */
    fun record(
        note: String,
        frequencyHz: Float,
        cents: Float,
        clarity: Float,
        calFactor: Float,
        ambient: String,
    ) {
        if (!recording) return
        val s = Sample(
            elapsedMs = SystemClock.elapsedRealtime() - sessionStartMs,
            note = note,
            frequencyHz = frequencyHz,
            cents = cents,
            clarity = clarity,
            calFactor = calFactor,
            ambient = ambient,
        )
        val current = _samples.value
        _samples.value =
            if (current.size >= MAX_SAMPLES) current else current + s
    }

    /** Per-note aggregates, sorted low to high by mean frequency. */
    fun statsByNote(list: List<Sample> = _samples.value): List<NoteStats> =
        list.groupBy { it.note }
            .map { (note, rows) ->
                val cents = rows.map { it.cents }
                NoteStats(
                    note = note,
                    count = rows.size,
                    meanCents = cents.average().toFloat(),
                    minCents = cents.min(),
                    maxCents = cents.max(),
                    meanHz = rows.map { it.frequencyHz }.average().toFloat(),
                    meanClarity = rows.map { it.clarity }.average().toFloat(),
                )
            }
            .sortedBy { it.meanHz }
}
