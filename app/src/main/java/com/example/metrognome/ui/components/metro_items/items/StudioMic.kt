package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.ItemPalette
import kotlin.math.sqrt

/**
 * A little gold handheld studio microphone, clipped to Metro's right lapel. The reward
 * for running a microphone "Groove Check": once a phone has proven it can hear the
 * player's timing, Metro pins on the mic that made it possible.
 *
 * Placement mirrors [LapelPin] on the opposite lapel, so the two balance when both are
 * worn. Right-lapel quad (mirror of the left lapel from drawBody):
 *   top-inner (0.15u, -7.55u)  lower-inner (0.65u, -6.85u)
 *   lower-outer (1.35u, -7.05u) top-outer (1.45u, -7.60u)
 * The mic sits toward the outer end at (1.05u, -7.30u) — outboard of the gold chain's
 * right anchor (~0.75u) so the two never overlap, matching how the pin clears it on the left.
 *
 * The geometry is centred on the (CX, CY) anchor, so hitCenter (and therefore the zoomed
 * dialog/celebration preview) frames the whole mic without any extra offset maths.
 *
 * Silhouette: a round wire-mesh grille on top, a tapered gold handle below, a collar
 * band where they meet, and a premium sparkle glint to match the wearables tier.
 */
object StudioMic : MetroItem {

    override val id             = "studio_mic"
    override val displayName    = "Studio Microphone"
    override val description    = "A pocket-sized gold studio mic, earned the day this phone proved it could hear you keep time."
    override val earnedMessage  = "Sound check passed! Metro pinned on a tiny gold studio mic to mark the day this phone proved it could really hear your groove. Wear it proud, the stage is yours."
    override val isBodyAttached = true

    // Mic centre in body units — outer end of the right lapel, mirroring the lapel pin.
    private const val CX = 1.05f
    private const val CY = -7.30f

    override fun hitCenter(u: Float) = Offset(CX * u, CY * u)
    override fun hitRadius(u: Float) = u * 0.42f

    // Metallic wire-mesh grille — silver, unique to this mic; gold trio is shared via ItemPalette.
    private val meshDark  = Color(0xFF1B1B22)   // gaps between the wires
    private val meshWire  = Color(0xFFB8B8C4)   // bright metal wires
    private val meshBase  = Color(0xFF55555F)   // mid grey base under the wires
    private val micShadow = Color(0x55000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val px = CX * u
        val py = CY * u

        // ── Geometry (centred on the anchor); grille noticeably wider than the handle ──
        val grilleR = 0.20f * u
        val grilleC = Offset(px, py - 0.17f * u)

        val handleTopW = 0.135f * u                 // clearly narrower than the grille diameter
        val handleBotW = 0.11f * u                  // slight downward taper
        val handleTopY = grilleC.y + grilleR * 0.55f
        val handleBotY = py + 0.36f * u

        // ── Soft contact shadow (offset duplicate of the grille) ──────────────
        drawCircle(micShadow.copy(alpha = 0.22f), grilleR, grilleC + Offset(u * 0.05f, u * 0.06f))

        // ── Handle: tapered gold body ─────────────────────────────────────────
        val handle = Path().apply {
            moveTo(px - handleTopW, handleTopY)
            lineTo(px + handleTopW, handleTopY)
            lineTo(px + handleBotW, handleBotY)
            lineTo(px - handleBotW, handleBotY)
            close()
        }
        drawPath(
            handle,
            brush = Brush.horizontalGradient(
                colors = listOf(ItemPalette.goldDark, ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                startX = px - handleTopW,
                endX   = px + handleTopW,
            ),
        )
        drawPath(handle, color = ItemPalette.goldDark.copy(alpha = 0.55f),
            style = Stroke(width = u * 0.018f, join = StrokeJoin.Round))
        // A thin darker grip ring near the base of the handle
        drawLine(
            color = ItemPalette.goldDark.copy(alpha = 0.7f),
            start = Offset(px - handleBotW * 0.95f, handleBotY - u * 0.07f),
            end   = Offset(px + handleBotW * 0.95f, handleBotY - u * 0.07f),
            strokeWidth = u * 0.02f, cap = StrokeCap.Round,
        )

        // ── Grille: silver mesh ball with a wire grid (the defining mic cue) ──
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(meshBase, meshDark),
                center = grilleC - Offset(grilleR * 0.3f, grilleR * 0.35f),
                radius = grilleR * 1.5f,
            ),
            radius = grilleR,
            center = grilleC,
        )
        // Cross-hatch wires clipped to the ball → unmistakable mesh.
        clipPath(Path().apply { addOval(circleRect(grilleC, grilleR * 0.97f)) }) {
            val lat = 6
            for (i in 1 until lat) {
                val dy = grilleR * (-1f + 2f * i / lat)
                val half = sqrt((grilleR * grilleR - dy * dy).coerceAtLeast(0f))
                drawLine(meshWire.copy(alpha = 0.55f),
                    Offset(grilleC.x - half, grilleC.y + dy),
                    Offset(grilleC.x + half, grilleC.y + dy),
                    strokeWidth = u * 0.016f)
            }
            val lon = 6
            for (i in 1 until lon) {
                val dx = grilleR * (-1f + 2f * i / lon)
                val half = sqrt((grilleR * grilleR - dx * dx).coerceAtLeast(0f))
                drawLine(meshWire.copy(alpha = 0.40f),
                    Offset(grilleC.x + dx, grilleC.y - half),
                    Offset(grilleC.x + dx, grilleC.y + half),
                    strokeWidth = u * 0.013f)
            }
        }
        // Bright gold rim around the grille.
        drawCircle(ItemPalette.goldMid, grilleR, grilleC, style = Stroke(width = u * 0.05f))
        drawCircle(ItemPalette.goldDark.copy(alpha = 0.6f), grilleR, grilleC, style = Stroke(width = u * 0.018f))

        // ── Grille seam: a thin gold ring across the middle of the ball (SM58-style) ──
        val bandY    = grilleC.y + grilleR * 0.06f                 // roughly the ball's middle
        val bandHalf = sqrt((grilleR * grilleR - (bandY - grilleC.y) * (bandY - grilleC.y))
            .coerceAtLeast(0f)) * 0.9f                             // span the ball, just inside the rim
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(ItemPalette.goldDark, ItemPalette.goldLight, ItemPalette.goldDark),
                startX = px - bandHalf,
                endX   = px + bandHalf,
            ),
            start = Offset(px - bandHalf, bandY),
            end   = Offset(px + bandHalf, bandY),
            strokeWidth = u * 0.035f, cap = StrokeCap.Round,
        )

        // ── Soft specular on the grille (kept subtle so it reads as metal, not a gem) ──
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = grilleR * 0.22f,
            center = grilleC - Offset(grilleR * 0.38f, grilleR * 0.42f),
        )
    }

    private fun circleRect(c: Offset, r: Float) =
        androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)
}
