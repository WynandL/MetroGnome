package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.theme.GnomeColors
import com.example.metrognome.viewmodel.BeatEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// Pre-calculated star positions (seed fixed for determinism)
private val stars: List<Pair<Float, Float>> = run {
    val rng = Random(1337)
    (0 until 90).map { Pair(rng.nextFloat(), rng.nextFloat()) }
}

@Composable
fun GnomeCanvas(
    bpm: Int,
    isPlaying: Boolean,
    beatEvents: SharedFlow<BeatEvent>,
    flashOnBeat: Boolean,
    accentBeat: Int,          // 1-based; 0 = no accent
    activeItems: List<MetroItem> = emptyList(),
    onItemTapped: (MetroItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentBpm by rememberUpdatedState(bpm)

    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val breathAnim by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAnim"
    )

    val pendulumAngle = remember { Animatable(0f) }
    val bounce = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val twinkle = remember { Animatable(0f) }

    // Return baton to upright when metronome stops
    LaunchedEffect(isPlaying) {
        if (!isPlaying) pendulumAngle.animateTo(0f, tween(300, easing = LinearEasing))
    }

    LaunchedEffect(beatEvents) {
        var goingRight = true
        beatEvents.collect { event ->
            // Pendulum: alternate direction on every beat, driven by actual beat timing.
            // Each animateTo takes exactly one beat duration so the baton arrives at
            // the opposite extreme precisely when the next beat fires.
            val beatMs = (60_000f / currentBpm).toInt().coerceAtLeast(100)
            val target = if (goingRight) 1f else -1f
            goingRight = !goingRight
            launch { pendulumAngle.animateTo(target, tween(beatMs, easing = LinearEasing)) }

            launch {
                bounce.snapTo(1f)
                bounce.animateTo(0f, tween(250))
            }
            if (flashOnBeat) {
                launch {
                    val maxFlash = if (accentBeat > 0 && event.beat == accentBeat - 1) 0.7f else 0.35f
                    flash.snapTo(maxFlash)
                    flash.animateTo(0f, tween(350))
                }
            }
            launch {
                twinkle.snapTo(1f)
                twinkle.animateTo(0f, tween(300))
            }
        }
    }

    val effectivePendulum = pendulumAngle.value
    val effectiveBreath = if (!isPlaying) breathAnim else 0f

    val canvasSize = remember { mutableStateOf(Size.Zero) }

    Canvas(modifier = modifier
        .fillMaxSize()
        .pointerInput(activeItems) {
            detectTapGestures { tapOffset ->
                val s = canvasSize.value
                if (s == Size.Zero) return@detectTapGestures
                val u     = s.height / 17f
                val cx    = s.width / 2f
                val baseY = s.height * 0.97f
                val bodyX = tapOffset.x - cx
                val bodyY = tapOffset.y - baseY
                activeItems.firstOrNull { item ->
                    val center = item.hitCenter(u) ?: return@firstOrNull false
                    val dx = bodyX - center.x
                    val dy = bodyY - center.y
                    val r  = item.hitRadius(u)
                    dx * dx + dy * dy <= r * r
                }?.let(onItemTapped)
            }
        }
    ) {
        canvasSize.value = size
        val canvasCx    = size.width / 2f
        val canvasBaseY = size.height * 0.97f
        val u           = size.height / 17f

        drawBackground(twinkle.value)

        // Background items (scene decoration — not body-attached)
        activeItems.filter { !it.isBodyAttached }.forEach { item ->
            with(item) { draw(u, canvasCx, canvasBaseY) }
        }

        if (flash.value > 0f) {
            drawRect(color = GnomeColors.beatGlowAccent.copy(alpha = flash.value * 0.4f))
        }

        drawGnome(
            pendulumAngle = effectivePendulum,
            beatBounce = bounce.value,
            breathOffset = effectiveBreath,
            isPlaying = isPlaying,
            bodyItems = activeItems.filter { it.isBodyAttached },
            u = u,
            cx = canvasCx,
            baseY = canvasBaseY
        )
    }
}

// ── Background ────────────────────────────────────────────────────────────────

