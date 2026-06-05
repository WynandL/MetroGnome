package com.example.metrognome.ads

/**
 * Every interstitial placement in the app. Add an entry here before wiring a new trigger.
 *
 * Each placement carries its own policy; [AdManager] enforces them in order:
 *   1. isAdFree          — billing / Gnotes reward
 *   2. minAppDays        — new-user protection (no ads on day 0)
 *   3. minTriggerCount   — engagement gate (user must have hit triggers across the app ≥ N times)
 *   4. showEveryN        — per-placement frequency (e.g. every 3rd rhythm game)
 *   5. globalCooldownMs  — app-wide breathing room between any two ads
 *   6. placementCooldownMs — extra per-placement cooldown on top of global
 *   7. maxPerDay         — hard daily cap per placement
 */
enum class AdPlacement(
    internal val key: String,
    internal val label: String,
    internal val triggerNote: String,
    internal val minAppDays: Int,
    internal val minTriggerCount: Int,
    internal val showEveryN: Int,
    internal val globalCooldownMs: Long,
    internal val placementCooldownMs: Long,
    internal val maxPerDay: Int,
) {
    /** User dismisses the rhythm-game result screen. */
    RHYTHM_RESULT(
        key                 = "rhythm_result",
        label               = "Rhythm Game",
        triggerNote         = "after game result dismiss",
        minAppDays          = 0,
        minTriggerCount     = 3,
        showEveryN          = 3,
        globalCooldownMs    = 5 * 60_000L,
        placementCooldownMs = 0L,
        maxPerDay           = 5,
    ),

    /** User dismisses the speed-trainer result screen. */
    SPEED_TRAINER_RESULT(
        key                 = "trainer_result",
        label               = "Speed Trainer",
        triggerNote         = "after session result dismiss",
        minAppDays          = 0,
        minTriggerCount     = 2,
        showEveryN          = 2,
        globalCooldownMs    = 5 * 60_000L,
        placementCooldownMs = 0L,
        maxPerDay           = 4,
    ),

    /** User dismisses the practice-complete overlay. */
    PRACTICE_COMPLETE(
        key                 = "practice_complete",
        label               = "Practice",
        triggerNote         = "after practice-complete overlay dismiss",
        minAppDays          = 1,
        minTriggerCount     = 2,
        showEveryN          = 1,
        globalCooldownMs    = 5 * 60_000L,
        placementCooldownMs = 22 * 60 * 60_000L,
        maxPerDay           = 1,
    ),
}

// ── Dev display ────────────────────────────────────────────────────────────────

/** Builds a plain-English description of the full ad policy, derived entirely
 *  from the enum values; update the enum and this text updates automatically. */
fun buildAdPolicySummary(): String = buildString {
    val globalDuration = AdPlacement.entries.first().globalCooldownMs.humanDuration()
    appendLine("Global: no two ads fire within $globalDuration of each other, anywhere in the app.")
    appendLine()

    AdPlacement.entries.forEach { p ->
        appendLine("${p.label}  (${p.triggerNote})")

        if (p.minAppDays == 0) {
            append("Eligible from install day 0.")
        } else {
            val firstDay = p.minAppDays + 1
            append("Blocked on install day 0, only eligible from day $firstDay onward.")
        }

        append(" Engagement gate: all ads for this placement are held back until " +
                "${p.minTriggerCount} ad trigger${if (p.minTriggerCount > 1) "s have" else " has"} " +
                "fired across the whole app")
        val freeSessions = p.minTriggerCount - 1
        if (freeSessions > 0) {
            append(", so a brand-new user gets $freeSessions free " +
                    "session${if (freeSessions > 1) "s" else ""} before seeing anything")
        }
        append(". ")

        if (p.showEveryN == 1) {
            append("Shows every eligible trigger")
        } else {
            append("Shows on every ${p.showEveryN.ordinalWord()} eligible trigger")
        }

        if (p.placementCooldownMs > 0L) {
            append(", but a ${p.placementCooldownMs.humanDuration()} per-placement cooldown " +
                    "caps back-to-back shows (effectively once per day at this setting)")
        }
        append(". Hard daily cap: ${p.maxPerDay} show${if (p.maxPerDay > 1) "s" else ""}/day.")
        appendLine()
        appendLine()
    }
}

private fun Long.humanDuration(): String {
    val mins  = this / 60_000L
    val hours = mins  / 60
    val days  = hours / 24
    return when {
        days  > 0  -> "${days}d"
        hours > 0  -> "${hours}h"
        else       -> "${mins}min"
    }
}

private fun Int.ordinalWord(): String = when (this) {
    2    -> "2nd"
    3    -> "3rd"
    4    -> "4th"
    else -> "${this}th"
}
