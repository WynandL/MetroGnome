package com.example.metrognome.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.components.Particle
import com.example.metrognome.ui.components.drawNote
import com.example.metrognome.ui.components.drawParticles
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.ui.theme.ItemPalette
import com.example.metrognome.viewmodel.HitEvent
import com.example.metrognome.viewmodel.HitQuality
import com.example.metrognome.viewmodel.NoteState
import com.example.metrognome.viewmodel.RenderNote
import com.example.metrognome.viewmodel.RhythmChart
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * The rhythm game's stage: a lane of light falling through Metro's night forest.
 *
 * What is decoration and what is information is kept strictly apart:
 *
 *  - INFORMATION is exact. A note's height is linear in time (constant speed, so the eye can
 *    extrapolate it), computed per display frame from the note's own hit time. Its centre is on
 *    the ring's centre at the instant its click leaves the speaker. The gold band behind the
 *    ring is this session's PERFECT window drawn to scale, the green band the GOOD window. The
 *    ring's pulse and the moon's glow are the beat grid itself, not an animation beside it.
 *  - DECORATION never moves on its own clock in a way that could be read as a cue: stars
 *    twinkle, fireflies drift, pines stand still. The fireflies come out as the combo grows,
 *    so the forest waking up is the player's own streak.
 *
 * Depth is suggested by the lane narrowing and the notes growing as they fall. Only size
 * changes with depth, never speed.
 */

// ── Geometry ──────────────────────────────────────────────────────────────────

/** Where the ring sits, as a fraction of the stage's height. */
internal const val HIT_LINE_FRACTION = 0.80f
private const val TOP_SCALE = 0.46f          // lane width and note size at the top, relative to the line

internal class LaneGeometry(size: Size, dp: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val hitY = h * HIT_LINE_FRACTION
    /** A note's radius at the line. Capped so a narrow phone still shows forest either side. */
    val noteR = minOf(22f * dp, w * 0.068f)
    private val halfAtLine = noteR * 2.5f

    fun scaleAt(y: Float): Float = TOP_SCALE + (1f - TOP_SCALE) * (y / hitY).coerceIn(0f, 1.3f)
    fun halfWidthAt(y: Float): Float = halfAtLine * scaleAt(y)
}

// ── Scenery (fixed for the life of the stage) ────────────────────────────────

internal class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

internal class Firefly(
    val x: Float, val y: Float, val ax: Float, val ay: Float,
    val fx: Float, val fy: Float, val phase: Float,
)

internal class LaneScenery(seed: Int = 7) {
    val stars: List<Star>
    val fireflies: List<Firefly>

    init {
        val r = Random(seed)
        stars = List(46) {
            Star(r.nextFloat(), r.nextFloat() * 0.58f, 0.5f + r.nextFloat() * 1.1f, 0.5f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f)
        }
        // Either side of the lane, never across it.
        fireflies = List(16) { i ->
            val left = i % 2 == 0
            Firefly(
                x = if (left) 0.05f + r.nextFloat() * 0.24f else 0.71f + r.nextFloat() * 0.24f,
                y = 0.34f + r.nextFloat() * 0.52f,
                ax = 0.02f + r.nextFloat() * 0.03f, ay = 0.015f + r.nextFloat() * 0.03f,
                fx = 0.25f + r.nextFloat() * 0.4f, fy = 0.2f + r.nextFloat() * 0.45f,
                phase = r.nextFloat() * 6.28f,
            )
        }
    }
}

// ── Bursts ────────────────────────────────────────────────────────────────────

internal class LaneBurst(
    val startNs: Long,
    val color: Color,
    val milestone: Boolean,
    val particles: List<Particle>,
) {
    val durationS: Float get() = if (milestone) 1.3f else 0.9f
}

internal fun qualityColor(q: HitQuality): Color = when (q) {
    HitQuality.PERFECT -> AppColors.gold
    HitQuality.GOOD -> GameColors.good
    HitQuality.ALMOST -> GameColors.almost
    HitQuality.MISS -> GameColors.miss
    HitQuality.NONE -> AppColors.textSecondary
}

