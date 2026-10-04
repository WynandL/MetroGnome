package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * Metro's Collection icon: a treasure chest, drawn like the app's other glyphs
 * ([ChordGridIcon], [TunerNeedleIcon]): one colour, 1.5 dp lines for the structure, faint ones
 * for detail. A domed lid over a box, a lock straddling the seam, two faint straps, and one
 * glint (the sparkle of whatever is inside).
 */
@Composable
fun MetroCollectionIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = AppColors.gold) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        // Absolute dp, not scaled with the icon: the other glyphs ([TunerNeedleIcon] 1.5-1.8 dp,
        // [RhythmPulseIcon] 2 dp) keep one line weight at every size, and so must this.
        val heavy = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val faint = tint.copy(alpha = 0.38f)

        // Straps first, so the structure draws over them.
        listOf(8f, 16f).forEach { x ->
            drawLine(faint, p(x, 6.1f), p(x, 20f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
        }

        // Box.
        drawRoundRect(
            tint, topLeft = p(4f, 12f), size = Size(16f * u, 8.5f * u),
            cornerRadius = CornerRadius(1.6f * u), style = heavy,
        )

        // Domed lid.
        val lid = Path().apply {
            moveTo(4f * u, 12f * u)
            lineTo(4f * u, 9.8f * u)
            cubicTo(4f * u, 6.6f * u, 7.6f * u, 4.6f * u, 12f * u, 4.6f * u)
            cubicTo(16.4f * u, 4.6f * u, 20f * u, 6.6f * u, 20f * u, 9.8f * u)
            lineTo(20f * u, 12f * u)
        }
        drawPath(lid, tint, style = heavy)

        // Lock straddling the seam.
        drawRoundRect(
            tint, topLeft = p(10.3f, 10.4f), size = Size(3.4f * u, 4.8f * u),
            cornerRadius = CornerRadius(0.9f * u),
        )

        // The sparkle of what is inside.
        drawGlint(p(20.4f, 3.6f), 1.8f * u, tint)
    }
}

private fun DrawScope.drawGlint(c: Offset, r: Float, color: Color) {
    val n = r * 0.2f
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + n, c.y - n, c.x + r, c.y)
        quadraticTo(c.x + n, c.y + n, c.x, c.y + r)
        quadraticTo(c.x - n, c.y + n, c.x - r, c.y)
        quadraticTo(c.x - n, c.y - n, c.x, c.y - r)
        close()
    }
    drawPath(path, color)
}
