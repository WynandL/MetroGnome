package com.example.metrognome.ui.game

import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView

/**
 * The clock the rhythm stage draws from.
 *
 * [displayMs] is when the frame now being drawn will be on the glass, on the boot clock
 * (`SystemClock.elapsedRealtime`), the same clock the audio engine stamps each click's
 * presentation on. A note is drawn where it will be at that moment, so it sits on the line when
 * the click leaves the speaker, not a frame or two after.
 *
 * Why not "now": a frame is drawn at one vsync and shown a vsync or two later. At 60 Hz that
 * is 17 to 33 ms, a third to two thirds of the perfect window, and it made every note look
 * late. From API 33 the system says exactly when it expects to present the frame
 * (`Choreographer.FrameTimeline`); below that the lead is taken as two refresh periods, the
 * usual app-to-display pipeline.
 *
 * Both values are snapshot state. Read them inside a draw block and only the draw phase reruns,
 * once per frame, with no recomposition.
 */
@Stable
class StageClock internal constructor() {
    /** Predicted on-glass time of the current frame, boot clock, ms. */
    var displayMs by mutableDoubleStateOf(SystemClock.elapsedRealtime().toDouble())
        internal set

    /** The frame's own vsync time (`System.nanoTime` base), for effects timed in seconds. */
    var frameNs by mutableLongStateOf(0L)
        internal set
}

@Composable
fun rememberStageClock(): StageClock {
    val view = LocalView.current
    val clock = remember { StageClock() }
    LaunchedEffect(clock) {
        val refresh = view.display?.refreshRate?.takeIf { it >= 30f } ?: 60f
        val lead = PresentationLead(fallbackNs = (2e9 / refresh).toLong())
        try {
            while (true) {
                withFrameNanos { frameNs ->
                    // Frame times are CLOCK_MONOTONIC; the audio grid is on BOOTTIME.
                    val bootOffsetNs = SystemClock.elapsedRealtimeNanos() - System.nanoTime()
                    clock.frameNs = frameNs
                    clock.displayMs = (frameNs + bootOffsetNs + lead.ns) / 1e6
                }
            }
        } finally {
            lead.stop()
        }
    }
    return clock
}

/** How far ahead of its vsync a frame reaches the display. */
private class PresentationLead(fallbackNs: Long) {
    @Volatile var ns: Long = fallbackNs
    @Volatile var active = true

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Api33.follow(this)
    }

    fun stop() { active = false }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33 {
    fun follow(lead: PresentationLead) {
        val choreographer = Choreographer.getInstance()
        choreographer.postVsyncCallback(object : Choreographer.VsyncCallback {
            override fun onVsync(data: Choreographer.FrameData) {
                if (!lead.active) return
                val ahead = data.preferredFrameTimeline.expectedPresentationTimeNanos - data.frameTimeNanos
                // Ignore anything implausible (a paused display, a first frame).
                if (ahead in 1L..100_000_000L) lead.ns = ahead
                choreographer.postVsyncCallback(this)
            }
        })
    }
}
