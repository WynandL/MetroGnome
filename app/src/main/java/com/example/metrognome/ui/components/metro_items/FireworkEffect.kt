package com.example.metrognome.ui.components.metro_items

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Celebratory firework burst for the Gnome canvas sky.
 *
 * ── This is NOT a [MetroItem] and is deliberately absent from [METRO_ITEM_REGISTRY]. ──
 * It is not unlockable, not collectible, and bypasses the entire unlock / celebration
 * pipeline. It is a transient, *triggered* visual reward: when the player lands a very
 * accurate clap in a Practice or Speed Trainer session, GnomeCanvas spawns one of these
 * behind Metro for ~1 second. It cannot be a real MetroItem because:
 *   - the [MetroItem.draw] contract is static (no time/progress), but a firework is animated; and
 *   - registry membership means the unlock pipeline, which we explicitly do not want here.
 * It only reuses the items' sky-placement convention (absolute canvas coords, scaled by `u`).
 *
 * Visuals echo the confetti in UnlockCelebrationOverlay by reusing [AppColors.confetti].
 */

/** Cap on simultaneous bursts so a streak of perfect claps stays celebratory, not chaotic. */
const val MAX_FIREWORK_BURSTS = 4

/**
 * One live burst. [center] is an absolute canvas position (px); [seed] makes each burst's
 * spark angles/colours deterministic but varied. [progress] is animated 0 -> 1 by the caller;
 * the burst is removed when it completes.
 */
class FireworkBurst(val center: Offset, val seed: Int) {
    val progress = Animatable(0f)
}

private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * Draw a single burst at [progress] (0..1). [u] is the gnome unit (size.height/17) used to
 * scale the burst so it sits naturally in the scene. Pure draw - no state.
 */
fun DrawScope.drawFireworkBurst(progress: Float, center: Offset, u: Float, seed: Int) {
    if (progress <= 0f || progress >= 1f) return

    val rng = Random(seed)
    val sparkCount = 16 + rng.nextInt(10)             // 16..25 sparks
    val maxRadius = u * (2.4f + rng.nextFloat() * 1.3f)
    val palette = AppColors.confetti
    val colorA = palette[rng.nextInt(palette.size)]
    val colorB = palette[rng.nextInt(palette.size)]

    // Sparks fly out fast then slow (ease-out), fade as they go, and drift gently down.
    val ease = 1f - (1f - progress) * (1f - progress)
    val fade = (1f - progress).coerceIn(0f, 1f)
    val gravity = u * 1.6f * progress * progress

    // Bright launch flash in the first quarter of the burst.
    if (progress < 0.25f) {
        val flash = 1f - progress / 0.25f
        drawCircle(
            color = Color.White.copy(alpha = 0.55f * flash),
            radius = u * 0.45f * (1f + progress * 2.5f),
            center = center,
        )
    }

    for (i in 0 until sparkCount) {
        val angle = (i.toFloat() / sparkCount) * TWO_PI + rng.nextFloat() * 0.30f
        val speed = 0.70f + rng.nextFloat() * 0.50f
        val r = maxRadius * ease * speed
        val ca = cos(angle.toDouble()).toFloat()
        val sa = sin(angle.toDouble()).toFloat()

        val headX = center.x + ca * r
        val headY = center.y + sa * r + gravity
        val tail = u * 0.55f * speed
        val tailX = center.x + ca * (r - tail)
        val tailY = center.y + sa * (r - tail) + gravity

        val sparkColor = if (i % 2 == 0) colorA else colorB
        val width = (u * 0.12f * (1f - progress * 0.6f)).coerceAtLeast(1f)

        drawLine(
            color = sparkColor.copy(alpha = fade),
            start = Offset(tailX, tailY),
            end = Offset(headX, headY),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
        drawCircle(sparkColor.copy(alpha = fade), radius = width, center = Offset(headX, headY))
    }
}
