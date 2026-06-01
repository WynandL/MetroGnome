package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.rhythm.RhythmDetector
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
    ) : TrainerSessionState()
    data class Complete(
        val config: SpeedTrainerConfig,
        val steps: List<Int>,
        val reachedStepIndex: Int,
        val stepStats: List<StepStat>,
        val previousReachedBpm: Int?,
    ) : TrainerSessionState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SpeedTrainerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = SpeedTrainerPrefs(app)
    private val itemTracker = MetroItemTracker(app)

    private val _config = MutableStateFlow(prefs.loadConfig())
    val config: StateFlow<SpeedTrainerConfig> = _config.asStateFlow()

    private val _sessionState = MutableStateFlow<TrainerSessionState>(TrainerSessionState.Idle)
    val sessionState: StateFlow<TrainerSessionState> = _sessionState.asStateFlow()

    // One-shot BPM change requests for MetronomeScreen to action.
    private val _bpmRequest = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val bpmRequest: SharedFlow<Int> = _bpmRequest.asSharedFlow()

    // ── Internal session bookkeeping ──────────────────────────────────────────

    private var steps: List<Int> = emptyList()
    private var currentStepIndex = 0
    private var barsRemaining = 0
    private var lastBeatMs = 0L
    private var currentTimeSig = 4
    private var countdownBeatsLeft = 0

    // Output-latency calibration — measured during the count-in, applied during the session.
    // Collected raw deviations (uncorrected) when the user plays along with the countdown.
    // Median of these samples becomes the per-session latency bias.
    private val calibrationSamples = mutableListOf<Float>()
    private var latencyBiasMs = 0f

    // Per-step hit deviation ring buffer for auto-advance + display
    private val recentDeviations = ArrayDeque<Float>()

    // Accumulated stats per completed step
    private val stepStats = mutableListOf<StepStat>()
    private var currentStepDeviations = mutableListOf<Float>()

    // Mic
    private var detector: RhythmDetector? = null

    private val isDevMode get() = DevEasterEgg.isDevModeActive(getApplication())

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
        val cfg = _config.value
        steps = cfg.stepsSequence()
        if (steps.isEmpty()) return

        currentStepIndex = 0
        currentTimeSig = timeSig
        barsRemaining = cfg.totalBarsPerStep
        // 2 display bars + 1 extra beat so the session starts cleanly on a downbeat.
        // The 9th beat (beat=0) is the session's first downbeat — we transition then.
        countdownBeatsLeft = timeSig * 2 + 1
        calibrationSamples.clear()
        latencyBiasMs = 0f
        recentDeviations.clear()
        stepStats.clear()
        currentStepDeviations.clear()

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
            micEnabled = cfg.micEnabled,
        )

        if (cfg.micEnabled && hasMicPermission() && isDevMode) startMic()
    }

    fun cancelSession() {
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

        // Suppress the metronome click from mic detection.
        // The window is offset by latencyBiasMs so it covers the actual playback moment,
        // not just the write-queue moment where lastBeatMs is stamped.
        detector?.let { det ->
            val suppressMs = if (det.echoCancellationActive) 20L else 60L
            det.suppressUntilMs = lastBeatMs + latencyBiasMs.toLong() + suppressMs
        }
        if (isDevMode) {
            val suppressMs = if (detector?.echoCancellationActive == true) 20L else 60L
            MicDiagnosticsBuffer.logBeat(
                beat = beat,
                suppressUntilMs = lastBeatMs + latencyBiasMs.toLong() + suppressMs,
                estimatedPlayMs = lastBeatMs + latencyBiasMs.toLong(),
            )
        }

        when (val state = _sessionState.value) {
            is TrainerSessionState.Countdown -> {
                countdownBeatsLeft--
                if (countdownBeatsLeft <= 0) {
                    // Finalise calibration before starting the session.
                    // Median of countdown deviations estimates the device output latency.
                    // Clamped to 0–350ms — negative latency is impossible, >350ms is pathological.
                    if (calibrationSamples.size >= 3) {
                        latencyBiasMs = calibrationSamples.sorted()[calibrationSamples.size / 2]
                            .coerceIn(0f, 350f)
                    }
                    if (isDevMode) {
                        MicDiagnosticsBuffer.logCalibrationFinalized(latencyBiasMs, calibrationSamples.size)
                    }
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
            is TrainerSessionState.Countdown -> {
                // Collect calibration samples during count-in.
                // Accept positive deviations only (after the beat) in a plausible
                // output-latency range. Negative values or very small values are
                // click bleed that slipped past AEC; >400ms is genuine lateness.
                if (rawDeviation in 10f..400f) {
                    calibrationSamples.add(rawDeviation)
                    if (isDevMode) {
                        MicDiagnosticsBuffer.logCalibrationSample(rawDeviation, calibrationSamples.size - 1)
                    }
                }
            }
            is TrainerSessionState.Running -> {
                // Apply the per-session latency correction so deviation is centred on 0
                // for a musician playing in time, rather than offset by output latency.
                val deviation = rawDeviation - latencyBiasMs
                if (isDevMode) MicDiagnosticsBuffer.logOnsetAccepted(onsetMs, rawDeviation, deviation)

                recentDeviations.addLast(deviation)
                if (recentDeviations.size > RING_SIZE) recentDeviations.removeFirst()

                currentStepDeviations.add(deviation)

                val cfg = _config.value
                val goodHits = recentDeviations.count { abs(it) <= cfg.autoAdvanceWindowMs }
                val progress = goodHits.toFloat() / RING_SIZE.toFloat()

                val state = _sessionState.value as? TrainerSessionState.Running ?: return
                _sessionState.value = state.copy(
                    autoAdvanceProgress = progress,
                    lastDeviationMs = deviation,
                )

                if (progress >= AUTO_ADVANCE_THRESHOLD) {
                    advanceToNextStep()
                }
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

    private fun completeSession() {
        stopMic()
        itemTracker.recordSpeedTrainingCompleted()
        val cfg = _config.value
        val previousBest = prefs.loadReachedBpm(cfg.startBpm, cfg.targetBpm)
        val reachedBpm = steps.getOrNull(currentStepIndex) ?: cfg.targetBpm
        prefs.saveReachedBpm(cfg.startBpm, cfg.targetBpm, reachedBpm)
        AnalyticsTracker.logSpeedTrainerCompleted(
            startBpm      = cfg.startBpm,
            targetBpm     = cfg.targetBpm,
            reachedBpm    = reachedBpm,
            totalSessions = itemTracker.speedTrainingSessionsCompleted(),
            micEnabled    = cfg.micEnabled,
        )
        _sessionState.value = TrainerSessionState.Complete(
            config = cfg,
            steps = steps,
            reachedStepIndex = currentStepIndex,
            stepStats = stepStats.toList(),
            previousReachedBpm = previousBest,
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
    )

    // ── Mic ───────────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private fun startMic() {
        detector?.stop()
        val det = RhythmDetector()
        detector = det
        det.start()
        viewModelScope.launch {
            det.detections.collect { onsetMs -> onOnset(onsetMs) }
        }
        if (isDevMode) {
            MicDiagnosticsBuffer.startSession("SpeedTrainer", det.echoCancellationActive)
            viewModelScope.launch { det.amplitude.collect { MicDiagnosticsBuffer.updateAmplitude(it) } }
            det.debugOnSuppressed = { onsetMs ->
                MicDiagnosticsBuffer.logOnsetSuppressed(onsetMs, det.suppressUntilMs)
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
        super.onCleared()
    }

    companion object {
        // Number of recent hits tracked for auto-advance ring fill.
        // 2 bars of 4/4 = 8 hits feels responsive without being jumpy.
        private const val RING_SIZE = 8
        private const val AUTO_ADVANCE_THRESHOLD = 1.0f
    }
}