/** Every tenth hit in a row is a finale, as every seventh tap is on the About card. */
internal const val COMBO_MILESTONE = 10

/**
 * The burst for one hit. Made of the About card's own particles, thrown as two fans out to the
 * sides rather than a fountain, so nothing flies back up the lane over the next note.
 */
internal fun laneBurstFor(e: HitEvent, frameNs: Long): LaneBurst {
    val r = Random(e.noteId * 7919 + e.combo)
    val milestone = e.combo > 0 && e.combo % COMBO_MILESTONE == 0
    val count = when {
        milestone -> 32
        e.quality == HitQuality.PERFECT -> 10
        e.quality == HitQuality.GOOD -> 6
        else -> 3
    }
    val palette = when {
        milestone -> AppColors.confetti
        e.quality == HitQuality.PERFECT -> listOf(AppColors.gold, ItemPalette.goldLight, Color.White, AppColors.gold)
        e.quality == HitQuality.GOOD -> listOf(GameColors.good, Color.White)
        else -> listOf(GameColors.almost)
    }
    val particles = List(count) { i ->
        val kind = when {
            milestone -> r.nextInt(3)
            e.quality == HitQuality.PERFECT -> when (r.nextInt(10)) { in 0..5 -> 0; in 6..7 -> 1; else -> 2 }
            e.quality == HitQuality.GOOD -> if (r.nextInt(3) == 0) 2 else 0
            else -> 2
        }
        val side = if (i % 2 == 0) -1f else 1f
        // 34 to 83 degrees off vertical: outward and a little up.
        val angle = if (milestone) r.nextFloat() * 2f * PI.toFloat()
                    else (-PI / 2).toFloat() + side * (0.6f + r.nextFloat() * 0.85f)
        Particle(
            angle = angle,
            speed = if (milestone) 150f + r.nextFloat() * 240f else 110f + r.nextFloat() * 190f,
            size = when (kind) { 1 -> 6f + r.nextFloat() * 3f; 0 -> 3.5f + r.nextFloat() * 3.5f; else -> 2f + r.nextFloat() * 2f },
            spin = (r.nextFloat() - 0.5f) * 520f,
            spin0 = r.nextFloat() * 360f,
            life = if (milestone) 0.7f + r.nextFloat() * 0.5f else 0.42f + r.nextFloat() * 0.36f,
            delay = r.nextFloat() * (if (milestone) 0.12f else 0.04f),
            kind = kind,
            color = if (kind == 1 && !milestone) AppColors.textAccent else palette[r.nextInt(palette.size)],
        )
    }
    return LaneBurst(frameNs, qualityColor(e.quality), milestone, particles)
}

// ── The frame ─────────────────────────────────────────────────────────────────

/**
 * Draws one frame of the stage for display time [nowMs] (see [StageClock]).
 *
 * @param chart null during the countdown: the lane and ring are drawn, nothing moves on it.
 * @param ringColor the ring's colour: idle, or the last judgement's while [ringFlash] decays.
 * @param ringFlash 1 at the instant of a judgement, decaying to 0.
 * @param clapFlash 1 when the microphone hears a clap (scored or not), decaying to 0.
 */
