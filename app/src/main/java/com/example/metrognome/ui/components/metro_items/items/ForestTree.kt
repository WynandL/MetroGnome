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
import com.example.metrognome.ui.theme.ItemPalette

/**
 * Metro's forest guardian — a grand old oak.
 *
 * Trunk x uses size.width * 0.10f (10% from left edge) so it is always
 * correctly anchored regardless of device aspect ratio. Previously used
 * cx-3.5u which is height-derived and overshot the left edge on narrow phones.
 *
 * Drawn before Metro (background layer), so Metro stands in front of it.
 */
object ForestTree : MetroItem {

    override val id             = "forest_tree"
    override val displayName    = "Old Oak Tree"
    override val description    = "Metro's forest guardian...a grand old oak that was always there."
    override val unlockCondition = "Use the app for 30 days"
    override val earnedMessage  = "One whole month! The forest recognises a true musician. Metro's oak has always been there, waiting patiently while he practised. It stands proud beside him now."
    override val isBodyAttached  = false

    // Rough approximation: tree is near left edge, ~3.1u left of centre on most phones
    override fun hitCenter(u: Float) = Offset(-3.1f * u, -4.5f * u)
    override fun hitRadius(u: Float) = u * 1.8f

    // tx = canvasW*0.10; tree spans baseY (roots) to baseY-7.6u (canopy top) → centre at baseY-3.8u
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.10f, baseY - 3.8f * u)
    override fun previewRadius(u: Float) = u * 4.2f

    private val mossTint    = Color(0xFF5A6E3A)
    private val mossLight   = Color(0xFF7A9450)
    private val mossDark    = Color(0xFF44552C)
    private val leafDark    = Color(0xFF255C1C)
    private val leafMid     = Color(0xFF3D7A33)
    private val leafGreen   = Color(0xFF4A9240)
    private val leafLight   = Color(0xFF5EAD52)
    private val leafLighter = Color(0xFF7BC56A)
    private val barkDark    = Color(0xFF3A2614)
    private val barkDeep    = Color(0xFF1E1408)
    private val sunDapple   = Color(0xFFCDE8A0)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val tx      = size.width * 0.10f     // 10% from left edge — width-anchored, not height-derived
        val groundY = baseY - 0.1f * u
        val trunkW  = 0.54f * u              // ~15% wider than before
        val trunkH  = 5.3f * u              // ~15% taller than before
        val trunkTopY = groundY - trunkH

        // Flaring roots at the base
        val rootPath = Path().apply {
            moveTo(tx - trunkW, groundY)
            cubicTo(tx - trunkW * 1.2f, groundY - 0.5f * u, tx - trunkW * 0.6f, groundY - 1.0f * u, tx - trunkW * 0.5f, groundY - 1.5f * u)
            lineTo(tx + trunkW * 0.5f, groundY - 1.5f * u)
            cubicTo(tx + trunkW * 0.6f, groundY - 1.0f * u, tx + trunkW * 1.2f, groundY - 0.5f * u, tx + trunkW, groundY)
            close()
        }
        drawPath(
            rootPath,
            brush = Brush.verticalGradient(
                colors = listOf(ItemPalette.woodLight, ItemPalette.woodBrown),
                startY = groundY - 1.5f * u,
                endY   = groundY
            )
        )

        // Trunk
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(ItemPalette.woodBrown, ItemPalette.woodLight, ItemPalette.woodBrown),
                startX = tx - trunkW / 2f,
                endX   = tx + trunkW / 2f
            ),
            topLeft = Offset(tx - trunkW / 2f, trunkTopY),
            size = Size(trunkW, trunkH),
            cornerRadius = CornerRadius(trunkW * 0.25f)
        )

        // Root creases for depth
        drawLine(barkDark.copy(alpha = 0.35f), Offset(tx - trunkW * 0.55f, groundY - 1.25f * u), Offset(tx - trunkW * 0.9f, groundY - 0.1f * u), strokeWidth = 0.05f * u, cap = StrokeCap.Round)
        drawLine(barkDark.copy(alpha = 0.35f), Offset(tx + trunkW * 0.55f, groundY - 1.25f * u), Offset(tx + trunkW * 0.9f, groundY - 0.1f * u), strokeWidth = 0.05f * u, cap = StrokeCap.Round)

        // Bark grooves — gently curved vertical channels
        listOf(-0.22f, 0.0f, 0.20f).forEach { gx ->
            val x = tx + gx * trunkW
            drawPath(
                Path().apply {
                    moveTo(x, trunkTopY + 0.3f * u)
                    cubicTo(
                        x + 0.06f * u, trunkTopY + trunkH * 0.35f,
                        x - 0.06f * u, trunkTopY + trunkH * 0.65f,
                        x + 0.02f * u, groundY - 0.2f * u
                    )
                },
                color = barkDark.copy(alpha = 0.30f),
                style = Stroke(width = 0.045f * u, cap = StrokeCap.Round)
            )
        }

        // Knot-hole
        val knotC = Offset(tx + trunkW * 0.16f, trunkTopY + 1.05f * u)
        drawOval(barkDark, topLeft = Offset(knotC.x - 0.12f * u, knotC.y - 0.17f * u), size = Size(0.24f * u, 0.34f * u))
        drawOval(barkDeep, topLeft = Offset(knotC.x - 0.07f * u, knotC.y - 0.11f * u), size = Size(0.14f * u, 0.22f * u))
        drawOval(ItemPalette.woodLight.copy(alpha = 0.45f), topLeft = Offset(knotC.x - 0.12f * u, knotC.y - 0.17f * u), size = Size(0.24f * u, 0.34f * u), style = Stroke(width = 0.03f * u))

        // Eighth note carved into the bark (easter egg — ties into the music tier)
        drawCarvedNote(Offset(tx - trunkW * 0.10f, trunkTopY + 2.7f * u), 0.7f * u, u)

        // Moss — one irregular matte patch on the shaded base (not bubbles)
        drawMossPatch(tx, trunkTopY, u)

        // Main branches — longer to match larger size
        drawLine(ItemPalette.woodBrown, Offset(tx, trunkTopY + 1.1f * u), Offset(tx - 1.5f * u, trunkTopY + 0.1f * u), strokeWidth = 0.25f * u, cap = StrokeCap.Round)
        drawLine(ItemPalette.woodBrown, Offset(tx, trunkTopY + 1.7f * u), Offset(tx + 1.25f * u, trunkTopY + 0.8f * u), strokeWidth = 0.21f * u, cap = StrokeCap.Round)
        drawLine(ItemPalette.woodBrown, Offset(tx, trunkTopY + 0.7f * u), Offset(tx - 0.8f * u, trunkTopY - 0.6f * u), strokeWidth = 0.16f * u, cap = StrokeCap.Round)

        // Leafy canopy — same blob layout, now with volume and texture
        val cy = trunkTopY - 0.45f * u
        // Faint back fillers
        drawCircle(leafDark.copy(alpha = 0.5f), radius = 0.69f * u, center = Offset(tx - 1.38f * u, cy + 0.80f * u))
        drawCircle(leafDark.copy(alpha = 0.5f), radius = 0.63f * u, center = Offset(tx + 1.50f * u, cy + 0.98f * u))
        // Volumetric puffs (lit from upper-left), back to front
        leafPuff(Offset(tx - 0.98f * u, cy + 0.40f * u), 1.55f * u, leafMid,     leafDark)
        leafPuff(Offset(tx + 1.03f * u, cy + 0.58f * u), 1.38f * u, leafMid,     leafDark)
        leafPuff(Offset(tx - 0.29f * u, cy + 0.12f * u), 1.73f * u, leafGreen,   leafMid)
        leafPuff(Offset(tx + 0.52f * u, cy - 0.40f * u), 1.50f * u, leafLight,   leafGreen)
        leafPuff(Offset(tx - 0.63f * u, cy - 0.75f * u), 1.21f * u, leafLighter, leafLight)
        leafPuff(Offset(tx + 1.27f * u, cy - 0.23f * u), 0.81f * u, leafLighter, leafLight)

        // Foliage clumps — break up the smooth edges (fixed positions = no flicker)
        listOf(
            Triple(-1.0f, -0.6f, 0.28f) to 0,
            Triple(-0.4f, -1.0f, 0.24f) to 0,
            Triple(0.1f, -0.7f, 0.22f) to 0,
            Triple(-1.3f, 0.0f, 0.20f) to 0,
            Triple(0.3f, 0.2f, 0.30f) to 1,
            Triple(-0.7f, 0.5f, 0.26f) to 1,
            Triple(1.0f, -0.5f, 0.24f) to 1,
            Triple(-0.2f, 0.7f, 0.22f) to 1,
            Triple(0.9f, 0.7f, 0.30f) to 2,
            Triple(1.4f, 0.3f, 0.22f) to 2,
            Triple(0.4f, 1.0f, 0.26f) to 2,
            Triple(-1.1f, 0.9f, 0.22f) to 2,
            Triple(-0.2f, 1.1f, 0.20f) to 2,
            Triple(1.2f, -0.1f, 0.18f) to 2
        ).forEach { (d, tone) ->
            val c = Offset(tx + d.first * u, cy + d.second * u)
            val col = when (tone) {
                0 -> leafLighter
                1 -> leafGreen
                else -> leafDark
            }
            drawCircle(col.copy(alpha = 0.55f), d.third * u, c)
        }

        // Dappled sunlight specks
        listOf(
            Offset(tx - 0.6f * u, cy - 0.9f * u),
            Offset(tx - 0.1f * u, cy - 1.1f * u),
            Offset(tx + 0.7f * u, cy - 0.7f * u)
        ).forEach { drawCircle(sunDapple.copy(alpha = 0.5f), 0.12f * u, it) }
    }

    /** One irregular, matte moss patch with mottling and fine speckle — clings to the shaded base. */
    private fun DrawScope.drawMossPatch(tx: Float, trunkTopY: Float, u: Float) {
        val patch = Path().apply {
            moveTo(tx - 0.05f * u, trunkTopY + 3.5f * u)
            cubicTo(tx + 0.06f * u, trunkTopY + 3.6f * u, tx + 0.10f * u, trunkTopY + 3.95f * u, tx + 0.03f * u, trunkTopY + 4.25f * u)
            cubicTo(tx + 0.08f * u, trunkTopY + 4.55f * u, tx - 0.02f * u, trunkTopY + 4.88f * u, tx - 0.14f * u, trunkTopY + 4.9f * u)
            cubicTo(tx - 0.27f * u, trunkTopY + 4.88f * u, tx - 0.32f * u, trunkTopY + 4.45f * u, tx - 0.29f * u, trunkTopY + 4.05f * u)
            cubicTo(tx - 0.34f * u, trunkTopY + 3.75f * u, tx - 0.21f * u, trunkTopY + 3.5f * u, tx - 0.05f * u, trunkTopY + 3.5f * u)
            close()
        }
        drawPath(patch, color = mossTint.copy(alpha = 0.85f))

        // Lighter mottle toward the lit upper-left
        val mottle = Path().apply {
            moveTo(tx - 0.06f * u, trunkTopY + 3.66f * u)
            cubicTo(tx - 0.18f * u, trunkTopY + 3.7f * u, tx - 0.28f * u, trunkTopY + 3.95f * u, tx - 0.23f * u, trunkTopY + 4.18f * u)
            cubicTo(tx - 0.13f * u, trunkTopY + 4.04f * u, tx - 0.02f * u, trunkTopY + 3.92f * u, tx - 0.06f * u, trunkTopY + 3.66f * u)
            close()
        }
        drawPath(mottle, color = mossLight.copy(alpha = 0.5f))

        // Fine speckle — tiny matte dots for mossy texture (fixed positions = no flicker)
        listOf(
            Triple(-0.20f, 3.9f, 0.034f),
            Triple(-0.05f, 4.1f, 0.030f),
            Triple(-0.24f, 4.4f, 0.030f),
            Triple(-0.10f, 4.62f, 0.026f),
            Triple(0.01f, 4.32f, 0.026f),
            Triple(-0.29f, 4.15f, 0.026f)
        ).forEach { (dx, dy, r) ->
            drawCircle(mossDark.copy(alpha = 0.55f), r * u, Offset(tx + dx * u, trunkTopY + dy * u))
        }
        listOf(
            Triple(-0.15f, 3.82f, 0.026f),
            Triple(-0.26f, 4.06f, 0.022f),
            Triple(-0.08f, 4.45f, 0.022f)
        ).forEach { (dx, dy, r) ->
            drawCircle(mossLight.copy(alpha = 0.7f), r * u, Offset(tx + dx * u, trunkTopY + dy * u))
        }
    }

    /** A single canopy puff with volume — lit from the upper-left, shading to the base colour. */
    private fun DrawScope.leafPuff(center: Offset, radius: Float, light: Color, dark: Color) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(light, dark),
                center = Offset(center.x - radius * 0.35f, center.y - radius * 0.40f),
                radius = radius * 1.35f
            ),
            radius = radius,
            center = center
        )
    }

    /** A small eighth note carved into the bark — a hidden nod to the music tier. */
    private fun DrawScope.drawCarvedNote(center: Offset, h: Float, u: Float) {
        val cut    = barkDark.copy(alpha = 0.70f)
        val bevel  = ItemPalette.woodLight.copy(alpha = 0.55f)
        val headRx = h * 0.26f
        val headRy = h * 0.20f
        val headC  = Offset(center.x - h * 0.08f, center.y + h * 0.32f)
        val stemX  = headC.x + headRx * 0.9f
        val stemTop = headC.y - h * 0.80f

        // Bevel highlight (the carved edge catching light)
        drawLine(bevel, Offset(stemX - 0.02f * u, headC.y), Offset(stemX - 0.02f * u, stemTop), strokeWidth = h * 0.10f, cap = StrokeCap.Round)
        // Cut stem
        drawLine(cut, Offset(stemX, headC.y), Offset(stemX, stemTop), strokeWidth = h * 0.10f, cap = StrokeCap.Round)
        // Flag
        drawPath(
            Path().apply {
                moveTo(stemX, stemTop)
                cubicTo(
                    stemX + h * 0.30f, stemTop + h * 0.10f,
                    stemX + h * 0.26f, stemTop + h * 0.34f,
                    stemX + h * 0.05f, stemTop + h * 0.46f
                )
            },
            color = cut,
            style = Stroke(width = h * 0.08f, cap = StrokeCap.Round)
        )
        // Head
        drawOval(cut, topLeft = Offset(headC.x - headRx, headC.y - headRy), size = Size(headRx * 2f, headRy * 2f))
    }
}
