package com.example.metrognome.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.metrognome.audio.MetronomeEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.core.content.edit

data class BeatEvent(val beat: Int)

class MetronomeViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("metrognome_prefs", Context.MODE_PRIVATE)
    private val engine = MetronomeEngine()

    // ── Observable state ───────────────────────────────────────────────────────
    private val _bpm = MutableStateFlow(prefs.getInt("bpm", 120))
    private val _isPlaying = MutableStateFlow(false)
    private val _currentBeat = MutableStateFlow(0)
    private val _timeSig = MutableStateFlow(prefs.getInt("time_sig", 4))
    private val _accentFirst = MutableStateFlow(prefs.getBoolean("accent", true))
    private val _soundType = MutableStateFlow(prefs.getInt("sound_type", 0))
    private val _volume = MutableStateFlow(prefs.getFloat("volume", 0.85f))
    private val _flashOnBeat = MutableStateFlow(prefs.getBoolean("flash", true))

    val bpm: StateFlow<Int> = _bpm.asStateFlow()
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()
    val timeSig: StateFlow<Int> = _timeSig.asStateFlow()
    val accentFirst: StateFlow<Boolean> = _accentFirst.asStateFlow()
    val soundType: StateFlow<Int> = _soundType.asStateFlow()
    val volume: StateFlow<Float> = _volume.asStateFlow()
    val flashOnBeat: StateFlow<Boolean> = _flashOnBeat.asStateFlow()

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
            engine.stop()
            _isPlaying.value = false
            _currentBeat.value = 0
        } else {
            syncEngineSettings()
            engine.start()
            _isPlaying.value = true
        }
    }

    fun setBpm(newBpm: Int) {
        val clamped = newBpm.coerceIn(20, 300)
        _bpm.value = clamped
        engine.bpm = clamped
        prefs.edit { putInt("bpm", clamped) }
    }

    fun tapTempo() {
        val now = System.currentTimeMillis()
        // If more than 2.5 s since the last tap, the user started a new tapping session
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

    fun stopPlayback() {
        if (_isPlaying.value) {
            engine.stop()
            _isPlaying.value = false
            _currentBeat.value = 0
        }
    }

    fun setTimeSig(sig: Int) {
        _timeSig.value = sig
        engine.timeSignature = sig
        prefs.edit { putInt("time_sig", sig) }
    }

    fun setAccentFirst(on: Boolean) {
        _accentFirst.value = on
        engine.accentFirst = on
        prefs.edit { putBoolean("accent", on) }
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

    private fun syncEngineSettings() {
        engine.bpm = _bpm.value
        engine.timeSignature = _timeSig.value
        engine.accentFirst = _accentFirst.value
        engine.soundType = _soundType.value
        engine.volume = _volume.value
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
