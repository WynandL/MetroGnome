package com.example.metrognome.ads

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.example.metrognome.BuildConfig
import com.example.metrognome.points.PointsBannerData
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.points.PointsLimits
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val REWARDED_AD_UNIT_TEST = "ca-app-pub-3940256099942544/5224354917"
// TODO: replace with your AdMob rewarded ad unit ID after creating it in the AdMob console.
private const val REWARDED_AD_UNIT_PROD = "ca-app-pub-8485854692249613/6776283607"

private const val PREFS_NAME     = "rewarded_ad_manager"
private const val KEY_LIFETIME   = "lifetime_gnotes"
private const val KEY_TODAY      = "today_gnotes"
private const val KEY_TODAY_DATE = "today_date"

/**
 * Manages opt-in rewarded ads that grant the user [PointsConfig.REWARDED_GNOTES_PER_WATCH] Gnotes,
 * capped at [PointsLimits.REWARDED_GNOTES_PER_DAY] per calendar day.
 *
 * [lifetimeGnotes] is stored as already-capped points (capped by [grantReward] at earn time),
 * so PointsCalculator can add it directly without re-applying daily caps.
 */
class RewardedAdManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var loadedAd: RewardedAd? = null
    private var isLoading = false

    private val _adLoaded = MutableStateFlow(false)
    /** Emits true when a rewarded ad is ready to show; false while loading or after show. */
    val adLoaded: StateFlow<Boolean> = _adLoaded.asStateFlow()

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        val unitId = if (BuildConfig.DEBUG) REWARDED_AD_UNIT_TEST else REWARDED_AD_UNIT_PROD
        RewardedAd.load(context, unitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loadedAd = ad
                    isLoading = false
                    _adLoaded.value = true
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadedAd = null
                    isLoading = false
                    _adLoaded.value = false
                }
            })
    }

    /** True when the user hasn't hit the [PointsLimits.REWARDED_GNOTES_PER_DAY] cap yet. */
    fun canWatch(): Boolean = todayGnotes() < PointsLimits.REWARDED_GNOTES_PER_DAY

    /** Gnotes still earnable today via rewarded ads. */
    fun remainingToday(): Int = (PointsLimits.REWARDED_GNOTES_PER_DAY - todayGnotes()).coerceAtLeast(0)

    /** Total rewarded gnotes ever earned (lifetime, already-capped at earn time). */
    fun lifetimeGnotes(): Int = prefs.getInt(KEY_LIFETIME, 0)

    /**
     * Show the rewarded ad. [onDone] fires after the ad closes regardless of outcome.
     * Gnotes are granted inside the reward listener if the user earns the reward.
     * If no ad is loaded, [onDone] fires immediately and a preload is triggered.
     */
    fun show(activity: Activity, onDone: () -> Unit) {
        val ad = loadedAd
        if (ad == null) {
            preload()
            onDone()
            return
        }

        _adLoaded.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadedAd = null
                preload()
                onDone()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                loadedAd = null
                preload()
                onDone()
            }
        }
        ad.show(activity) { grantReward() }
    }

    /** Records the reward and posts the Gnotes banner. Returns actual points earned. */
    fun grantReward(): Int {
        val alreadyToday = todayGnotes()
        val remaining    = (PointsLimits.REWARDED_GNOTES_PER_DAY - alreadyToday).coerceAtLeast(0)
        val earned       = PointsConfig.REWARDED_GNOTES_PER_WATCH.coerceAtMost(remaining)
        if (earned <= 0) return 0

        val newToday    = alreadyToday + earned
        val newLifetime = prefs.getInt(KEY_LIFETIME, 0) + earned
        prefs.edit {
            putInt(KEY_LIFETIME, newLifetime)
            putInt(KEY_TODAY, newToday)
            putInt(KEY_TODAY_DATE, todayEpochDay())
        }

        PointsBannerQueue.post(PointsBannerData(
            pointsEarned     = earned,
            activityLabel    = "Watch ad",
            todayCount       = newToday,
            dailyLimit       = PointsLimits.REWARDED_GNOTES_PER_DAY,
            limitJustReached = newToday >= PointsLimits.REWARDED_GNOTES_PER_DAY,
        ))
        return earned
    }

    private fun todayGnotes(): Int {
        return if (prefs.getInt(KEY_TODAY_DATE, -1) == todayEpochDay())
            prefs.getInt(KEY_TODAY, 0)
        else 0
    }

    // Local-midnight epoch day so the "per day" cap resets at the user's local
    // midnight — matching the Gnotes daily cap (DailyActivityLog). A raw UTC day
    // would reset mid-afternoon for users with large negative UTC offsets.
    private fun todayEpochDay(): Int {
        val now = System.currentTimeMillis()
        return ((now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L).toInt()
    }
}
