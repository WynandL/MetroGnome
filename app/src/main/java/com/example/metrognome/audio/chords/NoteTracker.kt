package com.example.metrognome.audio.chords

import com.example.metrognome.audio.dsp.PitchDetector
import com.example.metrognome.audio.tuner.ListeningState
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.roundToInt

/**
 * Onset-segmented note tracker: turns a stream of per-frame analyses into note events.
 *
 * This is the standard monophonic note-labelling model (Brossier, Bello & Plumbley,
 * "Fast labelling of notes in music signals", ISMIR 2004; the design behind aubio's
 * `notes`): an **onset** marks where a note begins, the first few frames of the attack
 * are skipped (a plucked string's transient has no settled pitch), and the note's pitch
 * is the **median** of the detector's readings over the frames that follow. There is no
 * hold and no ride-out: a new onset is a new note, full stop. That is the property the
 * tuner's gate cannot have, since a tuner must keep its needle on a note while someone
 * talks over it, and it is the whole reason this tracker exists beside it.
 *
 * How long the evidence must hold depends on how good it is. A note that is loud and
 * clear ([STRONG_RMS], [STRONG_CLARITY]) is decided in about 70 ms; anything weaker must
 * hold its pitch for [PATIENT_MS]. The first cut decided everything the fast way, and on
 * a real phone it named dogs barking outside, a hadeda and a cleared throat: all pitched,
 * all quiet, none of them holding a pitch.
 *
 * Each frame brings two pitch readings. [Frame.pitch] is plain MPM over the window.
 * [Frame.residual] is MPM after the spectrum from just before the onset has been
 * subtracted ([PitchDetector.detectAbove]), and it is the one the evidence prefers:
 * when a note is plucked over one still ringing, the plain reading tends to the pair's
 * common period (a fifth reads an octave below its root), while the residual hears only
 * what is new.
 *
 * The room is profiled for [PROFILE_MS] first, the tuner's way: a level floor, and any
 * tone already sounding through most of it (mains hum, a fan, an alarm), which is then
 * never evidence while it stays near the floor. The floor follows the room afterwards,
 * quickly downward and by a slow ratchet upward.
 *
 * Two guarded fallbacks cover notes with no clean attack:
 *
 *  - **A rise from silence** counts as an onset even if the flux missed it (a very soft
 *    first note). Nothing is armed for the residual on this path, so it decides from
 *    the plain pitch, and that must be *clear* ([STEADY_CLARITY], not the residual's
 *    [CANDIDATE_CLARITY]): the level rising out of silence is also what a microphone's
 *    automatic gain does to room hum in the gap after a loud note, and that hum read as
 *    a note at clarity 0.6 on a real phone, a low phantom in every gap that, being below
 *    the bass, restarted the chord (found 2026-09-21 through the Chord Loop). A real
 *    soft note reads above 0.9.
 *  - **A steady new pitch** (clear, and holding [STEADY_MS] within [STEADY_CENTS]) with
 *    no onset counts as a note, so a bowed or swelled note still lands. This path is
 *    the one that could be fooled by the virtual fundamental of a sustained chord, so
 *    it refuses a pitch that is a sub-harmonic (an integer division, 2..8) of any note
 *    emitted in the last [RECENT_MS], and refuses to re-emit a note still in that memory
 *    (an old note re-emerging as a newer one decays). A real pluck of either has an
 *    onset and takes the flux-onset path, which has neither guard.
 *
 * Frequencies only, no note names: mapping to MIDI (reference pitch, calibration) is
 * the capture engine's job, so this stays deterministic and unit-testable on frames.
 *
 * @param hopMillis wall-clock duration of one frame, used to turn the millisecond
 *        constants into frame counts.
 */
class NoteTracker(hopMillis: Double) {

    /** One analysed hop. */
    data class Frame(
        /** The onset detector reported an onset on this call. */
        val onset: Boolean,
        /** Plain pitch over the window, or null. */
        val pitch: PitchDetector.Pitch?,
        /** Pitch of what is new relative to the pre-onset spectrum, or null when none is armed. */
        val residual: PitchDetector.Pitch?,
        /** RMS of the window, samples normalised to about +-1. */
        val rms: Float,
    )

