package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A luxury wristwatch on Metro's left wrist, aligned to his geometry.
 *
 * The shirt-cuff line in GnomeCanvas is drawn as a HORIZONTAL line:
 *   start = Offset(handX - 0.28u, handY - 0.22u)
 *   end   = Offset(handX + 0.28u, handY - 0.22u)   ← purely horizontal, same Y
 *
 * The watch strap must therefore be horizontal (perpendicular to the
 * nearly-vertical forearm). The watch is drawn upright (straps above/below)
 * then rotated -90° so the straps extend left and right along the cuff.
 *
 * Watch centre: (handX, cuffY) = (-2.70u, -4.02u) — sits right on the cuff line.
 * Crown pokes upward after the rotation, consistent with a side-crown at the arm edge.
 */
object LuxuryWatch : MetroItem {

    override val id              = "luxury_watch"
    override val displayName     = "Luxury Wristwatch"
    override val description     = "An 18-karat gold case, sapphire crystal, Swiss movement. Very metro."
    override val unlockCondition = "30 minutes of metronome use"
    override val earnedMessage   = "Well done for 30 minutes of solid practice! Metro keeps time with a Swiss-movement 18-karat gold watch. He earned it — and so did you."
    override val isBodyAttached  = true

    override fun hitCenter(u: Float) = Offset(-2.70f * u, -4.02f * u)
    override fun hitRadius(u: Float) = u * 0.40f

    private val goldCase    = Color(0xFFD4A800)
    private val goldLight   = Color(0xFFFFE566)
    private val goldDark    = Color(0xFF8B6800)
    private val dialWhite   = Color(0xFFF5F0E8)
    private val dialShadow  = Color(0xFFCCC5B0)
    private val strapDark   = Color(0xFF2C1A0A)
    private val strapMid    = Color(0xFF4A2E14)
    private val strapStitch = Color(0xFFAA8855)
    private val lumeGreen   = Color(0xAAB8FF88)
    private val indexGold   = Color(0xFFFFE566)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Anchor exactly on the cuff line
        val wx = -2.70f * u   // = handX
        val wy = -4.02f * u   // = handY - 0.22u  (cuff Y)

        val caseR  = 0.26f * u
        val dialR  = 0.19f * u
        val strapW = 0.20f * u   // strap width (perpendicular to strap axis)
        val strapH = 0.24f * u   // strap length (how far it extends from case)

