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
import androidx.compose.ui.geometry.RoundRect
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
            drawMouth(u)
            drawMustache(u)
            drawNose(u)          // nose ball sits on top of the moustache, tucked under the bridge
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
    // A fitted collar rises behind the tie; the shirt front continues between the lapels.
    drawPath(Path().apply {
        moveTo(-0.36f * u, -8.20f * u)
        lineTo(-0.50f * u, -7.62f * u)
        lineTo(0f, -7.04f * u)
        lineTo(0.50f * u, -7.62f * u)
        lineTo(0.36f * u, -8.20f * u)
        lineTo(0f, -7.91f * u)
        close()
    }, brush = Brush.linearGradient(
        colors = listOf(GnomeColors.shirt, GnomeColors.shirtShade),
        start = Offset(-0.30f * u, -8.16f * u), end = Offset(0.48f * u, -7.10f * u)
    ))
    for (side in listOf(-1f, 1f)) {
        val fold = Path().apply {
            moveTo(side * 0.36f * u, -8.20f * u)
            lineTo(0f, -7.91f * u)
            lineTo(side * 0.25f * u, -7.65f * u)
            lineTo(side * 0.43f * u, -7.92f * u)
            close()
        }
        drawPath(fold, brush = Brush.verticalGradient(
            colors = listOf(GnomeColors.shirt, GnomeColors.shirtShade),
            startY = -8.20f * u, endY = -7.65f * u
        ))
        // Rounded fabric wings, broad at their outer ends and gathered at the knot.
        val wing = Path().apply {
            moveTo(side * 0.08f * u, -7.91f * u)
            cubicTo(side * 0.26f * u, -7.98f * u, side * 0.52f * u, -8.12f * u, side * 0.58f * u, -8.12f * u)
            cubicTo(side * 0.64f * u, -8.15f * u, side * 0.58f * u, -7.68f * u, side * 0.55f * u, -7.65f * u)
            cubicTo(side * 0.50f * u, -7.62f * u, side * 0.24f * u, -7.76f * u, side * 0.08f * u, -7.80f * u)
            close()
        }
        drawPath(Path().apply { addPath(wing, Offset(0.025f * u, 0.04f * u)) },
            color = GnomeColors.tieDark.copy(alpha = 0.25f))
        drawPath(wing, brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFF4548), Color(0xFFE31C2A), GnomeColors.tieDark),
            start = Offset(side * 0.48f * u - 0.12f * u, -8.10f * u),
            end = Offset(side * 0.16f * u + 0.12f * u, -7.59f * u)
        ))
    }
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFF5355), Color(0xFFE31C2A), GnomeColors.tieDark),
            center = Offset(-0.045f * u, -7.91f * u), radius = 0.24f * u
        ),
        topLeft = Offset(-0.14f * u, -8.02f * u), size = Size(0.28f * u, 0.31f * u)
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
        brush = rollGradient(-0.35f * u, 0.35f * u, steps = 5, strength = 0.55f, color = ::goldRollColor),
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
    Offset(-HEAD_R * 0.40f * u, (HEAD_CY - HEAD_R * 0.12f) * u)

private fun headLightRadius(u: Float) = HEAD_R * 1.50f * u

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
    // A broad light from screen-left reaches the cheek and chin before falling away.
    // The longer falloff preserves modelling across the lower face instead of clamping
    // both cheeks to the same shadow colour. Ears sample this same light field.
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
    // Bigger, lower blush — reads as fuller, rounder cheeks (friendlier face)
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * 1.1f * u, cy + 0.5f * u)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GnomeColors.cheek, GnomeColors.cheek, Color.Transparent),
                center = c, radius = 0.55f * u
            ),
            radius = 0.55f * u, center = c
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
// A pointed ear: the upper edge runs almost level from the temple out to the tip, the
// lower edge is full and convex.
private fun earPath(side: Float, u: Float): Path {
    val ecy = HEAD_CY * u + 0.05f * u
    return Path().apply {
        moveTo(side * 1.56f * u, ecy - 0.46f * u)
        cubicTo(
            side * 1.90f * u, ecy - 0.52f * u,
            side * 2.25f * u, ecy - 0.48f * u,
            side * 2.50f * u, ecy - 0.42f * u
        )
        cubicTo(
            side * 2.42f * u, ecy + 0.10f * u,
            side * 2.05f * u, ecy + 0.56f * u,
            side * 1.56f * u, ecy + 0.46f * u
        )
        close()
    }
}

