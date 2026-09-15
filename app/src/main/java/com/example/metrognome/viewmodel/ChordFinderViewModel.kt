package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.AmbientReport
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.theory.ChordReading
import com.example.metrognome.theory.ChordTheory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which drawn instrument the Chord Finder shows for entering notes by touch. */
enum class ChordInstrument(val displayName: String) {
    PIANO("Piano"),
    GUITAR("Guitar"),
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
 */
class ChordFinderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tunerPrefs = app.getSharedPreferences("tuner_prefs", Context.MODE_PRIVATE)
    private val tuner = Tuner()

    private val _notes = MutableStateFlow<List<Int>>(emptyList())
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

    /** The tuner's live note, for the "hearing E3" readout while listening. */
    val heard: StateFlow<Tuner.Reading?> = tuner.reading
    val amplitude: StateFlow<Float> = tuner.amplitude
    val ambient: StateFlow<AmbientReport> = tuner.ambient

    private val _captured = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    /** Fires with the MIDI note each time the microphone adds one, for the UI to flash it. */
    val captured: SharedFlow<Int> = _captured.asSharedFlow()

    /** The tuner's A4 reference, so a note is named the same way on both screens. */
    val referenceHz: Float get() = tunerPrefs.getFloat("reference_hz", 440f)

    init {
        tuner.referenceHz = referenceHz
        tuner.calibrationFactor = tunerPrefs.getFloat("calibration_factor", 1f)
        viewModelScope.launch { captureFromMic() }
    }

    // ── Notes ───────────────────────────────────────────────────────────────────

    /** Tap on an instrument: add the note, or remove it if it is already there. */
    fun toggleNote(midi: Int) {
        if (midi in _notes.value) removeNote(midi) else addNote(midi)
    }

    fun addNote(midi: Int) {
        if (midi in _notes.value || _notes.value.size >= MAX_NOTES) return
        _notes.value = _notes.value + midi
    }

    fun removeNote(midi: Int) {
        _notes.value = _notes.value - midi
    }

    fun clear() {
        _notes.value = emptyList()
        lastCaptured = null
    }

    fun setInstrument(instrument: ChordInstrument) {
        _instrument.value = instrument
        prefs.edit { putString(KEY_INSTRUMENT, instrument.name) }
    }

    // ── Microphone ──────────────────────────────────────────────────────────────

    /** Open the mic if RECORD_AUDIO is held. Safe to call when already listening. */
    fun startListening() {
        if (_listening.value || !hasMicPermission()) return
        tuner.referenceHz = referenceHz
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

    fun toggleListening() = if (_listening.value) stopListening() else startListening()

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
                _notes.value = _notes.value + midi
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
            ?: ChordInstrument.PIANO

    override fun onCleared() {
        tuner.stop()
        super.onCleared()
    }

    companion object {
        private const val PREFS = "chord_finder_prefs"
        private const val KEY_INSTRUMENT = "instrument"

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
    }
}
