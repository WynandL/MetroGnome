package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.ItemPalette
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * An enamelled gold lapel pin shaped like a classic wind-up metronome, worn on
 * Metro's left lapel. A "one week with Metro" membership pin — the first wearable
 * earned through loyalty rather than play-time.
 *
 * Coordinate anchor (body-coord origin at cx/baseY after GnomeCanvas translate):
 *   Left lapel quad corners (from drawBody):
 *     top-inner (-0.15u, -7.55u)  lower-inner (-0.65u, -6.85u)
 *     lower-outer (-1.35u, -7.05u) top-outer (-1.45u, -7.60u)
 *   The pin sits toward the outer end of the lapel at (-1.05u, -7.30u) — its right
 *   edge (~-0.80u) clears the chain's left anchor (-0.75u) so the two never overlap,
 *   and it stays clear of the bow tie (centre, ~-7.82u) and pocket square (~-1.2u, -6.6u).
 *
 * Note: hitCenter (and therefore the zoomed dialog/celebration preview) tracks this
 * anchor automatically — moving the pin on Metro never changes how it frames in dialogs.
 *
 * Silhouette: a narrow-topped trapezoid case (gold frame + purple enamel face),
 * an inverted pendulum rod with a slider weight, a top winding knob, and a
 * sparkle glint to match the premium wearables tier.
 */
object LapelPin : MetroItem {

    override val id             = "lapel_pin"
    override val displayName    = "Metronome Lapel Pin"
    override val description    = "A hand-enamelled gold pin shaped like Metro's own metronome. Worn close to the heart."
    override val earnedMessage  = "A whole week of keeping time together! Metro pinned this little enamelled metronome to his lapel in your honour, worn close to the heart, because the best tempo is the one you keep coming back to."
    override val isBodyAttached = true

    // Pin centre in body units — toward the outer end of the left lapel, right edge
    // clearing the gold chain's left anchor (-0.75u).
    private const val CX = -1.05f
    private const val CY = -7.30f

    override fun hitCenter(u: Float) = Offset(CX * u, CY * u)
    override fun hitRadius(u: Float) = u * 0.40f

