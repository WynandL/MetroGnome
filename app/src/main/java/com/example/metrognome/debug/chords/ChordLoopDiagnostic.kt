package com.example.metrognome.debug.chords

import android.os.SystemClock
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.chords.ChordEngine
import com.example.metrognome.audio.chords.NoteAnalyzer
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.theory.ChordReading
import com.example.metrognome.viewmodel.ChordFinderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

/**
 * DEV ONLY: a closed loop that plays the test chords through the speaker, watches what the
 * Chord Finder captures through the microphone, and reports exactly what came back.
 *
 * Two modes:
 *
 *  - **Spaced**: notes with silence between them, and the loop adjusts the playback
 *    timings until every chord comes back exactly as expected. The result is stored per
 *    device in [ChordTestTimingsStore], so a later run on that phone passes in its first
 *    round. It runs on whichever engine the Chords page has selected and says so.
 *  - **Legato**: a fast arpeggio with every note ringing on under the next, played once
 *    per [ChordEngine] with nothing tuned, and the two engines' results side by side:
 *    notes captured, chords named, how long after each note started it was captured. The
 *    verdict is computed from those numbers, never assumed. This is the comparison the
 *    onset engine was built to win, run on real hardware.
 *
 * ## Why a loop and not a table (spaced mode)
 * The failures have distinct signatures in the tuner's own state, and each points at one
 * knob. The loop reads the signature and turns that knob, rather than cycling a list:
 *
 *  - **Not heard** (no reading in the note's window, the ambient gate never left QUIET):
 *    the speaker is too quiet at that pitch. Raise the level; at full level, hold the note
 *    longer; if it still cannot be heard, shift the sequence up an octave, since a phone
 *    speaker puts out almost nothing below ~300 Hz.
 *  - **Heard but not captured** (readings on the right pitch, no capture): the lock came
 *    too late for the finder's three-reading confirmation. Hold the note longer.
 *  - **Held previous** (readings still on the *previous* note, or the note captured only
 *    after its window): the tuner rode out the silence and treated the new note as an
 *    interferer over the old one. Lengthen the gap; the lock drops through silence.
 *  - **Octave error** (heard or captured 12 semitones off): the speaker voices the partials
 *    and not the fundamental. Shift up an octave.
 *  - **Extra notes** (captures nobody asked for): distortion or harmonics registering as
 *    notes. Lower the level a little and widen the gap.
 *
 * A round is one pass over all three chords with the finder cleared between them (so what
 * is being tested is capture, not the below-the-bass split). It passes when every chord's
 * notes are captured, nothing extra is, and the finder names each chord as expected. Up
 * to [MAX_ROUNDS] rounds; the loop is built to converge, and a report of failure after
 * that many is meant to be rare and to say exactly which signature never went away.
 *
 * Everything observed comes from the ViewModel's public flows, the same ones the screen
 * draws from, so the loop sees precisely what the user would.
 */
object ChordLoopDiagnostic {

    enum class Status { IDLE, WAITING_FOR_MIC, RUNNING, PASSED, FAILED }

    enum class Mode(val label: String, val blurb: String) {
        SPACED("Spaced", "Notes with silence between them. Tunes the timings until the selected engine captures every chord."),
        LEGATO("Legato", "A fast arpeggio, each note ringing on under the next. Plays it to both engines and compares them."),
        LISTEN("Listen", "Plays nothing. Records what the selected engine makes of the room for ${LISTEN_MS / 1000} s: talk, cough, let the dogs bark."),
    }

    /** How long [Mode.LISTEN] records. */
    const val LISTEN_MS = 30_000L

    enum class Fault { OK, NOT_HEARD, HEARD_NOT_CAPTURED, HELD_PREVIOUS, LATE, OCTAVE_ERROR, WRONG_PITCH }

    data class NoteResult(
        val expected: Int,
        val fault: Fault,
        val captured: Int?,
        /** Capture time after the note's nominal start; includes the speaker's own latency. */
        val captureDelayMs: Long?,
        val heardMidis: List<Int>,
        val statesSeen: Set<ListeningState>,
    )

