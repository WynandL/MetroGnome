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
import kotlin.math.cos
import kotlin.math.sin

/**
 * A wooden torch post Metro planted to light his way home after late rehearsals.
 * Positioned at size.width * 0.70f. Base is raised 1u above ground so it reads
 * as set back in the scene — justifying its height relative to the tree.
 * Flames reuse the same time-based flicker technique as the old campfire.
 */
object TorchPost : MetroItem {

    override val id             = "torch_post"
    override val displayName    = "Forest Torch"
    override val description    = "A torch Metro planted to light his way home after late rehearsals."
    override val unlockCondition = "Complete 15 rhythm games"
    override val earnedMessage  = "15 rhythm games! Metro planted this torch so the forest path home is never dark again. It flickers with every beat."
    override val isBodyAttached  = false

    // size.width * 0.70f — approximation for tap detection
    override fun hitCenter(u: Float) = Offset(1.8f * u, -2.5f * u)
    override fun hitRadius(u: Float) = u * 0.9f

    // px = maxOf(canvasW*0.76, canvasW/2+3u); post spans baseY-1u to flame tip ~baseY-6u → centre at baseY-3u
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(maxOf(canvasW * 0.76f, canvasW * 0.5f + 3f * u), baseY - 3.0f * u)
    override fun previewRadius(u: Float) = u * 3.5f

    private val wrapColor  = Color(0xFF8B6914)
    private val wrapDark   = Color(0xFF5A4210)
    private val flameDeep  = Color(0xFFFF4400)
    private val flameMid   = Color(0xFFFF8800)
    private val flameTip   = Color(0xFFFFDD00)
    private val flameWhite = Color(0xCCFFFF99)
    private val glowOrange = Color(0x33FF6600)
    private val grassDark  = Color(0xFF3A6B2E)
    private val grassMid   = Color(0xFF4E8C3F)
    private val grassLight = Color(0xFF62A852)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        // 76% of screen width, but never closer than 3u to the right of Metro's centre
        val px       = maxOf(size.width * 0.76f, cx + 3.0f * u)
        val groundY  = baseY - 1.0f * u   // raised base — torch is set back in the scene
        val postH    = 4.2f * u
        val postW    = 0.16f * u
        val postTopY = groundY - postH
        val headH    = 0.50f * u
        val headW    = 0.36f * u
        val headTopY = postTopY - headH * 0.5f
        val fu       = u * 0.78f
        val t        = (System.currentTimeMillis() % 12_000L) / 1000f

        // Glow halo around the flame
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowOrange, Color.Transparent),
                center = Offset(px, headTopY),
                radius = fu * 2.8f
            ),
            radius = fu * 2.8f,
            center = Offset(px, headTopY)
        )

        // Grass blades radiating from the post base
        val grassAngles = listOf(
            12f to 0.42f, 28f to 0.56f, 46f to 0.48f, 65f to 0.60f, 82f to 0.44f,
            98f to 0.52f, 116f to 0.58f, 135f to 0.46f, 152f to 0.54f, 168f to 0.40f
        )
        grassAngles.forEachIndexed { i, (angleDeg, lengthFactor) ->
            val grassColor = when (i % 3) { 0 -> grassDark; 1 -> grassMid; else -> grassLight }
            drawGrassBlade(px, groundY, angleDeg, lengthFactor * u, u, grassColor)
        }

        // Post shaft
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(ItemPalette.woodBrown, ItemPalette.woodLight, ItemPalette.woodBrown),
                startX = px - postW / 2f,
                endX   = px + postW / 2f
            ),
            topLeft = Offset(px - postW / 2f, postTopY),
            size    = Size(postW, groundY - postTopY),
            cornerRadius = CornerRadius(postW * 0.3f)
        )

        // Torch head — wrapped cloth bundle
        drawRoundRect(
            color   = wrapColor,
            topLeft = Offset(px - headW / 2f, headTopY),
            size    = Size(headW, headH),
            cornerRadius = CornerRadius(headW * 0.25f)
        )
        // Wrap bands
        repeat(3) { i ->
            val bandY = headTopY + headH * (0.2f + i * 0.28f)
            drawLine(wrapDark, Offset(px - headW / 2f, bandY), Offset(px + headW / 2f, bandY), strokeWidth = 0.035f * u, cap = StrokeCap.Round)
        }

        // Layered flames — headTopY + 0.30*fu offsets so flameBaseY lands at headTopY
        val flameAnchor = headTopY + 0.30f * fu
        drawFlame(fu, px,             flameAnchor, t, widthFactor = 1.0f,  heightFactor = 1.0f,  color = flameDeep,  phaseOff = 0.0f)
        drawFlame(fu, px - 0.09f * fu, flameAnchor, t, widthFactor = 0.75f, heightFactor = 0.86f, color = flameMid,   phaseOff = 0.7f)
        drawFlame(fu, px + 0.07f * fu, flameAnchor, t, widthFactor = 0.65f, heightFactor = 0.78f, color = flameMid,   phaseOff = 1.4f)
        drawFlame(fu, px,             flameAnchor, t, widthFactor = 0.45f, heightFactor = 0.55f, color = flameTip,   phaseOff = 0.3f)
        drawFlame(fu, px,             flameAnchor, t, widthFactor = 0.26f, heightFactor = 0.30f, color = flameWhite, phaseOff = 1.0f)
    }

    private fun DrawScope.drawGrassBlade(
        baseX: Float, baseY: Float,
        angleDeg: Float, length: Float, u: Float, color: Color
    ) {
        val rad  = Math.toRadians(angleDeg.toDouble())
        val endX = (baseX + cos(rad) * length).toFloat()
        val endY = (baseY - sin(rad) * length).toFloat()   // y-up in screen coords
        // Curve the blade by offsetting the control point perpendicular to the blade direction
        val perpRad = rad + Math.PI / 2
        val ctrlX = ((baseX + endX) / 2f + cos(perpRad) * length * 0.28f).toFloat()
        val ctrlY = ((baseY + endY) / 2f - sin(perpRad) * length * 0.28f).toFloat()
        drawPath(
            Path().apply {
                moveTo(baseX, baseY)
                quadraticTo(ctrlX, ctrlY, endX, endY)
            },
            color = color,
            style = Stroke(width = u * 0.07f, cap = StrokeCap.Round)
        )
    }

    private fun DrawScope.drawFlame(
        u: Float, baseX: Float, baseY: Float, t: Float,
        widthFactor: Float, heightFactor: Float,
        color: Color, phaseOff: Float
    ) {
        val fw         = 0.30f * u * widthFactor
        val fh         = 0.90f * u * heightFactor
        val sway       = sin(t * 3.5f + phaseOff) * 0.09f * u
        val tipX       = baseX + sway
        val flameBaseY = baseY - 0.30f * u

        drawPath(
            Path().apply {
                moveTo(baseX - fw, flameBaseY)
                cubicTo(baseX - fw * 0.8f, flameBaseY - fh * 0.4f, tipX - fw * 0.3f, flameBaseY - fh * 0.8f, tipX, flameBaseY - fh)
                cubicTo(tipX + fw * 0.3f, flameBaseY - fh * 0.8f, baseX + fw * 0.8f, flameBaseY - fh * 0.4f, baseX + fw, flameBaseY)
                close()
            },
            color = color
        )
    }
}
