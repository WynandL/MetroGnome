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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import com.example.metrognome.ui.components.metro_items.FireworkBurst
import com.example.metrognome.ui.components.metro_items.drawFireworkBurst
import com.example.metrognome.ui.components.metro_items.MAX_FIREWORK_BURSTS
import com.example.metrognome.ui.components.metro_items.items.drawSparkle
import com.example.metrognome.ui.theme.ItemPalette
import com.example.metrognome.ui.theme.GnomeColors
import com.example.metrognome.viewmodel.BeatEvent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
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
    modifier: Modifier = Modifier,
    accentBeats: Set<Int> = emptySet(),
    activeItems: List<MetroItem> = emptyList(),
    onItemTapped: (MetroItem) -> Unit = {},
    // Optional: each emission spawns a celebratory firework in the sky (a very accurate clap in
    // Practice / Speed Trainer). Null by default, so previews and the plain metronome are
    // unaffected. NOT part of the item/unlock system - see FireworkEffect.kt.
    greatHitSignal: Flow<Unit>? = null,
) {
    val currentBpm by rememberUpdatedState(bpm)

    // Live firework bursts. Each great hit adds one (capped); it self-removes when its
    // progress animation completes. Drawn behind Metro in the sky.
    val fireworkBursts = remember { mutableStateListOf<FireworkBurst>() }
    val burstScope = rememberCoroutineScope()
    val canvasSize = remember { mutableStateOf(Size.Zero) }
    if (greatHitSignal != null) {
        LaunchedEffect(greatHitSignal) {
            greatHitSignal.collect {
                val s = canvasSize.value
                if (s == Size.Zero || fireworkBursts.size >= MAX_FIREWORK_BURSTS) return@collect
                val center = Offset(
                    s.width * (0.18f + Random.nextFloat() * 0.64f),
                    s.height * (0.10f + Random.nextFloat() * 0.28f),
                )
                val burst = FireworkBurst(center, Random.nextInt())
                fireworkBursts.add(burst)
                burstScope.launch {
                    burst.progress.animateTo(1f, tween(1100, easing = LinearEasing))
                    fireworkBursts.remove(burst)
                }
            }
        }
    }

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
                    val maxFlash = if (event.beat in accentBeats) 0.7f else 0.35f
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

        // Celebratory fireworks (great-clap reward) — deep sky, behind every item and Metro.
        fireworkBursts.forEach { burst ->
            drawFireworkBurst(burst.progress.value, burst.center, u, burst.seed)
        }

        // Background items (scene decoration — not body-attached), behind Metro
        activeItems.filter { !it.isBodyAttached && !it.isForeground }.forEach { item ->
            with(item) { draw(u, canvasCx, canvasBaseY) }
        }

        if (flash.value > 0f) {
            drawRect(color = GnomeColors.beatGlowAccent.copy(alpha = flash.value * 0.4f))
        }

        drawGnome(
            pendulumAngle = effectivePendulum,
            beatBounce = bounce.value,
            breathOffset = effectiveBreath,
            bodyItems = activeItems.filter { it.isBodyAttached },
            u = u,
            cx = canvasCx,
            baseY = canvasBaseY
        )

        // Foreground background items — drawn last so they appear in front of Metro's body/shoes.
        activeItems.filter { !it.isBodyAttached && it.isForeground }.forEach { item ->
            with(item) { draw(u, canvasCx, canvasBaseY) }
        }
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

// internal (not private): also drawn by MetroAvatar.kt as the single shared source of
// Metro's likeness. Any change here updates both the animated on-screen gnome and every
// static avatar use — do not fork this function.
internal fun DrawScope.drawGnome(
    pendulumAngle: Float = 0f,
    beatBounce: Float = 0f,
    breathOffset: Float = 0f,
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
        drawLegs(u)
        drawShoes(u)
        drawLeftArm(u)
        drawBody(u)
        drawBelt(u)
        drawButtons(u)
        drawBaton(u, pendulumAngle)
        drawRightArm(u)

        // ── Head group — bobs on every beat ───────────────────────────────
        val headBob = beatBounce * u * 0.2f
        withTransform({ translate(0f, headBob) }) {
            drawNeck(u)
            drawHead(u)
            drawHair(u)
            drawEars(u)
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
        // Drawn after the head group so the collar sits in front of the neck
        drawShirtCollar(u)
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

private const val SHOE_SPAN = 2.32f       // outermost toe reach either side, in u
private const val SHOE_LEAD_LIFT = 0.14f  // fresh surface each shoe turns toward the light

private fun DrawScope.drawShoes(u: Float) {
    // hx centres each shoe over its leg (±0.62u). The mouth of the shoe is cut to the
    // trousers' own width and alignment: the leg spans ±0.20u..±1.04u, so the opening runs
    // from hx - 0.62u (= ±0.20u) to hx + 0.22u (= ±1.04u). It used to span only
    // ±0.27u..±0.80u — narrower than the leg on BOTH sides — so the trouser overhung the
    // shoe at the ankle and he looked too fat-legged for his own footwear.
    fun shoe(side: Float) {
        val hx = side * 0.82f * u
        val path = Path().apply {
            moveTo(hx - side * 0.62f * u, -0.05f * u)   // inner heel
            lineTo(hx + side * 1.2f * u, -0.05f * u)   // sole base toward toe
            cubicTo(
                hx + side * 1.5f * u, -0.05f * u,
                hx + side * 1.58f * u, -0.38f * u,
                hx + side * 1.32f * u, -0.60f * u        // toe tip
            )
            cubicTo(
                hx + side * 0.82f * u, -0.52f * u,
                hx + side * 0.48f * u, -0.46f * u,
                hx + side * 0.22f * u, -0.56f * u
            )
            lineTo(hx - side * 0.62f * u, -0.56f * u)   // back to heel
            close()
        }
        // Shaded from ONE falloff spanning both shoes, not a ramp restarting inside each.
        // Two independent ramps gave each shoe its own highlight, which is what you get from
        // two light sources — under a single light the pair reads as one continuous fall from
        // screen-left to screen-right. Each shoe then gets a lift at its screen-left end:
        // that end is a fresh surface turned toward the light, so the second shoe's heel
        // picks back up to roughly where the first shoe's heel left off instead of
        // continuing straight down into black.
        //
        // The global term is compressed to 0.6 for exactly that reason — at full strength
        // the far toe on the right lands on shoeDark and the shoe stops being red.
        val x0 = minOf(hx - side * 0.62f * u, hx + side * 1.5f * u)
        val x1 = maxOf(hx - side * 0.62f * u, hx + side * 1.5f * u)
        val steps = 10
        val stops = ArrayList<Pair<Float, Color>>(steps + 1)
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = x0 + (x1 - x0) * t
            val global = rollLambert((x / (SHOE_SPAN * u)).coerceIn(-1f, 1f))
            val lift = SHOE_LEAD_LIFT * (1f - (t / 0.5f).coerceAtMost(1f))
            val shade = (0.5f + (global - 0.5f) * 0.6f + lift).coerceIn(0f, 1f)
            stops.add(t to shoeRollColor(shade))
        }
        drawPath(path, brush = Brush.horizontalGradient(*stops.toTypedArray(), startX = x0, endX = x1))
        // Glossy toe highlight
        drawOval(
            color = GnomeColors.shoeGloss,
            topLeft = Offset(hx + side * 0.65f * u, -0.53f * u),
            size = Size(side * 0.42f * u, 0.15f * u)
        )
        // Cream sole edge
        drawLine(
            color = GnomeColors.shoeSole,
            start = Offset(hx - side * 0.62f * u, -0.04f * u),
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
    // Runs on DOWN to -0.35u, well past the shoes' top edge at -0.56u, and drawGnome now
    // draws the legs before the shoes so the overlap is hidden. They used to stop at -0.6u
    // and be drawn on top, which left the trousers hanging in the air above the shoe: the
    // shoe's upper line dips to -0.46u around the instep, so even where the two nominally
    // met there was a gap of open background between them.
    val botY = -0.35f * u
    val h = botY - topY
    // Each leg is its own cylinder, so each gets its own ramp across its own width. That is
    // the opposite call from the lapels and the bow tie, and for the opposite reason: those
    // are one object in two halves, these are genuinely two objects, so two highlights is
    // what a viewer expects rather than a symmetry error.
    fun leg(xCenter: Float) {
        drawRoundRect(
            brush = rollGradient(
                xCenter - 0.42f * u, xCenter + 0.42f * u, steps = 6, color = ::pantsRollColor
            ),
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

// Hands are small spheres a long way from the head, so they cannot use the face's
// head-centred falloff — they get their own miniature sphere gradient instead, lit from the
// same upper-left so they still belong to the same scene.
private fun DrawScope.drawHand(u: Float, handX: Float, handY: Float) {
    val r = 0.34f * u
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(GnomeColors.skinHighlight, GnomeColors.skin, GnomeColors.skinDark),
            center = Offset(handX - r * 0.35f, handY - r * 0.35f), radius = r * 1.35f
        ),
        radius = r, center = Offset(handX, handY)
    )
}

// ── Left arm ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawLeftArm(u: Float) {
    val shoulderX = -1.75f * u
    val shoulderY = -6.4f * u
    val handX = -2.7f * u
    val handY = -3.8f * u
    // ONE smooth cubic from shoulder to hand, replacing the two that used to meet at an
    // elbow point. The centreline of the old pair was continuous, but the forearm segment
    // put its control point further left than BOTH its endpoints, so the curve bulged out
    // and came back — and that inflection pinches the inner side of a stroke this thick into
    // a visible corner. The kink was in the stroke's outline, not the path's joint, which is
    // why nudging the elbow would never have found it. Passes within 0.05u of where the old
    // elbow sat, so the arm keeps its shape.
    drawPath(
        Path().apply {
            moveTo(shoulderX, shoulderY)
            cubicTo(
                -2.40f * u, -5.95f * u,
                -2.78f * u, -4.80f * u,
                handX, handY
            )
        },
        brush = rollGradient(handX - 0.45f * u, shoulderX + 0.45f * u, steps = 6) { shade ->
            lerp(GnomeColors.jacketDark, GnomeColors.jacket, shade)
        },
        style = Stroke(width = 0.82f * u, cap = StrokeCap.Round)
    )
    drawLine(
        color = GnomeColors.shirt,
        start = Offset(handX - 0.28f * u, handY - 0.22f * u),
        end = Offset(handX + 0.28f * u, handY - 0.22f * u),
        strokeWidth = 0.12f * u,
        cap = StrokeCap.Round
    )
    drawHand(u, handX, handY)
}

// ── Right arm (holds baton) ───────────────────────────────────────────────────

private fun DrawScope.drawRightArm(u: Float) {
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
        brush = rollGradient(shoulderX - 0.45f * u, handX + 0.45f * u, steps = 6) { shade ->
            lerp(GnomeColors.jacketDark, GnomeColors.jacket, shade)
        },
        style = Stroke(width = 0.82f * u, cap = StrokeCap.Round)
    )
    drawLine(
        color = GnomeColors.shirt,
        start = Offset(handX - 0.28f * u, handY - 0.22f * u),
        end = Offset(handX + 0.28f * u, handY - 0.22f * u),
        strokeWidth = 0.12f * u,
        cap = StrokeCap.Round
    )
    drawHand(u, handX, handY)
}

// ── Conducting baton ──────────────────────────────────────────────────────────

private fun DrawScope.drawBaton(u: Float, pendulumAngle: Float) {
    withTransform({
        translate(2.0f * u, -4.5f * u)
        rotate(pendulumAngle * 40f, Offset.Zero)
    }) {
        val batonLen = 4.2f * u
        val rodW     = 0.18f * u

        // ── Polished gold rod — cross-width sheen (bright left → dark right) ───
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(ItemPalette.goldLight, GnomeColors.batonGold, GnomeColors.batonDark),
                start = Offset(-rodW / 2f, 0f), end = Offset(rodW / 2f, 0f)
            ),
            start = Offset(0f, 0f), end = Offset(0f, batonLen),
            strokeWidth = rodW, cap = StrokeCap.Round
        )
        // Bright highlight stripe just left of centre
        drawLine(
            color = ItemPalette.goldLight.copy(alpha = 0.9f),
            start = Offset(-rodW * 0.24f, 0.20f * u), end = Offset(-rodW * 0.24f, batonLen - 0.45f * u),
            strokeWidth = rodW * 0.26f, cap = StrokeCap.Round
        )
        // A small glint catching the light partway up the rod
        drawSparkle(center = Offset(-rodW * 0.18f, 1.30f * u), radius = rodW * 0.5f, color = Color.White.copy(alpha = 0.8f))

        // ── Calibration ticks — engraved (dark cut + light bevel) ─────────────
        for (i in 0 until 4) {
            val ty = 0.55f * u + i * 0.22f * u
            drawLine(GnomeColors.batonDark, Offset(-0.11f * u, ty), Offset(0.11f * u, ty), strokeWidth = 0.06f * u)
            drawLine(ItemPalette.goldLight.copy(alpha = 0.5f), Offset(-0.11f * u, ty - 0.025f * u), Offset(0.11f * u, ty - 0.025f * u), strokeWidth = 0.02f * u)
        }

        // ── Bob — polished gold sphere ────────────────────────────────────────
        val ballR = 0.38f * u
        // Joint shadow where the rod meets the bob
        drawCircle(GnomeColors.batonDark.copy(alpha = 0.45f), radius = rodW * 0.55f, center = Offset(0f, batonLen - ballR * 0.92f))
        // Sphere body — lit from the upper-left (in local space)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ItemPalette.goldLight, GnomeColors.batonGold, ItemPalette.goldDark),
                center = Offset(-ballR * 0.35f, batonLen - ballR * 0.35f), radius = ballR * 1.5f
            ),
            radius = ballR, center = Offset(0f, batonLen)
        )
        // Faint rim light on the lower-right
        drawArc(
            color = ItemPalette.goldLight.copy(alpha = 0.5f),
            startAngle = 25f, sweepAngle = 75f, useCenter = false,
            topLeft = Offset(-ballR * 0.85f, batonLen - ballR * 0.85f),
            size = Size(ballR * 1.7f, ballR * 1.7f),
            style = Stroke(width = rodW * 0.22f, cap = StrokeCap.Round)
        )
        // Specular dot + sparkle
        drawCircle(Color.White.copy(alpha = 0.7f), radius = ballR * 0.22f, center = Offset(-ballR * 0.38f, batonLen - ballR * 0.40f))
        drawSparkle(center = Offset(-ballR * 0.32f, batonLen - ballR * 0.34f), radius = ballR * 0.52f, color = Color.White.copy(alpha = 0.85f))
    }
}

