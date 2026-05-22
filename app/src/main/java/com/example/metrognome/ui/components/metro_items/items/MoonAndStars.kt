package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.sin

/**
 * A crescent moon that rises in the upper sky whenever Metro plays late.
 *
 * Drawn as a dark base disc (the full moon silhouette) with a gold disc clipped
 * to the moon boundary on top. The clip ensures no gold overflows the moon circle,
 * so both the outer lit limb and the inner terminator are arcs of the same radius —
 * a geometrically consistent crescent with no occluder disc floating inside.
 *
 * Stars: five 4-pointed diamond stars with secondary diagonal rays, glow halos,
 * and independent twinkle phases. Three tones (warm gold, blue-white, faint silver)
 * give the field natural variety — astronomer's trick: star colour indicates temperature.
 *
 * Position: size.width * 0.82f, size.height * 0.10f — width- and height-anchored
 * so it always sits in the upper-right corner of the sky regardless of device aspect ratio.
 */
object MoonAndStars : MetroItem {

    override val id             = "moon_and_stars"
    override val displayName    = "Crescent Moon"
    override val description    = "A crescent moon that rises when Metro plays deep into the night."
    override val earnedMessage  = "10 hours of music! The moon herself has noticed. She rises now whenever Metro plays, bathing the forest in silver light."
    override val isBodyAttached  = false

    // Moon draws at (canvasW*0.82, canvasH*0.10). Body-space conversion (subtract cx, baseY):
    //   Y: 0.10*h - 0.97*h = -0.87*h → -0.87*17 = -14.8u  (independent of aspect ratio)
    //   X: 0.82*w - 0.50*w = 0.32*w → 0.32*(w/h)*17 ≈ 2.7u on a typical portrait phone
    // Old hitCenter(-13.0u) was 1.8u off in Y — outside the 1.3u radius, so taps never landed.
    override fun hitCenter(u: Float) = Offset(2.7f * u, -14.8f * u)
    override fun hitRadius(u: Float) = u * 1.3f

    // mx = canvasW*0.82, my = canvasH*0.10 — zooms into just the crescent
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.82f, canvasH * 0.10f)
    override fun previewRadius(u: Float) = u * 1.2f

    // ── Palette ───────────────────────────────────────────────────────────────
    private val moonGold   = Color(0xFFFFDB4D)
    private val moonYellow = Color(0xFFFFF0A0)
    private val moonShadow = Color(0xFF1A1050)
    private val glowColor  = Color(0x33FFF0A0)
    // Star tones — warm = giant/nearby star, cool = blue-white main sequence, faint = distant
    private val starWarm   = Color(0xFFFFF0C0)   // warm pale gold
    private val starCool   = Color(0xFFEEF4FF)   // blue-white
    private val starFaint  = Color(0xFFBBCCFF)   // distant silvery blue

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t    = (System.nanoTime() / 1_000_000_000.0).toFloat()
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

        // Stars — screen-relative, independent twinkle phases, varied tone
        drawStarSpark(u, size.width * 0.60f, size.height * 0.14f, 0.11f, t, 0.00f, starCool)
        drawStarSpark(u, size.width * 0.72f, size.height * 0.05f, 0.09f, t, 1.20f, starFaint)
        drawStarSpark(u, size.width * 0.90f, size.height * 0.12f, 0.13f, t, 2.40f, starWarm)
        drawStarSpark(u, size.width * 0.55f, size.height * 0.08f, 0.08f, t, 3.60f, starFaint)
        drawStarSpark(u, size.width * 0.88f, size.height * 0.04f, 0.10f, t, 4.80f, starCool)
    }

    /**
     * A proper 4-pointed diamond star with:
     *   - Soft radial glow halo
     *   - Sharp main rays (cardinal) via Path — vertical slightly longer than horizontal
     *   - Faint secondary diagonal rays (45°-rotated copy at ~50% scale)
     *   - Bright white core dot
     *   - Slow independent twinkle via sin(t * 1.9 + phase)
     */
    private fun DrawScope.drawStarSpark(
        u: Float, x: Float, y: Float, rFactor: Float,
        t: Float, phase: Float, color: Color,
    ) {
        val r       = rFactor * u
        val twinkle = 0.80f + 0.20f * sin(t * 1.9f + phase)
        val arm     = r * 2.6f * twinkle
        val inner   = arm * 0.13f

        // Soft glow halo unique to each star
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.22f * twinkle), Color.Transparent),
                center = Offset(x, y),
                radius = arm * 2.2f,
            ),
            radius = arm * 2.2f,
            center = Offset(x, y),
        )

        // Main 4-pointed star (cardinal directions)
        drawSharpStar(x, y, longArm = arm, horizArm = arm * 0.78f, innerR = inner, color = color)

        // Secondary diagonal rays — faint, 45°-rotated, half scale
        withTransform({ rotate(45f, pivot = Offset(x, y)) }) {
            drawSharpStar(
                x, y,
                longArm  = arm * 0.50f,
                horizArm = arm * 0.39f,
                innerR   = inner * 0.50f,
                color    = color,
                alpha    = 0.42f,
            )
        }

        // Bright white core — the star's nucleus catching full light
        drawCircle(Color.White.copy(alpha = 0.90f * twinkle), radius = r * 0.72f, center = Offset(x, y))
    }

    /**
     * A sharp 4-pointed diamond star. Each ray tapers to a needle tip; the 4 concave
     * inner points sit at 45° between the ray tips, creating a clean hourglass silhouette
     * rather than a rounded blob.
     *
     * longArm  — vertical ray length (top + bottom)
     * horizArm — horizontal ray length (slightly shorter looks more natural)
     * innerR   — distance from centre to each concave inner point
     */
    private fun DrawScope.drawSharpStar(
        cx: Float, cy: Float,
        longArm: Float, horizArm: Float, innerR: Float,
        color: Color, alpha: Float = 1f,
    ) {
        val ic = innerR * 0.7071f  // inner concave projected to 45° components
        drawPath(
            Path().apply {
                moveTo(cx,            cy - longArm)   // top tip
                lineTo(cx + ic,       cy - ic)         // TR inner concave
                lineTo(cx + horizArm, cy)              // right tip
                lineTo(cx + ic,       cy + ic)         // BR inner concave
                lineTo(cx,            cy + longArm)    // bottom tip
                lineTo(cx - ic,       cy + ic)         // BL inner concave
                lineTo(cx - horizArm, cy)              // left tip
                lineTo(cx - ic,       cy - ic)         // TL inner concave
                close()
            },
            color = color,
            alpha = alpha,
        )
    }
}