    /**
     * One raw event from the observed timeline, timestamped relative to the chord's nominal
     * start (negative when it was already in force before the chord began sounding).
     * [value] is a note label ("C4", or "none" for a reading that dropped out) for CAPTURE
     * and READING, or a [ListeningState] name for STATE.
     */
    data class TimelineEvent(val tMs: Long, val kind: Kind, val value: String) {
        enum class Kind { CAPTURE, READING, STATE }
    }

    /**
     * One hop of the onset engine while a chord sounded, relative to the chord's nominal
     * start: the analyzer's inputs to the tracker and the tracker's verdict. Recorded only
     * on the Brossier engine (the tuner engine has no per-hop trace); the whole reason the
     * log exists, since a phantom note's cause is visible only in the frames around it.
     */
    data class FrameRow(
        val tMs: Long,
        val rms: Float,
        val flux: Float,
        val onset: Boolean,
        val pitchHz: Float?,
        val pitchClarity: Float?,
        val residualHz: Float?,
        val residualClarity: Float?,
        val state: ListeningState,
        val loud: Boolean,
        val floor: Float,
        /** A note the tracker decided on this hop, "C4/FLUX/0.97", or null. */
        val note: String?,
        /** On a detector onset, the fraction of power that was new; null otherwise. */
        val newFraction: Float?,
    )

    data class ChordResult(
        val expectedNotes: List<Int>,
        val expectedSymbol: String,
        val gotNotes: List<Int>,
        val gotSymbol: String?,
        val notes: List<NoteResult>,
        val extras: List<Int>,
        /** Every capture/reading/state change observed while this chord sounded, in order. Not shown on screen; for export. */
        val events: List<TimelineEvent> = emptyList(),
        /** The onset engine's per-hop trace over the same span (empty on the tuner engine). For export. */
        val frames: List<FrameRow> = emptyList(),
    ) {
        val notesOk get() = notes.all { it.fault == Fault.OK } && extras.isEmpty()
        val pass get() = notesOk && gotSymbol == expectedSymbol
    }

    data class RoundReport(
        val round: Int,
        val engine: ChordEngine,
        val timings: ChordTestTimings,
        val chords: List<ChordResult>,
        val diagnosis: String,
        val adjustment: String,
    ) {
        val pass get() = chords.all { it.pass }
    }

    /** One engine's legato run. */
    data class LegatoReport(
        val engine: ChordEngine,
        val chords: List<ChordResult>,
    ) {
        val notesTotal get() = chords.sumOf { it.notes.size }
        val notesHit get() = chords.sumOf { c -> c.notes.count { it.fault == Fault.OK } }
        val chordsPassed get() = chords.count { it.pass }
        val extras get() = chords.sumOf { it.extras.size }
        val pass get() = chords.all { it.pass }
        /** Median capture delay over the notes that were captured, or null if none were. */
        val medianDelayMs: Long? get() {
            val d = chords.flatMap { c -> c.notes.mapNotNull { it.captureDelayMs } }.sorted()
            return if (d.isEmpty()) null else d[d.size / 2]
        }
        /** "9/9 notes, 3/3 chords, median 180 ms". */
        val summary: String get() =
            "$notesHit/$notesTotal notes, $chordsPassed/${chords.size} chords" +
                (medianDelayMs?.let { ", median $it ms" } ?: "") +
                (if (extras > 0) ", $extras extra" else "")
    }

