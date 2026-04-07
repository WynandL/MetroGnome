package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.MetronomeEngine
import com.example.metrognome.audio.RhythmDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.core.content.edit

// ── Enums ──────────────────────────────────────────────────────────────────────

enum class GamePhase { IDLE, COUNTDOWN, PLAYING, RESULT }

/** Lifecycle of a single note. HIT and MISSED are terminal states. */
enum class NoteState { UPCOMING, ACTIVE, HIT, MISSED }

/** Judgement given for a tap. */
enum class HitQuality { PERFECT, GOOD, ALMOST, MISS, NONE }

// ── Data classes ───────────────────────────────────────────────────────────────

/**
 * A single note in the rhythm game.
 *
 * Invariants (never violated):
 *   hitTimeMs  == targetBeat * beatIntervalMs
 *   spawnTimeMs == hitTimeMs - NOTE_TRAVEL_MS
 *   state transitions: UPCOMING → ACTIVE → HIT | MISSED (terminal)
 */
data class Note(
    val targetBeat: Int,
    val hitTimeMs: Long,   // targetBeat * beatIntervalMs
    val spawnTimeMs: Long,   // hitTimeMs  - NOTE_TRAVEL_MS
    var state: NoteState = NoteState.UPCOMING
)

/**
 * Render-only snapshot of a note for the UI.
 *
 * progress = (songTimeMs - spawnTimeMs) / NOTE_TRAVEL_MS
 *   0.0 → just spawned (top of lane)
 *   1.0 → at hit line
 *   >1.0 → past hit line
 *
 * Position is ONLY for rendering — hit detection uses time, not position.
 */
data class RenderNote(
    val id: Int,
    val progress: Float,
    val state: NoteState
)

data class GameResult(
    val score: Int,
    val maxCombo: Int,
    val perfects: Int,
    val goods: Int,
    val almosts: Int,
    val misses: Int,
    val isNewHighScore: Boolean = false
)

// ── Constants ─────────────────────────────────────────────────────────────────

/** Canonical difficulty names — used as SharedPreferences keys. */
val DIFFICULTY_NAMES = listOf("Beginner", "Easy", "Medium", "Hard", "Expert")

/** Base timing windows in ms. Multiplied by the user's tolerance setting. */
const val PERFECT_WINDOW_MS = 50L
const val GOOD_WINDOW_MS = 100L
const val MISS_WINDOW_MS = 150L

