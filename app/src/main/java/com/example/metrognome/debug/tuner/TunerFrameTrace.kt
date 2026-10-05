package com.example.metrognome.debug.tuner

import android.os.SystemClock

/**
 * Per-hop trace of the tuner's decisions, for driving known test tones through the phone's
 * speaker from adb and reading back exactly what the tuner made of them (see the debug
 * build's `AudioProbeReceiver`). Off by default; when off, [record] costs one volatile read.
 *
 * Written from the [com.example.metrognome.audio.tuner.Tuner] capture thread; [dumpCsv] is
 * called from the main thread, so the buffer is synchronised.
 */
object TunerFrameTrace {

    private const val MAX_FRAMES = 20_000

    @Volatile var enabled: Boolean = false
        private set

    private var startMs = 0L
    private val rows = ArrayList<String>()

    fun start() {
        synchronized(rows) { rows.clear() }
        startMs = SystemClock.elapsedRealtime()
        enabled = true
    }

    fun stop() { enabled = false }

    fun record(
        pitchHz: Float?,
        clarity: Float?,
        rms: Float,
        presence: Float,
        state: String,
        locked: Boolean,
        candidateHz: Float?,
        displayedHz: Float?,
    ) {
        if (!enabled) return
        val t = SystemClock.elapsedRealtime() - startMs
        val row = "$t,${pitchHz ?: ""},${clarity ?: ""},$rms,$presence,$state,$locked," +
                "${candidateHz ?: ""},${displayedHz ?: ""}"
        synchronized(rows) { if (rows.size < MAX_FRAMES) rows.add(row) }
    }

    fun dumpCsv(): String = synchronized(rows) {
        buildString {
            appendLine("t_ms,pitch_hz,clarity,rms,presence,state,locked,candidate_hz,displayed_hz")
            rows.forEach { appendLine(it) }
        }
    }
}