// ── Body — near-black pinstripe suit ─────────────────────────────────────────

private fun DrawScope.drawBody(u: Float) {
    val bodyRect = Rect(Offset(-1.8f * u, -7.6f * u), Size(3.6f * u, 4.0f * u))
    val jacketClip = Path().apply { addOval(bodyRect) }

    // Jacket base — a torso is a roll like everything else, so it takes the shared ramp
    // rather than the diagonal linear gradient it used to have.
    drawPath(jacketClip, brush = rollGradient(-1.8f * u, 1.8f * u, color = ::clothRollColor))
    // Pinstripes clipped to the jacket oval so they follow the body contour
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
    // NO edge treatment on the torso, deliberately. Three were tried and all failed the same
    // way: a horizontal band lit a slab down the whole right side and left the shadow side
    // reading lighter than the middle; a constant-alpha contour stroke read as a halo drawn
    // around the shape; and fading that stroke along the contour still read as an applied
    // effect rather than as light. The lesson is the hat brim's: a specular edge needs a
    // surface with enough detail to sit on, and a flat near-black oval has none — so the
    // roll-off in the fill is left to carry the form on its own, which it does honestly.
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
    // Lapels. One gradient spanning BOTH, not one each: they are two halves of a single
    // garment opening, and shading them separately mirrors the highlight and makes the chest
    // look symmetrically lit from two directions at once.
    val lapelRoll = rollGradient(-1.45f * u, 1.45f * u, steps = 8, strength = 0.85f) { shade ->
        lerp(clothRollColor(shade), GnomeColors.jacketRim, 0.22f * shade)
    }
    // The shadow each lapel drops onto the chest beneath it — they sit proud of the front.
    for (lapel in listOf(leftLapel, rightLapel)) {
        drawPath(
            Path().apply { addPath(lapel, Offset(0.04f * u, 0.07f * u)) },
            color = GnomeColors.jacketDark.copy(alpha = 0.75f)
        )
    }
    drawPath(leftLapel, brush = lapelRoll)
    drawPath(rightLapel, brush = lapelRoll)
    drawPath(leftLapel, color = GnomeColors.jacketDark, style = Stroke(width = 0.05f * u))
    drawPath(rightLapel, color = GnomeColors.jacketDark, style = Stroke(width = 0.05f * u))
    // Pocket square — white, dapper. Low strength: it is small and bright, exactly the case
    // where a full-range ramp stops reading as lit and starts reading as soiled.
    drawPath(
        Path().apply {
            moveTo(-1.42f * u, -6.82f * u)
            lineTo(-1.12f * u, -6.92f * u)
            lineTo(-1.02f * u, -6.52f * u)
            lineTo(-1.32f * u, -6.42f * u)
            close()
        },
        brush = rollGradient(-1.42f * u, -1.02f * u, steps = 4, strength = 0.45f) { shade ->
            lerp(GnomeColors.shirtShade, GnomeColors.shirt, shade)
        }
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
        brush = rollGradient(-0.52f * u, 0.52f * u, steps = 6, strength = 0.65f) { shade ->
            lerp(GnomeColors.shirtShade, GnomeColors.shirt, shade)
        }
    )
    // Bow tie. Both wings and the knot share ONE gradient spanning the whole tie, so it
    // turns as a single object — shading each wing separately would give the pair a mirrored
    // highlight and read as two ties rather than one.
    val tieRoll = rollGradient(-0.5f * u, 0.5f * u, steps = 8, strength = 0.50f) { shade ->
        when {
            shade < 0.5f -> lerp(GnomeColors.tieDark, GnomeColors.tie, shade / 0.5f)
            else -> lerp(GnomeColors.tie, GnomeColors.tieLight, (shade - 0.5f) / 0.5f)
        }
    }
    drawPath(Path().apply {
        moveTo(-0.05f * u, -7.82f * u); lineTo(
        -0.5f * u,
        -7.62f * u
    ); lineTo(-0.5f * u, -8.02f * u); close()
    }, brush = tieRoll)
    drawPath(Path().apply {
        moveTo(0.05f * u, -7.82f * u); lineTo(
        0.5f * u,
        -7.62f * u
    ); lineTo(0.5f * u, -8.02f * u); close()
    }, brush = tieRoll)
    // The knot stands proud of the wings, so it reads a touch lighter than the ramp alone.
    drawCircle(
        brush = rollGradient(-0.12f * u, 0.12f * u, steps = 4, strength = 0.55f) { shade ->
            lerp(GnomeColors.tie, GnomeColors.tieLight, shade * 0.7f)
        },
        radius = 0.12f * u, center = Offset(0f, -7.82f * u)
    )
}

