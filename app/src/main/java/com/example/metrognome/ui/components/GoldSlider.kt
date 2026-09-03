package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * The tuner's gold slider: a glowing thumb on a gradient rail.
 *
 * Three things distinguish it from a stock Material slider, and all three are about making
 * a value feel like it is being *tuned* rather than set:
 *
 *  1. The active portion of the rail is a gradient, dim at the origin and bright under the
 *     thumb, with a radial bloom where the two meet. The eye is drawn to the value, not to
 *     the whole travelled distance.
 *  2. The thumb's halo brightens on every change and springs back to a resting glow, so
 *     movement registers even when the thumb itself has barely moved.
 *  3. An optional [markerFraction] tick, for a rail with a canonical position on it (the
 *     reference pitch's 440 Hz), which lights up when the value is sitting on it.
 *
 * Extracted from `ReferencePitchCard` when the drone panel needed the same control. Any new
 * slider on the tuner should use this rather than a stock `Slider`, so the screen does not
 * end up with two visual languages for the same gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    /** Position of the canonical tick, 0..1 along the rail; null for no tick. */
    markerFraction: Float? = null,
    /** True when the value is sitting on [markerFraction], which lights the tick gold. */
    markerHighlighted: Boolean = false,
) {
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    // Halo brightens instantly on any value change, springs back to resting glow when idle.
    val haloAlpha = remember { Animatable(RESTING_HALO) }
    LaunchedEffect(value) {
        haloAlpha.snapTo(ACTIVE_HALO)
        haloAlpha.animateTo(
            targetValue = RESTING_HALO,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 80f),
        )
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
        thumb = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp),
            ) {
                Box(Modifier.size(24.dp).background(AppColors.gold.copy(alpha = haloAlpha.value), CircleShape))
                Box(Modifier.size(12.dp).background(AppColors.gold, CircleShape))
            }
        },
        track = { _ ->
            Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                val trackHeight = 2.dp.toPx()
                val centreY = center.y
                val thumbX = size.width * fraction

                // Inactive rail, full width underneath.
                drawLine(
                    color = AppColors.surfaceVariant,
                    start = Offset(0f, centreY),
                    end = Offset(size.width, centreY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )

                if (fraction > 0f) {
                    // Active portion: dim at the origin, bright at the thumb.
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AppColors.gold.copy(alpha = 0.25f),
                                AppColors.gold.copy(alpha = 0.85f),
                            ),
                            startX = 0f,
                            endX = thumbX,
                        ),
                        topLeft = Offset(0f, centreY - trackHeight / 2),
                        size = Size(thumbX, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2),
                    )

                    // Radial bloom where the rail meets the thumb.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AppColors.gold.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(thumbX, centreY),
                            radius = 12.dp.toPx(),
                        ),
                        radius = 12.dp.toPx(),
                        center = Offset(thumbX, centreY),
                    )
                }

                if (markerFraction != null) {
                    val markerX = size.width * markerFraction.coerceIn(0f, 1f)
                    drawLine(
                        color = if (markerHighlighted) AppColors.gold.copy(alpha = 0.6f)
                                else AppColors.textDim.copy(alpha = 0.4f),
                        start = Offset(markerX, centreY - 5.dp.toPx()),
                        end = Offset(markerX, centreY + 5.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
    )
}

/** Halo alpha the thumb settles back to. */
private const val RESTING_HALO = 0.15f

/** Halo alpha the thumb jumps to on any change. */
private const val ACTIVE_HALO = 0.45f
