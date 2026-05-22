package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Shared drawing cues for the premium wearables tier (earring, watch, chain).
 * Keeping them here gives the gold items a single, consistent finish.
 */

/** Four-point star glint — the shared "shiny" cue, with a bright core. */
internal fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val inner = radius * 0.26f
    val star = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(star, color = color)
    drawCircle(color = color, radius = inner * 0.9f, center = center)
}
