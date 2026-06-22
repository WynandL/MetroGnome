package com.example.metrognome.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.example.metrognome.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlin.math.ln

private const val AD_UNIT_ID_TEST = "ca-app-pub-3940256099942544/1033173712"
private const val AD_UNIT_ID_PROD = "ca-app-pub-8485854692249613/2040537682"

private const val AD_BREAK_DELAY_MS = 3000L

private const val PREFS_NAME             = "ad_manager"
private const val KEY_TOTAL_TRIGGERS     = "total_triggers"
private const val KEY_TOTAL_SHOWS        = "total_shows"
private const val KEY_LAST_GLOBAL_MS     = "last_global_ms"

// Read-only borrow from UsageDayTracker: distinct calendar days the user opened the app.
// Keys are stable and intentionally not imported to keep the ads package self-contained.
private const val USAGE_PREFS_NAME  = "usage_days"
private const val USAGE_KEY_COUNT   = "count"

private val AdPlacement.lastShownMsKey        get() = "ad_${key}_last_shown_ms"
private val AdPlacement.lastSessionSecondsKey get() = "ad_${key}_last_session_s"
private val AdPlacement.todayCountKey         get() = "ad_${key}_today_count"
private val AdPlacement.todayDateKey          get() = "ad_${key}_today_date"

/**
 * Central interstitial-ad controller with an adaptive tier system.
 *
 * Cooldowns are computed dynamically on every gate check — no static lookup table.
 * Three signals interact:
 *
 *   Tier      — EXPLORER / CASUAL / ENGAGED from install age + lifetime opportunity count.
 *               Sets the base global and per-placement cooldowns.
 *
 *   Depth     — the session length stored at the last ad show for a placement.
 *               A longer prior session extends the current cooldown (rewards engagement).
 *
 *   Frequency — each ad shown today for a placement adds 30% to the next cooldown
 *               for that placement (self-regulates fast sessions like Rhythm Game).
 *
 * The global cooldown also stretches logarithmically with total lifetime shows.
 *
 * Add new triggers by:
 *   1. Adding an entry to [AdPlacement] with its policy.
 *   2. Calling [maybeShow] at the relevant UI event, passing the session duration.
 *
 * onDone is always invoked: immediately if the ad is skipped/unavailable, or
 * after the full-screen ad closes.
 */
class AdManager(private val context: Context) {

    private val prefs     = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var loadedAd  : InterstitialAd? = null
    private var isLoading = false

