package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * Cuban-link gold chain draped across Metro's chest with a gold medallion pendant.
 *
 * Coordinate anchor (body-coord origin at cx/baseY after GnomeCanvas translate):
 *   Bow tie centre: (0f, -7.82u)     — chain anchors just below this, inside the lapels
 *   Jacket body:    top=-7.6u  belt=-4.05u  — droop sits at mid-chest ~-5.8u
 *
 * Bezier drape: anchors at (-0.75u, -7.40u) and (0.75u, -7.40u).
 * Control point chosen so the curve bottom lands at -5.80u:
 *   ctrlY = 2 * droopY - anchorY = 2*(-5.80) - (-7.40) = -4.20u
 *
 * Pendant hangs BELOW the chain bottom: py = droopY + 0.32u = -5.48u
 *
 * Pendant design: a clean gold coin medallion — round disc with bevelled rim,
 * radial gradient, engraved quarter-note head + stem, and a specular highlight.
 * Kept intentionally simple so it reads clearly at ~10–14px radius.
 */
object GoldChain : MetroItem {

    override val id              = "gold_chain"
    override val displayName     = "Cuban-Link Gold Chain"
    override val description     = "Heavy 24-karat Cuban links with an engraved gold medallion. Unmistakably metro."
    override val unlockCondition = "1 hour of metronome use"
    override val earnedMessage   = "Well done for a full hour of keeping the beat! Metro's Cuban-link gold chain is your reward — heavy 24-karat links and an engraved medallion. Unmistakably metro."
    override val isBodyAttached  = true

    override fun hitCenter(u: Float) = Offset(0f, -5.48f * u)
    override fun hitRadius(u: Float) = u * 0.35f

    private val goldLight  = Color(0xFFFFE566)
    private val goldMid    = Color(0xFFD4A800)
    private val goldDark   = Color(0xFF8B6800)
    private val goldDeep   = Color(0xFF5A4000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val anchorLX = -0.75f * u
        val anchorRX =  0.75f * u
        val anchorY  = -7.40f * u   // below bow tie, inside lapel V
        val droopY   = -5.80f * u   // mid-chest

        // ctrlY chosen so quadratic Bezier bottom == droopY when anchors are symmetric
        val ctrlY = 2f * droopY - anchorY   // = -4.20u

        drawChain(u, anchorLX, anchorY, anchorRX, anchorY, ctrlX = 0f, ctrlY = ctrlY)
        drawMedallion(u, droopY)
    }

    private fun DrawScope.drawChain(
        u: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        ctrlX: Float, ctrlY: Float
    ) {
        val steps = 20
        val points = (0..steps).map { i ->
            val t  = i.toFloat() / steps
            val mt = 1f - t
            Offset(
                mt * mt * x1 + 2f * mt * t * ctrlX + t * t * x2,
                mt * mt * y1 + 2f * mt * t * ctrlY + t * t * y2
            )
        }

        // Shadow pass
        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color(0x55000000),
                start = Offset(points[i].x + u * 0.025f, points[i].y + u * 0.025f),
                end   = Offset(points[i + 1].x + u * 0.025f, points[i + 1].y + u * 0.025f),
                strokeWidth = u * 0.12f, cap = StrokeCap.Round
            )
        }

        // Cuban links — alternating horizontal / vertical ovals
        for ((i, p) in points.withIndex()) {
            val hz = i % 2 == 0   // horizontal link
            val lw = if (hz) u * 0.115f else u * 0.068f
            val lh = if (hz) u * 0.068f else u * 0.115f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(goldLight, goldMid, goldDark),
                    center = Offset(p.x - lw * 0.18f, p.y - lh * 0.18f),
                    radius = lw * 1.1f
                ),
                topLeft = Offset(p.x - lw / 2f, p.y - lh / 2f),
                size = Size(lw, lh)
            )
            drawOval(
                color = goldDark.copy(alpha = 0.55f),
                topLeft = Offset(p.x - lw / 2f, p.y - lh / 2f),
                size = Size(lw, lh),
                style = Stroke(width = u * 0.016f)
            )
            // Specular
            drawOval(
                color = goldLight.copy(alpha = 0.50f),
                topLeft = Offset(p.x - lw * 0.30f, p.y - lh * 0.38f),
                size = Size(lw * 0.36f, lh * 0.26f)
            )
        }
    }

    private fun DrawScope.drawMedallion(u: Float, droopY: Float) {
        val mx = 0f
        val my = droopY + 0.32f * u   // hangs BELOW chain bottom

        val discR  = 0.22f * u
        val bailH  = 0.12f * u
        val bailW  = u * 0.065f

        // ── Bail (small loop connecting chain to medallion) ───────────────────
        drawOval(
            color = goldDark,
            topLeft = Offset(mx - bailW / 2f, my - discR - bailH),
            size = Size(bailW, bailH),
            style = Stroke(width = u * 0.030f)
        )

        // ── Disc shadow ───────────────────────────────────────────────────────
        drawCircle(
            color = Color(0x44000000),
            radius = discR + u * 0.018f,
            center = Offset(mx + u * 0.018f, my + u * 0.018f)
        )

        // ── Disc body ─────────────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldLight, goldMid, goldDark, goldDeep),
                center = Offset(mx - discR * 0.25f, my - discR * 0.25f),
                radius = discR * 1.6f
            ),
            radius = discR,
            center = Offset(mx, my)
        )

        // ── Bevelled rim ──────────────────────────────────────────────────────
        drawCircle(
            color = goldDeep.copy(alpha = 0.70f),
            radius = discR,
            center = Offset(mx, my),
            style = Stroke(width = u * 0.028f)
        )
        drawCircle(
            color = goldLight.copy(alpha = 0.45f),
            radius = discR - u * 0.020f,
            center = Offset(mx, my),
            style = Stroke(width = u * 0.012f)
        )

        // ── Engraved quarter-note ─────────────────────────────────────────────
        // Note head: small tilted oval, dark engraving
        val nhCx = mx - u * 0.030f
        val nhCy = my + u * 0.045f
        val nhW  = discR * 0.52f
        val nhH  = discR * 0.38f
        drawOval(
            color = goldDeep.copy(alpha = 0.85f),
            topLeft = Offset(nhCx - nhW / 2f, nhCy - nhH / 2f),
            size = Size(nhW, nhH)
        )
        // Stem going up-right from note head
        val stemBX = nhCx + nhW * 0.42f
        val stemBY = nhCy - nhH * 0.10f
        val stemTX = stemBX + u * 0.012f
        val stemTY = stemBY - discR * 0.80f
        drawLine(
            color = goldDeep.copy(alpha = 0.85f),
            start = Offset(stemBX, stemBY),
            end   = Offset(stemTX, stemTY),
            strokeWidth = u * 0.038f,
            cap = StrokeCap.Round
        )

        // ── Specular highlight (upper-left of disc) ───────────────────────────
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = discR * 0.30f,
            center = Offset(mx - discR * 0.38f, my - discR * 0.38f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = discR * 0.55f,
            center = Offset(mx - discR * 0.28f, my - discR * 0.28f)
        )
    }
}
