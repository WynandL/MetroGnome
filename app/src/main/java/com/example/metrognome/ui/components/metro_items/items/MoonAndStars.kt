package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.sin

/**
 * A crescent moon that rises in the upper sky whenever Metro plays late.
 *
 * Only the lit crescent is drawn: the moon disc with the night-side disc cut out of it
 * (`PathOperation.Difference`), filled once with the gold gradient. The night side used to be
 * painted as a dark navy disc, which read as a second, darker moon against the sky; since
 * 2026-11-20 the sky shows through it instead. One path, so the crescent has a single
 * anti-aliased edge, and its outer limb and inner terminator are both true circular arcs.
 *
 * Stars: five 4-pointed diamond stars with secondary diagonal rays, glow halos,
 * and independent twinkle phases. Three tones (warm gold, blue-white, faint silver)
 * give the field natural variety — astronomer's trick: star colour indicates temperature.
 *
 * Position: size.width * MOON_X, size.height * MOON_Y, width- and height-anchored so it
 * always sits in the upper-right of the sky regardless of device aspect ratio. Moved down
 * from 0.10 to 0.18 of the height (stars with it) on 2026-11-19, when the Home screen's
 * sound pill took the top-right corner: at 0.10 the pill covered the top of the moon and two
 * stars, and took the moon's taps.
 */
private const val MOON_X = 0.82f
private const val MOON_Y = 0.18f

/**
 * The moon's top never rises above this far below the canvas top, whatever the canvas
 * height: the Home sound pill is a fixed size in dp (12 dp margin + about 32 dp, more with
 * large fonts), while MOON_Y scales with the canvas, so on a short canvas (a small phone)
 * the fraction alone put the moon under the pill.
 */
private const val MOON_TOP_CLEAR_DP = 58f

object MoonAndStars : MetroItem {

    override val id             = "moon_and_stars"
    override val displayName    = "Crescent Moon"
    override val description    = "A crescent moon that rises when Metro plays deep into the night."
    override val earnedMessage  = "Four hours of music! The moon herself has noticed. She rises now whenever Metro plays, bathing the forest in silver light."
    override val isBodyAttached  = false

    // Moon draws at (canvasW*MOON_X, canvasH*MOON_Y). Body-space conversion (subtract cx, baseY):
    //   Y: 0.18*h - 0.97*h = -0.79*h → -0.79*17 = -13.4u  (independent of aspect ratio)
    //   X: 0.82*w - 0.50*w = 0.32*w → 0.32*(w/h)*17 ≈ 2.7u on a typical portrait phone
    // Keep these three in step with MOON_X/MOON_Y (draw, hit, preview): when they disagreed
    // (an old hitCenter of -13.0u against a moon at -14.8u), taps never landed.
    override fun hitCenter(u: Float) = Offset(2.7f * u, -13.4f * u)

    /** Where the moon is drawn, in canvas px: the single source for draw, hit and preview. */
    private fun moonCenter(w: Float, h: Float, u: Float, dp: Float): Offset =
        Offset(w * MOON_X, maxOf(h * MOON_Y, MOON_TOP_CLEAR_DP * dp + 0.90f * u))

    // Exact on every canvas: the drawn centre less the body origin (cx, baseY).
    override fun hitCenterOnCanvas(canvasW: Float, canvasH: Float, u: Float, dp: Float): Offset {
        val c = moonCenter(canvasW, canvasH, u, dp)
        return Offset(c.x - canvasW / 2f, c.y - canvasH * 0.97f)
    }

    override fun previewCenterOnCanvas(canvasW: Float, canvasH: Float, u: Float, baseY: Float, dp: Float) =
        moonCenter(canvasW, canvasH, u, dp)
    override fun hitRadius(u: Float) = u * 1.3f

    // mx = canvasW*0.82, my = canvasH*0.10 — zooms into just the crescent
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * MOON_X, canvasH * MOON_Y)
    override fun previewRadius(u: Float) = u * 1.2f

    // ── Palette ───────────────────────────────────────────────────────────────
    private val moonGold   = Color(0xFFFFDB4D)
    private val moonYellow = Color(0xFFFFF0A0)
    private val glowColor  = Color(0x33FFF0A0)
    // Star tones — warm = giant/nearby star, cool = blue-white main sequence, faint = distant
    private val starWarm   = Color(0xFFFFF0C0)   // warm pale gold
    private val starCool   = Color(0xFFEEF4FF)   // blue-white
    private val starFaint  = Color(0xFFBBCCFF)   // distant silvery blue

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t    = (System.nanoTime() / 1_000_000_000.0).toFloat()
        val mc   = moonCenter(size.width, size.height, u, density)
        val mx   = mc.x
        val my   = mc.y
        val moonR = 0.90f * u

        // Soft ambient glow halo, centred a little toward the lit limb now that the night
        // side is open sky.
        val glowC = Offset(mx - 0.25f * u, my)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = glowC,
                radius = u * 2.2f
            ),
            radius = u * 2.2f,
            center = glowC
        )

        // The crescent alone: the moon disc less the night-side disc, so the sky shows through.
        val shadowR = u * 0.96f
        val shadowC = Offset(mx + 0.55f * u, my - 0.10f * u)
        val crescent = Path().apply {
            op(
                Path().apply { addOval(Rect(mx - moonR, my - moonR, mx + moonR, my + moonR)) },
                Path().apply { addOval(Rect(shadowC.x - shadowR, shadowC.y - shadowR, shadowC.x + shadowR, shadowC.y + shadowR)) },
                PathOperation.Difference,
            )
        }
        drawPath(
            crescent,
            brush = Brush.radialGradient(
                colors = listOf(moonYellow, moonGold),
                center = Offset(mx - 0.20f * u, my - 0.20f * u),
                radius = 0.60f * u,
            ),
        )

        // Stars — screen-relative, independent twinkle phases, varied tone
        // The field moves with the moon (each y is its old height plus the moon's drop).
        val dy = my - size.height * 0.10f
        drawStarSpark(u, size.width * 0.60f, size.height * 0.14f + dy, 0.11f, t, 0.00f, starCool)
        drawStarSpark(u, size.width * 0.72f, size.height * 0.05f + dy, 0.09f, t, 1.20f, starFaint)
        drawStarSpark(u, size.width * 0.90f, size.height * 0.12f + dy, 0.13f, t, 2.40f, starWarm)
        drawStarSpark(u, size.width * 0.55f, size.height * 0.08f + dy, 0.08f, t, 3.60f, starFaint)
        drawStarSpark(u, size.width * 0.88f, size.height * 0.04f + dy, 0.10f, t, 4.80f, starCool)
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
