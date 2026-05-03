package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object TinkerbellFairy : MetroItem {

    override val id              = "tinkerbell_fairy"
    override val displayName     = "Tinkerbell"
    override val description     = "A luminous fairy who has always been listening from the treetops."
    override val unlockCondition = "Play the metronome for 6 hours total"
    override val earnedMessage   = "Six hours of music! The forest held its breath — and then a shimmer. She's been watching from the treetops this whole time. Tonight she finally decided to show herself. Tinkerbell dances with every beat."
    override val isBodyAttached  = false

    // Upper-left sky — tight zoom captures the full figure comfortably
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.22f, baseY - u * 6.5f)
    override fun previewRadius(u: Float) = u * 2.4f

    // ── Palette ──────────────────────────────────────────────────────────────
    private val skinPeach   = Color(0xFFFFC4A0)
    private val skinShade   = Color(0xFFDD9A6A)
    private val hairGold    = Color(0xFFFFCC30)
    private val hairShade   = Color(0xFFC89010)
    private val hairLight   = Color(0xFFFFE880)
    private val dressGreen  = Color(0xFF70CC40)
    private val dressMid    = Color(0xFF52A030)
    private val dressDark   = Color(0xFF3A7020)
    private val dressLight  = Color(0xFF9AE860)
    private val shoeGreen   = Color(0xFF2A5818)
    private val eyeIris     = Color(0xFF4A7C28)
    private val eyePupil    = Color(0xFF1E1008)
    private val wingBase    = Color(0x88C8F0FF)
    private val wingShimmer = Color(0x66FFFFFF)
    private val wingVein    = Color(0x99A0D0EE)
    private val dustGold    = Color(0xFFFFE050)
    private val dustWhite   = Color(0xEEFFFFFF)
    private val glowWarm    = Color(0x44FFE580)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t       = (System.currentTimeMillis() % 200_000L) / 1000f
        val floatY  = sin(t * 1.10f) * u * 1.0f
        val floatX  = cos(t * 0.68f) * u * 0.52f
        val flutter = abs(sin(t * 8.8f))
        val glow    = 0.55f + 0.45f * sin(t * 2.1f)

        val fcx = size.width * 0.22f + floatX
        val fcy = baseY - u * 6.5f + floatY

        drawCircle(
            brush  = Brush.radialGradient(
                colors = listOf(glowWarm, Color.Transparent),
                center = Offset(fcx, fcy),
                radius = u * 2.8f
            ),
            radius = u * 2.8f,
            center = Offset(fcx, fcy),
            alpha  = glow
        )

        drawDust(u, fcx, fcy, t)
        drawWings(u, fcx, fcy, flutter)
        drawBody(u, fcx, fcy)
        drawHead(u, fcx, fcy)
    }

    // ── Fairy dust sparkle trail ──────────────────────────────────────────────
    private fun DrawScope.drawDust(u: Float, fcx: Float, fcy: Float, t: Float) {
        for (i in 0 until 8) {
            val phase = (t * 0.75f + i * 0.26f) % 1f
            val dx    = fcx + cos(t * 0.68f + i * 1.15f) * u * 0.55f * (1f - phase * 0.4f) - phase * u * 0.3f
            val dy    = fcy + sin(t * 1.10f + i * 0.90f) * u * 0.75f * (1f - phase * 0.3f) + phase * u * 0.55f
            val r     = u * (0.055f + 0.065f * (1f - phase))
            val a     = (1f - phase) * 0.9f
            drawCircle(dustGold,  radius = r * 1.8f, center = Offset(dx, dy), alpha = a * 0.38f)
            drawCircle(dustWhite, radius = r,         center = Offset(dx, dy), alpha = a)
        }
    }

    // ── Four translucent wings behind the body ────────────────────────────────
    private fun DrawScope.drawWings(u: Float, fcx: Float, fcy: Float, flutter: Float) {
        val ax = fcx
        val ay = fcy - u * 0.35f
        val ws = 0.65f + 0.35f * flutter  // horizontal spread: beats inward

        val ulWing = Path().apply {
            moveTo(ax - u * 0.06f, ay)
            cubicTo(ax - u * 0.28f, ay - u * 0.78f, ax - u * 1.40f * ws, ay - u * 0.90f, ax - u * 1.58f * ws, ay - u * 0.36f)
            cubicTo(ax - u * 1.32f * ws, ay + u * 0.10f, ax - u * 0.36f, ay + u * 0.04f, ax - u * 0.06f, ay)
            close()
        }
        val urWing = Path().apply {
            moveTo(ax + u * 0.06f, ay)
            cubicTo(ax + u * 0.28f, ay - u * 0.78f, ax + u * 1.40f * ws, ay - u * 0.90f, ax + u * 1.58f * ws, ay - u * 0.36f)
            cubicTo(ax + u * 1.32f * ws, ay + u * 0.10f, ax + u * 0.36f, ay + u * 0.04f, ax + u * 0.06f, ay)
            close()
        }
        val llWing = Path().apply {
            moveTo(ax - u * 0.08f, ay + u * 0.06f)
            cubicTo(ax - u * 0.22f, ay + u * 0.50f, ax - u * 0.86f * ws, ay + u * 0.70f, ax - u * 0.80f * ws, ay + u * 0.26f)
            cubicTo(ax - u * 0.58f * ws, ay + u * 0.05f, ax - u * 0.20f, ay - u * 0.02f, ax - u * 0.08f, ay + u * 0.06f)
            close()
        }
        val lrWing = Path().apply {
            moveTo(ax + u * 0.08f, ay + u * 0.06f)
            cubicTo(ax + u * 0.22f, ay + u * 0.50f, ax + u * 0.86f * ws, ay + u * 0.70f, ax + u * 0.80f * ws, ay + u * 0.26f)
            cubicTo(ax + u * 0.58f * ws, ay + u * 0.05f, ax + u * 0.20f, ay - u * 0.02f, ax + u * 0.08f, ay + u * 0.06f)
            close()
        }

        for (wing in listOf(ulWing, urWing, llWing, lrWing)) {
            drawPath(wing, color = wingBase)
            drawPath(wing, color = wingShimmer, alpha = 0.55f)
        }

        // Veins on upper pair
        val vStroke = Stroke(width = u * 0.032f, cap = StrokeCap.Round)
        for (sign in listOf(-1f, 1f)) {
            drawPath(
                Path().apply {
                    moveTo(ax + sign * u * 0.06f, ay)
                    cubicTo(ax + sign * u * 0.48f, ay - u * 0.46f, ax + sign * u * 1.02f * ws, ay - u * 0.68f, ax + sign * u * 1.42f * ws, ay - u * 0.30f)
                },
                color = wingVein, style = vStroke
            )
            drawPath(
                Path().apply {
                    moveTo(ax + sign * u * 0.10f, ay - u * 0.20f)
                    cubicTo(ax + sign * u * 0.52f, ay - u * 0.68f, ax + sign * u * 1.08f * ws, ay - u * 0.78f, ax + sign * u * 1.32f * ws, ay - u * 0.54f)
                },
                color = wingVein, style = vStroke, alpha = 0.60f
            )
        }
    }

    // ── Dress, arms, legs ─────────────────────────────────────────────────────
    private fun DrawScope.drawBody(u: Float, fcx: Float, fcy: Float) {
        // Bodice
        val bodice = Path().apply {
            moveTo(fcx - u * 0.23f, fcy - u * 0.42f)
            cubicTo(fcx - u * 0.28f, fcy - u * 0.18f, fcx - u * 0.26f, fcy + u * 0.08f, fcx - u * 0.18f, fcy + u * 0.26f)
            lineTo(fcx + u * 0.18f, fcy + u * 0.26f)
            cubicTo(fcx + u * 0.26f, fcy + u * 0.08f, fcx + u * 0.28f, fcy - u * 0.18f, fcx + u * 0.23f, fcy - u * 0.42f)
            close()
        }
        drawPath(bodice, color = dressMid)
        drawPath(
            Path().apply {
                moveTo(fcx - u * 0.06f, fcy - u * 0.40f)
                cubicTo(fcx - u * 0.09f, fcy - u * 0.12f, fcx - u * 0.07f, fcy + u * 0.10f, fcx - u * 0.03f, fcy + u * 0.24f)
                lineTo(fcx + u * 0.06f, fcy + u * 0.24f)
                cubicTo(fcx + u * 0.08f, fcy + u * 0.10f, fcx + u * 0.09f, fcy - u * 0.12f, fcx + u * 0.06f, fcy - u * 0.40f)
                close()
            },
            color = dressLight, alpha = 0.48f
        )

        // Skirt
        val skirt = Path().apply {
            moveTo(fcx - u * 0.18f, fcy + u * 0.26f)
            cubicTo(fcx - u * 0.40f, fcy + u * 0.52f, fcx - u * 0.50f, fcy + u * 0.76f, fcx - u * 0.44f, fcy + u * 0.88f)
            cubicTo(fcx - u * 0.20f, fcy + u * 0.96f, fcx + u * 0.20f, fcy + u * 0.96f, fcx + u * 0.44f, fcy + u * 0.88f)
            cubicTo(fcx + u * 0.50f, fcy + u * 0.76f, fcx + u * 0.40f, fcy + u * 0.52f, fcx + u * 0.18f, fcy + u * 0.26f)
            close()
        }
        drawPath(skirt, color = dressGreen)
        drawPath(
            Path().apply {
                moveTo(fcx - u * 0.12f, fcy + u * 0.28f)
                cubicTo(fcx - u * 0.28f, fcy + u * 0.50f, fcx - u * 0.32f, fcy + u * 0.70f, fcx - u * 0.22f, fcy + u * 0.88f)
                lineTo(fcx + u * 0.02f, fcy + u * 0.88f)
                lineTo(fcx + u * 0.02f, fcy + u * 0.28f)
                close()
            },
            color = dressLight, alpha = 0.32f
        )
        drawPath(
            Path().apply {
                moveTo(fcx - u * 0.44f, fcy + u * 0.88f)
                cubicTo(fcx - u * 0.30f, fcy + u * 0.97f, fcx + u * 0.30f, fcy + u * 0.97f, fcx + u * 0.44f, fcy + u * 0.88f)
            },
            color = dressDark,
            style = Stroke(width = u * 0.052f, cap = StrokeCap.Round)
        )

        // Legs
        drawLine(skinPeach, Offset(fcx - u * 0.10f, fcy + u * 0.88f), Offset(fcx - u * 0.12f, fcy + u * 1.30f), strokeWidth = u * 0.12f, cap = StrokeCap.Round)
        drawLine(skinPeach, Offset(fcx + u * 0.10f, fcy + u * 0.88f), Offset(fcx + u * 0.12f, fcy + u * 1.30f), strokeWidth = u * 0.12f, cap = StrokeCap.Round)

        // Pointed fairy shoes
        for (sign in listOf(-1f, 1f)) {
            drawPath(
                Path().apply {
                    val sx = fcx + sign * u * 0.11f
                    val sy = fcy + u * 1.24f
                    moveTo(sx, sy)
                    cubicTo(sx + sign * u * 0.05f, sy + u * 0.06f, sx + sign * u * 0.22f, sy + u * 0.18f, sx + sign * u * 0.38f, sy + u * 0.14f)
                    cubicTo(sx + sign * u * 0.26f, sy + u * 0.03f, sx + sign * u * 0.08f, sy - u * 0.06f, sx, sy)
                    close()
                },
                color = shoeGreen
            )
        }

        // Left arm — raised, holds wand
        drawPath(
            Path().apply {
                moveTo(fcx - u * 0.21f, fcy - u * 0.28f)
                cubicTo(fcx - u * 0.52f, fcy - u * 0.22f, fcx - u * 0.68f, fcy + u * 0.02f, fcx - u * 0.66f, fcy + u * 0.19f)
            },
            color = skinPeach,
            style = Stroke(width = u * 0.135f, cap = StrokeCap.Round)
        )
        drawCircle(skinPeach, radius = u * 0.088f, center = Offset(fcx - u * 0.66f, fcy + u * 0.19f))

        // Wand shaft + star
        drawLine(Color(0xFFFFD930), Offset(fcx - u * 0.66f, fcy + u * 0.19f), Offset(fcx - u * 0.92f, fcy - u * 0.21f), strokeWidth = u * 0.046f, cap = StrokeCap.Round)
        drawWandStar(u, fcx - u * 0.92f, fcy - u * 0.21f)

        // Right arm — relaxed
        drawPath(
            Path().apply {
                moveTo(fcx + u * 0.21f, fcy - u * 0.28f)
                cubicTo(fcx + u * 0.48f, fcy - u * 0.16f, fcx + u * 0.55f, fcy + u * 0.05f, fcx + u * 0.49f, fcy + u * 0.23f)
            },
            color = skinPeach,
            style = Stroke(width = u * 0.135f, cap = StrokeCap.Round)
        )
        drawCircle(skinPeach, radius = u * 0.088f, center = Offset(fcx + u * 0.49f, fcy + u * 0.23f))

        // Neck
        drawLine(skinShade, Offset(fcx, fcy - u * 0.42f), Offset(fcx, fcy - u * 0.60f), strokeWidth = u * 0.165f, cap = StrokeCap.Round)
    }

    // ── 5-point wand star ─────────────────────────────────────────────────────
    private fun DrawScope.drawWandStar(u: Float, sx: Float, sy: Float) {
        val outerR = u * 0.17f
        val innerR = u * 0.068f
        val star = Path().apply {
            for (i in 0 until 10) {
                val angle = Math.toRadians(i * 36.0 - 90.0)
                val r = if (i % 2 == 0) outerR else innerR
                val px = (sx + r * Math.cos(angle)).toFloat()
                val py = (sy + r * Math.sin(angle)).toFloat()
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        drawPath(star, color = Color(0xFFFFE650))
        drawPath(star, color = Color(0xCCFFFFFF), alpha = 0.65f)
        drawCircle(Color(0xFFFFFFFF), radius = u * 0.052f, center = Offset(sx, sy))
    }

    // ── Head, hair, face ──────────────────────────────────────────────────────
    private fun DrawScope.drawHead(u: Float, fcx: Float, fcy: Float) {
        val hcy = fcy - u * 0.92f
        val hR  = u * 0.42f

        drawCircle(skinShade, radius = hR, center = Offset(fcx + u * 0.022f, hcy + u * 0.032f), alpha = 0.32f)
        drawCircle(skinPeach, radius = hR, center = Offset(fcx, hcy))

        // Pointed ears (both sides)
        for (sign in listOf(-1f, 1f)) {
            val earPath = Path().apply {
                moveTo(fcx + sign * hR * 0.82f, hcy - hR * 0.08f)
                lineTo(fcx + sign * hR * 1.28f, hcy - hR * 0.32f)
                lineTo(fcx + sign * hR * 0.90f, hcy + hR * 0.22f)
                cubicTo(fcx + sign * hR * 0.88f, hcy + hR * 0.10f, fcx + sign * hR * 0.85f, hcy, fcx + sign * hR * 0.82f, hcy - hR * 0.08f)
                close()
            }
            drawPath(earPath, color = skinPeach)
            drawPath(earPath, color = skinShade, alpha = 0.28f)
        }

        // Hair mass (covers ear bases naturally)
        val hairMain = Path().apply {
            moveTo(fcx - hR * 0.92f, hcy + hR * 0.10f)
            cubicTo(fcx - hR * 0.86f, hcy - hR * 1.12f, fcx - hR * 0.15f, hcy - hR * 1.38f, fcx + hR * 0.12f, hcy - hR * 1.40f)
            cubicTo(fcx + hR * 0.58f, hcy - hR * 1.34f, fcx + hR * 0.90f, hcy - hR * 1.06f, fcx + hR * 0.90f, hcy - hR * 0.48f)
            cubicTo(fcx + hR * 0.92f, hcy - hR * 0.28f, fcx + hR * 0.88f, hcy + hR * 0.02f, fcx + hR * 0.82f, hcy + hR * 0.10f)
            cubicTo(fcx + hR * 0.55f, hcy - hR * 0.05f, fcx + hR * 0.22f, hcy - hR * 0.72f, fcx, hcy - hR * 0.76f)
            cubicTo(fcx - hR * 0.22f, hcy - hR * 0.72f, fcx - hR * 0.58f, hcy - hR * 0.05f, fcx - hR * 0.92f, hcy + hR * 0.10f)
            close()
        }
        drawPath(hairMain, color = hairShade)
        drawPath(hairMain, color = hairGold, alpha = 0.88f)
        drawPath(
            Path().apply {
                moveTo(fcx - hR * 0.28f, hcy - hR * 1.30f)
                cubicTo(fcx - hR * 0.05f, hcy - hR * 1.40f, fcx + hR * 0.32f, hcy - hR * 1.28f, fcx + hR * 0.55f, hcy - hR * 1.02f)
                cubicTo(fcx + hR * 0.38f, hcy - hR * 0.80f, fcx - hR * 0.02f, hcy - hR * 0.85f, fcx - hR * 0.22f, hcy - hR * 1.08f)
                close()
            },
            color = hairLight, alpha = 0.52f
        )

        // Bun (slightly off-centre, on top of hair)
        val bunCx = fcx + hR * 0.10f
        val bunCy = hcy - hR * 1.22f
        drawCircle(hairShade, radius = hR * 0.295f, center = Offset(bunCx, bunCy))
        drawCircle(hairGold,  radius = hR * 0.255f, center = Offset(bunCx, bunCy))
        drawCircle(hairLight, radius = hR * 0.105f, center = Offset(bunCx - hR * 0.05f, bunCy - hR * 0.09f), alpha = 0.62f)

        // Eyes
        val eyeY  = hcy - hR * 0.10f
        val eyeLX = fcx - hR * 0.30f
        val eyeRX = fcx + hR * 0.28f
        for (ex in listOf(eyeLX, eyeRX)) {
            drawOval(Color(0xFFFFFFFF), topLeft = Offset(ex - u * 0.120f, eyeY - u * 0.095f), size = Size(u * 0.240f, u * 0.180f))
            drawCircle(eyeIris,             radius = u * 0.082f, center = Offset(ex, eyeY))
            drawCircle(eyePupil,            radius = u * 0.052f, center = Offset(ex, eyeY))
            drawCircle(Color(0xFFFFFFFF),   radius = u * 0.022f, center = Offset(ex + u * 0.018f, eyeY - u * 0.022f))
        }

        // Brows
        val browStroke = Stroke(width = u * 0.046f, cap = StrokeCap.Round)
        for (sign in listOf(-1f, 1f)) {
            val bx = fcx + sign * hR * 0.29f
            drawPath(
                Path().apply {
                    moveTo(bx - sign * u * 0.115f, eyeY - u * 0.160f)
                    cubicTo(bx - sign * u * 0.020f, eyeY - u * 0.222f, bx + sign * u * 0.055f, eyeY - u * 0.215f, bx + sign * u * 0.115f, eyeY - u * 0.165f)
                },
                color = hairShade, style = browStroke
            )
        }

        // Nose dot
        drawCircle(skinShade, radius = u * 0.026f, center = Offset(fcx + u * 0.022f, hcy + u * 0.072f), alpha = 0.52f)

        // Smile
        drawPath(
            Path().apply {
                moveTo(fcx - u * 0.082f, hcy + u * 0.198f)
                cubicTo(fcx - u * 0.020f, hcy + u * 0.270f, fcx + u * 0.038f, hcy + u * 0.270f, fcx + u * 0.082f, hcy + u * 0.198f)
            },
            color = skinShade,
            style = Stroke(width = u * 0.038f, cap = StrokeCap.Round),
            alpha = 0.85f
        )

        // Cheek blush
        drawCircle(Color(0xFFFF9090), radius = u * 0.130f, center = Offset(fcx - hR * 0.50f, hcy + u * 0.118f), alpha = 0.26f)
        drawCircle(Color(0xFFFF9090), radius = u * 0.130f, center = Offset(fcx + hR * 0.50f, hcy + u * 0.118f), alpha = 0.26f)
    }
}
