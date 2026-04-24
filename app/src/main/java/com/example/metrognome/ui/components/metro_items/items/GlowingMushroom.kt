package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * The musician's mushroom — a detailed bioluminescent fantasy mushroom
 * sitting on the ground to Metro's right, slightly behind him.
 *
 * Background item (isBodyAttached = false): drawn in raw canvas coordinates
 * before the Metro translate block. Positioned near baseY (ground level)
 * to the right of Metro's feet.
 *
 * Design:
 *   - Chunky round cap with radial gradient (deep purple → glowing teal rim)
 *   - White polka-dot spots on the cap
 *   - Cream stalk with subtle vertical shading
 *   - Soft teal ambient glow halo around the whole mushroom
 *   - Two tiny smaller mushrooms beside it for scene depth
 */
object GlowingMushroom : MetroItem {

    override val id            = "glowing_mushroom"
    override val displayName   = "Bioluminescent Mushroom"
    override val description   = "A rare glowing mushroom the gnome found on his metro travels. The musician insisted."
    override val unlockCondition = "Complete 5 rhythm games"
    override val earnedMessage   = "Well done for completing 5 rhythm games! Metro found this rare bioluminescent mushroom on his metro travels. The musician insisted it stays."
    override val isBodyAttached  = false

    // Position is screen-width anchored (size.width * 0.88f) — hitCenter is an approximation only.
    override fun hitCenter(u: Float) = Offset(3.8f * u, -0.1f * u)
    override fun hitRadius(u: Float) = u * 1.1f

    // Palette
    private val capDeep    = Color(0xFF2D0A5C)
    private val capMid     = Color(0xFF6B1FA8)
    private val glowTeal   = Color(0xFF00E5CC)
    private val glowTealDim = Color(0x4400E5CC)
    private val stalkCream = Color(0xFFE8DFC8)
    private val stalkShade = Color(0xFFBAAF94)
    private val spotWhite  = Color(0xCCFFFFFF)
    private val rimGlow    = Color(0xBB00E5CC)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // Position: to Metro's right, at ground level
        // Metro's right shoe tip extends to about cx + 2.0u, so place at cx + 2.8u
        // Width-anchored so the cluster sits at the right edge of the screen on any device
        val mx = size.width * 0.88f
        val my = baseY - 0.1f * u

        drawMushroom(u, mx, my, scale = 1.5f)

        drawMushroom(u, mx + 0.80f * u, my + 0.05f * u, scale = 0.55f)
        drawMushroom(u, mx - 1.05f * u, my + 0.08f * u, scale = 0.38f)
    }

    private fun DrawScope.drawMushroom(u: Float, mx: Float, my: Float, scale: Float) {
        val s = u * scale

        val stalkW  = 0.55f * s
        val stalkH  = 0.70f * s
        val stalkX  = mx - stalkW / 2f
        val stalkY  = my - stalkH

        val capW    = 1.30f * s
        val capH    = 0.80f * s
        val capX    = mx - capW / 2f
        val capY    = stalkY - capH * 0.65f

        // ── Ambient glow halo ─────────────────────────────────────────────────
        if (scale >= 0.8f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowTealDim, Color.Transparent),
                    center = Offset(mx, stalkY - capH * 0.3f),
                    radius = capW * 1.1f
                ),
                radius = capW * 1.1f,
                center = Offset(mx, stalkY - capH * 0.3f)
            )
        }

        // ── Stalk ─────────────────────────────────────────────────────────────
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(stalkShade, stalkCream, stalkShade),
                startX = stalkX,
                endX   = stalkX + stalkW
            ),
            topLeft = Offset(stalkX, stalkY),
            size = Size(stalkW, stalkH),
            cornerRadius = CornerRadius(stalkW * 0.3f)
        )

        // ── Cap — oval with radial gradient ──────────────────────────────────
        val capPath = Path().apply {
            moveTo(mx - capW * 0.5f, stalkY)
            cubicTo(
                mx - capW * 0.55f, stalkY - capH * 0.2f,
                mx - capW * 0.55f, capY,
                mx, capY
            )
            cubicTo(
                mx + capW * 0.55f, capY,
                mx + capW * 0.55f, stalkY - capH * 0.2f,
                mx + capW * 0.5f,  stalkY
            )
            close()
        }
        drawPath(
            capPath,
            brush = Brush.radialGradient(
                colors = listOf(capMid, capDeep, Color(0xFF120430)),
                center = Offset(mx - s * 0.15f, stalkY - capH * 0.55f),
                radius = capW * 0.8f
            )
        )

        // ── Glowing teal rim at the cap edge ─────────────────────────────────
        drawPath(
            capPath,
            color = rimGlow,
            style = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
        )

        // ── Polka-dot spots (white, semi-transparent) ─────────────────────────
        if (scale >= 0.5f) {
            val spotR = s * 0.10f
            drawCircle(spotWhite, spotR,        Offset(mx,               stalkY - capH * 0.60f))
            drawCircle(spotWhite, spotR * 0.75f, Offset(mx - s * 0.30f,  stalkY - capH * 0.45f))
            drawCircle(spotWhite, spotR * 0.75f, Offset(mx + s * 0.28f,  stalkY - capH * 0.42f))
            if (scale >= 0.8f) {
                drawCircle(spotWhite, spotR * 0.55f, Offset(mx + s * 0.05f,  stalkY - capH * 0.80f))
                drawCircle(spotWhite, spotR * 0.50f, Offset(mx - s * 0.18f,  stalkY - capH * 0.78f))
            }
        }

        // ── Tiny teal glow dots at rim (bioluminescent spores) ────────────────
        if (scale >= 0.8f) {
            for (i in 0..4) {
                val angle = Math.toRadians((i * 36.0) - 160.0)
                val dx = (Math.cos(angle) * capW * 0.46f).toFloat()
                val dy = (Math.sin(angle) * capH * 0.28f).toFloat()
                drawCircle(
                    glowTeal.copy(alpha = 0.85f),
                    radius = s * 0.05f,
                    center = Offset(mx + dx, stalkY - capH * 0.12f + dy)
                )
            }
        }
    }
}