    data class State(
        val status: Status = Status.IDLE,
        val mode: Mode = Mode.SPACED,
        val round: Int = 0,
        val timings: ChordTestTimings = ChordTestTimings.DEFAULT,
        val rounds: List<RoundReport> = emptyList(),
        val legato: List<LegatoReport> = emptyList(),
        val message: String = "",
        /** Index into [ChordArpeggioTestTone.CHORDS] of the chord sounding now, or -1 between chords. */
        val chordIndex: Int = -1,
        /** The engine the chord is being played to right now. */
        val engine: ChordEngine? = null,
        /** Notes fully played so far in the current pass (a spaced round, or the whole legato comparison). */
        val notesDone: Int = 0,
        /** Notes in the current pass: 100% on the progress bar. */
        val notesTotal: Int = 0,
        /** `elapsedRealtime` at which the sounding chord's first note is due, for per-note progress; 0 between chords. */
        val chordStartsAt: Long = 0L,
        /** Milliseconds from one of the sounding chord's notes to the next. */
        val chordSlotMs: Long = 0L,
        /** Notes in the sounding chord. */
        val chordNotes: Int = 0,
    ) {
        /**
         * Progress through the current pass, 0..1, counting the sounding chord's notes by
         * the clock: a note is credited once its slot has begun.
         */
        fun progressAt(nowMs: Long): Float {
            if (notesTotal <= 0) return 0f
            val elapsed = nowMs - chordStartsAt
            val inChord = if (chordStartsAt > 0L && chordSlotMs > 0L && elapsed >= 0L)
                (elapsed / chordSlotMs + 1).coerceAtMost(chordNotes.toLong()).toInt() else 0
            return ((notesDone + inChord).toFloat() / notesTotal).coerceIn(0f, 1f)
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    const val MAX_ROUNDS = 8
    private const val MIC_WAIT_MS = 60_000L
    private const val ROOM_PROFILE_MS = 1_200L
    private const val SETTLE_MS = 1_500L

    /**
     * Output latency allowance: an event this long after a note's nominal start is still
     * that note's. Static AudioTrack start-up plus the tuner's own acquire is comfortably
     * inside it, and the gaps are always longer, so attribution cannot straddle.
     */
    private const val LATENCY_MS = 250L

    /** Legato mode: a capture of the expected note this soon before its nominal start still counts as it (clock skew). */
    private const val LEGATO_EARLY_MS = 60L

    /** Marker in the readings timeline for "the tuner reported nothing". */
    private const val NO_READING = -1

    val isRunning: Boolean get() = job?.isActive == true

    /** Where the last run's full log is written when a run ends, whatever its outcome; readable with `adb shell run-as`. */
    const val LAST_LOG_NAME = "chord_loop_last.json"

    /**
     * Start a run. Returns false if one is already going. [logDir] (the app's files dir)
     * receives [LAST_LOG_NAME] when the run ends, so a log can be pulled over adb without
     * touching the phone.
     */
    fun start(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float, mode: Mode, logDir: File? = null): Boolean {
        if (isRunning) return false
        job = scope.launch { run(vm, store, referenceHz, mode, logDir) }
        return true
    }

    fun cancel() {
        job?.cancel()
        ChordArpeggioTestTone.stop()
        _state.value = _state.value.copy(status = Status.IDLE, message = "Cancelled", chordIndex = -1, engine = null)
    }

    private suspend fun run(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float, mode: Mode, logDir: File?) {
        vm.diagnosticMode = true
        try {
            when (mode) {
                Mode.SPACED -> runSpaced(vm, store, referenceHz)
                Mode.LEGATO -> runLegato(vm, store, referenceHz)
                Mode.LISTEN -> runListen(vm, store, referenceHz)
            }
        } finally {
            vm.diagnosticMode = false
            if (logDir != null) withContext(NonCancellable + Dispatchers.IO) {
                runCatching { File(logDir, LAST_LOG_NAME).writeText(_state.value.toJsonReport()) }
            }
        }
    }

    /** Wait for the Chords tab's mic, then for the engine to profile the room. False if it never opened. */
    private suspend fun awaitMic(vm: ChordFinderViewModel): Boolean {
        val listening = withTimeoutOrNull(MIC_WAIT_MS) { vm.listening.first { it } } ?: false
        if (!listening) {
            _state.value = _state.value.copy(status = Status.FAILED, message = "Mic never opened: open the Chords tab with the mic on, then run again")
            return false
        }
        delay(ROOM_PROFILE_MS)   // the tuner profiles the room before it will lock on anything
        return true
    }

    // ── Spaced: the self-tuning loop ────────────────────────────────────────────

    private suspend fun runSpaced(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float) {
        var timings = store.load()
        val engine = vm.engine.value
        _state.value = State(status = Status.WAITING_FOR_MIC, mode = Mode.SPACED, timings = timings, engine = engine,
            message = "Go to the Chords tab; the loop starts when its mic opens")
        if (!awaitMic(vm)) return

        val rounds = ArrayList<RoundReport>()
        val notesPerRound = ChordArpeggioTestTone.CHORDS.sumOf { it.size }
        for (round in 1..MAX_ROUNDS) {
            _state.value = _state.value.copy(status = Status.RUNNING, round = round, timings = timings, message = "Round $round on ${engine.methodName}: $timings",
                notesDone = 0, notesTotal = notesPerRound)
            val chords = ChordArpeggioTestTone.CHORDS.mapIndexed { i, chord ->
                _state.value = _state.value.copy(chordIndex = i)
                playAndObserve(vm, chord, ChordArpeggioTestTone.EXPECTED_SYMBOLS[i], timings, referenceHz)
            }
            _state.value = _state.value.copy(chordIndex = -1)
            val (diagnosis, next, adjustment) = diagnose(chords, timings)
            val report = RoundReport(round, engine, timings, chords, diagnosis, adjustment)
            rounds += report
            if (report.pass) {
                store.save(timings)
                _state.value = State(Status.PASSED, Mode.SPACED, round, timings, rounds, message = "${engine.methodName} passed in round $round. Timings saved: $timings")
                return
            }
            _state.value = _state.value.copy(rounds = rounds.toList())
            timings = next
        }
        _state.value = _state.value.copy(status = Status.FAILED, rounds = rounds.toList(), chordIndex = -1,
            message = "${engine.methodName}: not exact after $MAX_ROUNDS rounds. Last: ${rounds.last().diagnosis}")
    }

    // ── Listen: nothing played, the room recorded ───────────────────────────────

    /**
     * Records [LISTEN_MS] of the selected engine hearing the room with nothing played, as
     * one "chord" whose expected notes are none, so every note it decides is an extra
     * and the frames around each are in the log. For measuring what speech, a cough or
     * the dogs look like to the engine, against what a plucked note looks like.
     */
    private suspend fun runListen(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float) {
        val timings = store.load()
        val engine = vm.engine.value
        _state.value = State(status = Status.WAITING_FOR_MIC, mode = Mode.LISTEN, timings = timings, engine = engine,
            message = "Go to the Chords tab; recording starts when its mic opens")
        if (!awaitMic(vm)) return
        val steps = 10
        _state.value = _state.value.copy(status = Status.RUNNING, round = 1, notesDone = 0, notesTotal = steps,
            message = "Listening on ${engine.methodName} for ${LISTEN_MS / 1000} s. Make some noise.")
        val (timeline, started) = observe(vm, 0L, referenceHz, steps, LISTEN_MS / steps) {
            val t = SystemClock.elapsedRealtime()
            delay(LISTEN_MS)
            t
        }
        val gotNotes = vm.notes.value.sorted()
        val gotSymbol = (vm.reading.value as? ChordReading.Identified)?.best?.symbol
        val result = ChordResult(emptyList(), "(nothing)", gotNotes, gotSymbol, emptyList(), gotNotes,
            exportEvents(timeline, started), exportFrames(timeline, started, referenceHz))
        val decided = result.frames.count { it.note != null }
        val report = RoundReport(1, engine, timings, listOf(result),
            diagnosis = "$decided note(s) decided from the room, ${gotNotes.size} in the set", adjustment = "none")
        _state.value = _state.value.copy(
            status = if (decided == 0) Status.PASSED else Status.FAILED, rounds = listOf(report), chordIndex = -1,
            message = "${engine.methodName}: ${report.diagnosis}",
        )
    }

    // ── Legato: both engines, same playing, side by side ───────────────────────

    private suspend fun runLegato(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float) {
        val timings = store.load()
        val original = vm.engine.value
        _state.value = State(status = Status.WAITING_FOR_MIC, mode = Mode.LEGATO, timings = timings,
            message = "Go to the Chords tab; the comparison starts when its mic opens")
        if (!awaitMic(vm)) return

        val reports = ArrayList<LegatoReport>()
        val notesTotal = ChordArpeggioTestTone.CHORDS.sumOf { it.size } * ChordEngine.entries.size
        _state.value = _state.value.copy(notesDone = 0, notesTotal = notesTotal)
        try {
            for (engine in ChordEngine.entries) {
                if (vm.engine.value != engine) {
                    withContext(Dispatchers.Main) { vm.setEngine(engine) }
                    if (!awaitMic(vm)) return
                }
                _state.value = _state.value.copy(status = Status.RUNNING, engine = engine, round = reports.size + 1,
                    message = "Playing legato to ${engine.methodName}")
                val chords = ChordArpeggioTestTone.CHORDS.mapIndexed { i, chord ->
                    _state.value = _state.value.copy(chordIndex = i)
                    playAndObserveLegato(vm, chord, ChordArpeggioTestTone.EXPECTED_SYMBOLS[i], timings, referenceHz)
                }
                _state.value = _state.value.copy(chordIndex = -1)
                reports += LegatoReport(engine, chords)
                _state.value = _state.value.copy(legato = reports.toList())
            }
        } finally {
            // Also on cancellation: the page must come back on the engine the user chose.
            withContext(NonCancellable + Dispatchers.Main) { vm.setEngine(original) }
        }
        val verdict = legatoVerdict(reports)
        val anyPass = reports.any { it.pass }
        _state.value = _state.value.copy(
            status = if (anyPass) Status.PASSED else Status.FAILED,
            legato = reports, engine = null, chordIndex = -1, message = verdict,
        )
    }

    /** The one-line conclusion, from the numbers. */
    private fun legatoVerdict(reports: List<LegatoReport>): String {
        val byName = reports.associateBy { it.engine }
        val m = byName[ChordEngine.MCLEOD]
        val b = byName[ChordEngine.BROSSIER]
        val lines = reports.joinToString("; ") { "${it.engine.methodName} ${it.summary}" }
        val conclusion = when {
            m == null || b == null -> ""
            b.pass && !m.pass -> "Brossier follows legato playing; McLeod does not."
            m.pass && !b.pass -> "McLeod follows legato playing; Brossier does not."
            b.pass && m.pass -> {
                val bd = b.medianDelayMs; val md = m.medianDelayMs
                if (bd != null && md != null && bd < md) "Both follow legato playing; Brossier is ${md - bd} ms quicker."
                else "Both follow legato playing."
            }
            else -> {
                val bh = b.notesHit; val mh = m.notesHit
                when {
                    bh > mh -> "Neither is exact; Brossier caught more notes ($bh vs $mh)."
                    mh > bh -> "Neither is exact; McLeod caught more notes ($mh vs $bh)."
                    else -> "Neither is exact; see the notes below."
                }
            }
        }
        return "$lines. $conclusion".trim()
    }

    // ── One chord, observed ─────────────────────────────────────────────────────

    private class Timeline {
        val captures = ArrayList<Pair<Long, Int>>()            // (time, midi)
        val readings = ArrayList<Pair<Long, Int>>()            // (time, midi or NO_READING) on every change, nulls included
        val states = ArrayList<Pair<Long, ListeningState>>()   // (time, state) on every change
        val frames = ArrayList<Pair<Long, NoteAnalyzer.Trace>>()   // (time, hop) every hop, onset engine only
    }

    /** Every event in [timeline], timestamped relative to [started], oldest first. For export; not used by fault detection. */
    private fun exportEvents(timeline: Timeline, started: Long): List<TimelineEvent> = synchronized(timeline) {
        val events = ArrayList<TimelineEvent>()
        timeline.captures.forEach { (t, midi) -> events += TimelineEvent(t - started, TimelineEvent.Kind.CAPTURE, NoteNames.labelOf(midi)) }
        timeline.readings.forEach { (t, midi) -> events += TimelineEvent(t - started, TimelineEvent.Kind.READING, if (midi == NO_READING) "none" else NoteNames.labelOf(midi)) }
        timeline.states.forEach { (t, s) -> events += TimelineEvent(t - started, TimelineEvent.Kind.STATE, s.name) }
        events.sortBy { it.tMs }
        events
    }

    private fun exportFrames(timeline: Timeline, started: Long, referenceHz: Float): List<FrameRow> = synchronized(timeline) {
        timeline.frames.map { (t, tr) ->
            val o = tr.observation
            FrameRow(
                tMs = t - started,
                rms = tr.rms, flux = tr.flux, onset = tr.onset,
                pitchHz = tr.pitch?.frequency, pitchClarity = tr.pitch?.clarity,
                residualHz = tr.residual?.frequency, residualClarity = tr.residual?.clarity,
                state = o.state, loud = o.loud, floor = o.floor,
                note = o.note?.let { n -> "${NoteNames.label(n.frequency, referenceHz)}/${n.source}/${"%.2f".format(n.clarity)}${if (n.patient) "/patient" else ""}" },
                newFraction = tr.newFraction.takeIf { !it.isNaN() },
            )
        }
    }

    /**
     * Clear the finder, watch its flows while [play] sounds a chord, and hand back the
     * timeline and the start time. [noteCount] and [slotMs] describe the chord for the
     * progress bar: its notes are credited by the clock from the moment playback is due.
     */
    private suspend fun observe(
        vm: ChordFinderViewModel,
        chordGapMs: Long,
        referenceHz: Float,
        noteCount: Int,
        slotMs: Long,
        play: suspend () -> Long,
    ): Pair<Timeline, Long> {
        withContext(Dispatchers.Main) { vm.clear() }
        _state.value = _state.value.copy(chordStartsAt = SystemClock.elapsedRealtime() + chordGapMs, chordSlotMs = slotMs, chordNotes = noteCount)
        delay(chordGapMs)

        val timeline = Timeline()
        vm.setFrameTrace { tr -> synchronized(timeline) { timeline.frames += SystemClock.elapsedRealtime() to tr } }
        val watchers = listOf(
            scope.launch {
                vm.captured.collect { midi -> synchronized(timeline) { timeline.captures += SystemClock.elapsedRealtime() to midi } }
            },
            scope.launch {
                var last = NO_READING
                vm.heard.collect { rd ->
                    val midi = rd?.let { NoteNames.nearestMidi(it.frequency, referenceHz) } ?: NO_READING
                    if (midi != last) synchronized(timeline) { timeline.readings += SystemClock.elapsedRealtime() to midi }
                    last = midi
                }
            },
            scope.launch {
                var last: ListeningState? = null
                vm.ambient.collect { rep ->
                    if (rep.state != last) synchronized(timeline) { timeline.states += SystemClock.elapsedRealtime() to rep.state }
                    last = rep.state
                }
            },
        )
        val started = try {
            val t = withContext(Dispatchers.IO) { play() }
            delay(SETTLE_MS)
            t
        } finally {
            // Also on cancellation: the watchers live in the object's scope, not this
            // coroutine's, and would otherwise keep collecting the ViewModel's flows forever.
            watchers.forEach { it.cancel() }
            vm.setFrameTrace(null)
        }
        _state.value = _state.value.copy(notesDone = _state.value.notesDone + noteCount, chordStartsAt = 0L, chordSlotMs = 0L, chordNotes = 0)
        return timeline to started
    }

    private suspend fun playAndObserve(
        vm: ChordFinderViewModel,
        chord: List<Int>,
        expectedSymbol: String,
        timings: ChordTestTimings,
        referenceHz: Float,
    ): ChordResult {
        val slot = (timings.noteMs + timings.gapMs).toLong()
        val (timeline, started) = observe(vm, timings.chordGapMs.toLong(), referenceHz, chord.size, slot) {
            ChordArpeggioTestTone.playChord(chord, timings, referenceHz)
        }

        val gotNotes = vm.notes.value.sorted()
        val gotSymbol = (vm.reading.value as? ChordReading.Identified)?.best?.symbol
        val expectedShifted = chord.map { it + timings.octaveShift }

        /** Which note (index) an event at [t] belongs to: the last one that had started, allowing for latency. */
        fun slotOf(t: Long): Int = ((t - started - LATENCY_MS) / slot).toInt().coerceIn(0, chord.lastIndex)

        val results = synchronized(timeline) {
            expectedShifted.mapIndexed { i, expected ->
                val windowStart = started + i * slot
                val windowEnd = windowStart + timings.noteMs + LATENCY_MS
                val capturesHere = timeline.captures.filter { slotOf(it.first) == i }
                // Readings and states are logged on change, so what was already in force when
                // the window opened counts too: a lock still sitting on the previous note is
                // exactly the "held previous" signature, and it never changes inside the window.
                val heardHere = (listOfNotNull(timeline.readings.lastOrNull { it.first < windowStart }?.second) +
                    timeline.readings.filter { it.first in windowStart..windowEnd }.map { it.second })
                    .filter { it != NO_READING }
                val statesHere = (listOfNotNull(timeline.states.lastOrNull { it.first < windowStart }?.second) +
                    timeline.states.filter { it.first in windowStart..windowEnd }.map { it.second }).toSet()
                val hit = capturesHere.firstOrNull { it.second == expected }
                val lateHit = timeline.captures.firstOrNull { it.second == expected && slotOf(it.first) > i }
                val wrongHit = capturesHere.firstOrNull { it.second != expected && it.second !in expectedShifted }
                val prev = expectedShifted.getOrNull(i - 1)

                val fault = when {
                    hit != null -> Fault.OK
                    lateHit != null -> Fault.LATE
                    wrongHit != null && abs(wrongHit.second - expected) == 12 -> Fault.OCTAVE_ERROR
                    wrongHit != null -> Fault.WRONG_PITCH
                    heardHere.any { it == expected } -> Fault.HEARD_NOT_CAPTURED
                    heardHere.any { abs(it - expected) == 12 } -> Fault.OCTAVE_ERROR
                    prev != null && heardHere.any { it == prev } -> Fault.HELD_PREVIOUS
                    statesHere.any { it == ListeningState.ACQUIRING || it == ListeningState.LOCKED } -> Fault.HEARD_NOT_CAPTURED
                    else -> Fault.NOT_HEARD
                }
                NoteResult(
                    expected = expected,
                    fault = fault,
                    captured = (hit ?: lateHit ?: wrongHit)?.second,
                    captureDelayMs = hit?.let { it.first - windowStart },
                    heardMidis = heardHere.distinct(),
                    statesSeen = statesHere,
                )
            }
        }
        val extras = gotNotes.filter { it !in expectedShifted }
        return ChordResult(expectedShifted, expectedSymbol, gotNotes, gotSymbol, results, extras,
            exportEvents(timeline, started), exportFrames(timeline, started, referenceHz))
    }

    /**
     * Legato attribution is by pitch, not by slot: the notes overlap, so a capture belongs
     * to the expected note of that pitch, and its delay is measured from that note's
     * nominal start (the speaker's start-up latency included, the same for both engines).
     */
    private suspend fun playAndObserveLegato(
        vm: ChordFinderViewModel,
        chord: List<Int>,
        expectedSymbol: String,
        timings: ChordTestTimings,
        referenceHz: Float,
    ): ChordResult {
        val step = ChordArpeggioTestTone.LEGATO_STEP_MS.toLong()
        val (timeline, started) = observe(vm, timings.chordGapMs.toLong(), referenceHz, chord.size, step) {
            ChordArpeggioTestTone.playLegato(chord, timings, referenceHz)
        }
        val gotNotes = vm.notes.value.sorted()
        val gotSymbol = (vm.reading.value as? ChordReading.Identified)?.best?.symbol
        val expectedShifted = chord.map { it + timings.octaveShift }

        val results = synchronized(timeline) {
            expectedShifted.mapIndexed { i, expected ->
                val nominalStart = started + i * step
                val nextStart = nominalStart + step
                val hit = timeline.captures.firstOrNull { it.second == expected && it.first >= nominalStart - LEGATO_EARLY_MS }
                val heardAll = timeline.readings.map { it.second }.filter { it != NO_READING }
                val heardHere = (listOfNotNull(timeline.readings.lastOrNull { it.first < nominalStart }?.second) +
                    timeline.readings.filter { it.first in nominalStart..(nextStart + LATENCY_MS) }.map { it.second })
                    .filter { it != NO_READING }
                val statesHere = timeline.states.filter { it.first in nominalStart..(nextStart + LATENCY_MS) }.map { it.second }.toSet()
                val prev = expectedShifted.getOrNull(i - 1)
                val wrong = timeline.captures.firstOrNull { it.first in nominalStart..nextStart && it.second !in expectedShifted }
                val fault = when {
                    hit != null -> Fault.OK
                    wrong != null && abs(wrong.second - expected) == 12 -> Fault.OCTAVE_ERROR
                    wrong != null -> Fault.WRONG_PITCH
                    heardAll.any { it == expected } -> Fault.HEARD_NOT_CAPTURED
                    heardHere.any { abs(it - expected) == 12 } -> Fault.OCTAVE_ERROR
                    prev != null && heardHere.all { it == prev } && heardHere.isNotEmpty() -> Fault.HELD_PREVIOUS
                    heardHere.isNotEmpty() -> Fault.HEARD_NOT_CAPTURED
                    else -> Fault.NOT_HEARD
                }
                NoteResult(
                    expected = expected,
                    fault = fault,
                    captured = (hit ?: wrong)?.second,
                    captureDelayMs = hit?.let { (it.first - nominalStart).coerceAtLeast(0) },
                    heardMidis = heardHere.distinct(),
                    statesSeen = statesHere,
                )
            }
        }
        val extras = gotNotes.filter { it !in expectedShifted }
        return ChordResult(expectedShifted, expectedSymbol, gotNotes, gotSymbol, results, extras,
            exportEvents(timeline, started), exportFrames(timeline, started, referenceHz))
    }

    // ── Diagnosis → next timings (spaced mode) ──────────────────────────────────

    private fun diagnose(chords: List<ChordResult>, t: ChordTestTimings): Triple<String, ChordTestTimings, String> {
        val faults = chords.flatMap { c -> c.notes.map { it.fault } }.filter { it != Fault.OK }
        val extras = chords.sumOf { it.extras.size }
        val nameOnly = chords.any { it.notesOk && !it.pass }

        if (faults.isEmpty() && extras == 0 && !nameOnly) return Triple("All chords exact", t, "none")

        val counts = faults.groupingBy { it }.eachCount()
        val dominant = counts.maxByOrNull { it.value }?.key
        var next = t
        val why = StringBuilder()
        val how = StringBuilder()

        when (dominant) {
            Fault.NOT_HEARD -> {
                why.append("${counts[dominant]} note(s) never registered at the mic")
                next = when {
                    t.amplitude < 1f -> { how.append("level up"); t.copy(amplitude = t.amplitude + 0.15f) }
                    t.noteMs < 2_500 -> { how.append("hold notes longer"); t.copy(noteMs = t.noteMs + 400) }
                    t.octaveShift == 0 -> { how.append("shift up an octave, speaker cannot voice these"); t.copy(octaveShift = 12) }
                    else -> { how.append("hold notes longer still"); t.copy(noteMs = t.noteMs + 400) }
                }
            }
            Fault.HEARD_NOT_CAPTURED -> {
                why.append("${counts[dominant]} note(s) heard but the lock came too late to capture")
                how.append("hold notes longer"); next = t.copy(noteMs = t.noteMs + 400)
            }
            Fault.HELD_PREVIOUS, Fault.LATE -> {
                why.append("${(counts[Fault.HELD_PREVIOUS] ?: 0) + (counts[Fault.LATE] ?: 0)} note(s) arrived while the tuner still held the previous one")
                how.append("widen the gap, lengthen notes a little"); next = t.copy(gapMs = t.gapMs + 400, noteMs = t.noteMs + 200)
            }
            Fault.OCTAVE_ERROR -> {
                why.append("${counts[dominant]} note(s) read an octave off")
                next = if (t.octaveShift == 0) { how.append("shift up an octave"); t.copy(octaveShift = 12) }
                    else { how.append("hold notes longer"); t.copy(noteMs = t.noteMs + 300) }
            }
            Fault.WRONG_PITCH -> {
                why.append("${counts[dominant]} note(s) read as a different pitch")
                how.append("level down a little, hold longer"); next = t.copy(amplitude = t.amplitude - 0.1f, noteMs = t.noteMs + 300)
            }
            else -> {}
        }
        if (extras > 0) {
            if (why.isNotEmpty()) why.append("; ")
            why.append("$extras unexpected note(s) captured")
            if (how.isNotEmpty()) how.append(", ")
            how.append("level down a little, widen the gap")
            next = next.copy(amplitude = next.amplitude - 0.1f, gapMs = next.gapMs + 200)
        }
        if (nameOnly) {
            if (why.isNotEmpty()) why.append("; ")
            why.append("all notes captured but a chord was named differently (theory, not timing)")
        }
        val clamped = next.clamped()
        if (clamped == t && how.isNotEmpty()) how.append(" (already at the limit)")
        return Triple(why.toString(), clamped, how.ifEmpty { StringBuilder("none") }.toString())
    }
}
