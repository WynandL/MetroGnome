package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.ItemPalette

/**
 * A wooden music stand with parchment sheet music — placed left of Metro as a
 * practitioner's reward. Unlocked after 5 completed practice sessions.
 *
 * Scene position: size.width * 0.32f — between ForestTree (10%) and Metro (~50%).
 * Background item; drawn before Metro so he stands in front of it.
 *
 * Geometry (all relative to px = size.width*0.32f, groundY = baseY):
 *   Two legs from a junction 0.45u above ground, spreading ±1u / +0.75u.
 *   Post from junction to 4.0u above ground.
 *   Height-adjuster ring detail at 1.8u above ground.
 *   Desk: 1.40u wide × 1.85u tall, containing a parchment page with a 5-line
 *   staff and four quarter notes. A 0.20u lip at the base holds the pages.
 */
object MusicStand : MetroItem {

    override val id              = "music_stand"
    override val displayName     = "Music Stand"
    override val description     = "A proper music stand that appeared when Metro got serious about practice. Sheet music never lies."
    override val unlockCondition = "Complete 5 practice sessions"
    override val earnedMessage   = "Five sessions in! Metro set up his practice stand. It appeared overnight, as if the forest knew dedication deserved a reward."
    override val isBodyAttached  = false

    // Stand is ~18% left of canvas centre — approximation for tap detection on typical phones
    override fun hitCenter(u: Float) = Offset(-2.1f * u, -2.2f * u)
    override fun hitRadius(u: Float) = u * 1.5f