private fun DrawScope.drawBackground(twinkle: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(GnomeColors.bgTop, GnomeColors.bgBottom),
            startY = 0f,
            endY = size.height
        )
    )
    for ((fx, fy) in stars) {
        val x = fx * size.width
        val y = fy * size.height * 0.72f
        val r = 1.2f + fx * 1.8f
        val alpha = 0.35f + fy * 0.55f + twinkle * 0.3f * abs(fx - 0.5f)
        drawCircle(
            color = Color.White.copy(alpha = alpha.coerceIn(0.1f, 1f)),
            radius = r,
            center = Offset(x, y)
        )
    }
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0x33311B8A)),
            startY = size.height * 0.78f,
            endY = size.height
        )
    )
}

// ── MetroGnome — corporate metropolitan garden gnome ─────────────────────────
//
// Coordinate system: origin at feet level (after translate to cx, baseY).
// Negative Y = above feet. 1 unit (u) = size.height / 18.
//
// Head group: neck, head, hair, beard, face features, and hat all move together
// on each beat — a cool, confident head-bob nod.

private fun DrawScope.drawGnome(
    pendulumAngle: Float,
    beatBounce: Float,
    breathOffset: Float,
    isPlaying: Boolean,
    bodyItems: List<MetroItem> = emptyList(),
    u: Float = size.height / 17f,
    cx: Float = size.width / 2f,
    baseY: Float = size.height * 0.97f
) {
    val breathTranslate = breathOffset * u * 0.1f

    withTransform({
        translate(cx, baseY + breathTranslate)
    }) {
        drawShadow(u)
        drawShoes(u)
        drawLegs(u)
        drawLeftArm(u)
        drawBody(u)
        drawShirtCollar(u)
        drawBelt(u)
        drawButtons(u)
        drawBaton(u, pendulumAngle)
        drawRightArm(u, pendulumAngle)

        // ── Head group — bobs on every beat ───────────────────────────────
        val headBob = beatBounce * u * 0.5f
        withTransform({ translate(0f, headBob) }) {
            drawNeck(u)
            drawHead(u)
            drawHair(u)
            drawNose(u)
            drawMustache(u)
            drawSunglasses(u)
            drawEyebrows(u)
            drawHat(u, beatBounce)
            // Head-attached items (earrings etc.) bob with the head
            bodyItems.filter { it.isHeadAttached }.forEach { item ->
                with(item) { draw(u, cx, baseY) }
            }
        }

        // Body-attached (non-head) cosmetic items drawn last on top of the gnome
        bodyItems.filter { !it.isHeadAttached }.forEach { item ->
            with(item) { draw(u, cx, baseY) }
        }
    }
}

// ── Ground shadow ─────────────────────────────────────────────────────────────

private fun DrawScope.drawShadow(u: Float) {
    drawOval(
        color = Color(0x44000000),
        topLeft = Offset(-2.0f * u, -0.3f * u),
        size = Size(4.0f * u, 0.5f * u)
    )
}

// ── Red Oxford dress shoes ────────────────────────────────────────────────────

private fun DrawScope.drawShoes(u: Float) {
    // hx centres each shoe over its leg (±0.62u); inner heel extent is 0.55u,
    // giving a clear ~0.14u gap between the two heels at ≈ ±0.07u.
    fun shoe(side: Float) {
        val hx = side * 0.82f * u
        val path = Path().apply {
            moveTo(hx - side * 0.55f * u, -0.05f * u)   // inner heel
            lineTo(hx + side * 1.2f * u, -0.05f * u)   // sole base toward toe
            cubicTo(
                hx + side * 1.5f * u, -0.05f * u,
                hx + side * 1.58f * u, -0.38f * u,
                hx + side * 1.32f * u, -0.60f * u        // toe tip
            )
            cubicTo(
                hx + side * 0.82f * u, -0.52f * u,
                hx + side * 0.16f * u, -0.46f * u,
                hx - side * 0.02f * u, -0.56f * u
            )
            lineTo(hx - side * 0.55f * u, -0.56f * u)   // back to heel
            close()
        }
        drawPath(path, color = GnomeColors.shoe)
        // Glossy toe highlight
        drawOval(
            color = GnomeColors.shoeGloss,
            topLeft = Offset(hx + side * 0.65f * u, -0.53f * u),
            size = Size(side * 0.42f * u, 0.15f * u)
        )
        // Cream sole edge
        drawLine(
            color = GnomeColors.shoeSole,
            start = Offset(hx - side * 0.55f * u, -0.04f * u),
            end = Offset(hx + side * 1.2f * u, -0.04f * u),
            strokeWidth = 0.07f * u
        )
    }
    shoe(-1f)
    shoe(1f)
}

