package com.example.metrognome.audio

import com.example.metrognome.audio.dsp.PitchDetector
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.log2

/** What the tuner currently thinks the microphone is hearing. */
enum class ListeningState {
    /** Measuring the room's baseline at start-up. */
    PROFILING,

    /** Nothing meaningful above the learned background. */
    QUIET,

    /** Sound is present, but no clear musical tone — movement, broadband noise. */
    NOISE,

    /** A pitch is present but won't hold still — almost always a voice. */
    UNSTABLE,

    /** A steady tone has appeared and is being confirmed. */
    ACQUIRING,

    /** A steady, sustained tone is confirmed — this is treated as the instrument. */
    LOCKED,
}

/** Coarse classification of the learned background level. */
enum class AmbientLevel { QUIET, MODERATE, NOISY }

/**
 * A single live snapshot of what the tuner hears and what it is doing about it.
 *
 * @property state          the current decision
 * @property headline       short status, e.g. "Locked on A4"
 * @property guidance       one line of plain-language advice / explanation
 * @property ambientLevel   how loud the learned background is
 * @property candidateHz    the pitch currently under consideration, if any
 * @property stabilityCents spread of recent pitches in cents; NaN if not applicable
 * @property locked         true only when [state] is [ListeningState.LOCKED]
 * @property ambientBins    [BIN_COUNT] normalized (0..1) frequency-activity values,
 *                          log-spaced from [BIN_FREQ_LO] to [BIN_FREQ_HI] Hz.
 *                          Populated during profiling; frozen after it completes.
 */
data class AmbientReport(
    val state: ListeningState,
    val headline: String,
    val guidance: String,
    val ambientLevel: AmbientLevel,
    val candidateHz: Float?,
    val stabilityCents: Float,
    val locked: Boolean,
    val ambientBins: FloatArray = FloatArray(BIN_COUNT),
) {
    companion object {
        /** Number of log-frequency bins in [ambientBins]. */
        const val BIN_COUNT = 24
        /** Low end of the histogram frequency axis (Hz). */
        const val BIN_FREQ_LO = 80f
        /** High end of the histogram frequency axis (Hz). */
        const val BIN_FREQ_HI = 3500f

        /** Neutral report for when the tuner is not listening. */
        val Idle = AmbientReport(
            state = ListeningState.QUIET,
            headline = "Getting ready…",
            guidance = "",
            ambientLevel = AmbientLevel.QUIET,
            candidateHz = null,
            stabilityCents = Float.NaN,
            locked = false,
            ambientBins = FloatArray(BIN_COUNT),
        )
    }

    // FloatArray breaks data-class structural equality — override manually.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AmbientReport) return false
        return state == other.state && headline == other.headline &&
                guidance == other.guidance && ambientLevel == other.ambientLevel &&
                candidateHz == other.candidateHz && stabilityCents == other.stabilityCents &&
                locked == other.locked && ambientBins.contentEquals(other.ambientBins)
    }

    override fun hashCode(): Int {
        var r = state.hashCode()
        r = 31 * r + headline.hashCode()
        r = 31 * r + ambientLevel.hashCode()
        r = 31 * r + (candidateHz?.hashCode() ?: 0)
        r = 31 * r + stabilityCents.hashCode()
        r = 31 * r + locked.hashCode()
        r = 31 * r + ambientBins.contentHashCode()
        return r
    }
}

/**
 * Smart ambient/environment analyzer — the gate between raw pitch detection and
 * the tuner needle.
 *
 * ## Why this exists
 * A pitch detector reports a frequency for *any* periodic sound: a voice, a
 * humming fan, a passing car all have pitch. Feeding that straight to the
 * needle makes it chase every noise in the room. This class decides, frame by
 * frame, whether what's heard is a genuine instrument note worth showing.
 *
 * ## How it decides — no machine learning, just temporal reasoning
 *  1. **Profile the room.** For the first [PROFILE_MS] it measures the
 *     background level, and notes any steady tone already present (a mains hum,
 *     a fan) so that tone can later be ignored.
 *  2. **Gate on level.** A frame must stand clearly above the learned
 *     background to be worth analysing at all.
 *  3. **Acquire on stability.** A musical note holds one pitch; a voice glides
 *     and jumps constantly. Only when recent pitches sit inside a tight
 *     [ACQUIRE_CENTS] band for [ACQUIRE_MS] does the analyzer *lock*.
 *  4. **Hold with hysteresis.** Once locked it tracks loosely — a slow tuning
 *     glide stays locked, brief detection gaps are ridden out — but a sudden
 *     jump or a real silence drops the lock. Tight to acquire, loose to hold:
 *     the needle is steady yet still responsive.
 *
 * Pure logic — it consumes detection results, never raw audio — so it is
 * deterministic and unit-testable. One instance per capture session; not
 * thread-safe.
 *
 * @param hopMillis wall-clock duration of one analysis frame, used to convert
 *        the millisecond constants below into frame counts.
 */
