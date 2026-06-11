package com.example.metrognome.debug.mic

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton ring buffer that collects mic timing events for the engineering overlay.
 *
 * Written from ViewModel threads (IO/Default), read from the Compose main thread.
 * [MutableStateFlow] is thread-safe by construction.
 *
 * Debug-only. Delete this file and all call sites to remove the feature entirely.
 */
object MicDiagnosticsBuffer {

    const val MAX_EVENTS = 200
    private const val MAX_AMP_SAMPLES = 300   // ~7s at 44.1 kHz / 1024-sample buffers

    // ── Events ────────────────────────────────────────────────────────────────

    private val _events = MutableStateFlow<List<MicDiagnosticsEvent>>(emptyList())
    val events: StateFlow<List<MicDiagnosticsEvent>> = _events.asStateFlow()

    // ── Amplitude history (timestampMs, amplitude 0..1) ───────────────────────

    private val _ampHistory = MutableStateFlow<List<Pair<Long, Float>>>(emptyList())
    val ampHistory: StateFlow<List<Pair<Long, Float>>> = _ampHistory.asStateFlow()

    // ── Summary stats (observed individually by the stats row) ────────────────

    private val _source = MutableStateFlow("")
    val source: StateFlow<String> = _source.asStateFlow()

    private val _aecActive = MutableStateFlow(false)
    val aecActive: StateFlow<Boolean> = _aecActive.asStateFlow()

    private val _latencyBiasMs = MutableStateFlow(0f)
    val latencyBiasMs: StateFlow<Float> = _latencyBiasMs.asStateFlow()

    private val _lastRawDevMs = MutableStateFlow<Float?>(null)
    val lastRawDevMs: StateFlow<Float?> = _lastRawDevMs.asStateFlow()

    private val _lastCalDevMs = MutableStateFlow<Float?>(null)
    val lastCalDevMs: StateFlow<Float?> = _lastCalDevMs.asStateFlow()

    private val _calibrationSampleCount = MutableStateFlow(0)
    val calibrationSampleCount: StateFlow<Int> = _calibrationSampleCount.asStateFlow()

    private val _sessionStartMs = MutableStateFlow(0L)
    val sessionStartMs: StateFlow<Long> = _sessionStartMs.asStateFlow()

    // ── Session lifecycle ─────────────────────────────────────────────────────

    fun startSession(source: String, aecActive: Boolean) {
        val now = SystemClock.elapsedRealtime()
        _events.value = emptyList()
        _ampHistory.value = emptyList()
        _source.value = source
        _aecActive.value = aecActive
        _latencyBiasMs.value = 0f
        _lastRawDevMs.value = null
        _lastCalDevMs.value = null
        _calibrationSampleCount.value = 0
        _sessionStartMs.value = now
        append(MicDiagnosticsEvent.SessionStarted(now, source, aecActive))
    }

    fun endSession() {
        append(MicDiagnosticsEvent.SessionEnded(SystemClock.elapsedRealtime()))
    }

    // ── Event loggers ─────────────────────────────────────────────────────────

    fun logBeat(beat: Int, suppressUntilMs: Long, estimatedPlayMs: Long) {
        append(MicDiagnosticsEvent.BeatFired(SystemClock.elapsedRealtime(), beat, suppressUntilMs, estimatedPlayMs))
    }

    fun logOnsetAccepted(onsetMs: Long, rawDev: Float, calDev: Float) {
        _lastRawDevMs.value = rawDev
        _lastCalDevMs.value = calDev
        append(MicDiagnosticsEvent.OnsetAccepted(onsetMs, rawDev, calDev))
    }

    fun logOnsetSuppressed(onsetMs: Long, suppressUntilMs: Long) {
        append(MicDiagnosticsEvent.OnsetSuppressed(onsetMs, suppressUntilMs))
    }

    fun logOnsetRejected(onsetMs: Long, rawDev: Float) {
        append(MicDiagnosticsEvent.OnsetRejected(onsetMs, rawDev))
    }

    fun logClickRejected(onsetMs: Long, lowRms: Double, highRms: Double, peakRatio: Double) {
        append(MicDiagnosticsEvent.ClickRejected(onsetMs, lowRms, highRms, peakRatio))
    }

    fun logCalibrationSample(rawDev: Float, sampleIndex: Int) {
        _calibrationSampleCount.value = sampleIndex + 1
        append(MicDiagnosticsEvent.CalibrationSample(SystemClock.elapsedRealtime(), rawDev, sampleIndex))
    }

    fun logCalibrationFinalized(biasMs: Float, sampleCount: Int) {
        _latencyBiasMs.value = biasMs
        append(MicDiagnosticsEvent.CalibrationFinalized(SystemClock.elapsedRealtime(), biasMs, sampleCount))
    }

    // ── Amplitude ─────────────────────────────────────────────────────────────

    fun updateAmplitude(amplitude: Float) {
        val t = SystemClock.elapsedRealtime()
        val prev = _ampHistory.value
        _ampHistory.value = if (prev.size >= MAX_AMP_SAMPLES) prev.drop(1) + (t to amplitude)
                            else prev + (t to amplitude)
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun clear() {
        _events.value = emptyList()
        _ampHistory.value = emptyList()
    }

    private fun append(event: MicDiagnosticsEvent) {
        val current = _events.value
        _events.value = if (current.size >= MAX_EVENTS) current.drop(1) + event else current + event
    }
}
