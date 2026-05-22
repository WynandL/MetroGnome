package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * Rendered as fine jewelry:
 *   - A polished gold ring with two light sources (upper-left specular + lower-right rim light)
 *   - A rounded ruby drop at the base with a tiny musical note hidden inside (easter egg)
 *   - A gold bail joining the stone to the hoop
 *   - Sparkle glints on the metal and the stone
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

    // Palette — ruby is item-unique, gold comes from ItemPalette
    private val gemRed       = Color(0xFFD11A1A)   // ruby body
    private val gemDeep      = Color(0xFF5C0000)   // deep edge shadow
    private val gemHighlight = Color(0xFFFF9DA0)   // bright crown
    private val noteGlow     = Color(0xFFFFE9A8)   // warm gold of the hidden note
    private val ringShade    = Color(0x40000000)

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

        val arcTopLeft = Offset(hoopCx - hoopR + wireW * 0.25f, hoopCy - hoopR + wireW * 0.25f)
        val arcSize    = Size((hoopR - wireW * 0.25f) * 2f, (hoopR - wireW * 0.25f) * 2f)

        // ── Contact shadow where the hoop meets the lobe ──────────────────────
        drawCircle(
            color = ringShade,
            radius = hoopR + wireW * 0.3f,
            center = Offset(hoopCx + wireW * 0.2f, hoopCy + wireW * 0.35f),
            style = Stroke(width = wireW * 0.9f)
        )

        // ── Main hoop — polished gold, lit from the upper-left ────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                center = Offset(hoopCx - hoopR * 0.35f, hoopCy - hoopR * 0.35f),
                radius = hoopR * 1.7f
            ),
            radius = hoopR,
            center = Offset(hoopCx, hoopCy),
            style = Stroke(width = wireW)
        )

        // ── Primary specular — bright arc on the upper-left ───────────────────
        drawArc(
            color = ItemPalette.goldLight.copy(alpha = 0.9f),
            startAngle = 200f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = wireW * 0.32f, cap = StrokeCap.Round)
        )

        // ── Secondary rim light — faint arc on the lower-right (second source) ─
        drawArc(
            color = ItemPalette.goldLight.copy(alpha = 0.40f),
            startAngle = 35f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = wireW * 0.22f, cap = StrokeCap.Round)
        )

        // ── Glint where the hoop catches the light ────────────────────────────
        drawSparkle(
            center = Offset(hoopCx - hoopR * 0.66f, hoopCy - hoopR * 0.66f),
            radius = wireW * 0.95f,
            color = Color.White.copy(alpha = 0.9f)
        )

        // ── Faceted ruby drop at the base of the hoop ─────────────────────────
        val gemCx = hoopCx
        val gemCy = hoopCy + hoopR     // bottom of the hoop
        val gemR  = 0.10f * u

        // Gold bail joining the stone to the hoop
        drawCircle(
            color = ItemPalette.goldMid,
            radius = wireW * 0.55f,
            center = Offset(gemCx, gemCy - gemR * 0.85f)
        )

        // Stone body — radial gradient gives a clean, rounded ruby
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(gemHighlight, gemRed, gemDeep),
                center = Offset(gemCx - gemR * 0.30f, gemCy - gemR * 0.35f),
                radius = gemR * 1.5f
            ),
            radius = gemR,
            center = Offset(gemCx, gemCy)
        )

        // Easter egg: a tiny musical note suspended inside the stone
        // Nudged left so the stem+flag don't bias it off-centre; sized to clear the bezel
        drawMusicNote(
            center = Offset(gemCx - gemR * 0.06f, gemCy),
            height = gemR * 1.05f,
            color = noteGlow.copy(alpha = 0.92f)
        )

        // Gold bezel rim framing the stone
        drawCircle(
            color = ItemPalette.goldDark,
            radius = gemR,
            center = Offset(gemCx, gemCy),
            style = Stroke(width = wireW * 0.4f)
        )

        // One clean specular dot + one sparkle for shine
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = gemR * 0.16f,
            center = Offset(gemCx - gemR * 0.44f, gemCy - gemR * 0.48f)
        )
        drawSparkle(
            center = Offset(gemCx + gemR * 0.42f, gemCy - gemR * 0.42f),
            radius = gemR * 0.42f,
            color = Color.White.copy(alpha = 0.85f)
        )
    }

    /** A tiny eighth note — the easter egg hidden inside the ruby. */
    private fun DrawScope.drawMusicNote(center: Offset, height: Float, color: Color) {
        val headRx  = height * 0.30f
        val headRy  = height * 0.23f
        val headC   = Offset(center.x - height * 0.10f, center.y + height * 0.30f)
        val stemX   = headC.x + headRx * 0.92f
        val stemTop = headC.y - height * 0.85f

        // Stem
        drawLine(
            color = color,
            start = Offset(stemX, headC.y),
            end = Offset(stemX, stemTop),
            strokeWidth = height * 0.11f,
            cap = StrokeCap.Round
        )
        // Flag off the top of the stem
        drawPath(
            Path().apply {
                moveTo(stemX, stemTop)
                cubicTo(
                    stemX + height * 0.34f, stemTop + height * 0.12f,
                    stemX + height * 0.30f, stemTop + height * 0.40f,
                    stemX + height * 0.06f, stemTop + height * 0.52f
                )
                cubicTo(
                    stemX + height * 0.26f, stemTop + height * 0.34f,
                    stemX + height * 0.22f, stemTop + height * 0.16f,
                    stemX, stemTop + height * 0.10f
                )
                close()
            },
            color = color
        )
        // Notehead — tilted oval, drawn last so it sits over the stem base
        withTransform({ rotate(-18f, pivot = headC) }) {
            drawOval(
                color = color,
                topLeft = Offset(headC.x - headRx, headC.y - headRy),
                size = Size(headRx * 2f, headRy * 2f)
            )
        }
    }
}