// ── Belt ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBelt(u: Float) {
    drawRect(
        brush = rollGradient(-1.25f * u, 1.25f * u, steps = 8) { shade ->
            lerp(GnomeColors.belt, GnomeColors.jacket, shade)
        },
        topLeft = Offset(-1.25f * u, -4.05f * u),
        size = Size(2.5f * u, 0.55f * u)
    )
    // Buckle: gold, and small, so a gentle strength — the sunglasses frame taught this one.
    drawRect(
        brush = rollGradient(-0.35f * u, 0.35f * u, steps = 5, strength = 0.45f, color = ::goldRollColor),
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
    // Each button is its own little dome, so each gets its own ramp across its own width —
    // unlike the lapels or the bow tie, these genuinely are separate objects and should each
    // carry their own highlight. Gentle strength, as with every small gold piece on him.
    val r = 0.13f * u
    val gold = rollGradient(-r, r, steps = 5, strength = 0.50f, color = ::goldRollColor)
    for (y in listOf(-5.05f * u, -5.75f * u, -6.45f * u)) {
        drawCircle(brush = gold, radius = r, center = Offset(0f, y))
        drawCircle(GnomeColors.jacketDark, radius = 0.06f * u, center = Offset(0f, y))
    }
}

// ── Neck ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawNeck(u: Float) {
    val top = -8.5f * u
    val bottom = -7.72f * u
    // The neck sits behind and below the face, in its shadow, so it never gets near the
    // skin tones the cheeks use — its whole range runs from skinShadow to skinDark. It is
    // also a cylinder, so it takes the same left-to-right roll as everything else, just
    // across a narrow span.
    drawRoundRect(
        brush = rollGradient(-0.38f * u, 0.38f * u, steps = 6, strength = 0.75f) { shade ->
            lerp(GnomeColors.skinShadow, GnomeColors.skinDark, shade)
        },
        topLeft = Offset(-0.38f * u, top),
        size = Size(0.76f * u, 0.78f * u),
        cornerRadius = CornerRadius(0.15f * u)
    )
    // Shadow cast down onto it by the jaw immediately above.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(GnomeColors.skinShadow.copy(alpha = 0.75f), Color.Transparent),
            startY = top, endY = bottom - 0.12f * u
        ),
        topLeft = Offset(-0.38f * u, top),
        size = Size(0.76f * u, 0.78f * u),
        cornerRadius = CornerRadius(0.15f * u)
    )
}