/** How long a note travels from spawn to the hit line. */
const val NOTE_TRAVEL_MS = 2000L

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RhythmGameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("rhythm_highscores", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    val detector = RhythmDetector()

    // ── Public state flows ────────────────────────────────────────────────────

    private val _phase = MutableStateFlow(GamePhase.IDLE)
    private val _score = MutableStateFlow(0)
    private val _combo = MutableStateFlow(0)
    private val _countDown = MutableStateFlow(3)
    private val _currentBeat = MutableStateFlow(0)
    private val _bpm = MutableStateFlow(80)
    private val _timeSig = MutableStateFlow(4)
    private val _lastQuality = MutableStateFlow(HitQuality.NONE)
    private val _result = MutableStateFlow<GameResult?>(null)
    private val _useMic = MutableStateFlow(false)
    private val _lastHitOffset = MutableStateFlow(0L)
    private val _beatsRemaining = MutableStateFlow(0)
    private val _tolerance = MutableStateFlow(1.5f)
    private val _highScores = MutableStateFlow(loadHighScores())

    /** Render-ready note list updated at ~60 fps by the game loop. */
    private val _visibleNotes = MutableStateFlow<List<RenderNote>>(emptyList())

    val phase: StateFlow<GamePhase> = _phase.asStateFlow()
    val score: StateFlow<Int> = _score.asStateFlow()
    val combo: StateFlow<Int> = _combo.asStateFlow()
    val countDown: StateFlow<Int> = _countDown.asStateFlow()
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()
    val bpm: StateFlow<Int> = _bpm.asStateFlow()
    val timeSig: StateFlow<Int> = _timeSig.asStateFlow()
    val lastQuality: StateFlow<HitQuality> = _lastQuality.asStateFlow()
    val result: StateFlow<GameResult?> = _result.asStateFlow()
    val useMic: StateFlow<Boolean> = _useMic.asStateFlow()
    val lastHitOffset: StateFlow<Long> = _lastHitOffset.asStateFlow()
    val beatsRemaining: StateFlow<Int> = _beatsRemaining.asStateFlow()
    val tolerance: StateFlow<Float> = _tolerance.asStateFlow()
    val highScores: StateFlow<Map<String, Int>> = _highScores.asStateFlow()
    val visibleNotes: StateFlow<List<RenderNote>> = _visibleNotes.asStateFlow()

    /** Live mic amplitude 0..1. Non-zero only while mic mode is active. */
    val micAmplitude: StateFlow<Float> = detector.amplitude

    private val _beatPulse = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val beatPulse: SharedFlow<Int> = _beatPulse.asSharedFlow()

    /**
     * Fires on every microphone onset detection — regardless of timing.
     * Paired with [lastQuality]: raw flash = mic heard something;
     * quality change = it was scored.
     */
    private val _micDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val micDetected: SharedFlow<Unit> = _micDetected.asSharedFlow()

    // ── Internal state ────────────────────────────────────────────────────────

    /** Monotonic clock value at game start (SystemClock.elapsedRealtime). */
    private var gameStartElapsedMs = 0L

    /** Wall-clock value at game start (System.currentTimeMillis). Used for mic ts conversion. */
    private var gameStartWallMs = 0L

    private var intervalMs = 750L
    private var totalBeats = 32
    private var maxCombo = 0
    private var countPerfect = 0;
    private var countGood = 0
    private var countBad = 0;
    private var countMiss = 0

    /**
     * Tolerance-scaled timing windows, fixed for the duration of a game session.
     * Computed once in beginPlay() so tick() and processTap() always agree.
     */
    private var winPerfect = PERFECT_WINDOW_MS
    private var winGood = GOOD_WINDOW_MS
    private var winMiss = MISS_WINDOW_MS

    /** The full note sequence for the current game. Mutated by the game loop on Main. */
    private val notes = mutableListOf<Note>()

    private var currentDifficultyName = ""

    private var countdownJob: Job? = null
    private var engineStartJob: Job? = null
    private var gameLoopJob: Job? = null
    private var micJob: Job? = null

    init {
        engine.onBeat = { beat ->
            viewModelScope.launch {
                _currentBeat.value = beat
                _beatPulse.emit(beat)
                // Suppress mic 60 ms after each click to block click-pickup.
                if (_phase.value == GamePhase.PLAYING && _useMic.value) {
                    detector.suppressUntilMs = System.currentTimeMillis() + 60L
                }
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun setDifficulty(levelBpm: Int, beats: Int = 32, name: String = "") {
        _bpm.value = levelBpm
        totalBeats = beats
        currentDifficultyName = name
    }

    /** Timing tolerance multiplier. 0.5 = strict, 1.5 = default, 2.5 = very easy. */
    fun setTolerance(v: Float) {
        _tolerance.value = v.coerceIn(0.5f, 2.5f)
    }

    fun toggleMic(on: Boolean) {
        _useMic.value = on
    }

    fun startGame() {
        reset()
        _phase.value = GamePhase.COUNTDOWN
        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _countDown.value = i; delay(1000)
            }
            beginPlay()
        }
    }

    /** Called on screen tap or button press. tapTimeMs = songTimeMs at tap. */
    fun onScreenTap() {
        if (_phase.value != GamePhase.PLAYING) return
        processTap(songTimeMs())
    }

    fun dismissResult() {
        _phase.value = GamePhase.IDLE
        _result.value = null
    }

    fun stopGame() {
        cancelJobs()
        engine.stop(); detector.stop()
        _phase.value = GamePhase.IDLE
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun songTimeMs(): Long = SystemClock.elapsedRealtime() - gameStartElapsedMs

    private fun beginPlay() {
        val bpm = _bpm.value
        intervalMs = (60_000.0 / bpm).toLong()
        _beatsRemaining.value = totalBeats

        // Compute tolerance-scaled windows once for this session (Fix 1).
        // Both tick() and processTap() read these — they can never disagree.
        val tol = _tolerance.value
        winPerfect = (PERFECT_WINDOW_MS * tol).toLong()
        winGood = (GOOD_WINDOW_MS * tol).toLong()
        winMiss = (MISS_WINDOW_MS * tol).toLong()

        // Pre-generate all notes with a NOTE_TRAVEL_MS lead-in offset (Fix 2).
        //
        // Without offset: beat 0 hitTimeMs = 0  → already at hit line at game start.
        // With    offset: beat 0 hitTimeMs = NOTE_TRAVEL_MS → note spawns at top of lane
        //                 at songT=0 and travels the full lane before the click fires.
        //
        // Invariant preserved: delta between consecutive hitTimes == beatIntervalMs.
        notes.clear()
        repeat(totalBeats) { beat ->
            val hitTime = NOTE_TRAVEL_MS + beat.toLong() * intervalMs
            notes.add(
                Note(
                    targetBeat = beat,
                    hitTimeMs = hitTime,
                    spawnTimeMs = hitTime - NOTE_TRAVEL_MS   // == beat * intervalMs  (≥ 0 always)
                )
            )
        }

        engine.bpm = bpm
        engine.timeSignature = _timeSig.value
        engine.accentFirst = true
        engine.soundType = 0

        // Mic starts immediately — gives it NOTE_TRAVEL_MS to calibrate noise floor.
        // Only start if RECORD_AUDIO permission is actually held; the UI may have set
        // useMic=true before the user fully granted the permission.
        val micReady = _useMic.value && ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (micReady) {
            detector.start()
            micJob = viewModelScope.launch {
                detector.detections.collect { wallTs ->
                    _micDetected.tryEmit(Unit)
                    if (_phase.value == GamePhase.PLAYING) {
                        processTap(wallTs - gameStartWallMs)
                    }
                }
            }
        }

        // Record game start on the monotonic clock and wall clock simultaneously.
        gameStartElapsedMs = SystemClock.elapsedRealtime()
        gameStartWallMs = System.currentTimeMillis()
        _phase.value = GamePhase.PLAYING

        // Delay engine start by NOTE_TRAVEL_MS so the click fires exactly when
        // each note reaches the hit line — metronome and visuals stay in sync.
        engineStartJob = viewModelScope.launch {
            delay(NOTE_TRAVEL_MS)
            engine.start()
        }

        // Main game loop: updates note states and produces render snapshots at ~60 fps.
        // All coroutines share viewModelScope (Main thread) so there is no shared-state race.
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val songT = songTimeMs()
                tick(songT)

                if (notes.all { it.state == NoteState.HIT || it.state == NoteState.MISSED }) {
                    endGame()
                    break
                }
                delay(16L)   // ~60 fps; wall-clock, not frame count
            }
        }
    }

    /**
     * One game-loop tick:
     *  1. Transition UPCOMING → ACTIVE when spawnTimeMs reached.
     *  2. Transition ACTIVE → MISSED when miss window has passed.
     *  3. Build render list (ACTIVE / recently-MISSED notes with position).
     */
    private fun tick(songT: Long) {
        var remaining = 0
        val renderList = mutableListOf<RenderNote>()

        for ((i, note) in notes.withIndex()) {
            when (note.state) {
                NoteState.UPCOMING -> {
                    if (songT >= note.spawnTimeMs) note.state = NoteState.ACTIVE
                }

                NoteState.ACTIVE -> {
                    if (songT > note.hitTimeMs + winMiss) {
                        note.state = NoteState.MISSED
                        recordMiss()
                    }
                }

                NoteState.HIT, NoteState.MISSED -> Unit   // terminal — no transition
            }

            if (note.state == NoteState.UPCOMING || note.state == NoteState.ACTIVE) remaining++

            // Include ACTIVE and briefly-past MISSED notes in the render list.
            // HIT notes are not rendered (quality flash at hit line covers the feedback).
            if (note.state == NoteState.ACTIVE || note.state == NoteState.MISSED) {
                val progress = (songT - note.spawnTimeMs).toFloat() / NOTE_TRAVEL_MS.toFloat()
                if (progress < 1.8f) {   // cull once well past the hit line
                    renderList.add(RenderNote(i, progress.coerceAtLeast(0f), note.state))
                }
            }
        }

        _beatsRemaining.value = remaining
        _visibleNotes.value = renderList
    }

    /**
     * On tap event (screen touch or mic onset):
     *  - Find the closest ACTIVE note to [tapSongTimeMs].
     *  - Compute delta; apply timing windows.
     *  - If within missWindow → mark HIT and score; otherwise ignore tap.
     *
     * One tap can hit at most one note (spec §13).
     */
    private fun processTap(tapSongTimeMs: Long) {
        // Use session windows — same values that tick() uses for auto-miss (Fix 1).
        val candidate = notes
            .filter { it.state == NoteState.ACTIVE }
            .minByOrNull { abs(tapSongTimeMs - it.hitTimeMs) }
            ?: return   // no active note → empty tap, no effect

        val delta = tapSongTimeMs - candidate.hitTimeMs   // +late, −early
        val absDelta = abs(delta)
        _lastHitOffset.value = delta

        // Spec §9: hit judgement
        val quality = when {
            absDelta <= winPerfect -> HitQuality.PERFECT
            absDelta <= winGood -> HitQuality.GOOD
            absDelta <= winMiss -> HitQuality.ALMOST
            else -> HitQuality.NONE  // NOT A HIT — do not assign note
        }

        if (quality == HitQuality.NONE) return   // too far from any note

        candidate.state = NoteState.HIT   // mark terminal HIT

        val newCombo = _combo.value + 1
        _combo.value = newCombo
        if (newCombo > maxCombo) maxCombo = newCombo

        _score.value += when (quality) {
            HitQuality.PERFECT -> 100
            HitQuality.GOOD -> 70
            HitQuality.ALMOST -> 30
            else -> 0
        }

        _lastQuality.value = quality
        when (quality) {
            HitQuality.PERFECT -> countPerfect++
            HitQuality.GOOD -> countGood++
            HitQuality.ALMOST -> countBad++
            else -> Unit
        }

        viewModelScope.launch {
            delay(650)
            if (_lastQuality.value == quality) _lastQuality.value = HitQuality.NONE
        }
    }

    /** Called when the miss window expires on an un-tapped note. */
    private fun recordMiss() {
        countMiss++
        _combo.value = 0
        _lastQuality.value = HitQuality.MISS
        viewModelScope.launch {
            delay(600)
            if (_lastQuality.value == HitQuality.MISS) _lastQuality.value = HitQuality.NONE
        }
    }

    private fun endGame() {
        if (_phase.value == GamePhase.RESULT) return
        engine.stop(); detector.stop()
        val finalScore = _score.value
        val isNew = currentDifficultyName.isNotEmpty() &&
                finalScore > (_highScores.value[currentDifficultyName] ?: 0)
        if (isNew) {
            _highScores.value = _highScores.value.toMutableMap()
                .also { it[currentDifficultyName] = finalScore }
            prefs.edit { putInt("hs_$currentDifficultyName", finalScore) }
        }
        _result.value =
            GameResult(finalScore, maxCombo, countPerfect, countGood, countBad, countMiss, isNew)
        _phase.value = GamePhase.RESULT
    }

    private fun reset() {
        cancelJobs()
        engine.stop(); detector.stop()
        _score.value = 0
        _combo.value = 0
        _currentBeat.value = 0
        _lastQuality.value = HitQuality.NONE
        _lastHitOffset.value = 0L
        _result.value = null
        _visibleNotes.value = emptyList()
        _beatsRemaining.value = 0
        notes.clear()
        maxCombo = 0
        countPerfect = 0; countGood = 0; countBad = 0; countMiss = 0
    }

    private fun cancelJobs() {
        countdownJob?.cancel(); engineStartJob?.cancel()
        gameLoopJob?.cancel(); micJob?.cancel()
    }

    private fun loadHighScores(): Map<String, Int> =
        DIFFICULTY_NAMES.associateWith { name -> prefs.getInt("hs_$name", 0) }

    override fun onCleared() {
        engine.stop(); detector.stop(); super.onCleared()
    }
}