// ── Legs — slim dark pinstripe trousers ───────────────────────────────────────

private fun DrawScope.drawLegs(u: Float) {
    val topY = -3.6f * u
    val botY = -0.6f * u
    val h = botY - topY
    fun leg(xCenter: Float) {
        drawRoundRect(
            color = GnomeColors.pants,
            topLeft = Offset(xCenter - 0.42f * u, topY),
            size = Size(0.84f * u, h),
            cornerRadius = CornerRadius(0.25f * u)
        )
        drawLine(
            color = GnomeColors.pantsHighlight,
            start = Offset(xCenter, topY + 0.3f * u),
            end = Offset(xCenter, botY - 0.2f * u),
            strokeWidth = 0.05f * u
        )
    }
    leg(-0.62f * u)
    leg(0.62f * u)
}

// ── Left arm ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawLeftArm(u: Float) {
    val shoulderX = -1.75f * u
    val shoulderY = -6.4f * u
    val elbowX = -2.5f * u
    val elbowY = -5.0f * u
    val handX = -2.7f * u
    val handY = -3.8f * u
    drawPath(
        Path().apply {
            moveTo(shoulderX, shoulderY)
            cubicTo(
                shoulderX - 0.3f * u,
                shoulderY + 0.4f * u,
                elbowX,
                elbowY - 0.2f * u,
                elbowX,
                elbowY
            )
            cubicTo(elbowX, elbowY + 0.4f * u, handX - 0.15f * u, handY - 0.3f * u, handX, handY)
        },
        color = GnomeColors.jacketDark, style = Stroke(width = 0.82f * u, cap = StrokeCap.Round)
    )
    drawLine(
        color = GnomeColors.shirt,
        start = Offset(handX - 0.28f * u, handY - 0.22f * u),
        end = Offset(handX + 0.28f * u, handY - 0.22f * u),
        strokeWidth = 0.12f * u,
        cap = StrokeCap.Round
    )
    drawCircle(GnomeColors.skin, radius = 0.34f * u, center = Offset(handX, handY))
}

// ── Right arm (holds baton) ───────────────────────────────────────────────────

private fun DrawScope.drawRightArm(u: Float, pendulumAngle: Float) {
    val shoulderX = 1.75f * u
    val shoulderY = -6.4f * u
    val elbowX = 2.2f * u
    val elbowY = -5.2f * u
    val handX = 2.0f * u
    val handY = -4.5f * u
    drawPath(
        Path().apply {
            moveTo(shoulderX, shoulderY)
            cubicTo(
                shoulderX + 0.3f * u,
                shoulderY + 0.3f * u,
                elbowX,
                elbowY - 0.2f * u,
                elbowX,
                elbowY
            )
            cubicTo(elbowX, elbowY + 0.4f * u, handX + 0.2f * u, handY - 0.3f * u, handX, handY)
        },
        color = GnomeColors.jacketDark, style = Stroke(width = 0.82f * u, cap = StrokeCap.Round)
    )
    drawLine(
        color = GnomeColors.shirt,
        start = Offset(handX - 0.28f * u, handY - 0.22f * u),
        end = Offset(handX + 0.28f * u, handY - 0.22f * u),
        strokeWidth = 0.12f * u,
        cap = StrokeCap.Round
    )
    drawCircle(GnomeColors.skin, radius = 0.34f * u, center = Offset(handX, handY))
}

// ── Conducting baton ──────────────────────────────────────────────────────────

