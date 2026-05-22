package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bioluminescent mushroom cluster — three mushrooms at Metro's right, slightly
 * behind him. Background item (drawn before the Metro translate block).
 *
 * Cluster layout (back → front draw order):
 *   tiny left (scale 0.42), medium right (scale 0.60), main centre (scale 1.05).
 *
 * Each mushroom has:
 *   - Radial-gradient dome cap with a rim colour band + matte velvet crown highlight
 *   - Gill crescent visible under the cap edge with radial gill lines
 *   - Cream stalk with a skirt ring (annulus) at one-third height
 *   - Polka-dot spots with a 3-D shadow crescent so they read as raised bumps
 *   - Pulsing ambient glow, each mushroom slightly out of phase
 *
 * Main + medium mushrooms emit floating bioluminescent spores that drift upward
 * from fixed launch points on the cap rim, fading as they rise. Time-based
 * (System.nanoTime) — no per-frame Random calls.
 */
object GlowingMushroom : MetroItem {

    override val id             = "glowing_mushroom"
    override val displayName    = "Bioluminescent Mushroom"
    override val description    = "A rare glowing mushroom the gnome found on his metro travels. The musician insisted."
    override val earnedMessage  = "Well done for completing 5 rhythm games! Metro found this rare bioluminescent mushroom on his metro travels. The musician insisted it stays."
    override val isBodyAttached  = false

    // Tap-detection approximation (background items use cx+hitCenter as abs pos)
    override fun hitCenter(u: Float) = Offset(3.8f * u, -0.1f * u)
    override fun hitRadius(u: Float) = u * 1.1f

