package com.example.metrognome.ui.game

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.components.GhostButton
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.overlays.OverlayEyebrow
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.viewmodel.GamePhase
import com.example.metrognome.viewmodel.HitQuality
import com.example.metrognome.viewmodel.RhythmChart
import com.example.metrognome.viewmodel.RhythmGameViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * The rhythm game while it is being played: the countdown and the game share this one stage, so
 * the lane the player is about to watch is already there while the numbers count down.
 *
 * Top to bottom: score / notes left / combo, the microphone strip (mic mode only), the lane
 * (see RhythmLane.kt), the timing meter, the TAP key and Stop.
 */
@Composable
internal fun RhythmStage(vm: RhythmGameViewModel, phase: GamePhase, countDown: Int) {
    val score by vm.score.collectAsStateWithLifecycle()
    val combo by vm.combo.collectAsStateWithLifecycle()
    val lastQuality by vm.lastQuality.collectAsStateWithLifecycle()
    val beatsRemaining by vm.beatsRemaining.collectAsStateWithLifecycle()
    val chart by vm.chart.collectAsStateWithLifecycle()
    val useMic by vm.useMic.collectAsStateWithLifecycle()
    val micAmplitude by vm.micAmplitude.collectAsStateWithLifecycle()
    // Read inside the draw block only: sixty updates a second must not recompose the stage.
    val visibleNotes = vm.visibleNotes.collectAsStateWithLifecycle()

    val playing = phase == GamePhase.PLAYING
    val clock = rememberStageClock()
    val scenery = remember { LaneScenery() }
    val bursts = remember { mutableListOf<LaneBurst>() }
    val recentHits = remember { mutableListOf<MeterTick>() }

    // Bumped by every judgement, so two PERFECTs in a row each flash the ring.
    val judgements = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        vm.hitEvents.collect { e ->
            judgements.intValue++
            val now = clock.frameNs
            bursts.removeAll { (now - it.startNs) / 1e9f > it.durationS }
            bursts.add(laneBurstFor(e, now))
            if (recentHits.size >= METER_TICKS) recentHits.removeAt(0)
            recentHits.add(MeterTick(e.offsetMs, e.quality, now))
        }
    }
    // A new game starts with a clean meter.
    LaunchedEffect(chart) { if (chart == null) { recentHits.clear(); bursts.clear() } }

    // The ring takes the judgement's colour for a moment, then settles back.
    val ringFlash = remember { Animatable(0f) }
    LaunchedEffect(lastQuality) { if (lastQuality == HitQuality.MISS) judgements.intValue++ }
    LaunchedEffect(judgements.intValue) {
        if (judgements.intValue > 0) {
            ringFlash.snapTo(1f)
            ringFlash.animateTo(0f, tween(420))
        }
    }
    val clapFlash = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        vm.micDetected.collect {
            clapFlash.snapTo(1f)
            clapFlash.animateTo(0f, tween(220))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        StageHud(
            score = score,
            combo = combo,
            remaining = beatsRemaining,
            total = chart?.hitElapsedMs?.size ?: 0,
        )

        if (useMic && playing) {
            Spacer(Modifier.height(8.dp))
            MicStrip(
                amplitude = micAmplitude,
                lastQuality = lastQuality,
                micDetected = vm.micDetected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val idle = lastQuality == HitQuality.NONE || ringFlash.value <= 0.01f
                drawStage(
                    scenery = scenery,
                    nowMs = clock.displayMs,
                    frameNs = clock.frameNs,
                    chart = chart,
                    notes = visibleNotes.value,
                    combo = combo,
                    ringColor = if (idle) AppColors.textSecondary else qualityColor(lastQuality),
                    ringFlash = ringFlash.value,
                    clapFlash = clapFlash.value,
                    bursts = bursts.toList(),
                )
            }

            if (playing) {
                QualityCallout(
                    lastQuality = lastQuality,
                    // Under the ring: anywhere above it would sit on top of the next note.
                    modifier = Modifier.align(BiasAlignment(0f, 0.86f)),
                )
            } else {
                CountdownOverlay(countDown, modifier = Modifier.align(BiasAlignment(0f, -0.25f)))
            }
        }

        Spacer(Modifier.height(8.dp))
        TimingMeter(
            chart = chart,
            ticks = recentHits,
            clock = clock,
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .height(34.dp),
        )
        Spacer(Modifier.height(10.dp))

        TapKey(enabled = playing, onTapDown = vm::onScreenTapAt, onAccessibleClick = vm::onScreenTap)

        Spacer(Modifier.height(10.dp))
        GhostButton("Stop", { vm.stopGame() }, Modifier.width(132.dp))
        Spacer(Modifier.height(10.dp))
    }
}

// ── HUD ───────────────────────────────────────────────────────────────────────