private fun DrawScope.drawBaton(u: Float, pendulumAngle: Float) {
    withTransform({
        translate(2.0f * u, -4.5f * u)
        rotate(pendulumAngle * 40f, Offset.Zero)
    }) {
        val batonLen = 4.2f * u
        drawLine(
            color = GnomeColors.batonGold, start = Offset(0f, 0f), end = Offset(0f, batonLen),
            strokeWidth = 0.18f * u, cap = StrokeCap.Round
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFFE566), GnomeColors.batonGold),
                center = Offset(-0.08f * u, batonLen - 0.1f * u), radius = 0.42f * u
            ),
            radius = 0.38f * u, center = Offset(0f, batonLen)
        )
        for (i in 0 until 4) {
            drawLine(
                color = GnomeColors.batonDark,
                start = Offset(-0.11f * u, 0.55f * u + i * 0.22f * u),
                end = Offset(0.11f * u, 0.55f * u + i * 0.22f * u), strokeWidth = 0.06f * u
            )
        }
    }
}

// ── Body — near-black pinstripe suit ─────────────────────────────────────────

private fun DrawScope.drawBody(u: Float) {
    // Jacket base
    drawOval(
        brush = Brush.linearGradient(
            colors = listOf(GnomeColors.jacketLight, GnomeColors.jacket, GnomeColors.jacketDark),
            start = Offset(-1.8f * u, -7.6f * u),
            end = Offset(1.8f * u, -3.6f * u)
        ),
        topLeft = Offset(-1.8f * u, -7.6f * u),
        size = Size(3.6f * u, 4.0f * u)
    )
    // Pinstripes clipped to the jacket oval so they follow the body contour
    val jacketClip = Path().apply {
        addOval(Rect(Offset(-1.8f * u, -7.6f * u), Size(3.6f * u, 4.0f * u)))
    }
    drawContext.canvas.save()
    drawContext.canvas.clipPath(jacketClip)
    for (i in -6..6) {
        val x = i * 0.28f * u
        drawLine(
            color = GnomeColors.pinstripe,
            start = Offset(x, -7.5f * u),
            end = Offset(x, -3.7f * u),
            strokeWidth = 0.03f * u
        )
    }
    drawContext.canvas.restore()
    // Left lapel
    val leftLapel = Path().apply {
        moveTo(-0.15f * u, -7.55f * u)
        lineTo(-0.65f * u, -6.85f * u)
        lineTo(-1.35f * u, -7.05f * u)
        lineTo(-1.45f * u, -7.6f * u)
        close()
    }
    val rightLapel = Path().apply {
        moveTo(0.15f * u, -7.55f * u)
        lineTo(0.65f * u, -6.85f * u)
        lineTo(1.35f * u, -7.05f * u)
        lineTo(1.45f * u, -7.6f * u)
        close()
    }
    drawPath(leftLapel, color = GnomeColors.jacketLight)
    drawPath(rightLapel, color = GnomeColors.jacketLight)
    drawPath(leftLapel, color = GnomeColors.jacketDark, style = Stroke(width = 0.05f * u))
    drawPath(rightLapel, color = GnomeColors.jacketDark, style = Stroke(width = 0.05f * u))
    // Pocket square — white, dapper
    drawPath(
        Path().apply {
            moveTo(-1.42f * u, -6.82f * u)
            lineTo(-1.12f * u, -6.92f * u)
            lineTo(-1.02f * u, -6.52f * u)
            lineTo(-1.32f * u, -6.42f * u)
            close()
        },
        color = GnomeColors.shirt
    )
}

// ── Shirt collar & bow tie ────────────────────────────────────────────────────

