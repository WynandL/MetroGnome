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

object GlissieFairy : MetroItem {

    override val id              = "glissie_fairy"
    override val displayName     = "Glissie"
    override val description     = "A musical fairy who dances slow glissandos through Metro's night sky."
    override val earnedMessage   = "Six hours of music, and the forest finally stirred. Glissie has been up there all along, spinning slow glissandos in the dark. Now that you've earned her trust, she dances for you both."
    override val isBodyAttached  = false

    // Tap target: canvas pos ≈ (canvasW*0.22, baseY-6.5u). In body space (subtract cx=canvasW/2,
    // subtract baseY): x ≈ -0.28*canvasW ≈ -2.2u on a portrait phone (canvasW/canvasH ≈ 0.47).
    // Radius is generous to catch her mid-float animation (±0.52u lateral drift).
    override fun hitCenter(u: Float) = Offset(-2.2f * u, -6.5f * u)
    override fun hitRadius(u: Float) = u * 1.8f

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
    private val dressPurple = Color(0xFFAA60CC)
    private val dressMid    = Color(0xFF7C3EA8)
    private val dressDark   = Color(0xFF4E2478)
    private val dressLight  = Color(0xFFCC96EE)
    private val shoePlum    = Color(0xFF2E1254)
    private val eyeIris     = Color(0xFF7040B8)
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
        val ay = fcy - u * 0.35f
        val ws = 0.65f + 0.35f * flutter  // horizontal spread: beats inward

