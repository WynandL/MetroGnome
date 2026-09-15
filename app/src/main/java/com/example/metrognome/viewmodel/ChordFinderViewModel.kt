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
import com.example.metrognome.audio.chords.ChordPlaybackPace
import com.example.metrognome.audio.chords.ChordPlaybackPlan
import com.example.metrognome.audio.chords.ChordPlayer
import com.example.metrognome.audio.chords.ChordVoice
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * **A new arpeggio starts from the bottom.** From the notes alone, "the next chord" and
 * "an extension of this one" are the same stream: C E G then B is Cmaj7, C E G then G B D
 * reads as Cmaj9 and is right about the notes it was given. The boundary has to come from
 * outside the notes, and the one signal that costs the player nothing is the convention
 * the page already teaches: the lowest note is the bass. So a mic-captured note *below* the
 * current bass, once the set holds [RESTART_MIN_NOTES] notes, starts a new set with that
 * note. Extensions live on top, so they still extend; the one miss is adding a lower bass
 * for a slash chord, which a tap handles. Tapped notes are exempt, since a tap is
 * deliberate. A silence timer was considered and rejected: pausing to think about the
 * seventh must not cost the triad.
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
 * ## Hearing it back
 * [hearChord] plays the collected notes as an arpeggio from the bass up and then together,
 * through [ChordPlayer] with [ChordVoice] (the sound of the web chord finder's "hear it"),
 * at the user's [playbackPace]. The mic is not closed for it, unlike for the drone: a
 * two-second phrase is not worth a permission round trip. Instead [captureFromMic] ignores
 * readings while the phrase sounds and for [PLAYBACK_MUTE_TAIL_MS] after, since the
 * closing chord is exactly the kind of steady sound the tuner would otherwise lock on and
 * feed back into the set, usually as a wrong octave.
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

    private val player = ChordPlayer()
    /** True while "hear it" is sounding; the key shows stop. */
    val playing: StateFlow<Boolean> = player.playing

    private val _playbackPace = MutableStateFlow(loadPace())
    /** How fast "hear it" walks through the chord. Persisted. */
    val playbackPace: StateFlow<ChordPlaybackPace> = _playbackPace.asStateFlow()

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

    /**
     * DEV ONLY, set by the Chord Loop diagnostic while it plays test chords through the
     * speaker: chords named during a run are not logged, credited or counted toward the
     * guitar, so a diagnostic cannot farm Gnotes or muddy analytics from a developer phone.
     */
    @Volatile var diagnosticMode = false

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
        player.stop()
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
            if (diagnosticMode) return@launch   // a dev loop's chords earn and log nothing
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

    // ── Hearing it back ─────────────────────────────────────────────────────────

    private var playJob: Job? = null
    @Volatile private var captureMuted = false

    /** Cut a sounding "hear it" short. Safe when nothing is sounding. */
    fun stopChord() = player.stop()

    /** Play the collected notes as an arpeggio, then together. No-op with nothing to play; a tap while sounding stops instead. */
    fun hearChord() {
        val notes = _notes.value
        if (notes.isEmpty()) return
        if (playJob?.isActive == true) { stopChord(); return }
        val pace = _playbackPace.value
        val symbol = (reading.value as? ChordReading.Identified)?.best?.symbol
        AnalyticsTracker.logChordPlayed(symbol, notes.size, pace.name)
        playJob = viewModelScope.launch {
            captureMuted = true
            try {
                withContext(Dispatchers.IO) {
                    val events = ChordPlaybackPlan.arpeggioThenChord(notes, pace)
                    player.play(ChordVoice.render(events, referenceHz))
                }
                delay(PLAYBACK_MUTE_TAIL_MS)
            } finally {
                captureMuted = false
                pendingMidi = null
                pendingFrames = 0
            }
        }
    }

    fun setPlaybackPace(pace: ChordPlaybackPace) {
        if (pace == _playbackPace.value) return
        _playbackPace.value = pace
        prefs.edit { putString(KEY_PACE, pace.name) }
    }

    private fun loadPace(): ChordPlaybackPace =
        prefs.getString(KEY_PACE, null)
            ?.let { runCatching { ChordPlaybackPace.valueOf(it) }.getOrNull() }
            ?: ChordPlaybackPace.QUICK

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
            if (captureMuted) {
                // The speaker is sounding the chord; nothing heard now is the player's.
                pendingMidi = null
                pendingFrames = 0
                return@collect
            }
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
            val current = _notes.value
            val bass = current.minOrNull()
            if (bass != null && midi < bass && current.size >= RESTART_MIN_NOTES) {
                // Below the bass of a set that is already a chord: a new arpeggio has begun.
                micNotes++
                lastSource = "mic"
                setNotes(listOf(midi))
                _captured.tryEmit(midi)
            } else if (midi !in current && current.size < MAX_NOTES) {
                micNotes++
                lastSource = "mic"
                setNotes(current + midi)
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
        player.stop()
        tuner.stop()
        super.onCleared()
    }

    companion object {
        private const val PREFS = "chord_finder_prefs"
        private const val KEY_INSTRUMENT = "instrument"
        private const val KEY_NOTES = "notes"
        private const val KEY_MIC_ENABLED = "mic_enabled"
        private const val KEY_PACE = "playback_pace"

        /** How long after "hear it" ends the mic keeps ignoring readings, for the tuner's lock to let go of the last chord. */
        private const val PLAYBACK_MUTE_TAIL_MS = 600L

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

        /**
         * Once the set holds this many notes, a mic note below its bass starts a new set.
         * Three, not "is named": a set that came out wrong is exactly what the player wants
         * to start over from, and two notes plus a lower one is a bass added last (E G,
         * then C: C major), not a restart.
         */
        private const val RESTART_MIN_NOTES = 3
    }
}