class AmbientDetector(hopMillis: Double) {

    companion object {
        /** Room-profiling duration at start-up. */
        private const val PROFILE_MS = 900.0

        /** How long a tone must stay steady before the analyzer locks. */
        private const val ACQUIRE_MS = 320.0

        /** Span of recent pitch history examined for stability. */
        private const val STABILITY_WINDOW_MS = 560.0

        /** Detection gap (no clear tone) ridden out before a lock is dropped. */
        private const val HOLD_GAP_MS = 360.0

        /**
         * How long a loud wrong-pitch signal (speech, noise) is tolerated before the lock
         * is dropped. Longer than [HOLD_GAP_MS] because speech is typically short and the
         * instrument note is still physically present underneath it.
         */
        private const val HOLD_DISTURB_MS = 800.0

        /**
         * After losing lock, if the same note returns within this window it is re-locked
         * immediately (no full [ACQUIRE_MS] cycle). Handles "spoke briefly, still playing".
         */
        private const val REACQUIRE_MS = 1500.0

        /** To lock: every recent pitch must sit within this many cents of the others. */
        private const val ACQUIRE_CENTS = 18f

        /** Once locked: the most a pitch may move between frames before the lock breaks. */
        private const val HOLD_STEP_CENTS = 80f

        /** Recent tonal frames needed before stability is even assessed. */
        private const val MIN_RECENT = 3

        /** A frame's pitch counts as a real tone only above this detector clarity. */
        private const val CLARITY_MIN = 0.85f

        /** A frame is "active" only when its level exceeds the background by this factor. */
        private const val LEVEL_MARGIN = 2.0f

        /** Speed at which the background-level estimate tracks the room. */
        private const val FLOOR_ADAPT = 0.05f

        /** The background estimate never collapses below this (normalised RMS). */
        private const val MIN_FLOOR = 0.0012f

        /** Learned background above these → MODERATE / NOISY. */
        private const val MODERATE_FLOOR = 0.010f
        private const val NOISY_FLOOR = 0.030f

        /** A tone within this many cents of the learned room hum is treated as the hum. */
        private const val HUM_REJECT_CENTS = 70f
        /** …but only while it stays below the background by less than this factor. */
        private const val HUM_LEVEL_MARGIN = 3.0f
        /** Fraction of profiling frames that must be tonal to declare a room hum. */
        private const val HUM_PRESENCE = 0.6
        /** …and those tonal frames must themselves agree within this many cents. */
        private const val HUM_SPREAD_CENTS = 60f
    }

    private val profileFrames    = framesFor(PROFILE_MS, hopMillis)
    private val acquireFrames    = framesFor(ACQUIRE_MS, hopMillis).coerceAtLeast(2)
    private val historyFrames    = framesFor(STABILITY_WINDOW_MS, hopMillis).coerceAtLeast(MIN_RECENT)
    private val holdGapFrames    = framesFor(HOLD_GAP_MS, hopMillis).coerceAtLeast(1)
    private val holdDisturbFrames = framesFor(HOLD_DISTURB_MS, hopMillis).coerceAtLeast(1)
    private val reacquireFrames  = framesFor(REACQUIRE_MS, hopMillis).coerceAtLeast(1)

    // ── Learned room model ──────────────────────────────────────────────────────
    private var profiling = true
    private val profileLevels = ArrayList<Float>()
    private val profilePitches = ArrayList<Float>()
    private var ambientFloor = MIN_FLOOR
    private var humHz = 0f   // 0 = no steady room tone learned

    // ── Ambient frequency histogram ─────────────────────────────────────────────
    private val binAccum = FloatArray(AmbientReport.BIN_COUNT)  // raw counts during profiling
    private val binNorm  = FloatArray(AmbientReport.BIN_COUNT)  // normalized 0..1 (frozen post-profiling)

    // ── Rolling detection state ─────────────────────────────────────────────────
    private val pitchRing = ArrayDeque<Float?>()
    private var steadyRun = 0
    private var gapRun = 0
    private var disturbRun = 0      // frames of active-but-wrong-pitch while engaged
    private var engaged = false
    private var lockedHz = 0f

    // ── Fast re-acquisition ──────────────────────────────────────────────────────
    /** Hz of the most recently lost lock; 0 = none pending. */
    private var recentlyLockedHz = 0f
    /** Frames elapsed since lock was lost — expires the re-acquire window. */
    private var recentLockAge = 0

