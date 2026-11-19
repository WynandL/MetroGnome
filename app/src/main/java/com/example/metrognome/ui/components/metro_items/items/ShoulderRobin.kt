package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.PI
import kotlin.math.sin

/**
 * A little robin perched on Metro's left shoulder (the viewer's right), facing in toward his
 * head as if listening. The 15-minute item (added 2026-11-19): the first item after the
 * 5-minute earring, filling the gap the analytics showed (117 users earned the earring in
 * six weeks, 20 the 30-minute watch), so a new player meets a second unlock in their
 * first real session.
 *
 * Drawn in its own unit space (1 = one body unit `u`, origin at the feet, facing left) and
 * scaled by [SCALE], so the shape is designed once and only placed here. The art follows
 * the shading rules the other items use:
 *  - **one silhouette**: head and body are a single smooth path, so there is one antialiased
 *    outer edge and no seam at the neck (two ovals read as a snowman; the first cut did);
 *  - the orange bib, the grey band behind it and the pale belly are painted *inside* that
 *    path under a clip, never as shapes on top of it;
 *  - lit from the upper left like Metro: lighter crown and back on the left, the breast rolling
 *    darker to the lower right, a soft contact shadow where the feet meet the jacket;
 *  - motion is a slow breath only (no per-frame randomness), so the bird is alive in the
 *    scene and still in the unlock preview's zoom.
 *
 * Placement: the jacket oval (drawBody: centre (0, -5.6u), half-axes 1.8u x 2.0u) meets the
 * sleeve's top near x = 1.7u, y = -6.5u. At [SCALE] the beak's tip reaches ~1.32u, clear of the
 * Studio Mic's right edge (~1.30u at y -7.3u).
 */
object ShoulderRobin : MetroItem {

    override val id            = "shoulder_robin"
    override val displayName   = "Robin"
    override val description   = "A little robin who heard Metro keeping time and stayed to listen."
    override val earnedMessage = "Fifteen minutes of steady time! A little robin heard Metro keeping the beat and hopped onto his shoulder to listen. Metro's very first fan."
    override val isBodyAttached = true

    private const val FX = 1.74f
    private const val FY = -6.50f
    private const val SCALE = 0.90f

    // Visual centre of the bird (between beak and tail, mid-height), for taps and the preview.
    override fun hitCenter(u: Float) = Offset((FX + 0.10f * SCALE) * u, (FY - 0.42f * SCALE) * u)
    override fun hitRadius(u: Float) = u * 0.55f

    private val backLight  = Color(0xFFA5835F)
    private val back       = Color(0xFF7D5C3F)
    private val backDark   = Color(0xFF4F3926)
    private val wingDark   = Color(0xFF5A412C)
    private val breastLit  = Color(0xFFF7A766)
    private val breast     = Color(0xFFEA7838)
    private val breastDark = Color(0xFFC25A27)
    private val bandGrey   = Color(0xFF9AA0A6)
    private val belly      = Color(0xFFF4EBDD)
    private val beakTop    = Color(0xFF2E241C)
    private val beakLow    = Color(0xFF4A3B2E)
    private val legColor   = Color(0xFF9A7A5E)
    private val eyeColor   = Color(0xFF120D0A)
    private val shadow     = Color(0xFF000000)

    /** Head and body, one closed curve: forehead, crown, nape, back, rump, belly, breast, chin. */
    private val silhouette = Path().apply {
        moveTo(-0.31f, -0.63f)
        cubicTo(-0.30f, -0.76f, -0.18f, -0.84f, -0.06f, -0.80f)
        cubicTo(0.06f, -0.77f, 0.10f, -0.68f, 0.16f, -0.60f)
        cubicTo(0.26f, -0.50f, 0.36f, -0.45f, 0.42f, -0.38f)
        cubicTo(0.40f, -0.24f, 0.28f, -0.13f, 0.12f, -0.11f)
        cubicTo(-0.06f, -0.08f, -0.24f, -0.17f, -0.30f, -0.30f)
        cubicTo(-0.36f, -0.42f, -0.36f, -0.52f, -0.31f, -0.63f)
        close()
    }

    /** The orange face and breast; its far edge runs behind the eye and down the flank. */
    private val bib = Path().apply {
        moveTo(-0.24f, -0.79f)
        cubicTo(-0.14f, -0.76f, -0.06f, -0.70f, -0.05f, -0.60f)
        cubicTo(-0.03f, -0.48f, -0.02f, -0.36f, -0.10f, -0.24f)
        lineTo(-0.30f, -0.18f)
        lineTo(-0.50f, -0.40f)
        lineTo(-0.44f, -0.80f)
        close()
    }

    private val wing = Path().apply {
        moveTo(-0.01f, -0.57f)
        cubicTo(0.14f, -0.62f, 0.31f, -0.52f, 0.41f, -0.33f)
        lineTo(0.33f, -0.335f)
        lineTo(0.36f, -0.29f)
        cubicTo(0.24f, -0.33f, 0.12f, -0.33f, 0.03f, -0.39f)
        cubicTo(-0.03f, -0.44f, -0.04f, -0.52f, -0.01f, -0.57f)
        close()
    }

