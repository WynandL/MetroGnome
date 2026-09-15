package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Thin horizontal scrollbar that briefly fades in to advertise that the row is scrollable,
 * then fades out. Re-appears while scrolling and lingers briefly after the scroll stops.
 *
 * Width of the scrollbar matches its parent — caller should align it directly under the
 * scrollable row so the thumb position visually corresponds to the visible content.
 */
@Composable
fun FadingHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    initialHoldMs: Long = 2200,
    fadeAfterScrollMs: Long = 1000,
    /**
     * Optional word set into the middle of the bar ("scroll"), flanked by chevrons, for a
     * row whose scrollability is not obvious from its content. A bare bar under a row of
     * chips reads as a scrollbar; the same bar under a drawn keyboard read as part of the
     * instrument, and the dev did not realise the keyboard scrolled. The word sits on a
     * pill of [hintBackground] (the card the bar is on), which is what breaks the bar
     * around it. Fades with the bar. With a hint the bar is [SCROLLBAR_HINT_HEIGHT] tall, not 2.5 dp.
     */
    hint: String? = null,
    hintBackground: Color = AppColors.surfaceDim,
) {
    if (scrollState.maxValue <= 0) return

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.snapTo(1f)
        delay(initialHoldMs.milliseconds)
        alpha.animateTo(0f, tween(600))
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            alpha.snapTo(1f)
        } else {
            delay(fadeAfterScrollMs.milliseconds)
            alpha.animateTo(0f, tween(500))
        }
    }

    Box(
        modifier = modifier
            .height(if (hint == null) 2.5.dp else SCROLLBAR_HINT_HEIGHT)
            .alpha(alpha.value),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(2.5.dp)) {
            val trackColor = AppColors.textDim.copy(alpha = 0.18f)
            val thumbColor = AppColors.gold.copy(alpha = 0.65f)

            drawRect(color = trackColor)

            val viewport = size.width
            val total = viewport + scrollState.maxValue.toFloat()
            val thumbW = (viewport * viewport / total).coerceAtLeast(20f)
            val travelable = (viewport - thumbW).coerceAtLeast(0f)
            val progress =
                if (scrollState.maxValue > 0) scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                else 0f
            val x = travelable * progress

            drawRect(
                color = thumbColor,
                topLeft = Offset(x, 0f),
                size = Size(thumbW, size.height),
            )
        }
        if (hint != null) {
            Text(
                "‹  $hint  ›",
                color = AppColors.textMuted,
                fontSize = 9.sp, lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .background(hintBackground)
                    .padding(horizontal = 6.dp),
            )
        }
    }
}

/** Height of the bar when it carries a [FadingHorizontalScrollbar] hint. */
val SCROLLBAR_HINT_HEIGHT = 14.dp