@Composable
private fun StageHud(score: Int, combo: Int, remaining: Int, total: Int) {
    val shownScore by animateIntAsState(score, tween(260), label = "score")
    val progress by animateFloatAsState(
        targetValue = if (total > 0) 1f - remaining.toFloat() / total else 0f,
        animationSpec = tween(300), label = "progress",
    )
    // One pop per hit: a spring back from slightly large. Not a breathing loop.
    val comboPop = remember { Animatable(1f) }
    LaunchedEffect(combo) {
        if (combo > 0) {
            comboPop.snapTo(1.22f)
            comboPop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            HudStat("SCORE", "$shownScore", AppColors.gold, Alignment.Start, Modifier.weight(1f))
            HudStat("NOTES LEFT", "$remaining", AppColors.textSecondary, Alignment.CenterHorizontally, Modifier.weight(1f))
            HudStat(
                "COMBO", "×$combo", if (combo >= COMBO_MILESTONE) AppColors.gold else AppColors.textAccent,
                Alignment.End,
                Modifier.weight(1f).graphicsLayer {
                    scaleX = comboPop.value; scaleY = comboPop.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.7f)
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(AppColors.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(AppColors.gold),
            )
        }
    }
}

@Composable
private fun HudStat(label: String, value: String, color: Color, align: Alignment.Horizontal, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = align) {
        Text(
            label, color = AppColors.textDim, fontSize = 10.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
        Text(value, color = color, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
    }
}

// ── Countdown ─────────────────────────────────────────────────────────────────

@Composable
private fun CountdownOverlay(countDown: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OverlayEyebrow("GET READY", color = AppColors.textSecondary)
        AnimatedContent(
            targetState = countDown,
            transitionSpec = { scaleIn(initialScale = 1.5f) + fadeIn() togetherWith scaleOut(targetScale = 0.6f) + fadeOut() },
            label = "countdown",
        ) { count ->
            Text(
                count.toString(), fontSize = 120.sp, lineHeight = 128.sp, fontWeight = FontWeight.Black,
                color = AppColors.gold, textAlign = TextAlign.Center,
            )
        }
        Text(
            "Hit each note as it fills the ring",
            color = AppColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp,
        )
    }
}

// ── Judgement callout ─────────────────────────────────────────────────────────

@Composable
private fun QualityCallout(lastQuality: HitQuality, modifier: Modifier = Modifier) {
    fun word(q: HitQuality) = when (q) {
        HitQuality.PERFECT -> "PERFECT"
        HitQuality.GOOD -> "GOOD"
        HitQuality.ALMOST -> "ALMOST"
        HitQuality.MISS -> "MISS"
        HitQuality.NONE -> ""
    }
    // The last real judgement, held while the word fades out (a plain holder, not state).
    val held = remember { arrayOf(HitQuality.PERFECT) }
    if (lastQuality != HitQuality.NONE) held[0] = lastQuality
    Box(modifier = modifier.height(30.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = lastQuality != HitQuality.NONE,
            enter = scaleIn(tween(90), initialScale = 0.7f) + fadeIn(tween(60)),
            exit = fadeOut(tween(260)),
        ) {
            Text(
                word(held[0]), color = qualityColor(held[0]), fontSize = 22.sp, lineHeight = 26.sp,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp,
            )
        }
    }
}

// ── Timing meter ──────────────────────────────────────────────────────────────

internal class MeterTick(val offsetMs: Long, val quality: HitQuality, val bornNs: Long)

private const val METER_TICKS = 8
private const val METER_TICK_LIFE_S = 3.0f

/**
 * Where each recent hit landed against the beat: early to the left, late to the right, on the
 * same scale as the judgement windows (gold = perfect, green = good, blue = the rest of the
 * hit window). The newest mark is brightest; older ones fade, so a player who is steadily
 * early or late sees a cluster sitting to one side.
 */
@Composable
private fun TimingMeter(chart: RhythmChart?, ticks: List<MeterTick>, clock: StageClock, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MeterLabel("EARLY")
            MeterLabel("LATE")
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            val dp = 1.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val half = size.width / 2f
            val trackH = 6f * dp
            val radius = CornerRadius(trackH / 2f)

            fun zone(halfWidth: Float, color: Color) = drawRoundRect(
                color, Offset(cx - halfWidth, cy - trackH / 2f), Size(halfWidth * 2f, trackH), radius,
            )
            zone(half, AppColors.surfaceVariant)
            if (chart != null && chart.missMs > 0) {
                val perMs = half / chart.missMs
                zone(half, GameColors.almost.copy(alpha = 0.22f))
                zone(chart.goodMs * perMs, GameColors.good.copy(alpha = 0.30f))
                zone(chart.perfectMs * perMs, AppColors.goldBorderStrong)

                val now = clock.frameNs   // subscribes the meter to the frame clock, for the fade
                ticks.toList().forEachIndexed { i, tick ->
                    val age = ((now - tick.bornNs) / 1e9f).coerceAtLeast(0f)
                    val newest = i == ticks.lastIndex
                    val alpha = if (newest) 1f else (1f - age / METER_TICK_LIFE_S).coerceIn(0f, 0.55f)
                    if (alpha <= 0f) return@forEachIndexed
                    val x = cx + (tick.offsetMs * perMs).coerceIn(-half, half)
                    val reach = (if (newest) 9f else 6f) * dp
                    drawLine(
                        qualityColor(tick.quality).copy(alpha = alpha),
                        Offset(x, cy - reach), Offset(x, cy + reach),
                        strokeWidth = (if (newest) 3f else 2f) * dp, cap = StrokeCap.Round,
                    )
                }
            }
            // Dead centre: exactly on the beat.
            drawLine(Color.White.copy(alpha = 0.55f), Offset(cx, cy - 7f * dp), Offset(cx, cy + 7f * dp), 1f * dp)
        }
    }
}

