package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.ItemPalette

/**
 * A small, detailed gold hoop earring on Metro's left ear.
 *
 * Coordinate anchor derived from drawEars geometry (ecy = cy + 0.1f·u = -9.9f·u):
 *   earX = -1.95f·u  — x of the lower-bezier CP2 (side * 1.95f·u, lobe region)
 *   earY = ecy + 0.13f·u = -9.77f·u  — attachment just above the lobe proper
 *   hoopCy = earY + hoopR = -9.49f·u  — hoop centre sits in the lobe zone
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
    override val unlockCondition = "5 minutes of metronome use"
    override val earnedMessage   = "Well done for keeping the beat going for 5 minutes! Metro rewarded himself with a little bling. A classic gold hoop, because even gnomes deserve nice things."
    override val isBodyAttached  = true
    override val isHeadAttached  = true

    override fun hitCenter(u: Float) = Offset(-1.95f * u, (-10.0f + 0.51f) * u)
    override fun hitRadius(u: Float) = u * 0.45f

    // Palette
    private val gemRed     = Color(0xFFCC2222)
    private val gemHighlight = Color(0xFFFF8888)
    private val ringShade  = Color(0x44000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Ear lobe anchor — derived from drawEars pointy-ear geometry
        // earX: lower-bezier CP2 x = side * 1.95f * u (side = -1 for left ear)
        // earY: ecy + 0.13f·u, where ecy = (cy + 0.1f) * u = -9.9f·u
        val earX = -1.95f * u
        val earY = (-10.0f + 0.23f) * u                 // = -9.77f·u

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
                colors = listOf(ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                center = Offset(hoopCx - hoopR * 0.3f, hoopCy - hoopR * 0.3f),
                radius = hoopR * 1.6f
            ),
            radius = hoopR,
            center = Offset(hoopCx, hoopCy),
            style = Stroke(width = wireW)
        )

        // ── Inner specular highlight (thin bright arc on upper-left) ──────────
        drawArc(
            color = ItemPalette.goldLight.copy(alpha = 0.85f),
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
            color = ItemPalette.goldDark,
            radius = gemR,
            center = Offset(gemCx, gemCy),
            style = Stroke(width = wireW * 0.4f)
        )
    }
}
