package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A crescent moon that rises in the upper sky whenever Metro plays late.
 *
 * Drawn as a dark base disc (the full moon silhouette) with a gold disc clipped
 * to the moon boundary on top. The clip ensures no gold overflows the moon circle,
 * so both the outer lit limb and the inner terminator are arcs of the same radius —
 * a geometrically consistent crescent with no occluder disc floating inside.
 *
 * Position: size.width * 0.82f, size.height * 0.10f — width- and height-anchored
 * so it always sits in the upper-right corner of the sky regardless of device aspect ratio.
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

    // mx = canvasW*0.82, my = canvasH*0.10 — zooms into just the crescent
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.82f, canvasH * 0.10f)
    override fun previewRadius(u: Float) = u * 1.2f

    private val moonGold    = Color(0xFFFFDB4D)
    private val moonYellow  = Color(0xFFFFF0A0)
    private val moonShadow  = Color(0xFF1A1050)
    private val glowColor   = Color(0x33FFF0A0)
    private val starWhite   = Color(0xFFFFFFFF)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val mx   = size.width  * 0.82f
        val my   = size.height * 0.10f
        val moonR = 0.90f * u

        // Soft ambient glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = Offset(mx, my),
                radius = u * 2.2f
            ),
            radius = u * 2.2f,
            center = Offset(mx, my)
        )

        // All moon drawing inside one clipPath so the boundary has a single anti-aliased edge.
        // Gold disc is drawn oversized (fills past the clip) to avoid any fringe at the edge.
        clipPath(Path().apply {
            addOval(Rect(mx - moonR, my - moonR, mx + moonR, my + moonR))
        }) {
            // Gold fills the entire moon disc
            drawCircle(moonGold, radius = moonR * 1.1f, center = Offset(mx, my))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(moonYellow, moonGold),
                    center = Offset(mx - 0.20f * u, my - 0.20f * u),
                    radius = 0.60f * u
                ),
                radius = moonR * 1.1f,
                center = Offset(mx, my)
            )
            // Dark occluder covers the night side. Slightly larger than moonR so anti-aliasing
            // at the intersection points can't let gold peek through; clip prevents any overflow.
            drawCircle(moonShadow, radius = u * 0.96f, center = Offset(mx + 0.55f * u, my - 0.10f * u))
        }

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