private fun DrawScope.drawShirtCollar(u: Float) {
    drawPath(
        Path().apply {
            moveTo(-0.52f * u, -7.52f * u); lineTo(0f, -6.95f * u); lineTo(0.52f * u, -7.52f * u)
            lineTo(0.32f * u, -8.08f * u); lineTo(0f, -7.88f * u); lineTo(-0.32f * u, -8.08f * u)
            close()
        },
        color = GnomeColors.shirt
    )
    // Bow tie
    drawPath(Path().apply {
        moveTo(-0.05f * u, -7.82f * u); lineTo(
        -0.5f * u,
        -7.62f * u
    ); lineTo(-0.5f * u, -8.02f * u); close()
    }, color = GnomeColors.tie)
    drawPath(Path().apply {
        moveTo(0.05f * u, -7.82f * u); lineTo(
        0.5f * u,
        -7.62f * u
    ); lineTo(0.5f * u, -8.02f * u); close()
    }, color = GnomeColors.tie)
    drawCircle(GnomeColors.tie, radius = 0.12f * u, center = Offset(0f, -7.82f * u))
}

// ── Belt ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBelt(u: Float) {
    drawRect(
        GnomeColors.belt,
        topLeft = Offset(-1.75f * u, -4.05f * u),
        size = Size(3.5f * u, 0.55f * u)
    )
    drawRect(
        GnomeColors.beltBuckle,
        topLeft = Offset(-0.35f * u, -4.05f * u),
        size = Size(0.7f * u, 0.55f * u)
    )
    drawRect(
        GnomeColors.belt,
        topLeft = Offset(-0.18f * u, -3.99f * u),
        size = Size(0.36f * u, 0.43f * u)
    )
}

// ── Gold jacket buttons ───────────────────────────────────────────────────────

private fun DrawScope.drawButtons(u: Float) {
    for (y in listOf(-5.05f * u, -5.75f * u, -6.45f * u)) {
        drawCircle(GnomeColors.buttonGold, radius = 0.13f * u, center = Offset(0f, y))
        drawCircle(GnomeColors.jacketDark, radius = 0.06f * u, center = Offset(0f, y))
    }
}

// ── Neck ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawNeck(u: Float) {
    drawRoundRect(
        color = GnomeColors.skin,
        topLeft = Offset(-0.38f * u, -8.5f * u),
        size = Size(0.76f * u, 0.55f * u),
        cornerRadius = CornerRadius(0.15f * u)
    )
}

// ── Head ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawHead(u: Float) {
    val cx = 0f
    val cy = -10.0f * u
    val r = 1.85f * u
    // Head sphere
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(GnomeColors.skinHighlight, GnomeColors.skin, GnomeColors.skinDark),
            center = Offset(cx - r * 0.25f, cy - r * 0.25f), radius = r * 1.3f
        ),
        radius = r, center = Offset(cx, cy)
    )
    // Ears
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * r * 0.97f
        drawCircle(GnomeColors.skin, radius = 0.36f * u, center = Offset(ex, cy + 0.15f * u))
        drawCircle(
            GnomeColors.skinDark,
            radius = 0.20f * u,
            center = Offset(ex + side * 0.05f * u, cy + 0.15f * u)
        )
    }
    // Cheek blush
    drawCircle(
        GnomeColors.cheek,
        radius = 0.48f * u,
        center = Offset(cx - 1.05f * u, cy + 0.45f * u)
    )
    drawCircle(
        GnomeColors.cheek,
        radius = 0.48f * u,
        center = Offset(cx + 1.05f * u, cy + 0.45f * u)
    )
}

// ── Grey side-parted hair ─────────────────────────────────────────────────────
//
// Drawn after the head circle so it sits on top of the head edges.
// The hat (drawn last) will naturally cover the top portion.
// Hair peeks out on the sides and at the forehead — classic corporate side part.

