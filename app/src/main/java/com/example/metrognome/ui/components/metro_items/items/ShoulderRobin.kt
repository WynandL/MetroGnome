package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * A little robin perched on Metro's left shoulder (the viewer's right), facing in toward his
 * head as if listening. The 15-minute item (added 2026-11-19): the first item after the
 * 5-minute earring, filling the gap the analytics showed (117 users earned the earring in
 * six weeks, 20 the 30-minute watch), so a new player meets a second unlock in their
 * first real session.
 *
 * Placement: the jacket oval (drawBody: centre (0, -5.6u), half-axes 1.8u x 2.0u) is at
 * y = -6.5u at x = 1.6u. The feet sit there; the bird spans about x 1.38..2.06u, clear of the
 * Studio Mic's right edge (~1.30u) and of the right arm, which starts below the shoulder.
 *
 * Motion: a small head tilt every few seconds and a breath in the body, from wall-clock
 * time (fixed phases, no randomness in draw). Lit from the upper left like everything on
 * Metro: a lighter crown and back on the left, the breast's warm orange rolling darker to
 * the right.
 */
object ShoulderRobin : MetroItem {

    override val id            = "shoulder_robin"
    override val displayName   = "Robin"
    override val description   = "A little robin who heard Metro keeping time and stayed to listen."
    override val earnedMessage = "Fifteen minutes of steady time! A little robin heard Metro keeping the beat and hopped onto his shoulder to listen. Metro's very first fan."
    override val isBodyAttached = true

    // Feet on the shoulder line; the body's visual centre is ~0.38u above them.
    private const val FX = 1.68f
    private const val FY = -6.50f

    override fun hitCenter(u: Float) = Offset(FX * u, (FY - 0.38f) * u)
    override fun hitRadius(u: Float) = u * 0.50f

    private val back       = Color(0xFF7A5A3E)   // warm olive-brown
    private val backLight  = Color(0xFF9C7A58)
    private val backDark   = Color(0xFF4E3826)
    private val breast     = Color(0xFFE8763A)   // robin orange
    private val breastLit  = Color(0xFFF39A5C)
    private val breastDark = Color(0xFFB9552A)
    private val belly      = Color(0xFFF1E6D6)
    private val beak       = Color(0xFF3A2A1E)
    private val legColor   = Color(0xFF8A6A50)
    private val eyeColor   = Color(0xFF15100C)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t = (System.nanoTime() / 1_000_000_000.0).toFloat()
        val fx = FX * u
        val fy = FY * u

        // Breath: a 1.5% swell on a slow cycle. Tilt: the head turns up for ~0.6 s every 4 s.
        val breath = 1f + 0.015f * sin(t * 2f * PI.toFloat() / 2.6f)
        val cycle = (t % 4f) / 4f
        val tiltPhase = if (cycle in 0.70f..0.85f) sin((cycle - 0.70f) / 0.15f * PI.toFloat()) else 0f
        val tilt = -14f * tiltPhase

        // Legs: two short strokes into the shoulder.
        val legW = 0.045f * u
        drawLine(legColor, Offset(fx - 0.05f * u, fy - 0.16f * u), Offset(fx - 0.08f * u, fy), legW, StrokeCap.Round)
        drawLine(legColor, Offset(fx + 0.07f * u, fy - 0.16f * u), Offset(fx + 0.06f * u, fy), legW, StrokeCap.Round)

        // Tail: a tapered wedge out to the right and down, behind the body.
        val tail = Path().apply {
            moveTo(fx + 0.18f * u, fy - 0.36f * u)
            lineTo(fx + 0.56f * u, fy - 0.20f * u)
            lineTo(fx + 0.52f * u, fy - 0.10f * u)
            lineTo(fx + 0.12f * u, fy - 0.22f * u)
            close()
        }
        drawPath(tail, backDark)

        // Body: a plump egg, breast forward (to the left, toward Metro's head).
        val bw = 0.64f * u * breath
        val bh = 0.50f * u * breath
        val bodyCenter = Offset(fx + 0.02f * u, fy - 0.38f * u)
        val bodyRect = Rect(Offset(bodyCenter.x - bw / 2f, bodyCenter.y - bh / 2f), Size(bw, bh))
        val body = Path().apply { addOval(bodyRect) }
        drawPath(
            body,
            Brush.linearGradient(
                listOf(backLight, back, backDark),
                start = Offset(bodyRect.left, bodyRect.top),
                end = Offset(bodyRect.right, bodyRect.bottom),
            ),
        )
        // Breast and belly, clipped to the body so the edge is the body's one edge.
        clipPath(body) {
            drawOval(
                Brush.linearGradient(
                    listOf(breastLit, breast, breastDark),
                    start = Offset(bodyRect.left, bodyRect.top),
                    end = Offset(bodyRect.left + bw * 0.7f, bodyRect.bottom),
                ),
                topLeft = Offset(bodyRect.left - bw * 0.10f, bodyRect.top + bh * 0.08f),
                size = Size(bw * 0.62f, bh * 0.86f),
            )
            drawOval(
                belly,
                topLeft = Offset(bodyRect.left + bw * 0.20f, bodyRect.top + bh * 0.66f),
                size = Size(bw * 0.46f, bh * 0.40f),
            )
            // Wing: a darker crescent along the back.
            drawOval(
                backDark.copy(alpha = 0.55f),
                topLeft = Offset(bodyRect.left + bw * 0.46f, bodyRect.top + bh * 0.18f),
                size = Size(bw * 0.56f, bh * 0.52f),
            )
            drawArc(
                backLight.copy(alpha = 0.6f), startAngle = 200f, sweepAngle = 110f, useCenter = false,
                topLeft = Offset(bodyRect.left + bw * 0.50f, bodyRect.top + bh * 0.22f),
                size = Size(bw * 0.48f, bh * 0.40f),
                style = Stroke(width = 0.03f * u),
            )
        }

        // Head, tilting about the neck.
        val neck = Offset(bodyCenter.x - bw * 0.30f, bodyCenter.y - bh * 0.30f)
        rotate(tilt, pivot = neck) {
            val hr = 0.19f * u
            val hc = Offset(neck.x - 0.02f * u, neck.y - 0.12f * u)
            drawCircle(
                Brush.radialGradient(listOf(backLight, back), Offset(hc.x - hr * 0.3f, hc.y - hr * 0.4f), hr * 1.4f),
                hr, hc,
            )
            // Orange face and throat, the robin's mark, on the side facing Metro.
            clipPath(Path().apply { addOval(Rect(hc, hr)) }) {
                drawCircle(breast, hr * 0.85f, Offset(hc.x - hr * 0.45f, hc.y + hr * 0.45f))
            }
            // Beak, pointing left.
            val beakPath = Path().apply {
                moveTo(hc.x - hr * 0.88f, hc.y - hr * 0.05f)
                lineTo(hc.x - hr * 1.55f, hc.y + hr * 0.08f)
                lineTo(hc.x - hr * 0.86f, hc.y + hr * 0.25f)
                close()
            }
            drawPath(beakPath, beak)
            // Eye with a catch-light.
            val eye = Offset(hc.x - hr * 0.28f, hc.y - hr * 0.18f)
            drawCircle(eyeColor, max(hr * 0.17f, 0.6f), eye)
            drawCircle(Color.White.copy(alpha = 0.85f), max(hr * 0.06f, 0.4f), Offset(eye.x - hr * 0.05f, eye.y - hr * 0.06f))
        }
    }
}
