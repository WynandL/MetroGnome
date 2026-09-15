package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.ItemPalette

/**
 * An acoustic guitar leaning at Metro's right, headstock tilted a few degrees away from
 * him as though propped against the night. The reward for naming twenty-five different
 * chords in the Chord Finder: the feature was built for a guitarist, and once someone has
 * found that many chords they have earned the instrument they were finding them on.
 *
 * Scene position: cx + 3.9u, standing on the ground line, between Metro (~50%) and the
 * torch post (82%). The whole instrument is drawn in an upright local frame and then
 * rotated by [LEAN_DEG] about its ground contact point, so every part leans together and
 * the strings stay parallel to the neck.
 *
 * Geometry (upright, y up from the ground contact at 0):
 *   Body    0 .. -2.9u   a figure-of-eight outline, lower bout wider than the upper
 *   Neck    -2.85u .. -4.9u, tapering from 0.30u to 0.24u wide, six frets
 *   Head    -4.9u .. -5.5u, 0.40u wide, three tuners each side
 *   Strings bridge (-0.85u) to nut (-4.9u)
 */
object AcousticGuitar : MetroItem {

    override val id             = "acoustic_guitar"
    override val displayName    = "Acoustic Guitar"
    override val description    = "Metro's own acoustic guitar, propped at his side once you had named twenty-five different chords."
    override val earnedMessage  = "Twenty-five chords named! Metro fetched his old acoustic from the shed. He mostly knows the cowboy chords, but with you around, that number is going up."
    override val isBodyAttached = false

    private const val OFFSET_X = 3.9f     // units right of Metro's centre
    private const val LEAN_DEG = 7f       // clockwise: the headstock tilts away from Metro

    override fun hitCenter(u: Float) = Offset(OFFSET_X * u, -2.3f * u)
    override fun hitRadius(u: Float) = u * 1.6f

    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.5f + OFFSET_X * u + 0.3f * u, baseY - 2.7f * u)
    override fun previewRadius(u: Float) = u * 3.2f

    // Spruce top and its shading; the sides and neck use the shared wood duo.
    private val topHoney    = Color(0xFFE2B36A)
    private val topShade    = Color(0xFFB98A44)
    private val topEdge     = Color(0xFF7A5424)
    private val holeDark    = Color(0xFF1A110A)
    private val rosette     = Color(0xFF3B2412)
    private val stringSilver = Color(0xFFD8D4E0)
    private val fretSilver  = Color(0xFFB0AAB8)
    private val pegPale     = Color(0xFFEDE6D6)
    private val shadowCol   = Color(0x33000000)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val px = cx + OFFSET_X * u
        val groundY = baseY

        // Ground contact shadow, drawn unrotated: the shadow lies on the ground.
        drawOval(
            color = shadowCol,
            topLeft = Offset(px - 0.95f * u, groundY - 0.14f * u),
            size = Size(1.9f * u, 0.28f * u),
        )

        rotate(degrees = LEAN_DEG, pivot = Offset(px, groundY)) {
            drawGuitar(u, px, groundY)
        }
    }

    private fun DrawScope.drawGuitar(u: Float, px: Float, gy: Float) {
        // ── Body: figure of eight, mirrored about the centre line ────────────
        val body = Path().apply {
            moveTo(px, gy)
            cubicTo(px + 1.00f * u, gy, px + 1.00f * u, gy - 1.20f * u, px + 0.58f * u, gy - 1.55f * u)   // lower bout to waist
            cubicTo(px + 0.88f * u, gy - 1.80f * u, px + 0.82f * u, gy - 2.90f * u, px, gy - 2.90f * u)   // upper bout to top
            cubicTo(px - 0.82f * u, gy - 2.90f * u, px - 0.88f * u, gy - 1.80f * u, px - 0.58f * u, gy - 1.55f * u)
            cubicTo(px - 1.00f * u, gy - 1.20f * u, px - 1.00f * u, gy, px, gy)
            close()
        }
        // Top-lit from the upper left, like everything else in the scene.
        drawPath(
            body,
            brush = Brush.linearGradient(
                colors = listOf(topHoney, topShade),
                start = Offset(px - 0.9f * u, gy - 2.9f * u),
                end = Offset(px + 0.9f * u, gy),
            ),
        )
        drawPath(body, color = topEdge, style = Stroke(width = 0.05f * u))

        // ── Sound hole with rosette ──────────────────────────────────────────
        val holeC = Offset(px, gy - 1.78f * u)
        drawCircle(rosette, radius = 0.36f * u, center = holeC)
        drawCircle(holeDark, radius = 0.28f * u, center = holeC)

        // ── Bridge ───────────────────────────────────────────────────────────
        val bridgeY = gy - 0.85f * u
        drawRoundRect(
            color = ItemPalette.woodBrown,
            topLeft = Offset(px - 0.36f * u, bridgeY - 0.06f * u),
            size = Size(0.72f * u, 0.13f * u),
            cornerRadius = CornerRadius(0.04f * u),
        )

        // ── Neck and headstock ───────────────────────────────────────────────
        val neckBottomY = gy - 2.85f * u
        val nutY = gy - 4.90f * u
        val neck = Path().apply {
            moveTo(px - 0.15f * u, neckBottomY)
            lineTo(px + 0.15f * u, neckBottomY)
            lineTo(px + 0.12f * u, nutY)
            lineTo(px - 0.12f * u, nutY)
            close()
        }
        drawPath(
            neck,
            brush = Brush.horizontalGradient(
                colors = listOf(ItemPalette.woodLight, ItemPalette.woodBrown),
                startX = px - 0.15f * u, endX = px + 0.15f * u,
            ),
        )
        val headH = 0.60f * u
        drawRoundRect(
            color = ItemPalette.woodBrown,
            topLeft = Offset(px - 0.20f * u, nutY - headH),
            size = Size(0.40f * u, headH),
            cornerRadius = CornerRadius(0.08f * u),
        )
        // Nut: the pale bar where neck meets head.
        drawLine(pegPale, Offset(px - 0.13f * u, nutY), Offset(px + 0.13f * u, nutY), strokeWidth = 0.05f * u)
        // Tuning pegs, three a side.
        for (i in 0 until 3) {
            val py = nutY - headH * (0.22f + 0.28f * i)
            drawCircle(pegPale, radius = 0.045f * u, center = Offset(px - 0.24f * u, py))
            drawCircle(pegPale, radius = 0.045f * u, center = Offset(px + 0.24f * u, py))
        }

        // ── Frets ────────────────────────────────────────────────────────────
        for (i in 1..6) {
            val frac = i / 7f
            val fy = neckBottomY + (nutY - neckBottomY) * frac
            val halfW = (0.15f - 0.03f * frac) * u
            drawLine(fretSilver, Offset(px - halfW, fy), Offset(px + halfW, fy), strokeWidth = 0.025f * u)
        }

        // ── Strings: bridge to nut, fanning slightly wider at the bridge ─────
        for (i in 0 until 6) {
            val t = (i - 2.5f) / 2.5f
            drawLine(
                color = stringSilver.copy(alpha = 0.85f),
                start = Offset(px + t * 0.16f * u, bridgeY),
                end = Offset(px + t * 0.10f * u, nutY),
                strokeWidth = 0.018f * u,
                cap = StrokeCap.Round,
            )
        }
    }
}