    /** Which path decided a note: the onset detector, a rise out of silence, or a steady pitch with no attack. */
    enum class Source { FLUX, LEVEL, STEADY }

    /** A note the player sounded. */
    data class Note(
        val frequency: Float,
        /** Mean clarity of the frames that decided it. */
        val clarity: Float,
        val source: Source,
        /** Decided by the patient rule (weak evidence that held), not the fast one. */
        val patient: Boolean = false,
    ) {
        /** True when decided from an onset (flux or level); false for the steady-pitch fallback. */
        val fromOnset: Boolean get() = source != Source.STEADY
    }

    /** What the tracker made of one frame. */
    data class Observation(
        val state: ListeningState,
        /** What is being heard right now, for a live readout, or null. */
        val heardHz: Float?,
        val heardClarity: Float,
        /** A note decided on this frame, or null. */
        val note: Note?,
        /** The frame cleared the level floor (diagnostics). */
        val loud: Boolean = false,
        /** The level floor in force (diagnostics). */
        val floor: Float = 0f,
    )

    companion object {
        /**
         * Room profiling at start-up: the level floor, and any tone already present. The
         * tuner's 900 ms, not the 300 ms of the first cut, which was over before a phone
         * mic's automatic gain had settled after opening and learned a floor the room
         * then rose above.
         */
        private const val PROFILE_MS = 900.0

        /** A frame is loud when its RMS exceeds the floor by this factor. */
        private const val LEVEL_MARGIN = 2.0f
        private const val MIN_FLOOR = 0.0012f
        private const val FLOOR_ADAPT = 0.02f

        /**
         * Upward ratchet of the floor on loud frames with no pitch in them, a fixed
         * fraction per frame (the tuner's rule, at this hop): a room that gets louder is
         * the floor within about a minute, a brief bang moves it by a few percent.
         */
        private const val FLOOR_RISE = 0.0005f

        /**
         * A tone present through most of profiling is the room's (mains hum, a fan, an
         * alarm), the tuner's rule exactly: at least [HUM_PRESENCE] of the profile frames
         * pitched at [STEADY_CLARITY] and agreeing within [HUM_SPREAD_CENTS]. Afterwards a
         * pitch within [HUM_REJECT_CENTS] of it, while the frame is no louder than
         * [HUM_LEVEL_MARGIN] times the floor, is not evidence of anything. Added after a
         * silent room named G1 (49 Hz, mains) the moment the mic opened.
         */
        private const val HUM_PRESENCE = 0.6
        private const val HUM_SPREAD_CENTS = 60f
        private const val HUM_REJECT_CENTS = 70f
        private const val HUM_LEVEL_MARGIN = 3.0f

        /** Frames after an onset whose pitch is not trusted (the attack transient). */
        private const val ATTACK_SKIP_MS = 25.0

        /** Evidence window after the skip for *strong* evidence; the decision falls at its end at the latest. */
        private const val EVIDENCE_MS = 110.0

        /** Agreeing candidate frames needed to decide a note. */
        const val MIN_EVIDENCE = 4

        /** Candidates within this many cents of their median agree. */
        private const val AGREE_CENTS = 60f

        /** Fraction of candidates that must agree at the end of the window (early decisions need all of them). */
        private const val AGREE_FRACTION = 0.6f

        /**
         * Strong evidence: the frames' median RMS at least this (about -28 dBFS; a played
         * note at a phone sits at -25 to -10) and their mean clarity at least
         * [STRONG_CLARITY]. Anything weaker is decided by the patient rule instead.
         * Measured 2026-09-21 in a room with dogs barking outside: every phantom the fast
         * rule let through was a pitched bark or voice at -45 to -35 dBFS, swelling over
         * 200 ms with clarity climbing 0.6 to 0.89 and the pitch gliding 85 to 180 cents,
         * while a note hit -15 dBFS with clarity 0.9+ inside 40 ms and held within 2 cents.
         */
        private const val STRONG_RMS = 0.04f
        private const val STRONG_CLARITY = 0.9f

        /**
         * The patient rule for weak evidence: the window stays open this long, and the
         * pitch must hold within [PATIENT_CENTS] on [PATIENT_MIN_EVIDENCE] frames making up
         * [PATIENT_FRACTION] of the candidates. A bark or a word glides and is over before
         * this; a soft note holds. Slower than the fast rule, still a third of the tuner.
         */
        private const val PATIENT_MS = 300.0
        private const val PATIENT_CENTS = 30f
        private const val PATIENT_MIN_EVIDENCE = 14
        private const val PATIENT_FRACTION = 0.7f

        /** A reading below this clarity is not evidence. Lower than the tuner's acquire bar: the residual of a mix is never pristine. */
        private const val CANDIDATE_CLARITY = 0.5f

        /** Onsets closer than this to the current evidence window's start are the same event. */
        private const val MIN_ONSET_INTERVAL_MS = 50.0

        /**
         * Steady-pitch fallback: clarity, tolerance and duration a new pitch must hold with
         * no onset. The hold is the patient rule's [PATIENT_MS], not the 140 ms of the
         * first cut: a spoken vowel at low level held 140 ms within 25 cents and was named
         * (F2, D#2 from the dev talking to the phone); a bowed swell holds far longer.
         */
        private const val STEADY_CLARITY = 0.85f
        private const val STEADY_CENTS = 25f
        private const val STEADY_MS = 300.0

        /** A pitch this close to the current note is the same note. */
        private const val SAME_NOTE_CENTS = 60f

        /** Notes emitted within this window guard the steady-pitch fallback. */
        private const val RECENT_MS = 4_000.0

        /** Sub-harmonic guard: integer ratios up to this, within this tolerance. */
        private const val SUBHARMONIC_MAX = 8
        private const val SUBHARMONIC_CENTS = 35f

        /** Quiet frames before the current note is considered over. */
        private const val NOTE_END_MS = 200.0

        /** Clarity below which the live readout shows nothing. */
        private const val HEARD_CLARITY = 0.5f
    }