    // True from the moment an ad break is committed (banner posted) until the
    // full-screen ad is dismissed/fails. Guards the 3s banner window so concurrent
    // triggers are absorbed cleanly instead of posting a second banner. Main-thread only.
    private var adBreakInFlight = false

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        val unitId = if (BuildConfig.DEBUG) AD_UNIT_ID_TEST else AD_UNIT_ID_PROD
        InterstitialAd.load(context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { loadedAd = ad; isLoading = false }
                override fun onAdFailedToLoad(e: LoadAdError) { loadedAd = null; isLoading = false }
            })
    }

    /**
     * @param sessionSeconds Duration of the session that just ended (0 if not applicable).
     *   Used as the gate for [AdPlacement.minSessionSeconds] AND stored for the depth
     *   multiplier that will stretch the NEXT cooldown for this placement.
     */
    fun maybeShow(
        placement: AdPlacement,
        activity: Activity,
        isAdFree: Boolean,
        sessionSeconds: Int = 0,
        onDone: () -> Unit,
    ) {
        // An ad break is already committed for this session (banner showing, ad about
        // to display). Absorb the concurrent trigger: no second banner, no double count.
        if (adBreakInFlight) { onDone(); return }

        val totalTriggers = prefs.getInt(KEY_TOTAL_TRIGGERS, 0) + 1
        prefs.edit { putInt(KEY_TOTAL_TRIGGERS, totalTriggers) }

        if (!shouldShow(placement, isAdFree, totalTriggers, sessionSeconds)) {
            if (loadedAd == null) preload()
            onDone()
            return
        }

        val ad = loadedAd
        if (ad == null || activity.isFinishing) {
            preload()
            onDone()
            return
        }

        // Commit. Consume the ad, mark the break in flight, and record the show NOW so
        // cooldowns reflect the in-flight ad immediately. Then post the pre-ad banner;
        // the 3s delay lets it land before the full-screen ad takes over. recordShow at
        // commit (not after the delay) means a rare finishing-bail leaves one cooldown
        // slightly conservative, an acceptable bias toward fewer ads.
        loadedAd = null
        adBreakInFlight = true
        recordShow(placement, sessionSeconds)
        AdBreakQueue.post()

        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing) { adBreakInFlight = false; preload(); onDone(); return@postDelayed }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { adBreakInFlight = false; preload(); onDone() }
                override fun onAdFailedToShowFullScreenContent(e: AdError) { adBreakInFlight = false; preload(); onDone() }
            }
            ad.show(activity)
        }, AD_BREAK_DELAY_MS)
    }

    /**
     * True if an interstitial is in flight (banner showing / about to show) or one was
     * shown within [windowMs]. Used by the review prompt to stay out of any moment where
     * an ad could be on screen, so a rating sheet never stacks with an interstitial.
     */
    fun recentlyShowedAd(windowMs: Long = 90_000L): Boolean {
        if (adBreakInFlight) return true
        return System.currentTimeMillis() - prefs.getLong(KEY_LAST_GLOBAL_MS, 0L) < windowMs
    }

    @Suppress("unused")
    fun debugReset() {
        prefs.edit { clear() }
        loadedAd = null
        preload()
    }

    // ── Tier ──────────────────────────────────────────────────────────────────────

    fun userTier(): UserTier {
        val loyalty  = loyaltyDays()
        val triggers = prefs.getInt(KEY_TOTAL_TRIGGERS, 0)
        return when {
            loyalty < AdEngineConfig.EXPLORER_MAX_LOYALTY_DAYS || triggers < AdEngineConfig.EXPLORER_MAX_TRIGGERS -> UserTier.EXPLORER
            loyalty >= AdEngineConfig.ENGAGED_MIN_LOYALTY_DAYS && triggers >= AdEngineConfig.ENGAGED_MIN_TRIGGERS -> UserTier.ENGAGED
            else -> UserTier.CASUAL
        }
    }

    // ── Dynamic cooldowns ─────────────────────────────────────────────────────────

    /**
     * Global minimum gap between any two ads.
     * Base is tier-dependent; extended logarithmically with lifetime show count
     * so long-term users naturally breathe more between ads.
     */
    private fun effectiveGlobalCooldownMs(tier: UserTier): Long {
        val base = when (tier) {
            UserTier.EXPLORER -> AdEngineConfig.GLOBAL_COOLDOWN_EXPLORER_MS
            UserTier.CASUAL   -> AdEngineConfig.GLOBAL_COOLDOWN_CASUAL_MS
            UserTier.ENGAGED  -> AdEngineConfig.GLOBAL_COOLDOWN_ENGAGED_MS
        }
        val shows      = prefs.getInt(KEY_TOTAL_SHOWS, 0)
        val multiplier = (1.0 + ln(1.0 + shows) * AdEngineConfig.GLOBAL_LOG_FACTOR)
            .coerceIn(1.0, AdEngineConfig.GLOBAL_LOG_CAP)
        return (base * multiplier).toLong()
    }

    /**
     * Per-placement cooldown, with two compounding multipliers:
     *
     *   Depth multiplier — the session stored at the previous show for this placement
     *   compared to [AdPlacement.referenceSessionSeconds]. A session 2× the reference
     *   extends the cooldown by up to 2.5×. Disabled when referenceSessionSeconds == 0.
     *
     *   Frequency multiplier — each ad shown today for this placement adds 30%,
     *   naturally slowing down rapid-fire sessions (e.g. many short rhythm games).
     *
     * Result is clamped to [[AdPlacement.minCooldownMs], [AdPlacement.maxCooldownMs]].
     */
    private fun effectivePlacementCooldownMs(placement: AdPlacement, tier: UserTier): Long {
        val base = when (tier) {
            UserTier.EXPLORER -> placement.exploreCooldownMs
            UserTier.CASUAL   -> placement.casualCooldownMs
            UserTier.ENGAGED  -> placement.engagedCooldownMs
        }

        val storedSession = prefs.getInt(placement.lastSessionSecondsKey, 0)
        val depthMultiplier = if (placement.referenceSessionSeconds > 0 && storedSession > 0) {
            val ratio = storedSession.toDouble() / placement.referenceSessionSeconds
            (1.0 + (ratio - 1.0).coerceAtLeast(0.0) * AdEngineConfig.DEPTH_FACTOR)
                .coerceIn(1.0, AdEngineConfig.DEPTH_MAX_MULTIPLIER)
        } else 1.0

        val todayShows          = todayShowCount(placement)
        val frequencyMultiplier = 1.0 + todayShows * AdEngineConfig.FREQUENCY_STEP

        return (base * depthMultiplier * frequencyMultiplier).toLong()
            .coerceIn(placement.minCooldownMs, placement.maxCooldownMs)
    }

    // ── Gate ─────────────────────────────────────────────────────────────────────

    private fun shouldShow(
        placement: AdPlacement,
        isAdFree: Boolean,
        totalTriggers: Int,
        sessionSeconds: Int,
    ): Boolean {
        if (isAdFree) return false

        if (loyaltyDays() < placement.minAppDays) return false
        if (totalTriggers < placement.minTriggerCount) return false
        if (placement.minSessionSeconds > 0 && sessionSeconds < placement.minSessionSeconds) return false

        val tier = userTier()
        val now  = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_GLOBAL_MS, 0L) < effectiveGlobalCooldownMs(tier)) return false
        if (now - prefs.getLong(placement.lastShownMsKey, 0L) < effectivePlacementCooldownMs(placement, tier)) return false

        return true
    }

    // ── Persistence ───────────────────────────────────────────────────────────────

    private fun recordShow(placement: AdPlacement, sessionSeconds: Int) {
        val now   = System.currentTimeMillis()
        val today = todayEpochDay()
        prefs.edit {
            putLong(KEY_LAST_GLOBAL_MS, now)
            putInt(KEY_TOTAL_SHOWS,     prefs.getInt(KEY_TOTAL_SHOWS, 0) + 1)
            putLong(placement.lastShownMsKey,        now)
            putInt(placement.lastSessionSecondsKey,  sessionSeconds)
            putInt(placement.todayCountKey,          todayShowCount(placement) + 1)
            putInt(placement.todayDateKey,           today)
        }
    }

    private fun todayShowCount(placement: AdPlacement): Int {
        val today = todayEpochDay()
        return if (prefs.getInt(placement.todayDateKey, -1) == today)
            prefs.getInt(placement.todayCountKey, 0)
        else 0
    }

    private fun loyaltyDays(): Int {
        return context.getSharedPreferences(USAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(USAGE_KEY_COUNT, 0)
    }

    // Local-midnight epoch day so the per-placement frequency counter resets at the
    // user's local midnight, not a raw UTC boundary (which lands mid-afternoon in
    // large negative UTC offsets).
    private fun todayEpochDay(): Int {
        val now = System.currentTimeMillis()
        return ((now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }
}
