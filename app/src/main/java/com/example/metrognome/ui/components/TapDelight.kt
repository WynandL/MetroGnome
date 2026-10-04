package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.ItemPalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A small piece of delight for anything tappable that has no job to do: add
 * `Modifier.tapDelight()` and every touch answers back.
 *
 * What a tap does, all at once and all from this one modifier:
 *  - the content squashes under the finger and springs back with a wobble,
 *  - two rings (three on a finale) roll out from the exact touch point,
 *  - a burst of gold sparks, lilac music notes and glints flies out, arcs, and falls,
 *  - a soft sheen sweeps across the content,
 *  - a light haptic tick.
 *
 * It escalates: tap again within 1.2 s and the combo grows (more sparks, a firmer haptic);
 * every seventh tap in a streak is a finale (a ring of confetti, a rising haptic).
 *
 * It only observes touches, never consumes them, so it composes with any other gesture on
 * the same element (the About row's hidden developer-mode tap counter keeps working).
 * Particles and rings are drawn over the content and may spill past it, up to whatever ancestor
 * clips. The sheen and the touch flash are surface effects, so they are clipped to [shape] (the
 * default is the app card's) and never show as a rectangle past rounded corners.
 */
fun Modifier.tapDelight(shape: Shape = AppCardDefaults.Shape): Modifier = composed {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val bursts = remember { mutableListOf<Burst>() }
    val frame = remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    val streak = remember { LongArray(2) }   // [0] = combo, [1] = last tap (ms)

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (bursts.isNotEmpty()) {
            withFrameNanos { ns ->
                bursts.forEach { if (it.startNs < 0) it.startNs = ns }
                bursts.removeAll { (ns - it.startNs) / 1e9f > it.duration }
                frame.longValue = ns
            }
        }
        running = false
        // A tap that landed on the loop's last frame must not be left waiting for the next one.
        if (bursts.isNotEmpty()) running = true
    }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = tilt.value
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                // Initial pass and never consumed: we watch, other gestures still get the touch.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val now = System.currentTimeMillis()
                val combo = if (now - streak[1] < COMBO_WINDOW_MS) streak[0] + 1 else 1L
                streak[0] = combo
                streak[1] = now
                val finale = combo % FINALE_EVERY == 0L
                val level = min(combo, 6L).toInt()

                haptics.fire(
                    when {
                        finale -> HapticPattern.SUCCESS
                        combo >= 3 -> HapticPattern.CLICK
                        else -> HapticPattern.TICK
                    }
                )

                val rnd = Random(now)
                val count = if (finale) 54 else 12 + level * 3
                bursts.add(
                    Burst(
                        origin = down.position,
                        finale = finale,
                        level = level,
                        particles = List(count) { Particle.random(rnd, finale) },
                    )
                )
                running = true

                scope.launch {
                    scale.animateTo(if (finale) 0.92f else 0.955f, tween(70))
                    scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow))
                }
                scope.launch {
                    val dir = if (rnd.nextBoolean()) 1f else -1f
                    tilt.animateTo(dir * (1.1f + level * 0.25f), tween(70))
                    tilt.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 140f))
                }
            }
        }
        .drawWithContent {
            drawContent()
            // Read the frame clock FIRST, before any early return: this is what subscribes the
            // draw layer to frame updates. `bursts` is a plain list, so a tap alone invalidates
            // nothing; it is the clock ticking that redraws.
            val ns = frame.longValue
            if (bursts.isEmpty()) return@drawWithContent
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            bursts.toList().forEach { drawBurst(it, ns, clip) }
        }
}

private const val COMBO_WINDOW_MS = 1_200L
private const val FINALE_EVERY = 7L

// ── Model ─────────────────────────────────────────────────────────────────────

private class Burst(
    val origin: Offset,
    val finale: Boolean,
    val level: Int,
    val particles: List<Particle>,
    var startNs: Long = -1L,
) {
    val duration: Float get() = if (finale) 1.7f else 1.25f
}

private class Particle(
    val angle: Float,       // radians
    val speed: Float,       // dp/s
    val size: Float,        // dp
    val spin: Float,        // deg/s
    val spin0: Float,
    val life: Float,        // s
    val delay: Float,       // s
    val kind: Int,          // 0 spark, 1 note, 2 glint
    val color: Color,
) {
    companion object {
        private val colors = listOf(
            AppColors.gold, ItemPalette.goldLight, ItemPalette.goldMid,
            AppColors.textAccent, AppColors.mediumPurple, Color.White,
        )

        fun random(r: Random, finale: Boolean): Particle {
            val kind = when (r.nextInt(10)) { in 0..4 -> 0; in 5..6 -> 1; else -> 2 }
            // Bias upward: a fountain, not a ring, so it reads as joy rather than an explosion.
            val angle = (-PI / 2 + (r.nextFloat() - 0.5f) * (if (finale) 2f * PI else 1.5f * PI)).toFloat()
            return Particle(
                angle = angle,
                speed = (if (finale) 140f else 90f) + r.nextFloat() * (if (finale) 240f else 170f),
                size = when (kind) { 1 -> 7f + r.nextFloat() * 4f; 0 -> 4f + r.nextFloat() * 4f; else -> 2f + r.nextFloat() * 2f },
                spin = (r.nextFloat() - 0.5f) * 520f,
                spin0 = r.nextFloat() * 360f,
                life = 0.7f + r.nextFloat() * 0.5f,
                delay = if (finale) r.nextFloat() * 0.18f else r.nextFloat() * 0.05f,
                kind = kind,
                color = if (kind == 1) listOf(AppColors.textAccent, AppColors.mediumPurple, AppColors.gold)[r.nextInt(3)]
                        else colors[r.nextInt(colors.size)],
            )
        }
    }
}

