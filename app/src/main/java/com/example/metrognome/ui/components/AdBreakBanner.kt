package com.example.metrognome.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.metrognome.ads.AdBreakQueue
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Transient pill that slides in just before an interstitial ad fires. Renders the
 * shared [BannerPill] asset in a deliberately subtle neutral/italic tone (it is a
 * heads-up before an ad, not a celebration). Collected from [AdBreakQueue].
 *
 * Drop inside a Box at [androidx.compose.ui.Alignment.TopCenter]. Shown for ~3 s as
 * a fallback; the ad normally appears on top of it first.
 */
@Composable
fun AdBreakBanner(modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdBreakQueue.messages.collect { msg ->
            if (show) {
                show = false
                delay(280.milliseconds)
            }
            message = msg
            show = true
            delay(3000.milliseconds)
            show = false
        }
    }

    TransientBannerHost(visible = show, modifier = modifier) {
        BannerPill(
            BannerModel(
                accent   = AppColors.mediumPurple,
                icon     = Icons.Filled.MusicNote,
                segments = listOf(BannerSegment(message)),
                italic   = true,
            )
        )
    }
}
