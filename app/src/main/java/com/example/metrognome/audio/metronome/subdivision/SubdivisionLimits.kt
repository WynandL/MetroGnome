package com.example.metrognome.audio.metronome.subdivision

import kotlin.math.max
import kotlin.math.min

/**
 * The fastest tempo at which a voice can be subdivided and still be heard as separate ticks.
 *
 * Two limits, and the stricter wins:
 * - **The voice's body.** Every tick gets a window up to the next tick and is cut (with the
 *   engine's 20 ms fade) at its end. A window shorter than the voice cuts away what makes it
 *   that voice, so the window must hold [BODY_ENERGY] of the voice's energy. Measured from the
 *   real buffers, the longer of beat and accent (the accent is the one cut at the first
 *   sub-tick), so a re-voiced sound gets its new limit for free.
 * - **The buzz floor.** Ticks closer than [MIN_TICK_MS] stop reading as a rhythm and start
 *   reading as a rattle, however short the voice (60 ms is sixteenths at 250 BPM, about 17
 *   ticks a second).
 *
 * Above its limit the subdivision rests (the beat plays alone) and comes back when the tempo
 * drops; the setting itself is never changed.
 */
object SubdivisionLimits {

    const val MIN_TICK_MS = 60.0
    const val BODY_ENERGY = 0.90
    const val MAX_BPM = 300

    /** Milliseconds from the start of [pcm] until [BODY_ENERGY] of its energy has sounded. */
    fun bodyMs(pcm: ShortArray, sampleRate: Int): Double {
        var total = 0.0
        for (s in pcm) total += s.toDouble() * s
        if (total <= 0.0) return 0.0
        val target = total * BODY_ENERGY
        var run = 0.0
        for (i in pcm.indices) {
            run += pcm[i].toDouble() * pcm[i]
            if (run >= target) return (i + 1) * 1000.0 / sampleRate
        }
        return pcm.size * 1000.0 / sampleRate
    }

    /** The fastest BPM at which [clicks] ticks per beat fit a voice whose body is [bodyMs]. */
    fun maxBpm(bodyMs: Double, clicks: Int): Int {
        if (clicks <= 1) return MAX_BPM
        val tickMs = max(bodyMs, MIN_TICK_MS)
        return min(MAX_BPM, (60_000.0 / (clicks * tickMs)).toInt())
    }
}