@Composable
private fun MeterLabel(text: String) {
    Text(
        text, color = AppColors.textDim, fontSize = 10.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
    )
}

// ── TAP key ───────────────────────────────────────────────────────────────────

/**
 * The TAP key: the play button's raised purple face.
 *
 * It scores on touch-DOWN, with the pointer event's own timestamp, for every finger that lands
 * (two thumbs alternating must both count). A click callback would fire on release, after the
 * finger's rest time, and only for one finger at a time. [onAccessibleClick] covers a click that
 * arrives with no touch behind it (a screen reader, a keyboard).
 */
@Composable
private fun TapKey(enabled: Boolean, onTapDown: (Long) -> Unit, onAccessibleClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(1f) }
    val lastDown = remember { mutableLongStateOf(0L) }

    RaisedControl(
        onClick = { if (SystemClock.elapsedRealtime() - lastDown.longValue > 1_000L) onAccessibleClick() },
        enabled = enabled,
        shape = CircleShape,
        tint = AppColors.primaryPurple,
        modifier = Modifier
            .size(104.dp)
            .graphicsLayer {
                scaleX = press.value; scaleY = press.value
                alpha = if (enabled) 1f else 0.45f
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        // Initial pass, never consumed: the key underneath still gets its ripple.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { change ->
                            if (change.changedToDown()) {
                                // Pointer times are uptimeMillis; the game runs on elapsedRealtime.
                                val toBoot = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()
                                lastDown.longValue = SystemClock.elapsedRealtime()
                                onTapDown(change.uptimeMillis + toBoot)
                                scope.launch {
                                    press.animateTo(0.90f, tween(40))
                                    press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                                }
                            }
                        }
                    }
                }
            },
    ) {
        Text("TAP", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
    }
}

// ── Microphone strip ──────────────────────────────────────────────────────────

private const val MIC_BARS = 30

/**
 * A scrolling level meter of what the microphone hears, shown in mic mode only. The bars flash
 * white when a clap is detected and in the judgement's colour when it scores, so a player can
 * tell "not heard" from "heard, but off the beat".
 */
@Composable
private fun MicStrip(
    amplitude: Float,
    lastQuality: HitQuality,
    micDetected: SharedFlow<Unit>,
    modifier: Modifier = Modifier,
) {
    val history = remember { FloatArray(MIC_BARS) }
    val head = remember { mutableLongStateOf(0L) }
    LaunchedEffect(amplitude) {
        System.arraycopy(history, 1, history, 0, MIC_BARS - 1)
        history[MIC_BARS - 1] = amplitude
        head.longValue++
    }

    val scoredFlash = remember { Animatable(0f) }
    LaunchedEffect(lastQuality) {
        if (lastQuality != HitQuality.NONE) {
            scoredFlash.snapTo(0.95f)
            scoredFlash.animateTo(0f, tween(500))
        }
    }
    val heardFlash = remember { Animatable(0f) }
    LaunchedEffect(micDetected) {
        micDetected.collect {
            heardFlash.snapTo(0.6f)
            heardFlash.animateTo(0f, tween(250))
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.weight(1f).height(28.dp)) {
            head.longValue   // redraw when a sample arrives
            val gap = 2.dp.toPx()
            val barW = (size.width - gap * (MIC_BARS - 1)) / MIC_BARS
            val scored = scoredFlash.value
            val heard = heardFlash.value
            val flashColor = if (scored > 0.01f) qualityColor(lastQuality) else Color.White
            val flash = if (scored > 0.01f) scored else heard
            for (i in 0 until MIC_BARS) {
                val amp = history[i]
                // Amplified so a quiet clap still shows; centred, so it reads as a waveform.
                val barH = (amp * size.height * 8f).coerceIn(2.dp.toPx(), size.height)
                val base = when {
                    amp > 0.20f -> AppColors.gold
                    amp > 0.06f -> AppColors.textAccent
                    else -> GameColors.eqQuiet
                }
                drawRoundRect(
                    color = if (flash > 0.01f) androidx.compose.ui.graphics.lerp(base, flashColor, flash) else base,
                    topLeft = Offset(i * (barW + gap), (size.height - barH) / 2f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "MIC", color = AppColors.textDim, fontSize = 10.sp, lineHeight = 14.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
    }
}
