package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A polished steel tuning fork held in Metro's right hand.
 *
 * All coordinates are body-space (origin = feet centre after withTransform translate).
 * Right-hand anchor from drawRightArm: handX = 2.0u, handY = -4.5u.
 * Fork U-base (fy) is chosen so the handle bottom lands at hand level:
 *   fy + handleH = handY  →  fy = -4.5u - 0.80u = -5.30u
 * Prong tips reach up to fy - 0.90u = -6.20u.
 * Visual centre ≈ midpoint of full extent: (fy - 0.90u + fy + 0.80u) / 2 = fy - 0.05u = -5.35u
 *
 * hitCenter  — visual centre in body coords; drives tap detection + popup zoom.
 * hitRadius  — half-extent that covers the full prong-to-handle span (0.90u above, 0.80u below).
 */
object TuningFork : MetroItem {

    override val id             = "tuning_fork"
    override val displayName    = "Tuning Fork"
    override val description    = "A precision A440 fork. Metro listens to the universe."
    override val unlockCondition = "Help improve the tuner"
    override val earnedMessage  = "Your feedback shapes how the tuner hears the world. Metro picked up a precision A440 fork to celebrate and to remind himself to stay sharp."
    override val isBodyAttached = true
    override val isHeadAttached = false

    // Visual centre: halfway between prong tips (-6.20u) and handle bottom (-4.50u) → -5.35u
    override fun hitCenter(u: Float) = Offset(2.0f * u, -5.35f * u)
    // Covers full vertical extent from centre: max(0.90u + 0.05u, 0.80u + 0.05u) = 0.95u
    override fun hitRadius(u: Float) = u * 0.95f

    private val steel     = Color(0xFFCDD5DC)
    private val steelDark = Color(0xFF8A9BAB)
    private val highlight = Color(0xFFF2F6F9)
    private val shadow    = Color(0x33000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Body-space coordinates — do NOT add cx or baseY; this draw function runs
        // inside withTransform({ translate(cx, baseY) }) in GnomeCanvas.
        val fx = 2.0f * u    // right-hand x
        val fy = -5.30f * u  // U-base: handle bottom lands at hand level (-4.50u)

        val halfGap = 0.15f * u   // half-gap between prong centre lines
        val prongW  = 0.08f * u   // prong stroke width
        val prongH  = 0.90f * u   // prong length above U-base
        val handleH = 0.80f * u   // handle length below U-base
        val handleW = 0.12f * u   // handle stroke width

        // ── Drop shadow ───────────────────────────────────────────────────────
        drawLine(
            color = shadow,
            start = Offset(fx + 0.05f * u, fy - prongH),
            end   = Offset(fx + 0.05f * u, fy + handleH),
            strokeWidth = handleW * 2.2f,
            cap = StrokeCap.Round,
        )

        // ── Left prong ────────────────────────────────────────────────────────
        drawLine(
            color = steel,
            start = Offset(fx - halfGap, fy - prongH),
            end   = Offset(fx - halfGap, fy),
            strokeWidth = prongW,
            cap = StrokeCap.Round,
        )

        // ── Right prong ───────────────────────────────────────────────────────
        drawLine(
            color = steel,
            start = Offset(fx + halfGap, fy - prongH),
            end   = Offset(fx + halfGap, fy),
            strokeWidth = prongW,
            cap = StrokeCap.Round,
        )

        // ── Polished edges on each prong (bright left, dark right) ────────────
        listOf(fx - halfGap, fx + halfGap).forEach { px ->
            drawLine(
                color = highlight.copy(alpha = 0.70f),
                start = Offset(px - prongW * 0.24f, fy - prongH + prongW * 0.6f),
                end   = Offset(px - prongW * 0.24f, fy - prongW * 0.3f),
                strokeWidth = prongW * 0.30f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = steelDark.copy(alpha = 0.50f),
                start = Offset(px + prongW * 0.26f, fy - prongH + prongW * 0.8f),
                end   = Offset(px + prongW * 0.26f, fy - prongW * 0.3f),
                strokeWidth = prongW * 0.20f,
                cap = StrokeCap.Round,
            )
        }

        // ── U-curve connecting the prong bases ────────────────────────────────
        drawArc(
            color = steel,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(fx - halfGap, fy - halfGap),
            size = Size(halfGap * 2f, halfGap * 2f),
            style = Stroke(width = prongW, cap = StrokeCap.Round),
        )

        // ── Handle ────────────────────────────────────────────────────────────
        drawLine(
            color = steel,
            start = Offset(fx, fy),
            end   = Offset(fx, fy + handleH),
            strokeWidth = handleW,
            cap = StrokeCap.Round,
        )

        // ── Specular highlight on handle left face ────────────────────────────
        drawLine(
            color = highlight.copy(alpha = 0.75f),
            start = Offset(fx - handleW * 0.22f, fy + 0.06f * u),
            end   = Offset(fx - handleW * 0.22f, fy + handleH * 0.78f),
            strokeWidth = handleW * 0.28f,
            cap = StrokeCap.Round,
        )

        // ── Dark edge on handle right face ────────────────────────────────────
        drawLine(
            color = steelDark.copy(alpha = 0.55f),
            start = Offset(fx + handleW * 0.22f, fy + 0.06f * u),
            end   = Offset(fx + handleW * 0.22f, fy + handleH * 0.78f),
            strokeWidth = handleW * 0.22f,
            cap = StrokeCap.Round,
        )

        // ── Glints catching the light along the polished steel ────────────────
        // Irregular heights down the prongs and handle (never on the tips) so they
        // read as reflections, not baubles. Fixed positions = no per-frame flicker.
        listOf(
            Triple(fx - halfGap, fy - prongH * 0.72f, prongW * 0.95f),
            Triple(fx - halfGap, fy - prongH * 0.34f, prongW * 0.66f),
            Triple(fx + halfGap, fy - prongH * 0.55f, prongW * 0.85f),
            Triple(fx + halfGap, fy - prongH * 0.18f, prongW * 0.60f),
            Triple(fx - handleW * 0.12f, fy + handleH * 0.34f, handleW * 0.50f),
        ).forEach { (gx, gy, r) ->
            drawSparkle(center = Offset(gx, gy), radius = r, color = highlight.copy(alpha = 0.9f))
        }
    }
}
