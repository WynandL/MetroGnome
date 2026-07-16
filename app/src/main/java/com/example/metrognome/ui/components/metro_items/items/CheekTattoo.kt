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
    private const val TY = -9.65f  // * u
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

    /** A small flash-art eighth note built from notehead, stem and flag. */
    private fun DrawScope.drawEighthNote(center: Offset, height: Float, color: Color) {
        val headRx  = height * 0.30f
        val headRy  = height * 0.23f
        val headC   = Offset(center.x - height * 0.10f, center.y + height * 0.30f)
        val stemX   = headC.x + headRx * 0.92f
        val stemTop = headC.y - height * 0.85f

        // Stem
        drawLine(
            color = color,
            start = Offset(stemX, headC.y),
            end = Offset(stemX, stemTop),
            strokeWidth = height * 0.11f,
            cap = StrokeCap.Round
        )
        // Flag off the top of the stem
        drawPath(
            Path().apply {
                moveTo(stemX, stemTop)
                cubicTo(
                    stemX + height * 0.34f, stemTop + height * 0.12f,
                    stemX + height * 0.30f, stemTop + height * 0.40f,
                    stemX + height * 0.06f, stemTop + height * 0.52f
                )
                cubicTo(
                    stemX + height * 0.26f, stemTop + height * 0.34f,
                    stemX + height * 0.22f, stemTop + height * 0.16f,
                    stemX, stemTop + height * 0.10f
                )
                close()
            },
            color = color
        )
        // Notehead - tilted oval, drawn last so it sits over the stem base
        withTransform({ rotate(-18f, pivot = headC) }) {
            drawOval(
                color = color,
                topLeft = Offset(headC.x - headRx, headC.y - headRy),
                size = Size(headRx * 2f, headRy * 2f)
            )
        }
    }
}
