package com.example.metrognome.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.metronome.MetronomeEngine
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.billing.BillingManager
import com.example.metrognome.practice.PracticeSessionManager
import com.example.metrognome.presets.BpmPreset
import com.example.metrognome.presets.BpmPresetsManager
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.billing.PURCHASABLE_ITEM_REGISTRY
import com.example.metrognome.whatsnew.AppWhatsNew
import com.example.metrognome.whatsnew.WhatsNewTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.example.metrognome.analytics.AnalyticsTracker

data class BeatEvent(val beat: Int)
data class PracticeResult(val durationMinutes: Int, val streak: Int, val totalSessions: Int)

class MetronomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("metrognome_prefs", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    val itemTracker = MetroItemTracker(app)
    val billingManager = BillingManager(app)
    private val whatsNewTracker = WhatsNewTracker(app)
    private val presetsManager   = BpmPresetsManager(app)
    private val practiceManager  = PracticeSessionManager(app)

    val isAdFree: StateFlow<Boolean>                    = billingManager.isAdFree
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

    private val _practiceStreak            = MutableStateFlow(practiceManager.getCurrentStreak())
    val practiceStreak: StateFlow<Int>                    = _practiceStreak.asStateFlow()

    private var practiceJob: Job? = null

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
            syncEngineSettings()
            engine.start()
            _isPlaying.value = true
            AnalyticsTracker.logMetronomeStarted(_bpm.value, _soundType.value, _timeSig.value)
            startPlayTimer()
        }
        _practiceGoalSeconds.value = minutes * 60
        _practiceSecondsRemaining.value = minutes * 60
        _isPracticeActive.value = true
        AnalyticsTracker.logPracticeStarted(minutes)
        startPracticeTimer()
    }

    fun cancelPractice() {
        if (_isPracticeActive.value) {
            val elapsed = _practiceGoalSeconds.value - _practiceSecondsRemaining.value
            AnalyticsTracker.logPracticeCancelled(elapsed, _practiceGoalSeconds.value)
        }
        practiceJob?.cancel()
        practiceJob = null
        _isPracticeActive.value = false
        _practiceSecondsRemaining.value = 0
    }

    fun dismissPracticeResult() {
        _pendingPracticeResult.value = null
    }

    fun debugClearPracticeMode() {
        _isPracticeEnabled.value = false
        prefs.edit { putBoolean("feature_practice_enabled", false) }
        practiceManager.debugClear()
        cancelPractice()
        _practiceStreak.value = 0
    }

    fun debugClearSpeedTrainer() {
        _isSpeedTrainerEnabled.value = false
        prefs.edit { putBoolean("feature_speed_trainer_enabled", false) }
    }

    private fun startPracticeTimer() {
        practiceJob?.cancel()
        practiceJob = viewModelScope.launch {
            while (_practiceSecondsRemaining.value > 0 && _isPracticeActive.value) {
                delay(1_000L)
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
        val goalMinutes   = _practiceGoalSeconds.value / 60
        val newStreak     = practiceManager.recordSession()
        val totalSessions = practiceManager.getTotalSessions()
        _isPracticeActive.value = false
        _practiceStreak.value = newStreak
        AnalyticsTracker.logPracticeCompleted(goalMinutes, newStreak, totalSessions)
        _pendingPracticeResult.value = PracticeResult(goalMinutes, newStreak, totalSessions)
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
    private val _accentBeat = MutableStateFlow(prefs.getInt("accent_beat", 1))
    private val _soundType = MutableStateFlow(prefs.getInt("sound_type", 0))
    private val _volume = MutableStateFlow(prefs.getFloat("volume", 0.85f))
    private val _flashOnBeat = MutableStateFlow(prefs.getBoolean("flash", true))
    private val _isMuted = MutableStateFlow(prefs.getBoolean("muted", false))
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", false))

    val bpm: StateFlow<Int> = _bpm.asStateFlow()
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()
    val timeSig: StateFlow<Int> = _timeSig.asStateFlow()
    val accentBeat: StateFlow<Int> = _accentBeat.asStateFlow()
    val soundType: StateFlow<Int> = _soundType.asStateFlow()
    val volume: StateFlow<Float> = _volume.asStateFlow()
    val flashOnBeat: StateFlow<Boolean> = _flashOnBeat.asStateFlow()
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _beatEvents = MutableSharedFlow<BeatEvent>(extraBufferCapacity = 4)
    val beatEvents: SharedFlow<BeatEvent> = _beatEvents.asSharedFlow()

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
        _isPlaying.value = false
        AnalyticsTracker.logMetronomeStopped()
        _currentBeat.value = 0
    }

    private fun startPlayTimer() {
        playTimerJob?.cancel()
        playTimerJob = viewModelScope.launch {
            while (true) {
                delay(10_000L)
                itemTracker.addMetronomeSeconds(10)
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

    fun setTimeSig(sig: Int) {
        _timeSig.value = sig
        engine.timeSignature = sig
        if (_accentBeat.value > sig) setAccentBeat(1)
        prefs.edit { putInt("time_sig", sig) }
    }

    fun setAccentBeat(beat: Int) {
        _accentBeat.value = beat
        engine.accentBeat = beat - 1
        prefs.edit { putInt("accent_beat", beat) }
    }

    fun setSoundType(type: Int) {
        val requiredProduct = PREMIUM_SOUND_REGISTRY.find { it.soundTypeIndex == type }?.productId
        if (requiredProduct != null && requiredProduct !in billingManager.purchasedSoundIds.value) return
        _soundType.value = type
        engine.soundType = type
        prefs.edit { putInt("sound_type", type) }
        AnalyticsTracker.logSoundChanged(type)
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
        engine.accentBeat = _accentBeat.value - 1
        engine.soundType = _soundType.value
        engine.volume = _volume.value
        engine.muted = _isMuted.value
    }

    override fun onCleared() {
        practiceJob?.cancel()
        stopInternal()
        abandonAudioFocus()
        billingManager.release()
        super.onCleared()
    }
}
