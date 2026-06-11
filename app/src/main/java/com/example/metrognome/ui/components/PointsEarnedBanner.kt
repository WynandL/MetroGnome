package com.example.metrognome.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.metrognome.points.PointsBannerData
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Transient pill that slides in from the top after a Gnotes-earning activity, then
 * auto-dismisses. Renders the shared [BannerPill] asset; collects from
 * [PointsBannerQueue] so no external state is needed.
 *
 * Drop inside a Box at [androidx.compose.ui.Alignment.TopCenter].
 */
@Composable
fun PointsEarnedBanner(modifier: Modifier = Modifier) {
    var bannerData by remember { mutableStateOf<PointsBannerData?>(null) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PointsBannerQueue.events.collect { data ->
            if (show) {
                show = false
                delay(280.milliseconds)
            }
            bannerData = data
            show = true
            delay(2800.milliseconds)
            show = false
        }
    }

    TransientBannerHost(visible = show, modifier = modifier) {
        bannerData?.let { BannerPill(it.toBannerModel()) }
    }
}

/** Loyalty milestone celebration — same asset as Gnotes, same celebratory gold. */
@Composable
fun LoyaltyMilestoneBanner(modifier: Modifier = Modifier) {
    var days by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PointsBannerQueue.milestones.collect { d ->
            if (show) {
                show = false
                delay(280.milliseconds)
            }
            days = d
            show = true
            delay(3500.milliseconds)
            show = false
        }
    }

    TransientBannerHost(visible = show, modifier = modifier) {
        BannerPill(milestoneBannerModel(days))
    }
}

// ── Event → BannerModel mappers ─────────────────────────────────────────────────

private fun PointsBannerData.toBannerModel(): BannerModel {
    val atLimit = limitJustReached || (pointsEarned == 0 && todayCount >= dailyLimit)
    return if (pointsEarned > 0) {
        BannerModel(
            accent   = AppColors.gold,
            icon     = Icons.Filled.Bolt,
            lead     = "+$pointsEarned",
            leadUnit = PointsConfig.CURRENCY_NAME,
            segments = listOf(
                BannerSegment(activityLabel),
                BannerSegment("$todayCount / $dailyLimit today", strong = atLimit),
            ),
        )
    } else {
        // Limit already reached before this completion: neutral purple, no headline token.
        BannerModel(
            accent   = AppColors.primaryPurple,
            segments = listOf(
                BannerSegment("Daily limit reached", strong = true),
                BannerSegment(activityLabel),
                BannerSegment("$todayCount / $dailyLimit today", strong = true),
            ),
        )
    }
}

private fun milestoneBannerModel(days: Int) = BannerModel(
    accent   = AppColors.gold,
    icon     = Icons.Filled.EmojiEvents,
    lead     = "$days",
    leadUnit = if (days == 1) "day" else "days",
    segments = listOf(BannerSegment(milestoneLabel(days))),
)

private fun milestoneLabel(days: Int) = when (days) {
    7    -> "One week with Metro"
    30   -> "One month strong"
    60   -> "Two months in"
    100  -> "100 days. Legend."
    365  -> "One full year"
    else -> "$days-day milestone"
}
