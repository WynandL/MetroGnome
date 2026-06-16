package com.example.metrognome.audio.tuner

import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.dsp.PitchDetector
import kotlin.math.abs
import kotlin.math.ceil
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
 * @property humHz          frequency of the steady room tone being filtered out, or
 *                          0f when none was learned during profiling
 */
data class AmbientReport(
    val state: ListeningState,
    val headline: String,
    val guidance: String,
    val ambientLevel: AmbientLevel,
    val candidateHz: Float?,
    val stabilityCents: Float,
    val locked: Boolean,
    val humHz: Float = 0f,
) {
    companion object {
        /** Low end of the tuner's frequency-rail display axis (Hz). */
        const val DISPLAY_FREQ_LO = 80f
        /** High end of the tuner's frequency-rail display axis (Hz). */
        const val DISPLAY_FREQ_HI = 3500f

        /** Neutral report for when the tuner is not listening. */
        val Idle = AmbientReport(
            state = ListeningState.QUIET,
            headline = "Getting ready…",
            guidance = "",
            ambientLevel = AmbientLevel.QUIET,
            candidateHz = null,
            stabilityCents = Float.NaN,
            locked = false,
        )
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

    /**
     * Upper limit on how far the live noise estimate may stretch the hold ride-out windows
     * (Tier 5). 1.0 disables the stretch entirely. Settable so the [AmbientTuning] strength
     * level can drive it live, while keeping this class free of any global reads (so the unit
     * tests stay deterministic). Defaults to the full stretch.
     */
    var maxHoldScale: Float = DEFAULT_MAX_HOLD_SCALE

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

        /**
         * To *acquire* a new lock, a frame's pitch must clear this clarity — strict, so a
         * voice or noise can never trigger a false lock. Tight to acquire.
         */
        const val ACQUIRE_CLARITY = 0.85f

        /**
         * To *hold* an existing lock, a frame need only clear this lower clarity (provided
         * its pitch still sits on the locked note). The [PitchDetector] reports correct
         * frequencies well below the acquire bar when noise erodes clarity; discarding
         * those frames is what used to starve a perfectly good lock in a noisy room. Loose
         * to hold. The same value is the bar for the targeted presence probe.
         */
        const val HOLD_CLARITY = 0.65f

        /** Default upper limit on how far a noisy room may stretch the hold ride-out windows. */
        private const val DEFAULT_MAX_HOLD_SCALE = 2.0f

        /** Per-frame decay of the live broadband-noise estimate when no fresh noise arrives. */
        private const val NOISE_DECAY = 0.04f

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
    private var liveNoise = 0f   // live peak-hold estimate of loud, tone-free (noise) frames


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
     * @param pitch       the [PitchDetector] result for this window, or null
     * @param levelRms    RMS level of the same window (normalised, ≈0..1)
     * @param nearClarity optional targeted presence of the *currently locked* note at its
     *        own frequency (see [PitchDetector.presenceAt]). Lets a lock survive a louder
     *        interferer that hijacks the global pitch, or clarity erosion in noise, by
     *        confirming the note is still physically present. 0 when not supplied / not
     *        locked; ignored entirely while acquiring, so it can never cause a false lock.
     * @return what the analyzer now believes about the environment
     */
    fun observe(
        pitch: PitchDetector.Pitch?,
        levelRms: Float,
        nearClarity: Float = 0f,
    ): AmbientReport {
        val rawTonal = pitch != null && pitch.clarity >= ACQUIRE_CLARITY
        val rawHz = pitch?.frequency ?: 0f

        // ── Profiling: learn the room, decide nothing yet ───────────────────────
        if (profiling) {
            profileLevels.add(levelRms)
            if (rawTonal) profilePitches.add(rawHz)
            if (profileLevels.size >= profileFrames) finishProfiling()
            return report(ListeningState.PROFILING, null, Float.NaN)
        }

        // A tone sitting on the learned hum, no louder than the room, is the hum.
        val isHum = rawTonal && humHz > 0f &&
                abs(cents(rawHz, humHz)) < HUM_REJECT_CENTS &&
                levelRms < ambientFloor * HUM_LEVEL_MARGIN
        val tonal = rawTonal && !isHum
        val hz = if (tonal) rawHz else null

        // A pitch that clears the lower *hold* bar (and is not the hum). Used only to keep
        // an existing lock alive — never to acquire one.
        val holdTonal = pitch != null && pitch.clarity >= HOLD_CLARITY && !isHum

        val loud = levelRms > ambientFloor * LEVEL_MARGIN
        // Track the room only on genuinely quiet, tone-free frames.
        if (!loud && !rawTonal) {
            ambientFloor = (ambientFloor + FLOOR_ADAPT * (levelRms - ambientFloor))
                .coerceAtLeast(MIN_FLOOR)
        }

        // Live broadband-noise estimate: peak-hold the level of loud, tone-free frames and
        // decay slowly. Unlike [ambientFloor] (frozen after profiling) this reacts to noise
        // that starts mid-session, and stretches the hold ride-out while noise is present.
        liveNoise = if (loud && !rawTonal) maxOf(liveNoise, levelRms)
                    else liveNoise * (1f - NOISE_DECAY)

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
            // The note is confirmed still present this frame if EITHER the global pitch
            // sits on the locked note (even at the lenient hold clarity) OR the targeted
            // presence probe still finds it at its own frequency (survives a hijack/noise).
            val onNoteByPitch = loud && holdTonal &&
                    abs(cents(rawHz, lockedHz)) <= HOLD_STEP_CENTS
            val onNoteByPresence = nearClarity >= HOLD_CLARITY
            if (onNoteByPitch || onNoteByPresence) {
                if (onNoteByPitch) lockedHz = rawHz   // track a slow glide via the real pitch
                gapRun = 0
                disturbRun = 0
                return report(ListeningState.LOCKED, lockedHz, spread)
            }

            // Not confirmed this frame. Ride it out — longer when the room is noisy, and
            // longer for a competing tone (speech over the note) than for plain silence.
            val scale = holdScale()
            val disturbance = loud && holdTonal   // a wrong-pitch tone sitting over the note
            if (disturbance) {
                gapRun = 0
                disturbRun++
                if (disturbRun <= (holdDisturbFrames * scale).toInt())
                    return report(ListeningState.LOCKED, lockedHz, spread)
            } else {
                disturbRun = 0
                gapRun++
                if (gapRun <= (holdGapFrames * scale).toInt())
                    return report(ListeningState.LOCKED, lockedHz, spread)
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

        // Acquire on a *robust* spread (a single stray frame in noise no longer blocks a
        // lock), but still report the full spread for the UI stability read.
        val acquireSpread = robustSpread(recent)
        val steadyEnough = recent.size >= MIN_RECENT &&
                !acquireSpread.isNaN() && acquireSpread <= ACQUIRE_CENTS
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

    /**
     * Factor (1..[MAX_HOLD_SCALE]) by which to stretch the hold ride-out windows, driven by
     * the live noise estimate relative to the loudness gate. 1 in a quiet room; grows as
     * recent broadband noise rises, so a confirmed lock is given more rope exactly when the
     * room is working against it. Acquisition is never scaled, so false locks stay hard.
     */
    private fun holdScale(): Float {
        if (maxHoldScale <= 1f) return 1f
        val ref = (ambientFloor * LEVEL_MARGIN).coerceAtLeast(MIN_FLOOR)
        return (liveNoise / ref).coerceIn(1f, maxHoldScale)
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
            humHz = humHz,
        )
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

    /**
     * Spread in cents after trimming the single lowest and highest value, so one stray
     * frame (an octave blip, a transient in noise) cannot by itself block acquisition.
     * Falls back to the plain [centsSpread] for short rings where trimming is unsafe.
     */
    private fun robustSpread(values: List<Float>): Float {
        if (values.size < 5) return if (values.size >= 2) centsSpread(values) else Float.NaN
        val sorted = values.sorted()
        val trimmed = sorted.subList(1, sorted.size - 1)
        return (1200.0 * log2(trimmed.last().toDouble() / trimmed.first().toDouble())).toFloat()
    }
}
