package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A crescent moon that rises in the upper sky whenever Metro plays late.
 * Drawn as two overlapping circles: a bright gold disc, then a dark disc
 * offset to reveal the crescent. A few extra bright stars cluster around it.
 *
 * Position: size.width * 0.82f, size.height * 0.10f — width- and height-anchored
 * so it always sits in the upper-right corner of the sky regardless of device
 * aspect ratio. Previously used cx+3.5u which is height-derived and too central.
 */
object MoonAndStars : MetroItem {

    override val id             = "moon_and_stars"
    override val displayName    = "Crescent Moon"
    override val description    = "A crescent moon that rises when Metro plays deep into the night."
    override val unlockCondition = "Play the metronome for 10 hours total"
    override val earnedMessage  = "10 hours of music! The moon herself has noticed. She rises now whenever Metro plays, bathing the forest in silver light."
    override val isBodyAttached  = false

    // Approximation only — moon is near upper-right corner, exact position is screen-relative
    override fun hitCenter(u: Float) = Offset(3.0f * u, -13.0f * u)
    override fun hitRadius(u: Float) = u * 1.3f

    private val moonGold    = Color(0xFFFFDB4D)
    private val moonYellow  = Color(0xFFFFF0A0)
    private val moonShadow  = Color(0xFF1A1050)   // matches background night sky
    private val glowColor   = Color(0x33FFF0A0)
    private val starWhite   = Color(0xFFFFFFFF)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Width- and height-anchored: always upper-right corner of the visible canvas
        val mx = size.width  * 0.82f
        val my = size.height * 0.10f
        val clampedMy = my   // already in the upper portion by definition

        // Soft ambient glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = Offset(mx, clampedMy),
                radius = u * 2.2f
            ),
            radius = u * 2.2f,
            center = Offset(mx, clampedMy)
        )

        // Crescent: full gold disc, then occlude with a shadow disc offset right
        drawCircle(moonGold, radius = 0.90f * u, center = Offset(mx, clampedMy))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(moonYellow, moonGold),
                center = Offset(mx - 0.2f * u, clampedMy - 0.2f * u),
                radius = 0.6f * u
            ),
            radius = 0.90f * u,
            center = Offset(mx, clampedMy)
        )
        // Occluding disc to create the crescent shape
        drawCircle(moonShadow, radius = 0.80f * u, center = Offset(mx + 0.45f * u, clampedMy - 0.15f * u))

        // Star sparks clustered around the moon — all screen-relative
        drawStarSpark(u, size.width * 0.60f, size.height * 0.14f, 0.11f)
        drawStarSpark(u, size.width * 0.72f, size.height * 0.05f, 0.09f)
        drawStarSpark(u, size.width * 0.90f, size.height * 0.12f, 0.13f)
        drawStarSpark(u, size.width * 0.55f, size.height * 0.08f, 0.08f)
        drawStarSpark(u, size.width * 0.88f, size.height * 0.04f, 0.10f)
    }

    private fun DrawScope.drawStarSpark(u: Float, x: Float, y: Float, rFactor: Float) {
        val r = rFactor * u
        drawLine(starWhite, Offset(x - r * 2.2f, y), Offset(x + r * 2.2f, y), strokeWidth = r * 0.55f, cap = StrokeCap.Round)
        drawLine(starWhite, Offset(x, y - r * 2.2f), Offset(x, y + r * 2.2f), strokeWidth = r * 0.55f, cap = StrokeCap.Round)
        drawCircle(starWhite, radius = r * 0.9f, center = Offset(x, y))
    }
}