internal fun DrawScope.drawStage(
    scenery: LaneScenery,
    nowMs: Double,
    frameNs: Long,
    chart: RhythmChart?,
    notes: List<RenderNote>,
    combo: Int,
    ringColor: Color,
    ringFlash: Float,
    clapFlash: Float,
    bursts: List<LaneBurst>,
) {
    val dp = 1.dp.toPx()
    val g = LaneGeometry(size, dp)
    val t = ((frameNs % 3_600_000_000_000L) / 1e9).toFloat()   // seconds, for ambience only
    val heat = (combo / 16f).coerceIn(0f, 1f)

    // Where we are on the beat grid: which beat, and how far through it (0 at the click).
    var beatIndex = 0
    var beatPhase = 1f
    var onGrid = false
    if (chart != null && chart.hitElapsedMs.isNotEmpty()) {
        val beats = (nowMs - chart.hitElapsedMs[0]) / chart.beatMs
        val whole = floor(beats)
        beatIndex = whole.toInt()
        beatPhase = (beats - whole).toFloat()
        onGrid = beatIndex >= 0 && beatIndex <= chart.hitElapsedMs.size
    }
    val beatGlow = if (onGrid) (1f - beatPhase).pow(2) else 0f
    val downbeat = onGrid && chart != null && beatIndex % chart.beatsPerBar == 0

    drawSky(scenery, g, dp, t, beatGlow)
    drawForest(g)
    drawLane(g, dp, heat)
    if (chart != null) {
        drawWindows(g, chart)
        drawGrid(g, dp, chart, nowMs)
    }
    drawRing(g, dp, ringColor, ringFlash, clapFlash, beatPhase, beatGlow, downbeat)
    drawNotes(g, chart, notes, nowMs)
    drawFireflies(scenery, g, dp, t, combo)

    val origin = Offset(g.cx, g.hitY)
    bursts.forEach { b ->
        val age = ((frameNs - b.startNs) / 1e9f).coerceAtLeast(0f)
        if (age > b.durationS) return@forEach
        val p = (age / (if (b.milestone) 0.7f else 0.45f)).coerceIn(0f, 1f)
        if (p < 1f) {
            val eased = 1f - (1f - p).pow(3)
            drawCircle(
                color = b.color.copy(alpha = (1f - p).pow(1.6f) * 0.8f),
                radius = g.noteR * (1.1f + (if (b.milestone) 3.6f else 2.1f) * eased),
                center = origin,
                style = Stroke(width = (3f * (1f - p) + 0.7f) * dp),
            )
        }
        drawParticles(b.particles, origin, age)
    }
}