// ── Head ──────────────────────────────────────────────────────────────────────
//
// The head's sphere ramp is defined once here and shared with the ears, which need to
// agree with it exactly at the point they meet — the same colour-continuity problem the
// hat's brim had against its cone. Anything else on the face that must sit in the same
// light samples these rather than guessing at a matching gradient of its own.

private const val HEAD_R = 1.85f      // head radius, in u
private const val HEAD_CY = -10.0f    // head centre height, in u

/** Where the key light lands on the head sphere, and how far its falloff reaches. */
private fun headLightCenter(u: Float) =
    Offset(-HEAD_R * 0.30f * u, (HEAD_CY - HEAD_R * 0.30f) * u)

private fun headLightRadius(u: Float) = HEAD_R * 1.18f * u

/**
 * The head's three-stop skin ramp, evaluated at falloff fraction [t].
 *
 * Matches the head circle's radial gradient exactly for t in 0..1 (Compose spaces three
 * stops evenly), then KEEPS GOING past 1 into [GnomeColors.skinShadow]. The extension is
 * what the ears need: they sit past the head's own silhouette, where the circle's gradient
 * has already clamped flat at skinDark, so without it an ear is one uniform slab and has no
 * profile left to share with the face.
 */
private fun skinSphereColor(t: Float): Color = when {
    t < 0.5f -> lerp(GnomeColors.skinHighlight, GnomeColors.skin, t / 0.5f)
    t < 1.0f -> lerp(GnomeColors.skin, GnomeColors.skinDark, (t - 0.5f) / 0.5f)
    else -> lerp(GnomeColors.skinDark, GnomeColors.skinShadow, ((t - 1f) / 0.6f).coerceAtMost(1f))
}

/** Falloff fraction of a point on (or beside) the face — what [skinSphereColor] takes. */
private fun skinFalloffAt(x: Float, y: Float, u: Float): Float {
    val c = headLightCenter(u)
    val dx = x - c.x
    val dy = y - c.y
    return (sqrt(dx * dx + dy * dy) / headLightRadius(u)).coerceAtLeast(0f)
}

private fun DrawScope.drawHead(u: Float) {
    val cx = 0f
    val cy = HEAD_CY * u
    // Head sphere. The falloff is tighter than it was (1.18r rather than 1.3r, centred
    // further up-left) so the face actually turns from left to right instead of sitting in
    // one flat wash — it was the only large form on him still reading as evenly lit.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(GnomeColors.skinHighlight, GnomeColors.skin, GnomeColors.skinDark),
            center = headLightCenter(u), radius = headLightRadius(u)
        ),
        radius = HEAD_R * u, center = Offset(cx, cy)
    )
    // Cheek blush. Faded rather than flat-filled: as a constant-alpha circle it had a hard
    // rim, which went unnoticed on an evenly-lit face but reads as a stuck-on disc now that
    // the face turns underneath it.
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * 1.05f * u, cy + 0.45f * u)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GnomeColors.cheek, GnomeColors.cheek, Color.Transparent),
                center = c, radius = 0.48f * u
            ),
            radius = 0.48f * u, center = c
        )
    }
}

// ── Ears ──────────────────────────────────────────────────────────────────────

// Each ear is shaded by sampling the HEAD's own sphere ramp along the ear's own span, then
// lifting the whole thing a few shades. That gives two things at once: at the inner edge the
// ear meets the cheek at (nearly) the colour the cheek already is, so there is no step where
// they join — the same continuity the hat's brim needed against its cone — and because the
// light centre sits left of him, the falloff runs further on his left ear than his right, so
// that ear darkens with the cheek behind it instead of staying a bright flap against a
// shaded face. The lift ramps in from ZERO at the inner edge rather than being uniform: a
// uniform one is a step at the very junction it is meant to hide, so the ear starts at
// exactly the cheek's colour and only lifts as it comes forward off the head.
private const val EAR_LIFT = 0.14f

// Shared with the hat, which needs the ear outlines for its cast-shadow clip.
private fun earPath(side: Float, u: Float): Path {
    val ecy = HEAD_CY * u + 0.1f * u
    return Path().apply {
        moveTo(side * 1.56f * u, ecy - 0.48f * u)
        cubicTo(
            side * 1.85f * u, ecy - 0.55f * u,
            side * 2.40f * u, ecy - 0.70f * u,
            side * 2.54f * u, ecy - 0.56f * u
        )
        cubicTo(
            side * 2.40f * u, ecy - 0.38f * u,
            side * 1.95f * u, ecy + 0.38f * u,
            side * 1.56f * u, ecy + 0.48f * u
        )
        close()
    }
}

private fun DrawScope.drawEars(u: Float) {
    val cx = 0f
    val cy = HEAD_CY * u
    for (side in listOf(-1f, 1f)) {
        val ecy = cy + 0.1f * u
        val innerX = cx + side * 1.56f * u
        val outerX = cx + side * 2.54f * u
        val innerCol = skinSphereColor(skinFalloffAt(innerX, ecy, u))
        val outerCol =
            lerp(skinSphereColor(skinFalloffAt(outerX, ecy, u)), GnomeColors.skinHighlight, EAR_LIFT)
        drawPath(
            earPath(side, u),
            brush = Brush.horizontalGradient(
                colors = if (side < 0f) listOf(outerCol, innerCol) else listOf(innerCol, outerCol),
                startX = minOf(innerX, outerX), endX = maxOf(innerX, outerX)
            )
        )
        drawPath(
            Path().apply {
                moveTo(cx + side * 1.70f * u, ecy - 0.18f * u)
                cubicTo(
                    cx + side * 1.90f * u, ecy - 0.20f * u,
                    cx + side * 2.28f * u, ecy - 0.44f * u,
                    cx + side * 2.38f * u, ecy - 0.38f * u
                )
                cubicTo(
                    cx + side * 2.26f * u, ecy - 0.22f * u,
                    cx + side * 1.92f * u, ecy + 0.20f * u,
                    cx + side * 1.70f * u, ecy + 0.18f * u
                )
                close()
            },
            // Semi-transparent so the inner fold darkens whatever the ear already is,
            // rather than stamping one fixed tone that would vanish on his left ear now
            // that ear runs close to skinDark by itself.
            color = GnomeColors.skinShadow.copy(alpha = 0.55f)
        )
    }
}

// ── Grey side-parted hair ─────────────────────────────────────────────────────
//
// Drawn after the head circle so it sits on top of the head edges.
// The hat (drawn last) will naturally cover the top portion.
// Hair peeks out on the sides and at the forehead — classic corporate side part.

