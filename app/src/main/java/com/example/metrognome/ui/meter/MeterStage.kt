package com.example.metrognome.ui.meter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.haptics.HapticEngine
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.theory.Meter
import com.example.metrognome.theory.MeterTheory
import com.example.metrognome.ui.components.AppCard
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.FadingHorizontalScrollbar
import com.example.metrognome.ui.components.Particle
import com.example.metrognome.ui.components.PinnedLines
import com.example.metrognome.ui.components.PinnedSlot
import com.example.metrognome.audio.metronome.subdivision.Subdivision
import com.example.metrognome.ui.subdivision.SubdivisionRow
import com.example.metrognome.viewmodel.SubdivisionStatus
import com.example.metrognome.ui.components.PlayStopKey
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.components.drawParticles
import com.example.metrognome.ui.dialogs.DialogCloseButton
import com.example.metrognome.ui.overlays.AppWindowDialog
import com.example.metrognome.ui.overlays.OverlayEyebrow
import com.example.metrognome.ui.scene.StagePageDots
import com.example.metrognome.ui.scene.drawNightSky
import com.example.metrognome.ui.scene.drawPineForest
import com.example.metrognome.ui.scene.drawStageDisc
import com.example.metrognome.ui.scene.drawStageSpotlight
import com.example.metrognome.ui.scene.starField
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.BeatEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The meter stage: every everyday time signature on the sound stage's moonlit night, each as a
 * beat wheel (the bar's beats round a disc, grouped by its accents, the fraction at the heart),
 * swiped through one at a time; the last page is the player's own meter, built with the
 * fraction steppers. Choosing one is an event: the wheel lights beat by beat, throws the About
 * card's sparks and gives a tick. While the metronome plays, the wheel in use lights with every
 * beat and a soft hand sweeps the bar, Metro's baton in another form.
 *
 * Stateless (it knows nothing about the metronome; MetronomeMeterStage.kt is the binding), and
 * built from the pieces the other stages use: the night (ui/scene), the spotlight, disc and
 * page dots shared with the sound stage, PlayStopKey, PrimaryButton, AppCard, the pinned-text
 * slots (nothing moves as meters change), the particles and the beat gem shared with Home.
 */

@Immutable
data class MeterStageState(
    val inUse: Meter,
    /** The accents in use, for [inUse]. */
    val accents: Set<Int>,
    val playing: Boolean,
    val bpm: Int,
    /** A Practice or Speed Trainer session is open: the meter cannot change until it ends. */
    val locked: Boolean,
    /** The metronome's subdivision. Global, not the focused meter's: it is never dimmed by paging. */
    val subdivision: SubdivisionStatus,
)

@Composable
fun MeterStageOverlay(
    state: MeterStageState,
    beats: Flow<BeatEvent>?,
    onUse: (Meter) -> Unit,
    onToggleAccent: (Int) -> Unit,
    onResetAccents: () -> Unit,
    onPlayToggle: () -> Unit,
    onClose: () -> Unit,
    onSubdivision: (Subdivision) -> Unit,
) {
    AppWindowDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MeterStage(state, beats, onUse, onToggleAccent, onResetAccents, onPlayToggle, onClose, onSubdivision)
    }
}

private val MEDALLION = 176.dp
// On a short phone with large fonts the card needs the height: the wheel gives way down to this.
private val MEDALLION_MIN = 100.dp
// Below the wheel: the 10 dp gap and the 8 dp page dots.
private val UNDER_CAROUSEL = 18.dp
private const val PRESET_DEFAULT_CUSTOM_TOP = 5
private const val PRESET_DEFAULT_CUSTOM_BOTTOM = 8

