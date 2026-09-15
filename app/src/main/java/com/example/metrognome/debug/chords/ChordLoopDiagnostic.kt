package com.example.metrognome.debug.chords

import android.os.SystemClock
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.theory.ChordReading
import com.example.metrognome.viewmodel.ChordFinderViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * DEV ONLY: a closed loop that plays the test chords through the speaker, watches what the
 * Chord Finder captures through the microphone, and adjusts the playback timings until
 * every chord comes back exactly as expected. The result is stored per device in
 * [ChordTestTimingsStore], so the plain "Play Test Chords" button works first time after.
 *
 * ## Why a loop and not a table
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

    enum class Fault { OK, NOT_HEARD, HEARD_NOT_CAPTURED, HELD_PREVIOUS, LATE, OCTAVE_ERROR, WRONG_PITCH }

    data class NoteResult(
        val expected: Int,
        val fault: Fault,
        val captured: Int?,
        val captureDelayMs: Long?,
        val heardMidis: List<Int>,
        val statesSeen: Set<ListeningState>,
    )

    data class ChordResult(
        val expectedNotes: List<Int>,
        val expectedSymbol: String,
        val gotNotes: List<Int>,
        val gotSymbol: String?,
        val notes: List<NoteResult>,
        val extras: List<Int>,
    ) {
        val notesOk get() = notes.all { it.fault == Fault.OK } && extras.isEmpty()
        val pass get() = notesOk && gotSymbol == expectedSymbol
    }

    data class RoundReport(
        val round: Int,
        val timings: ChordTestTimings,
        val chords: List<ChordResult>,
        val diagnosis: String,
        val adjustment: String,
    ) {
        val pass get() = chords.all { it.pass }
    }

    data class State(
        val status: Status = Status.IDLE,
        val round: Int = 0,
        val timings: ChordTestTimings = ChordTestTimings.DEFAULT,
        val rounds: List<RoundReport> = emptyList(),
        val message: String = "",
    )

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

    /** Marker in the readings timeline for "the tuner reported nothing". */
    private const val NO_READING = -1

    val isRunning: Boolean get() = job?.isActive == true

    /** Start a run. Returns false if one is already going. */
    fun start(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float): Boolean {
        if (isRunning) return false
        job = scope.launch { run(vm, store, referenceHz) }
        return true
    }

    fun cancel() {
        job?.cancel()
        ChordArpeggioTestTone.stop()
        _state.value = _state.value.copy(status = Status.IDLE, message = "Cancelled")
    }

    private suspend fun run(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float) {
        vm.diagnosticMode = true
        try {
            runLoop(vm, store, referenceHz)
        } finally {
            vm.diagnosticMode = false
        }
    }

    private suspend fun runLoop(vm: ChordFinderViewModel, store: ChordTestTimingsStore, referenceHz: Float) {
        var timings = store.load()
        _state.value = State(status = Status.WAITING_FOR_MIC, timings = timings, message = "Go to the Chords tab; the loop starts when its mic opens")

        val listening = withTimeoutOrNull(MIC_WAIT_MS) { vm.listening.first { it } } ?: false
        if (!listening) {
            _state.value = _state.value.copy(status = Status.FAILED, message = "Mic never opened: open the Chords tab with the mic on, then run again")
            return
        }
        delay(ROOM_PROFILE_MS)   // the tuner profiles the room before it will lock on anything

        val rounds = ArrayList<RoundReport>()
        for (round in 1..MAX_ROUNDS) {
            _state.value = _state.value.copy(status = Status.RUNNING, round = round, timings = timings, message = "Round $round: $timings")
            val chords = ChordArpeggioTestTone.CHORDS.mapIndexed { i, chord ->
                playAndObserve(vm, chord, ChordArpeggioTestTone.EXPECTED_SYMBOLS[i], timings, referenceHz)
            }
            val (diagnosis, next, adjustment) = diagnose(chords, timings)
            val report = RoundReport(round, timings, chords, diagnosis, adjustment)
            rounds += report
            if (report.pass) {
                store.save(timings)
                _state.value = State(Status.PASSED, round, timings, rounds, "Passed in round $round. Timings saved: $timings")
                return
            }
            _state.value = _state.value.copy(rounds = rounds.toList())
            timings = next
        }
        _state.value = _state.value.copy(status = Status.FAILED, rounds = rounds.toList(),
            message = "Not exact after $MAX_ROUNDS rounds. Last: ${rounds.last().diagnosis}")
    }

    // ── One chord, observed ─────────────────────────────────────────────────────

    private class Timeline {
        val captures = ArrayList<Pair<Long, Int>>()            // (time, midi)
        val readings = ArrayList<Pair<Long, Int>>()            // (time, midi or NO_READING) on every change, nulls included
        val states = ArrayList<Pair<Long, ListeningState>>()   // (time, state) on every change
    }

    private suspend fun playAndObserve(
        vm: ChordFinderViewModel,
        chord: List<Int>,
        expectedSymbol: String,
        timings: ChordTestTimings,
        referenceHz: Float,
    ): ChordResult {
        withContext(Dispatchers.Main) { vm.clear() }
        delay(timings.chordGapMs.toLong())

        val timeline = Timeline()
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
            val t = withContext(Dispatchers.IO) { ChordArpeggioTestTone.playChord(chord, timings, referenceHz) }
            delay(SETTLE_MS)
            t
        } finally {
            // Also on cancellation: the watchers live in the object's scope, not this
            // coroutine's, and would otherwise keep collecting the ViewModel's flows forever.
            watchers.forEach { it.cancel() }
        }

        val gotNotes = vm.notes.value.sorted()
        val gotSymbol = (vm.reading.value as? ChordReading.Identified)?.best?.symbol
        val expectedShifted = chord.map { it + timings.octaveShift }
        val slot = (timings.noteMs + timings.gapMs).toLong()

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
        return ChordResult(expectedShifted, expectedSymbol, gotNotes, gotSymbol, results, extras)
    }

    // ── Diagnosis → next timings ────────────────────────────────────────────────

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
