package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A cheerful patch of wildflowers at Metro's left side, just past his shoe tips.
 * Ground-level background item — no body-attachment needed.
 */
object ForestFloorFlowers : MetroItem {

    override val id             = "forest_floor_flowers"
    override val displayName    = "Wildflowers"
    override val description    = "A cheerful patch of wildflowers that sprouted beside Metro."
    override val earnedMessage  = "You've been with Metro for 3 days! The forest floor is starting to bloom! These little wildflowers sprouted up beside his shoes to welcome you back."
    override val isBodyAttached  = false

    // Flowers span ~9-33% of screen width — approximation for tap detection
    override fun hitCenter(u: Float) = Offset(-2.1f * u, -0.5f * u)
    override fun hitRadius(u: Float) = u * 1.8f

    // Show a few flowers close-up: centre on the left of the patch, mid-stem height
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.21f, baseY - 0.6f * u)
    override fun previewRadius(u: Float) = u * 2.0f

    private val stemGreen   = Color(0xFF4A7C3F)
    private val yellow      = Color(0xFFFFE066)
    private val pink        = Color(0xFFFF8FAB)
    private val white        = Color(0xFFF5F5F5)
    private val orange      = Color(0xFFFF9900)
    private val lightYellow = Color(0xFFFFFF88)
    private val leafGreen   = Color(0xFF5E9E52)
    private val centerDark  = Color(0xFFCC7000)   // stamen dots

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Grass tufts first (behind flowers). Narrower band: ~9%-33%.
        drawGrassBlades(u, size.width * 0.10f, baseY)
        drawGrassBlades(u, size.width * 0.165f, baseY)
        drawGrassBlades(u, size.width * 0.235f, baseY)
        drawGrassBlades(u, size.width * 0.30f, baseY)
        drawGrassBlades(u, size.width * 0.33f, baseY)

        // A couple of buds for variety, tucked among the blooms
        drawBud(u, size.width * 0.11f, baseY, pink,   0.46f)
        drawBud(u, size.width * 0.285f, baseY, yellow, 0.42f)

        // Blooms — denser, narrower, slightly shorter than before, with varied petal counts
        drawFlower(u, size.width * 0.09f,  baseY, white,  orange,      0.42f, 5, -0.04f)
        drawFlower(u, size.width * 0.125f, baseY, yellow, centerDark,  0.58f, 6,  0.03f)
        drawFlower(u, size.width * 0.16f,  baseY, pink,   lightYellow, 0.64f, 5, -0.03f)
        drawFlower(u, size.width * 0.195f, baseY, white,  orange,      0.50f, 6,  0.04f)
        drawFlower(u, size.width * 0.23f,  baseY, yellow, lightYellow, 0.60f, 5, -0.02f)
        drawFlower(u, size.width * 0.265f, baseY, pink,   orange,      0.54f, 6,  0.03f)
        drawFlower(u, size.width * 0.30f,  baseY, white,  lightYellow, 0.46f, 5, -0.03f)
        drawFlower(u, size.width * 0.33f,  baseY, yellow, orange,      0.42f, 6, -0.04f)
    }

    private fun DrawScope.drawFlower(
        u: Float, x: Float, groundY: Float,
        petalColor: Color, centerColor: Color, scale: Float,
        petals: Int, lean: Float
    ) {
        val s        = u * scale
        val topX     = x + lean * s
        val stemTopY = groundY - 1.05f * s

        // Stem (gently leaning)
        drawLine(
            color = stemGreen,
            start = Offset(x, groundY),
            end   = Offset(topX, stemTopY),
            strokeWidth = 0.09f * s,
            cap = StrokeCap.Round
        )

        // Two leaves on the stem
        leafAt(x - 0.01f * s, groundY - 0.42f * s, s * 0.95f, -1f)
        leafAt(x + 0.04f * s, groundY - 0.72f * s, s * 0.75f, 1f)

        // Petals — elongated ovals radiating from the bloom centre, with a soft tip sheen
        val petalW    = 0.18f * s
        val petalH    = 0.34f * s
        val petalDist = 0.24f * s
        val step      = 360f / petals
        for (i in 0 until petals) {
            withTransform({ rotate(i * step, pivot = Offset(topX, stemTopY)) }) {
                val cyP = stemTopY - petalDist
                drawOval(
                    color   = petalColor,
                    topLeft = Offset(topX - petalW / 2f, cyP - petalH / 2f),
                    size    = Size(petalW, petalH)
                )
                drawOval(
                    color   = white.copy(alpha = 0.35f),
                    topLeft = Offset(topX - petalW * 0.28f, cyP - petalH * 0.42f),
                    size    = Size(petalW * 0.56f, petalH * 0.40f)
                )
            }
        }

        // Bloom centre + ring of stamen dots + a small highlight
        drawCircle(centerColor, radius = 0.16f * s, center = Offset(topX, stemTopY))
        for (i in 0 until 5) {
            val a  = Math.toRadians(i * 72.0)
            val dx = (Math.cos(a) * 0.07f * s).toFloat()
            val dy = (Math.sin(a) * 0.07f * s).toFloat()
            drawCircle(centerDark, radius = 0.028f * s, center = Offset(topX + dx, stemTopY + dy))
        }
        drawCircle(white.copy(alpha = 0.5f), radius = 0.05f * s, center = Offset(topX - 0.04f * s, stemTopY - 0.04f * s))
    }

    /** A small leaf on the stem. dir = -1 for left, +1 for right. */
    private fun DrawScope.leafAt(ax: Float, ay: Float, s: Float, dir: Float) {
        val leaf = Path().apply {
            moveTo(ax, ay)
            cubicTo(ax + dir * 0.30f * s, ay - 0.06f * s, ax + dir * 0.34f * s, ay - 0.24f * s, ax + dir * 0.10f * s, ay - 0.26f * s)
            cubicTo(ax + dir * 0.06f * s, ay - 0.14f * s, ax + dir * 0.02f * s, ay - 0.05f * s, ax, ay)
            close()
        }
        drawPath(leaf, color = leafGreen)
        drawLine(
            color = stemGreen.copy(alpha = 0.5f),
            start = Offset(ax, ay),
            end   = Offset(ax + dir * 0.18f * s, ay - 0.19f * s),
            strokeWidth = 0.02f * s,
            cap = StrokeCap.Round
        )
    }

    /** A closed flower bud — short stem, green calyx, coloured tip. */
    private fun DrawScope.drawBud(u: Float, x: Float, groundY: Float, color: Color, scale: Float) {
        val s    = u * scale
        val topY = groundY - 0.85f * s
        drawLine(stemGreen, Offset(x, groundY), Offset(x + 0.03f * s, topY), strokeWidth = 0.08f * s, cap = StrokeCap.Round)
        leafAt(x, groundY - 0.34f * s, s * 0.8f, -1f)
        drawOval(leafGreen, topLeft = Offset(x - 0.10f * s, topY - 0.02f * s), size = Size(0.20f * s, 0.22f * s))
        drawOval(color, topLeft = Offset(x - 0.085f * s, topY - 0.16f * s), size = Size(0.17f * s, 0.22f * s))
        drawOval(white.copy(alpha = 0.35f), topLeft = Offset(x - 0.05f * s, topY - 0.14f * s), size = Size(0.06f * s, 0.12f * s))
    }

    private fun DrawScope.drawGrassBlades(u: Float, x: Float, groundY: Float) {
        val offsets = listOf(-0.15f, 0f, 0.18f, -0.08f)
        offsets.forEach { dx ->
            drawLine(
                color = leafGreen,
                start = Offset(x + dx * u, groundY),
                end   = Offset(x + dx * u + 0.1f * u, groundY - 0.55f * u),
                strokeWidth = 0.07f * u,
                cap = StrokeCap.Round
            )
        }
    }
}