    // Preview popup: centre on the main mushroom, show the full cluster
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.86f, baseY - 0.85f * u)
    override fun previewRadius(u: Float) = u * 1.9f

    // ── Palette ───────────────────────────────────────────────────────────────
    private val capDeep     = Color(0xFF1A0438)
    private val capMid      = Color(0xFF5515A0)
    private val capRim      = Color(0xFF8E3AD4)
    private val gillColor   = Color(0xFF32065C)
    private val glowTeal    = Color(0xFF00E5CC)
    private val glowTealDim = Color(0x2800E5CC)
    private val rimGlow     = Color(0xBB00E5CC)
    private val stalkCream  = Color(0xFFEEE5CC)
    private val stalkShade  = Color(0xFFBDB09A)
    private val spotWhite   = Color(0xCCFFFFFF)
    private val spotShadow  = Color(0x2A000044)
    private val velvet      = Color(0x1EFFFFFF)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t  = (System.nanoTime() / 1_000_000_000.0).toFloat()
        val mx = size.width * 0.86f
        val my = baseY - 0.08f * u

        // Back-to-front: tiny left first so main sits on top
        drawMushroom(u, mx - 0.82f * u, my + 0.15f * u, scale = 0.42f, t = t, spores = 0)
        drawMushroom(u, mx + 0.88f * u, my + 0.08f * u, scale = 0.60f, t = t, spores = 2)
        drawMushroom(u, mx,             my,              scale = 1.05f, t = t, spores = 5)
    }

    // ─────────────────────────────────────────────────────────────────────────
    private fun DrawScope.drawMushroom(
        u: Float, mx: Float, my: Float,
        scale: Float, t: Float, spores: Int,
    ) {
        val s = u * scale

        val stalkW = 0.50f * s
        val stalkH = 0.68f * s
        val stalkX = mx - stalkW / 2f
        val stalkY = my - stalkH

        val capW = 1.22f * s
        val capH = 0.74f * s
        val capY = stalkY - capH * 0.68f

        // ── Pulsing ambient glow ──────────────────────────────────────────────
        // mx * 0.01f gives each mushroom a unique phase based on its x position
        val pulse = 0.87f + 0.13f * sin(t * 1.4f + mx * 0.01f)
        val glowR = capW * 1.05f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowTealDim, Color.Transparent),
                center = Offset(mx, stalkY - capH * 0.30f),
                radius = glowR,
            ),
            radius = glowR,
            center = Offset(mx, stalkY - capH * 0.30f),
        )

        // ── Gill crescent — drawn before cap so only the edge peeks out ───────
        if (scale >= 0.55f) {
            drawOval(
                color   = gillColor,
                topLeft = Offset(mx - capW * 0.44f, stalkY - s * 0.17f),
                size    = Size(capW * 0.88f, s * 0.21f),
            )
            val lineCount = if (scale >= 0.85f) 7 else 5
            for (i in 0 until lineCount) {
                val frac = (i / (lineCount - 1).toFloat()) * 2f - 1f  // -1..1
                val gx   = mx + frac * capW * 0.40f
                drawLine(
                    color       = gillColor.copy(alpha = 0.50f),
                    start       = Offset(gx, stalkY - s * 0.13f),
                    end         = Offset(gx, stalkY + s * 0.01f),
                    strokeWidth = s * 0.024f,
                    cap         = StrokeCap.Round,
                )
            }
        }

        // ── Stalk ─────────────────────────────────────────────────────────────
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(stalkShade, stalkCream, stalkCream, stalkShade),
                startX = stalkX,
                endX   = stalkX + stalkW,
            ),
            topLeft      = Offset(stalkX, stalkY),
            size         = Size(stalkW, stalkH),
            cornerRadius = CornerRadius(stalkW * 0.28f),
        )
        // ── Cap — dome with downward-curled rim ───────────────────────────────
        val capPath = Path().apply {
            moveTo(mx - capW * 0.50f, stalkY - s * 0.06f)
            cubicTo(
                mx - capW * 0.57f, stalkY - capH * 0.24f,
                mx - capW * 0.52f, capY,
                mx, capY,
            )
            cubicTo(
                mx + capW * 0.52f, capY,
                mx + capW * 0.57f, stalkY - capH * 0.24f,
                mx + capW * 0.50f, stalkY - s * 0.06f,
            )
            close()
        }

        // Base fill — deep violet to near-black radial gradient
        drawPath(
            capPath,
            brush = Brush.radialGradient(
                colors = listOf(capMid, capDeep, Color(0xFF0C0120)),
                center = Offset(mx - s * 0.11f, stalkY - capH * 0.58f),
                radius = capW * 0.74f,
            ),
        )

        // Rim colour band — lighter purple bleeding inward from the edge
        drawPath(
            capPath,
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, capRim.copy(alpha = 0.38f)),
                center = Offset(mx, stalkY - capH * 0.38f),
                radius = capW * 0.60f,
            ),
        )

        // Velvet crown highlight — matte soft patch at the apex
        if (scale >= 0.52f) {
            drawOval(
                color   = velvet,
                topLeft = Offset(mx - capW * 0.20f, capY - s * 0.01f),
                size    = Size(capW * 0.40f, capH * 0.28f),
            )
        }

        // Glowing teal rim stroke
        drawPath(
            capPath,
            color = rimGlow,
            style = Stroke(width = s * 0.055f, cap = StrokeCap.Round),
        )

        // ── Polka-dot spots — each with a 3-D shadow crescent ────────────────
        if (scale >= 0.50f) {
            val sr = s * 0.092f
            drawSpot(mx, stalkY, s, sr,         0f,         -capH * 0.62f)
            drawSpot(mx, stalkY, s, sr * 0.72f, -s * 0.27f, -capH * 0.44f)
            drawSpot(mx, stalkY, s, sr * 0.72f,  s * 0.25f, -capH * 0.41f)
            if (scale >= 0.80f) {
                drawSpot(mx, stalkY, s, sr * 0.50f,  s * 0.06f,  -capH * 0.82f)
                drawSpot(mx, stalkY, s, sr * 0.46f, -s * 0.15f,  -capH * 0.79f)
                drawSpot(mx, stalkY, s, sr * 0.36f,  s * 0.32f,  -capH * 0.66f)
            }
        }

        // ── Floating bioluminescent spores ────────────────────────────────────
        if (spores > 0) {
            // Fixed launch angles on the cap rim and their time phase offsets
            val launchAngles  = listOf(-150.0, -110.0, -70.0, -35.0, 5.0)
            val phaseOffsets  = listOf(0.00f,   0.95f,  1.90f, 2.85f, 1.40f)
            val sporePeriod   = 3.8f  // seconds per full rise cycle

            for (i in 0 until spores.coerceAtMost(launchAngles.size)) {
                val progress = ((t + phaseOffsets[i]) / sporePeriod) % 1f
                val alpha    = (1f - progress * 1.35f).coerceIn(0f, 1f)
                if (alpha < 0.01f) continue

                // Fixed launch point on the cap rim
                val rad = (launchAngles[i] * PI / 180.0).toFloat()
                val rx  = mx + cos(rad) * capW * 0.46f
                val ry  = stalkY - capH * 0.12f + sin(rad) * capH * 0.26f

                // Rise with a gentle lateral drift (sin wave, not random)
                val driftX = sin(progress * PI.toFloat() * 2.2f) * s * 0.11f
                val sporeX = rx + driftX
                val sporeY = ry - progress * capH * 1.65f

                drawCircle(
                    color  = glowTeal.copy(alpha = alpha * 0.88f),
                    radius = s * 0.036f * (1f - progress * 0.48f),
                    center = Offset(sporeX, sporeY),
                )
            }
        }
    }

    // ── Spot helper — solid white bump with shadow crescent + tiny glint ──────
    private fun DrawScope.drawSpot(
        mx: Float, stalkY: Float, s: Float,
        r: Float, dx: Float, dy: Float,
    ) {
        val sx = mx + dx
        val sy = stalkY + dy
        drawCircle(spotShadow, r * 0.88f, Offset(sx + r * 0.22f, sy + r * 0.22f))
        drawCircle(spotWhite,  r,          Offset(sx, sy))
        drawCircle(Color(0x88FFFFFF), r * 0.30f, Offset(sx - r * 0.24f, sy - r * 0.24f))
    }
}
