package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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

    private val mossTint   = Color(0xFF5A6E3A)
    private val leafDark   = Color(0xFF255C1C)
    private val leafMid    = Color(0xFF3D7A33)
    private val leafGreen  = Color(0xFF4A9240)
    private val leafLight  = Color(0xFF5EAD52)

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

        // Moss patches on trunk
        drawRoundRect(
            color = mossTint.copy(alpha = 0.40f),
            topLeft = Offset(tx - trunkW * 0.48f, trunkTopY + 1.4f * u),
            size = Size(trunkW * 0.38f, 2.0f * u),
            cornerRadius = CornerRadius(0.1f * u)
        )

        // Main branches — longer to match larger size
        drawLine(ItemPalette.woodBrown, Offset(tx, trunkTopY + 1.1f * u), Offset(tx - 1.5f * u, trunkTopY + 0.1f * u), strokeWidth = 0.25f * u, cap = StrokeCap.Round)
        drawLine(ItemPalette.woodBrown, Offset(tx, trunkTopY + 1.7f * u), Offset(tx + 1.25f * u, trunkTopY + 0.8f * u), strokeWidth = 0.21f * u, cap = StrokeCap.Round)
        drawLine(ItemPalette.woodBrown, Offset(tx, trunkTopY + 0.7f * u), Offset(tx - 0.8f * u, trunkTopY - 0.6f * u), strokeWidth = 0.16f * u, cap = StrokeCap.Round)

        // Full leafy canopy — all radii ~15% larger than before
        val cy = trunkTopY - 0.45f * u
        drawCircle(leafDark,  radius = 1.55f * u, center = Offset(tx - 0.98f * u, cy + 0.40f * u))
        drawCircle(leafDark,  radius = 1.38f * u, center = Offset(tx + 1.03f * u, cy + 0.58f * u))
        drawCircle(leafMid,   radius = 1.73f * u, center = Offset(tx - 0.29f * u, cy + 0.12f * u))
        drawCircle(leafGreen, radius = 1.50f * u, center = Offset(tx + 0.52f * u, cy - 0.40f * u))
        drawCircle(leafLight, radius = 1.21f * u, center = Offset(tx - 0.63f * u, cy - 0.75f * u))
        drawCircle(leafLight, radius = 0.81f * u, center = Offset(tx + 1.27f * u, cy - 0.23f * u))
        drawCircle(leafDark.copy(alpha = 0.5f), radius = 0.69f * u, center = Offset(tx - 1.38f * u, cy + 0.80f * u))
        drawCircle(leafDark.copy(alpha = 0.5f), radius = 0.63f * u, center = Offset(tx + 1.50f * u, cy + 0.98f * u))
    }
}
