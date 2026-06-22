package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.rhythm.RhythmDetector
import com.example.metrognome.audio.selftest.MicCalibration
import com.example.metrognome.speedtrainer.SpeedTrainerConfig
import com.example.metrognome.speedtrainer.SpeedTrainerPrefs
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.debug.mic.MicDiagnosticsBuffer
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Public session state ──────────────────────────────────────────────────────

data class StepStat(
    val bpm: Int,
    val avgDeviationMs: Float?,
    val hitCount: Int,
)

sealed class TrainerSessionState {
    object Idle : TrainerSessionState()
    data class Countdown(
        val config: SpeedTrainerConfig,
        val steps: List<Int>,
        val timeSig: Int,
        val currentBeat: Int,   // 0-indexed within bar; -1 = before first click
        val currentBar: Int,    // 1-indexed display bar (1 or 2)
        val totalBars: Int,     // always 2
    ) : TrainerSessionState()
    data class Running(
        val config: SpeedTrainerConfig,
        val steps: List<Int>,
        val currentStepIndex: Int,
        val barsRemainingThisStep: Int,
        val autoAdvanceProgress: Float,
        val lastDeviationMs: Float?,
        val micUsed: Boolean,
    ) : TrainerSessionState()
    data class Complete(
        val config: SpeedTrainerConfig,
        val steps: List<Int>,
        val reachedStepIndex: Int,
        val stepStats: List<StepStat>,
        val previousReachedBpm: Int?,
        // Whether the mic actually ran this session (not just the persisted preference) -
        // drives the result layout so a stale enabled-flag can never show empty mic bars.
        val micUsed: Boolean,
        // Graded timing bonus actually credited this session (after the daily cap), and the
        // 0..1 performance share behind it. 0 = hidden.
        val performanceBonus: Int = 0,
        val performanceFraction: Float = 0f,
        // Groove Check grade (0..100, length-independent) and its plain-language read, shown to the
        // player. grooveScore 0 = no qualifying mic session (hidden by the overlay).
        val grooveScore: Int = 0,
        val grooveRead: String = "",
    ) : TrainerSessionState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SpeedTrainerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs           = SpeedTrainerPrefs(app)
    private val itemTracker     = MetroItemTracker(app)
    private val dailyLog        = com.example.metrognome.points.DailyActivityLog(app)
    private val practiceManager = com.example.metrognome.practice.PracticeSessionManager(app)

    private val _config = MutableStateFlow(prefs.loadConfig())
    val config: StateFlow<SpeedTrainerConfig> = _config.asStateFlow()

    private val _sessionState = MutableStateFlow<TrainerSessionState>(TrainerSessionState.Idle)
    val sessionState: StateFlow<TrainerSessionState> = _sessionState.asStateFlow()

    // One-shot BPM change requests for MetronomeScreen to action.
    private val _bpmRequest = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val bpmRequest: SharedFlow<Int> = _bpmRequest.asSharedFlow()

    // Emitted when a clap lands very close to the beat - drives the celebratory firework in the
    // Gnome canvas. Purely visual; no effect on scoring. See FireworkEffect.kt.
    private val _greatHit = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val greatHit: SharedFlow<Unit> = _greatHit.asSharedFlow()

    // ── Internal session bookkeeping ──────────────────────────────────────────

    private var steps: List<Int> = emptyList()
    private var currentStepIndex = 0
    private var barsRemaining = 0
    private var lastBeatMs = 0L
    private var currentTimeSig = 4
    private var countdownBeatsLeft = 0

    // Output-latency correction applied to mic deviations. Sourced once per session from
    // the device self-test constant via MicCalibration (the single source of truth). The
    // count-in is now purely a "get ready / play along" lead-in, no longer a measurement.
    private var latencyBiasMs = 0f

    private var sessionStartMs = 0L
    private var sessionMicUsed = false

    // Per-step hit deviation ring buffer for auto-advance + display
    private val recentDeviations = ArrayDeque<Float>()

    // Accumulated stats per completed step
    private val stepStats = mutableListOf<StepStat>()
    private var currentStepDeviations = mutableListOf<Float>()

