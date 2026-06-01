package com.example.metrognome.points.rewards

import android.content.Context
import androidx.core.content.edit
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
 * Manages Beats-based rewards that live outside the billing/purchase system.
 *
 * Currently: reaching [RewardConfig.MAX_DAILY_BEATS] in a single day grants
 * [RewardConfig.AD_FREE_DAYS] days of ad-free experience. The grant is recorded
 * once per calendar day so back-to-back triggering cannot stack the reward.
 *
 * Listens to [PointsBannerQueue] — any points-earning activity triggers a check.
 * The caller is also responsible for calling [refresh] on app resume so that
 * an expired reward is revoked without requiring a new activity event.
 *
 * Reward state is stored in its own SharedPreferences ("points_rewards") and is
 * entirely independent of billing. [isAdFreeActive] should be OR-combined with
 * [com.example.metrognome.billing.BillingManager.isAdFree] in the ViewModel.
 */
class RewardManager(context: Context, scope: CoroutineScope) {

    private val prefs    = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tracker  = MetroItemTracker(context)
    private val dailyLog = DailyActivityLog(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _isAdFreeActive = MutableStateFlow(isAdFreeNow())
    val isAdFreeActive: StateFlow<Boolean> = _isAdFreeActive.asStateFlow()

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

    /** The ms timestamp until which the earned ad-free reward is active (0 if none). */
    fun adFreeUntilMs(): Long = prefs.getLong(KEY_AD_FREE_UNTIL_MS, 0L)

    /**
     * Re-evaluate whether the stored reward has expired.
     * Call on every app resume so a reward that expired overnight is revoked promptly.
     */
    fun refresh() {
        _isAdFreeActive.value = isAdFreeNow()
    }

    private fun isAdFreeNow() = System.currentTimeMillis() < adFreeUntilMs()

    private fun checkAndGrant() {
        _isAdFreeActive.value = isAdFreeNow()

        // Only grant once per calendar day — prevents a single sustained session
        // from repeatedly triggering the grant after the threshold is crossed.
        val today = dateFormat.format(Date())
        if (prefs.getString(KEY_GRANTED_DATE, null) == today) return

        val todayBeats = PointsCalculator.todayBeats(dailyLog.todayActivity(tracker))
        if (todayBeats < RewardConfig.MAX_DAILY_BEATS) return

        val until = System.currentTimeMillis() + RewardConfig.AD_FREE_DAYS * 86_400_000L
        prefs.edit {
            putLong(KEY_AD_FREE_UNTIL_MS, until)
            putString(KEY_GRANTED_DATE, today)
        }
        _isAdFreeActive.value = true
        _rewardGranted.tryEmit(until)
    }

    companion object {
        private const val PREFS_NAME           = "points_rewards"
        private const val KEY_AD_FREE_UNTIL_MS = "ad_free_until_ms"
        private const val KEY_GRANTED_DATE     = "reward_granted_date"
    }
}
