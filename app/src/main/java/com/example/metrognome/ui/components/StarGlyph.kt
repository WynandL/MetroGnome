package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.ui.theme.ItemPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The app's star and sparkle, drawn. A "★" or "✨" typed into a string is drawn by whatever
 * font the phone falls back to (a quarter-note character in a text field came out as a dot), so anything that
 * means "star" in the UI is one of these: the premium mark on a chip, the rhythm game's rating.
 */

/** A five-point star centred on [c] with outer radius [r]. */
internal fun starPath(c: Offset, r: Float): Path = Path().apply {
    val inner = r * 0.46f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else inner
        val a = (-PI / 2 + i * PI / 5).toFloat()
        val x = c.x + cos(a) * radius
        val y = c.y + sin(a) * radius
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Gold, lit from the upper left, when [earned]; a faint placeholder when not. */
internal fun DrawScope.drawStar(c: Offset, r: Float, earned: Boolean) {
    if (r <= 0f) return
    val path = starPath(c, r)
    if (earned) {
        drawPath(
            path,
            Brush.linearGradient(
                listOf(ItemPalette.goldLight, AppColors.gold, ItemPalette.goldMid),
                start = Offset(c.x - r, c.y - r), end = Offset(c.x + r, c.y + r),
            ),
        )
    } else {
        drawPath(path, GameColors.starEmpty)
    }
}

/** One gold star, [size] across. */
@Composable
fun StarGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        drawStar(Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension / 2f, earned = true)
    }
}

/** A cluster of three gold sparkles, [size] across: the drawn stand-in for the sparkles emoji. */
@Composable
fun SparkleGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        drawSpark(Offset(s * 0.42f, s * 0.56f), s * 0.40f, AppColors.gold)
        drawSpark(Offset(s * 0.80f, s * 0.22f), s * 0.18f, ItemPalette.goldLight)
        drawSpark(Offset(s * 0.84f, s * 0.80f), s * 0.12f, AppColors.goldSoft)
    }
}

/**
 * The rhythm game's three-star rating as a still row. One drawing for the difficulty tiles on
 * the Rhythm page and the result screen, so the two cannot disagree about what a star is.
 */
@Composable
fun StarRating(stars: Int, starSize: Dp, modifier: Modifier = Modifier, gap: Dp = 2.dp) {
    Canvas(modifier = modifier.size(width = starSize * 3 + gap * 2, height = starSize)) {
        val r = size.height / 2f
        val step = starSize.toPx() + gap.toPx()
        repeat(3) { i -> drawStar(Offset(r + i * step, r), r, earned = i < stars) }
    }
}
