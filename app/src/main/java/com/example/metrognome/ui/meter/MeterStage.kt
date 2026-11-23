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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
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
import com.example.metrognome.viewmodel.SubdivisionStatus
import com.example.metrognome.ui.components.PlayStopKey
import com.example.metrognome.ui.components.PrimaryButton
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
import com.example.metrognome.ui.subdivision.caption
import androidx.compose.ui.draw.clip
import com.example.metrognome.ui.theme.AppRadius
import com.example.metrognome.ui.components.raisedFace
import com.example.metrognome.ui.components.CHIP_HEIGHT
import com.example.metrognome.ui.components.SectionLabel
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

            // The wheel and its dots, centred in whatever height is left: full size on most
            // phones, smaller on a short one with large fonts, so the card never runs out of
            // room (it had none to spare at 360 x 640 dp and 1.3x fonts).
            val carousel: @Composable (Dp) -> Unit = { room ->
                val medallion = (room - UNDER_CAROUSEL - 24.dp).coerceIn(MEDALLION_MIN, MEDALLION)
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
            val details: @Composable (Modifier) -> Unit = { m ->
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
                    modifier = m,
                )
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth > maxHeight * 1.3f) {
                    // A phone on its side: stacked, the card took the whole height and the
                    // wheels vanished. Side by side, the wheels keep the left and the card
                    // scrolls on the right if even the full height is short.
                    val tall = maxHeight
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { carousel(tall) }
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = tall).padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { details(Modifier) }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            carousel(maxHeight)
                        }
                        details(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
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
        com.example.metrognome.ui.scene.StageDetents(pager, quiet = state.locked)
        HorizontalPager(
            state = pager,
            flingBehavior = com.example.metrognome.ui.scene.stageFlingBehavior(pager),
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

/*
 * The card under the wheels (redesigned 2026-11-23 to one grammar, after the dev found it
 * crowded): a title line (the meter's name and feel, its status at the right), then sections
 * that all read the same way, a caps label at the left and its control at the right on one
 * chip-high line, then the play and use keys and one hint line. Three text sizes: title, body
 * and the caps labels. The fraction is not repeated in words (the wheel and the BAR steppers
 * show it), and the subdivision says nothing while it simply sounds; the hint line speaks only
 * about what matters now (a pause, a held meter, how to accent).
 */

private val ROW = CHIP_HEIGHT
private val SECTION_GAP = 12.dp

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
        // Title: name and feel at the left, status at the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                PinnedSlot(lineHeight = 26.sp) {
                    Text(
                        MeterTheory.label(meter), color = AppColors.textPrimary, fontSize = 20.sp, lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = PinnedLines,
                    )
                }
                PinnedSlot(lineHeight = 18.sp) {
                    Text(
                        MeterTheory.description(meter), color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = PinnedLines,
                    )
                }
            }
            val (status, color) = when {
                inUse -> "IN USE" to AppColors.gold
                isCustomPage -> "YOUR OWN" to AppColors.goldSoft
                else -> "" to AppColors.textDim
            }
            // On the title's line: the label's 14 sp line centred in the title's 26 sp one.
            Box(Modifier.height(with(LocalDensity.current) { 26.sp.toDp() }), contentAlignment = Alignment.CenterEnd) {
                SectionLabel(status, color = color, maxLines = 1)
            }
        }

        Spacer(Modifier.height(SECTION_GAP))
        SectionHeader("ACCENTS") {
            if (customAccents) {
                Text(
                    "Reset", color = AppColors.goldSoft, fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onResetAccents)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Reset accents to the natural grouping" },
                )
            }
        }
        AccentEditor(
            beats = meter.top,
            accents = accents,
            editable = inUse,
            pulse = pulse,
            live = inUse && state.playing,
            onToggle = onToggleAccent,
        )

        Spacer(Modifier.height(SECTION_GAP))
        SectionHeader("BAR") { FractionSteppers(meter = meter, onStep = onStep) }
        Spacer(Modifier.height(8.dp))
        SectionHeader("SUBDIVISION") {
            com.example.metrognome.ui.subdivision.SubdivisionChips(status = state.subdivision, onChoose = onSubdivision)
        }

        Spacer(Modifier.height(SECTION_GAP + 4.dp))
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

        // One hint, the one that matters now.
        Spacer(Modifier.height(8.dp))
        PinnedSlot(lineHeight = 16.sp, alignment = Alignment.Center) {
            val sub = state.subdivision
            val hint = when {
                !inUse && state.locked -> "Change the meter after your session"
                sub.chosen != Subdivision.OFF && !sub.sounding ->
                    "Subdivision " + sub.caption().replaceFirstChar { it.lowercase() }
                !inUse -> "Use it, then tap a beat to accent it"
                else -> "Tap a beat to accent it"
            }
            Text(
                hint, color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = PinnedLines,
            )
        }
    }
}