        // Rotate -90° around watch centre → straps become horizontal, aligning with cuff
        withTransform({ rotate(-90f, Offset(wx, wy)) }) {
            drawWatchBody(u, wx, wy, caseR, dialR, strapW, strapH)
        }
    }

    private fun DrawScope.drawWatchBody(
        u: Float, wx: Float, wy: Float,
        caseR: Float, dialR: Float, strapW: Float, strapH: Float
    ) {
        // ── Straps (above and below case — become left/right after rotation) ──
        drawStrapSegment(u, wx, wy - caseR - strapH, strapW, strapH)
        drawStrapSegment(u, wx, wy + caseR,           strapW, strapH)

        // ── Drop shadow ───────────────────────────────────────────────────────
        drawCircle(
            color = Color(0x55000000),
            radius = caseR + u * 0.022f,
            center = Offset(wx + u * 0.022f, wy + u * 0.022f)
        )

        // ── Gold case bezel ───────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldLight, goldCase, goldDark),
                center = Offset(wx - caseR * 0.3f, wy - caseR * 0.3f),
                radius = caseR * 1.8f
            ),
            radius = caseR,
            center = Offset(wx, wy)
        )

        // ── Dial face ─────────────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(dialWhite, dialShadow),
                center = Offset(wx, wy),
                radius = dialR * 1.1f
            ),
            radius = dialR,
            center = Offset(wx, wy)
        )

        // ── Minute track ring ─────────────────────────────────────────────────
        drawCircle(
            color = goldCase.copy(alpha = 0.45f),
            radius = dialR * 0.91f,
            center = Offset(wx, wy),
            style = Stroke(width = u * 0.011f)
        )

        // ── 12 hour indices ───────────────────────────────────────────────────
        for (i in 0 until 12) {
            val a    = Math.toRadians((i * 30.0) - 90.0)
            val dist = dialR * 0.77f
            val len  = if (i % 3 == 0) dialR * 0.15f else dialR * 0.08f
            val w    = if (i % 3 == 0) u * 0.028f    else u * 0.016f
            val icx  = wx + (cos(a) * dist).toFloat()
            val icy  = wy + (sin(a) * dist).toFloat()
            val ddx  = (cos(a) * len / 2f).toFloat()
            val ddy  = (sin(a) * len / 2f).toFloat()
            drawLine(indexGold, Offset(icx - ddx, icy - ddy), Offset(icx + ddx, icy + ddy),
                strokeWidth = w, cap = StrokeCap.Round)
        }

        // ── Hour hand (~10 o'clock) ───────────────────────────────────────────
        drawHand(u, wx, wy, Math.toRadians(-60.0), dialR * 0.50f, u * 0.052f)
        // ── Minute hand (~2 o'clock) ──────────────────────────────────────────
        drawHand(u, wx, wy, Math.toRadians(60.0),  dialR * 0.70f, u * 0.036f)

        // ── Centre pivot ──────────────────────────────────────────────────────
        drawCircle(goldDark,  radius = u * 0.026f, center = Offset(wx, wy))
        drawCircle(goldLight, radius = u * 0.012f, center = Offset(wx, wy))

        // ── Crown (right of case — becomes top after -90° rotation) ──────────
        val crownW = u * 0.056f
        val crownH = u * 0.095f
        drawRoundRect(
            color = goldCase,
            topLeft = Offset(wx + caseR, wy - crownH / 2f),
            size = Size(crownW, crownH),
            cornerRadius = CornerRadius(crownW * 0.4f)
        )
        drawRoundRect(
            color = goldLight.copy(alpha = 0.55f),
            topLeft = Offset(wx + caseR + crownW * 0.15f, wy - crownH * 0.35f),
            size = Size(crownW * 0.3f, crownH * 0.70f),
            cornerRadius = CornerRadius(crownW * 0.15f)
        )
    }

    private fun DrawScope.drawStrapSegment(
        u: Float, wx: Float, topY: Float, sw: Float, sh: Float
    ) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(strapDark, strapMid, strapDark),
                startX = wx - sw / 2f, endX = wx + sw / 2f
            ),
            topLeft = Offset(wx - sw / 2f, topY),
            size = Size(sw, sh),
            cornerRadius = CornerRadius(sw * 0.15f)
        )
        val inset = sw * 0.13f
        val step  = sh / 5f
        for (d in 0..3) {
            val y1 = topY + step * d + step * 0.1f
            val y2 = y1 + step * 0.5f
            for (side in listOf(-1f, 1f)) {
                val sx = wx + side * (sw / 2f - inset)
                drawLine(strapStitch, Offset(sx, y1), Offset(sx, y2),
                    strokeWidth = u * 0.014f, cap = StrokeCap.Round)
            }
        }
    }

    private fun DrawScope.drawHand(
        u: Float, cx: Float, cy: Float,
        angle: Double, length: Float, width: Float
    ) {
        val tipX  = (cx + cos(angle) * length).toFloat()
        val tipY  = (cy + sin(angle) * length).toFloat()
        val tailX = (cx - cos(angle) * length * 0.18f).toFloat()
        val tailY = (cy - sin(angle) * length * 0.18f).toFloat()
        drawLine(goldCase, Offset(tailX, tailY), Offset(tipX, tipY),
            strokeWidth = width, cap = StrokeCap.Round)
        val lumeLen = length * 0.24f
        val lsX = (cx + cos(angle) * (length - lumeLen)).toFloat()
        val lsY = (cy + sin(angle) * (length - lumeLen)).toFloat()
        drawLine(lumeGreen, Offset(lsX, lsY), Offset(tipX, tipY),
            strokeWidth = width * 0.52f, cap = StrokeCap.Round)
    }
}