    // px ≈ canvasW*0.32f; visual centre at mid-desk height ≈ 2.2u above ground
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.32f, baseY - 2.2f * u)
    override fun previewRadius(u: Float) = u * 2.5f

    private val paperWhite = Color(0xFFFBF6EC)
    private val paperWarm  = Color(0xFFEADCBE)
    private val paperEdge  = Color(0xFFD4C9A0)
    private val inkColor   = Color(0xFF1C1C1C)
    private val brassLight = Color(0xFFE8C66A)
    private val brassMid   = Color(0xFFB8902F)
    private val shadowCol  = Color(0x33000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val px       = size.width * 0.32f
        val groundY  = baseY

        val postW    = 0.12f * u
        val legJoinY = groundY - 0.45f * u
        val postTopY = groundY - 4.0f * u

        val deskW    = 1.40f * u
        val deskH    = 1.85f * u
        val lipH     = 0.20f * u
        val deskTopY = postTopY
        val deskBotY = deskTopY + deskH

        // ── Ground contact shadow ────────────────────────────────────────────────
        drawOval(
            color = shadowCol,
            topLeft = Offset(px - 1.05f * u, groundY - 0.12f * u),
            size = Size(2.0f * u, 0.26f * u)
        )

        // ── Legs ────────────────────────────────────────────────────────────────
        drawLine(ItemPalette.woodBrown, Offset(px, legJoinY), Offset(px - 1.00f * u, groundY), strokeWidth = postW * 1.1f, cap = StrokeCap.Round)
        drawLine(ItemPalette.woodBrown, Offset(px, legJoinY), Offset(px + 0.75f * u, groundY), strokeWidth = postW * 1.1f, cap = StrokeCap.Round)

        // ── Post ────────────────────────────────────────────────────────────────
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(ItemPalette.woodBrown, ItemPalette.woodLight, ItemPalette.woodBrown),
                startX = px - postW,
                endX   = px + postW
            ),
            start       = Offset(px, legJoinY),
            end         = Offset(px, postTopY),
            strokeWidth = postW * 2f,
            cap         = StrokeCap.Butt
        )

        // Height-adjuster ring detail
        drawCircle(
            color  = ItemPalette.woodBrown,
            radius = postW * 1.6f,
            center = Offset(px, groundY - 1.8f * u),
            style  = Stroke(width = postW * 0.7f)
        )

        // Brass adjuster knob with a glint
        val knobC = Offset(px + postW * 1.4f, groundY - 1.8f * u)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(brassLight, brassMid),
                center = Offset(knobC.x - postW * 0.2f, knobC.y - postW * 0.2f),
                radius = postW * 1.1f
            ),
            radius = postW * 0.70f,
            center = knobC
        )
        drawSparkle(center = Offset(knobC.x - postW * 0.15f, knobC.y - postW * 0.15f), radius = postW * 0.5f, color = Color.White.copy(alpha = 0.85f))

        // ── Desk drop shadow ───────────────────────────────────────────────────
        drawRoundRect(
            color = shadowCol,
            topLeft = Offset(px - deskW / 2f + 0.06f * u, deskTopY + 0.07f * u),
            size = Size(deskW, deskH),
            cornerRadius = CornerRadius(postW * 0.4f)
        )

        // ── Desk back panel ──────────────────────────────────────────────────────
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(ItemPalette.woodBrown, ItemPalette.woodLight, ItemPalette.woodBrown),
                startX = px - deskW / 2f,
                endX   = px + deskW / 2f
            ),
            topLeft      = Offset(px - deskW / 2f, deskTopY),
            size         = Size(deskW, deskH),
            cornerRadius = CornerRadius(postW * 0.4f)
        )

        // Top-edge highlight along the desk rim
        drawLine(
            color = ItemPalette.woodLight.copy(alpha = 0.7f),
            start = Offset(px - deskW / 2f + postW * 0.6f, deskTopY + postW * 0.35f),
            end   = Offset(px + deskW / 2f - postW * 0.6f, deskTopY + postW * 0.35f),
            strokeWidth = postW * 0.30f,
            cap = StrokeCap.Round
        )

        // ── Parchment sheet ──────────────────────────────────────────────────────
        val sheetM = 0.11f * u
        val sheetW = deskW - sheetM * 2f
        val sheetH = deskH - sheetM * 2f - lipH
        val sheetX = px - sheetW / 2f
        val sheetY = deskTopY + sheetM

        drawRoundRect(
            brush        = Brush.verticalGradient(
                colors = listOf(paperWhite, paperWarm),
                startY = sheetY,
                endY   = sheetY + sheetH
            ),
            topLeft      = Offset(sheetX, sheetY),
            size         = Size(sheetW, sheetH),
            cornerRadius = CornerRadius(postW * 0.3f)
        )
        drawRoundRect(
            color        = paperEdge,
            topLeft      = Offset(sheetX, sheetY),
            size         = Size(sheetW, sheetH),
            cornerRadius = CornerRadius(postW * 0.3f),
            style        = Stroke(width = postW * 0.25f)
        )
        // Soft inner shadow where the page meets the lip
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, shadowCol),
                startY = sheetY + sheetH * 0.74f,
                endY   = sheetY + sheetH
            ),
            topLeft = Offset(sheetX, sheetY + sheetH * 0.72f),
            size    = Size(sheetW, sheetH * 0.28f),
            cornerRadius = CornerRadius(postW * 0.3f)
        )

        // 5-line musical staff
        val staffTop = sheetY + sheetH * 0.18f
        val staffBot = sheetY + sheetH * 0.72f
        val spacing  = (staffBot - staffTop) / 4f
        val lx0      = sheetX + sheetW * 0.10f
        val lx1      = sheetX + sheetW * 0.90f
        for (i in 0..4) {
            drawLine(
                color       = inkColor,
                start       = Offset(lx0, staffTop + i * spacing),
                end         = Offset(lx1, staffTop + i * spacing),
                strokeWidth = postW * 0.20f
            )
        }

        // 4/4 time signature at the start of the staff
        drawTimeSignature(sheetX + sheetW * 0.14f, staffTop, spacing)

        // A little phrase: quarter, beamed eighth pair, quarter
        val noteR   = spacing * 0.36f
        val stemLen = spacing * 2.0f
        val nX = floatArrayOf(0.36f, 0.52f, 0.66f, 0.80f).map { sheetX + sheetW * it }
        val nY = floatArrayOf(3.0f, 2.5f, 1.5f, 2.0f).map { staffTop + spacing * it }
        for (i in 0..3) {
            drawOval(
                color   = inkColor,
                topLeft = Offset(nX[i] - noteR, nY[i] - noteR * 0.72f),
                size    = Size(noteR * 2f, noteR * 1.44f)
            )
            val sx = nX[i] + noteR * 0.82f
            drawLine(
                color       = inkColor,
                start       = Offset(sx, nY[i]),
                end         = Offset(sx, nY[i] - stemLen),
                strokeWidth = postW * 0.22f,
                cap         = StrokeCap.Round
            )
        }
        // Beam joining the eighth-note pair (notes 1 & 2)
        drawLine(
            color       = inkColor,
            start       = Offset(nX[1] + noteR * 0.82f, nY[1] - stemLen),
            end         = Offset(nX[2] + noteR * 0.82f, nY[2] - stemLen),
            strokeWidth = spacing * 0.30f,
            cap         = StrokeCap.Butt
        )

        // ── Desk lip — small ledge to hold the pages ────────────────────────────
        drawRoundRect(
            color        = ItemPalette.woodLight,
            topLeft      = Offset(px - deskW / 2f, deskBotY - lipH),
            size         = Size(deskW, lipH),
            cornerRadius = CornerRadius(postW * 0.35f)
        )
    }

    /** A stacked 4/4 time signature built from simple strokes. */
    private fun DrawScope.drawTimeSignature(x: Float, staffTop: Float, s: Float) {
        val w  = s * 0.85f
        val h  = s * 1.8f
        val sw = s * 0.22f
        drawDigit4(x, staffTop + 0.1f * s, w, h, sw)
        drawDigit4(x, staffTop + 2.1f * s, w, h, sw)
    }

    private fun DrawScope.drawDigit4(x: Float, top: Float, w: Float, h: Float, sw: Float) {
        drawLine(inkColor, Offset(x + 0.55f * w, top), Offset(x, top + 0.62f * h), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(inkColor, Offset(x, top + 0.62f * h), Offset(x + 0.95f * w, top + 0.62f * h), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(inkColor, Offset(x + 0.62f * w, top), Offset(x + 0.62f * w, top + h), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