    /** Notched tail, behind the body. */
    private val tail = Path().apply {
        moveTo(0.32f, -0.43f)
        cubicTo(0.45f, -0.37f, 0.58f, -0.32f, 0.69f, -0.29f)
        lineTo(0.625f, -0.245f)
        lineTo(0.675f, -0.19f)
        cubicTo(0.55f, -0.20f, 0.42f, -0.23f, 0.29f, -0.27f)
        close()
    }

    private val beakUpper = Path().apply {
        moveTo(-0.305f, -0.665f)
        cubicTo(-0.36f, -0.655f, -0.42f, -0.63f, -0.475f, -0.605f)
        lineTo(-0.30f, -0.592f)
        close()
    }
    private val beakLower = Path().apply {
        moveTo(-0.30f, -0.598f)
        lineTo(-0.445f, -0.600f)
        cubicTo(-0.40f, -0.578f, -0.35f, -0.565f, -0.305f, -0.560f)
        close()
    }

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t = (System.nanoTime() / 1_000_000_000.0).toFloat()
        // A slow breath: the body swells 1.5% from the feet up.
        val breath = 1f + 0.015f * sin(t * 2f * PI.toFloat() / 2.8f)
        val k = u * SCALE

        withTransform({
            translate(FX * u, FY * u)
            scale(k, k, pivot = Offset.Zero)
        }) {
            // Contact shadow on the jacket, under the feet.
            drawOval(
                Brush.radialGradient(listOf(shadow.copy(alpha = 0.45f), Color.Transparent), Offset(0.02f, 0.005f), 0.24f),
                topLeft = Offset(-0.22f, -0.035f), size = Size(0.48f, 0.08f),
            )

            // Legs and toes gripping the shoulder.
            val leg = Stroke(width = 0.03f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun foot(x: Float) {
                drawPath(Path().apply {
                    moveTo(x, -0.13f); lineTo(x - 0.015f, 0f)
                    moveTo(x - 0.015f, 0f); lineTo(x - 0.10f, 0.012f)
                    moveTo(x - 0.015f, 0f); lineTo(x + 0.05f, 0.01f)
                }, legColor, style = leg)
            }
            foot(-0.03f)
            foot(0.09f)

            withTransform({ scale(1f, breath, pivot = Offset(0f, -0.11f)) }) {
                drawPath(
                    tail,
                    Brush.linearGradient(listOf(back, backDark), start = Offset(0.32f, -0.43f), end = Offset(0.68f, -0.19f)),
                )

                drawPath(
                    silhouette,
                    Brush.linearGradient(
                        listOf(backLight, back, backDark),
                        start = Offset(-0.20f, -0.84f), end = Offset(0.42f, -0.10f),
                    ),
                )
                clipPath(silhouette) {
                    // The grey band that frames a robin's bib, just behind it.
                    drawPath(bib, bandGrey.copy(alpha = 0.40f), style = Stroke(width = 0.06f))
                    drawPath(
                        bib,
                        Brush.linearGradient(
                            listOf(breastLit, breast, breastDark),
                            start = Offset(-0.36f, -0.74f), end = Offset(-0.04f, -0.20f),
                        ),
                    )
                    // Pale belly, soft-edged so it blends rather than sits on top.
                    drawOval(
                        Brush.radialGradient(
                            listOf(belly, belly.copy(alpha = 0.85f), belly.copy(alpha = 0f)),
                            Offset(0.04f, -0.13f), 0.22f,
                        ),
                        topLeft = Offset(-0.20f, -0.26f), size = Size(0.48f, 0.26f),
                    )
                    // Rim light on the crown and back only: it fades out by mid-body, or the
                    // belly gets a pale outline that reads as a drawn border.
                    drawPath(
                        silhouette,
                        Brush.verticalGradient(
                            listOf(backLight.copy(alpha = 0.45f), Color.Transparent),
                            startY = -0.84f, endY = -0.45f,
                        ),
                        style = Stroke(width = 0.025f),
                    )
                }

                // Wing: folded on the flank, two primaries showing, a lit upper edge.
                drawPath(
                    wing,
                    Brush.linearGradient(listOf(back, wingDark), start = Offset(0f, -0.58f), end = Offset(0.40f, -0.30f)),
                )
                val quill = Stroke(width = 0.016f, cap = StrokeCap.Round)
                drawLine(backDark.copy(alpha = 0.6f), Offset(0.14f, -0.47f), Offset(0.36f, -0.335f), quill.width, StrokeCap.Round)
                drawLine(backDark.copy(alpha = 0.5f), Offset(0.10f, -0.42f), Offset(0.30f, -0.325f), quill.width, StrokeCap.Round)
                drawPath(Path().apply {
                    moveTo(-0.01f, -0.57f)
                    cubicTo(0.14f, -0.62f, 0.31f, -0.52f, 0.41f, -0.33f)
                }, backLight.copy(alpha = 0.55f), style = Stroke(width = 0.018f, cap = StrokeCap.Round))

                // Beak (upper darker than lower), then the eye with its catch-light.
                drawPath(beakUpper, beakTop)
                drawPath(beakLower, beakLow)
                drawCircle(eyeColor, 0.048f, Offset(-0.185f, -0.665f))
                drawCircle(Color.White.copy(alpha = 0.9f), 0.016f, Offset(-0.198f, -0.680f))
            }
        }
    }
}