/** A caps label at the left and its control (or value) at the right, on one [ROW]-high line. */
@Composable
private fun SectionHeader(label: String, end: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label, maxLines = 1, modifier = Modifier.weight(1f).padding(end = 8.dp))
        end()
    }
}

private val CELL_MIN = 30.dp
private val CELL_MAX = 36.dp
private val EDITOR_HEIGHT = 36.dp

/**
 * The bar as a row of beat gems, one checkbox each, grouped like the wheel (the gaps say
 * where the groups fall, so the beats carry no numbers). Tapping a gem accents it or takes
 * the accent away. A long bar that cannot keep a 30 dp target per beat scrolls instead of
 * shrinking, so every beat stays tappable. Dimmed and inert for a meter that is not in use
 * (it shows that meter's natural accents).
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
                        Canvas(Modifier.fillMaxSize()) {
                            if (live) pulse.frameNs
                            val r = 7.5f.dp.toPx() * (1f + 0.14f * swell) * (1f + 0.18f * (if (live) pulse.pop(i) else 0f))
                            drawBeatGem(
                                Offset(size.width / 2f, size.height / 2f), r, accent,
                                lit = if (live) pulse.lit(i) else 0f,
                                ring = if (live) pulse.ring(i) else -1f,
                                glint = if (live) pulse.glint(i) else -1f,
                            )
                        }
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
 * The fraction as two compact steppers, top over bottom read left to right ("- 7 + / - 8 +"),
 * each one pill at chip height. A step that lands on an everyday meter goes to its page;
 * anything else becomes the player's own meter on the last page.
 */
@Composable
private fun FractionSteppers(meter: Meter, onStep: (Meter) -> Unit) {
    val dens = MeterTheory.SENSIBLE_DENOMINATORS
    val di = dens.indexOf(meter.bottom).let { if (it < 0) dens.indexOf(4) else it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperPill(
            value = meter.top,
            canDown = meter.top > MeterTheory.TOP_RANGE.first,
            canUp = meter.top < MeterTheory.TOP_RANGE.last,
            downDescription = "Fewer beats per bar",
            upDescription = "More beats per bar",
            onDown = { onStep(meter.copy(top = meter.top - 1)) },
            onUp = { onStep(meter.copy(top = meter.top + 1)) },
        )
        Text(
            "/", color = AppColors.textDim, fontSize = 16.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 6.dp).clearAndSetSemantics {},
        )
        StepperPill(
            value = meter.bottom,
            canDown = di > 0,
            canUp = di < dens.lastIndex,
            downDescription = "Smaller beat value number",
            upDescription = "Larger beat value number",
            onDown = { onStep(meter.copy(bottom = dens[di - 1])) },
            onUp = { onStep(meter.copy(bottom = dens[di + 1])) },
        )
    }
}

private val STEP_ZONE = 32.dp
// Wide enough for "16" at 1.3x fonts.
private val STEP_VALUE = 30.dp

/**
 * One number with its minus and plus on a single raised pill at chip height: the keys are
 * zones of the pill, not separate buttons (as buttons they took the platform's 48 dp minimum
 * and dwarfed the beat gems).
 */
@Composable
private fun StepperPill(
    value: Int,
    canDown: Boolean,
    canUp: Boolean,
    downDescription: String,
    upDescription: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        Modifier
            .height(ROW)
            .clip(AppRadius.Pill)
            .raisedFace(AppRadius.Pill, AppColors.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepZone("−", downDescription, canDown, onDown)
        Text(
            "$value", color = AppColors.textPrimary, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            modifier = Modifier.width(STEP_VALUE).clearAndSetSemantics {},
        )
        StepZone("+", upDescription, canUp, onUp)
    }
}

@Composable
private fun StepZone(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(STEP_ZONE)
            .fillMaxHeight()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol, color = if (enabled) AppColors.textPrimary else AppColors.textDim,
            fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