private fun DrawScope.drawHair(u: Float) {
    // Left side — hair falls from under hat brim, alongside head
    drawPath(
        Path().apply {
            moveTo(-1.42f * u, -11.52f * u)
            cubicTo(-1.68f * u, -11.15f * u, -2.08f * u, -10.72f * u, -2.12f * u, -10.1f * u)
            cubicTo(-1.96f * u, -10.0f * u, -1.65f * u, -10.08f * u, -1.52f * u, -10.38f * u)
            cubicTo(-1.46f * u, -10.88f * u, -1.28f * u, -11.28f * u, -1.18f * u, -11.48f * u)
            close()
        },
        color = GnomeColors.hairGrey
    )
    // Right side
    drawPath(
        Path().apply {
            moveTo(1.42f * u, -11.52f * u)
            cubicTo(1.68f * u, -11.15f * u, 2.08f * u, -10.72f * u, 2.12f * u, -10.1f * u)
            cubicTo(1.96f * u, -10.0f * u, 1.65f * u, -10.08f * u, 1.52f * u, -10.38f * u)
            cubicTo(1.46f * u, -10.88f * u, 1.28f * u, -11.28f * u, 1.18f * u, -11.48f * u)
            close()
        },
        color = GnomeColors.hairGrey
    )
    // Forelock — swept from left-center to right (classic side part)
    // Visible just below the front edge of the tilted hat brim
    drawPath(
        Path().apply {
            moveTo(-0.6f * u, -11.68f * u)
            cubicTo(0.0f * u, -11.82f * u, 0.72f * u, -11.65f * u, 1.05f * u, -11.45f * u)
            cubicTo(0.82f * u, -11.40f * u, 0.12f * u, -11.56f * u, -0.45f * u, -11.58f * u)
            close()
        },
        color = GnomeColors.hairDark
    )
}

// ── Nose ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawNose(u: Float) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(GnomeColors.skinHighlight, GnomeColors.nosePink),
            center = Offset(-0.08f * u, -9.42f * u), radius = 0.5f * u
        ),
        topLeft = Offset(-0.44f * u, -9.72f * u),
        size = Size(0.88f * u, 0.72f * u)
    )
    drawOval(
        color = GnomeColors.skinDark.copy(alpha = 0.35f),
        topLeft = Offset(-0.3f * u, -9.38f * u),
        size = Size(0.2f * u, 0.18f * u)
    )
    drawOval(
        color = GnomeColors.skinDark.copy(alpha = 0.35f),
        topLeft = Offset(0.1f * u, -9.38f * u),
        size = Size(0.2f * u, 0.18f * u)
    )
}

// ── Full Santa moustache ──────────────────────────────────────────────────────
//
// Wide, drooping, off-white — connects naturally into the beard below.

private fun DrawScope.drawMustache(u: Float) {
    val baseY = -9.12f * u
    // Left wing
    drawPath(
        Path().apply {
            moveTo(-0.08f * u, baseY)
            cubicTo(
                -0.45f * u,
                baseY - 0.12f * u,
                -1.35f * u,
                baseY - 0.08f * u,
                -1.58f * u,
                baseY + 0.42f * u
            )
            cubicTo(
                -1.45f * u,
                baseY + 0.62f * u,
                -0.78f * u,
                baseY + 0.55f * u,
                -0.35f * u,
                baseY + 0.44f * u
            )
            cubicTo(-0.12f * u, baseY + 0.36f * u, 0f, baseY + 0.25f * u, -0.08f * u, baseY)
            close()
        },
        color = GnomeColors.beard
    )
    // Right wing
    drawPath(
        Path().apply {
            moveTo(0.08f * u, baseY)
            cubicTo(
                0.45f * u,
                baseY - 0.12f * u,
                1.35f * u,
                baseY - 0.08f * u,
                1.58f * u,
                baseY + 0.42f * u
            )
            cubicTo(
                1.45f * u,
                baseY + 0.62f * u,
                0.78f * u,
                baseY + 0.55f * u,
                0.35f * u,
                baseY + 0.44f * u
            )
            cubicTo(0.12f * u, baseY + 0.36f * u, 0f, baseY + 0.25f * u, 0.08f * u, baseY)
            close()
        },
        color = GnomeColors.beard
    )
    // Subtle shadow under moustache for depth
    drawLine(
        color = GnomeColors.beardShade.copy(alpha = 0.4f),
        start = Offset(-1.2f * u, baseY + 0.52f * u),
        end = Offset(1.2f * u, baseY + 0.52f * u),
        strokeWidth = 0.08f * u, cap = StrokeCap.Round
    )
}

// ── Gold-frame sunglasses ─────────────────────────────────────────────────────

