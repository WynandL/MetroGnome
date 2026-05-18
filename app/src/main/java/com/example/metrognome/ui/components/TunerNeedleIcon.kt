package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tuner-needle icon for the bottom navigation bar.
 *
 * Geometry is sized so the arc's peak sits ~1.5 dp from the canvas top,
 * matching the visual height of the adjacent Material icons (MusicNote,
 * Stars, Settings).  The arc sweeps ±40° around 12 o'clock; the pivot
 * sits at 83 % of the canvas height; the needle points straight up
 * (in-tune position).
 *
 * r = 77 % of canvas height → arc top = pivotY - r ≈ 1.5 dp.
 * Horizontal extent at the arc endpoints (±40° from 270°) ≈ 0.1 dp
 * inset from each edge, so the arc fills the full canvas width.
 */
@Composable
fun TunerNeedleIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val cx     = size.width / 2f
        val pivotY = size.height * 0.83f
        val r      = size.height * 0.77f

        // Arc track — muted, spans ±40° around the 12 o'clock position
        drawArc(
            color      = color.copy(alpha = 0.38f),
            startAngle = 230f,
            sweepAngle = 80f,
            useCenter  = false,
            topLeft    = Offset(cx - r, pivotY - r),
            size       = Size(r * 2f, r * 2f),
            style      = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Needle — pointing dead-centre (in-tune position), angle 270° = straight up
        val angle = Math.toRadians(270.0)
        drawLine(
            color       = color,
            start       = Offset(cx, pivotY),
            end         = Offset(
                cx + cos(angle).toFloat() * r * 0.88f,
                pivotY + sin(angle).toFloat() * r * 0.88f,
            ),
            strokeWidth = 1.8.dp.toPx(),
            cap         = StrokeCap.Round,
        )

        // Pivot circle
        drawCircle(color, radius = 2.5.dp.toPx(), center = Offset(cx, pivotY))
    }
}
