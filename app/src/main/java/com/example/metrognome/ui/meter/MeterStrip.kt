package com.example.metrognome.ui.meter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.BeatEvent
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min

/**
 * The strip's whole height on Home. 16 dp taller than the old beat row's 72 dp (the dev asked
 * for more room round the beats), all of it in the row, so the gems stay centred in it.
 */
val METER_STRIP_HEIGHT = 88.dp

private val ROW_TOP = 44.dp
private val ROW_HEIGHT = 44.dp
private val MAX_PITCH = 22.dp
private val LABEL_WIDTH = 14.dp
private val LABEL_GAP = 9.dp
private val SIDE_PAD = 8.dp

/** Where the row of gems ends, from the strip's top: the hint card hangs from here. */
val METER_ROW_BOTTOM = ROW_TOP + ROW_HEIGHT

/** While inviting, a light runs along the bar every few seconds. */
private const val SWEEP_PERIOD_S = 3.4f
private const val SWEEP_S = 1.2f
/** The glow behind the bar breathes over this period. */
private const val BREATH_S = 2.4f

/**
 * The time signature above Metro: the bar's beats as gems, grouped by its accents, with the
 * meter itself as a small stacked fraction at the left (7/8 and 7/4 have the same seven
 * beats; the fraction is what tells them apart). Tapping it opens the meter stage.
 *
 * There is no pill. While [invite] is on (Home decides: the player is idle on a stopped
 * metronome and has not yet acknowledged the hint, see [MeterHintCard]) the bar asks to be
 * touched: a soft gold glow breathes behind it and a light runs along the beats every few
 * seconds, the same motion the bar makes when it plays. Never while the metronome plays.
 *
 * Timing: each gem lights when its click is heard ([BeatPulse]), not on the early beat
 * callback. Draws in the draw phase off [BeatPulse.frameNs], so Home is not recomposed every
 * frame.
 */
@Composable
fun MeterStrip(
    top: Int,
    bottom: Int,
    accents: Set<Int>,
    isPlaying: Boolean,
    bpm: Int,
    beats: Flow<BeatEvent>,
    invite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inviting = invite && !isPlaying
    var inviteStartNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(inviting) { if (inviting) inviteStartNs = System.nanoTime() }
    val pulse = rememberBeatPulse(beats, isPlaying, bpm, keepAwake = inviting)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val numeralStyle = remember(density) {
        // A mark, not body text: sized in dp so the fraction always fits its 28 dp row.
        TextStyle(fontSize = with(density) { 11.dp.toSp() }, fontWeight = FontWeight.Bold)
    }
    val topText = remember(top, numeralStyle) { measurer.measure("$top", numeralStyle) }
    val bottomText = remember(bottom, numeralStyle) { measurer.measure("$bottom", numeralStyle) }

    BoxWithConstraints(modifier.fillMaxWidth().height(METER_STRIP_HEIGHT)) {
        val available = maxWidth - 32.dp - LABEL_WIDTH - LABEL_GAP - SIDE_PAD * 2
        val line = remember(top, accents, available, density) {
            with(density) { MeterLayout.line(top, accents, available.toPx(), MAX_PITCH.toPx()) }
        }
        val dotsWidth = with(density) { line.width.toDp() }
        val n = line.centres.size

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = ROW_TOP)
                .height(ROW_HEIGHT)
                // No clip and no ripple: a lit gem's halo and ring reach past the row, and a
                // clip cut them off flat. The stage opening is the tap's feedback.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { contentDescription = "Time signature $top/$bottom, tap to change" }
                .padding(horizontal = SIDE_PAD),
        ) {
            Canvas(Modifier.size(LABEL_WIDTH, ROW_HEIGHT)) {
                pulse.frameNs // subscribe: redraw every frame while the pulse runs
                val dp = 1.dp.toPx()
                val down = if (0 in accents) pulse.lit(0) else 0f
                val ink = lerp(AppColors.textMuted, AppColors.gold, down * 0.85f)
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawText(topText, color = ink, topLeft = Offset(cx - topText.size.width / 2f, cy - 1f * dp - topText.size.height))
                drawLine(
                    lerp(AppColors.goldBorder, AppColors.gold, down), Offset(cx - 5f * dp, cy), Offset(cx + 5f * dp, cy),
                    strokeWidth = 1.2f * dp,
                )
                drawText(bottomText, color = ink, topLeft = Offset(cx - bottomText.size.width / 2f, cy + 1f * dp))
            }
            Spacer(Modifier.width(LABEL_GAP))
            Canvas(Modifier.size(dotsWidth, ROW_HEIGHT)) {
                val now = pulse.frameNs
                val dp = 1.dp.toPx()
                val cy = size.height / 2f
                val base = min(6f * dp, line.pitch * 0.30f)

                // The invitation: a breathing glow behind the bar, and a light running along it.
                var sweep = -1f
                if (inviting) {
                    val t = ((now - inviteStartNs) / 1e9f).coerceAtLeast(0f)
                    val fadeIn = (t / 0.8f).coerceAtMost(1f)
                    val breath = 0.5f - 0.5f * cos(2f * PI.toFloat() * t / BREATH_S)
                    drawInviteGlow(cy, (0.14f + 0.22f * breath) * fadeIn)
                    val cycle = (t - 0.6f).mod(SWEEP_PERIOD_S)
                    if (t > 0.6f && cycle < SWEEP_S * 1.4f) sweep = cycle / SWEEP_S
                }

                for (i in 0 until n) {
                    val accent = i in accents
                    val x = line.centres[i]
                    var lit = pulse.lit(i)
                    if (sweep in 0f..1.4f) {
                        val d = (x / size.width) - (sweep * 1.25f - 0.12f)
                        lit = maxOf(lit, 0.55f * exp(-(d * d) / 0.012f))
                    }
                    val r = base * (if (accent) 1.14f else 1f) * (1f + 0.12f * lit + 0.22f * pulse.pop(i))
                    drawBeatGem(
                        c = Offset(x, cy), r = r, accent = accent, lit = lit,
                        ring = if (isPlaying) pulse.ring(i) else -1f,
                        glint = if (isPlaying) pulse.glint(i) else -1f,
                    )
                }
            }
        }
    }
}

/** A soft gold glow along the whole row of gems, wider than tall, fading out at its ends. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInviteGlow(cy: Float, alpha: Float) {
    val dp = 1.dp.toPx()
    val halfW = size.width / 2f + 14f * dp
    val halfH = 15f * dp
    val c = Offset(size.width / 2f, cy)
    scale(scaleX = halfW / halfH, scaleY = 1f, pivot = c) {
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                0f to AppColors.gold.copy(alpha = alpha),
                0.55f to AppColors.gold.copy(alpha = alpha * 0.45f),
                1f to androidx.compose.ui.graphics.Color.Transparent,
                center = c, radius = halfH,
            ),
            halfH, c,
        )
    }
}