private fun DrawScope.drawSky(scenery: LaneScenery, g: LaneGeometry, dp: Float, t: Float, beatGlow: Float) {
    drawRect(Brush.verticalGradient(listOf(GameColors.stageSkyTop, GameColors.stageSkyBottom)))

    scenery.stars.forEach { s ->
        val tw = sin(t * s.speed + s.phase)
        drawCircle(Color.White.copy(alpha = 0.18f + 0.62f * tw * tw), s.size * dp, Offset(s.x * g.w, s.y * g.h))
    }

    // The moon breathes with the beat: its halo swells on each click and settles before the next.
    val moon = Offset(g.w * 0.83f, g.h * 0.105f)
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
private fun DrawScope.drawForest(g: LaneGeometry) {
    val far = floatArrayOf(0.02f, 0.13f, 0.235f, 0.765f, 0.87f, 0.98f)
    val farH = floatArrayOf(0.30f, 0.36f, 0.27f, 0.28f, 0.37f, 0.31f)
    for (i in far.indices) drawPine(far[i] * g.w, g.h, farH[i] * g.h, g.w * 0.17f, GameColors.stagePineFar)
    val near = floatArrayOf(-0.02f, 0.085f, 0.915f, 1.02f)
    val nearH = floatArrayOf(0.50f, 0.40f, 0.42f, 0.52f)
    for (i in near.indices) drawPine(near[i] * g.w, g.h, nearH[i] * g.h, g.w * 0.22f, GameColors.stagePineNear)
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

private fun DrawScope.drawLane(g: LaneGeometry, dp: Float, heat: Float) {
    val topHalf = g.halfWidthAt(0f)
    val bottomHalf = g.halfWidthAt(g.h)
    val beam = Path().apply {
        moveTo(g.cx - topHalf, 0f)
        lineTo(g.cx + topHalf, 0f)
        lineTo(g.cx + bottomHalf, g.h)
        lineTo(g.cx - bottomHalf, g.h)
        close()
    }
    val lit = 0.30f + 0.20f * heat
    drawPath(
        beam,
        Brush.verticalGradient(
            0f to GameColors.laneGlow.copy(alpha = 0f),
            HIT_LINE_FRACTION to GameColors.laneGlow.copy(alpha = lit),
            1f to GameColors.laneGlow.copy(alpha = lit * 0.45f),
        ),
    )
    // The edges: a hairline of gold that firms up as the combo builds.
    val edge = lerp(AppColors.goldBorder, AppColors.goldBorderStrong, heat)
    val edgeBrush = Brush.verticalGradient(0f to Color.Transparent, HIT_LINE_FRACTION to edge, 1f to edge)
    drawLine(edgeBrush, Offset(g.cx - topHalf, 0f), Offset(g.cx - bottomHalf, g.h), 1.2f * dp)
    drawLine(edgeBrush, Offset(g.cx + topHalf, 0f), Offset(g.cx + bottomHalf, g.h), 1.2f * dp)
}

/** The judgement windows, to scale: a note whose centre is inside the gold band scores PERFECT. */
private fun DrawScope.drawWindows(g: LaneGeometry, chart: RhythmChart) {
    val half = g.halfWidthAt(g.hitY)
    val perMs = g.hitY / chart.travelMs
    val good = chart.goodMs * perMs
    val perfect = chart.perfectMs * perMs
    drawRect(GameColors.good.copy(alpha = 0.06f), Offset(g.cx - half, g.hitY - good), Size(half * 2f, good * 2f))
    drawRect(AppColors.goldTintActive, Offset(g.cx - half, g.hitY - perfect), Size(half * 2f, perfect * 2f))
}

/** The beat grid scrolling down the lane: a line on each bar's first beat, ticks on the rest. */
private fun DrawScope.drawGrid(g: LaneGeometry, dp: Float, chart: RhythmChart, nowMs: Double) {
    val hits = chart.hitElapsedMs
    for (i in hits.indices) {
        val y = ((1.0 - (hits[i] - nowMs) / chart.travelMs) * g.hitY).toFloat()
        if (y < 0f) break            // later notes are further up still
        if (y > g.h) continue
        val half = g.halfWidthAt(y)
        if (i % chart.beatsPerBar == 0) {
            drawLine(Color.White.copy(alpha = 0.16f), Offset(g.cx - half, y), Offset(g.cx + half, y), 1f * dp)
        } else {
            val tick = half * 0.16f
            drawLine(Color.White.copy(alpha = 0.12f), Offset(g.cx - half, y), Offset(g.cx - half + tick, y), 1f * dp)
            drawLine(Color.White.copy(alpha = 0.12f), Offset(g.cx + half - tick, y), Offset(g.cx + half, y), 1f * dp)
        }
    }
}

private fun DrawScope.drawRing(
    g: LaneGeometry, dp: Float, color: Color, flash: Float, clapFlash: Float,
    beatPhase: Float, beatGlow: Float, downbeat: Boolean,
) {
    val c = Offset(g.cx, g.hitY)
    val half = g.halfWidthAt(g.hitY)
    val ringR = g.noteR + 3f * dp

    // The line across the lane.
    drawLine(color.copy(alpha = 0.50f), Offset(g.cx - half - 8f * dp, g.hitY), Offset(g.cx - ringR, g.hitY), 1.5f * dp, StrokeCap.Round)
    drawLine(color.copy(alpha = 0.50f), Offset(g.cx + ringR, g.hitY), Offset(g.cx + half + 8f * dp, g.hitY), 1.5f * dp, StrokeCap.Round)

    // The beat itself: a ring that leaves the target on each click. Gold on the bar's first beat.
    if (beatGlow > 0.01f) {
        drawCircle(
            color = (if (downbeat) AppColors.gold else AppColors.textAccent).copy(alpha = beatGlow * 0.32f),
            radius = ringR * (1f + 0.85f * beatPhase),
            center = c,
            style = Stroke(width = 1.5f * dp),
        )
    }

    if (flash > 0.01f) {
        val r = g.noteR * 3.2f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.42f * flash), Color.Transparent), c, r), r, c)
    }
    drawCircle(color.copy(alpha = 0.08f + 0.10f * flash), ringR, c)
    drawCircle(color, ringR, c, style = Stroke(width = 3f * dp))

    // The microphone heard a clap, whether or not it scored.
    if (clapFlash > 0.01f) {
        drawCircle(Color.White.copy(alpha = 0.70f * clapFlash), ringR + 5f * dp, c, style = Stroke(width = 2f * dp))
    }
}