// ── Drawing ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawBurst(b: Burst, frameNs: Long, surfaceClip: Path) {
    val t = if (b.startNs < 0) 0f else ((frameNs - b.startNs) / 1e9f).coerceAtLeast(0f)
    val dp = 1.dp.toPx()
    val maxR = (if (b.finale) 150f else 90f + b.level * 10f) * dp

    // Flash at the touch point.
    if (t < 0.28f) {
        val p = t / 0.28f
        clipPath(surfaceClip) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(AppColors.gold.copy(alpha = 0.55f * (1 - p)), Color.Transparent),
                    center = b.origin, radius = 46f * dp * (0.4f + p),
                ),
                radius = 46f * dp * (0.4f + p), center = b.origin,
            )
        }
    }

    // Rings.
    val rings = if (b.finale) 3 else 2
    for (k in 0 until rings) {
        val p = ((t - k * 0.09f) / (if (b.finale) 0.9f else 0.7f)).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) continue
        val eased = 1f - (1f - p).pow(3)
        drawCircle(
            color = (if (k == 1) AppColors.textAccent else AppColors.gold).copy(alpha = (1 - p).pow(1.6f) * 0.7f),
            radius = (6f + (maxR / dp - 6f) * eased) * dp * (1f - k * 0.18f),
            center = b.origin,
            style = Stroke(width = (3.2f * (1 - p) + 0.8f) * dp),
        )
    }

    // Sheen sweeping across the content.
    val sp = (t / 0.55f).coerceIn(0f, 1f)
    if (sp in 0.001f..0.999f) {
        val band = 150f * dp
        val sx = -band + (size.width + 2 * band) * sp
        clipPath(surfaceClip) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f + 0.02f * b.level), Color.Transparent),
                    start = Offset(sx - band, 0f),
                    end = Offset(sx + band, size.height * 0.6f),
                ),
                size = Size(size.width, size.height),
            )
        }
    }

    // Particles.
    val gravity = 420f * dp
    b.particles.forEach { p ->
        val age = t - p.delay
        if (age <= 0f || age >= p.life) return@forEach
        val u = age / p.life
        val drag = (1f - exp(-3f * age)) / 3f
        val x = b.origin.x + cos(p.angle) * p.speed * dp * drag
        val y = b.origin.y + sin(p.angle) * p.speed * dp * drag + 0.5f * gravity * age * age
        val pop = if (age < 0.1f) 0.4f + 1.2f * (age / 0.1f) - 0.6f * (age / 0.1f).pow(2) else 1f
        val alpha = min(1f, (1f - u) * 2.6f)
        val r = p.size * dp * pop * (1f - 0.35f * u)
        val c = p.color.copy(alpha = alpha)
        val spin = p.spin0 + p.spin * age
        when (p.kind) {
            0 -> rotate(spin, Offset(x, y)) { drawSpark(Offset(x, y), r, c) }
            1 -> rotate(spin * 0.25f, Offset(x, y)) { drawNote(Offset(x, y), r, c) }
            else -> {
                drawCircle(c.copy(alpha = alpha * 0.35f), r * 2.2f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = alpha), r * 0.8f, Offset(x, y))
            }
        }
    }
}

/** Four-point glint with concave sides. */
private fun DrawScope.drawSpark(c: Offset, r: Float, color: Color) {
    val n = r * 0.2f
    val path = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x + n, c.y - n, c.x + r, c.y)
        quadraticTo(c.x + n, c.y + n, c.x, c.y + r)
        quadraticTo(c.x - n, c.y + n, c.x - r, c.y)
        quadraticTo(c.x - n, c.y - n, c.x, c.y - r)
        close()
    }
    drawPath(path, color)
}

/** A single eighth note: head, stem and flag. */
private fun DrawScope.drawNote(c: Offset, r: Float, color: Color) {
    drawOval(color, Offset(c.x - r * 0.55f, c.y - r * 0.35f + r * 0.6f), Size(r * 1.1f, r * 0.75f))
    val sx = c.x + r * 0.5f
    drawLine(color, Offset(sx, c.y + r * 0.9f), Offset(sx, c.y - r * 1.2f), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
    val flag = Path().apply {
        moveTo(sx, c.y - r * 1.2f)
        quadraticTo(sx + r * 0.95f, c.y - r * 0.8f, sx + r * 0.6f, c.y - r * 0.1f)
        quadraticTo(sx + r * 0.7f, c.y - r * 0.6f, sx, c.y - r * 0.7f)
        close()
    }
    drawPath(flag, color)
}