private fun DrawScope.drawSunglasses(u: Float) {
    val lensY = -10.3f * u
    val lensH = 0.62f * u
    val lensW = 1.1f * u
    fun lens(lx: Float) {
        drawRoundRect(
            color = GnomeColors.glassLens,
            topLeft = Offset(lx - lensW / 2, lensY - lensH / 2), size = Size(lensW, lensH),
            cornerRadius = CornerRadius(0.2f * u)
        )
        drawRoundRect(
            color = GnomeColors.glassFrame,
            topLeft = Offset(lx - lensW / 2, lensY - lensH / 2), size = Size(lensW, lensH),
            cornerRadius = CornerRadius(0.2f * u), style = Stroke(width = 0.1f * u)
        )
        drawLine(
            color = GnomeColors.glassReflect,
            start = Offset(lx - lensW * 0.3f, lensY - lensH * 0.25f),
            end = Offset(lx - lensW * 0.05f, lensY + lensH * 0.15f),
            strokeWidth = 0.11f * u, cap = StrokeCap.Round
        )
    }
    lens(-0.7f * u); lens(0.7f * u)
    drawLine(
        GnomeColors.glassFrame,
        Offset(-0.15f * u, lensY),
        Offset(0.15f * u, lensY),
        strokeWidth = 0.08f * u
    )
    drawLine(
        GnomeColors.glassFrame,
        Offset(-0.7f * u - lensW / 2, lensY),
        Offset(-1.82f * u, lensY + 0.1f * u),
        strokeWidth = 0.08f * u
    )
    drawLine(
        GnomeColors.glassFrame,
        Offset(0.7f * u + lensW / 2, lensY),
        Offset(1.82f * u, lensY + 0.1f * u),
        strokeWidth = 0.08f * u
    )
}

// ── Eyebrows — dark, confident ────────────────────────────────────────────────

private fun DrawScope.drawEyebrows(u: Float) {
    val browY = -10.85f * u
    drawPath(Path().apply {
        moveTo(-1.58f * u, browY + 0.05f * u)
        cubicTo(
            -1.08f * u,
            browY - 0.2f * u,
            -0.58f * u,
            browY - 0.15f * u,
            -0.2f * u,
            browY + 0.08f * u
        )
    }, color = GnomeColors.beardShade, style = Stroke(width = 0.21f * u, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo(1.58f * u, browY + 0.05f * u)
        cubicTo(
            1.08f * u,
            browY - 0.2f * u,
            0.58f * u,
            browY - 0.15f * u,
            0.2f * u,
            browY + 0.08f * u
        )
    }, color = GnomeColors.beardShade, style = Stroke(width = 0.21f * u, cap = StrokeCap.Round))
}

// ── Hat — classic red garden gnome cone ──────────────────────────────────────
//
// Iconic red pointy hat, tilted rakishly. Gold star on the side.
// Drawn last so it covers the top of the hair naturally.

private fun DrawScope.drawHat(u: Float, beatBounce: Float) {
    val hatBaseY = -11.4f * u
    val hatBobOffset = beatBounce * (-0.15f * u)

    withTransform({
        translate(0f, hatBobOffset)
        rotate(11f, Offset(0f, hatBaseY))
    }) {
        // Cone
        val conePath = Path().apply {
            moveTo(-1.75f * u, hatBaseY)
            cubicTo(
                -1.45f * u, hatBaseY - 2.0f * u,
                -0.22f * u, hatBaseY - 4.9f * u,
                0f, hatBaseY - 5.1f * u
            )
            cubicTo(
                0.22f * u, hatBaseY - 4.9f * u,
                1.45f * u, hatBaseY - 2.0f * u,
                1.75f * u, hatBaseY
            )
            close()
        }
        drawPath(
            conePath,
            brush = Brush.verticalGradient(
                colors = listOf(GnomeColors.hatRedLight, GnomeColors.hatRed, GnomeColors.hatRedDark),
                startY = hatBaseY - 5.1f * u, endY = hatBaseY
            )
        )

        // Single flat brim on top of the cone base
        drawOval(
            color = GnomeColors.hatRedDark,
            topLeft = Offset(-2.1f * u, hatBaseY - 0.45f * u),
            size = Size(4.2f * u, 0.58f * u)
        )
    }
}

