package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Pulse-line icon for the Rhythm tab in the bottom navigation bar.
 *
 * Draws an EKG-style waveform: flat baseline → sharp rise to peak →
 * drop past baseline → return → flat right.  The baseline sits at 58 %
 * of canvas height so the spike fills most of the upper space while the
 * small trough uses a portion of the lower space, keeping the shape
 * visually centred at 24 dp.
 */
@Composable
fun RhythmPulseIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val w     = size.width
        val h     = size.height
        val baseY = h * 0.58f

        val path = Path().apply {
            moveTo(0f,         baseY)      // left edge
            lineTo(w * 0.28f,  baseY)      // flat left
            lineTo(w * 0.40f,  h * 0.10f) // rise to peak
            lineTo(w * 0.52f,  h * 0.80f) // drop to trough
            lineTo(w * 0.62f,  baseY)      // back to baseline
            lineTo(w,          baseY)      // flat right
        }

        drawPath(
            path  = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap   = StrokeCap.Round,
                join  = StrokeJoin.Round,
            ),
        )
    }
}