@Composable
fun MeterStage(
    state: MeterStageState,
    beats: Flow<BeatEvent>?,
    onUse: (Meter) -> Unit,
    onToggleAccent: (Int) -> Unit,
    onResetAccents: () -> Unit,
    onPlayToggle: () -> Unit,
    onClose: () -> Unit,
    onSubdivision: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = MeterTheory.COMMON_METERS
    val customPage = presets.size
    var custom by remember {
        mutableStateOf(if (state.inUse in presets) Meter(PRESET_DEFAULT_CUSTOM_TOP, PRESET_DEFAULT_CUSTOM_BOTTOM) else state.inUse)
    }
    fun meterAt(page: Int) = if (page < customPage) presets[page] else custom

    val pager = rememberPagerState(initialPage = presets.indexOf(state.inUse).takeIf { it >= 0 } ?: customPage) { presets.size + 1 }
    val focused = meterAt(pager.currentPage.coerceIn(0, customPage))
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = remember { HapticEngine(context) }

    val pulse = rememberBeatPulse(beats, state.playing, state.bpm)
    var burst by remember { mutableStateOf<List<Particle>>(emptyList()) }
    var burstNs by remember { mutableLongStateOf(Long.MIN_VALUE) }

    fun choose(m: Meter) {
        if (m == state.inUse || state.locked) return
        onUse(m)
        pulse.celebrate()
        val r = Random(System.nanoTime())
        burst = List(22) { Particle.random(r, finale = false) }
        burstNs = pulse.celebrateNs // the pulse's clock (on-glass time), so sparks and shockwaves start together
        haptics.fire(HapticPattern.TICK)
    }

    val ambience = rememberInfiniteTransition(label = "meterAmbience")
    val t by ambience.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "meterTime",
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = LinearOutSlowInEasing)) }
    val stars = remember { starField(seed = 23) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value
                val s = 0.97f + 0.03f * appear.value
                scaleX = s; scaleY = s
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            pulse.frameNs
            val glow = if (state.playing) pulse.lastBeat.let { if (it >= 0) pulse.lit(it) else 0f } else 0f
            drawNightSky(stars, size.width, size.height, 1.dp.toPx(), t, glow, moonY = 0.165f)
            drawPineForest(size.width, size.height)
        }

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    OverlayEyebrow("TIME SIGNATURE", color = AppColors.goldSoft)
                    Text(
                        "Swipe to browse, tap to choose.",
                        color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                }
                DialogCloseButton(onClick = onClose)
            }

            // The wheel and its dots, centred in whatever height the card leaves: full size
            // on most phones, smaller on a short one with large fonts, so the card never runs
            // out of room (it had none to spare at 360 x 640 dp and 1.3x fonts).
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val medallion = (maxHeight - UNDER_CAROUSEL - 24.dp).coerceIn(MEDALLION_MIN, MEDALLION)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        MeterCarousel(
                            pager = pager,
                            meterAt = ::meterAt,
                            customPage = customPage,
                            state = state,
                            pulse = pulse,
                            medallion = medallion,
                            onTapFocused = { m ->
                                if (m == state.inUse) onPlayToggle() else choose(m)
                            },
                        )
                        // The chosen moment's sparks, over the carousel.
                        Canvas(Modifier.matchParentSize()) {
                            val now = pulse.frameNs
                            if (burst.isEmpty() || burstNs == Long.MIN_VALUE) return@Canvas
                            val s = (now - burstNs) / 1e9f
                            if (s in 0f..1.6f) drawParticles(burst, Offset(size.width / 2f, size.height / 2f), s)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    StagePageDots(presets.size + 1, pager.currentPage) { meterAt(it) == state.inUse }
                }
            }

            MeterDetails(
                meter = focused,
                isCustomPage = pager.currentPage == customPage,
                state = state,
                pulse = pulse,
                onStep = { next ->
                    val idx = presets.indexOf(next)
                    scope.launch {
                        if (idx >= 0) pager.animateScrollToPage(idx)
                        else {
                            custom = next
                            pager.animateScrollToPage(customPage)
                        }
                    }
                },
                onUse = { choose(focused) },
                onPlay = {
                    if (!state.playing && focused != state.inUse) choose(focused)
                    onPlayToggle()
                },
                onToggleAccent = onToggleAccent,
                onResetAccents = onResetAccents,
                onSubdivision = onSubdivision,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── The carousel of beat wheels ───────────────────────────────────────────────

@Composable
private fun MeterCarousel(
    pager: PagerState,
    meterAt: (Int) -> Meter,
    customPage: Int,
    state: MeterStageState,
    pulse: BeatPulse,
    medallion: Dp,
    onTapFocused: (Meter) -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = ((maxWidth - medallion) / 2).coerceAtLeast(0.dp)
        Canvas(Modifier.matchParentSize()) {
            pulse.frameNs
            val glow = if (state.playing && pulse.lastBeat >= 0) pulse.lit(pulse.lastBeat) else 0f
            drawStageSpotlight(
                c = Offset(size.width / 2f, size.height / 2f),
                r = medallion.toPx() / 2f * 0.80f,
                top = -size.height * 1.1f,
                glow = glow,
            )
        }
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fixed(medallion),
            contentPadding = PaddingValues(horizontal = side),
            pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth().height(medallion + 24.dp),
        ) { page ->
            val meter = meterAt(page)
            val inUse = meter == state.inUse
            val accents = if (inUse) state.accents else MeterTheory.defaultAccents(meter)
            val away = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.30f * away
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.55f * away
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (page == pager.currentPage) onTapFocused(meter)
                        else scope.launch { pager.animateScrollToPage(page) }
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = buildString {
                            append("${meter.display}, ${MeterTheory.label(meter)}")
                            if (page == customPage) append(", your own")
                            if (inUse) append(", in use")
                            if (page == pager.currentPage) append(if (inUse) ", tap to play or stop" else ", tap to choose it")
                        }
                    },
            ) {
                MeterMedallion(
                    meter = meter,
                    accents = accents,
                    inUse = inUse,
                    custom = page == customPage,
                    pulse = pulse,
                    live = inUse && state.playing,
                    size = medallion,
                )
            }
        }
    }
}

