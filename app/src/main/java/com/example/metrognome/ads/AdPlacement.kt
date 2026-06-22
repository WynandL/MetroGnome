package com.example.metrognome.ads

/**
 * Every interstitial placement in the app.
 *
 * Cooldowns are tiered by [UserTier] and further stretched at runtime by two
 * multipliers inside [AdManager]:
 *
 *   Depth     — how long was the session that preceded the LAST ad on this placement?
 *               A longer session earns more breathing room before the next show.
 *               Driven by [referenceSessionSeconds]; disabled when 0.
 *
 *   Frequency — each ad already shown today for this placement adds 30% to the next
 *               cooldown, compounding naturally for high-volume sessions.
 *
 * The resulting value is clamped to [[minCooldownMs], [maxCooldownMs]].
 * Ad frequency is governed entirely by these tiered cooldowns; there is no daily cap.
 */
enum class AdPlacement(
    internal val key: String,
    internal val label: String,
    internal val minAppDays: Int,
    /** Global lifetime ad-opportunity count required before this placement fires. */
    internal val minTriggerCount: Int,
    /** Session must be at least this many seconds; 0 = no session gate. */
    internal val minSessionSeconds: Int,
    internal val exploreCooldownMs: Long,
    internal val casualCooldownMs: Long,
    internal val engagedCooldownMs: Long,
    internal val minCooldownMs: Long,
    internal val maxCooldownMs: Long,
    /** Reference session length for the depth multiplier; 0 = frequency-only. */
    internal val referenceSessionSeconds: Int,
) {
    /** User dismisses the rhythm-game result screen. Games are ~40 s each, so
     *  frequency multiplier is the main self-regulator here. */
    RHYTHM_RESULT(
        key                    = "rhythm_result",
        label                  = "Rhythm Game",
        minAppDays             = 0,
        minTriggerCount        = 2,
        minSessionSeconds      = 0,
        exploreCooldownMs      = 12 * 60_000L,
        casualCooldownMs       = 20 * 60_000L,
        engagedCooldownMs      = 35 * 60_000L,
        minCooldownMs          =  8 * 60_000L,
        maxCooldownMs          = 90 * 60_000L,
        referenceSessionSeconds = 0,
    ),

    /** User dismisses the Speed Trainer result screen. */
    SPEED_TRAINER_RESULT(
        key                    = "trainer_result",
        label                  = "Speed Trainer",
        minAppDays             = 0,
        minTriggerCount        = 1,
        minSessionSeconds      = 0,
        exploreCooldownMs      = 10 * 60_000L,
        casualCooldownMs       = 18 * 60_000L,
        engagedCooldownMs      = 30 * 60_000L,
        minCooldownMs          =  8 * 60_000L,
        maxCooldownMs          = 120 * 60_000L,
        referenceSessionSeconds = 8 * 60,
    ),

    /** User dismisses the practice-complete overlay. */
    PRACTICE_COMPLETE(
        key                    = "practice_complete",
        label                  = "Practice",
        minAppDays             = 1,
        minTriggerCount        = 1,
        minSessionSeconds      = 0,
        exploreCooldownMs      = 45 * 60_000L,
        casualCooldownMs       = 60 * 60_000L,
        engagedCooldownMs      = 90 * 60_000L,
        minCooldownMs          = 30 * 60_000L,
        maxCooldownMs          = 240 * 60_000L,
        referenceSessionSeconds = 15 * 60,
    ),

    /** User manually stops the metronome after a qualifying session (>= [minSessionSeconds]).
     *  Filters out quick explore taps; rewards genuine practice sessions. */
    METRONOME_STOP(
        key                    = "metro_stop",
        label                  = "Metronome Stop",
        minAppDays             = 0,
        minTriggerCount        = 2,
        minSessionSeconds      = 180,
        exploreCooldownMs      = 18 * 60_000L,
        casualCooldownMs       = 28 * 60_000L,
        engagedCooldownMs      = 45 * 60_000L,
        minCooldownMs          = 15 * 60_000L,
        maxCooldownMs          = 180 * 60_000L,
        referenceSessionSeconds = 20 * 60,
    ),
}

