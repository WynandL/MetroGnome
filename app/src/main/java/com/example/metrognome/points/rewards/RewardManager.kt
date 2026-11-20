package com.example.metrognome.points.rewards

import android.content.Context
import androidx.core.content.edit
import com.example.metrognome.adfree.AdFreeWindow
import com.example.metrognome.adfree.SystemTime
import com.example.metrognome.adfree.TimeReading
import com.example.metrognome.adfree.WindowPrefs
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.points.DailyActivityLog
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.points.PointsCalculator
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Gnotes-based rewards that live outside the billing/purchase system.
 *
 * Currently: reaching [RewardConfig.MAX_DAILY_BEATS] in a single day grants
 * [RewardConfig.AD_FREE_DAYS] days of ad-free experience. The grant is recorded
 * once per calendar day so back-to-back triggering cannot stack the reward.
 *
 * Listens to [PointsBannerQueue] — any points-earning activity triggers a check.
 * The ad-free time runs on [AdFreeWindow] (2026-11-20), the clock the ad-free trial uses:
 * the ViewModel calls [refresh] every 20 s and on every return to the foreground, so the
 * days end on time whether or not the app is open, and moving the date does not stretch
 * them. Before this they ended only on the next resume, so ads stayed off for anyone who
 * kept the app open past the end. A grant stored the old way (an end time only) is read
 * once as a window and carries on.
 *
 * Reward state is stored in its own SharedPreferences ("points_rewards") and is
 * entirely independent of billing. [isAdFreeActive] should be OR-combined with
 * [com.example.metrognome.billing.BillingManager.isAdFree] in the ViewModel.
 */
class RewardManager(
    context: Context,
    scope: CoroutineScope,
    private val time: () -> TimeReading = SystemTime(context)::read,
) {

    private val appContext = context.applicationContext
    private val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tracker  = MetroItemTracker(context)
    private val dailyLog = DailyActivityLog(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var window: AdFreeWindow? = loadWindow()

    private val _isAdFreeActive = MutableStateFlow(isAdFreeNow())
    val isAdFreeActive: StateFlow<Boolean> = _isAdFreeActive.asStateFlow()

    private val _endsAtWallMs = MutableStateFlow(endsAt())
    /** When the ad-free days end, on the wall clock; null when none are running. Display only. */
    val endsAtWallMs: StateFlow<Long?> = _endsAtWallMs.asStateFlow()

    /**
     * Emits the expiry timestamp (ms since epoch) each time a reward is freshly granted.
     * Collect this to notify the user (e.g. a congratulations dialog).
     */
    private val _rewardGranted = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val rewardGranted: SharedFlow<Long> = _rewardGranted.asSharedFlow()

    init {
        scope.launch {
            PointsBannerQueue.events.collect { _ -> checkAndGrant() }
        }
    }

    /** Re-evaluates the ad-free days against the clock. Cheap; called every 20 s and on resume. */
    fun refresh() {
        _isAdFreeActive.value = isAdFreeNow()
        _endsAtWallMs.value = endsAt()
    }

    /** Brings the window up to date (saving the ratchet) and says whether it is still running. */
    private fun isAdFreeNow(): Boolean {
        val w = window ?: return false
        if (w.expired) return false
        val next = w.advance(time())
        if (next != w) { window = next; saveWindow(next) }
        return !next.expired
    }

    private fun endsAt(): Long? = window?.takeIf { !it.expired }?.endsAtWallMs(time())

    private fun loadWindow(): AdFreeWindow? =
        WindowPrefs.load(appContext, prefs, WINDOW)
            ?: prefs.getLong(KEY_AD_FREE_UNTIL_MS, 0L).takeIf { it > 0L }
                ?.let { AdFreeWindow.fromLegacyEnd(it, RewardConfig.AD_FREE_DAYS * 86_400_000L) }

    /** The 20-second ratchet: `apply`, off the main thread. */
    private fun saveWindow(w: AdFreeWindow?) = WindowPrefs.save(appContext, prefs, WINDOW, w, durable = false)

    private fun checkAndGrant() {
        refresh()

        // Only grant once per calendar day — prevents a single sustained session
        // from repeatedly triggering the grant after the threshold is crossed.
        val today = dateFormat.format(Date())
        if (prefs.getString(KEY_GRANTED_DATE, null) == today) return

        val todayBeats = PointsCalculator.todayBeats(dailyLog.todayActivity(tracker))
        if (todayBeats < RewardConfig.MAX_DAILY_BEATS) return

        val now = time()
        val granted = AdFreeWindow.start(now, RewardConfig.AD_FREE_DAYS * 86_400_000L)
        window = granted
        prefs.edit(commit = true) {
            WindowPrefs.save(appContext, this, WINDOW, granted, durable = true)
            remove(KEY_AD_FREE_UNTIL_MS)
            putString(KEY_GRANTED_DATE, today)
        }
        refresh()
        _rewardGranted.tryEmit(granted.endsAtWallMs(now))
        AnalyticsTracker.logRewardGranted("ad_free", RewardConfig.AD_FREE_DAYS)
    }

    companion object {
        private const val PREFS_NAME           = "points_rewards"
        /** The pre-2026-11-20 storage (an end time only); read once, then replaced by [WINDOW]. */
        private const val KEY_AD_FREE_UNTIL_MS = "ad_free_until_ms"
        private const val WINDOW               = "ad_free"
        private const val KEY_GRANTED_DATE     = "reward_granted_date"
    }
}
