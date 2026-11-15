package com.example.metrognome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * The drone's mark: a sine wave that travels while the tone sounds and sits still when it does
 * not. On the Tuner's drone panel it is the identity mark and the state indicator in one (a
 * drone still sounding after a scroll or a tab switch is visibly running); anywhere else the
 * drone is named (the Gnotes earn rules) it is drawn still, so the feature has one icon.
 * Meant for a 26 x 18 dp box, or 24 x 17 beside other 24 dp icons.
 */
@Composable
fun DroneWaveIcon(
    modifier: Modifier = Modifier,
    sounding: Boolean = false,
    idleColor: Color = AppColors.textDim,
    soundingColor: Color = AppColors.gold,
) {
    val transition = rememberInfiniteTransition(label = "droneWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "droneWavePhase",
    )
    val color by animateColorAsState(
        targetValue = if (sounding) soundingColor else idleColor,
        animationSpec = tween(260),
        label = "droneWaveTint",
    )

    Canvas(modifier = modifier) {
        val travel = if (sounding) phase else 0f
        val amplitude = size.height * 0.34f
        val midY = size.height / 2f
        val path = Path()
        val steps = 28
        for (i in 0..steps) {
            val x = size.width * i / steps
            val y = midY + amplitude * sin(2.0 * PI * (1.6 * i / steps - travel)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