    private val profileFrames = framesFor(PROFILE_MS, hopMillis)
    private val skipFrames = framesFor(ATTACK_SKIP_MS, hopMillis)
    private val evidenceFrames = framesFor(EVIDENCE_MS, hopMillis).coerceAtLeast(MIN_EVIDENCE)
    private val patientFrames = framesFor(PATIENT_MS, hopMillis).coerceAtLeast(PATIENT_MIN_EVIDENCE)
    private val minOnsetIntervalFrames = framesFor(MIN_ONSET_INTERVAL_MS, hopMillis)
    private val steadyFrames = framesFor(STEADY_MS, hopMillis)
    private val recentFrames = framesFor(RECENT_MS, hopMillis)
    private val noteEndFrames = framesFor(NOTE_END_MS, hopMillis)

    /** Frames after an onset during which a residual reading is wanted (skip plus the longest evidence window). */
    val evidenceSpanFrames: Int get() = skipFrames + patientFrames

    // Room model.
    private var profiling = true
    private val profileLevels = ArrayList<Float>()
    private val profilePitches = ArrayList<Float>()
    private var floor = MIN_FLOOR
    private var humHz = 0f   // 0 = no room tone learned
    private var prevLoud = false
    private var quietRun = 0

    /** The room tone learned during profiling, or 0 (diagnostics). */
    val roomToneHz: Float get() = humHz

    // Evidence collection after an onset.
    private var collecting = false
    private var evidenceStart = 0L
    private var windowFromFlux = false   // the detector saw this onset (else only the level rose)
    private val candidates = ArrayList<PitchDetector.Pitch>()
    private val candidateRms = ArrayList<Float>()   // parallel to candidates

    // Steady-pitch fallback.
    private var steadyHz = 0f
    private var steadyRun = 0

    // The current note and the recent memory.
    private var currentHz = 0f          // 0 = none sounding
    private var currentClarity = 0f
    private val recent = ArrayDeque<Pair<Float, Long>>()   // (hz, frame emitted)
    private var frameIndex = 0L

    /** Forget the current note and the recent memory, as when the user clears the finder. */
    fun forget() {
        currentHz = 0f
        recent.clear()
        collecting = false
        candidates.clear()
        candidateRms.clear()
        steadyRun = 0
    }

