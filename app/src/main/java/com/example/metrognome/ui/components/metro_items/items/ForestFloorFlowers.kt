package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A cheerful patch of wildflowers at Metro's left side, just past his shoe tips.
 * Ground-level background item — no body-attachment needed.
 */
object ForestFloorFlowers : MetroItem {

    override val id             = "forest_floor_flowers"
    override val displayName    = "Wildflowers"
    override val description    = "A cheerful patch of wildflowers that sprouted beside Metro."
    override val unlockCondition = "Use the app for 3 days"
    override val earnedMessage  = "You've been with Metro for 3 days — the forest floor is starting to bloom! These little wildflowers sprouted up beside his shoes to welcome you back."
    override val isBodyAttached  = false

    // Flowers span 8-44% of screen width — approximation for tap detection
    override fun hitCenter(u: Float) = Offset(-2.0f * u, -0.5f * u)
    override fun hitRadius(u: Float) = u * 2.0f

    private val stemGreen  = Color(0xFF4A7C3F)
    private val yellow     = Color(0xFFFFE066)
    private val pink       = Color(0xFFFF8FAB)
    private val white      = Color(0xFFF5F5F5)
    private val orange     = Color(0xFFFF9900)
    private val lightYellow = Color(0xFFFFFF88)
    private val leafGreen  = Color(0xFF5E9E52)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Patch starts at the tree base (8%) and spreads across to 44%
        drawFlower(u, size.width * 0.09f, baseY, white,  orange,      0.42f)
        drawFlower(u, size.width * 0.13f, baseY, yellow, orange,      0.60f)
        drawFlower(u, size.width * 0.17f, baseY, pink,   lightYellow, 0.68f)
        drawFlower(u, size.width * 0.21f, baseY, white,  orange,      0.52f)
        drawFlower(u, size.width * 0.25f, baseY, yellow, lightYellow, 0.74f)
        drawFlower(u, size.width * 0.30f, baseY, pink,   orange,      0.62f)
        drawFlower(u, size.width * 0.34f, baseY, white,  lightYellow, 0.56f)
        drawFlower(u, size.width * 0.39f, baseY, yellow, orange,      0.50f)
        drawFlower(u, size.width * 0.43f, baseY, pink,   lightYellow, 0.44f)
        drawGrassBlades(u, size.width * 0.11f, baseY)
        drawGrassBlades(u, size.width * 0.19f, baseY)
        drawGrassBlades(u, size.width * 0.28f, baseY)
        drawGrassBlades(u, size.width * 0.37f, baseY)
        drawGrassBlades(u, size.width * 0.44f, baseY)
    }

    private fun DrawScope.drawFlower(
        u: Float, x: Float, groundY: Float,
        petalColor: Color, centerColor: Color, scale: Float
    ) {
        val s        = u * scale
        val stemTopY = groundY - 1.15f * s

        // Stem
        drawLine(
            color = stemGreen,
            start = Offset(x, groundY),
            end   = Offset(x + 0.05f * s, stemTopY),
            strokeWidth = 0.09f * s,
            cap = StrokeCap.Round
        )

        // Small leaf on stem
        val leafPath = Path().apply {
            moveTo(x, stemTopY + 0.55f * s)
            cubicTo(x - 0.35f * s, stemTopY + 0.30f * s, x - 0.40f * s, stemTopY + 0.10f * s, x, stemTopY + 0.35f * s)
            close()
        }
        drawPath(leafPath, color = leafGreen)

        // 5 petals radiating from stem top
        val petalR    = 0.22f * s
        val petalDist = 0.26f * s
        for (i in 0 until 5) {
            val angle = Math.toRadians(i * 72.0 - 90.0)
            val px = (x + Math.cos(angle) * petalDist).toFloat()
            val py = (stemTopY + Math.sin(angle) * petalDist).toFloat()
            drawCircle(petalColor, radius = petalR, center = Offset(px, py))
        }

        // Center
        drawCircle(centerColor, radius = 0.17f * s, center = Offset(x + 0.02f * s, stemTopY))
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
