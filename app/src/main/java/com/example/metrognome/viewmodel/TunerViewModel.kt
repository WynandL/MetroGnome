package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.random.Random
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.audio.tuner.AmbientReport
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.audio.tuner.TunerCalibrator
import com.example.metrognome.audio.tuner.TunerFeedbackConfig
import com.example.metrognome.audio.tuner.TunerFeedbackReporter
import com.example.metrognome.audio.tuner.TunerSessionSnapshot
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Snapshot of the persisted calibration.
 *
 * @property factor       multiplicative correction applied to detections
 * @property accuracyCents measured residual accuracy at calibration time
 * @property calibrated   true once a calibration run has been stored
 */
data class CalibrationInfo(
    val factor: Float,
    val accuracyCents: Float,
    val calibrated: Boolean,
    val mode: CalibrationMode? = null,
)

/** The two ways the tuner can be calibrated. */
enum class CalibrationMode {
    /** Plays tones and re-detects them — removes detector bias; relative only. */
    LOOPBACK,

    /** Listens to a struck tuning fork — corrects true absolute pitch error. */
    REFERENCE,

    /** Calibrated by the musician declaring a live-detected note as in-tune. */
    INSTRUMENT,
}

/** Live state of a calibration run. */
sealed interface CalibrationState {
    data object Idle : CalibrationState

    /**
     * @param currentToneHz frequency (Hz) currently playing during a loopback
     *        sweep; null during reference-fork listening or before the first tone.
     */
    data class Running(
        val mode: CalibrationMode,
        val progress: Float,
        val currentToneHz: Float? = null,
    ) : CalibrationState

    data class DoneLoopback(val result: TunerCalibrator.Result) : CalibrationState
    data class DoneReference(val result: TunerCalibrator.ReferenceResult) : CalibrationState

    /** @param mode which calibration mode failed, so the dialog can offer a targeted retry. */
    data class Failed(val message: String, val mode: CalibrationMode? = null) : CalibrationState
}

/**
 * Drives the [Tuner] engine and [TunerCalibrator] for the tuner screen.
 *
 * Owns: mic-permission gating, the listening lifecycle, persistence of the
 * reference pitch and calibration result (SharedPreferences `tuner_prefs`), and
 * both calibration flows — each pauses live listening while it runs.
 */
class TunerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("tuner_prefs", Context.MODE_PRIVATE)
    private val tuner = Tuner()
    private val calibrator = TunerCalibrator()
    private val tracker = MetroItemTracker(app)

    // ── Engine state, surfaced straight through ────────────────────────────────

    val reading: StateFlow<Tuner.Reading?> = tuner.reading
    val amplitude: StateFlow<Float> = tuner.amplitude

    /** Live environment analysis — drives the on-screen "what I hear" panel. */
    val ambient: StateFlow<AmbientReport> = tuner.ambient

    // ── Owned state ─────────────────────────────────────────────────────────────

    private val _referenceHz = MutableStateFlow(prefs.getFloat(KEY_REFERENCE, 440f))
    val referenceHz: StateFlow<Float> = _referenceHz.asStateFlow()

    private val _calibration = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val calibration: StateFlow<CalibrationState> = _calibration.asStateFlow()

    private val _calibrationInfo = MutableStateFlow(loadCalibrationInfo())
    val calibrationInfo: StateFlow<CalibrationInfo> = _calibrationInfo.asStateFlow()

    private val _feedbackPrompt = MutableStateFlow<TunerSessionSnapshot?>(null)
    val feedbackPrompt: StateFlow<TunerSessionSnapshot?> = _feedbackPrompt.asStateFlow()

    // ── Usage tracking (item unlocks + analytics) ───────────────────────────────

    private val _activeItemIds = MutableStateFlow(tracker.unlockedIds(METRO_ITEM_REGISTRY))
    private val _newlyUnlocked = MutableSharedFlow<MetroItemEntry>(extraBufferCapacity = 8)

    private var usageTimerJob: Job? = null

    /** Whether the screen wants the mic open — survives the calibration pause. */
    @Volatile
    private var listenIntent = false

    init {
        tuner.referenceHz = _referenceHz.value
        tuner.calibrationFactor = _calibrationInfo.value.factor
        if (TunerFeedbackConfig.ENABLED) startTuneDetection()
    }

    // ── Listening ───────────────────────────────────────────────────────────────

    /** Begin listening if RECORD_AUDIO is held; remembers the intent regardless. */
    @androidx.annotation.RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startListening() {
        listenIntent = true
        if (hasMicPermission()) {
            tuner.start()
            startUsageTimer()
        }
    }

    fun stopListening() {
        listenIntent = false
        tuner.stop()
        stopUsageTimer()
    }

    private fun startUsageTimer() {
        usageTimerJob?.cancel()
        AnalyticsTracker.logTunerStarted(_referenceHz.value)
        usageTimerJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                if (ambient.value.locked) {
                    tracker.addTunerSeconds(10)
                    checkForNewUnlocks()
                }
            }
        }
    }

    private fun stopUsageTimer() {
        if (usageTimerJob != null) {
            usageTimerJob?.cancel()
            usageTimerJob = null
            AnalyticsTracker.logTunerStopped()
        }
    }

    private fun checkForNewUnlocks() {
        val previous = _activeItemIds.value
        val current = tracker.unlockedIds(METRO_ITEM_REGISTRY)
        _activeItemIds.value = current
        current.filter { it !in previous && it !in tracker.celebratedIds() }
            .mapNotNull { id -> METRO_ITEM_REGISTRY.find { entry -> entry.item.id == id } }
            .forEach { entry ->
                _newlyUnlocked.tryEmit(entry)
                AnalyticsTracker.logItemUnlocked(entry.item.id, entry.item.id)
            }
    }

    // ── Reference pitch ─────────────────────────────────────────────────────────

    fun setReferenceHz(hz: Float) {
        val clamped = hz.coerceIn(MIN_REFERENCE, MAX_REFERENCE)
        _referenceHz.value = clamped
        tuner.referenceHz = clamped
        prefs.edit { putFloat(KEY_REFERENCE, clamped) }
    }

    fun nudgeReference(deltaHz: Float) = setReferenceHz(_referenceHz.value + deltaHz)

    // ── Feedback ─────────────────────────────────────────────────────────────────

    fun submitFeedback(snapshot: TunerSessionSnapshot, thumbsUp: Boolean, reason: String? = null) {
        viewModelScope.launch {
            if (thumbsUp) tracker.recordTunerFeedback()
            val given = prefs.getInt(KEY_RESPONSES_GIVEN, 0) + 1
            prefs.edit { putInt(KEY_RESPONSES_GIVEN, given) }
            TunerFeedbackReporter.submit(snapshot, thumbsUp, reason)
        }
    }

    fun dismissFeedback() { _feedbackPrompt.value = null }

    /** DEV: force-show the feedback card using the current reading (or a synthetic A4 if silent). */
    fun debugTriggerFeedback() {
        Log.d("TunerFeedback", "debug trigger fired — switch to Tuner tab to see card")
        val r = reading.value
        val info = _calibrationInfo.value
        _feedbackPrompt.value = TunerSessionSnapshot(
            noteName            = r?.let { "${it.noteName}${it.octave}" } ?: "A4",
            detectedHz          = r?.frequency ?: 440f,
            centsOffset         = r?.cents ?: 0f,
            clarity             = r?.clarity ?: 1f,
            stableSeconds       = 5,
            referenceHz         = _referenceHz.value,
            calibrationFactor   = info.factor,
            calibrationMode     = info.mode?.name,
            calibrationAccuracyCents = info.accuracyCents,
            ambientState        = ambient.value.state.name,
        )
    }

    /**
     * Watches the reading stream for a sustained stable pitch.  After
     * STABLE_FRAMES consecutive high-clarity frames on the same note, the
     * next silence/note-change may prompt the user for one-tap feedback —
     * at most once per PROMPT_COOLDOWN_MS, and only probabilistically so
     * the card does not appear after every short tuning check.
     */
    private fun startTuneDetection() = viewModelScope.launch {
        var stableFrames = 0
        var stableStartMs = 0L
        var anchorHz = 0f
        var bestReading: Tuner.Reading? = null
        var lastPromptMs = 0L

        reading.collect { r ->
            if (_calibration.value is CalibrationState.Running) {
                stableFrames = 0; return@collect
            }
            if (r != null && r.clarity >= MIN_CLARITY_FOR_STABLE) {
                if (stableFrames == 0) {
                    stableStartMs = System.currentTimeMillis()
                    anchorHz = r.frequency
                } else if (abs(1200f * log2(r.frequency / anchorHz)) > STABILITY_CENTS) {
                    // Note shifted — restart the stable window on the new pitch
                    stableFrames = 0
                    stableStartMs = System.currentTimeMillis()
                    anchorHz = r.frequency
                }
                bestReading = r
                stableFrames++
            } else {
                if (stableFrames >= STABLE_FRAMES_THRESHOLD && _feedbackPrompt.value == null) {
                    val now = System.currentTimeMillis()
                    if (now - lastPromptMs >= computePromptCooldownMs() && Random.nextFloat() < computePromptChance()) {
                        lastPromptMs = now
                        prefs.edit { putInt(KEY_PROMPTS_SHOWN, prefs.getInt(KEY_PROMPTS_SHOWN, 0) + 1) }
                        bestReading?.let { locked ->
                            val info = _calibrationInfo.value
                            _feedbackPrompt.value = TunerSessionSnapshot(
                                noteName = "${locked.noteName}${locked.octave}",
                                detectedHz = locked.frequency,
                                centsOffset = locked.cents,
                                clarity = locked.clarity,
                                stableSeconds = ((now - stableStartMs) / 1000L).toInt(),
                                referenceHz = _referenceHz.value,
                                calibrationFactor = info.factor,
                                calibrationMode = info.mode?.name,
                                calibrationAccuracyCents = info.accuracyCents,
                                ambientState = ambient.value.state.name,
                            )
                        }
                    }
                }
                stableFrames = 0
                bestReading = null
            }
        }
    }

    private fun computePromptCooldownMs(): Long {
        val shown = prefs.getInt(KEY_PROMPTS_SHOWN, 0)
        val given = prefs.getInt(KEY_RESPONSES_GIVEN, 0)
        if (shown < 3) return PROMPT_COOLDOWN_NORMAL_MS
        val rate = given.toFloat() / shown
        return when {
            rate > 0.50f -> PROMPT_COOLDOWN_ENGAGED_MS
            rate > 0.20f -> PROMPT_COOLDOWN_NORMAL_MS
            else         -> PROMPT_COOLDOWN_LOW_MS
        }
    }

    private fun computePromptChance(): Float {
        val shown = prefs.getInt(KEY_PROMPTS_SHOWN, 0)
        val given = prefs.getInt(KEY_RESPONSES_GIVEN, 0)
        if (shown < 3) return PROMPT_CHANCE_NORMAL
        val rate = given.toFloat() / shown
        return when {
            rate > 0.50f -> PROMPT_CHANCE_ENGAGED
            rate > 0.20f -> PROMPT_CHANCE_NORMAL
            else         -> PROMPT_CHANCE_LOW
        }
    }

    // ── Calibration ─────────────────────────────────────────────────────────────

    /**
     * Loopback self-test: plays tones, re-detects them, removes detector bias.
     * Live listening is paused for its duration and resumed afterwards.
     */
    @androidx.annotation.RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun calibrate() = runCalibration(CalibrationMode.LOOPBACK) { onUpdate ->
        var latestTone: Float? = null
        calibrator.calibrate(
            onProgress = { progress -> onUpdate(progress, latestTone) },
            onToneStart = { hz -> latestTone = hz.toFloat(); onUpdate(
                (_calibration.value as? CalibrationState.Running)?.progress ?: 0f, latestTone
            ) },
        )?.let { result ->
            if (result.confident) applyCalibration(result.correctionFactor, result.accuracyCents, CalibrationMode.LOOPBACK)
            Log.d("TunerCalibration", "Loopback: confident=${result.confident} " +
                    "factor=${result.correctionFactor} accuracy=${result.accuracyCents}¢")
            result.perTone.forEach { t ->
                Log.d("TunerCalibration", "  ${t.toneHz.toInt()} Hz  " +
                        "error=${t.rawErrorCents}¢  clarity=${t.clarity}")
            }
            CalibrationState.DoneLoopback(result)
        }
    }

    /**
     * Tuning-fork calibration: listens to a struck fork of the current
     * [referenceHz] pitch and corrects true absolute pitch error.
     */
    @androidx.annotation.RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun calibrateFromReference() = runCalibration(CalibrationMode.REFERENCE) { onUpdate ->
        calibrator.calibrateFromReference(_referenceHz.value.toDouble()) { progress ->
            onUpdate(progress, null)
        }?.let { result ->
            if (result.confident) applyCalibration(result.correctionFactor, result.spreadCents, CalibrationMode.REFERENCE)
            Log.d("TunerCalibration", "Fork: confident=${result.confident} " +
                    "ref=${result.referenceHz}Hz measured=${result.measuredHz}Hz " +
                    "spread=${result.spreadCents}¢ samples=${result.sampleCount}")
            CalibrationState.DoneReference(result)
        }
    }

    /**
     * Shared scaffolding for both calibration modes: guard against re-entry and
     * missing permission, pause the mic, run [block], and resume the mic only if
     * the screen still wants it. [block] returns the terminal state, or null if
     * the run could not complete.
     */
    private fun runCalibration(
        mode: CalibrationMode,
        block: suspend ((Float, Float?) -> Unit) -> CalibrationState?,
    ) {
        if (_calibration.value is CalibrationState.Running) return
        if (!hasMicPermission()) {
            _calibration.value = CalibrationState.Failed(
                "Microphone permission is required.", mode
            )
            return
        }

        tuner.stop()   // not stopListening() — keep listenIntent so we can resume
        _calibration.value = CalibrationState.Running(mode, 0f)

        viewModelScope.launch {
            val outcome = try {
                block { progress, toneHz ->
                    _calibration.value = CalibrationState.Running(mode, progress, toneHz)
                }
            } catch (_: Exception) {
                null
            }
            _calibration.value = outcome
                ?: CalibrationState.Failed(
                    "Calibration could not run - check the microphone and that media volume is up.",
                    mode,
                )

            if (listenIntent && hasMicPermission()) {
                try { tuner.start() } catch (_: SecurityException) { /* permission revoked mid-run */ }
            }
        }
    }

    /**
     * Calibrate using the musician's own instrument as the reference.
     *
     * When the musician is certain that [reading] represents a perfectly in-tune note,
     * this derives a correction factor that maps what the mic actually captured (raw Hz,
     * before the existing calibration factor) to the note's true target frequency.
     *
     * Math:
     *   targetHz  = reading.frequency / 2^(cents/1200)   — exact Hz the note should be
     *   rawHz     = reading.frequency / currentFactor     — what the mic actually heard
     *   newFactor = targetHz / rawHz                      — correction to make raw → target
     *
     * A sanity gate of ±~100 cents (6 % frequency ratio) rejects cases where the note
     * was mis-identified or the instrument is wildly out of tune.
     */
    fun calibrateToNote(reading: Tuner.Reading) {
        val targetHz = reading.frequency / 2.0.pow(reading.cents / 1200.0)
        val rawHz    = reading.frequency / _calibrationInfo.value.factor.toDouble()
        val newFactor = (targetHz / rawHz).toFloat()
        if (newFactor !in 0.94f..1.06f) return  // >~100 ¢ offset — likely a wrong note
        applyCalibration(newFactor, abs(reading.cents), CalibrationMode.INSTRUMENT)
    }

    fun dismissCalibration() {
        _calibration.value = CalibrationState.Idle
    }

    /** Discard the stored calibration and revert to raw, uncorrected detection. */
    fun clearCalibration() {
        tuner.calibrationFactor = 1f
        _calibrationInfo.value = CalibrationInfo(1f, Float.NaN, calibrated = false)
        prefs.edit { remove(KEY_FACTOR); remove(KEY_ACCURACY); remove(KEY_MODE) }
    }

    private fun applyCalibration(factor: Float, accuracyCents: Float, mode: CalibrationMode) {
        tuner.calibrationFactor = factor
        _calibrationInfo.value = CalibrationInfo(factor, accuracyCents, calibrated = true, mode = mode)
        prefs.edit {
            putFloat(KEY_FACTOR, factor)
            putFloat(KEY_ACCURACY, accuracyCents)
            putString(KEY_MODE, mode.name)
        }
    }

    private fun loadCalibrationInfo() = CalibrationInfo(
        factor = prefs.getFloat(KEY_FACTOR, 1f),
        accuracyCents = prefs.getFloat(KEY_ACCURACY, Float.NaN),
        calibrated = prefs.contains(KEY_FACTOR),
        mode = prefs.getString(KEY_MODE, null)
            ?.let { try { CalibrationMode.valueOf(it) } catch (_: Exception) { null } },
    )

    private fun hasMicPermission() = ContextCompat.checkSelfPermission(
        getApplication(), Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        stopUsageTimer()
        tuner.stop()
        super.onCleared()
    }

    companion object {
        /** Reference-pitch bounds — the practical range of historical/orchestral A4. */
        const val MIN_REFERENCE = 415f
        const val MAX_REFERENCE = 466f

        private const val KEY_REFERENCE = "reference_hz"
        private const val KEY_FACTOR = "calibration_factor"
        private const val KEY_ACCURACY = "calibration_accuracy"
        private const val KEY_MODE = "calibration_mode"

        // Tune-completion detection
        /** Tuner emits ~11 readings/s (4096-sample hop at 44.1 kHz); 40 frames ≈ 3.7 s. */
        private const val STABLE_FRAMES_THRESHOLD = 40
        /** Clarity threshold — below this the reading is too noisy to count as stable. */
        private const val MIN_CLARITY_FOR_STABLE = 0.70f
        /** Maximum cents drift within a stable run before restarting the window. */
        private const val STABILITY_CENTS = 25f

        // Feedback prompt frequency — adapted dynamically from response history
        private const val KEY_PROMPTS_SHOWN   = "fb_prompts_shown"
        private const val KEY_RESPONSES_GIVEN = "fb_responses_given"

        private const val PROMPT_COOLDOWN_ENGAGED_MS = 3 * 60 * 1000L   // response rate > 50 %
        private const val PROMPT_COOLDOWN_NORMAL_MS  = 5 * 60 * 1000L   // response rate 20–50 %
        private const val PROMPT_COOLDOWN_LOW_MS     = 10 * 60 * 1000L  // response rate < 20 %
        private const val PROMPT_CHANCE_ENGAGED      = 0.75f
        private const val PROMPT_CHANCE_NORMAL       = 0.50f
        private const val PROMPT_CHANCE_LOW          = 0.30f
    }
}