    /** Feed one frame; call once per hop. */
    fun observe(raw: Frame): Observation {
        val f = frameIndex++
        if (profiling) {
            profileLevels += raw.rms
            raw.pitch?.let { if (it.clarity >= STEADY_CLARITY) profilePitches += it.frequency }
            if (profileLevels.size >= profileFrames) finishProfiling()
            return Observation(ListeningState.PROFILING, null, 0f, null)
        }

        // A pitch on the room tone, no louder than the room, is the room: drop it from
        // the frame before anything else looks at it.
        val frame = if (humHz > 0f && raw.rms < floor * HUM_LEVEL_MARGIN)
            raw.copy(pitch = raw.pitch?.takeUnless { isHum(it) }, residual = raw.residual?.takeUnless { isHum(it) })
        else raw

        val loud = frame.rms > floor * LEVEL_MARGIN
        val toneFree = frame.pitch == null || frame.pitch.clarity < HEARD_CLARITY
        if (toneFree) {
            floor = if (loud) minOf(frame.rms, floor * (1f + FLOOR_RISE))
                else (floor + FLOOR_ADAPT * (frame.rms - floor)).coerceAtLeast(MIN_FLOOR)
        }
        while (recent.isNotEmpty() && f - recent.first().second > recentFrames) recent.removeFirst()

        // An onset, from the detector or from a rise out of silence, opens (or reopens) the
        // evidence window, unless it is the same event as the one already being weighed.
        val levelOnset = loud && !prevLoud
        prevLoud = loud
        if (frame.onset || levelOnset) {
            val sameEvent = collecting && f - evidenceStart < minOnsetIntervalFrames
            if (!sameEvent) {
                collecting = true
                evidenceStart = f
                windowFromFlux = frame.onset
                candidates.clear()
                candidateRms.clear()
                steadyRun = 0
            } else if (frame.onset) {
                windowFromFlux = true
            }
        }

        var note: Note? = null

        if (collecting) {
            val age = (f - evidenceStart).toInt()
            if (age >= skipFrames) {
                // A flux window is judged on the residual alone: the plain pitch of a mix is
                // its common period, and falling back to it on a frame where nothing new was
                // found would name the virtual fundamental of whatever is ringing.
                val cand = if (windowFromFlux) frame.residual else frame.pitch
                val bar = if (windowFromFlux) CANDIDATE_CLARITY else STEADY_CLARITY
                if (loud && cand != null && cand.clarity >= bar) { candidates += cand; candidateRms += frame.rms }
                val quickClose = age >= skipFrames + evidenceFrames - 1
                val patientClose = age >= skipFrames + patientFrames - 1
                decide(if (windowFromFlux) Source.FLUX else Source.LEVEL, quickClose, patientClose)?.let { note = it }
                if (note != null || patientClose) collecting = false
            }
        } else if (loud && frame.pitch != null && frame.pitch.clarity >= STEADY_CLARITY) {
            // Steady-pitch fallback for a note with no attack.
            val p = frame.pitch
            if (steadyRun > 0 && abs(cents(p.frequency, steadyHz)) <= STEADY_CENTS) steadyRun++
            else { steadyHz = p.frequency; steadyRun = 1 }
            if (steadyRun >= steadyFrames) {
                val isNew = currentHz == 0f || abs(cents(steadyHz, currentHz)) > SAME_NOTE_CENTS
                if (isNew && !inRecent(steadyHz) && !subharmonicOfRecent(steadyHz)) {
                    note = emit(steadyHz, p.clarity, Source.STEADY, at = f)
                }
                steadyRun = 0
            }
        } else {
            steadyRun = 0
        }

        // Note end: sustained quiet.
        if (loud) quietRun = 0 else if (++quietRun >= noteEndFrames) currentHz = 0f

        val state = when {
            collecting -> ListeningState.ACQUIRING
            !loud -> ListeningState.QUIET
            currentHz > 0f -> ListeningState.LOCKED
            frame.pitch != null && frame.pitch.clarity >= HEARD_CLARITY -> ListeningState.UNSTABLE
            else -> ListeningState.NOISE
        }

        // Live readout: the evidence so far while weighing, the current note while it
        // sustains (not the mix's virtual fundamental), else whatever clear pitch is there.
        val live = frame.residual ?: frame.pitch
        val heard: PitchDetector.Pitch? = when {
            !loud -> null
            collecting && candidates.isNotEmpty() -> candidates.last()
            currentHz > 0f -> {
                val p = frame.pitch
                if (p != null && abs(cents(p.frequency, currentHz)) <= SAME_NOTE_CENTS) p
                else PitchDetector.Pitch(currentHz, currentClarity)
            }
            live != null && live.clarity >= HEARD_CLARITY -> live
            else -> null
        }
        return Observation(state, heard?.frequency, heard?.clarity ?: 0f, note, loud, floor)
    }