/**
 * One meter as a beat wheel: the stage's disc, the bar's beats as gems round its rim (starting
 * at twelve o'clock, grouped by [accents]), and the fraction at its heart. While [live], the
 * gems light with the beat and a soft hand sweeps the bar; [BeatPulse.celebrate] lights it all
 * in a cascade. The player's own meter carries a dashed outer ring: a meter made, not picked.
 */
@Composable
fun MeterMedallion(
    meter: Meter,
    accents: Set<Int>,
    inUse: Boolean,
    custom: Boolean,
    pulse: BeatPulse,
    live: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val numeral = remember(size, density) {
        TextStyle(fontSize = with(density) { (size * 0.40f * 0.43f).toSp() }, fontWeight = FontWeight.Black)
    }
    val topText = remember(meter.top, numeral) { measurer.measure("${meter.top}", numeral) }
    val bottomText = remember(meter.bottom, numeral) { measurer.measure("${meter.bottom}", numeral) }
    val positions = remember(meter.top, accents) { MeterLayout.ring(meter.top, accents) }
    val pitchTurns = remember(meter.top, accents) { MeterLayout.ringPitch(meter.top, accents) }

    Canvas(modifier.size(size)) {
        pulse.frameNs
        val dp = 1.dp.toPx()
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f * 0.80f
        val beatGlow = if (live && pulse.lastBeat >= 0) pulse.lit(pulse.lastBeat) else 0f
        val since = if (inUse) pulse.sinceCelebrate() else null

        if (inUse) {
            drawCircle(
                Brush.radialGradient(listOf(AppColors.gold.copy(alpha = 0.16f + 0.14f * beatGlow), Color.Transparent), c, r * 1.5f),
                r * 1.5f, c,
            )
        }
        // The chosen moment, part one: a glow behind the disc and a gold shockwave off its rim.
        if (since != null && since < 1.1f) {
            val p = since / 1.1f
            val fade = (1f - p) * (1f - p)
            drawCircle(
                Brush.radialGradient(listOf(AppColors.gold.copy(alpha = 0.45f * fade), Color.Transparent), c, r * 1.8f),
                r * 1.8f, c,
            )
            for (k in 0..1) {
                val q = ((since - k * 0.14f) / 0.9f).coerceIn(0f, 1f)
                if (q <= 0f || q >= 1f) continue
                val e = 1f - (1f - q) * (1f - q) * (1f - q)
                drawCircle(
                    AppColors.gold.copy(alpha = 0.6f * (1f - q) * (1f - q)),
                    r * (1.02f + 0.5f * e), c,
                    style = Stroke(width = (3f * (1f - q) + 0.6f) * dp),
                )
            }
        }
        drawStageDisc(c, r)
        val (rim, rimWidth) = if (inUse) AppColors.gold to 2f else AppColors.goldBorderStrong to 1.5f
        drawCircle(rim, r, c, style = Stroke(width = rimWidth * dp))
        if (custom) {
            drawCircle(
                AppColors.goldBorder, r * 1.09f, c,
                style = Stroke(width = 1f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * dp, 4f * dp))),
            )
        }

        val ringR = r * 0.76f
        val n = positions.size
        fun turnAt(i: Int) = positions[i]

        // The hand: from the bar's start to where it has got to, a faint gold arc.
        if (live) {
            pulse.barPosition()?.let { pos ->
                val i = floor(pos).toInt().coerceIn(0, n - 1)
                val f = pos - i
                val from = turnAt(i)
                val to = if (i + 1 < n) turnAt(i + 1) else 1f
                val turn = from + (to - from) * f
                drawArc(
                    AppColors.gold.copy(alpha = 0.22f),
                    startAngle = -90f, sweepAngle = 360f * turn, useCenter = false,
                    topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2),
                    style = Stroke(width = 3f * dp, cap = StrokeCap.Round),
                )
            }
        }

        val gemR = min(r * 0.105f, (2f * PI.toFloat() * ringR * pitchTurns) * 0.30f)
        for (i in 0 until n) {
            val a = (-PI / 2 + 2 * PI * turnAt(i)).toFloat()
            val gc = Offset(c.x + cos(a) * ringR, c.y + sin(a) * ringR)
            val accent = i in accents
            var lit = if (live) pulse.lit(i) else 0f
            var ring = if (live) pulse.ring(i) else -1f
            var glint = if (live) pulse.glint(i) else -1f
            var pop = if (live) pulse.pop(i) else 0f
            // Part two: the bar lights round the wheel, beat by beat, from the downbeat.
            if (since != null) {
                val ai = since - 0.12f - i * 0.085f
                if (ai >= 0f) {
                    lit = max(lit, exp(-ai / 0.7f))
                    if (ai < 0.45f) ring = ai / 0.45f
                    if (ai < 0.38f) glint = ai / 0.38f
                    if (ai < 0.4f) pop = max(pop, if (ai < 0.07f) ai / 0.07f else exp(-(ai - 0.07f) / 0.16f))
                }
            }
            val gr = gemR * (if (accent) 1.14f else 1f) * (1f + 0.12f * lit + 0.22f * pop)
            drawBeatGem(gc, gr, accent, lit, ring, glint)
        }

        // The fraction at the heart.
        val barW = r * 0.40f
        drawText(topText, color = AppColors.textPrimary, topLeft = Offset(c.x - topText.size.width / 2f, c.y - 2f * dp - topText.size.height))
        drawLine(
            AppColors.gold.copy(alpha = 0.75f + 0.25f * beatGlow),
            Offset(c.x - barW / 2f, c.y), Offset(c.x + barW / 2f, c.y),
            strokeWidth = 2.5f * dp, cap = StrokeCap.Round,
        )
        drawText(bottomText, color = AppColors.textPrimary, topLeft = Offset(c.x - bottomText.size.width / 2f, c.y + 2f * dp))
    }
}