// The side falls are mirror images, so one builder serves both. Pulled out as a function
// because drawHat needs the same geometry: the brim's cast shadow is clipped to the head
// PLUS this hair (see hatShadowSurface), otherwise the hair sits lit outside a shadowed face.
private fun hairSidePath(side: Float, u: Float): Path = Path().apply {
    moveTo(side * 1.42f * u, -11.52f * u)
    cubicTo(
        side * 1.68f * u, -11.15f * u,
        side * 2.08f * u, -10.72f * u,
        side * 2.12f * u, -10.1f * u
    )
    cubicTo(
        side * 1.96f * u, -10.0f * u,
        side * 1.65f * u, -10.08f * u,
        side * 1.52f * u, -10.38f * u
    )
    cubicTo(
        side * 1.46f * u, -10.88f * u,
        side * 1.28f * u, -11.28f * u,
        side * 1.18f * u, -11.48f * u
    )
    close()
}

/**
 * Head sphere ∪ both hair falls ∪ both ears — everything the hat's brim casts its shadow
 * onto. Each piece earned its place by being visibly wrong when left out: the hair sat lit
 * beside a shadowed face, and the ears (which reach under the brim just as the cheeks do)
 * left a step at the exact ear/cheek junction the ear shading works to erase.
 */
private fun hatShadowSurface(u: Float): Path {
    var acc = Path().apply {
        addOval(Rect(Offset(-1.85f * u, -11.85f * u), Size(3.7f * u, 3.7f * u)))
    }
    for (part in listOf(
        hairSidePath(-1f, u), hairSidePath(1f, u), earPath(-1f, u), earPath(1f, u)
    )) {
        val next = Path()
        next.op(acc, part, PathOperation.Union)
        acc = next
    }
    return acc
}

private fun DrawScope.drawHair(u: Float) {
    // Sides — hair falls from under hat brim, alongside head
    drawPath(hairSidePath(-1f, u), color = GnomeColors.hairGrey)
    drawPath(hairSidePath(1f, u), color = GnomeColors.hairGrey)
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
    val topLeft = Offset(-0.44f * u, -9.72f * u)
    val size = Size(0.88f * u, 0.72f * u)
    // Ball of the nose, lit from the upper-left like the head sphere it sits on. The old
    // highlight was centred and nearly as wide as the nose itself, which washed the whole
    // shape out flat; pulling it up-left and adding a shadow end gives it a turn.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                GnomeColors.skinHighlight,
                GnomeColors.nosePink,
                GnomeColors.noseShade
            ),
            center = Offset(-0.17f * u, -9.58f * u), radius = 0.80f * u
        ),
        topLeft = topLeft, size = size
    )
    // Terminator down the shadow side.
    drawOval(
        brush = Brush.linearGradient(
            0.50f to Color.Transparent,
            1.00f to GnomeColors.noseShade.copy(alpha = 0.55f),
            start = Offset(-0.44f * u, -9.72f * u),
            end = Offset(0.44f * u, -9.00f * u)
        ),
        topLeft = topLeft, size = size
    )
    // Soft catch of light on the ball. Kept broad and low-contrast — skin is only
    // semi-matte, and a tight specular dot here reads as plastic the way the hat's did.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                GnomeColors.skinHighlight.copy(alpha = 0.75f),
                Color.Transparent,
            ),
            center = Offset(-0.15f * u, -9.54f * u), radius = 0.30f * u
        ),
        topLeft = topLeft, size = size
    )
    // Nostrils. Smaller than they were — at the old size they widened the nose into a snout —
    // and softened rather than flat-filled: a hole reads as a hole because it deepens toward
    // its middle, not because it has a crisp rim. They also sit under the same key light as
    // everything else, so the right one runs deeper: it is on the shadow side of the ball,
    // where there is less light finding its way in.
    fun nostril(cx: Float, depth: Float) {
        val w = 0.073f * u
        val h = 0.063f * u
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    GnomeColors.nostril.copy(alpha = depth),
                    GnomeColors.nostril.copy(alpha = depth * 0.86f),
                    GnomeColors.nostril.copy(alpha = depth * 0.30f),
                ),
                center = Offset(cx - 0.01f * u, -9.30f * u), radius = 0.055f * u
            ),
            topLeft = Offset(cx - w / 2f, -9.30f * u - h / 2f),
            size = Size(w, h)
        )
    }
    nostril(-0.19f * u, 0.46f)
    nostril(0.19f * u, 0.56f)
}

// ── Full Santa moustache ──────────────────────────────────────────────────────
//
// Wide, drooping, off-white — connects naturally into the beard below.
//
// Shaded as ONE entity spanning left to right, not as two wings that happen to touch.
// Both reasons match the hat's: a single unioned path has no internal edge to leave a
// seam on, and shading that is positional rather than per-part carries continuously
// across the middle. The key light is the same upper-left one the hat, head and baton
// use — which matters most here, because this is the largest bright mass on him and a
// flat fill on it was reading as a paper cut-out stuck to his face.

private fun DrawScope.drawMustache(u: Float) {
    val baseY = -9.12f * u
    val topY = baseY - 0.12f * u
    val botY = baseY + 0.62f * u

    fun wing(side: Float) = Path().apply {
        moveTo(side * 0.08f * u, baseY)
        cubicTo(
            side * 0.45f * u, baseY - 0.12f * u,
            side * 1.35f * u, baseY - 0.08f * u,
            side * 1.58f * u, baseY + 0.42f * u
        )
        cubicTo(
            side * 1.45f * u, baseY + 0.62f * u,
            side * 0.78f * u, baseY + 0.55f * u,
            side * 0.35f * u, baseY + 0.44f * u
        )
        cubicTo(side * 0.12f * u, baseY + 0.36f * u, 0f, baseY + 0.25f * u, side * 0.08f * u, baseY)
        close()
    }
    val moustache = Path().apply { op(wing(-1f), wing(1f), PathOperation.Union) }

    // Shadow it casts onto the cheeks and chin. Same construction as the hat's, including
    // the same trap: an offset copy whose hard top edge hides under the moustache itself,
    // with only the softly fading lower crescent visible — and clipped to the head sphere,
    // because the moustache is WIDER than his face at this height (±1.58u of moustache
    // against a head only ~1.08u across down here), so its tips overhang open sky and an
    // unclipped shadow hangs there in mid-air. Nudged right as well as down, since the
    // light comes from the left.
    drawContext.canvas.save()
    drawContext.canvas.clipPath(
        Path().apply { addOval(Rect(Offset(-1.85f * u, -11.85f * u), Size(3.7f * u, 3.7f * u))) }
    )
    drawPath(
        Path().apply { addPath(moustache, Offset(0.03f * u, 0.13f * u)) },
        brush = Brush.verticalGradient(
            colors = listOf(
                GnomeColors.skinDark.copy(alpha = 0.55f),
                Color.Transparent,
            ),
            startY = botY - 0.10f * u, endY = botY + 0.15f * u
        )
    )
    drawContext.canvas.restore()

    // Key light across the whole span, sampled from the SAME Lambert term the hat cone uses,
    // with x across the moustache standing in for position around the roll. That is what
    // makes this read as the same light rather than merely a similar one: the bright band
    // lands left of centre and the right edge falls right off, exactly as it does on the hat.
    drawPath(moustache, brush = rollGradient(-1.58f * u, 1.58f * u, color = ::beardRollColor))
    // It droops, so it is a roll of hair rather than a flat shape: crown catching light,
    // underside turning away beneath it.
    drawPath(
        moustache,
        brush = Brush.verticalGradient(
            0.00f to Color.Transparent,
            0.38f to Color.Transparent,
            1.00f to GnomeColors.beardShade.copy(alpha = 0.58f),
            startY = topY, endY = botY
        )
    )
    // The nose sits directly above and casts down onto it. This also does the work of the
    // centre parting — the two halves meet right under the nose, so the same shadow reads
    // as both, and stacking a separate parting on top only muddies the middle.
    drawPath(
        moustache,
        brush = Brush.radialGradient(
            colors = listOf(
                GnomeColors.beardShade.copy(alpha = 0.50f),
                GnomeColors.beardShade.copy(alpha = 0.22f),
                Color.Transparent,
            ),
            center = Offset(-0.05f * u, topY + 0.04f * u), radius = 0.80f * u
        )
    )
}

