package com.example.metrognome.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import com.example.metrognome.ui.components.AppCard
import com.example.metrognome.ui.components.AppCardDefaults
import com.example.metrognome.ui.components.AppDivider
import com.example.metrognome.ui.components.GhostButton
import com.example.metrognome.ui.components.Particle
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.components.drawParticles
import com.example.metrognome.ui.components.tapDelight
import com.example.metrognome.ui.overlays.OverlayEyebrow
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.ui.theme.ItemPalette
import com.example.metrognome.viewmodel.GameResult
import com.example.metrognome.viewmodel.rhythmStars
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Stars ─────────────────────────────────────────────────────────────────────

/** A five-point star centred on [c] with outer radius [r]. Drawn, never a text glyph. */
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

/**
 * The game's three-star rating as a still row. One drawing for the difficulty tiles on the
 * Rhythm page and (animated, in [RhythmResultPanel]) the result, so the two cannot disagree
 * about what a star looks like.
 */
@Composable
internal fun StarRating(stars: Int, starSize: Dp, modifier: Modifier = Modifier, gap: Dp = 2.dp) {
    Canvas(modifier = modifier.size(width = starSize * 3 + gap * 2, height = starSize)) {
        val r = size.height / 2f
        val step = starSize.toPx() + gap.toPx()
        repeat(3) { i -> drawStar(Offset(r + i * step, r), r, earned = i < stars) }
    }
}

// ── Result ────────────────────────────────────────────────────────────────────

private class StarBurst(val index: Int, val startNs: Long, val particles: List<Particle>)

private fun starBurst(index: Int, startNs: Long, finale: Boolean): StarBurst {
    val r = Random(index * 31 + 5)
    val palette = if (finale) AppColors.confetti else listOf(AppColors.gold, ItemPalette.goldLight, Color.White, AppColors.textAccent)
    return StarBurst(
        index, startNs,
        List(if (finale) 46 else 14) {
            val kind = when (r.nextInt(10)) { in 0..5 -> 0; in 6..7 -> 1; else -> 2 }
            Particle(
                angle = r.nextFloat() * 2f * PI.toFloat(),
                speed = (if (finale) 150f else 100f) + r.nextFloat() * (if (finale) 260f else 160f),
                size = when (kind) { 1 -> 7f + r.nextFloat() * 4f; 0 -> 4f + r.nextFloat() * 4f; else -> 2f + r.nextFloat() * 2f },
                spin = (r.nextFloat() - 0.5f) * 520f,
                spin0 = r.nextFloat() * 360f,
                life = 0.7f + r.nextFloat() * 0.5f,
                delay = r.nextFloat() * (if (finale) 0.16f else 0.05f),
                kind = kind,
                color = palette[r.nextInt(palette.size)],
            )
        },
    )
}

/**
 * The end of a game: the stars land one at a time, each with a burst of the About card's sparks
 * (a three-star run ends on a finale), the score counts up, and the hits are shown as one bar
 * split by judgement so the shape of the run reads before any number does.
 */
@Composable
internal fun RhythmResultPanel(result: GameResult?, onPlayAgain: () -> Unit, onDismiss: () -> Unit) {
    if (result == null) return
    val total = result.perfects + result.goods + result.almosts + result.misses
    // Single source of truth (shared with the Rhythm-page difficulty tiles) so the stars earned
    // for a run match the stars shown next to that difficulty's best score.
    val stars = rhythmStars(result.score, total)

    val haptics = LocalHaptics.current
    val clock = rememberStageClock()
    val starScale = remember(result) { List(3) { Animatable(0f) } }
    val shownScore = remember(result) { Animatable(0f) }
    val bursts = remember(result) { mutableListOf<StarBurst>() }

    LaunchedEffect(result) {
        delay(250)
        repeat(3) { i ->
            val earned = i < stars
            if (earned) {
                bursts.add(starBurst(i, clock.frameNs, finale = i == 2))
                haptics.fire(if (i == 2) HapticPattern.SUCCESS else HapticPattern.TICK)
            }
            starScale[i].animateTo(
                1f,
                if (earned) spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow) else tween(160),
            )
        }
    }
    LaunchedEffect(result) {
        shownScore.animateTo(result.score.toFloat(), tween(1100, delayMillis = 250, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))

        // The hero answers a tap the way the About card does.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tapDelight(AppCardDefaults.DialogShape)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OverlayEyebrow(if (result.isNewHighScore) "NEW BEST" else "RESULT", color = if (result.isNewHighScore) AppColors.gold else AppColors.textMuted)
            Spacer(Modifier.height(14.dp))

            val starSize = 58.dp
            val gap = 10.dp
            Canvas(modifier = Modifier.size(width = starSize * 3 + gap * 2, height = starSize)) {
                val r = size.height / 2f
                val step = starSize.toPx() + gap.toPx()
                val now = clock.frameNs   // the bursts move on the frame clock
                repeat(3) { i ->
                    val c = Offset(r + i * step, r)
                    // The middle star sits a little proud of the other two.
                    val lift = if (i == 1) -r * 0.16f else 0f
                    val centre = Offset(c.x, c.y + lift)
                    val earned = i < stars
                    if (earned && starScale[i].value > 0.05f) {
                        val glow = r * 1.9f
                        drawCircle(Brush.radialGradient(listOf(AppColors.goldTintActive, Color.Transparent), centre, glow), glow, centre)
                    }
                    drawStar(centre, r * starScale[i].value * (if (i == 1) 1.12f else 1f), earned)
                }
                bursts.toList().forEach { b ->
                    val age = ((now - b.startNs) / 1e9f).coerceAtLeast(0f)
                    if (age < 1.9f) drawParticles(b.particles, Offset(r + b.index * step, r), age)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "${shownScore.value.toInt()}",
                color = AppColors.textPrimary, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Black,
            )
            Text("points", color = AppColors.textMuted, fontSize = 14.sp, lineHeight = 20.sp)
        }

        Spacer(Modifier.height(18.dp))

        AppCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
            JudgementBar(result, total, Modifier.fillMaxWidth().height(10.dp))
            Spacer(Modifier.height(14.dp))
            ResultRow("Perfect", "${result.perfects}", AppColors.gold)
            ResultRow("Good", "${result.goods}", GameColors.good)
            ResultRow("Almost", "${result.almosts}", GameColors.almost)
            ResultRow("Miss", "${result.misses}", GameColors.miss)
            Spacer(Modifier.height(8.dp))
            AppDivider()
            Spacer(Modifier.height(8.dp))
            ResultRow("Best combo", "×${result.maxCombo}", AppColors.textAccent)
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton("Play again", onPlayAgain, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        GhostButton("Done", onDismiss, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

/** Every note of the run as one bar, split by how it was judged. */
@Composable
private fun JudgementBar(result: GameResult, total: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val clip = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2f))
            )
        }
        clipPath(clip) {
            drawRect(AppColors.surfaceVariant)
            if (total > 0) {
                var x = 0f
                listOf(
                    result.perfects to AppColors.gold,
                    result.goods to GameColors.good,
                    result.almosts to GameColors.almost,
                    result.misses to GameColors.miss,
                ).forEach { (count, color) ->
                    val w = size.width * count / total
                    if (w > 0f) drawRect(color, Offset(x, 0f), Size(w, size.height))
                    x += w
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
    }
}
