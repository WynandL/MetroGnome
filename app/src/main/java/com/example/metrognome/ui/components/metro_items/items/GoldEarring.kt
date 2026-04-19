package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A small, detailed gold hoop earring on Metro's left ear.
 *
 * Coordinate anchor (from GnomeCanvas, origin at cx/baseY after translate):
 *   Left ear centre: Offset(-(r*0.97f + 0.1f*u), -10.15f*u)
 *   where r = 1.85f*u
 *   → effectively Offset(-1.895f*u, -10.15f*u)
 *
 * The hoop hangs below the ear lobe as a proper earring would.
 * Rendered as:
 *   - A gold open ring (partial arc drawn as a thick circle stroke)
 *   - A tiny gemstone catch at the bottom (red cabochon with specular highlight)
 *   - A hairline inner shadow on the ring for depth
 */
object GoldEarring : MetroItem {

    override val id            = "gold_earring"
    override val displayName   = "Gold Hoop Earring"
    override val description   = "A classic 18-karat gold hoop. Metro's first step toward full bling."
    override val unlockCondition = "10 minutes of metronome use"
    override val earnedMessage   = "Well done for keeping the beat going for 10 minutes! Metro rewarded himself with a little bling — a classic gold hoop, because even gnomes deserve nice things."
    override val isBodyAttached  = true
    override val isHeadAttached  = true

    override fun hitCenter(u: Float) = Offset(-(1.85f * 0.97f + 0.05f) * u, (-10.0f + 0.35f + 0.28f) * u)
    override fun hitRadius(u: Float) = u * 0.45f

    // Palette
    private val goldLight  = Color(0xFFFFE566)
    private val goldMid    = Color(0xFFD4A800)
    private val goldDark   = Color(0xFF8B6800)
    private val gemRed     = Color(0xFFCC2222)
    private val gemHighlight = Color(0xFFFF8888)
    private val ringShade  = Color(0x44000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Ear lobe anchor — left ear, slightly below centre
        val earX = -(1.85f * u * 0.97f) - 0.05f * u   // just past the ear edge
        val earY = -10.0f * u + 0.35f * u               // earlobe height

        val hoopR      = 0.28f * u    // outer radius of the hoop
        val wireW      = 0.10f * u    // stroke width of the ring wire
        val hoopCx     = earX
        val hoopCy     = earY + hoopR  // hoop centre sits below lobe

        // ── Outer shadow ring (depth illusion) ────────────────────────────────
        drawCircle(
            color = ringShade,
            radius = hoopR + wireW * 0.3f,
            center = Offset(hoopCx + wireW * 0.2f, hoopCy + wireW * 0.3f),
            style = Stroke(width = wireW * 0.9f)
        )

        // ── Main hoop — radial gold gradient simulated with two concentric arcs ─
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldLight, goldMid, goldDark),
                center = Offset(hoopCx - hoopR * 0.3f, hoopCy - hoopR * 0.3f),
                radius = hoopR * 1.6f
            ),
            radius = hoopR,
            center = Offset(hoopCx, hoopCy),
            style = Stroke(width = wireW)
        )

        // ── Inner specular highlight (thin bright arc on upper-left) ──────────
        drawArc(
            color = goldLight.copy(alpha = 0.85f),
            startAngle = 200f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(hoopCx - hoopR + wireW * 0.25f, hoopCy - hoopR + wireW * 0.25f),
            size = androidx.compose.ui.geometry.Size(
                (hoopR - wireW * 0.25f) * 2f,
                (hoopR - wireW * 0.25f) * 2f
            ),
            style = Stroke(width = wireW * 0.3f, cap = StrokeCap.Round)
        )

        // ── Gemstone catch at the bottom of the hoop ─────────────────────────
        val gemCx = hoopCx
        val gemCy = hoopCy + hoopR     // bottom of the hoop
        val gemR  = 0.10f * u

        // Gem body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(gemHighlight, gemRed, Color(0xFF660000)),
                center = Offset(gemCx - gemR * 0.3f, gemCy - gemR * 0.3f),
                radius = gemR * 1.4f
            ),
            radius = gemR,
            center = Offset(gemCx, gemCy)
        )
        // Gem specular dot
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = gemR * 0.28f,
            center = Offset(gemCx - gemR * 0.25f, gemCy - gemR * 0.28f)
        )
        // Gem rim
        drawCircle(
            color = goldDark,
            radius = gemR,
            center = Offset(gemCx, gemCy),
            style = Stroke(width = wireW * 0.4f)
        )
    }
}