        val ulWing = Path().apply {
            moveTo(fcx - u * 0.06f, ay)
            cubicTo(
                fcx - u * 0.28f, ay - u * 0.78f,
                fcx - u * 1.40f * ws, ay - u * 0.90f, fcx - u * 1.58f * ws, ay - u * 0.36f)
            cubicTo(
                fcx - u * 1.32f * ws, ay + u * 0.10f,
                fcx - u * 0.36f, ay + u * 0.04f, fcx - u * 0.06f, ay)
            close()
        }
        val urWing = Path().apply {
            moveTo(fcx + u * 0.06f, ay)
            cubicTo(
                fcx + u * 0.28f, ay - u * 0.78f,
                fcx + u * 1.40f * ws, ay - u * 0.90f, fcx + u * 1.58f * ws, ay - u * 0.36f)
            cubicTo(
                fcx + u * 1.32f * ws, ay + u * 0.10f,
                fcx + u * 0.36f, ay + u * 0.04f, fcx + u * 0.06f, ay)
            close()
        }
        val llWing = Path().apply {
            moveTo(fcx - u * 0.08f, ay + u * 0.06f)
            cubicTo(
                fcx - u * 0.22f, ay + u * 0.50f,
                fcx - u * 0.86f * ws, ay + u * 0.70f, fcx - u * 0.80f * ws, ay + u * 0.26f)
            cubicTo(
                fcx - u * 0.58f * ws, ay + u * 0.05f,
                fcx - u * 0.20f, ay - u * 0.02f, fcx - u * 0.08f, ay + u * 0.06f)
            close()
        }
        val lrWing = Path().apply {
            moveTo(fcx + u * 0.08f, ay + u * 0.06f)
            cubicTo(
                fcx + u * 0.22f, ay + u * 0.50f,
                fcx + u * 0.86f * ws, ay + u * 0.70f, fcx + u * 0.80f * ws, ay + u * 0.26f)
            cubicTo(
                fcx + u * 0.58f * ws, ay + u * 0.05f,
                fcx + u * 0.20f, ay - u * 0.02f, fcx + u * 0.08f, ay + u * 0.06f)
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
                    moveTo(fcx + sign * u * 0.06f, ay)
                    cubicTo(
                        fcx + sign * u * 0.48f, ay - u * 0.46f,
                        fcx + sign * u * 1.02f * ws, ay - u * 0.68f,
                        fcx + sign * u * 1.42f * ws, ay - u * 0.30f)
                },
                color = wingVein, style = vStroke
            )
            drawPath(
                Path().apply {
                    moveTo(fcx + sign * u * 0.10f, ay - u * 0.20f)
                    cubicTo(
                        fcx + sign * u * 0.52f, ay - u * 0.68f,
                        fcx + sign * u * 1.08f * ws, ay - u * 0.78f,
                        fcx + sign * u * 1.32f * ws, ay - u * 0.54f)
                },
                color = wingVein, style = vStroke, alpha = 0.60f
            )
        }
    }

    // ── Dress, arms, legs ─────────────────────────────────────────────────────
    private fun DrawScope.drawBody(u: Float, fcx: Float, fcy: Float) {

        // Legs
        drawLine(skinPeach, Offset(fcx - u * 0.10f, fcy + u * 0.88f), Offset(fcx - u * 0.12f, fcy + u * 1.30f), strokeWidth = u * 0.12f, cap = StrokeCap.Round)
        drawLine(skinPeach, Offset(fcx + u * 0.10f, fcy + u * 0.88f), Offset(fcx + u * 0.12f, fcy + u * 1.30f), strokeWidth = u * 0.12f, cap = StrokeCap.Round)

        // Pointed fairy shoes
        for (sign in listOf(-1f, 1f)) {
            drawPath(
                Path().apply {
                    val sx = fcx + sign * u * 0.04f  // inward of leg; shoe extends outward to toe
                    val sy = fcy + u * 1.30f          // aligned with leg bottom
                    moveTo(sx, sy - u * 0.08f)        // top of shoe back
                    lineTo(sx, sy + u * 0.08f)        // bottom of shoe back — covers rounded leg cap
                    cubicTo(
                        sx + sign * u * 0.06f, sy + u * 0.14f,
                        sx + sign * u * 0.24f, sy + u * 0.22f,
                        sx + sign * u * 0.42f, sy + u * 0.14f)  // toe tip
                    cubicTo(
                        sx + sign * u * 0.30f, sy + u * 0.02f,
                        sx + sign * u * 0.10f, sy - u * 0.08f,
                        sx, sy - u * 0.08f)  // top edge back to start
                    close()
                },
                color = shoePlum
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
        drawLine(Color(0xFFFFD930), Offset(fcx - u * 0.66f, fcy + u * 0.19f), Offset(fcx - u * 0.92f, fcy - u * 0.21f), strokeWidth = u * 0.046f, cap = StrokeCap.Round)
        drawWandStar(u, fcx - u * 0.92f, fcy - u * 0.21f)

        drawPath(
            Path().apply {
                moveTo(fcx + u * 0.21f, fcy - u * 0.28f)
                cubicTo(fcx + u * 0.48f, fcy - u * 0.16f, fcx + u * 0.55f, fcy + u * 0.05f, fcx + u * 0.49f, fcy + u * 0.23f)
            },
            color = skinPeach,
            style = Stroke(width = u * 0.135f, cap = StrokeCap.Round)
        )
        drawCircle(skinPeach, radius = u * 0.088f, center = Offset(fcx + u * 0.49f, fcy + u * 0.23f))

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

        val skirt = Path().apply {
            moveTo(fcx - u * 0.18f, fcy + u * 0.26f)
            cubicTo(fcx - u * 0.40f, fcy + u * 0.52f, fcx - u * 0.50f, fcy + u * 0.76f, fcx - u * 0.44f, fcy + u * 0.88f)
            cubicTo(fcx - u * 0.20f, fcy + u * 0.96f, fcx + u * 0.20f, fcy + u * 0.96f, fcx + u * 0.44f, fcy + u * 0.88f)
            cubicTo(fcx + u * 0.50f, fcy + u * 0.76f, fcx + u * 0.40f, fcy + u * 0.52f, fcx + u * 0.18f, fcy + u * 0.26f)
            close()
        }
        drawPath(skirt, color = dressPurple)
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

        // Neck
        drawLine(skinPeach, Offset(fcx, fcy - u * 0.42f), Offset(fcx, fcy - u * 0.60f), strokeWidth = u * 0.165f, cap = StrokeCap.Round)
    }

    // ── 5-point wand star ─────────────────────────────────────────────────────
    private fun DrawScope.drawWandStar(u: Float, sx: Float, sy: Float) {
        val outerR = u * 0.17f
        val innerR = u * 0.068f
        val star = Path().apply {
            for (i in 0 until 10) {
                val angle = Math.toRadians(i * 36.0 - 90.0)
                val r = if (i % 2 == 0) outerR else innerR
                val px = (sx + r * cos(angle)).toFloat()
                val py = (sy + r * sin(angle)).toFloat()
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
            // Outer ear — fully curved, broader base, graceful upward-pointing tip
            val earOuter = Path().apply {
                moveTo(fcx + sign * hR * 0.80f, hcy - hR * 0.18f)
                cubicTo(
                    fcx + sign * hR * 0.98f, hcy - hR * 0.24f,
                    fcx + sign * hR * 1.16f, hcy - hR * 0.30f,
                    fcx + sign * hR * 1.22f, hcy - hR * 0.36f
                )
                cubicTo(
                    fcx + sign * hR * 1.28f, hcy - hR * 0.38f,
                    fcx + sign * hR * 1.26f, hcy - hR * 0.22f,
                    fcx + sign * hR * 1.10f, hcy - hR * 0.06f
                )
                cubicTo(
                    fcx + sign * hR * 1.00f, hcy + hR * 0.10f,
                    fcx + sign * hR * 0.92f, hcy + hR * 0.20f,
                    fcx + sign * hR * 0.88f, hcy + hR * 0.28f
                )
                cubicTo(
                    fcx + sign * hR * 0.86f, hcy + hR * 0.12f,
                    fcx + sign * hR * 0.82f, hcy - hR * 0.04f,
                    fcx + sign * hR * 0.80f, hcy - hR * 0.18f
                )
                close()
            }
            drawPath(earOuter, color = skinPeach)
            drawPath(earOuter, color = skinShade, alpha = 0.20f)

            // Inner ear cavity — concave fold that adds depth
            val earInner = Path().apply {
                moveTo(fcx + sign * hR * 0.84f, hcy - hR * 0.08f)
                cubicTo(
                    fcx + sign * hR * 0.96f, hcy - hR * 0.16f,
                    fcx + sign * hR * 1.08f, hcy - hR * 0.20f,
                    fcx + sign * hR * 1.12f, hcy - hR * 0.16f
                )
                cubicTo(
                    fcx + sign * hR * 1.10f, hcy - hR * 0.06f,
                    fcx + sign * hR * 1.00f, hcy + hR * 0.08f,
                    fcx + sign * hR * 0.90f, hcy + hR * 0.18f
                )
                cubicTo(
                    fcx + sign * hR * 0.87f, hcy + hR * 0.08f,
                    fcx + sign * hR * 0.85f, hcy,
                    fcx + sign * hR * 0.84f, hcy - hR * 0.08f
                )
                close()
            }
            drawPath(earInner, color = skinShade, alpha = 0.42f)

            // Helix highlight — bright rim along the upper outer edge
            drawPath(
                Path().apply {
                    moveTo(fcx + sign * hR * 0.84f, hcy - hR * 0.14f)
                    cubicTo(
                        fcx + sign * hR * 1.00f, hcy - hR * 0.22f,
                        fcx + sign * hR * 1.16f, hcy - hR * 0.28f,
                        fcx + sign * hR * 1.20f, hcy - hR * 0.33f
                    )
                },
                color = skinPeach,
                style = Stroke(width = hR * 0.055f, cap = StrokeCap.Round),
                alpha = 0.70f
            )
        }

        // Hair mass
        val hairMain = Path().apply {
            moveTo(fcx - hR * 0.90f, hcy - hR * 0.20f)
            cubicTo(
                fcx - hR * 1.06f, hcy - hR * 0.65f,
                fcx - hR * 0.75f, hcy - hR * 1.28f,
                fcx + hR * 0.08f, hcy - hR * 1.40f
            )
            cubicTo(
                fcx + hR * 0.76f, hcy - hR * 1.28f,
                fcx + hR * 1.06f, hcy - hR * 0.65f,
                fcx + hR * 0.90f, hcy - hR * 0.20f
            )
            cubicTo(
                fcx + hR * 0.76f, hcy - hR * 0.44f,
                fcx + hR * 0.20f, hcy - hR * 0.76f,
                fcx,              hcy - hR * 0.78f
            )
            cubicTo(
                fcx - hR * 0.20f, hcy - hR * 0.76f,
                fcx - hR * 0.74f, hcy - hR * 0.46f,
                fcx - hR * 0.90f, hcy - hR * 0.20f
            )
            close()
        }
        drawPath(hairMain, color = hairShade)
        drawPath(hairMain, color = hairGold, alpha = 0.88f)
        // Shine patch
        drawPath(
            Path().apply {
                moveTo(fcx - hR * 0.24f, hcy - hR * 1.28f)
                cubicTo(
                    fcx - hR * 0.02f, hcy - hR * 1.40f,
                    fcx + hR * 0.34f, hcy - hR * 1.26f,
                    fcx + hR * 0.52f, hcy - hR * 1.00f
                )
                cubicTo(
                    fcx + hR * 0.36f, hcy - hR * 0.82f,
                    fcx + hR * 0.02f, hcy - hR * 0.86f,
                    fcx - hR * 0.18f, hcy - hR * 1.08f
                )
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