    // Enamel palette is unique to this pin; gold is shared via ItemPalette.
    private val enamel      = Color(0xFF4A2E8C)   // deep brand-purple enamel
    private val enamelLight = Color(0xFF6B47C0)
    private val enamelDeep  = Color(0xFF2A1656)
    private val pinShadow   = Color(0x55000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val px = CX * u
        val py = CY * u

        val h       = 0.56f * u
        val halfTop = 0.115f * u
        val halfBot = 0.25f * u
        val topY    = py - h * 0.5f
        val botY    = py + h * 0.5f

        val corner = 0.04f * u   // rounded case corners — no sharp points to alias

        // ── Soft contact shadow (two offset layers → no hard diagonal edge) ───
        drawPath(
            roundedTrapezoid(px + u * 0.05f, halfTop, halfBot, topY + u * 0.06f, botY + u * 0.06f, corner),
            color = pinShadow.copy(alpha = 0.22f),
        )
        drawPath(
            roundedTrapezoid(px + u * 0.028f, halfTop, halfBot, topY + u * 0.035f, botY + u * 0.035f, corner),
            color = pinShadow.copy(alpha = 0.30f),
        )

        // ── Gold case frame (rounded trapezoid) ───────────────────────────────
        val frame = roundedTrapezoid(px, halfTop, halfBot, topY, botY, corner)
        drawPath(
            frame,
            brush = Brush.linearGradient(
                colors = listOf(ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                start = Offset(px - halfBot, topY),
                end   = Offset(px + halfBot, botY),
            ),
        )
        // Single soft dark rim, round joins — the only outline on the gold edge
        drawPath(frame, color = ItemPalette.goldDark.copy(alpha = 0.55f), style = Stroke(width = u * 0.018f, join = StrokeJoin.Round))

        // ── Enamel face (generous uniform inset → solid, confident gold band) ─
        val b    = 0.07f * u
        val face = roundedTrapezoid(px, halfTop - b, halfBot - b, topY + b, botY - b, corner * 0.6f)
        drawPath(
            face,
            brush = Brush.verticalGradient(
                colors = listOf(enamelLight, enamel, enamelDeep),
                startY = topY + b,
                endY   = botY - b,
            ),
        )

        // ── Inverted pendulum: pivot near the base, rod leaning up slightly ───
        val pivot  = Offset(px - 0.015f * u, botY - 0.085f * u)
        val rodTop = Offset(px + 0.025f * u, topY + 0.11f * u)
        // Three-pass rod: dark core, gold body, bright centre highlight
        drawLine(enamelDeep, pivot, rodTop, strokeWidth = u * 0.05f, cap = StrokeCap.Round)
        drawLine(ItemPalette.goldDark, pivot, rodTop, strokeWidth = u * 0.034f, cap = StrokeCap.Round)
        drawLine(ItemPalette.goldLight.copy(alpha = 0.85f), pivot, rodTop, strokeWidth = u * 0.012f, cap = StrokeCap.Round)

        // ── Slider weight — a small metal block crossing the rod ──────────────
        val wC = Offset(
            pivot.x + (rodTop.x - pivot.x) * 0.52f,
            pivot.y + (rodTop.y - pivot.y) * 0.52f,
        )
        val rodAngle = Math.toDegrees(
            atan2((rodTop.y - pivot.y).toDouble(), (rodTop.x - pivot.x).toDouble())
        ).toFloat() + 90f                          // block sits perpendicular to the rod
        val wW = 0.17f * u
        val wH = 0.085f * u
        withTransform({ rotate(rodAngle, pivot = wC) }) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                    startY = wC.y - wH / 2f,
                    endY   = wC.y + wH / 2f,
                ),
                topLeft      = Offset(wC.x - wW / 2f, wC.y - wH / 2f),
                size         = Size(wW, wH),
                cornerRadius = CornerRadius(u * 0.025f, u * 0.025f),
            )
            drawRoundRect(
                color        = ItemPalette.goldDark.copy(alpha = 0.6f),
                topLeft      = Offset(wC.x - wW / 2f, wC.y - wH / 2f),
                size         = Size(wW, wH),
                cornerRadius = CornerRadius(u * 0.025f, u * 0.025f),
                style        = Stroke(width = u * 0.012f),
            )
            // Top highlight edge on the block
            drawLine(
                color = ItemPalette.goldLight.copy(alpha = 0.7f),
                start = Offset(wC.x - wW * 0.34f, wC.y - wH * 0.28f),
                end   = Offset(wC.x + wW * 0.34f, wC.y - wH * 0.28f),
                strokeWidth = u * 0.01f,
                cap = StrokeCap.Round,
            )
        }

        // ── Pivot rivet (with a tiny specular) ────────────────────────────────
        drawCircle(ItemPalette.goldMid, radius = u * 0.04f, center = pivot)
        drawCircle(ItemPalette.goldDark.copy(alpha = 0.7f), radius = u * 0.04f, center = pivot, style = Stroke(width = u * 0.012f))
        drawCircle(Color.White.copy(alpha = 0.8f), radius = u * 0.013f, center = Offset(pivot.x - u * 0.012f, pivot.y - u * 0.012f))

        // ── Top winding knob ──────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ItemPalette.goldLight, ItemPalette.goldMid, ItemPalette.goldDark),
                center = Offset(px - u * 0.015f, topY - u * 0.015f),
                radius = u * 0.07f,
            ),
            radius = u * 0.05f,
            center = Offset(px, topY),
        )
        drawCircle(ItemPalette.goldDark.copy(alpha = 0.6f), radius = u * 0.05f, center = Offset(px, topY), style = Stroke(width = u * 0.01f))

        // ── Enamel specular + premium sparkle ─────────────────────────────────
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = 0.13f * u,
            center = Offset(px - 0.05f * u, py - 0.11f * u),
        )
        drawSparkle(
            center = Offset(px - 0.12f * u, topY + 0.12f * u),
            radius = 0.09f * u,
            color = Color.White.copy(alpha = 0.9f),
        )
    }

    /**
     * Symmetric trapezoid (narrower at the top) with rounded corners. Rounding the
     * corners removes the sharp vertices that otherwise alias ("pixelate") on the
     * steep angled sides when the pin is zoomed in the preview canvas.
     */
    private fun roundedTrapezoid(
        cxp: Float,
        halfTop: Float,
        halfBot: Float,
        topY: Float,
        botY: Float,
        r: Float,
    ): Path {
        val corners = listOf(
            Offset(cxp - halfTop, topY),   // top-left
            Offset(cxp + halfTop, topY),   // top-right
            Offset(cxp + halfBot, botY),   // bottom-right
            Offset(cxp - halfBot, botY),   // bottom-left
        )
        return Path().apply {
            for (i in corners.indices) {
                val curr  = corners[i]
                val prev  = corners[(i + 3) % 4]
                val next  = corners[(i + 1) % 4]
                val entry = towards(curr, prev, r)
                val exit  = towards(curr, next, r)
                if (i == 0) moveTo(entry.x, entry.y) else lineTo(entry.x, entry.y)
                // vertex as both control points → a clean rounded corner
                cubicTo(curr.x, curr.y, curr.x, curr.y, exit.x, exit.y)
            }
            close()
        }
    }

    /** Point [dist] px from [from] toward [to] (clamped to the edge midpoint). */
    private fun towards(from: Offset, to: Offset, dist: Float): Offset {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val len = hypot(dx, dy)
        if (len == 0f) return from
        val t = (dist / len).coerceAtMost(0.5f)
        return Offset(from.x + dx * t, from.y + dy * t)
    }
}
