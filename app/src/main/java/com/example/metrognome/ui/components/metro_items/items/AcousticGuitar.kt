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
 * An acoustic guitar propped against the forest tree, as though Metro left it there after
 * playing earlier. The reward for naming twenty-five different chords in the Chord
 * Finder: the feature was built for a guitarist, and once someone has found that many
 * chords they have earned the instrument they were finding them on.
 *
 * Scene position: its foot [FOOT_FROM_TRUNK] right of the trunk's centre line (the trunk
 * is width-anchored at `size.width * 0.10`, see [ForestTree]), standing on the ground
 * line and leaning [LEAN_DEG] back towards the trunk, so the body sits a little in front
 * of the tree's base and the neck rests up its face. Drawn after the tree in the registry,
 * hence in front of it. It first stood at cx + 3.9u between Metro and the torch post,
 * leaning on nothing, and the dev asked for it under the tree instead. Without the tree
 * (a guitar can be earned in days, the tree takes a month) it simply leans on the night,
 * as it did before. The whole instrument is drawn in an upright local frame and then
 * rotated about its ground contact point, so every part leans together and the strings
 * stay parallel to the neck.
 *
 * Scale: the instrument is drawn in its own unit g = [SCALE] x u. At full u it stood 5.5u
 * tall against Metro's ~11u, which put it beside him on the same plane; at 0.6 it is the
 * music stand's size and sits back at the tree with it. Everything below is in g.
 *
 * Geometry (upright, y up from the ground contact at 0):
 *   Body    0 .. -2.9u   classical proportions on a body length L = 2.9u: lower bout
 *                        0.72L wide at 0.29L up, waist 0.48L at 0.55L, upper bout 0.57L at
 *                        0.78L, with a flat-ish bottom and top. The first cut was two
 *                        near-circles with a deep pinch, and read as a figure of eight.
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

    private const val TRUNK_X_FRAC   = 0.10f   // ForestTree's trunk anchor, as a fraction of canvas width
    private const val SCALE          = 0.6f    // guitar units per scene unit: g = SCALE * u
    private const val FOOT_FROM_TRUNK = 0.55f  // scene units right of the trunk's centre line
    private const val LEAN_DEG       = -10f    // anticlockwise: the headstock leans left, onto the trunk

    // Trunk ≈ cx - 3.1u on a typical phone (ForestTree's own approximation); the leaning
    // guitar's visual centre is a little left of its foot.
    override fun hitCenter(u: Float) = Offset((-3.1f + FOOT_FROM_TRUNK - 0.2f) * u, -1.5f * u)
    override fun hitRadius(u: Float) = u * 1.1f

    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * TRUNK_X_FRAC + (FOOT_FROM_TRUNK - 0.2f) * u, baseY - 1.65f * u)
    override fun previewRadius(u: Float) = u * 2.0f

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
        val px = size.width * TRUNK_X_FRAC + FOOT_FROM_TRUNK * u
        val groundY = baseY
        val g = u * SCALE

        // Ground contact shadow, drawn unrotated: the shadow lies on the ground.
        drawOval(
            color = shadowCol,
            topLeft = Offset(px - 0.95f * g, groundY - 0.14f * g),
            size = Size(1.9f * g, 0.28f * g),
        )

        rotate(degrees = LEAN_DEG, pivot = Offset(px, groundY)) {
            drawGuitar(g, px, groundY)
        }
    }

    private fun DrawScope.drawGuitar(u: Float, px: Float, gy: Float) {
        // ── Body: classical outline, mirrored about the centre line ──────────
        // Half-widths and heights from a real classical guitar (370 / 240 / 280 mm bouts
        // on a 490 mm body). Every control point at a bout or the waist is vertical, so
        // the curve peaks there rather than pinching; the bottom and top are flat-ish.
        val lowerW = 1.05f * u; val lowerY = gy - 0.85f * u
        val waistW = 0.70f * u; val waistY = gy - 1.60f * u
        val upperW = 0.82f * u; val upperY = gy - 2.25f * u
        val topY = gy - 2.90f * u
        val body = Path().apply {
            moveTo(px, gy)
            cubicTo(px + 0.62f * u, gy, px + lowerW, gy - 0.40f * u, px + lowerW, lowerY)   // bottom to lower bout
            cubicTo(px + lowerW, gy - 1.25f * u, px + waistW, gy - 1.38f * u, px + waistW, waistY)  // in to the waist
            cubicTo(px + waistW, gy - 1.85f * u, px + upperW, gy - 2.00f * u, px + upperW, upperY)  // out to the upper bout
            cubicTo(px + upperW, gy - 2.58f * u, px + 0.50f * u, topY, px, topY)              // round to the top
            cubicTo(px - 0.50f * u, topY, px - upperW, gy - 2.58f * u, px - upperW, upperY)
            cubicTo(px - upperW, gy - 2.00f * u, px - waistW, gy - 1.85f * u, px - waistW, waistY)
            cubicTo(px - waistW, gy - 1.38f * u, px - lowerW, gy - 1.25f * u, px - lowerW, lowerY)
            cubicTo(px - lowerW, gy - 0.40f * u, px - 0.62f * u, gy, px, gy)
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

        // ── Sound hole with rosette: just above the waist, 0.17L across ──────
        val holeC = Offset(px, gy - 1.82f * u)
        drawCircle(rosette, radius = 0.33f * u, center = holeC)
        drawCircle(holeDark, radius = 0.25f * u, center = holeC)

        // ── Bridge: a classical bridge is wide, most of the lower bout ───────
        val bridgeY = gy - 0.85f * u
        drawRoundRect(
            color = ItemPalette.woodBrown,
            topLeft = Offset(px - 0.42f * u, bridgeY - 0.06f * u),
            size = Size(0.84f * u, 0.13f * u),
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
