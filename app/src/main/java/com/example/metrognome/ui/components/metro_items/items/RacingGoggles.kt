package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.metrognome.ui.components.HAT_BASE_Y
import com.example.metrognome.ui.components.HAT_TILT_DEG
import com.example.metrognome.ui.components.hatConePath
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.ItemPalette

/**
 * Brass-rimmed racing goggles pushed up onto Metro's hat: the Speed Trainer's item (added
 * 2026-11-19), earned by finishing a Speed Trainer session on five different days, the same
 * days-not-sessions rule as the Music Stand.
 *
 * Head-attached, so it bobs with the head like the plume, and drawn in the hat's own 4° rake
 * (GnomeCanvas.HAT_TILT_DEG about (0, hatBaseY = -11.14u)) so the strap follows the band.
 * The strap wraps the cone just above the brim (brim top -11.59u), at the height the plume
 * tucks into (-12.10u). The lenses sit a little left of centre, pushed up casually, so the
 * right lens ends clear of the plume's quill at x 0.95u.
 *
 * Lit from the upper left like Metro: the rims are the gold trio rolling from light to dark,
 * the glass is tinted with the night sky reflected in its upper half and one curved glint,
 * and the strap is leather with faint stitching, darker where it turns away round the cone.
 */
object RacingGoggles : MetroItem {

    override val id             = "racing_goggles"
    override val displayName    = "Racing Goggles"
    override val description    = "Brass-rimmed racing goggles, pushed up on Metro's hat for the next fast tempo."
    override val earnedMessage  = "Five days on the Speed Trainer! Metro pushed a pair of racing goggles up onto his hat. Faster tempos ahead."
    override val isBodyAttached = true
    override val isHeadAttached = true

    private const val STRAP_Y    = -12.06f   // strap centre line at the cone's edges
    private const val SAG        = 0.16f     // how far the strap dips at the front of the cone
    private const val LENS_R     = 0.40f
    private const val LX         = -0.71f    // left lens centre x
    private const val RX         = 0.31f     // right lens centre x
    private const val LY         = -12.16f   // lens centre y

    override fun hitCenter(u: Float) = Offset(((LX + RX) / 2f) * u, LY * u)
    override fun hitRadius(u: Float) = u * 0.95f

