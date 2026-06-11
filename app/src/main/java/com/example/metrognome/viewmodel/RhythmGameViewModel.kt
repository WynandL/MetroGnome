package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.metronome.MetronomeEngine
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.example.metrognome.audio.rhythm.RhythmDetector
import com.example.metrognome.audio.selftest.MicCalibration
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
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
import com.example.metrognome.BuildConfig
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.debug.mic.MicDiagnosticsBuffer

// ── Enums ──────────────────────────────────────────────────────────────────────

enum class GamePhase { IDLE, COUNTDOWN, PLAYING, RESULT }

/** Lifecycle of a single note. HIT and MISSED are terminal states. */
enum class NoteState { UPCOMING, ACTIVE, HIT, MISSED }

/** Judgment given for a tap. */
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

/** Base timing windows in ms (screen taps). Multiplied by the user's tolerance setting. */
const val PERFECT_WINDOW_MS = 50L
const val GOOD_WINDOW_MS = 100L
const val MISS_WINDOW_MS = 150L

/**
 * Wider, fixed windows used when the microphone scores claps. Clapping cannot hit a tap-tight
 * window reliably, so being roughly on the beat must still count. Not tolerance-scaled.
 */
const val MIC_PERFECT_WINDOW_MS = 110L
const val MIC_GOOD_WINDOW_MS = 190L
const val MIC_MISS_WINDOW_MS = 290L

/** How long a note travels from spawn to the hit line. */
const val NOTE_TRAVEL_MS = 2000L

/**
 * Extra grace before an un-tapped note auto-misses, applied in mic mode only.
 * A mic onset is delivered up to one audio buffer + a scheduling hop after it
 * actually occurred; without this grace a valid late-window hit could be
 * auto-missed before its detection is ever delivered to [RhythmGameViewModel].
 */
const val MIC_MISS_GRACE_MS = 120L

/** After a hit, extra taps within this window are forgiven as double-tap twitch. */
const val STRAY_TAP_GRACE_MS = 140L

// ── ViewModel ─────────────────────────────────────────────────────────────────

class RhythmGameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("rhythm_highscores", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    private val itemTracker = MetroItemTracker(app)
    private val dailyLog    = com.example.metrognome.points.DailyActivityLog(app)
    val detector = RhythmDetector(classifyClaps = true)

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
    // Fixed timing-window multiplier (1.5 = default). No user-facing tolerance control is
    // wired, so this is a constant rather than adjustable state.
    private val toleranceMultiplier = 1.5f
    private val _highScores = MutableStateFlow(loadHighScores())

    /** Render-ready note list updated at ~60 fps by the game loop. */
    private val _visibleNotes = MutableStateFlow<List<RenderNote>>(emptyList())

    val phase: StateFlow<GamePhase> = _phase.asStateFlow()
    val score: StateFlow<Int> = _score.asStateFlow()
    val combo: StateFlow<Int> = _combo.asStateFlow()
    val countDown: StateFlow<Int> = _countDown.asStateFlow()
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()
    val timeSig: StateFlow<Int> = _timeSig.asStateFlow()
    val lastQuality: StateFlow<HitQuality> = _lastQuality.asStateFlow()
    val result: StateFlow<GameResult?> = _result.asStateFlow()
    val useMic: StateFlow<Boolean> = _useMic.asStateFlow()
    val lastHitOffset: StateFlow<Long> = _lastHitOffset.asStateFlow()
    val beatsRemaining: StateFlow<Int> = _beatsRemaining.asStateFlow()
    val highScores: StateFlow<Map<String, Int>> = _highScores.asStateFlow()
    val visibleNotes: StateFlow<List<RenderNote>> = _visibleNotes.asStateFlow()

    /** Live mic amplitude 0..1. Non-zero only while mic mode is active. */
    val micAmplitude: StateFlow<Float> = detector.amplitude

    private val _unlockQueue = MutableStateFlow<List<MetroItemEntry>>(emptyList())
    val unlockQueue: StateFlow<List<MetroItemEntry>> = _unlockQueue.asStateFlow()

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

    private var intervalMs = 750L
    private var totalBeats = 32
    private var maxCombo = 0
    private var countPerfect = 0
    private var countGood = 0
    private var countBad = 0
    private var countMiss = 0

    /** songTime of the last genuine hit — used to forgive double-tap twitch. */
    private var lastHitSongTimeMs = Long.MIN_VALUE

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

    // Output-latency correction for mic onsets, from the device self-test constant via
    // MicCalibration (the single source of truth). 0 when uncalibrated (dev). Set per game.
    private var micLatencyMs = 0L

    init {
        engine.onBeat = { beat ->
            // The detector rejects the metronome click spectrally (classifyClaps), so no
            // time-suppression window is set here - an on-beat clap is no longer blocked.
            if (BuildConfig.DEBUG && _useMic.value) {
                val now = SystemClock.elapsedRealtime()
                MicDiagnosticsBuffer.logBeat(beat, now, now)
            }
            viewModelScope.launch { _currentBeat.value = beat }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun setDifficulty(levelBpm: Int, beats: Int = 32, name: String = "") {
        _bpm.value = levelBpm
        totalBeats = beats
        currentDifficultyName = name
    }


    fun startGame() {
        reset()
        _phase.value = GamePhase.COUNTDOWN
        AnalyticsTracker.logGameStarted(currentDifficultyName, _bpm.value)
        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                _countDown.value = i
                delay(1.seconds)
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
        if (BuildConfig.DEBUG && _useMic.value) MicDiagnosticsBuffer.endSession()
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
        val tol = toleranceMultiplier
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
        engine.accentBeat = 0
        engine.soundType = 0

        // Mic mode is the single app-wide toggle (MicCalibration.isActive); the game uses
        // the mic automatically when it is on. Only start if RECORD_AUDIO is actually held
        // (granted during calibration; a later revoke falls back to tap mode).
        val cal = MicCalibration.read(getApplication())
        _useMic.value = cal.isActive
        // Clapping is far less precise than an instant screen tap, so widen the windows when the
        // mic is scoring. The tolerance-scaled tap windows above stay for tap mode.
        if (cal.isActive) {
            // Wide clap windows, but clamped so no window exceeds ~45% of the beat interval.
            // Beyond that, adjacent notes' hit windows overlap at faster tempos (Hard/Expert) and
            // a clap or stray onset can be matched to the wrong note. Clamping keeps every clap
            // unambiguously owned by one note while staying forgiving at slow tempos. Order is
            // preserved (perfect <= good <= miss).
            val maxWindow = (intervalMs * 0.45).toLong()
            winMiss = MIC_MISS_WINDOW_MS.coerceAtMost(maxWindow)
            winGood = MIC_GOOD_WINDOW_MS.coerceAtMost(winMiss)
            winPerfect = MIC_PERFECT_WINDOW_MS.coerceAtMost(winGood)
            // The accented downbeat is loud enough to either clip into broadband harmonics or
            // throw a late room reflection that AEC cannot cancel - the clap detector then takes
            // that for a clap, producing one false "hit" on every downbeat (with nobody clapping).
            // Drop the accent in mic mode so all 16 clicks are uniform and reject identically.
            engine.accentBeat = -1
        }
        val micReady = cal.isActive && ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (micReady) {
            micLatencyMs = cal.latencyMs.toLong()
            detector.start()
            if (BuildConfig.DEBUG) {
                MicDiagnosticsBuffer.startSession("RhythmGame", detector.echoCancellationActive)
                detector.debugOnClickRejected = { onset, onsetMs ->
                    MicDiagnosticsBuffer.logClickRejected(onsetMs, onset.lowRms, onset.highRms, onset.peakRatio)
                }
                viewModelScope.launch { detector.amplitude.collect { MicDiagnosticsBuffer.updateAmplitude(it) } }
            }
            micJob = viewModelScope.launch {
                detector.detections.collect { onsetElapsedMs ->
                    // Guard the whole body: a single bad onset must never cancel this collector
                    // and silently kill clap input for the rest of the game. Cancellation is
                    // rethrown so stopGame() still tears the job down cleanly.
                    try {
                        _micDetected.tryEmit(Unit)
                        // This game is VISUAL-cued (clap when the ball hits the line), so subtracting
                        // the full acoustic round-trip [micLatencyMs] is wrong - it over-shifts claps
                        // early (it is the right correction only for the audio-cued Speed Trainer).
                        // Only the small mic input latency applies here; we leave it uncorrected and
                        // let the widened hit windows absorb it.
                        val clapSongMs = onsetElapsedMs - gameStartElapsedMs
                        if (BuildConfig.DEBUG) {
                            // raw = what we now score against; cal = the old full-round-trip value,
                            // kept for comparison in the Mic Timing Log.
                            val nearest = notes.filter { it.state == NoteState.ACTIVE }
                                .minByOrNull { abs(clapSongMs - it.hitTimeMs) }
                            MicDiagnosticsBuffer.logOnsetAccepted(
                                onsetElapsedMs,
                                nearest?.let { (clapSongMs - it.hitTimeMs).toFloat() } ?: 0f,
                                nearest?.let { (clapSongMs - micLatencyMs - it.hitTimeMs).toFloat() } ?: 0f,
                            )
                        }
                        if (_phase.value == GamePhase.PLAYING) {
                            processTap(clapSongMs)
                        }
                    } catch (ce: kotlinx.coroutines.CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        // Swallow and keep listening for the next clap.
                    }
                }
            }
        }

        // Record game start on the monotonic clock — the single timebase shared
        // by the game loop, screen taps, and mic detections alike.
        gameStartElapsedMs = SystemClock.elapsedRealtime()
        _phase.value = GamePhase.PLAYING

        // Delay engine start by NOTE_TRAVEL_MS so the click fires exactly when
        // each note reaches the hit line — metronome and visuals stay in sync.
        engineStartJob = viewModelScope.launch {
            delay(NOTE_TRAVEL_MS.milliseconds)
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
                delay(16.milliseconds)   // ~60 fps; wall-clock, not frame count
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
        // In mic mode a detection arrives slightly after the onset; hold the
        // auto-miss back so an in-flight valid hit is not missed prematurely.
        val missGrace = if (_useMic.value) MIC_MISS_GRACE_MS else 0L

        for ((i, note) in notes.withIndex()) {
            when (note.state) {
                NoteState.UPCOMING -> {
                    if (songT >= note.spawnTimeMs) note.state = NoteState.ACTIVE
                }

                NoteState.ACTIVE -> {
                    if (songT > note.hitTimeMs + winMiss + missGrace) {
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

        if (candidate == null) {            // nothing on screen to hit
            registerStrayTap(tapSongTimeMs)
            return
        }

        val delta = tapSongTimeMs - candidate.hitTimeMs   // +late, −early
        val absDelta = abs(delta)

        // Spec §9: hit judgement
        val quality = when {
            absDelta <= winPerfect -> HitQuality.PERFECT
            absDelta <= winGood -> HitQuality.GOOD
            absDelta <= winMiss -> HitQuality.ALMOST
            else -> HitQuality.NONE  // NOT A HIT — do not assign note
        }

        if (quality == HitQuality.NONE) {   // tapped, but no note in range
            registerStrayTap(tapSongTimeMs)
            return
        }

        // Genuine hit — record the offset only now (a stray must not skew the meter).
        _lastHitOffset.value = delta
        lastHitSongTimeMs = tapSongTimeMs
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
            delay(650.milliseconds)
            if (_lastQuality.value == quality) _lastQuality.value = HitQuality.NONE
        }
    }

    /**
     * A tap that landed on no note — a mistimed input. Breaks the combo so that
     * mashing cannot farm a full combo, but costs no points and is not counted
     * as a missed note (the miss counters track notes, not stray taps).
     *
     * An extra tap within [STRAY_TAP_GRACE_MS] of a genuine hit is treated as
     * double-tap twitch and forgiven, so a jittery finger is not punished.
     */
    private fun registerStrayTap(tapSongTimeMs: Long) {
        if (tapSongTimeMs - lastHitSongTimeMs in 0 until STRAY_TAP_GRACE_MS) return

        _combo.value = 0
        _lastQuality.value = HitQuality.MISS
        viewModelScope.launch {
            delay(450.milliseconds)
            if (_lastQuality.value == HitQuality.MISS) _lastQuality.value = HitQuality.NONE
        }
    }

    /** Called when the miss window expires on an un-tapped note. */
    private fun recordMiss() {
        countMiss++
        _combo.value = 0
        _lastQuality.value = HitQuality.MISS
        viewModelScope.launch {
            delay(600.milliseconds)
            if (_lastQuality.value == HitQuality.MISS) _lastQuality.value = HitQuality.NONE
        }
    }

    private fun endGame() {
        if (_phase.value == GamePhase.RESULT) return
        if (BuildConfig.DEBUG && _useMic.value) MicDiagnosticsBuffer.endSession()
        engine.stop(); detector.stop()
        val finalScore = _score.value
        val isNew = currentDifficultyName.isNotEmpty() &&
                finalScore > (_highScores.value[currentDifficultyName] ?: 0)
        if (isNew) {
            _highScores.value = _highScores.value.toMutableMap()
                .also { it[currentDifficultyName] = finalScore }
            prefs.edit { putInt("hs_$currentDifficultyName", finalScore) }
        }
        itemTracker.recordGameCompleted()
        itemTracker.addGameScore(finalScore)
        run {
            val divisor = com.example.metrognome.points.PointsConfig.GAME_SCORE_DIVISOR
            val limit   = com.example.metrognome.points.PointsLimits.RHYTHM_BEATS_PER_DAY
            val todayScore     = dailyLog.todayActivity(itemTracker).gameScoreToday
            val prevScore      = (todayScore - finalScore).coerceAtLeast(0)
            val prevBeats      = (prevScore  / divisor).coerceAtMost(limit)
            val todayBeats     = (todayScore / divisor).coerceAtMost(limit)
            val earnedThisGame = (todayBeats - prevBeats).coerceAtLeast(0)
            com.example.metrognome.points.PointsBannerQueue.post(
                com.example.metrognome.points.PointsBannerData(
                    pointsEarned     = earnedThisGame,
                    activityLabel    = "Rhythm Game",
                    todayCount       = todayBeats,
                    dailyLimit       = limit,
                    limitJustReached = todayBeats == limit,
                )
            )
        }
        checkForNewUnlocks()
        _result.value =
            GameResult(finalScore, maxCombo, countPerfect, countGood, countBad, countMiss, isNew)
        AnalyticsTracker.logGameCompleted(currentDifficultyName, finalScore, countPerfect, countGood, countBad, countMiss, isNew)
        _phase.value = GamePhase.RESULT
    }

    fun checkForNewUnlocks() {
        val unlocked = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
        val celebrated = itemTracker.celebratedIds()
        // Purge: remove entries that are celebrated OR no longer unlocked (e.g. after dev reset)
        _unlockQueue.value = _unlockQueue.value.filter { it.item.id in unlocked && it.item.id !in celebrated }
        val newEntries = METRO_ITEM_REGISTRY.filter { it.item.id in unlocked && it.item.id !in celebrated }
        if (newEntries.isEmpty()) return
        val existing = _unlockQueue.value.map { it.item.id }.toSet()
        val toAdd = newEntries.filter { it.item.id !in existing }
        if (toAdd.isNotEmpty()) {
            _unlockQueue.value += toAdd
        }
    }

    fun markCelebrated(id: String) {
        itemTracker.markCelebrated(id)
        _unlockQueue.value = _unlockQueue.value.filter { it.item.id != id }
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
        lastHitSongTimeMs = Long.MIN_VALUE
    }

    private fun cancelJobs() {
        countdownJob?.cancel(); engineStartJob?.cancel()
        gameLoopJob?.cancel(); micJob?.cancel()
    }

    private fun loadHighScores(): Map<String, Int> =
        DIFFICULTY_NAMES.associateWith { name -> prefs.getInt("hs_$name", 0) }

    override fun onCleared() {
        engine.stop(); detector.stop()
        super.onCleared()
    }
}
