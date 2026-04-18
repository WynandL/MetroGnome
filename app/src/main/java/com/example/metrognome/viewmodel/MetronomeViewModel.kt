package com.example.metrognome.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.MetronomeEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit

data class BeatEvent(val beat: Int)

class MetronomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("metrognome_prefs", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()

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
        engine.onBeat = { beat ->
            viewModelScope.launch {
                _currentBeat.value = beat
                _beatEvents.emit(BeatEvent(beat))
            }
        }
        syncEngineSettings()
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
        }
    }

    fun stopPlayback() {
        if (_isPlaying.value) {
            stopInternal()
            abandonAudioFocus()
        }
    }

    private fun stopInternal() {
        engine.stop()
        _isPlaying.value = false
        _currentBeat.value = 0
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

    // beat is 1-based (1..timeSig); 0 means no accent
    fun setAccentBeat(beat: Int) {
        _accentBeat.value = beat
        engine.accentBeat = beat - 1   // 0 (None) → -1 (disabled); 1..N → 0..N-1
        prefs.edit { putInt("accent_beat", beat) }
    }

    fun setSoundType(type: Int) {
        _soundType.value = type
        engine.soundType = type
        prefs.edit { putInt("sound_type", type) }
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
        super.onCleared()
    }
}