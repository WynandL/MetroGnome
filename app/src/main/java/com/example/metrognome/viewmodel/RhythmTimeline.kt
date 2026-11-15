package com.example.metrognome.viewmodel

import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * The rhythm game's beat grid, on the boot clock (`SystemClock.elapsedRealtime`, ms).
 *
 * The game used to keep its own grid: a whole-millisecond interval (461 ms at 130 BPM, where
 * the true beat is 461.54) counted from the moment the game started, with the metronome engine
 * started two seconds later by a coroutine delay. The click the player heard was therefore on a
 * different grid from the notes they saw: late by the engine's start-up and output latency, and
 * drifting a further half millisecond per beat.
 *
 * Now there is one grid, and it is the engine's. The engine runs (muted) from the countdown, and
 * every beat it writes reports when that beat will leave the speaker ([observe]). Beats are
 * sample-accurate, so each report is an estimate of the same origin, `presented - index * beat`.
 * The first report or two come from the engine's queue fallback, which leaves out the device's
 * fixed output latency and so reads early; a real hardware timestamp reads later and is right.
 * The origin is therefore the latest estimate seen, never an average.
 *
 * Note times are then `origin + index * beat`, rounded per note from the exact beat length, so
 * nothing accumulates.
 */
class RhythmTimeline(bpm: Int) {

    /** Exact beat length in ms. Never rounded: 130 BPM is 461.538..., not 461. */
    val beatMs: Double = 60_000.0 / bpm

    private var originMs = Double.NEGATIVE_INFINITY

    /** True once at least one beat has been observed. */
    val anchored: Boolean
        @Synchronized get() = originMs != Double.NEGATIVE_INFINITY

    /** Beat [beatIndex] (counted from the engine's start) will be presented at [presentedMs]. */
    @Synchronized
    fun observe(beatIndex: Int, presentedMs: Long) {
        val estimate = presentedMs - beatIndex * beatMs
        if (estimate > originMs) originMs = estimate
    }

    /** Boot-clock ms at which beat [beatIndex] leaves the speaker. */
    @Synchronized
    fun beatTimeMs(beatIndex: Int): Long = (originMs + beatIndex * beatMs).roundToLong()

    /** Index of the first beat presented at or after [timeMs]. */
    @Synchronized
    fun firstBeatAtOrAfter(timeMs: Long): Int =
        ceil((timeMs - originMs) / beatMs).toInt().coerceAtLeast(0)
}
