package com.example.metrognome.viewmodel

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.metrognome.audio.rhythm.RhythmDetector
import com.example.metrognome.audio.selftest.MicCalibration
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.metronome.MetronomeEngine
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.billing.BillingManager
import com.example.metrognome.practice.PracticeSessionManager
import com.example.metrognome.theory.Meter
import com.example.metrognome.theory.MeterTheory
import com.example.metrognome.presets.BpmPreset
import com.example.metrognome.presets.BpmPresetsManager
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.billing.PURCHASABLE_ITEM_REGISTRY
import com.example.metrognome.debug.mic.MicDiagnosticsBuffer
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.whatsnew.AppWhatsNew
import com.example.metrognome.whatsnew.WhatsNewTracker
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.example.metrognome.analytics.AnalyticsTracker

data class BeatEvent(val beat: Int)
data class PracticeResult(
    val durationMinutes: Int,
    val streak: Int,
    val totalSessions: Int,
    // Average absolute timing deviation (ms) when mic mode ran this session; null when
    // the mic was not used. Drives the graded timing bonus below.
    val micAvgDeviationMs: Float? = null,
    val micHitCount: Int = 0,
    // Graded timing bonus actually credited this session (after the daily cap), and the
    // 0..1 performance share behind it. 0 = hidden.
    val performanceBonus: Int = 0,
    val performanceFraction: Float = 0f,
    // Groove Check grade (0..100, length-independent) and its plain-language read, shown to the
    // player. grooveScore 0 = the mic did not produce a qualifying session (hidden by the overlay).
    val grooveScore: Int = 0,
    val grooveRead: String = "",
)

class MetronomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("metrognome_prefs", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    val itemTracker = MetroItemTracker(app)
    private val dailyLog    = com.example.metrognome.points.DailyActivityLog(app)
    val billingManager = BillingManager(app)
    private val whatsNewTracker = WhatsNewTracker(app)
    private val presetsManager   = BpmPresetsManager(app)
    private val practiceManager  = PracticeSessionManager(app)
    private val rewardManager    = com.example.metrognome.points.rewards.RewardManager(app, viewModelScope)

    val isAdFree: StateFlow<Boolean> = combine(billingManager.isAdFree, rewardManager.isAdFreeActive) { billing, reward -> billing || reward }
        .stateIn(viewModelScope, SharingStarted.Eagerly, billingManager.isAdFree.value || rewardManager.isAdFreeActive.value)
    val rewardGranted: SharedFlow<Long> = rewardManager.rewardGranted

    private val usageDayTracker  = com.example.metrognome.points.UsageDayTracker(app)
    val rewardedAdManager = com.example.metrognome.ads.RewardedAdManager(app).also { it.preload() }
    private val pointsManager    = com.example.metrognome.points.PointsManager(app, rewardedAdManager)
    private val activityLogger   = com.example.metrognome.usage.ActivitySummaryLogger(app)
    private val _gnoteCount = MutableStateFlow(pointsManager.getSnapshot().total)
    val gnoteCount: StateFlow<Int> = _gnoteCount.asStateFlow()
    val rewardedAdLoaded: StateFlow<Boolean> = rewardedAdManager.adLoaded

    val removeAdsPriceText: StateFlow<String?>           = billingManager.priceText
    val isBillingAvailable: StateFlow<Boolean>           = billingManager.isBillingAvailable
    val isPurchasing: StateFlow<Boolean>                 = billingManager.isPurchasing
    val isBillingConnecting: StateFlow<Boolean>          = billingManager.isConnecting

    // Free feature gates — enabled once via first-use dialog, stored locally
    private val _isPresetsEnabled = MutableStateFlow(prefs.getBoolean("feature_presets_enabled", false))
    val isPresetsEnabled: StateFlow<Boolean> = _isPresetsEnabled.asStateFlow()

    private val _isPracticeEnabled = MutableStateFlow(prefs.getBoolean("feature_practice_enabled", false))
    val isPracticeEnabled: StateFlow<Boolean> = _isPracticeEnabled.asStateFlow()

    private val _isSpeedTrainerEnabled = MutableStateFlow(prefs.getBoolean("feature_speed_trainer_enabled", false))
    val isSpeedTrainerEnabled: StateFlow<Boolean> = _isSpeedTrainerEnabled.asStateFlow()

    private val _isPracticeActive          = MutableStateFlow(false)
    val isPracticeActive: StateFlow<Boolean>             = _isPracticeActive.asStateFlow()

    private val _practiceSecondsRemaining  = MutableStateFlow(0)
    val practiceSecondsRemaining: StateFlow<Int>         = _practiceSecondsRemaining.asStateFlow()

    private val _practiceGoalSeconds       = MutableStateFlow(0)
    val practiceGoalSeconds: StateFlow<Int>              = _practiceGoalSeconds.asStateFlow()

    private val _pendingPracticeResult     = MutableStateFlow<PracticeResult?>(null)
    val pendingPracticeResult: StateFlow<PracticeResult?> = _pendingPracticeResult.asStateFlow()

    private val _practiceStreak = MutableStateFlow(practiceManager.getCurrentStreak())
    val practiceStreak: StateFlow<Int> = _practiceStreak.asStateFlow()

    private val _bestStreak = MutableStateFlow(practiceManager.getBestStreak())
    val bestStreak: StateFlow<Int> = _bestStreak.asStateFlow()

    private val _practicedEpochDays = MutableStateFlow(practiceManager.getPracticedEpochDays())
    val practicedEpochDays: StateFlow<Set<Long>> = _practicedEpochDays.asStateFlow()


    private val _streakCardExpanded = MutableStateFlow(prefs.getBoolean("streak_card_expanded", false))
    val streakCardExpanded: StateFlow<Boolean> = _streakCardExpanded.asStateFlow()

    fun toggleStreakCard() {
        val next = !_streakCardExpanded.value
        _streakCardExpanded.value = next
        prefs.edit { putBoolean("streak_card_expanded", next) }
    }

    private var practiceJob: Job? = null

    // ── Practice mic pipeline (only live while a mic-enabled practice session runs) ──
    private var practiceDetector: RhythmDetector? = null
    private var lastBeatMs = 0L
    private var practiceLatencyMs = 0f
    private val practiceDeviations = mutableListOf<Float>()

    /** Dev easter-egg gate — mirrors SpeedTrainerViewModel so Practice feeds the Mic Timing Log. */
    private val isDevMode get() = DevEasterEgg.isDevModeActive(getApplication())

    // While a mic-measured session (Practice / Speed Trainer) is live, the engine plays the
    // classic click regardless of the user's chosen sound, so the spectral clap detector gets
    // the click profile it is tuned for. The saved sound (_soundType) is never mutated and is
    // restored when the session stops. See setMicSoundOverride / effectiveSoundType.
    private var forceClassicForMic = false

    private val _presets = MutableStateFlow(presetsManager.loadPresets())
    val presets: StateFlow<List<BpmPreset>> = _presets.asStateFlow()

    private val _presetLongPressHintSeen = MutableStateFlow(prefs.getBoolean("preset_long_press_hint_seen", false))
    val presetLongPressHintSeen: StateFlow<Boolean> = _presetLongPressHintSeen.asStateFlow()

    // Sounds
    val purchasedSoundIds: StateFlow<Set<String>>        = billingManager.purchasedSoundIds
    val soundPrices: StateFlow<Map<String, String?>>     = billingManager.soundPrices
    val availableSoundProductIds: StateFlow<Set<String>> = billingManager.availableSoundProductIds

    // Items
    val purchasedItemProductIds: StateFlow<Set<String>>  = billingManager.purchasedItemProductIds
    val itemPrices: StateFlow<Map<String, String?>>      = billingManager.itemPrices
    val availableItemProductIds: StateFlow<Set<String>>  = billingManager.availableItemProductIds

    fun enablePresets() {
        _isPresetsEnabled.value = true
        prefs.edit { putBoolean("feature_presets_enabled", true) }
        AnalyticsTracker.logFeatureEnabled("presets")
    }

    fun enablePractice() {
        _isPracticeEnabled.value = true
        prefs.edit { putBoolean("feature_practice_enabled", true) }
        AnalyticsTracker.logFeatureEnabled("practice")
    }

    fun enableSpeedTrainer() {
        _isSpeedTrainerEnabled.value = true
        prefs.edit { putBoolean("feature_speed_trainer_enabled", true) }
        AnalyticsTracker.logFeatureEnabled("speed_trainer")
    }

    fun purchaseRemoveAds(activity: Activity) = billingManager.launchPurchaseFlow(activity)
    fun purchaseSound(activity: Activity, productId: String) =
        billingManager.launchSoundPurchaseFlow(activity, productId)
    fun purchaseItem(activity: Activity, productId: String) =
        billingManager.launchItemPurchaseFlow(activity, productId)
    fun restorePurchases() = billingManager.restorePurchases()
    fun reconcilePurchases() { viewModelScope.launch { billingManager.reconcileInBackground() } }
    fun refreshReward()      { rewardManager.refresh() }
    fun recordUsageDay() {
        usageDayTracker.recordDay()
        _gnoteCount.value = pointsManager.getSnapshot().total
        activityLogger.log()
    }
    fun debugResetReview() = com.example.metrognome.review.AppReviewManager(getApplication()).debugReset()
    fun debugClearAdFree() = billingManager.debugClearAdFree()
    fun debugClearPresets() {
        _isPresetsEnabled.value = false
        prefs.edit { putBoolean("feature_presets_enabled", false) }
        presetsManager.debugClear()
        _presets.value = emptyList()
    }
    fun debugClearSoundPurchases() {
        billingManager.debugClearSoundPurchases()
        val premiumIndexes = PREMIUM_SOUND_REGISTRY.map { it.soundTypeIndex }.toSet()
        if (_soundType.value in premiumIndexes) setSoundType(0)
    }
    fun debugClearItemPurchases() {
        billingManager.debugClearItemPurchases()
        itemTracker.debugClearItemPurchases()
        _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
    }

    fun previewSound(soundTypeIndex: Int) = engine.playPreview(soundTypeIndex)

    fun savePreset(name: String, bpm: Int): Boolean {
        val saved = presetsManager.savePreset(name, bpm)
        if (saved) {
            _presets.value = presetsManager.loadPresets()
            AnalyticsTracker.logPresetSaved(bpm)
        }
        return saved
    }

    fun selectPreset(preset: BpmPreset) {
        setBpm(preset.bpm)
        AnalyticsTracker.logPresetLoaded(preset.bpm)
    }

    fun deletePreset(index: Int) {
        presetsManager.deletePreset(index)
        _presets.value = presetsManager.loadPresets()
    }

    fun markPresetLongPressHintSeen() {
        if (_presetLongPressHintSeen.value) return
        _presetLongPressHintSeen.value = true
        prefs.edit { putBoolean("preset_long_press_hint_seen", true) }
    }

    fun startPractice(minutes: Int) {
        if (!_isPlaying.value) {
            if (!requestAudioFocus()) return
            // Force the classic click before syncing so the engine starts on it (no-op if mic off).
            setMicSoundOverride(true)
            syncEngineSettings()
            engine.start()
            _isPlaying.value = true
            AnalyticsTracker.logMetronomeStarted(_bpm.value, _soundType.value, _timeSig.value)
            startPlayTimer()
        } else {
            // Metronome already running — swap the live click to classic for the mic session.
            setMicSoundOverride(true)
        }
        _practiceGoalSeconds.value = minutes * 60
        _practiceSecondsRemaining.value = minutes * 60
        _isPracticeActive.value = true
        AnalyticsTracker.logPracticeStarted(minutes)
        startPracticeTimer()
        startPracticeMic()
    }

    fun cancelPractice() {
        if (_isPracticeActive.value) {
            val elapsed = _practiceGoalSeconds.value - _practiceSecondsRemaining.value
            AnalyticsTracker.logPracticeCancelled(elapsed, _practiceGoalSeconds.value)
        }
        practiceJob?.cancel()
        practiceJob = null
        stopPracticeMic()
        _isPracticeActive.value = false
        _practiceSecondsRemaining.value = 0
    }

    // ── Practice mic helpers ────────────────────────────────────────────────────

    /** Start mic listening for this practice session, if mic mode is active and permitted. */
    @Suppress("MissingPermission")
    private fun startPracticeMic() {
        val micCal = MicCalibration.read(getApplication())
        practiceDeviations.clear()
        practiceLatencyMs = 0f
        if (!(micCal.isActive && hasMicPermission())) return

        practiceLatencyMs = micCal.latencyMs
        // Spectral mode: the detector rejects the metronome click by its signature, so a hit
        // landing on the beat still counts (the old time-suppression window dropped those).
        val det = RhythmDetector(classifyClaps = true)
        practiceDetector = det
        det.start()
        viewModelScope.launch {
            det.detections.collect { onsetMs -> onPracticeOnset(onsetMs) }
        }
        if (isDevMode) {
            MicDiagnosticsBuffer.startSession("Practice", det.echoCancellationActive)
            viewModelScope.launch { det.amplitude.collect { MicDiagnosticsBuffer.updateAmplitude(it) } }
            det.debugOnClickRejected = { onset, onsetMs ->
                MicDiagnosticsBuffer.logClickRejected(onsetMs, onset.lowRms, onset.highRms, onset.peakRatio)
            }
        }
    }

    /** Collect one corrected timing deviation against the most recent beat. */
    private fun onPracticeOnset(onsetMs: Long) {
        val raw = (onsetMs - lastBeatMs).toFloat()
        if (kotlin.math.abs(raw) > 500f) {            // outside a generous window — not a beat hit
            if (isDevMode) MicDiagnosticsBuffer.logOnsetRejected(onsetMs, raw)
            return
        }
        val calibrated = raw - practiceLatencyMs
        if (isDevMode) MicDiagnosticsBuffer.logOnsetAccepted(onsetMs, raw, calibrated)
        // A very accurate clap fires a celebratory firework (visual only).
        if (kotlin.math.abs(calibrated) <= com.example.metrognome.groove.GrooveScorer.GREAT_HIT_MS) {
            _greatHit.tryEmit(Unit)
        }
        practiceDeviations.add(calibrated)
    }

    private fun stopPracticeMic() {
        if (isDevMode && practiceDetector != null) MicDiagnosticsBuffer.endSession()
        practiceDetector?.stop()
        practiceDetector = null
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    fun dismissPracticeResult() {
        _pendingPracticeResult.value = null
    }

    // In-memory only — SharedPrefs untouched, real data survives app restart.
    fun debugSimulateStreak(days: Int) {
        _practiceStreak.value = days
        _bestStreak.value = maxOf(_bestStreak.value, days)
        val today = PracticeSessionManager.currentEpochDay()
        _practicedEpochDays.value = (0 until days).map { today - it }.toSet()
    }

    fun debugClearStreakSim() {
        _practiceStreak.value = practiceManager.getCurrentStreak()
        _bestStreak.value = practiceManager.getBestStreak()
        _practicedEpochDays.value = practiceManager.getPracticedEpochDays()
    }

    fun debugClearPracticeMode() {
        _isPracticeEnabled.value = false
        prefs.edit { putBoolean("feature_practice_enabled", false) }
        practiceManager.debugClear()
        cancelPractice()
        _practiceStreak.value = 0
        _bestStreak.value = 0
        _practicedEpochDays.value = emptySet()
    }

    fun debugClearSpeedTrainer() {
        _isSpeedTrainerEnabled.value = false
        prefs.edit { putBoolean("feature_speed_trainer_enabled", false) }
    }

    private fun startPracticeTimer() {
        practiceJob?.cancel()
        practiceJob = viewModelScope.launch {
            while (_practiceSecondsRemaining.value > 0 && _isPracticeActive.value) {
                delay(1.seconds)
                if (_isPlaying.value && _isPracticeActive.value) {
                    _practiceSecondsRemaining.value -= 1
                }
            }
            if (_isPracticeActive.value && _practiceSecondsRemaining.value == 0) {
                completePractice()
            }
        }
    }

    private fun completePractice() {
        val goalMinutes = _practiceGoalSeconds.value / 60

        // Snapshot baseline BEFORE writing so day-rollover resets correctly.
        val activityBefore = dailyLog.todayActivity(itemTracker)

        val newStreak     = practiceManager.recordSession()
        val totalSessions = practiceManager.getTotalSessions()
        itemTracker.addPracticeMinutes(goalMinutes)

        val activityAfter = dailyLog.todayActivity(itemTracker)

        // Points banner: time-based, delta between before/after capped at daily limit.
        run {
            val limitMins  = com.example.metrognome.points.PointsLimits.PRACTICE_MINUTES_PER_DAY
            val rate       = com.example.metrognome.points.PointsConfig.PER_PRACTICE_MINUTE
            val prevBeats  = (activityBefore.practiceMinutesToday * rate).coerceAtMost(limitMins * rate)
            val todayBeats = (activityAfter.practiceMinutesToday  * rate).coerceAtMost(limitMins * rate)
            val earnedNow  = (todayBeats - prevBeats).coerceAtLeast(0)
            val cappedMins = activityAfter.practiceMinutesToday.coerceAtMost(limitMins)
            com.example.metrognome.points.PointsBannerQueue.post(
                com.example.metrognome.points.PointsBannerData(
                    pointsEarned     = earnedNow,
                    activityLabel    = "Practice",
                    todayCount       = cappedMins,
                    dailyLimit       = limitMins,
                    limitJustReached = cappedMins == limitMins,
                )
            )
        }

        // Snapshot mic timing (if it ran) before tearing the detector down.
        val micHits = practiceDeviations.size
        val micAvgDeviation = practiceDeviations
            .map { kotlin.math.abs(it) }
            .takeIf { it.isNotEmpty() }
            ?.average()?.toFloat()
        // Adaptive Groove Check grade from the SIGNED per-hit deviations (consistency-first; see
        // groove/GrooveScorer). The grooveScore (0..100) is shown to the player; the Gnotes bonus
        // below is a separate, length-bounded reward.
        val realGroove = com.example.metrognome.groove.GrooveScorer.score(practiceDeviations.toList())
        stopPracticeMic()

        // Dev "simulate timing": with no real hits on a device/emulator that has no usable mic,
        // synthesize a plausible grade so the bonus + result UI can still be exercised. Only the
        // PERFORMANCE is synthesized; the session length stays real (max = session minutes).
        val simulate = isDevMode &&
            com.example.metrognome.audio.selftest.SelfTestCalibrationStore(getApplication()).devSimulateTiming &&
            micHits < com.example.metrognome.groove.GrooveScorer.MIN_HITS
        val groove = if (simulate) {
            val f = (40..90).random() / 100f
            realGroove.copy(
                grooveScore = Math.round(f * 100),
                fraction = f,
                hitCount = 12,
                qualified = true,
                read = "Simulated timing",
            )
        } else realGroove

        val rawBonus = com.example.metrognome.groove.GrooveScorer.bonusGnotes(groove.fraction, groove.hitCount, goalMinutes)
        var perfBonusEarned = 0
        if (rawBonus > 0) {
            val limit  = com.example.metrognome.points.PointsLimits.PERFORMANCE_BONUS_PER_DAY
            val before = dailyLog.todayActivity(itemTracker).performanceBonusToday.coerceAtMost(limit)
            itemTracker.addPerformanceBonus(rawBonus)
            val after  = dailyLog.todayActivity(itemTracker).performanceBonusToday.coerceAtMost(limit)
            perfBonusEarned = (after - before).coerceAtLeast(0)
            if (perfBonusEarned > 0) {
                AnalyticsTracker.logTimingBonusEarned(
                    source = "practice",
                    bonus = perfBonusEarned,
                    fraction = groove.fraction,
                    hitCount = groove.hitCount,
                )
                com.example.metrognome.points.PointsBannerQueue.post(
                    com.example.metrognome.points.PointsBannerData(
                        pointsEarned     = perfBonusEarned,
                        activityLabel    = "Timing Bonus",
                        todayCount       = after,
                        dailyLimit       = limit,
                        limitJustReached = after == limit && before < limit,
                    )
                )
            }
        }

        _isPracticeActive.value = false
        _practiceStreak.value = newStreak
        _bestStreak.value = practiceManager.getBestStreak()
        _practicedEpochDays.value = practiceManager.getPracticedEpochDays()
        AnalyticsTracker.logPracticeCompleted(goalMinutes, newStreak, totalSessions)
        _pendingPracticeResult.value = PracticeResult(
            durationMinutes = goalMinutes,
            streak = newStreak,
            totalSessions = totalSessions,
            micAvgDeviationMs = micAvgDeviation,
            micHitCount = micHits,
            performanceBonus = perfBonusEarned,
            performanceFraction = groove.fraction,
            grooveScore = if (groove.qualified) groove.grooveScore else 0,
            grooveRead = groove.read,
        )
        checkForNewUnlocks()
    }

    private val _activeItemIds = MutableStateFlow(itemTracker.unlockedIds(METRO_ITEM_REGISTRY))
    val activeItemIds: StateFlow<Set<String>> = _activeItemIds.asStateFlow()

    private val _unlockQueue = MutableStateFlow<List<MetroItemEntry>>(emptyList())
    val unlockQueue: StateFlow<List<MetroItemEntry>> = _unlockQueue.asStateFlow()

    private val _pendingWhatsNew = MutableStateFlow(whatsNewTracker.pendingKey(AppWhatsNew.ALL))
    val pendingWhatsNew: StateFlow<String?> = _pendingWhatsNew.asStateFlow()

    private val _cheatModeEnabled = MutableStateFlow(itemTracker.isCheatModeEnabled())
    val cheatModeEnabled: StateFlow<Boolean> = _cheatModeEnabled.asStateFlow()

    fun toggleCheatMode() {
        itemTracker.toggleCheatMode()
        _cheatModeEnabled.value = itemTracker.isCheatModeEnabled()
        _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
    }

    private var playTimerJob: Job? = null

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ── Audio Focus ────────────────────────────────────────────────────────────

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stopInternal()
        }
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        } else null

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    // ── Observable state ───────────────────────────────────────────────────────

    private val _bpm = MutableStateFlow(prefs.getInt("bpm", 120))
    private val _isPlaying = MutableStateFlow(false)
    private val _currentBeat = MutableStateFlow(0)
    private val _timeSig = MutableStateFlow(prefs.getInt("time_sig", 4))
    private val _timeSigDenom = MutableStateFlow(prefs.getInt("time_sig_denom", 4))
    private val _accentBeats = MutableStateFlow(loadAccentBeats())
    private val _soundType = MutableStateFlow(prefs.getInt("sound_type", 0))
    private val _volume = MutableStateFlow(prefs.getFloat("volume", 0.85f))
    private val _flashOnBeat = MutableStateFlow(prefs.getBoolean("flash", true))
    private val _isMuted = MutableStateFlow(prefs.getBoolean("muted", false))
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", false))

    val bpm: StateFlow<Int> = _bpm.asStateFlow()
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()
    val timeSig: StateFlow<Int> = _timeSig.asStateFlow()
    val timeSigDenom: StateFlow<Int> = _timeSigDenom.asStateFlow()
    val accentBeats: StateFlow<Set<Int>> = _accentBeats.asStateFlow()
    val soundType: StateFlow<Int> = _soundType.asStateFlow()
    val volume: StateFlow<Float> = _volume.asStateFlow()
    val flashOnBeat: StateFlow<Boolean> = _flashOnBeat.asStateFlow()
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _beatEvents = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 4)
    val beatEvents: SharedFlow<BeatEvent> = _beatEvents.asSharedFlow()

    // Emitted when a practice clap lands very close to the beat - drives the celebratory firework
    // in the Gnome canvas. Purely visual; no effect on scoring. See FireworkEffect.kt.
    private val _greatHit = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val greatHit: SharedFlow<Unit> = _greatHit.asSharedFlow()

    private val tapTimes = ArrayDeque<Long>(8)

    init {
        // One-time migration: users who purchased presets or practice mode in v4.x get
        // auto-enabled in v5.0 so they never see the Enable dialog for something they paid for.
        val oldBilling = app.getSharedPreferences("billing_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("feature_presets_enabled", false) &&
            oldBilling.getBoolean("presets_unlocked", false)) {
            prefs.edit { putBoolean("feature_presets_enabled", true) }
            _isPresetsEnabled.value = true
        }
        if (!prefs.getBoolean("feature_practice_enabled", false) &&
            oldBilling.getBoolean("practice_mode_unlocked", false)) {
            prefs.edit { putBoolean("feature_practice_enabled", true) }
            _isPracticeEnabled.value = true
        }

        val savedType = _soundType.value
        val requiredProduct = PREMIUM_SOUND_REGISTRY.find { it.soundTypeIndex == savedType }?.productId
        if (requiredProduct != null && requiredProduct !in billingManager.purchasedSoundIds.value) {
            _soundType.value = 0
            prefs.edit { putInt("sound_type", 0) }
        }

        viewModelScope.launch {
            com.example.metrognome.points.PointsBannerQueue.events.collect { _ ->
                _gnoteCount.value = pointsManager.getSnapshot().total
            }
        }

        viewModelScope.launch {
            billingManager.purchasedItemProductIds.collect { purchasedProductIds ->
                PURCHASABLE_ITEM_REGISTRY.forEach { def ->
                    if (def.productId in purchasedProductIds) {
                        itemTracker.forceUnlock(def.itemId)
                    }
                }
                _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
                checkForNewUnlocks()
            }
        }

        engine.onBeat = { beat ->
            // Stamp the beat for practice-mic deviation math. The detector rejects the click
            // spectrally (classifyClaps), so no time-suppression window is needed any more.
            lastBeatMs = SystemClock.elapsedRealtime()
            if (isDevMode && practiceDetector != null) {
                MicDiagnosticsBuffer.logBeat(
                    beat = beat,
                    suppressUntilMs = 0L,   // spectral mode has no suppression window
                    estimatedPlayMs = lastBeatMs + practiceLatencyMs.toLong(),
                )
            }
            viewModelScope.launch {
                _currentBeat.value = beat
                _beatEvents.emit(BeatEvent(beat))
            }
        }
        syncEngineSettings()
        checkForNewUnlocks()
    }

    fun markWhatsNewShown(versionKey: String) {
        whatsNewTracker.markShown(versionKey)
        _pendingWhatsNew.value = whatsNewTracker.pendingKey(AppWhatsNew.ALL)
    }

    fun debugResetWhatsNew() {
        whatsNewTracker.resetShown(AppWhatsNew.ALL.last())
        _pendingWhatsNew.value = whatsNewTracker.pendingKey(AppWhatsNew.ALL)
    }

    fun resetAllProgress() {
        itemTracker.resetAllProgress()
        _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
        _unlockQueue.value = emptyList()
    }

    fun previewUnlockCelebration(index: Int) {
        if (METRO_ITEM_REGISTRY.isEmpty()) return
        val entry = METRO_ITEM_REGISTRY[index.coerceIn(0, METRO_ITEM_REGISTRY.lastIndex)]
        if (entry !in _unlockQueue.value) {
            _unlockQueue.value += entry
        }
    }

    fun checkForNewUnlocks() {
        _practiceStreak.value = practiceManager.getCurrentStreak()
        _bestStreak.value = practiceManager.getBestStreak()
        _practicedEpochDays.value = practiceManager.getPracticedEpochDays()
        _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
        val celebrated = itemTracker.celebratedIds()
        _unlockQueue.value = _unlockQueue.value.filter { it.item.id in _activeItemIds.value && it.item.id !in celebrated }
        val newEntries = METRO_ITEM_REGISTRY.filter { it.item.id in _activeItemIds.value && it.item.id !in celebrated }
        if (newEntries.isEmpty()) return
        val existing = _unlockQueue.value.map { it.item.id }.toSet()
        val toAdd = newEntries.filter { it.item.id !in existing }
        if (toAdd.isNotEmpty()) {
            _unlockQueue.value += toAdd
            toAdd.forEach { AnalyticsTracker.logItemUnlocked(it.item.id, it.item.displayName) }
        }
    }

    fun markCelebrated(id: String) {
        itemTracker.markCelebrated(id)
        _unlockQueue.value = _unlockQueue.value.filter { it.item.id != id }
    }

    // ── Public actions ─────────────────────────────────────────────────────────

    fun togglePlay() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            if (!requestAudioFocus()) return
            syncEngineSettings()
            engine.start()
            _isPlaying.value = true
            AnalyticsTracker.logMetronomeStarted(_bpm.value, _soundType.value, _timeSig.value)
            startPlayTimer()
        }
    }

    fun stopPlayback() {
        if (_isPlaying.value) {
            stopInternal()
            abandonAudioFocus()
        }
    }

    private fun stopInternal() {
        stopPlayTimer()
        engine.stop()
        // Every stop path (practice/trainer complete or cancel, manual stop) funnels through
        // here, so this is the single point that lifts the mic classic-click override.
        forceClassicForMic = false
        _isPlaying.value = false
        AnalyticsTracker.logMetronomeStopped()
        _currentBeat.value = 0
    }

    private fun startPlayTimer() {
        playTimerJob?.cancel()
        playTimerJob = viewModelScope.launch {
            while (true) {
                delay(10.seconds)
                if (!SessionFlags.speedTrainerActive) itemTracker.addMetronomeSeconds(10)
                _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
                checkForNewUnlocks()
            }
        }
    }

    private fun stopPlayTimer() {
        playTimerJob?.cancel()
        playTimerJob = null
    }

    fun setBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(20, 300)
        _bpm.value = clamped
        engine.bpm = clamped
        prefs.edit { putInt("bpm", clamped) }
    }

    fun tapTempo() {
        val now = System.currentTimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2500) tapTimes.clear()

        tapTimes.addLast(now)
        if (tapTimes.size > 6) tapTimes.removeFirst()

        if (tapTimes.size >= 2) {
            val intervals = (1 until tapTimes.size).map { tapTimes[it] - tapTimes[it - 1] }
            val avgInterval = intervals.average()
            val tappedBpm = (60000.0 / avgInterval).toInt().coerceIn(20, 300)
            setBpm(tappedBpm)
        }
    }

    /**
     * Set the time signature. The bar length (top) drives the engine's pulse count, and the
     * accents are reset to the meter's natural grouping (see [MeterTheory.defaultAccents]).
     * The user can then fine-tune individual accents with [toggleAccent].
     */
    fun setMeter(top: Int, bottom: Int) {
        val accents = MeterTheory.defaultAccents(Meter(top, bottom))
        _timeSig.value = top
        _timeSigDenom.value = bottom
        _accentBeats.value = accents
        engine.timeSignature = top
        engine.accentBeats = accents
        prefs.edit {
            putInt("time_sig", top)
            putInt("time_sig_denom", bottom)
            putString("accent_beats", accents.joinToString(","))
        }
    }

    /** Flip the accent on a single 0-based pulse, leaving the rest of the pattern intact. */
    fun toggleAccent(beatIndex: Int) {
        val next = _accentBeats.value.toMutableSet()
        if (!next.add(beatIndex)) next.remove(beatIndex)
        _accentBeats.value = next
        engine.accentBeats = next
        prefs.edit { putString("accent_beats", next.joinToString(",")) }
    }

    /**
     * Restore the accent pattern saved in prefs, or derive the meter's natural accents when
     * none is stored yet (first run, or upgrade from the old single-accent setting).
     */
    private fun loadAccentBeats(): Set<Int> {
        prefs.getString("accent_beats", null)?.let { csv ->
            return csv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
        val top = prefs.getInt("time_sig", 4)
        val bottom = prefs.getInt("time_sig_denom", 4)
        return MeterTheory.defaultAccents(Meter(top, bottom))
    }

    fun setSoundType(type: Int) {
        val requiredProduct = PREMIUM_SOUND_REGISTRY.find { it.soundTypeIndex == type }?.productId
        if (requiredProduct != null && requiredProduct !in billingManager.purchasedSoundIds.value) return
        _soundType.value = type
        // Respect an active mic override: keep playing the classic click, but remember the pick
        // so it takes effect the moment the override lifts.
        engine.soundType = effectiveSoundType()
        prefs.edit { putInt("sound_type", type) }
        AnalyticsTracker.logSoundChanged(type)
    }

    /** The click the engine should actually play: classic while a mic session overrides it. */
    private fun effectiveSoundType(): Int = if (forceClassicForMic) 0 else _soundType.value

    /**
     * Engage (or lift) the classic-click override for a mic-measured session. The override only
     * takes hold when the mic is genuinely active and permitted, so this is a no-op for users who
     * have mic accuracy off. The user's saved sound is untouched; [stopInternal] and the lift call
     * restore it. Idempotent and safe to call repeatedly (e.g. on every trainer step).
     */
    fun setMicSoundOverride(sessionActive: Boolean) {
        val shouldForce = sessionActive &&
                MicCalibration.read(getApplication()).isActive && hasMicPermission()
        if (forceClassicForMic == shouldForce) return
        forceClassicForMic = shouldForce
        if (_isPlaying.value) engine.soundType = effectiveSoundType()
    }

    fun setVolume(v: Float) {
        _volume.value = v
        engine.volume = v
        prefs.edit { putFloat("volume", v) }
    }

    fun setFlashOnBeat(on: Boolean) {
        _flashOnBeat.value = on
        prefs.edit { putBoolean("flash", on) }
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        engine.muted = next
        prefs.edit { putBoolean("muted", next) }
    }

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        prefs.edit { putBoolean("keep_screen_on", on) }
    }

    private fun syncEngineSettings() {
        engine.bpm = _bpm.value
        engine.timeSignature = _timeSig.value
        engine.accentBeats = _accentBeats.value
        engine.soundType = effectiveSoundType()
        engine.volume = _volume.value
        engine.muted = _isMuted.value
    }

    override fun onCleared() {
        practiceJob?.cancel()
        stopPracticeMic()
        stopInternal()
        abandonAudioFocus()
        billingManager.release()
        super.onCleared()
    }
}
