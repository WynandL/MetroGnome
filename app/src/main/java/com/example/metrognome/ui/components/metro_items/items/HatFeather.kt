package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A jaunty plume tucked into the side of Metro's hat — reward for dedicated tuner use.
 *
 * Head-attached: drawn in head-group coordinates (after drawHat, so it sits in front),
 * and bobs with the head on every beat. It is NOT inside the hat's own 11° transform,
 * so all coordinates here are plain head-group space (same convention as GoldEarring).
 *
 * Geometry — a quadratic Bézier shaft from the tuck point on the right of the cone,
 * curving up and out past the hat's right edge:
 *   base (quill, tucked into the band): (0.95u, -12.10u)
 *   control (bows the plume outward):   (1.98u, -13.00u)
 *   tip (sweeps up-right past the cone): (2.00u, -14.55u)
 *
 * hitCenter is the curve midpoint so the unlock-popup zoom frames the whole feather.
 */
object HatFeather : MetroItem {

    override val id              = "hat_feather"
    override val displayName     = "Hat Plume"
    override val description     = "A fine feathered plume for Metro's cap, earned by listening closely with the tuner."
    override val earnedMessage   = "Fifteen minutes of careful listening! For his sharp ear, Metro tucked a bright plume into his cap. He wears it well."
    override val isBodyAttached  = true
    override val isHeadAttached  = true

    // Curve midpoint (visual centre) — drives tap detection + popup centring/zoom.
    override fun hitCenter(u: Float) = Offset(1.73f * u, -13.16f * u)
    override fun hitRadius(u: Float) = u * 1.5f

    private val quillColor = Color(0xFFF0E4B8)
    private val vaneDeep   = Color(0xFF1E3A6E)
    private val vaneMid    = Color(0xFF356FB6)
    private val vaneLight  = Color(0xFF6FB6E6)
    private val vaneTip    = Color(0xFFBFE6F7)
    private val edgeGlow   = Color(0xFFEAF7FF)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val base = Offset(0.95f * u, -12.10f * u)
        val ctrl = Offset(1.98f * u, -13.00f * u)
        val tip  = Offset(2.00f * u, -14.55f * u)
        drawFeather(base, ctrl, tip, maxW = 0.42f * u, u = u)
    }

    private fun DrawScope.drawFeather(p0: Offset, p1: Offset, p2: Offset, maxW: Float, u: Float) {
        val n = 24

        fun pt(t: Float): Offset {
            val mt = 1f - t
            return Offset(
                mt * mt * p0.x + 2f * mt * t * p1.x + t * t * p2.x,
                mt * mt * p0.y + 2f * mt * t * p1.y + t * t * p2.y
            )
        }
        fun tangent(t: Float): Offset {
            val dx = 2f * (1f - t) * (p1.x - p0.x) + 2f * t * (p2.x - p1.x)
            val dy = 2f * (1f - t) * (p1.y - p0.y) + 2f * t * (p2.y - p1.y)
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
            return Offset(dx / len, dy / len)
        }
        // Feather profile: zero at base, swells early (~0.37), tapers to a point.
        fun widthAt(t: Float): Float = maxW * sin(PI * t.toDouble().pow(0.7)).toFloat()

        // ── Vane outline (right edge up to tip, left edge back to base) ───────────
        val right = ArrayList<Offset>(n + 1)
        val left  = ArrayList<Offset>(n + 1)
        for (i in 0..n) {
            val t   = i.toFloat() / n
            val c   = pt(t)
            val tan = tangent(t)
            val nor = Offset(-tan.y, tan.x)
            val w   = widthAt(t)
            right.add(Offset(c.x + nor.x * w, c.y + nor.y * w))
            left.add(Offset(c.x - nor.x * w, c.y - nor.y * w))
        }
        val vane = Path().apply {
            moveTo(right[0].x, right[0].y)
            for (i in 1..n) lineTo(right[i].x, right[i].y)
            for (i in n downTo 0) lineTo(left[i].x, left[i].y)
            close()
        }
        drawPath(
            vane,
            brush = Brush.linearGradient(
                colors = listOf(vaneDeep, vaneMid, vaneLight, vaneTip),
                start = p0, end = p2
            )
        )

        // ── Barbs — short strokes from the shaft to the edge, leaning toward the tip ─
        val lean = 0.55f
        for (i in 2 until n step 2) {
            val t = i.toFloat() / n
            val w = widthAt(t)
            if (w < maxW * 0.12f) continue
            val c   = pt(t)
            val tan = tangent(t)
            val nor = Offset(-tan.y, tan.x)
            val dirR = Offset(nor.x * (1f - lean) + tan.x * lean, nor.y * (1f - lean) + tan.y * lean)
            val dirL = Offset(-nor.x * (1f - lean) + tan.x * lean, -nor.y * (1f - lean) + tan.y * lean)
            val barb = (if (i % 4 == 0) vaneLight else vaneMid).copy(alpha = 0.5f)
            drawLine(barb, c, Offset(c.x + dirR.x * w, c.y + dirR.y * w), strokeWidth = u * 0.02f, cap = StrokeCap.Round)
            drawLine(barb, c, Offset(c.x + dirL.x * w, c.y + dirL.y * w), strokeWidth = u * 0.02f, cap = StrokeCap.Round)
        }

        // ── Bright edge highlight along the upper (right) side ────────────────────
        val edge = Path().apply {
            moveTo(right[0].x, right[0].y)
            for (i in 1..n) lineTo(right[i].x, right[i].y)
        }
        drawPath(edge, color = edgeGlow.copy(alpha = 0.6f), style = Stroke(width = u * 0.022f, cap = StrokeCap.Round))

        // ── Shaft (rachis) over the vane, following the curve ─────────────────────
        val shaft = Path().apply {
            moveTo(pt(0f).x, pt(0f).y)
            for (i in 1..n) lineTo(pt(i.toFloat() / n).x, pt(i.toFloat() / n).y)
        }
        drawPath(shaft, color = quillColor, style = Stroke(width = u * 0.038f, cap = StrokeCap.Round))
        // Quill nub at the tuck point
        drawCircle(quillColor, radius = u * 0.055f, center = p0)

        // ── Sparkle glint, tying into the wearables tier ──────────────────────────
        drawSparkle(center = pt(0.62f), radius = u * 0.10f, color = edgeGlow)
    }
}