    /**
     * Feed one frame of analysis. Call once per detector window.
     *
     * @param pitch    the [PitchDetector] result for this window, or null
     * @param levelRms RMS level of the same window (normalised, ≈0..1)
     * @return what the analyzer now believes about the environment
     */
    fun observe(pitch: PitchDetector.Pitch?, levelRms: Float): AmbientReport {
        val rawTonal = pitch != null && pitch.clarity >= CLARITY_MIN
        val rawHz = pitch?.frequency ?: 0f

        // ── Profiling: learn the room, decide nothing yet ───────────────────────
        if (profiling) {
            profileLevels.add(levelRms)
            if (rawTonal) {
                profilePitches.add(rawHz)
                val bin = binIndex(rawHz)
                binAccum[bin]++
            }
            if (profileLevels.size >= profileFrames) finishProfiling()
            return report(ListeningState.PROFILING, null, Float.NaN)
        }

        // A tone sitting on the learned hum, no louder than the room, is the hum.
        val isHum = rawTonal && humHz > 0f &&
                abs(cents(rawHz, humHz)) < HUM_REJECT_CENTS &&
                levelRms < ambientFloor * HUM_LEVEL_MARGIN
        val tonal = rawTonal && !isHum
        val hz = if (tonal) rawHz else null

        val loud = levelRms > ambientFloor * LEVEL_MARGIN
        // Track the room only on genuinely quiet, tone-free frames.
        if (!loud && !rawTonal) {
            ambientFloor = (ambientFloor + FLOOR_ADAPT * (levelRms - ambientFloor))
                .coerceAtLeast(MIN_FLOOR)
        }

        val active = loud && tonal
        pushPitch(if (active) hz else null)
        val recent = pitchRing.filterNotNull()
        val spread = if (recent.size >= 2) centsSpread(recent) else Float.NaN

        // Age the re-acquire memory while not engaged; expire it once the window closes.
        if (!engaged && recentlyLockedHz > 0f) {
            recentLockAge++
            if (recentLockAge > reacquireFrames) recentlyLockedHz = 0f
        }

        // ── Engaged: hold the lock with loose, hysteretic criteria ──────────────
        if (engaged) {
            if (active && abs(cents(hz!!, lockedHz)) <= HOLD_STEP_CENTS) {
                // Good frame — still on the note.
                lockedHz = hz
                gapRun = 0
                disturbRun = 0
                return report(ListeningState.LOCKED, hz, spread)
            }
            if (!active) {
                // Silence / quiet — short ride-out (instrument may have been briefly muted).
                disturbRun = 0
                gapRun++
                if (gapRun <= holdGapFrames) return report(ListeningState.LOCKED, lockedHz, spread)
            } else {
                // Active but wrong pitch — speech or noise over the note.
                // Ride out longer: the instrument is likely still physically sounding.
                gapRun = 0
                disturbRun++
                if (disturbRun <= holdDisturbFrames) return report(ListeningState.LOCKED, lockedHz, spread)
            }
            // Lock has expired — store where it was for fast re-acquisition.
            recentlyLockedHz = lockedHz
            recentLockAge = 0
            engaged = false
            steadyRun = 0
            gapRun = 0
            disturbRun = 0
        }

        // ── Not engaged: classify, and try to acquire ───────────────────────────
        if (!loud) {
            steadyRun = 0
            return report(ListeningState.QUIET, null, spread)
        }
        if (!tonal) {
            steadyRun = 0
            return report(ListeningState.NOISE, null, spread)
        }

        // Fast re-acquisition: if the note that was just locked returns within the
        // re-acquire window, snap back immediately without requiring a full ACQUIRE_MS
        // stability cycle.  Handles "spoke briefly, instrument was still playing".
        if (recentlyLockedHz > 0f && hz != null &&
                abs(cents(hz, recentlyLockedHz)) <= ACQUIRE_CENTS) {
            engaged = true
            lockedHz = hz
            steadyRun = 0
            gapRun = 0
            disturbRun = 0
            recentlyLockedHz = 0f
            return report(ListeningState.LOCKED, hz, spread)
        }

        val steadyEnough = recent.size >= MIN_RECENT &&
                !spread.isNaN() && spread <= ACQUIRE_CENTS
        if (steadyEnough) {
            steadyRun++
            if (steadyRun >= acquireFrames) {
                engaged = true
                lockedHz = hz!!
                gapRun = 0
                disturbRun = 0
                return report(ListeningState.LOCKED, hz, spread)
            }
            return report(ListeningState.ACQUIRING, hz, spread)
        }
        steadyRun = 0
        return report(ListeningState.UNSTABLE, hz, spread)
    }

    // ── Internals ────────────────────────────────────────────────────────────────

