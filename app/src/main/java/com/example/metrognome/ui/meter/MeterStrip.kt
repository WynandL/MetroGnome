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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.AppRadius
import com.example.metrognome.viewmodel.BeatEvent
import kotlinx.coroutines.flow.Flow
import kotlin.math.exp
import kotlin.math.min

/** The strip's whole height on Home: exactly the old beat row's (48 + 20 + 4), so Metro keeps his. */
val METER_STRIP_HEIGHT = 72.dp

private val ROW_TOP = 44.dp
private val ROW_HEIGHT = 28.dp
private val MAX_PITCH = 22.dp
private val LABEL_WIDTH = 14.dp
private val LABEL_GAP = 9.dp
private val SIDE_PAD = 8.dp

/** Every 6.5 s while the hint is on, a light runs along the bar once. */
private const val HINT_PERIOD_S = 6.5f
private const val HINT_SWEEP_S = 1.2f

/**
 * The time signature above Metro: the bar's beats as gems, grouped by its accents, with the
 * meter itself as a small stacked fraction at the left (7/8 and 7/4 have the same seven
 * beats; the fraction is what tells them apart). Tapping it opens the meter stage.
 *
 * There is no pill: the fraction is what says "this is the time signature", and until the
 * stage has been opened once ([showHint]) a soft light runs along the resting bar every few
 * seconds while the metronome is stopped, lighting each beat in turn, the same motion the
 * bar makes when it plays. It stops for good once the player has found the stage.
 *
 * Timing: lights on the beat callback ([beats]), the same moment Metro's baton swings, so the
 * strip and Metro never disagree. Draws in the draw phase off [BeatPulse.frameNs], so Home is
 * not recomposed every frame.
 */
@Composable
fun MeterStrip(
    top: Int,
    bottom: Int,
    accents: Set<Int>,
    isPlaying: Boolean,
    bpm: Int,
    beats: Flow<BeatEvent>,
    showHint: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hintOn = showHint && !isPlaying
    // The frame loop wakes only for each sweep, so the pause between them costs nothing.
    var sweepStartNs by remember { mutableLongStateOf(0L) }
    var sweeping by remember { mutableStateOf(false) }
    LaunchedEffect(hintOn) {
        sweeping = false
        if (!hintOn) return@LaunchedEffect
        delay(1_500)
        while (true) {
            sweepStartNs = System.nanoTime()
            sweeping = true
            delay((HINT_SWEEP_S * 1.4f * 1000).toLong())
            sweeping = false
            delay(((HINT_PERIOD_S - HINT_SWEEP_S * 1.4f) * 1000).toLong())
        }
    }
    val pulse = rememberBeatPulse(beats, isPlaying, bpm, keepAwake = hintOn && sweeping)
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

                // The hint: a soft light running along the resting bar.
                val sweep = if (hintOn && sweeping) (now - sweepStartNs) / 1e9f / HINT_SWEEP_S else -1f

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