    /**
     * Try to decide from the candidates so far: fast when the evidence is strong, patient
     * when it is not.
     *
     * Strong evidence (level and clarity both high, see [STRONG_RMS]) is decided the quick
     * way: early only on unanimous agreement within [AGREE_CENTS]; from [quickClose] on, a
     * [AGREE_FRACTION] majority around the median will do. Weak evidence must hold within
     * [PATIENT_CENTS] on at least [PATIENT_MIN_EVIDENCE] frames: unanimously to decide
     * early, or as [PATIENT_FRACTION] of the candidates at [patientClose].
     */
    private fun decide(source: Source, quickClose: Boolean, patientClose: Boolean): Note? {
        if (candidates.size < MIN_EVIDENCE) return null
        val sorted = candidates.map { it.frequency }.sorted()
        val median = sorted[sorted.size / 2]
        val rmsSorted = candidateRms.sorted()
        val meanClarity = candidates.map { it.clarity }.average().toFloat()
        val strong = rmsSorted[rmsSorted.size / 2] >= STRONG_RMS && meanClarity >= STRONG_CLARITY

        val agreeing: List<PitchDetector.Pitch>
        val enough: Boolean
        if (strong) {
            agreeing = candidates.filter { abs(cents(it.frequency, median)) <= AGREE_CENTS }
            enough = if (quickClose) agreeing.size >= MIN_EVIDENCE && agreeing.size >= AGREE_FRACTION * candidates.size
                     else agreeing.size == candidates.size
        } else {
            agreeing = candidates.filter { abs(cents(it.frequency, median)) <= PATIENT_CENTS }
            enough = agreeing.size >= PATIENT_MIN_EVIDENCE &&
                (if (patientClose) agreeing.size >= PATIENT_FRACTION * candidates.size else agreeing.size == candidates.size)
        }
        if (!enough) return null
        val hzs = agreeing.map { it.frequency }.sorted()
        val hz = hzs[hzs.size / 2]
        val clarity = agreeing.map { it.clarity }.average().toFloat()
        return emit(hz, clarity, source, at = frameIndex - 1, patient = !strong)
    }

    private fun emit(hz: Float, clarity: Float, source: Source, at: Long, patient: Boolean = false): Note {
        currentHz = hz
        currentClarity = clarity
        quietRun = 0
        recent.addLast(hz to at)
        return Note(hz, clarity, source, patient)
    }

    private fun finishProfiling() {
        profiling = false
        profileLevels.sort()
        floor = profileLevels[profileLevels.size / 2].coerceAtLeast(MIN_FLOOR)
        if (profilePitches.size >= profileFrames * HUM_PRESENCE && profilePitches.size >= 2) {
            profilePitches.sort()
            val spread = cents(profilePitches.last(), profilePitches.first())
            if (spread < HUM_SPREAD_CENTS) humHz = profilePitches[profilePitches.size / 2]
        }
        profileLevels.clear()
        profilePitches.clear()
    }

    private fun isHum(p: PitchDetector.Pitch): Boolean = abs(cents(p.frequency, humHz)) < HUM_REJECT_CENTS

    private fun inRecent(hz: Float): Boolean =
        recent.any { abs(cents(hz, it.first)) <= SAME_NOTE_CENTS }

    /** True when [hz] divides a recent note's frequency by a small integer: the missing fundamental of a mix, not a played note. */
    private fun subharmonicOfRecent(hz: Float): Boolean = recent.any { (r, _) ->
        val ratio = r / hz
        val n = ratio.roundToInt()
        n in 2..SUBHARMONIC_MAX && abs(cents(r, hz * n)) <= SUBHARMONIC_CENTS
    }

    private fun framesFor(ms: Double, hopMs: Double): Int = ceil(ms / hopMs).toInt().coerceAtLeast(1)

    private fun cents(a: Float, b: Float): Float = (1200.0 * log2(a.toDouble() / b.toDouble())).toFloat()
}
