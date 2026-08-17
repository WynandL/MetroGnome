package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.ItemPalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shared "Gnotes just ticked up" flair: a brief burst of gold sparks that radiates from
 * whatever view is displaying a live Gnotes total. Wrap any such view in this composable
 * and pass the live count - a burst fires automatically whenever [count] rises while it's
 * on screen (e.g. [MetronomeViewModel]'s `gnoteCount`, refreshed every 10s during play).
 *
 * Deliberately never fires on first composition: [count] is captured as the starting
 * baseline, so mounting on an already-current value (navigating to a screen, or a
 * pre-existing count-up-from-zero reveal animation like Rhythm's PointsCard) does not
 * spawn a spurious burst. Only a genuine rise *after* that baseline counts as a tick.
 *
 * Purely additive: draws on top of [content] via an overlaid same-size [Canvas] and never
 * touches [content]'s own animations (e.g. PointsCard's number roll-up / scale pulse), so
 * it composes safely with a caller that already animates on the same value.
 *
 * Drawing is not clipped to this composable's own bounds by default in Compose, so the
 * burst can visibly overshoot a small pill - unless an ancestor clips (e.g. a card's
 * rounded-corner `Modifier.clip`), in which case the burst is contained within that shape.
 */
@Composable
fun GnoteTickFlair(
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var previousCount by remember { mutableIntStateOf(count) }
    val bursts = remember { mutableStateListOf<GnoteBurst>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(count) {
        if (count > previousCount) {
            val burst = GnoteBurst(seed = Random.nextInt())
            bursts += burst
            scope.launch {
                burst.progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(GNOTE_BURST_DURATION_MS, easing = LinearOutSlowInEasing),
                )
                bursts -= burst
            }
        }
        previousCount = count
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
        bursts.forEach { burst ->
            key(burst) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawGnoteBurst(burst.progress.value, burst.seed)
                }
            }
        }
    }
}

private const val GNOTE_BURST_DURATION_MS = 750

private class GnoteBurst(val seed: Int) {
    val progress = Animatable(0f)
}

private const val TWO_PI = (2.0 * PI).toFloat()

private fun DrawScope.drawGnoteBurst(progress: Float, seed: Int) {
    if (progress <= 0f || progress >= 1f) return

    val rng = Random(seed)
    val center = Offset(size.width / 2f, size.height / 2f)
    val unit = size.minDimension.coerceAtLeast(28f)
    val palette = listOf(AppColors.gold, ItemPalette.goldLight, ItemPalette.goldMid)

    val ease = 1f - (1f - progress) * (1f - progress)   // ease-out
    val fade = (1f - progress).coerceIn(0f, 1f)

    // Quick warm flash at the moment of the tick.
    if (progress < 0.3f) {
        val flash = 1f - progress / 0.3f
        drawCircle(
            color = AppColors.gold.copy(alpha = 0.30f * flash),
            radius = unit * (0.55f + progress * 0.9f),
            center = center,
        )
    }

    // Expanding, fading ring - the "ping".
    drawCircle(
        color = AppColors.gold.copy(alpha = 0.45f * fade),
        radius = unit * (0.45f + ease * 1.1f),
        center = center,
        style = Stroke(width = (unit * 0.05f).coerceAtLeast(1.5f)),
    )

    // Small sparks radiating outward, drifting gently up as they fade - echoes the
    // rising-note feel rather than falling confetti.
    val sparkCount = 8 + rng.nextInt(4)
    for (i in 0 until sparkCount) {
        val angle = (i.toFloat() / sparkCount) * TWO_PI + rng.nextFloat() * 0.35f
        val speed = 0.70f + rng.nextFloat() * 0.55f
        val r = unit * (0.9f + speed) * ease
        val ca = cos(angle.toDouble()).toFloat()
        val sa = sin(angle.toDouble()).toFloat()

        val drift = unit * 0.30f * progress
        val headX = center.x + ca * r
        val headY = center.y + sa * r - drift
        val tailLen = unit * 0.28f * speed
        val tailX = center.x + ca * (r - tailLen)
        val tailY = center.y + sa * (r - tailLen) - drift

        val color = palette[i % palette.size]
        val strokeWidth = (unit * 0.07f * (1f - progress * 0.5f)).coerceAtLeast(1.2f)

        drawLine(
            color = color.copy(alpha = fade),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(color.copy(alpha = fade), radius = strokeWidth * 0.85f, center = Offset(headX, headY))
    }
}
