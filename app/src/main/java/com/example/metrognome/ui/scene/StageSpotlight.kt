package com.example.metrognome.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/*
 * The two pieces every stage carousel shares (the sound stage, the drone's, the meter stage):
 * the spotlight that marks centre stage, and the page dots under the carousel. Moved out of
 * SoundStage.kt unchanged on 2026-11-21 when the meter stage became the second user.
 */

/**
 * Centre stage: a beam falling from [top] (usually above the canvas; draw unclipped) onto a lit
 * floor under an object of radius [r] at [c]. [glow] 0..1 brightens both with a sounding beat.
 */
internal fun DrawScope.drawStageSpotlight(c: Offset, r: Float, top: Float, glow: Float) {
    val floorY = c.y + r * 1.05f
    val floor = Rect(c.x - r * 1.3f, floorY - r * 0.22f, c.x + r * 1.3f, floorY + r * 0.22f)
    // The beam takes in the whole floor oval, so the pool of light is one shade; ending
    // it at the oval's middle lit only the back half.
    val trapezoid = Path().apply {
        moveTo(c.x - r * 0.22f, top)
        lineTo(c.x + r * 0.22f, top)
        lineTo(c.x + r * 1.25f, floorY)
        lineTo(c.x - r * 1.25f, floorY)
        close()
    }
    val beam = Path().apply { op(trapezoid, Path().apply { addOval(floor) }, PathOperation.Union) }
    drawPath(
        beam,
        Brush.verticalGradient(
            listOf(Color.Transparent, AppColors.gold.copy(alpha = 0.07f + 0.05f * glow)),
            startY = top, endY = c.y + r,
        ),
    )
    drawOval(
        Brush.radialGradient(
            listOf(AppColors.gold.copy(alpha = 0.22f + 0.10f * glow), Color.Transparent),
            Offset(c.x, floorY), r * 1.3f,
        ),
        topLeft = Offset(c.x - r * 1.3f, floorY - r * 0.22f),
        size = Size(r * 2.6f, r * 0.44f),
    )
}

/** A stage medallion's lifted disc, lit from the upper left like every raised face in the app. */
internal fun DrawScope.drawStageDisc(c: Offset, r: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(AppColors.surfaceActive, AppColors.card),
            Offset(c.x - r * 0.35f, c.y - r * 0.45f), r * 1.5f,
        ),
        r, c,
    )
}

/** One dot per page, the current one larger; [gold] picks pages drawn in gold (premium). */
@Composable
internal fun StagePageDots(count: Int, current: Int, gold: (Int) -> Boolean = { false }) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val on = i == current
            Canvas(Modifier.size(if (on) 8.dp else 6.dp)) {
                val base = if (gold(i)) AppColors.gold else AppColors.textPrimary
                drawCircle(base.copy(alpha = if (on) 1f else 0.30f))
            }
        }
    }
}