// ── The focused meter's card ──────────────────────────────────────────────────

@Composable
private fun MeterDetails(
    meter: Meter,
    isCustomPage: Boolean,
    state: MeterStageState,
    pulse: BeatPulse,
    onStep: (Meter) -> Unit,
    onUse: () -> Unit,
    onPlay: () -> Unit,
    onToggleAccent: (Int) -> Unit,
    onResetAccents: () -> Unit,
    onSubdivision: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inUse = meter == state.inUse
    val accents = if (inUse) state.accents else MeterTheory.defaultAccents(meter)
    val customAccents = inUse && state.accents != MeterTheory.defaultAccents(meter)

    AppCard(modifier = modifier.fillMaxWidth()) {
        PinnedSlot(lineHeight = 30.sp, alignment = Alignment.Center) {
            Text(
                MeterTheory.label(meter), color = AppColors.textPrimary, fontSize = 22.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = PinnedLines,
            )
        }
        PinnedSlot(lineHeight = 16.sp, alignment = Alignment.Center) {
            val (status, color) = when {
                inUse && isCustomPage -> "IN USE · YOUR OWN" to AppColors.gold
                inUse -> "IN USE · ${meter.display}" to AppColors.gold
                isCustomPage -> "YOUR OWN · ${meter.display}" to AppColors.goldSoft
                else -> meter.display to AppColors.textDim
            }
            Text(
                status, color = color, fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, style = PinnedLines,
            )
        }
        Spacer(Modifier.height(4.dp))
        PinnedSlot(lineHeight = 18.sp, alignment = Alignment.Center) {
            Text(
                MeterTheory.description(meter), color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = PinnedLines,
            )
        }
        Spacer(Modifier.height(10.dp))

        AccentEditor(
            beats = meter.top,
            accents = accents,
            editable = inUse,
            pulse = pulse,
            live = inUse && state.playing,
            onToggle = onToggleAccent,
        )

        Spacer(Modifier.height(10.dp))
        FractionSteppers(meter = meter, onStep = onStep)
        Spacer(Modifier.height(8.dp))
        SubdivisionRow(status = state.subdivision, onChoose = onSubdivision)
        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayStopKey(
                playing = state.playing,
                enabled = state.playing || inUse || !state.locked,
                onClick = onPlay,
                playDescription = "Play ${meter.display}",
                stopDescription = "Stop the metronome",
            )
            Spacer(Modifier.width(12.dp))
            val (label, enabled) = when {
                inUse -> "In use" to false
                state.locked -> "After your session" to false
                else -> "Use ${meter.display}" to true
            }
            PrimaryButton(
                label = label,
                onClick = onUse,
                enabled = enabled,
                icon = if (inUse) AppIcons.Check else null,
                modifier = Modifier.weight(1f),
            )
        }

        PinnedSlot(lineHeight = 30.sp, alignment = Alignment.Center) {
            val style = Modifier
            when {
                !inUse && state.locked -> FootLine("Change the meter after your session")
                !inUse -> FootLine("Use it, then tap a beat to accent it")
                customAccents -> Row(verticalAlignment = Alignment.CenterVertically) {
                    FootLine("Your own accents · ")
                    Text(
                        "Reset", color = AppColors.goldSoft, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold, style = PinnedLines,
                        modifier = style
                            .clickable(role = Role.Button, onClick = onResetAccents)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .semantics { contentDescription = "Reset accents to ${meter.display}'s natural grouping" },
                    )
                }
                else -> FootLine("Tap a beat to accent it")
            }
        }
    }
}

