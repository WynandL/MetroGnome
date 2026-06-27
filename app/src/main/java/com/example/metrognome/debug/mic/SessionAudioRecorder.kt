package com.example.metrognome.debug.mic

import android.content.Context
import android.os.SystemClock
import com.example.metrognome.dev.DevEasterEgg
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Singleton that records the raw mic PCM of a mic session to a WAV file the developer can play
 * back by ear, to judge for themselves what the detector heard (and double-check the clap-vs-click
 * machine decisions). Fed from [com.example.metrognome.audio.rhythm.RhythmDetector.debugOnPcm] on
 * the capture thread; the WAV is written to the app's private cache directory on [finish] - no new
 * permission and nothing leaves the device.
 *
 * It pairs with [MicDiagnosticsBuffer]: both anchor on [SystemClock.elapsedRealtime], so the Mic
 * Timing Log can draw a playhead over the amplitude trace that lines up with the audio.
 *
 * Debug-only. Delete this file and its call sites (RhythmDetector.debugOnPcm, the three ViewModels,
 * the Mic Timing Log player) to remove the feature entirely.
 */
object SessionAudioRecorder {

    /** Hard cap on captured audio. A session past this keeps only the first [MAX_SECONDS]. */
    private const val MAX_SECONDS = 60
    private const val MAX_SAMPLE_RATE = 48_000
    private const val CLIP_FILE_NAME = "mic_session_capture.wav"
    /** Envelope resolution: one peak per this many ms of audio. Drives the paged waveform. */
    private const val ENVELOPE_HOP_MS = 15

    /** A finished, playable capture. */
    data class Clip(
        val path: String,
        val sampleRate: Int,
        val durationMs: Long,
        /** elapsedRealtime of the first captured frame - the playhead's time origin. */
        val startMs: Long,
        /** Peak amplitude (0..1) per [envelopeHopMs] of audio, spanning the WHOLE recording. */
        val envelope: List<Float>,
        val envelopeHopMs: Int,
    )

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Captured PCM. Allocated once, reused across sessions (debug-only, avoids per-session churn).
    private var pcm: ShortArray? = null
    private var length = 0
    private var sampleRate = 44_100
    private var startMs = 0L
    private var capturing = false

    /**
     * Begin a fresh capture. Call just before the detector starts.
     *
     * Authoritative gate: this PHYSICALLY captures only in a debug build or when the user has
     * manually unlocked dev mode ([DevEasterEgg.isDevModeActive]). In any other case it stays idle
     * (no buffer allocation, no per-frame copy in [append]), so a normal production user is never
     * recorded - even if a future call site forgets to gate. Nothing leaves the device regardless.
     */
    @Synchronized
    fun begin(context: Context) {
        if (!DevEasterEgg.isDevModeActive(context)) {
            capturing = false
            return
        }
        if (pcm == null) pcm = ShortArray(MAX_SECONDS * MAX_SAMPLE_RATE)
        length = 0
        startMs = 0L
        capturing = true
    }

    /** Append one capture buffer. Copies synchronously - the source buffer is reused after this. */
    @Synchronized
    fun append(buffer: ShortArray, count: Int, sr: Int) {
        val dst = pcm ?: return
        if (!capturing) return
        if (startMs == 0L) startMs = SystemClock.elapsedRealtime()
        sampleRate = sr
        val room = dst.size - length
        if (room <= 0) return
        val n = if (count <= room) count else room
        System.arraycopy(buffer, 0, dst, length, n)
        length += n
    }

    /**
     * Stop capturing and write the WAV off the caller's thread. Posts the new [clip] when done.
     * Safe to call even if nothing was captured (it just clears the clip).
     */
    fun finish(context: Context) {
        val snapshot: ShortArray
        val frames: Int
        val sr: Int
        val start: Long
        synchronized(this) {
            capturing = false
            frames = length
            sr = sampleRate
            start = startMs
            val src = pcm
            snapshot = if (src == null || frames <= 0) ShortArray(0) else src.copyOf(frames)
        }
        if (snapshot.isEmpty()) {
            _clip.value = null
            return
        }
        val appContext = context.applicationContext
        io.launch {
            val file = File(appContext.cacheDir, CLIP_FILE_NAME)
            writeWav(file, snapshot, sr)
            val durationMs = snapshot.size * 1000L / sr
            _clip.value = Clip(file.absolutePath, sr, durationMs, start, envelope(snapshot, sr), ENVELOPE_HOP_MS)
        }
    }

    /** Peak-amplitude envelope (0..1) over [ENVELOPE_HOP_MS] hops across the whole recording. */
    private fun envelope(samples: ShortArray, sr: Int): List<Float> {
        val hop = (sr * ENVELOPE_HOP_MS / 1000).coerceAtLeast(1)
        val out = ArrayList<Float>((samples.size + hop - 1) / hop)
        var i = 0
        while (i < samples.size) {
            var peak = 0
            val end = minOf(i + hop, samples.size)
            var j = i
            while (j < end) {
                val a = abs(samples[j].toInt())
                if (a > peak) peak = a
                j++
            }
            out.add(peak / 32768f)
            i += hop
        }
        return out
    }

    /** 16-bit mono PCM WAV. */
    private fun writeWav(file: File, samples: ShortArray, sr: Int) {
        val dataBytes = samples.size * 2
        val header = ByteArray(44)
        fun putLe32(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte()
            header[off + 1] = (v shr 8 and 0xff).toByte()
            header[off + 2] = (v shr 16 and 0xff).toByte()
            header[off + 3] = (v shr 24 and 0xff).toByte()
        }
        fun putLe16(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte()
            header[off + 1] = (v shr 8 and 0xff).toByte()
        }
        fun putAscii(off: Int, s: String) = s.forEachIndexed { i, c -> header[off + i] = c.code.toByte() }

        putAscii(0, "RIFF"); putLe32(4, 36 + dataBytes); putAscii(8, "WAVE")
        putAscii(12, "fmt "); putLe32(16, 16); putLe16(20, 1); putLe16(22, 1)      // PCM, mono
        putLe32(24, sr); putLe32(28, sr * 2); putLe16(32, 2); putLe16(34, 16)      // byte rate, block align, bits
        putAscii(36, "data"); putLe32(40, dataBytes)

        FileOutputStream(file).use { out ->
            out.write(header)
            val bytes = ByteArray(dataBytes)
            var b = 0
            for (s in samples) {
                bytes[b++] = (s.toInt() and 0xff).toByte()
                bytes[b++] = (s.toInt() shr 8 and 0xff).toByte()
            }
            out.write(bytes)
        }
    }
}