// ── Gold-frame sunglasses ─────────────────────────────────────────────────────

private fun DrawScope.drawSunglasses(u: Float) {
    val lensY = -10.3f * u
    val lensH = 0.62f * u
    val lensW = 1.1f * u

    // One gradient spanning the whole pair — temple to temple — rather than one per lens, so
    // the gold turns continuously across his face instead of each lens repeating the same
    // little ramp. Same treatment for the lenses themselves: dark glass still catches the
    // key light, and leaving them flat black while the frame turned looked like a decal.
    val goldRoll = rollGradient(-1.82f * u, 1.82f * u, strength = 0.45f, color = ::goldRollColor)
    val lensRoll = rollGradient(-1.82f * u, 1.82f * u, strength = 0.40f) { shade ->
        lerp(GnomeColors.glassLens, GnomeColors.glassLensLit, shade)
    }

    fun lens(lx: Float) {
        drawRoundRect(
            brush = lensRoll,
            topLeft = Offset(lx - lensW / 2, lensY - lensH / 2), size = Size(lensW, lensH),
            cornerRadius = CornerRadius(0.2f * u)
        )
        drawRoundRect(
            brush = goldRoll,
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
        brush = goldRoll,
        start = Offset(-0.15f * u, lensY),
        end = Offset(0.15f * u, lensY),
        strokeWidth = 0.08f * u
    )
    drawLine(
        brush = goldRoll,
        start = Offset(-0.7f * u - lensW / 2, lensY),
        end = Offset(-1.82f * u, lensY + 0.1f * u),
        strokeWidth = 0.08f * u
    )
    drawLine(
        brush = goldRoll,
        start = Offset(0.7f * u + lensW / 2, lensY),
        end = Offset(1.82f * u, lensY + 0.1f * u),
        strokeWidth = 0.08f * u
    )
}

// ── Eyebrows — dark, confident ────────────────────────────────────────────────

// Shading only, deliberately no cast shadow: eyebrows lie flat against the brow, so there
// is nothing for them to stand proud of and cast onto. What they do get is the same
// left-to-right turn as the moustache — driven by the same rollLambert — so the pair of
// hair features on his face agree with each other and with the hat above them. The ramp is
// centred on the existing eyebrow colour, lifting slightly on the lit side and deepening on
// the shadow side, rather than making them lighter or darker overall.
private fun browRollColor(shade: Float): Color = lerp(
    lerp(GnomeColors.beardShade, GnomeColors.hairDark, 0.70f),
    lerp(GnomeColors.beardShade, GnomeColors.beard, 0.35f),
    shade
)

private fun DrawScope.drawEyebrows(u: Float) {
    val browY = -10.85f * u
    val brow = rollGradient(-1.58f * u, 1.58f * u, steps = 8, color = ::browRollColor)

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
    }, brush = brow, style = Stroke(width = 0.21f * u, cap = StrokeCap.Round))
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
    }, brush = brow, style = Stroke(width = 0.21f * u, cap = StrokeCap.Round))
}

// ── Cone shading model ───────────────────────────────────────────────────────
//
// A cone is NOT a sphere or a cylinder: its surface converges to a point, so its shading
// bands must converge to the apex too. Shading it with parallel vertical bands (a plain
// horizontal gradient) leaves the base correctly lit while the narrow tip sits in a single
// flat mid-tone, and the mismatch between the two is immediately readable as "wrong".
//
// So the hat's felt is painted with a SWEEP gradient centred on the apex: angle around the
// apex maps one-to-one onto position around the cone's base circle, which is exactly the
// parameterisation a cone's surface wants. Every band then radiates from the tip for free.

private const val KEY_LIGHT_X = -0.62f   // key light from the upper-LEFT, as head & baton
private const val KEY_LIGHT_Z = 0.78f    // ...and mostly toward the viewer

/**
 * Lambert term for a point on any horizontally-rolled surface, at position [nx] across its
 * width (-1 = left silhouette edge, 0 = facing the viewer, +1 = right edge).
 *
 * Shared deliberately: the hat cone and the moustache are both rolls lit by the same key
 * light, so driving them from one function is what keeps their shading genuinely consistent
 * instead of merely similar. Note the curve peaks around nx = -0.6 and falls back off at
 * nx = -1 — the lit edge still turns away from the viewer, which is why an evenly-brightening
 * left-to-right ramp never looks quite right on either of them.
 */
private fun rollLambert(nx: Float): Float {
    val nz = sqrt((1f - nx * nx).coerceAtLeast(0f))
    return (nx * KEY_LIGHT_X + nz * KEY_LIGHT_Z).coerceIn(0f, 1f)
}

/**
 * A left-to-right brush across [fromX]..[toX] whose colours come from [rollLambert] fed
 * through [color]. This is the standard way anything on Metro that reads as a horizontal
 * roll gets its shading — moustache, eyebrows, glasses, neck, collar, bow tie — so they all
 * turn under one light rather than each approximating it with a hand-picked ramp.
 */
private fun rollGradient(
    fromX: Float,
    toX: Float,
    steps: Int = 10,
    strength: Float = 1f,
    color: (Float) -> Color,
): Brush {
    val stops = ArrayList<Pair<Float, Color>>(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()          // 0 at his right (screen left) → 1 at his left
        // [strength] compresses the Lambert range toward its middle. The raw term runs the
        // full way to its extremes at the silhouette edges, which is right across something
        // as large as the hat but far too much across something the size of a lens or a bow
        // tie — at full strength those hit the ends of their ramps and stop reading as lit,
        // starting to read as bruised or grubby instead. Small elements dial it down.
        val raw = rollLambert(-1f + 2f * t)
        stops.add(t to color((0.5f + (raw - 0.5f) * strength).coerceIn(0f, 1f)))
    }
    return Brush.horizontalGradient(*stops.toTypedArray(), startX = fromX, endX = toX)
}

