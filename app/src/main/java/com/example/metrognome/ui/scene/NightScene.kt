package com.example.metrognome.ui.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.metrognome.ui.theme.GameColors
import kotlin.math.sin
import kotlin.random.Random

/*
 * The moonlit night the app's "stage" screens share: the Rhythm game's lane (RhythmLane.kt)
 * and the sound stage (ui/sounds/). Moved here verbatim from RhythmLane on 2026-11-19 so a
 * second stage could not grow its own slightly different sky; the drone is meant to reuse it
 * next. Nothing here carries timing information except [drawNightSky]'s beatGlow, which only
 * breathes the moon's halo with a click that is already sounding.
 */

/** One twinkling star, in fractions of the canvas, with its own twinkle rate and phase. */
internal class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

/**
 * [count] stars scattered over the top [heightFraction] of the sky. Seeded, so a stage draws
 * the same sky every time it opens. (The Rhythm lane builds its own list from a shared
 * Random with its fireflies, so its sky is unchanged by this helper existing.)
 */
internal fun starField(seed: Int, count: Int = 46, heightFraction: Float = 0.58f): List<Star> {
    val r = Random(seed)
    return List(count) {
        Star(r.nextFloat(), r.nextFloat() * heightFraction, 0.5f + r.nextFloat() * 1.1f, 0.5f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f)
    }
}

/**
 * Sky gradient, stars and the moon, over a [w] x [h] canvas. [t] is seconds (ambience only),
 * [beatGlow] 0..1 swells the moon's halo on a click (1 at the click, easing to 0).
 * [moonX]/[moonY] are fractions of the canvas, defaulting to where the lane has always had it.
 */
internal fun DrawScope.drawNightSky(
    stars: List<Star>,
    w: Float,
    h: Float,
    dp: Float,
    t: Float,
    beatGlow: Float,
    moonX: Float = 0.83f,
    moonY: Float = 0.105f,
) {
    drawRect(Brush.verticalGradient(listOf(GameColors.stageSkyTop, GameColors.stageSkyBottom)))

    stars.forEach { s ->
        val tw = sin(t * s.speed + s.phase)
        drawCircle(Color.White.copy(alpha = 0.18f + 0.62f * tw * tw), s.size * dp, Offset(s.x * w, s.y * h))
    }

    // The moon breathes with the beat: its halo swells on each click and settles before the next.
    val moon = Offset(w * moonX, h * moonY)
    val mr = 17f * dp
    val halo = mr * (2.9f + 0.7f * beatGlow)
    drawCircle(
        Brush.radialGradient(listOf(GameColors.moon.copy(alpha = 0.20f + 0.10f * beatGlow), Color.Transparent), moon, halo),
        halo, moon,
    )
    drawCircle(GameColors.moon, mr, moon)
    drawCircle(GameColors.moonShade, mr * 0.20f, Offset(moon.x - mr * 0.30f, moon.y - mr * 0.22f))
    drawCircle(GameColors.moonShade, mr * 0.26f, Offset(moon.x + mr * 0.32f, moon.y + mr * 0.30f))
    drawCircle(GameColors.moonShade, mr * 0.11f, Offset(moon.x + mr * 0.22f, moon.y - mr * 0.42f))
}

/** Pines rising from the bottom edge on both sides, a far row and a near row. */
internal fun DrawScope.drawPineForest(w: Float, h: Float) {
    val far = floatArrayOf(0.02f, 0.13f, 0.235f, 0.765f, 0.87f, 0.98f)
    val farH = floatArrayOf(0.30f, 0.36f, 0.27f, 0.28f, 0.37f, 0.31f)
    for (i in far.indices) drawPine(far[i] * w, h, farH[i] * h, w * 0.17f, GameColors.stagePineFar)
    val near = floatArrayOf(-0.02f, 0.085f, 0.915f, 1.02f)
    val nearH = floatArrayOf(0.50f, 0.40f, 0.42f, 0.52f)
    for (i in near.indices) drawPine(near[i] * w, h, nearH[i] * h, w * 0.22f, GameColors.stagePineNear)
}

private fun DrawScope.drawPine(x: Float, baseY: Float, height: Float, width: Float, color: Color) {
    val top = baseY - height
    val hw = width / 2f
    val path = Path().apply {
        moveTo(x, top)
        lineTo(x + hw * 0.46f, top + height * 0.36f)
        lineTo(x + hw * 0.22f, top + height * 0.36f)
        lineTo(x + hw * 0.74f, top + height * 0.66f)
        lineTo(x + hw * 0.36f, top + height * 0.66f)
        lineTo(x + hw, baseY)
        lineTo(x - hw, baseY)
        lineTo(x - hw * 0.36f, top + height * 0.66f)
        lineTo(x - hw * 0.74f, top + height * 0.66f)
        lineTo(x - hw * 0.22f, top + height * 0.36f)
        lineTo(x - hw * 0.46f, top + height * 0.36f)
        close()
    }
    drawPath(path, color)
}
