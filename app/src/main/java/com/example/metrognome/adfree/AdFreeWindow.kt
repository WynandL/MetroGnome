package com.example.metrognome.adfree

/*
 * Timed ad-free, the clock half: pure Kotlin, no Android, so every edge (sleep, reboot, the
 * clock moved either way, a restored backup) is unit-tested on the JVM (AdFreeWindowTest).
 * The ad-free trial and the daily-Gnotes ad-free days both run on it.
 */

/** One reading of the device's clocks, taken together. */
data class TimeReading(
    /** Wall clock, ms since the epoch. The user can move it. */
    val wallMs: Long,
    /** Time since boot, sleep included (`SystemClock.elapsedRealtime`). Cannot be moved. */
    val elapsedMs: Long,
    /** Which boot this is (`Settings.Global.BOOT_COUNT`), or [UNKNOWN_BOOT]. */
    val bootCount: Int,
) {
    companion object {
        const val UNKNOWN_BOOT = -1
    }
}

/**
 * A stretch of ad-free time that is used up by real time passing, open or closed.
 *
 * Expiry is never "is the wall clock past the end". Within one boot, consumption is measured
 * on the elapsed clock, which the user cannot move, so changing the date neither extends nor
 * cuts the window. After a reboot (the elapsed clock restarts) it falls back to the wall clock
 * since [startWallMs], which counts the time the phone was off; a wall clock now *before* the
 * start can only mean it was moved back, and ends the window. [consumedMs] only ever rises and
 * is saved on every check, so neither a reboot nor a wound-back clock can return time already
 * used. The one residual: time spent powered off after the last check, with the clock also
 * moved back before booting, is not counted. That takes deliberate effort for a few hours of
 * a free trial and is accepted.
 */
data class AdFreeWindow(
    val startWallMs: Long,
    val durationMs: Long,
    /** The elapsed-clock value at which [consumedMs] would be zero, on [anchorBoot]. */
    val anchorElapsedMs: Long,
    /** The boot [anchorElapsedMs] belongs to, or [NO_ANCHOR] to force the wall-clock path. */
    val anchorBoot: Int,
    /** The most of [durationMs] seen used so far. Never goes down. */
    val consumedMs: Long,
) {
    val expired: Boolean get() = consumedMs >= durationMs
    val remainingMs: Long get() = (durationMs - consumedMs).coerceAtLeast(0L)

    /** When the window ends, on the wall clock of [now]; for display only. */
    fun endsAtWallMs(now: TimeReading): Long = now.wallMs + remainingMs

    /** This window brought up to [now]: consumption ratcheted, the anchor moved after a reboot. */
    fun advance(now: TimeReading): AdFreeWindow {
        if (expired) return this
        val sameBoot = anchorBoot != NO_ANCHOR &&
            now.bootCount == anchorBoot &&
            now.elapsedMs >= anchorElapsedMs
        val measured = if (sameBoot) {
            now.elapsedMs - anchorElapsedMs
        } else {
            val sinceStart = now.wallMs - startWallMs
            if (sinceStart < 0) durationMs else sinceStart
        }
        val consumed = maxOf(consumedMs, measured).coerceIn(0L, durationMs)
        return if (sameBoot) {
            copy(consumedMs = consumed)
        } else {
            // Re-anchor on this boot, so from here on the elapsed clock does the measuring.
            copy(consumedMs = consumed, anchorElapsedMs = now.elapsedMs - consumed, anchorBoot = now.bootCount)
        }
    }

    /** The same window with [remainingMs] of it left from [now]; for the dev tools only. */
    fun withRemaining(now: TimeReading, remainingMs: Long): AdFreeWindow {
        val consumed = (durationMs - remainingMs).coerceIn(0L, durationMs)
        return copy(consumedMs = consumed, anchorElapsedMs = now.elapsedMs - consumed, anchorBoot = now.bootCount)
    }

    companion object {
        /** Forces the next [advance] onto the wall clock (a window from before this class). */
        const val NO_ANCHOR = Int.MIN_VALUE

        fun start(now: TimeReading, durationMs: Long) = AdFreeWindow(
            startWallMs = now.wallMs,
            durationMs = durationMs,
            anchorElapsedMs = now.elapsedMs,
            anchorBoot = now.bootCount,
            consumedMs = 0L,
        )

        /**
         * A window known only by its wall-clock end, as the Gnotes ad-free days were stored
         * before this class. Its first [advance] measures on the wall clock and anchors.
         */
        fun fromLegacyEnd(endWallMs: Long, durationMs: Long) = AdFreeWindow(
            startWallMs = endWallMs - durationMs,
            durationMs = durationMs,
            anchorElapsedMs = 0L,
            anchorBoot = NO_ANCHOR,
            consumedMs = 0L,
        )
    }
}