/** Maps a Lambert term onto the hat's felt ramp: deep shadow → dark → base red → lit red. */
private fun coneFeltColor(shade: Float): Color = when {
    shade < 0.35f -> lerp(GnomeColors.hatShadow, GnomeColors.hatRedDark, shade / 0.35f)
    shade < 0.70f -> lerp(GnomeColors.hatRedDark, GnomeColors.hatRed, (shade - 0.35f) / 0.35f)
    else -> lerp(GnomeColors.hatRed, GnomeColors.hatRedLight, (shade - 0.70f) / 0.30f)
}

/**
 * Onto the near-black suit cloth.
 *
 * The range is deliberately narrow at the dark end and generous at the light end. Cloth this
 * close to black has almost nowhere to go downward — jacket to jacketDark is a handful of
 * levels — so pushing the shadow side achieves nothing visible while the lit side is where
 * all the available contrast lives. This is why the suit looked so flat: not a missing
 * gradient, but a gradient with nothing to say.
 */
private fun clothRollColor(shade: Float): Color = when {
    shade < 0.5f -> lerp(GnomeColors.jacketDark, GnomeColors.jacket, shade / 0.5f)
    else -> lerp(GnomeColors.jacket, GnomeColors.jacketLight, (shade - 0.5f) / 0.5f)
}

/** Onto the trousers, which are a shade cooler and darker than the jacket. */
private fun pantsRollColor(shade: Float): Color =
    lerp(GnomeColors.pants, GnomeColors.pantsHighlight, shade)

/** Onto the red Oxford leather. */
private fun shoeRollColor(shade: Float): Color = when {
    shade < 0.5f -> lerp(GnomeColors.shoeDark, GnomeColors.shoe, shade / 0.5f)
    else -> lerp(GnomeColors.shoe, GnomeColors.shoeLight, (shade - 0.5f) / 0.5f)
}

/** Onto polished gold: the sunglasses frame. */
private fun goldRollColor(shade: Float): Color = when {
    shade < 0.5f -> lerp(ItemPalette.goldDark, GnomeColors.glassFrame, shade / 0.5f)
    else -> lerp(GnomeColors.glassFrame, ItemPalette.goldLight, (shade - 0.5f) / 0.5f)
}

/** The same mapping onto the moustache's hair ramp: grey shadow → off-white → lit white. */
private fun beardRollColor(shade: Float): Color = when {
    shade < 0.45f -> lerp(GnomeColors.beardShade, GnomeColors.beard, shade / 0.45f)
    else -> lerp(GnomeColors.beard, GnomeColors.beardLight, (shade - 0.45f) / 0.55f)
}

// ── Hat — classic red garden gnome cone ──────────────────────────────────────
//
// Iconic red pointy hat, tilted rakishly.
// Drawn last so it covers the top of the hair naturally.
//
// Shading follows the same key light as the head sphere and the baton bob: from the
// upper-LEFT. Every shading layer is painted through the cone path / brim paths below, so
// the silhouette is exactly what it always was — only the fill inside it gained depth.