    private fun finishProfiling() {
        profiling = false
        if (profileLevels.isNotEmpty()) {
            profileLevels.sort()
            ambientFloor = profileLevels[profileLevels.size / 2].coerceAtLeast(MIN_FLOOR)
        }
        // A tone present and consistent through most of profiling is the room's hum.
        if (profilePitches.size >= profileFrames * HUM_PRESENCE && profilePitches.size >= 2) {
            profilePitches.sort()
            if (centsSpread(profilePitches) < HUM_SPREAD_CENTS) {
                humHz = profilePitches[profilePitches.size / 2]
            }
        }
        // Freeze the normalized histogram from the profiling observations.
        val peak = binAccum.max().takeIf { it > 0f } ?: 1f
        for (i in binNorm.indices) binNorm[i] = binAccum[i] / peak
    }

    private fun pushPitch(hz: Float?) {
        pitchRing.addLast(hz)
        while (pitchRing.size > historyFrames) pitchRing.removeFirst()
    }

    private fun ambientBand(): AmbientLevel = when {
        ambientFloor >= NOISY_FLOOR -> AmbientLevel.NOISY
        ambientFloor >= MODERATE_FLOOR -> AmbientLevel.MODERATE
        else -> AmbientLevel.QUIET
    }

    private fun report(state: ListeningState, candidateHz: Float?, spread: Float): AmbientReport {
        val level = ambientBand()
        val (headline, guidance) = messageFor(state, level, candidateHz)
        return AmbientReport(
            state = state,
            headline = headline,
            guidance = guidance,
            ambientLevel = level,
            candidateHz = candidateHz,
            stabilityCents = spread,
            locked = state == ListeningState.LOCKED,
            ambientBins = currentBinsSnapshot(),
        )
    }

    /** Live-normalized bin snapshot for the histogram: during profiling returns partial
     *  data so the bars visually grow as the room is learned. After profiling, returns
     *  the frozen normalized profile. */
    private fun currentBinsSnapshot(): FloatArray {
        return if (profiling) {
            val peak = binAccum.max().takeIf { it > 0f } ?: 1f
            FloatArray(AmbientReport.BIN_COUNT) { binAccum[it] / peak }
        } else {
            binNorm.copyOf()
        }
    }

    /** Log-frequency bin index for [hz], clamped to valid range. */
    private fun binIndex(hz: Float): Int {
        val lo = ln(AmbientReport.BIN_FREQ_LO.toDouble())
        val hi = ln(AmbientReport.BIN_FREQ_HI.toDouble())
        val t = (ln(hz.toDouble()) - lo) / (hi - lo)
        return (t * AmbientReport.BIN_COUNT).toInt().coerceIn(0, AmbientReport.BIN_COUNT - 1)
    }

    /** The live-assistant copy: what is heard, what is being done, what to do. */
    private fun messageFor(
        state: ListeningState,
        level: AmbientLevel,
        candidateHz: Float?,
    ): Pair<String, String> {
        val humNote = if (humHz > 0f) " A steady room hum is being filtered out." else ""
        return when (state) {
            ListeningState.PROFILING ->
                "Learning the room" to
                        "Measuring background sound. Hold on a moment."

            ListeningState.QUIET -> "Listening" to (
                    when (level) {
                        AmbientLevel.QUIET -> "Quiet here. Play a single, sustained note."
                        AmbientLevel.MODERATE -> "Some background sound. Play a clear, steady note."
                        AmbientLevel.NOISY ->
                            "Noisy spot. A note will still work if it rings out above the background."
                    } + humNote)

            ListeningState.NOISE ->
                "Background sound" to
                        "I hear sound but no clear note, likely movement or speech. " +
                        "Play one steady note."

            ListeningState.UNSTABLE ->
                "Pitch won't settle" to
                        "The pitch keeps moving, probably a voice. " +
                        "Hold one note and let it ring."

            ListeningState.ACQUIRING -> {
                val near = candidateHz?.let { " near ${NoteNames.label(it)}" } ?: ""
                "Found a tone$near" to "Holding steady, confirming the note..."
            }

            ListeningState.LOCKED -> {
                val on = candidateHz?.let { " on ${NoteNames.label(it)}" } ?: ""
                "Locked$on" to "Steady tone, the needle is tracking it."
            }
        }
    }

    private fun framesFor(ms: Double, hopMs: Double): Int =
        ceil(ms / hopMs).toInt().coerceAtLeast(1)

    private fun cents(a: Float, b: Float): Float =
        (1200.0 * log2(a.toDouble() / b.toDouble())).toFloat()

    private fun centsSpread(values: List<Float>): Float =
        (1200.0 * log2(values.max().toDouble() / values.min().toDouble())).toFloat()
}