    // Every accepted hit's deviation across the whole session (not cleared per step).
    // Feeds the per-hit Timing Bonus score so one wild hit can't sink an otherwise-good run.
    private val sessionDeviations = mutableListOf<Float>()

    // Mic
    private var detector: RhythmDetector? = null

    private val isDevMode get() = DevEasterEgg.isDevModeActive(getApplication())

    /** DEV: synthesize plausible mic timing at completion (gated behind dev mode). */
    private val devSimulateTiming: Boolean
        get() = isDevMode &&
            com.example.metrognome.audio.selftest.SelfTestCalibrationStore(getApplication()).devSimulateTiming

    // ── Config ────────────────────────────────────────────────────────────────

    fun updateConfig(update: SpeedTrainerConfig.() -> SpeedTrainerConfig) {
        val current = _config.value
        val next = current.update()
        val asc = next.startBpm < next.targetBpm
        val safe = next.copy(
            startBpm = if (asc) next.startBpm.coerceIn(20, next.targetBpm - 1)
                       else     next.startBpm.coerceIn(next.targetBpm + 1, 300),
            targetBpm = if (asc) next.targetBpm.coerceIn(next.startBpm + 1, 300)
                        else     next.targetBpm.coerceIn(20, next.startBpm - 1),
            stepSize = next.stepSize.coerceAtLeast(if (next.incrementMode == SpeedTrainerConfig.IncrementMode.PERCENT) 0.5f else 1f),
            barsPerStep = next.barsPerStep.coerceIn(1, 32),
            repeatsPerStep = next.repeatsPerStep.coerceIn(1, 8),
            autoAdvanceWindowMs = next.autoAdvanceWindowMs.coerceIn(10, 100),
        )
        _config.value = safe
        prefs.saveConfig(safe)
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    fun beginSession(timeSig: Int) {
        val micCal = MicCalibration.read(getApplication())

        val cfg = _config.value
        steps = cfg.stepsSequence()
        if (steps.isEmpty()) return

        // Mic mode is the single app-wide toggle (MicCalibration.isActive) - no per-session
        // opt-in. Whether it actually ran drives the result layout.
        val micRunning = micCal.isActive && hasMicPermission()
        // Dev sim shows the mic result layout (and a synthetic bonus) even without a real mic.
        sessionMicUsed = micRunning || devSimulateTiming

        currentStepIndex = 0
        currentTimeSig = timeSig
        barsRemaining = cfg.totalBarsPerStep
        // 2 display bars + 1 extra beat so the session starts cleanly on a downbeat.
        // The 9th beat (beat=0) is the session's first downbeat — we transition then.
        countdownBeatsLeft = timeSig * 2 + 1
        latencyBiasMs = micCal.latencyMs
        recentDeviations.clear()
        stepStats.clear()
        currentStepDeviations.clear()
        sessionDeviations.clear()

        sessionStartMs = SystemClock.elapsedRealtime()
        SessionFlags.speedTrainerActive = true
        _sessionState.value = TrainerSessionState.Countdown(
            config = cfg,
            steps = steps,
            timeSig = timeSig,
            currentBeat = -1,   // nothing shown until first click lands
            currentBar = 1,
            totalBars = 2,
        )
        _bpmRequest.tryEmit(steps[0])
        AnalyticsTracker.logSpeedTrainerStarted(
            startBpm  = cfg.startBpm,
            targetBpm = cfg.targetBpm,
            stepCount = steps.size,
            micEnabled = micRunning,
        )

        if (micRunning) startMic()
    }

    fun cancelSession() {
        SessionFlags.speedTrainerActive = false
        stopMic()
        countdownBeatsLeft = 0
        AnalyticsTracker.logSpeedTrainerCancelled(
            stepIndex  = currentStepIndex,
            totalSteps = steps.size,
        )
        _sessionState.value = TrainerSessionState.Idle
    }

    fun dismissResult() {
        _sessionState.value = TrainerSessionState.Idle
    }

    // ── Beat counting (called from MetronomeScreen on each beat event) ────────

    fun onBeat(beat: Int) {
        lastBeatMs = SystemClock.elapsedRealtime()

        // The detector rejects the metronome click spectrally (classifyClaps), so there is no
        // time-suppression window any more; a hit landing on the beat still scores.
        if (isDevMode) {
            MicDiagnosticsBuffer.logBeat(
                beat = beat,
                suppressUntilMs = 0L,   // spectral mode has no suppression window
                estimatedPlayMs = lastBeatMs + latencyBiasMs.toLong(),
            )
        }

        when (val state = _sessionState.value) {
            is TrainerSessionState.Countdown -> {
                countdownBeatsLeft--
                if (countdownBeatsLeft <= 0) {
                    // Count-in done. latencyBiasMs was set from the device constant at
                    // session start; the count-in only lets the player get ready.
                    // This beat=0 is the session's first downbeat — start running.
                    // It begins bar 1 (it completes no bar), so the full bar count
                    // remains; later downbeats decrement as each bar completes.
                    barsRemaining = _config.value.totalBarsPerStep
                    _sessionState.value = runningState()
                } else {
                    val newBar = when {
                        state.currentBeat < 0 -> 1              // first click ever
                        beat == 0 -> state.currentBar + 1       // new bar started
                        else -> state.currentBar
                    }
                    _sessionState.value = state.copy(currentBeat = beat, currentBar = newBar)
                }
            }
            is TrainerSessionState.Running -> {
                // A downbeat (beat == 0) completes the bar that just played.
                if (beat == 0) {
                    barsRemaining--
                    if (barsRemaining <= 0) advanceToNextStep()
                    else _sessionState.value = runningState()
                }
            }
            else -> {}
        }
    }

    // ── Manual step controls ──────────────────────────────────────────────────

    fun skipStep() {
        if (_sessionState.value !is TrainerSessionState.Running) return
        advanceToNextStep()
    }

    fun retreatStep() {
        if (_sessionState.value !is TrainerSessionState.Running) return
        if (currentStepIndex == 0) return
        sealCurrentStepStat()
        if (stepStats.isNotEmpty()) stepStats.removeAt(stepStats.lastIndex)
        currentStepIndex--
        resetStep()
        _sessionState.value = runningState()
        _bpmRequest.tryEmit(steps[currentStepIndex])
    }

    // ── Mic onset callback (called from coroutine collecting detector.detections) ──

    fun onOnset(onsetMs: Long) {
        val rawDeviation = (onsetMs - lastBeatMs).toFloat()

        // Reject anything outside a generous ±500ms window around the beat.
        if (abs(rawDeviation) > 500f) {
            if (isDevMode) MicDiagnosticsBuffer.logOnsetRejected(onsetMs, rawDeviation)
            return
        }

        when (_sessionState.value) {
            // During the count-in the player is only getting ready; onsets are ignored
            // (no measurement happens here any more - latency comes from the self-test).
            is TrainerSessionState.Running -> {
                // Apply the per-session latency correction so deviation is centred on 0
                // for a musician playing in time, rather than offset by output latency.
                val deviation = rawDeviation - latencyBiasMs
                if (isDevMode) MicDiagnosticsBuffer.logOnsetAccepted(onsetMs, rawDeviation, deviation)

                // A very accurate clap fires a celebratory firework (visual only).
                if (abs(deviation) <= com.example.metrognome.groove.GrooveScorer.GREAT_HIT_MS) {
                    _greatHit.tryEmit(Unit)
                }

                recentDeviations.addLast(deviation)
                if (recentDeviations.size > RING_SIZE) recentDeviations.removeFirst()

                currentStepDeviations.add(deviation)
                sessionDeviations.add(deviation)

                val cfg = _config.value
                val goodHits = recentDeviations.count { abs(it) <= cfg.autoAdvanceWindowMs }
                val progress = goodHits.toFloat() / RING_SIZE.toFloat()

                val state = _sessionState.value as? TrainerSessionState.Running ?: return
                _sessionState.value = state.copy(
                    autoAdvanceProgress = progress,
                    lastDeviationMs = deviation,
                )

                // Auto-advance removed — mic accuracy earns bonus Gnotes at session end instead.
            }
            else -> {}
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun advanceToNextStep() {
        sealCurrentStepStat()
        if (currentStepIndex >= steps.lastIndex) {
            completeSession()
            return
        }
        currentStepIndex++
        resetStep()
        _sessionState.value = runningState()
        _bpmRequest.tryEmit(steps[currentStepIndex])
    }

    private fun resetStep() {
        barsRemaining = _config.value.totalBarsPerStep
        recentDeviations.clear()
        currentStepDeviations.clear()
    }

    private fun sealCurrentStepStat() {
        val bpm = steps.getOrNull(currentStepIndex) ?: return
        val avg = if (currentStepDeviations.isEmpty()) null
                  else currentStepDeviations.map { abs(it) }.average().toFloat()
        stepStats.add(StepStat(bpm = bpm, avgDeviationMs = avg, hitCount = currentStepDeviations.size))
    }

    /** Duration (seconds) of the most recently completed Speed Trainer session. */
    var lastSessionDurationSeconds: Int = 0
        private set

    private fun completeSession() {
        SessionFlags.speedTrainerActive = false
        stopMic()
        val elapsedSeconds = (SystemClock.elapsedRealtime() - sessionStartMs) / 1000L
        lastSessionDurationSeconds = elapsedSeconds.toInt().coerceAtLeast(0)

        // A completed Speed Trainer session counts as a practice day for the streak.
        practiceManager.recordSession()

        val activityBefore = dailyLog.todayActivity(itemTracker)
        itemTracker.recordSpeedTrainingCompleted()
        itemTracker.addSpeedTrainerSeconds(elapsedSeconds)
        val activityAfter = dailyLog.todayActivity(itemTracker)

        run {
            val limitMins  = com.example.metrognome.points.PointsLimits.SPEED_TRAINER_MINUTES_PER_DAY
            val rate       = com.example.metrognome.points.PointsConfig.PER_SPEED_TRAINER_MINUTE
            val prevBeats  = ((activityBefore.speedTrainerSecondsToday / 60).coerceAtMost(limitMins.toLong()) * rate).toInt()
            val todayBeats = ((activityAfter.speedTrainerSecondsToday  / 60).coerceAtMost(limitMins.toLong()) * rate).toInt()
            val earnedNow  = (todayBeats - prevBeats).coerceAtLeast(0)
            val cappedMins = (activityAfter.speedTrainerSecondsToday / 60).coerceAtMost(limitMins.toLong()).toInt()
            if (earnedNow > 0 || cappedMins >= limitMins) {
                com.example.metrognome.points.PointsBannerQueue.post(
                    com.example.metrognome.points.PointsBannerData(
                        pointsEarned     = earnedNow,
                        activityLabel    = "Speed Trainer",
                        todayCount       = cappedMins,
                        dailyLimit       = limitMins,
                        limitJustReached = cappedMins == limitMins,
                    )
                )
            }
        }

        // Adaptive Groove Check grade from the SIGNED per-hit deviations (consistency-first; see
        // groove/GrooveScorer). The grooveScore (0..100) is shown to the player; the Gnotes bonus
        // is a separate, length-bounded reward. Single source shared with Practice.
        val totalHits = sessionDeviations.size
        val realGroove = com.example.metrognome.groove.GrooveScorer.score(sessionDeviations.toList())

        // Dev "simulate timing": with no real hits on a device/emulator that has no usable mic,
        // synthesize a plausible grade so the bonus + result UI can still be exercised. Only the
        // PERFORMANCE is synthesized; the session length stays real (max = session minutes).
        val simulate = devSimulateTiming && totalHits < com.example.metrognome.groove.GrooveScorer.MIN_HITS
        val groove = if (simulate) {
            val f = (40..90).random() / 100f
            realGroove.copy(
                grooveScore = (f * 100).roundToInt(),
                fraction = f,
                hitCount = 12,
                qualified = true,
                read = "Simulated timing",
            )
        } else realGroove
        val sessionMinutes = (elapsedSeconds / 60f).let { kotlin.math.round(it) }.toInt()
        val rawBonus = com.example.metrognome.groove.GrooveScorer.bonusGnotes(groove.fraction, groove.hitCount, sessionMinutes)
        var performanceBonusEarned = 0
        if (rawBonus > 0) {
            val limit  = com.example.metrognome.points.PointsLimits.PERFORMANCE_BONUS_PER_DAY
            val before = dailyLog.todayActivity(itemTracker).performanceBonusToday.coerceAtMost(limit)
            itemTracker.addPerformanceBonus(rawBonus)
            val after  = dailyLog.todayActivity(itemTracker).performanceBonusToday.coerceAtMost(limit)
            performanceBonusEarned = (after - before).coerceAtLeast(0)
            if (performanceBonusEarned > 0) {
                AnalyticsTracker.logTimingBonusEarned(
                    source = "speed_trainer",
                    bonus = performanceBonusEarned,
                    fraction = groove.fraction,
                    hitCount = groove.hitCount,
                )
                com.example.metrognome.points.PointsBannerQueue.post(
                    com.example.metrognome.points.PointsBannerData(
                        pointsEarned     = performanceBonusEarned,
                        activityLabel    = "Timing Bonus",
                        todayCount       = after,
                        dailyLimit       = limit,
                        limitJustReached = after == limit && before < limit,
                    )
                )
            }
        }

        val cfg = _config.value
        val previousBest = prefs.loadReachedBpm(cfg.startBpm, cfg.targetBpm)
        val reachedBpm = steps.getOrNull(currentStepIndex) ?: cfg.targetBpm
        prefs.saveReachedBpm(cfg.startBpm, cfg.targetBpm, reachedBpm)
        AnalyticsTracker.logSpeedTrainerCompleted(
            startBpm      = cfg.startBpm,
            targetBpm     = cfg.targetBpm,
            reachedBpm    = reachedBpm,
            totalSessions = itemTracker.speedTrainingSessionsCompleted(),
            micEnabled    = sessionMicUsed,
        )
        _sessionState.value = TrainerSessionState.Complete(
            config = cfg,
            steps = steps,
            reachedStepIndex = currentStepIndex,
            stepStats = stepStats.toList(),
            previousReachedBpm = previousBest,
            micUsed = sessionMicUsed,
            performanceBonus = performanceBonusEarned,
            performanceFraction = groove.fraction,
            grooveScore = if (groove.qualified) groove.grooveScore else 0,
            grooveRead = groove.read,
        )
    }

    private fun runningState() = TrainerSessionState.Running(
        config = _config.value,
        steps = steps,
        currentStepIndex = currentStepIndex,
        barsRemainingThisStep = barsRemaining,
        autoAdvanceProgress = if (recentDeviations.isEmpty()) 0f
                              else recentDeviations.count { abs(it) <= _config.value.autoAdvanceWindowMs }
                                       .toFloat() / RING_SIZE,
        lastDeviationMs = recentDeviations.lastOrNull(),
        micUsed = sessionMicUsed,
    )

    // ── Mic ───────────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startMic() {
        detector?.stop()
        // Spectral mode: rejects the metronome click by signature and emits only claps, so an
        // on-beat hit still counts (the old time-suppression window dropped those).
        val det = RhythmDetector(classifyClaps = true)
        detector = det
        det.start()
        viewModelScope.launch {
            det.detections.collect { onsetMs -> onOnset(onsetMs) }
        }
        if (isDevMode) {
            MicDiagnosticsBuffer.startSession("SpeedTrainer", det.echoCancellationActive)
            viewModelScope.launch { det.amplitude.collect { MicDiagnosticsBuffer.updateAmplitude(it) } }
            det.debugOnClickRejected = { onset, onsetMs ->
                MicDiagnosticsBuffer.logClickRejected(onsetMs, onset.lowRms, onset.highRms, onset.peakRatio)
            }
        }
    }

    private fun stopMic() {
        if (isDevMode) MicDiagnosticsBuffer.endSession()
        detector?.stop()
        detector = null
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        stopMic()
    }

    companion object {
        // Number of recent hits tracked for the accuracy ring display.
        private const val RING_SIZE = 8
    }
}