private fun DrawScope.drawHat(u: Float, beatBounce: Float) {
    val hatBaseY = -11.1f * u
    val hatBobOffset = beatBounce * (-0.15f * u)

    // === CONTACT SHADOW ===
    // drawHat runs after drawHair, so this lands on the hair/forehead and reads as the brim
    // physically resting on his head rather than floating in front of it. Two things it has
    // to get right, and both are easy to get wrong:
    //
    // 1. It must not leave him. The brim is 2.1u wide but the head is only ~1.5u across at
    //    brim height — a brim overhangs — so an unclipped shadow hangs in the sky beside his
    //    hair. The clip is head sphere PLUS both hair falls: clipping to the head alone left
    //    the hair lit outside a shadowed face, which is exactly as unnatural as the spill it
    //    was preventing. Taken in HEAD space, before the hat's 11° rake is applied, while the
    //    shadow itself is drawn in hat space so it still hugs the tilted brim.
    // 2. It must start AT the brim's edge and reach the full width of the face. Drawn as its
    //    own ellipse it shows a hard elliptical top edge with a strip of lit hair above it —
    //    reading as a second brim painted on his face — and its lower arc curls back up at
    //    the sides, so the shadow pinched inward and left his temples lit. A broad band in
    //    HAT space fixes both: it is drawn BEFORE the hat, so the hat itself hides the top
    //    edge and the shadow emerges exactly where the felt meets him, and it spans wider
    //    than the head at every height, so the clip alone decides where it ends — which is
    //    precisely the face's own circumference.
    drawContext.canvas.save()
    drawContext.canvas.clipPath(hatShadowSurface(u))
    withTransform({
        translate(0f, hatBobOffset)
        rotate(11f, Offset(0f, hatBaseY))
    }) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(GnomeColors.hatContact, Color.Transparent),
                startY = hatBaseY - 0.10f * u,
                endY = hatBaseY + 0.80f * u
            ),
            topLeft = Offset(-2.6f * u, hatBaseY - 0.10f * u),
            size = Size(5.2f * u, 0.95f * u)
        )
    }
    drawContext.canvas.restore()

    withTransform({
        translate(0f, hatBobOffset)
        rotate(11f, Offset(0f, hatBaseY))
    }) {

        // === SHARED GEOMETRY & BRUSHES ===
        // The cone's brushes are built first because the BRIM is painted with them too.
        //
        // Colour continuity is the whole trick at the junction. The cone's felt is a sweep
        // gradient about the apex; the brim sits a hair below the cone's base, so sampling
        // that SAME brush there lands on the same angle and therefore the same colour. The
        // two shapes meet at identical pixels and the seam has nothing left to show. The
        // brim only departs from the cone as it travels away from it (see brim() below).
        val coneTipY = hatBaseY - 5.1f * u
        val conePath = Path().apply {
            moveTo(-1.75f * u, hatBaseY)
            cubicTo(
                -1.45f * u, hatBaseY - 2.0f * u,
                -0.22f * u, hatBaseY - 4.9f * u,
                0f, coneTipY
            )
            cubicTo(
                0.22f * u, hatBaseY - 4.9f * u,
                1.45f * u, hatBaseY - 2.0f * u,
                1.75f * u, hatBaseY
            )
            close()
        }
        // A sweep gradient about the apex makes the light and shadow bands radiate from the
        // tip exactly the way a cone's surface does. Stops are sampled from the Lambert term
        // across the base circle: right silhouette edge → facing the viewer → left edge.
        // Past the cone's own angular span the end stops clamp, which is what lets the brim
        // wings (wider than the cone) keep the colour the cone's edge arrived at.
        val edgeDeg = Math.toDegrees(
            atan2((hatBaseY - coneTipY).toDouble(), (1.75f * u).toDouble())
        ).toFloat()
        val fRight = edgeDeg / 360f            // sweep fraction of the right base corner
        val fLeft = (180f - edgeDeg) / 360f    // ...and of the left one
        val steps = 12
        val stops = ArrayList<Pair<Float, Color>>(steps + 3)
        stops.add(0f to coneFeltColor(rollLambert(1f)))
        for (i in 0..steps) {
            val s = i / steps.toFloat()        // 0 at the right edge → 1 at the left edge
            stops.add((fRight + (fLeft - fRight) * s) to coneFeltColor(rollLambert(1f - 2f * s)))
        }
        stops.add(1f to coneFeltColor(rollLambert(-1f)))
        val feltSweep = Brush.sweepGradient(*stops.toTypedArray(), center = Offset(0f, coneTipY))

        // Occlusion where the cone tucks into the brim: a long soft pass so the felt sinks
        // gradually into shadow, and a tight one right at the base. Both clamp past their end
        // stops, so painting them onto the brim as well continues the darkening seamlessly
        // rather than restarting it — again, no step at the junction.
        val aoLong = Brush.verticalGradient(
            colors = listOf(Color.Transparent, GnomeColors.hatShadow.copy(alpha = 0.48f)),
            startY = hatBaseY - 1.75f * u, endY = hatBaseY
        )
        val aoTight = Brush.verticalGradient(
            colors = listOf(Color.Transparent, GnomeColors.hatShadow.copy(alpha = 0.30f)),
            startY = hatBaseY - 0.42f * u, endY = hatBaseY
        )

        // === THE FELT — CONE AND BRIM AS ONE SILHOUETTE ===
        // Cone ∪ brim, unioned, then shaded in a single set of passes. This is what finally
        // killed the hairline along the join, and it is worth explaining because two more
        // obvious approaches both fail:
        //
        //   · Clipping the brim into halves. Android does not antialias clip edges, and the
        //     join is a near-horizontal line (the worst case) raked over by the 11° tilt, so
        //     it came out visibly stair-stepped.
        //   · Drawing the halves as separate antialiased paths, abutting or overlapping.
        //     Better, but still leaves a 1px line, because ANY opaque repaint next to
        //     already-shaded pixels reproduces it: at the new path's antialiased edge the
        //     coverage is ~50%, so that row gets half fresh unshaded felt blended over the
        //     shaded pixels beneath, and then only half of the occlusion re-applied on top.
        //     The row ends up with roughly half the shading of its neighbours and reads as a
        //     lighter line. Overlap cannot cure it; it just moves it.
        //
        // With one path there is no internal edge for either failure to happen on. The
        // shading is positional (angle about the apex, height, distance out to the wings)
        // rather than per-part, so it crosses the old join without knowing it was ever there.
        val brimTopLeft = Offset(-2.1f * u, hatBaseY - 0.45f * u)
        val brimSize = Size(4.2f * u, 0.58f * u)
        val brimRect = Rect(brimTopLeft, brimSize)
        val hatFelt = Path().apply {
            op(Path().apply { addOval(brimRect) }, conePath, PathOperation.Union)
        }

        drawPath(hatFelt, brush = feltSweep)
        drawPath(hatFelt, brush = aoLong)
        drawPath(hatFelt, brush = aoTight)
        // The brim then falls away from the cone's colour as it travels out from it. Two
        // falloffs that cannot collide, because the wings live entirely beyond |x| = 1.75u
        // and the front lip entirely below y = hatBaseY — so neither touches the cone:
        //   · wings — outward from the cone's base corners to the tips. Asymmetric, because
        //     the two wings are not in the same light: the left one is the nearest part of
        //     the whole hat to the key light and simply catches it, while the right one is
        //     turning away and falls into shadow. Lighting the left wing this way — broadly,
        //     across the whole sliver — replaced a hard specular stroke along its top edge.
        //     That stroke fought this very gradient (darkening the tip, then painting a
        //     bright line back onto it) and read as plastic rather than felt, which is matte
        //     and has no business carrying a mirror highlight in the first place.
        drawPath(
            hatFelt,
            brush = Brush.horizontalGradient(
                0.0000f to GnomeColors.hatRedLight.copy(alpha = 0.30f),
                0.0833f to Color.Transparent,
                0.9167f to Color.Transparent,
                1.0000f to GnomeColors.hatShadow.copy(alpha = 0.62f),
                startX = -2.1f * u, endX = 2.1f * u
            )
        )
        //   · front lip — downward from the join to the felt's outer edge
        drawPath(
            hatFelt,
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, GnomeColors.hatShadow.copy(alpha = 0.62f)),
                startY = hatBaseY, endY = hatBaseY + 0.14f * u
            )
        )
        // The tip catches more light than the body. A vertical wash rather than a radial
        // bloom: the cone is only a fraction of a unit wide up here, so a wash stays inside
        // the taper instead of stamping a circle onto a shape that has no circles in it.
        drawPath(
            conePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    GnomeColors.hatRedRim.copy(alpha = 0.30f),
                    Color.Transparent,
                ),
                startY = coneTipY, endY = coneTipY + 1.4f * u
            )
        )

        // The rim light is the one layer that still needs a clip: it is a stroke ON the
        // contour, so without one it would straddle the edge and fatten the silhouette.
        drawContext.canvas.save()
        drawContext.canvas.clipPath(conePath)
        // Crisp rim light tracing the lit contour, fading out before it reaches the brim.
        drawPath(
            Path().apply {
                moveTo(-1.72f * u, hatBaseY - 0.55f * u)
                cubicTo(
                    -1.45f * u, hatBaseY - 2.0f * u,
                    -0.22f * u, hatBaseY - 4.9f * u,
                    0f, coneTipY
                )
            },
            brush = Brush.verticalGradient(
                colors = listOf(
                    GnomeColors.hatRedRim.copy(alpha = 0.80f),
                    GnomeColors.hatRedRim.copy(alpha = 0.40f),
                    Color.Transparent,
                ),
                startY = coneTipY, endY = hatBaseY - 0.3f * u
            ),
            style = Stroke(width = 0.10f * u, cap = StrokeCap.Round)
        )
        // Cool ambient bounce off the night sky down the shadow-side contour. Strongest low,
        // where the most sky wraps around him. This is the edge that had been dark red on
        // dark blue, so the hat's own silhouette went missing along it.
        drawPath(
            Path().apply {
                moveTo(1.72f * u, hatBaseY - 0.55f * u)
                cubicTo(
                    1.45f * u, hatBaseY - 2.0f * u,
                    0.22f * u, hatBaseY - 4.9f * u,
                    0f, coneTipY
                )
            },
            brush = Brush.verticalGradient(
                0.00f to Color.Transparent,
                0.45f to GnomeColors.skyRim.copy(alpha = 0.20f),
                1.00f to GnomeColors.skyRim.copy(alpha = 0.40f),
                startY = coneTipY, endY = hatBaseY
            ),
            style = Stroke(width = 0.075f * u, cap = StrokeCap.Round)
        )

        drawContext.canvas.restore()

    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF1A1040, widthDp = 360, heightDp = 500)
@Composable
private fun GnomeCanvasIdlePreview() {
    GnomeCanvas(
        bpm = 120,
        isPlaying = false,
        beatEvents = MutableSharedFlow(),
        flashOnBeat = false,
        modifier = Modifier.fillMaxSize()
    )
}