private fun DrawScope.drawEars(u: Float) {
    val cx = 0f
    val cy = HEAD_CY * u
    for (side in listOf(-1f, 1f)) {
        val ecy = cy + 0.05f * u
        val innerX = cx + side * 1.56f * u
        val outerX = cx + side * 2.50f * u
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
                    cx + side * 1.90f * u, ecy - 0.25f * u,
                    cx + side * 2.20f * u, ecy - 0.34f * u,
                    cx + side * 2.32f * u, ecy - 0.28f * u
                )
                cubicTo(
                    cx + side * 2.22f * u, ecy - 0.04f * u,
                    cx + side * 1.92f * u, ecy + 0.24f * u,
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
// A band of hair hugging the side of the head: its inner edge is an arc concentric with
// the head circle (just inside the silhouette, so no seam), its outer edge bulges ~0.3u
// past it. It runs from under the brim down behind the ear, which covers its lower end.
private fun hairSidePath(side: Float, u: Float): Path = Path().apply {
    moveTo(side * 1.24f * u, -11.28f * u)           // inner arc, top (under the brim)
    lineTo(side * 1.32f * u, -11.37f * u)
    cubicTo(
        side * 1.75f * u, -11.30f * u,
        side * 2.12f * u, -10.65f * u,
        side * 1.93f * u, -10.27f * u               // outer edge, down to behind the ear
    )
    lineTo(side * 1.76f * u, -10.25f * u)
    cubicTo(
        side * 1.705f * u, -10.643f * u,
        side * 1.525f * u, -11.004f * u,
        side * 1.24f * u, -11.28f * u               // back up along the head's own curve
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
    // White like the moustache — one head of hair, and it reads softer than grey
    drawPath(hairSidePath(-1f, u), color = GnomeColors.beard)
    drawPath(hairSidePath(1f, u), color = GnomeColors.beard)
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
    // Full cheeks to the nose, with a small lowered tip over the moustache parting.
    val nose = Path().apply {
        moveTo(0f, -9.88f * u)
        cubicTo(-0.28f * u, -9.90f * u, -0.46f * u, -9.67f * u, -0.42f * u, -9.43f * u)
        cubicTo(-0.40f * u, -9.28f * u, -0.24f * u, -9.29f * u, -0.14f * u, -9.20f * u)
        cubicTo(-0.06f * u, -9.11f * u, 0.06f * u, -9.11f * u, 0.14f * u, -9.20f * u)
        cubicTo(0.24f * u, -9.29f * u, 0.40f * u, -9.28f * u, 0.42f * u, -9.43f * u)
        cubicTo(0.46f * u, -9.67f * u, 0.28f * u, -9.90f * u, 0f, -9.88f * u)
        close()
    }
    drawPath(nose, brush = Brush.radialGradient(
        0f to Color(0xFFFFD094), 0.48f to Color(0xFFF3AA70), 1f to Color(0xFFC37951),
        center = Offset(-0.14f * u, -9.71f * u), radius = 0.69f * u
    ))
    drawPath(nose, brush = Brush.verticalGradient(
        0f to Color.Transparent, 0.65f to Color.Transparent, 1f to GnomeColors.noseShade.copy(alpha = 0.35f),
        startY = -9.88f * u, endY = -9.15f * u
    ))
    // Small underside creases replace the two dots on the front of the nose.
    for (side in listOf(-1f, 1f)) {
        val nostril = Path().apply {
            moveTo(side * 0.22f * u, -9.29f * u)
            cubicTo(side * 0.24f * u, -9.35f * u, side * 0.29f * u, -9.35f * u, side * 0.32f * u, -9.32f * u)
        }
        drawPath(nostril, color = GnomeColors.nostril.copy(alpha = 0.55f),
            style = Stroke(width = 0.027f * u, cap = StrokeCap.Round))
    }
}

// ── Mouth — a quiet crease tucked behind the moustache ────────────────────────

private fun DrawScope.drawMouth(u: Float) {
    val smile = Path().apply {
        moveTo(-0.30f * u, -8.89f * u)
        cubicTo(-0.14f * u, -8.79f * u, 0.14f * u, -8.79f * u, 0.30f * u, -8.89f * u)
    }
    drawPath(
        smile,
        color = GnomeColors.skinShadow.copy(alpha = 0.78f),
        style = Stroke(width = 0.035f * u, cap = StrokeCap.Round)
    )
}

// ── Moustache — two soft lobes curling inward beneath the nose ─────────────────

private fun DrawScope.drawMustache(u: Float) {
    val cy = HEAD_CY * u
    // The inner edge falls almost vertically before rounding outward. This keeps the
    // mouth tucked into a narrow parting instead of exposing a triangular wedge of skin.
    fun wing(side: Float) = Path().apply {
        moveTo(side * 0.055f * u, cy + 0.78f * u)
        cubicTo(
            side * 0.54f * u, cy + 0.58f * u,
            side * 1.23f * u, cy + 0.82f * u,
            side * 1.62f * u, cy + 1.31f * u
        )
        cubicTo(
            side * 1.76f * u, cy + 1.49f * u,
            side * 1.70f * u, cy + 1.54f * u,
            side * 1.39f * u, cy + 1.54f * u
        )
        cubicTo(
            side * 0.98f * u, cy + 1.54f * u,
            side * 0.42f * u, cy + 1.39f * u,
            side * 0.18f * u, cy + 1.17f * u
        )
        cubicTo(
            side * 0.055f * u, cy + 1.05f * u,
            side * 0.035f * u, cy + 0.93f * u,
            side * 0.055f * u, cy + 0.78f * u
        )
        close()
    }
    val wings = listOf(wing(-1f), wing(1f))
    // Layered, low-opacity offsets soften the contact shadow, clipped to the face
    // so the overhanging ends cannot cast a shadow into the sky.
    drawContext.canvas.save()
    drawContext.canvas.clipPath(Path().apply {
        addOval(Rect(Offset(-HEAD_R * u, (HEAD_CY - HEAD_R) * u), Size(2f * HEAD_R * u, 2f * HEAD_R * u)))
    })
    for (offset in listOf(0.035f, 0.065f, 0.10f)) {
        wings.forEach { wing ->
            drawPath(
                Path().apply { addPath(wing, Offset(0.02f * u, offset * u)) },
                color = GnomeColors.skinShadow.copy(alpha = 0.09f)
            )
        }
    }
    drawContext.canvas.restore()

    wings.forEachIndexed { index, wing ->
        val side = if (index == 0) -1f else 1f
        // Each lobe has its own rounded surface, under the same upper-left light.
        drawPath(
            wing,
            brush = Brush.linearGradient(
                0f to GnomeColors.beardLight,
                0.48f to lerp(GnomeColors.beardLight, GnomeColors.beard, if (side < 0f) 0.65f else 1f),
                1f to if (side < 0f) Color(0xFFC8C1C5) else Color(0xFFB7AEB9),
                start = Offset(side * 0.70f * u - 0.16f * u, cy + 0.78f * u),
                end = Offset(side * 0.92f * u + 0.10f * u, cy + 1.68f * u)
            )
        )
        drawPath(
            wing,
            brush = Brush.radialGradient(
                colors = listOf(GnomeColors.beardShade.copy(alpha = 0.38f), Color.Transparent),
                center = Offset(0f, cy + 0.80f * u),
                radius = 0.38f * u
            )
        )
    }
}
// ── Gold-frame sunglasses ─────────────────────────────────────────────────────

private fun DrawScope.drawSunglasses(u: Float) {
    // Frame outer box ~1.31u x 0.91u per lens, centred 0.21u above the head's centre, with
    // a 0.11u frame; the frames sit only ~0.38u apart at the bridge.
    val lensY = (HEAD_CY - 0.21f) * u
    val lensH = 0.80f * u
    val lensW = 1.20f * u
    val frameW = 0.11f * u

    // One gradient spanning the whole pair — temple to temple — rather than one per lens, so
    // the gold turns continuously across his face instead of each lens repeating the same
    // little ramp. Same treatment for the lenses themselves: dark glass still catches the
    // key light, and leaving them flat black while the frame turned looked like a decal.
    val goldRoll = rollGradient(-1.82f * u, 1.82f * u, strength = 0.55f, color = ::goldRollColor)
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
            cornerRadius = CornerRadius(0.2f * u), style = Stroke(width = frameW)
        )
        drawLine(
            color = GnomeColors.glassReflect,
            start = Offset(lx - lensW * 0.28f, lensY - lensH * 0.22f),
            end = Offset(lx - lensW * 0.05f, lensY + lensH * 0.12f),
            strokeWidth = 0.11f * u, cap = StrokeCap.Round
        )
    }
    val lensX = 0.79f * u
    lens(-lensX); lens(lensX)
    // Bridge — a short bar between the frames, a little below lens-centre height; the nose
    // ball tucks up between the lenses beneath it
    drawLine(
        brush = goldRoll,
        start = Offset(-lensX + lensW / 2, lensY + 0.04f * u),
        end = Offset(lensX - lensW / 2, lensY + 0.04f * u),
        strokeWidth = 0.17f * u
    )
    drawLine(
        brush = goldRoll,
        start = Offset(-lensX - lensW / 2, lensY),
        end = Offset(-1.85f * u, lensY),
        strokeWidth = 0.11f * u
    )
    drawLine(
        brush = goldRoll,
        start = Offset(lensX + lensW / 2, lensY),
        end = Offset(1.85f * u, lensY),
        strokeWidth = 0.11f * u
    )
}

// ── Eyebrows — white, soft, gently arched ─────────────────────────────────────

private fun DrawScope.drawEyebrows(u: Float) {
    // Filled arches taper at the temples, with a fuller, rounded inner end.
    for (side in listOf(-1f, 1f)) {
        val brow = Path().apply {
            moveTo(side * 1.18f * u, -10.92f * u)
            cubicTo(side * 1.12f * u, -11.06f * u, side * 0.78f * u, -11.21f * u, side * 0.54f * u, -11.14f * u)
            cubicTo(side * 0.39f * u, -11.10f * u, side * 0.31f * u, -10.98f * u, side * 0.36f * u, -10.89f * u)
            cubicTo(side * 0.40f * u, -10.83f * u, side * 0.63f * u, -10.96f * u, side * 1.13f * u, -10.90f * u)
            cubicTo(side * 1.17f * u, -10.89f * u, side * 1.19f * u, -10.90f * u, side * 1.18f * u, -10.92f * u)
            close()
        }
        drawPath(Path().apply { addPath(brow, Offset(0.015f * u, 0.025f * u)) },
            color = GnomeColors.skinShadow.copy(alpha = 0.24f))
        drawPath(brow, brush = Brush.linearGradient(
            colors = listOf(GnomeColors.beardLight, GnomeColors.beard, GnomeColors.beardShade),
            start = Offset(-0.7f * u, -11.20f * u), end = Offset(0.7f * u, -10.81f * u)
        ))
    }
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

// ── Hat — classic red garden gnome cone ──────────────────────────────────────
//
// Iconic red pointy hat, tilted rakishly.
// Drawn last so it covers the top of the hair naturally.
//
// Shading follows the same key light as the head sphere and the baton bob: from the
// upper-LEFT. Every shading layer is painted through the cone path / brim paths below, so
// the silhouette is exactly what it always was — only the fill inside it gained depth.

private const val HAT_TILT_DEG = 4f

private fun DrawScope.drawHat(u: Float, beatBounce: Float) {
    // Raked 4° rather than 11°, so the brim's front lip sits just on the brows on the low
    // side of the tilt instead of covering the whole right brow.
    val hatBaseY = -11.14f * u
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
        rotate(HAT_TILT_DEG, Offset(0f, hatBaseY))
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
        rotate(HAT_TILT_DEG, Offset(0f, hatBaseY))
    }) {

        // === SHARED GEOMETRY & BRUSHES ===
        // The cone's brushes are built first because the BRIM is painted with them too.
        //
        // Colour continuity is the whole trick at the junction. The cone's felt is a sweep
        // gradient about the apex; the brim sits a hair below the cone's base, so sampling
        // that SAME brush there lands on the same angle and therefore the same colour. The
        // two shapes meet at identical pixels and the seam has nothing left to show. The
        // brim only departs from the cone as it travels away from it (see brim() below).
        val coneTipY = hatBaseY - 5.0f * u
        val conePath = Path().apply {
            moveTo(-1.75f * u, hatBaseY)
            cubicTo(
                -1.45f * u, hatBaseY - 2.0f * u,
                -0.22f * u, hatBaseY - 4.8f * u,
                0f, coneTipY
            )
            cubicTo(
                0.22f * u, hatBaseY - 4.8f * u,
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
                    -0.22f * u, hatBaseY - 4.8f * u,
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
                    0.22f * u, hatBaseY - 4.8f * u,
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

