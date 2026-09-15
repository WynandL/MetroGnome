package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.AmbientReport
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.theory.ChordReading
import com.example.metrognome.theory.ChordTheory
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which drawn instrument the Chord Finder shows for entering notes by touch. Guitar first: the app was built for a guitarist. */
enum class ChordInstrument(val displayName: String) {
    GUITAR("Guitar"),
    PIANO("Piano"),
}

/**
 * The Chord Finder: collect notes, name the chord.
 *
 * Notes arrive two ways and land in the same set: tapped on the drawn piano or fretboard,
 * or played one at a time into the microphone. The set is real MIDI notes, not pitch
 * classes, because [ChordTheory] needs the lowest note to choose between readings (C E G A
 * is C6 or Am7 depending on what is in the bass).
 *
 * ## The microphone path
 * This owns its own [Tuner] rather than borrowing [TunerViewModel]'s. The tuner's ViewModel
 * ties its engine to the Tuner tab's usage timer, its analytics session and its feedback
 * prompt, none of which should fire because someone is naming a chord. The engine itself
 * is exactly what is wanted: its ambient gate only passes a *steady, sustained* note, so
 * the arpeggio capture below inherits the tuner's speech and noise rejection for free.
 * The tuner's reference pitch and calibration are read from its prefs so both features
 * agree on what "E3" is. Lock telemetry is off by default ([com.example.metrognome.cloud.CloudReportConfig]),
 * so the second engine instance reports nothing.
 *
 * A note is captured when the tuner has held it for [CAPTURE_FRAMES] consecutive readings,
 * and the same note is not captured twice in a row: the player has to let the note stop
 * (the reading goes null) before that pitch counts again. That is what keeps a sustained
 * note from re-adding itself after the user removes it, without a timer.
 *
 * ## Analytics
 * A session is one visit to the tab ([onScreenEntered] / [onScreenLeft]), mirroring the
 * tuner's. A chord counts as named only once its symbol has held for [NAME_HOLD_MS], so a
 * four-note chord entered one note at a time logs the seventh and not the triad it passed
 * through. Notes are counted by where they came from, since "do people play or tap" is the
 * question this feature's design was a bet on.
 *
 * ## Gnotes
 * The same hold credits [MetroItemTracker.recordChordNamed], the Chord Finder's Gnotes
 * counter (5 per chord, 9 chords a day; see PointsConfig/PointsLimits), split by whether
 * the chord's last note was played in or tapped so a later item can tell the two apart.
 *
 * ## Item unlocks
 * The same 1.5 s hold that logs a chord also records it in [MetroItemTracker] as a
 * discovered chord (root + quality only, so "C" and "C/E" are one chord), which is what
 * the Acoustic Guitar item counts. The unlock queue and celebration mirror the rhythm
 * game's, so the popup shows here, on the tab where it was earned.
 *
 * ## What persists
 * The instrument, the collected notes and whether the mic is on all survive leaving the
 * page and restarting the app, so the page reopens exactly as it was left. The mic choice
 * matters most: a user who paused the mic (a noisy room, or tapping notes in on purpose)
 * should not find it listening again on the next visit.
 */
class ChordFinderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tunerPrefs = app.getSharedPreferences("tuner_prefs", Context.MODE_PRIVATE)
    private val tuner = Tuner()
    private val tracker = MetroItemTracker(app)

    private val _notes = MutableStateFlow(loadNotes())
    /** Collected MIDI notes in the order they arrived. */
    val notes: StateFlow<List<Int>> = _notes.asStateFlow()

    /** What the collected notes are, recomputed on every change. */
    val reading: StateFlow<ChordReading> = _notes
        .map { ChordTheory.identify(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChordReading.Empty)

    private val _instrument = MutableStateFlow(loadInstrument())
    val instrument: StateFlow<ChordInstrument> = _instrument.asStateFlow()

    private val _listening = MutableStateFlow(false)
    /** True while the mic is open and notes can be played in. */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _micEnabled = MutableStateFlow(prefs.getBoolean(KEY_MIC_ENABLED, true))
    /** The user's standing choice: should the mic open when the page does? Persisted. */
    val micEnabled: StateFlow<Boolean> = _micEnabled.asStateFlow()

    /** The tuner's live note, for the "hearing E3" readout while listening. */
    val heard: StateFlow<Tuner.Reading?> = tuner.reading
    val amplitude: StateFlow<Float> = tuner.amplitude
    val ambient: StateFlow<AmbientReport> = tuner.ambient

    private val _captured = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    /** Fires with the MIDI note each time the microphone adds one, for the UI to flash it. */
    val captured: SharedFlow<Int> = _captured.asSharedFlow()

    /** The tuner's A4 reference, so a note is named the same way on both screens. */
    val referenceHz: Float get() = tunerPrefs.getFloat("reference_hz", 440f)

    // ── Analytics ───────────────────────────────────────────────────────────────

    private var tapNotes = 0
    private var micNotes = 0
    private var chordsNamed = 0
    private var lastSource = "tap"
    private var lastNamedSymbol: String? = null
    private var nameHoldJob: Job? = null
    private var sessionActive = false

    /** The tab came on screen. */
    fun onScreenEntered() {
        tapNotes = 0; micNotes = 0; chordsNamed = 0
        sessionActive = true
        // A chord restored from the last visit was named then, not now.
        lastNamedSymbol = (reading.value as? ChordReading.Identified)?.best?.symbol
        AnalyticsTracker.logChordsSessionStarted(
            instrument = _instrument.value.name,
            mic = when {
                !hasMicPermission() -> "denied"
                _micEnabled.value -> "on"
                else -> "off"
            },
        )
    }

    /** The tab left the screen. */
    fun onScreenLeft() {
        nameHoldJob?.cancel()
        sessionActive = false
        AnalyticsTracker.logChordsSessionEnded(tapNotes, micNotes, chordsNamed)
    }

    private fun onReadingChanged(reading: ChordReading) {
        nameHoldJob?.cancel()
        // The ViewModel outlives the tab (and is created before it is ever shown, with the
        // persisted notes already loaded), so only a change made on screen counts.
        if (!sessionActive) return
        val best = (reading as? ChordReading.Identified)?.best
        val symbol = best?.symbol
        if (best == null || symbol == null) { lastNamedSymbol = null; return }
        if (symbol == lastNamedSymbol) return
        val noteCount = _notes.value.size
        val source = lastSource
        val discovered = best.rootName + best.type.symbol
        nameHoldJob = viewModelScope.launch {
            delay(NAME_HOLD_MS)
            lastNamedSymbol = symbol
            chordsNamed++
            AnalyticsTracker.logChordNamed(symbol, noteCount, source)
            tracker.recordChordDiscovered(discovered)
            tracker.recordChordNamed(fromMic = source == "mic")
            // Credits Gnotes with no banner (one every few seconds would be intolerable), so
            // the displayed total is nudged the way every continuous earner does it.
            PointsBannerQueue.postSilentEarn()
            checkForNewUnlocks()
        }
    }

    // ── Item unlocks (mirrors RhythmGameViewModel) ──────────────────────────────

    private val _unlockQueue = MutableStateFlow<List<MetroItemEntry>>(emptyList())
    /** Items unlocked on this tab and not yet celebrated, shown one at a time. */
    val unlockQueue: StateFlow<List<MetroItemEntry>> = _unlockQueue.asStateFlow()

    fun checkForNewUnlocks() {
        val unlocked = tracker.unlockedIds(METRO_ITEM_REGISTRY)
        val celebrated = tracker.celebratedIds()
        // Purge: remove entries that are celebrated OR no longer unlocked (e.g. after dev reset)
        _unlockQueue.value = _unlockQueue.value.filter { it.item.id in unlocked && it.item.id !in celebrated }
        val newEntries = METRO_ITEM_REGISTRY.filter { it.item.id in unlocked && it.item.id !in celebrated }
        if (newEntries.isEmpty()) return
        val existing = _unlockQueue.value.map { it.item.id }.toSet()
        val toAdd = newEntries.filter { it.item.id !in existing }
        if (toAdd.isNotEmpty()) {
            _unlockQueue.value += toAdd
            toAdd.forEach { AnalyticsTracker.logItemUnlocked(it.item.id, it.item.displayName) }
        }
    }

    fun markCelebrated(id: String) {
        tracker.markCelebrated(id)
        _unlockQueue.value = _unlockQueue.value.filter { it.item.id != id }
    }

    // ── Notes ───────────────────────────────────────────────────────────────────

    /** Tap on an instrument: add the note, or remove it if it is already there. */
    fun toggleNote(midi: Int) {
        if (midi in _notes.value) removeNote(midi) else addNote(midi)
    }

    fun addNote(midi: Int) {
        if (midi in _notes.value || _notes.value.size >= MAX_NOTES) return
        tapNotes++
        lastSource = "tap"
        setNotes(_notes.value + midi)
    }

    fun removeNote(midi: Int) {
        setNotes(_notes.value - midi)
    }

    fun clear() {
        setNotes(emptyList())
        lastCaptured = null
    }

    private fun setNotes(notes: List<Int>) {
        _notes.value = notes
        prefs.edit { putString(KEY_NOTES, notes.joinToString(",")) }
    }

    private fun loadNotes(): List<Int> =
        prefs.getString(KEY_NOTES, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.distinct()
            ?.take(MAX_NOTES)
            ?: emptyList()

    fun setInstrument(instrument: ChordInstrument) {
        if (instrument == _instrument.value) return
        _instrument.value = instrument
        prefs.edit { putString(KEY_INSTRUMENT, instrument.name) }
        AnalyticsTracker.logChordsInstrumentChanged(instrument.name)
    }

    // ── Microphone ──────────────────────────────────────────────────────────────

    /** Open the mic if RECORD_AUDIO is held. Safe to call when already listening. */
    fun startListening() {
        if (_listening.value || !hasMicPermission()) return
        // Re-read both on every open: the user may have changed the reference pitch or
        // calibrated the tuner since this ViewModel was created.
        tuner.referenceHz = referenceHz
        tuner.calibrationFactor = tunerPrefs.getFloat("calibration_factor", 1f)
        try {
            tuner.start()
        } catch (_: SecurityException) {
            return   // permission revoked between the check and the call
        }
        _listening.value = true
    }

    fun stopListening() {
        if (!_listening.value) return
        tuner.stop()
        _listening.value = false
        pendingMidi = null
        pendingFrames = 0
    }

    /** The user's mic toggle: remembered, and applied at once. */
    fun setMicEnabled(enabled: Boolean) {
        _micEnabled.value = enabled
        prefs.edit { putBoolean(KEY_MIC_ENABLED, enabled) }
        if (enabled) startListening() else stopListening()
    }

    fun toggleMic() = setMicEnabled(!_micEnabled.value)

    // Arpeggio capture state, touched only from the collector below.
    private var pendingMidi: Int? = null
    private var pendingFrames = 0
    private var lastCaptured: Int? = null

    private suspend fun captureFromMic() {
        tuner.reading.collect { rd ->
            if (rd == null) {
                // Silence or an unlocked frame: the next note may be the same pitch again.
                pendingMidi = null
                pendingFrames = 0
                lastCaptured = null
                return@collect
            }
            if (rd.clarity < MIN_CLARITY) return@collect
            val midi = NoteNames.nearestMidi(rd.frequency, tuner.referenceHz)
            if (midi == pendingMidi) pendingFrames++ else { pendingMidi = midi; pendingFrames = 1 }
            if (pendingFrames < CAPTURE_FRAMES || midi == lastCaptured) return@collect
            lastCaptured = midi
            if (midi !in _notes.value && _notes.value.size < MAX_NOTES) {
                micNotes++
                lastSource = "mic"
                setNotes(_notes.value + midi)
                _captured.tryEmit(midi)
            }
        }
    }

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(
        getApplication(), Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    private fun loadInstrument(): ChordInstrument =
        prefs.getString(KEY_INSTRUMENT, null)
            ?.let { runCatching { ChordInstrument.valueOf(it) }.getOrNull() }
            ?: ChordInstrument.GUITAR

    // Last, after every property above has its initial value: viewModelScope.launch on the
    // main thread runs a coroutine up to its first suspension at once, and StateFlow.collect
    // delivers the current value before suspending, so both collectors below touch the
    // capture and session state synchronously from inside this block.
    init {
        tuner.referenceHz = referenceHz
        tuner.calibrationFactor = tunerPrefs.getFloat("calibration_factor", 1f)
        viewModelScope.launch { captureFromMic() }
        viewModelScope.launch { reading.collect { onReadingChanged(it) } }
    }

    override fun onCleared() {
        tuner.stop()
        super.onCleared()
    }

    companion object {
        private const val PREFS = "chord_finder_prefs"
        private const val KEY_INSTRUMENT = "instrument"
        private const val KEY_NOTES = "notes"
        private const val KEY_MIC_ENABLED = "mic_enabled"

        /** More than this and nothing in the dictionary can name it anyway. */
        const val MAX_NOTES = 8

        /**
         * Consecutive tuner readings (about 90 ms each) on one note before it is added, on
         * top of the ambient gate's own 320 ms acquire. Rejects the frame or two of a wrong
         * octave a plucked string sometimes gives before it settles.
         */
        private const val CAPTURE_FRAMES = 3

        /** Below this the reading is too impure to trust as a deliberate note. */
        private const val MIN_CLARITY = 0.6f

        /** A chord name must hold this long before it is logged as named. */
        private const val NAME_HOLD_MS = 1_500L
    }
}
