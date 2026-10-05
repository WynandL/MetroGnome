package com.example.metrognome.debug.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.metrognome.debug.mic.MicDiagnosticsBuffer
import com.example.metrognome.debug.mic.MicDiagnosticsEvent
import com.example.metrognome.debug.tuner.TunerFrameTrace
import java.io.File

/**
 * Debug-build only: lets a PC drive known test tones through the phone's own speaker while
 * the app listens, and read back what the tuner decided. Playback happens inside the app's
 * process, so the Tuner tab stays in the foreground with its mic open.
 *
 *   adb shell am broadcast -n com.wynandl.metrognome/com.example.metrognome.debug.probe.AudioProbeReceiver --es cmd play --es file tones.wav
 *   ... --es cmd trace_start
 *   ... --es cmd trace_dump --es file trace.csv     (written to the app's files dir)
 *   ... --es cmd stop
 *   ... --es cmd mic_dump --es file mic.csv        (the last mic session's MicDiagnosticsBuffer)
 *
 * WAV files are read from the app's files dir (push to /data/local/tmp, then `run-as ... cp`).
 */
class AudioProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: return
        val file = intent.getStringExtra("file")
        when (cmd) {
            "play" -> play(context, File(context.filesDir, file ?: return))
            "stop" -> release()
            "trace_start" -> TunerFrameTrace.start()
            "mic_dump" -> File(context.filesDir, file ?: "mic_log.csv").writeText(micCsv())
            "trace_dump" -> {
                TunerFrameTrace.stop()
                File(context.filesDir, file ?: "tuner_trace.csv").writeText(TunerFrameTrace.dumpCsv())
            }
        }
        Log.i(TAG, "done $cmd ${file ?: ""}")
    }

    /**
     * The mic diagnostics log as CSV: one row per event, the capture-clock time first. Beats
     * (stamped at the engine callback) and rejected clicks (the metronome's own click as the
     * mic heard it) share the elapsedRealtime clock, which is what the timing-origin check uses.
     */
    private fun micCsv(): String = buildString {
        appendLine("t_ms,event,a,b,c")
        for (e in MicDiagnosticsBuffer.events.value) {
            val row = when (e) {
                is MicDiagnosticsEvent.SessionStarted -> "session_start,${e.source},${e.aecActive},"
                is MicDiagnosticsEvent.SessionEnded -> "session_end,,,"
                is MicDiagnosticsEvent.BeatFired -> "beat,${e.beat},${e.estimatedPlayMs},"
                is MicDiagnosticsEvent.ClickRejected -> "click,${e.lowRms},${e.highRms},${e.flatness}"
                is MicDiagnosticsEvent.OnsetAccepted -> "onset,${e.rawDeviationMs},${e.calibratedDeviationMs},"
                is MicDiagnosticsEvent.OnsetRejected -> "onset_rejected,${e.rawDeviationMs},,"
                else -> "${e::class.simpleName},,,"
            }
            appendLine("${e.timestampMs},$row")
        }
    }

    private fun play(context: Context, wav: File) {
        release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(wav.absolutePath)
            setOnCompletionListener { Log.i(TAG, "finished ${wav.name}"); release() }
            prepare()
            start()
        }
    }

    private companion object {
        const val TAG = "AudioProbe"
        var player: MediaPlayer? = null
        fun release() { player?.release(); player = null }
    }
}
