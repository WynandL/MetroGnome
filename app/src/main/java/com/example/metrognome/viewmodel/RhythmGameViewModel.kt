package com.example.metrognome.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.core.content.edit

enum class GamePhase { IDLE, COUNTDOWN, PLAYING, RESULT }

enum class HitQuality { PERFECT, GREAT, GOOD, MISS, NONE }

data class GameResult(
    val score: Int, val maxCombo: Int,
    val perfects: Int, val greats: Int, val goods: Int, val misses: Int,
    val isNewHighScore: Boolean = false
)

// Canonical difficulty names — used as SharedPreferences keys.
val DIFFICULTY_NAMES = listOf("Beginner", "Easy", "Medium", "Hard", "Expert")

class RhythmGameViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs  = app.getSharedPreferences("rhythm_highscores", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    val detector = RhythmDetector()

    // ── State flows ────────────────────────────────────────────────────────────
    private val _phase          = MutableStateFlow(GamePhase.IDLE)
    private val _score          = MutableStateFlow(0)
    private val _combo          = MutableStateFlow(0)
    private val _countDown      = MutableStateFlow(3)
    private val _currentBeat    = MutableStateFlow(0)
    private val _bpm            = MutableStateFlow(80)
    private val _timeSig        = MutableStateFlow(4)
    private val _lastQuality    = MutableStateFlow(HitQuality.NONE)
    private val _result         = MutableStateFlow<GameResult?>(null)
    private val _useMic         = MutableStateFlow(false)
    private val _beatIntervalMs = MutableStateFlow(750L)
    private val _lastHitOffset  = MutableStateFlow(0L)
    private val _beatsRemaining = MutableStateFlow(0)
    private val _tolerance      = MutableStateFlow(1.5f)
    private val _highScores     = MutableStateFlow(loadHighScores())

    val phase:          StateFlow<GamePhase>      = _phase.asStateFlow()
    val score:          StateFlow<Int>            = _score.asStateFlow()
    val combo:          StateFlow<Int>            = _combo.asStateFlow()
    val countDown:      StateFlow<Int>            = _countDown.asStateFlow()
    val currentBeat:    StateFlow<Int>            = _currentBeat.asStateFlow()
    val bpm:            StateFlow<Int>            = _bpm.asStateFlow()
    val timeSig:        StateFlow<Int>            = _timeSig.asStateFlow()
    val lastQuality:    StateFlow<HitQuality>     = _lastQuality.asStateFlow()
    val result:         StateFlow<GameResult?>    = _result.asStateFlow()
    val useMic:         StateFlow<Boolean>        = _useMic.asStateFlow()
    val beatIntervalMs: StateFlow<Long>           = _beatIntervalMs.asStateFlow()
    val lastHitOffset:  StateFlow<Long>           = _lastHitOffset.asStateFlow()
    val beatsRemaining: StateFlow<Int>            = _beatsRemaining.asStateFlow()
    val tolerance:      StateFlow<Float>          = _tolerance.asStateFlow()
    val highScores:     StateFlow<Map<String, Int>> = _highScores.asStateFlow()
    /** Live mic amplitude 0..1. Non-zero only while mic mode is active and game is playing. */
    val micAmplitude:   StateFlow<Float>            = detector.amplitude

    private val _beatPulse = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val beatPulse: SharedFlow<Int> = _beatPulse.asSharedFlow()

    /**
     * Fires every time the microphone triggers a detection — regardless of whether
     * the timing was correct. Paired with [lastQuality], the UI can show:
     *   • raw flash  = mic heard something
     *   • no quality change = it was outside the beat window (too early / too late)
     *   • quality flash = it was scored
     */
    private val _micDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val micDetected: SharedFlow<Unit> = _micDetected.asSharedFlow()

    // ── Timing windows (base values in ms, multiplied by tolerance) ─────────
    private val baseWindowPerfect = 120L
    private val baseWindowGreat   = 240L
    private val baseWindowGood    = 420L

    private val pointsPerfect = 15
    private val pointsGreat   = 10
    private val pointsGood    = 5

    // ── Internal game state ───────────────────────────────────────────────────
    private var gameStartMs   = 0L
    private var intervalMs    = 750L
    private var totalBeats    = 32
    private var beatsElapsed  = 0
    private var maxCombo      = 0
    private var countPerfect  = 0; private var countGreat = 0
    private var countGood     = 0; private var countMiss  = 0
    private var pendingBeatMs = -1L   // -1 = no beat pending; ≥0 = beat time waiting for tap
    private var gameEnding    = false // true once we've scheduled endGame()

    private var currentDifficultyName = ""

    private var countdownJob: Job? = null
    private var gameJob:      Job? = null

    init {
        engine.onBeat = { beat ->
            viewModelScope.launch {
                _currentBeat.value = beat
                _beatPulse.emit(beat)

                if (_phase.value == GamePhase.PLAYING && !gameEnding) {
                    // Previous beat not tapped? Score it as a miss.
                    if (pendingBeatMs >= 0) scoreMiss()

                    val now = System.currentTimeMillis()
                    pendingBeatMs = now

                    // Suppress mic for 60 ms after beat — covers the ~46 ms window
                    // in which the metronome click travels from speaker to mic.
                    // 60 ms is well below the minimum human reaction time (~150 ms)
                    // so no legitimate clap is ever blocked.
                    if (_useMic.value) detector.suppressUntilMs = now + 60L

                    beatsElapsed++
                    _beatsRemaining.value = (totalBeats - beatsElapsed).coerceAtLeast(0)

                    if (beatsElapsed >= totalBeats) {
                        // Give the player one full beat interval to tap the last note
                        // before ending the game, so the last beat isn't always a miss.
                        gameEnding = true
                        viewModelScope.launch {
                            delay(intervalMs)
                            endGame()
                        }
                    }
                }
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun setDifficulty(levelBpm: Int, beats: Int = 32, name: String = "") {
        _bpm.value = levelBpm
        totalBeats  = beats
        currentDifficultyName = name
    }

    /** Timing tolerance multiplier. 0.5 = strict, 1.5 = default, 2.5 = very easy. */
    fun setTolerance(v: Float) { _tolerance.value = v.coerceIn(0.5f, 2.5f) }

    fun toggleMic(on: Boolean) { _useMic.value = on }

    fun startGame() {
        reset()
        _phase.value = GamePhase.COUNTDOWN
        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) { _countDown.value = i; delay(1000) }
            beginPlay()
        }
    }

    /** Called when the user taps the screen or button. */
    fun onScreenTap() {
        if (_phase.value != GamePhase.PLAYING) return
        processTap(System.currentTimeMillis())
    }

    fun dismissResult() {
        _phase.value = GamePhase.IDLE
        _result.value = null
    }

    fun stopGame() {
        countdownJob?.cancel(); gameJob?.cancel()
        engine.stop(); detector.stop()
        _phase.value = GamePhase.IDLE
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun beginPlay() {
        val bpm = _bpm.value
        intervalMs = (60_000.0 / bpm).toLong()
        _beatIntervalMs.value = intervalMs
        _beatsRemaining.value = totalBeats
        engine.bpm           = bpm
        engine.timeSignature = _timeSig.value
        engine.accentFirst   = true
        engine.soundType     = 0
        engine.start()

        if (_useMic.value) {
            detector.start()
            gameJob = viewModelScope.launch {
                detector.detections.collect { ts ->
                    _micDetected.tryEmit(Unit)   // always signal raw detection for visualiser
                    if (_phase.value == GamePhase.PLAYING) processTap(ts)
                }
            }
        }

        gameStartMs  = System.currentTimeMillis()
        beatsElapsed = 0
        _phase.value = GamePhase.PLAYING
    }

    private fun processTap(tapMs: Long) {
        if (pendingBeatMs < 0) return          // no beat pending → stray tap, ignore
        if (gameEnding && pendingBeatMs < 0) return

        val tol      = _tolerance.value
        val wPerfect = (baseWindowPerfect * tol).toLong()
        val wGreat   = (baseWindowGreat   * tol).toLong()
        val wGood    = (baseWindowGood    * tol).toLong()

        val signedOffset = tapMs - pendingBeatMs   // +late, −early
        val absOffset    = abs(signedOffset)
        _lastHitOffset.value = signedOffset

        val quality = when {
            absOffset <= wPerfect -> HitQuality.PERFECT
            absOffset <= wGreat   -> HitQuality.GREAT
            absOffset <= wGood    -> HitQuality.GOOD
            else                  -> HitQuality.MISS
        }

        if (quality != HitQuality.MISS) pendingBeatMs = -1L   // consume beat

        val newCombo = if (quality != HitQuality.MISS) _combo.value + 1 else 0
        _combo.value = newCombo
        if (newCombo > maxCombo) maxCombo = newCombo

        val mult = comboMultiplier(newCombo)
        _score.value += when (quality) {
            HitQuality.PERFECT -> (pointsPerfect * mult).roundToInt()
            HitQuality.GREAT   -> (pointsGreat   * mult).roundToInt()
            HitQuality.GOOD    -> (pointsGood    * mult).roundToInt()
            else               -> 0
        }
        _lastQuality.value = quality

        when (quality) {
            HitQuality.PERFECT -> countPerfect++
            HitQuality.GREAT   -> countGreat++
            HitQuality.GOOD    -> countGood++
            HitQuality.MISS    -> countMiss++
            HitQuality.NONE    -> Unit
        }

        viewModelScope.launch {
            delay(650)
            if (_lastQuality.value == quality) _lastQuality.value = HitQuality.NONE
        }
    }

    /** Score an un-tapped beat as a miss. Called by onBeat when the previous beat was skipped. */
    private fun scoreMiss() {
        countMiss++
        _combo.value = 0
        _lastQuality.value = HitQuality.MISS
        viewModelScope.launch {
            delay(600)
            if (_lastQuality.value == HitQuality.MISS) _lastQuality.value = HitQuality.NONE
        }
    }

    private fun endGame() {
        if (_phase.value == GamePhase.RESULT) return   // guard double-call
        engine.stop(); detector.stop()
        // If the last beat was still pending (player didn't tap it in time) → miss
        if (pendingBeatMs >= 0) { countMiss++; _combo.value = 0 }
        val finalScore = _score.value
        val isNew = currentDifficultyName.isNotEmpty() &&
                    finalScore > (_highScores.value[currentDifficultyName] ?: 0)
        if (isNew) {
            _highScores.value = _highScores.value.toMutableMap()
                .also { it[currentDifficultyName] = finalScore }
            prefs.edit { putInt("hs_$currentDifficultyName", finalScore) }
        }
        _result.value = GameResult(finalScore, maxCombo, countPerfect, countGreat, countGood, countMiss, isNew)
        _phase.value  = GamePhase.RESULT
    }

    private fun reset() {
        countdownJob?.cancel(); gameJob?.cancel()
        engine.stop(); detector.stop()
        _score.value         = 0
        _combo.value         = 0
        _currentBeat.value   = 0
        _lastQuality.value   = HitQuality.NONE
        _lastHitOffset.value = 0L
        _result.value        = null
        pendingBeatMs        = -1L
        beatsElapsed         = 0
        maxCombo             = 0
        gameEnding           = false
        countPerfect = 0; countGreat = 0; countGood = 0; countMiss = 0
    }

    private fun comboMultiplier(combo: Int): Float = when {
        combo >= 16 -> 3.0f
        combo >= 8  -> 2.0f
        combo >= 4  -> 1.5f
        else        -> 1.0f
    }

    private fun loadHighScores(): Map<String, Int> =
        DIFFICULTY_NAMES.associateWith { name -> prefs.getInt("hs_$name", 0) }

    override fun onCleared() { engine.stop(); detector.stop(); super.onCleared() }
}
