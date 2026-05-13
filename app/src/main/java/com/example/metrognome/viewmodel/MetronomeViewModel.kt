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
import com.example.metrognome.audio.MetronomeEngine
import com.example.metrognome.billing.PREMIUM_SOUND_REGISTRY
import com.example.metrognome.billing.BillingManager
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.billing.PURCHASABLE_ITEM_REGISTRY
import com.example.metrognome.whats_new.AppWhatsNew
import com.example.metrognome.whats_new.WhatsNewTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.example.metrognome.analytics.AnalyticsTracker

data class BeatEvent(val beat: Int)

class MetronomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("metrognome_prefs", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()
    val itemTracker = MetroItemTracker(app)
    val billingManager = BillingManager(app)
    private val whatsNewTracker = WhatsNewTracker(app)

    val isAdFree: StateFlow<Boolean>                    = billingManager.isAdFree
    val removeAdsPriceText: StateFlow<String?>           = billingManager.priceText
    val isBillingAvailable: StateFlow<Boolean>           = billingManager.isBillingAvailable
    val isPurchasing: StateFlow<Boolean>                 = billingManager.isPurchasing
    val isBillingConnecting: StateFlow<Boolean>          = billingManager.isConnecting

    // Sounds
    val purchasedSoundIds: StateFlow<Set<String>>        = billingManager.purchasedSoundIds
    val soundPrices: StateFlow<Map<String, String?>>     = billingManager.soundPrices
    val availableSoundProductIds: StateFlow<Set<String>> = billingManager.availableSoundProductIds

    // Items
    val purchasedItemProductIds: StateFlow<Set<String>>  = billingManager.purchasedItemProductIds
    val itemPrices: StateFlow<Map<String, String?>>      = billingManager.itemPrices
    val availableItemProductIds: StateFlow<Set<String>>  = billingManager.availableItemProductIds

    fun purchaseRemoveAds(activity: Activity) = billingManager.launchPurchaseFlow(activity)
    fun purchaseSound(activity: Activity, productId: String) =
        billingManager.launchSoundPurchaseFlow(activity, productId)
    fun purchaseItem(activity: Activity, productId: String) =
        billingManager.launchItemPurchaseFlow(activity, productId)
    fun restorePurchases() = billingManager.restorePurchases()
    fun debugClearAdFree() = billingManager.debugClearAdFree()
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

    // ── Audio Focus (cleanly encapsulated) ─────────────────────────────────────

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

    // Tap-tempo state
    private val tapTimes = ArrayDeque<Long>(8)

    init {
        // If a premium sound was persisted but its purchase is no longer active (e.g. revoked),
        // fall back to Classic before the engine ever sees the saved value.
        val savedType = _soundType.value
        val requiredProduct = PREMIUM_SOUND_REGISTRY.find { it.soundTypeIndex == savedType }?.productId
        if (requiredProduct != null && requiredProduct !in billingManager.purchasedSoundIds.value) {
            _soundType.value = 0
            prefs.edit { putInt("sound_type", 0) }
        }

        // When billing confirms an item purchase (new or restored), force-unlock it in the
        // tracker so it appears on screen and triggers the celebration overlay.
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

    /** DEV: wipe all progress counters — simulates a clean installation. */
    fun resetAllProgress() {
        itemTracker.resetAllProgress()
        _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
        _unlockQueue.value = emptyList()
    }

    /** DEV: fire the celebration overlay for a specific registry item (no side effects on celebrated set). */
    fun previewUnlockCelebration(index: Int) {
        if (METRO_ITEM_REGISTRY.isEmpty()) return
        val entry = METRO_ITEM_REGISTRY[index.coerceIn(0, METRO_ITEM_REGISTRY.lastIndex)]
        if (entry !in _unlockQueue.value) {
            _unlockQueue.value += entry
        }
    }

    fun checkForNewUnlocks() {
        _activeItemIds.value = itemTracker.unlockedIds(METRO_ITEM_REGISTRY)
        val celebrated = itemTracker.celebratedIds()
        // Purge: remove entries that are celebrated OR no longer unlocked (e.g. after dev reset)
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
                delay(10_000L)   // tick every 10 seconds
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

    // beat is 1-based (1...timeSig); 0 means no accent
    fun setAccentBeat(beat: Int) {
        _accentBeat.value = beat
        engine.accentBeat = beat - 1   // 0 (None) → -1 (disabled); 1..N → 0..N-1
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
        stopInternal()
        abandonAudioFocus()
        billingManager.release()
        super.onCleared()
    }
}