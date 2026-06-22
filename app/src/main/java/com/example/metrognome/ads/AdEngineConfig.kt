package com.example.metrognome.ads

/**
 * Single source of truth for all AdManager tuning constants.
 * Change a value here and both the engine behaviour and the dev policy
 * dashboard update automatically — nothing drifts.
 */
object AdEngineConfig {

    // ── Tier thresholds ───────────────────────────────────────────────────────
    /** Distinct calendar days the user has opened the app (from UsageDayTracker). */
    const val EXPLORER_MAX_LOYALTY_DAYS = 3
    const val EXPLORER_MAX_TRIGGERS     = 6
    const val ENGAGED_MIN_LOYALTY_DAYS  = 21
    const val ENGAGED_MIN_TRIGGERS      = 30

    // ── Global cooldown bases (ms) ────────────────────────────────────────────
    const val GLOBAL_COOLDOWN_EXPLORER_MS = 3 * 60_000L
    const val GLOBAL_COOLDOWN_CASUAL_MS   = 5 * 60_000L
    const val GLOBAL_COOLDOWN_ENGAGED_MS  = 9 * 60_000L

    // ── Global cooldown logarithmic extension ─────────────────────────────────
    /** Fraction added per ln(shows+1) step. 0.12 = +12% per log unit. */
    const val GLOBAL_LOG_FACTOR = 0.12
    /** Maximum multiplier the logarithmic extension can reach. */
    const val GLOBAL_LOG_CAP    = 2.0

    // ── Frequency multiplier ──────────────────────────────────────────────────
    /** Fraction added to a placement's cooldown per ad already shown today on that placement. */
    const val FREQUENCY_STEP = 0.30

    // ── Depth multiplier ─────────────────────────────────────────────────────
    /** How steeply session length beyond the reference stretches the next cooldown.
     *  A session 2x the reference adds (2-1) * DEPTH_FACTOR = 0.5 to the multiplier. */
    const val DEPTH_FACTOR          = 0.50
    /** Maximum multiplier the depth signal can contribute. */
    const val DEPTH_MAX_MULTIPLIER  = 2.5
}