private fun DrawScope.drawNotes(g: LaneGeometry, chart: RhythmChart?, notes: List<RenderNote>, nowMs: Double) {
    for (note in notes) {
        val hitMs = chart?.hitElapsedMs?.getOrNull(note.id)
        // From the clock when the chart is known; the game loop's snapshot only as a fallback.
        val progress = if (chart != null && hitMs != null) (1.0 - (hitMs - nowMs) / chart.travelMs).toFloat() else note.progress
        val y = progress * g.hitY
        val scale = g.scaleAt(y)
        val r = g.noteR * scale
        if (y < -r * 4f || y > g.h + r) continue

        val missed = note.state == NoteState.MISSED
        val alpha = if (missed) (1f - (progress - 1f) / 0.45f).coerceIn(0f, 1f) else 1f
        if (alpha <= 0f) continue
        val inPerfect = !missed && chart != null && hitMs != null && abs(hitMs - nowMs) <= chart.perfectMs
        val light: Color
        val base: Color
        val dark: Color
        when {
            missed -> { light = GameColors.miss; base = GameColors.miss; dark = GameColors.stagePineNear }
            inPerfect -> { light = GameColors.noteGoldLight; base = AppColors.gold; dark = GameColors.noteGoldDark }
            else -> { light = GameColors.noteLight; base = GameColors.noteBase; dark = GameColors.noteDark }
        }
        val c = Offset(g.cx, y)

        if (!missed) {
            // A tail of light, so the direction and speed read at a glance.
            val tail = Path().apply {
                moveTo(g.cx - r * 0.62f, y)
                lineTo(g.cx + r * 0.62f, y)
                lineTo(g.cx, y - r * 3.4f)
                close()
            }
            drawPath(tail, Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.42f)), startY = y - r * 3.4f, endY = y))
        }
        val glowR = r * (if (inPerfect) 2.7f else 2.2f)
        drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.40f * alpha), Color.Transparent), c, glowR), glowR, c)
        // The body: one key light from the upper left, like everything else Metro owns.
        drawCircle(
            brush = Brush.radialGradient(
                0f to light, 0.55f to base, 1f to dark,
                center = Offset(g.cx - r * 0.30f, y - r * 0.36f), radius = r * 1.5f,
            ),
            radius = r, center = c, alpha = alpha,
        )
        drawNote(Offset(g.cx - r * 0.10f, y), r * 0.40f, GameColors.noteGlyph.copy(alpha = 0.72f * alpha))
    }
}

private fun DrawScope.drawFireflies(scenery: LaneScenery, g: LaneGeometry, dp: Float, t: Float, combo: Int) {
    val awake = (3 + combo / 2).coerceAtMost(scenery.fireflies.size)
    for (i in 0 until awake) {
        val f = scenery.fireflies[i]
        val x = (f.x + f.ax * sin(t * f.fx + f.phase)) * g.w
        val y = (f.y + f.ay * cos(t * f.fy + f.phase * 1.7f)) * g.h
        val tw = sin(t * 1.3f + f.phase * 2.3f)
        val a = 0.30f + 0.70f * tw * tw
        val c = Offset(x, y)
        drawCircle(Brush.radialGradient(listOf(GameColors.firefly.copy(alpha = 0.45f * a), Color.Transparent), c, 8f * dp), 8f * dp, c)
        drawCircle(GameColors.firefly.copy(alpha = a), 1.5f * dp, c)
    }
}