@Composable
private fun FootLine(text: String) = Text(
    text, color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, style = PinnedLines,
)

private val CELL_MIN = 30.dp
private val CELL_MAX = 36.dp
private val EDITOR_HEIGHT = 48.dp

/**
 * The bar as a row of beat gems, one checkbox each, grouped like the wheel. Tapping a gem
 * accents it or takes the accent away. A long bar that cannot keep a 30 dp target per beat
 * scrolls instead of shrinking, so every beat stays tappable. Dimmed and inert for a meter
 * that is not in use (it shows that meter's natural accents).
 */
@Composable
private fun AccentEditor(
    beats: Int,
    accents: Set<Int>,
    editable: Boolean,
    pulse: BeatPulse,
    live: Boolean,
    onToggle: (Int) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(EDITOR_HEIGHT), contentAlignment = Alignment.Center) {
        val fit = with(density) { MeterLayout.line(beats, accents, maxWidth.toPx(), CELL_MAX.toPx()) }
        val line = if (with(density) { fit.pitch.toDp() } >= CELL_MIN) fit
            else with(density) { MeterLayout.line(beats, accents, Float.MAX_VALUE, CELL_MIN.toPx()) }
        val pitch = with(density) { line.pitch.toDp() }
        val width = with(density) { line.width.toDp() }
        val scroll = rememberScrollState()
        Box(
            Modifier
                .then(if (width > maxWidth) Modifier.fillMaxWidth().horizontalScroll(scroll) else Modifier)
                .graphicsLayer { alpha = if (editable) 1f else 0.45f },
        ) {
            Box(Modifier.width(width).height(EDITOR_HEIGHT)) {
                for (i in 0 until line.centres.size) {
                    val accent = i in accents
                    val swell by animateFloatAsState(if (accent) 1f else 0f, tween(220), label = "accent$i")
                    Box(
                        modifier = Modifier
                            .offset(x = with(density) { line.centres[i].toDp() } - pitch / 2)
                            .width(pitch)
                            .height(EDITOR_HEIGHT)
                            .toggleable(
                                value = accent,
                                enabled = editable,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(i) },
                            )
                            .semantics { contentDescription = "Accent beat ${i + 1}" },
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(28.dp).align(Alignment.TopCenter)) {
                            if (live) pulse.frameNs
                            val r = 7.5f.dp.toPx() * (1f + 0.14f * swell) * (1f + 0.18f * (if (live) pulse.pop(i) else 0f))
                            drawBeatGem(
                                Offset(size.width / 2f, size.height / 2f + 2.dp.toPx()), r, accent,
                                lit = if (live) pulse.lit(i) else 0f,
                                ring = if (live) pulse.ring(i) else -1f,
                                glint = if (live) pulse.glint(i) else -1f,
                            )
                        }
                        Text(
                            "${i + 1}",
                            color = if (accent) AppColors.goldSoft else AppColors.textDim,
                            fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center, style = PinnedLines,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).clearAndSetSemantics {},
                        )
                    }
                }
            }
        }
        // A long bar scrolls; the app's fading bar says so (and hides itself when it fits).
        if (width > maxWidth) {
            FadingHorizontalScrollbar(
                scrollState = scroll,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The fraction as two steppers, top over bottom read left to right ("− 7 +  /  − 8 +"). A
 * step that lands on an everyday meter goes to its page; anything else becomes the player's
 * own meter on the last page.
 */
@Composable
private fun FractionSteppers(meter: Meter, onStep: (Meter) -> Unit) {
    val dens = MeterTheory.SENSIBLE_DENOMINATORS
    val di = dens.indexOf(meter.bottom).let { if (it < 0) dens.indexOf(4) else it }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepKey("−", "Fewer beats per bar", meter.top > MeterTheory.TOP_RANGE.first) {
            onStep(meter.copy(top = meter.top - 1))
        }
        StepValue(meter.top)
        StepKey("+", "More beats per bar", meter.top < MeterTheory.TOP_RANGE.last) {
            onStep(meter.copy(top = meter.top + 1))
        }
        Text(
            "/", color = AppColors.textDim, fontSize = 22.sp, lineHeight = 26.sp,
            modifier = Modifier.padding(horizontal = 12.dp).clearAndSetSemantics {},
        )
        StepKey("−", "Smaller beat value number", di > 0) { onStep(meter.copy(bottom = dens[di - 1])) }
        StepValue(meter.bottom)
        StepKey("+", "Larger beat value number", di < dens.lastIndex) { onStep(meter.copy(bottom = dens[di + 1])) }
    }
}

@Composable
private fun StepValue(value: Int) = Text(
    "$value", color = AppColors.gold, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold,
    // Wide enough for "16" at 1.3x fonts: 34 dp let the + key cover the second digit.
    textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
    modifier = Modifier.width(46.dp).clearAndSetSemantics {},
)

@Composable
private fun StepKey(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    RaisedControl(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        tint = AppColors.surfaceVariant,
        modifier = Modifier
            .size(34.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .semantics { contentDescription = description },
    ) {
        Text(
            symbol, color = AppColors.textPrimary, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center).clearAndSetSemantics {},
        )
    }
}
