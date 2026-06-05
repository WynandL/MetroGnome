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

private const val AD_UNIT_ID_TEST = "ca-app-pub-3940256099942544/1033173712"
private const val AD_UNIT_ID_PROD = "ca-app-pub-8485854692249613/2040537682"

private const val AD_BREAK_DELAY_MS   = 1800L

private const val PREFS_NAME          = "ad_manager"
private const val KEY_FIRST_LAUNCH_MS = "first_launch_ms"
private const val KEY_TOTAL_TRIGGERS  = "total_triggers"
private const val KEY_LAST_GLOBAL_MS  = "last_global_ms"

private val AdPlacement.callCountKey    get() = "ad_${key}_call_count"
private val AdPlacement.lastShownMsKey  get() = "ad_${key}_last_shown_ms"
private val AdPlacement.todayCountKey   get() = "ad_${key}_today_count"
private val AdPlacement.todayDateKey    get() = "ad_${key}_today_date"

/**
 * Central interstitial-ad controller. All placement decisions live here; callers
 * declare *when* to ask, not *whether* to show.
 *
 * Add new triggers by:
 *   1. Adding an entry to [AdPlacement] with its policy.
 *   2. Calling [maybeShow] at the relevant UI dismissal point.
 *
 * The onDone callback is always invoked: immediately if the ad is skipped or
 * unavailable, or after the full-screen ad closes.
 */
class AdManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var loadedAd: InterstitialAd? = null
    private var isLoading = false

    init {
        if (prefs.getLong(KEY_FIRST_LAUNCH_MS, 0L) == 0L) {
            prefs.edit { putLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis()) }
        }
    }

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        val unitId = if (BuildConfig.DEBUG) AD_UNIT_ID_TEST else AD_UNIT_ID_PROD
        InterstitialAd.load(context, unitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadedAd = ad
                    isLoading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadedAd = null
                    isLoading = false
                }
            })
    }

    fun maybeShow(
        placement: AdPlacement,
        activity: Activity,
        isAdFree: Boolean,
        onDone: () -> Unit,
    ) {
        // Increment counters regardless of gate outcome so they accumulate over time.
        val totalTriggers     = prefs.getInt(KEY_TOTAL_TRIGGERS, 0) + 1
        val placementCalls    = prefs.getInt(placement.callCountKey, 0) + 1
        prefs.edit {
            putInt(KEY_TOTAL_TRIGGERS, totalTriggers)
            putInt(placement.callCountKey, placementCalls)
        }

        if (!shouldShow(placement, isAdFree, totalTriggers, placementCalls)) {
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

        // Clear the slot immediately — prevents any double-show if maybeShow is called
        // again during the banner-display delay.
        loadedAd = null
        AdBreakQueue.post()

        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing) {
                preload()
                onDone()
                return@postDelayed
            }
            recordShow(placement)
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preload()
                    onDone()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    preload()
                    onDone()
                }
            }
            ad.show(activity)
        }, AD_BREAK_DELAY_MS)
    }

    @Suppress("unused")
    fun debugReset() {
        prefs.edit { clear() }
        loadedAd = null
        preload()
    }

    // ── Gates ─────────────────────────────────────────────────────────────────

    private fun shouldShow(
        placement: AdPlacement,
        isAdFree: Boolean,
        totalTriggers: Int,
        placementCallCount: Int,
    ): Boolean {
        // Gate 1: ad-free (billing or Gnotes reward)
        if (isAdFree) return false

        // Gate 2: app age — protect users who just installed and are exploring
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH_MS, System.currentTimeMillis())
        val appAgeDays  = (System.currentTimeMillis() - firstLaunch) / 86_400_000L
        if (appAgeDays < placement.minAppDays) return false

        // Gate 3: engagement — don't show until the user has demonstrated real usage
        if (totalTriggers < placement.minTriggerCount) return false

        // Gate 4: per-placement frequency (e.g. every 3rd rhythm game)
        if (placementCallCount % placement.showEveryN != 0) return false

        // Gate 5: app-wide cooldown
        val lastGlobal = prefs.getLong(KEY_LAST_GLOBAL_MS, 0L)
        if (System.currentTimeMillis() - lastGlobal < placement.globalCooldownMs) return false

        // Gate 6: per-placement cooldown
        if (placement.placementCooldownMs > 0L) {
            val lastPlacement = prefs.getLong(placement.lastShownMsKey, 0L)
            if (System.currentTimeMillis() - lastPlacement < placement.placementCooldownMs) return false
        }

        // Gate 7: daily cap
        if (todayShowCount(placement) >= placement.maxPerDay) return false

        return true
    }

    // ── Persistence helpers ────────────────────────────────────────────────────

    private fun recordShow(placement: AdPlacement) {
        val now   = System.currentTimeMillis()
        val today = todayEpochDay()
        prefs.edit {
            putLong(KEY_LAST_GLOBAL_MS, now)
            putLong(placement.lastShownMsKey, now)
            putInt(placement.todayCountKey, todayShowCount(placement) + 1)
            putInt(placement.todayDateKey, today)
        }
    }

    private fun todayShowCount(placement: AdPlacement): Int {
        val today = todayEpochDay()
        return if (prefs.getInt(placement.todayDateKey, -1) == today)
            prefs.getInt(placement.todayCountKey, 0)
        else 0
    }

    // Local-midnight epoch day so the per-placement daily cap resets at the user's
    // local midnight rather than a raw UTC boundary (which lands mid-afternoon for
    // large negative UTC offsets).
    private fun todayEpochDay(): Int {
        val now = System.currentTimeMillis()
        return ((now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }
}