    private val leather      = Color(0xFF6B4428)
    private val leatherLight = Color(0xFF8E5E3A)
    private val leatherDark  = Color(0xFF3F2716)
    private val stitch       = Color(0xFFD9B98A)
    private val cup          = Color(0xFF2A1A10)
    private val glassTop     = Color(0xFF3B4C78)
    private val glassBottom  = Color(0xFF141A30)
    private val glint        = Color(0xFFEAF2FF)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        rotate(HAT_TILT_DEG, pivot = Offset(0f, HAT_BASE_Y * u)) {
            // Everything that lies ON the felt (the strap, and the shadows of strap and
            // lenses) is clipped to the cone's own outline, so the strap ends exactly at the
            // hat's edge and reads as wrapping round it. A strap cut to a guessed width
            // stopped short of the edge with a hard square end and read as a sticker.
            clipPath(hatConePath(u)) {
                drawFeltShadows(u)
                drawStrap(u)
            }
            drawLens(Offset(LX * u, LY * u), u)
            drawLens(Offset(RX * u, LY * u), u)
            drawBridge(u)
        }
    }

    /** Soft shadows the strap and the lenses cast on the felt, down and right of each. */
    private fun DrawScope.drawFeltShadows(u: Float) {
        drawPath(strapPath(u, dy = 0.08f * u), Color.Black.copy(alpha = 0.22f))
        for (x in floatArrayOf(LX, RX)) {
            val c = Offset(x * u + 0.06f * u, LY * u + 0.10f * u)
            drawCircle(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), c, LENS_R * u * 1.35f),
                LENS_R * u * 1.35f, c,
            )
        }
    }

    /**
     * The band round the cone, dipping at the front. It runs well past the cone on both
     * sides on purpose: the caller clips it to the cone, which gives the true ends.
     */
    private fun strapPath(u: Float, dy: Float = 0f): Path {
        val left = -2.2f * u
        val right = 2.2f * u
        val half = 0.13f * u
        val y = STRAP_Y * u + dy
        val sag = SAG * u
        return Path().apply {
            moveTo(left, y - half)
            quadraticTo(0f, y - half + sag * 2f, right, y - half)
            lineTo(right, y + half)
            quadraticTo(0f, y + half + sag * 2f, left, y + half)
            close()
        }
    }

    /** The strap: leather, darkening towards both edges of the cone where it turns away. */
    private fun DrawScope.drawStrap(u: Float) {
        // The cone is about 1.5u wide each side at the strap's height; the gradient spans
        // that, so the darkest leather lands where the strap turns out of sight.
        val left = -1.52f * u
        val right = 1.52f * u
        val y = STRAP_Y * u
        val sag = SAG * u
        drawPath(
            strapPath(u),
            Brush.horizontalGradient(
                0f to leatherDark, 0.18f to leather, 0.38f to leatherLight, 0.75f to leather, 1f to leatherDark,
                startX = left, endX = right,
            ),
        )
        // Stitching along the middle of the band, faint, so the strap reads as leather.
        val seam = Path().apply {
            moveTo(-2.2f * u, y + 0.01f * u)
            quadraticTo(0f, y + 0.01f * u + sag * 2f, 2.2f * u, y + 0.01f * u)
        }
        drawPath(
            seam, stitch.copy(alpha = 0.45f),
            style = Stroke(width = 0.025f * u, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.09f * u, 0.07f * u))),
        )
    }

    private fun DrawScope.drawLens(c: Offset, u: Float) {
        val r = LENS_R * u
        // Leather cup behind the rim, slightly larger, offset down-right (the shadow side).
        drawCircle(cup, r * 1.18f, Offset(c.x + 0.03f * u, c.y + 0.04f * u))
        // Glass: tinted, the sky reflected in the top half, then one curved glint.
        val lens = Path().apply { addOval(Rect(c, r * 0.80f)) }
        clipPath(lens) {
            drawRect(
                Brush.verticalGradient(listOf(glassTop, glassBottom), startY = c.y - r, endY = c.y + r),
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2f, r * 2f),
            )
            drawArc(
                glint.copy(alpha = 0.75f), startAngle = 200f, sweepAngle = 55f, useCenter = false,
                topLeft = Offset(c.x - r * 0.58f, c.y - r * 0.58f), size = Size(r * 1.16f, r * 1.16f),
                style = Stroke(width = 0.05f * u, cap = StrokeCap.Round),
            )
            drawCircle(glint.copy(alpha = 0.5f), 0.035f * u, Offset(c.x + r * 0.30f, c.y + r * 0.28f))
        }
        // Brass rim, lit upper left.
        drawCircle(
            Brush.linearGradient(
                listOf(ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
            ),
            r * 0.90f, c, style = Stroke(width = 0.13f * u),
        )
        drawArc(
            Color.White.copy(alpha = 0.45f), startAngle = 195f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(c.x - r * 0.96f, c.y - r * 0.96f), size = Size(r * 1.92f, r * 1.92f),
            style = Stroke(width = 0.03f * u, cap = StrokeCap.Round),
        )
    }

    /** The padded bridge joining the two lenses. */
    private fun DrawScope.drawBridge(u: Float) {
        val y = LY * u
        val x0 = (LX + LENS_R * 1.0f) * u
        val x1 = (RX - LENS_R * 1.0f) * u
        drawLine(leatherDark, Offset(x0, y + 0.02f * u), Offset(x1, y + 0.02f * u), 0.14f * u, StrokeCap.Round)
        drawLine(leatherLight, Offset(x0, y - 0.02f * u), Offset(x1, y - 0.02f * u), 0.06f * u, StrokeCap.Round)
    }
}
