package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ads.AdBreakQueue
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Transient pill that slides in from the top just before an interstitial ad fires.
 * Collected from [AdBreakQueue]; no external state needed.
 *
 * Drop inside a [androidx.compose.foundation.layout.Box] at [Alignment.TopCenter].
 * The banner displays for ~1.8 s — the ad appears on top of it, so the auto-dismiss
 * timer is a fallback in case the ad fails to show.
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

    AnimatedVisibility(
        visible  = show,
        enter    = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit     = slideOutVertically(tween(280)) { -it } + fadeOut(tween(280)),
        modifier = modifier,
    ) {
        AdBreakPill(message)
    }
}

@Composable
private fun AdBreakPill(message: String) {
    val shape = RoundedCornerShape(50.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(AppColors.surfaceDeep, shape)
            .border(1.dp, AppColors.mediumPurple.copy(alpha = 0.45f), shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.MusicNote,
            contentDescription = null,
            tint               = AppColors.mediumPurple.copy(alpha = 0.75f),
            modifier           = Modifier.size(12.dp),
        )
        Text(
            text      = message,
            color     = AppColors.textSecondary,
            fontSize  = 12.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}
