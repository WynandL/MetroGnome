package com.example.metrognome.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.metrognome.ads.AdBreakQueue
import com.example.metrognome.points.PointsBannerData
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private class QueuedBanner(val model: BannerModel, val showFor: Duration, val isAdBreak: Boolean = false)

/**
 * The one host for every transient top banner: Gnotes earned, loyalty milestones and the
 * pre-ad notice. Drop once inside a Box at [androidx.compose.ui.Alignment.TopCenter].
 *
 * These used to be three hosts at the same spot, each with its own timer, so two events at
 * once painted one pill over the other (UI audit U10). The common case is the end of a
 * session, which posts a Gnotes banner in the same moment `AdManager` posts its notice.
 * Now there is one queue, played one banner at a time, with two rules:
 *
 *  - The ad notice jumps the queue and cuts off whatever is showing, because the
 *    interstitial follows it 3 s later (`AD_BREAK_DELAY_MS`) whatever the UI does. The
 *    banner it interrupted goes back to the front of the queue.
 *  - Nothing starts while the activity is not resumed. An interstitial pauses it, so a
 *    Gnotes banner waits for the ad to close instead of playing unseen underneath it.
 */
@Composable
fun TransientBanners(modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var model by remember { mutableStateOf<BannerModel?>(null) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Everything here runs on the main thread, so the deque needs no locking.
        val pending = ArrayDeque<QueuedBanner>()
        val wake = Channel<Unit>(Channel.CONFLATED)
        var showing: QueuedBanner? = null
        var playing: Job? = null

        launch {
            PointsBannerQueue.events.collect {
                pending.addLast(QueuedBanner(it.toBannerModel(), 2800.milliseconds))
                wake.trySend(Unit)
            }
        }
        launch {
            PointsBannerQueue.milestones.collect {
                pending.addLast(QueuedBanner(milestoneBannerModel(it), 3500.milliseconds))
                wake.trySend(Unit)
            }
        }
        launch {
            AdBreakQueue.messages.collect { message ->
                val interrupted = showing?.takeIf { !it.isAdBreak }
                if (interrupted != null) {
                    pending.addFirst(interrupted)
                    playing?.cancel()
                }
                pending.addFirst(QueuedBanner(adBreakBannerModel(message), 3000.milliseconds, isAdBreak = true))
                wake.trySend(Unit)
            }
        }

        while (true) {
            if (pending.isEmpty()) {
                wake.receive()
                continue
            }
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            val next = pending.removeFirstOrNull() ?: continue
            showing = next
            playing = launch {
                model = next.model
                show = true
                delay(next.showFor)
            }
            playing.join()
            showing = null
            show = false
            delay(EXIT_MS) // let the pill slide out before the next one slides in
        }
    }

    TransientBannerHost(visible = show, modifier = modifier) {
        model?.let { BannerPill(it) }
    }
}

/** Matches [TransientBannerHost]'s exit animation. */
private val EXIT_MS = 280.milliseconds

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

/** Loyalty milestone celebration: same asset as Gnotes, same celebratory gold. */
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

/** The pre-ad heads-up: deliberately subtle, neutral and italic. It is not a celebration. */
private fun adBreakBannerModel(message: String) = BannerModel(
    accent   = AppColors.mediumPurple,
    icon     = Icons.Filled.MusicNote,
    segments = listOf(BannerSegment(message)),
    italic   = true,
)
