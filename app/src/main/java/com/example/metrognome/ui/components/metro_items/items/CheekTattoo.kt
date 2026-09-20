package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem

/**
 * A small eighth-note tattoo on Metro's right cheek (screen right), balancing the
 * GoldEarring on the screen-left ear.
 *
 * Coordinate anchor derived from drawHead / drawSunglasses / drawMustache geometry:
 *   head centre (0, -10.0f*u), r = 1.85f*u
 *   right blush centre (1.05f*u, -9.55f*u), r = 0.48f*u
 *   sunglasses lens bottom edge y = -9.99f*u; temple arm passes above -10.2f*u
 *   mustache wing top edge y = ~-9.2f*u near x = 1.3f*u
 * The free cheekbone band is therefore y in [-9.9f*u, -9.4f*u], x in [1.1f*u, 1.55f*u].
 * The tattoo centres at (1.32f*u, -9.65f*u), height 0.5f*u, tilted like flash art.
 *
 * Rendered as ink, not jewelry: a single flat dark blue-grey layer with translucency
 * so the skin (and blush) shows through. No speculars, no sparkles, no under-layers -
 * a tattoo is flat on the skin, so any halo or shadow effect reads wrong.
 */
object CheekTattoo : MetroItem {

    override val id            = "cheek_tattoo"
    override val displayName   = "Music Note Tattoo"
    override val description   = "A tiny eighth note inked on Metro's cheek. Some commitments are permanent."
    override val earnedMessage   = "Thirty different days of keeping time together. That is not a habit anymore, that is devotion. Metro went ahead and made it permanent."
    override val isBodyAttached  = true
    override val isHeadAttached  = true

    // Tattoo anchor in body coordinates (after translate(cx, baseY))
    private const val TX = 1.32f   // * u
    private const val TY = -9.38f  // * u
    private const val TILT_DEG = -12f

    // Ink palette - item-unique, stays private (see Color System rules)
    private val inkBlue = Color(0xCC26374A)   // aged tattoo ink, translucent

    override fun hitCenter(u: Float) = Offset(TX * u, TY * u)
    override fun hitRadius(u: Float) = u * 0.45f

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val center = Offset(TX * u, TY * u)
        val h = 0.50f * u

        withTransform({ rotate(TILT_DEG, pivot = center) }) {
            drawEighthNote(center, h, inkBlue)
        }
    }

    /** Union the parts before applying translucent ink so the joins cannot darken. */
    private fun DrawScope.drawEighthNote(center: Offset, height: Float, color: Color) {
        val headC = Offset(center.x - height * 0.10f, center.y + height * 0.30f)
        val stemX = headC.x + height * 0.276f
        val stemTop = headC.y - height * 0.85f
        val halfStem = height * 0.055f

        // A gently tilted oval, expressed in the same coordinates as the stem and flag.
        val head = Path().apply {
            moveTo(headC.x + height * 0.285f, headC.y - height * 0.093f)
            cubicTo(
                headC.x + height * 0.324f, headC.y + height * 0.028f,
                headC.x + height * 0.229f, headC.y + height * 0.168f,
                headC.x + height * 0.071f, headC.y + height * 0.219f
            )
            cubicTo(
                headC.x - height * 0.087f, headC.y + height * 0.270f,
                headC.x - height * 0.246f, headC.y + height * 0.214f,
                headC.x - height * 0.285f, headC.y + height * 0.093f
            )
            cubicTo(
                headC.x - height * 0.324f, headC.y - height * 0.028f,
                headC.x - height * 0.229f, headC.y - height * 0.168f,
                headC.x - height * 0.071f, headC.y - height * 0.219f
            )
            cubicTo(
                headC.x + height * 0.087f, headC.y - height * 0.270f,
                headC.x + height * 0.246f, headC.y - height * 0.214f,
                headC.x + height * 0.285f, headC.y - height * 0.093f
            )
            close()
        }
        val stem = Path().apply {
            addRoundRect(RoundRect(
                rect = Rect(stemX - halfStem, stemTop - halfStem, stemX + halfStem, headC.y),
                cornerRadius = CornerRadius(halfStem)
            ))
        }
        // A full shoulder flows into a tapered flag with an open inner curve.
        val flag = Path().apply {
            moveTo(stemX, stemTop)
            cubicTo(
                stemX + height * 0.12f, stemTop + height * 0.09f,
                stemX + height * 0.35f, stemTop + height * 0.13f,
                stemX + height * 0.30f, stemTop + height * 0.32f
            )
            cubicTo(
                stemX + height * 0.28f, stemTop + height * 0.41f,
                stemX + height * 0.20f, stemTop + height * 0.49f,
                stemX + height * 0.13f, stemTop + height * 0.53f
            )
            cubicTo(
                stemX + height * 0.23f, stemTop + height * 0.38f,
                stemX + height * 0.26f, stemTop + height * 0.26f,
                stemX + height * 0.15f, stemTop + height * 0.21f
            )
            cubicTo(
                stemX + height * 0.10f, stemTop + height * 0.19f,
                stemX + height * 0.04f, stemTop + height * 0.17f,
                stemX, stemTop + height * 0.14f
            )
            close()
        }
        val headAndStem = Path.combine(PathOperation.Union, head, stem)
        val note = Path.combine(PathOperation.Union, headAndStem, flag)
        drawPath(note, color = color)
    }
}
