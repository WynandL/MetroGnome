package com.example.metrognome.ui.meter

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.game.PresentationLead
import com.example.metrognome.ui.components.drawSpark
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.ItemPalette
import com.example.metrognome.viewmodel.BeatEvent
import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * The beat gem: one beat of the bar, drawn the same way on the Home strip, on the meter
 * stage's wheel and in its accent editor, so the three can never look like different things.
 *
 * At rest it is a small lifted bead lit from the upper left (the app's one key light, as on
 * every raised face): dark violet for a plain beat, dark gold for an accent. When its beat
 * sounds it lights from the heart outward, pops, sends one ring out and (accents only) throws
 * four tiny glints, the About card's sparks in a dose small enough not to compete with Metro.
 * Then it fades over about a beat, so the last beat leaves a faint trail behind the current
 * one and the bar reads as motion, not as a dot jumping.
 */

/** Seconds a gem's ring takes to go out, and its glints. */
private const val RING_S = 0.45f
private const val GLINT_S = 0.38f
private const val NEVER = Long.MIN_VALUE

/** A presentation time further ahead than this is not believed (a bad timestamp). */
private const val MAX_LEAD_NS = 1_000_000_000L

/**
 * When each beat last sounded, and a frame clock to animate from. One per surface.
 * Read [frameNs] inside a draw block to redraw (not recompose) every frame while it runs.
 *
 * **A gem lights when its click is heard, not when the engine queues it.** The beat callback
 * fires a whole output buffer early (about a quarter of a second on the dev's S926B), so each
 * beat is held in [pending] until its `presentedMs` (when the click leaves the speaker, the
 * clock the mic features score against) and [frameNs] is the time the frame being drawn
 * reaches the glass, not its vsync. The two meet on the beat.
 */
@Stable
class BeatPulse internal constructor() {
    private val hitNs = LongArray(MeterLayout.MAX_BEATS) { NEVER }
    private var lastStampNs = NEVER
    private val pending = ArrayDeque<Pair<Int, Long>>()
    internal var celebrateNs = NEVER
        private set
    internal var beatSec = 0.5f

    /** On-glass time of the frame being drawn, System.nanoTime based. Read it in a draw block first. */
    var frameNs by mutableLongStateOf(System.nanoTime())
        internal set

    /** Bumped to wake the frame loop for a one-off animation. */
    internal var wake by mutableIntStateOf(0)
    internal var running = false

    /** Light the whole bar in a cascade (the stage's "chosen" moment). */
    fun celebrate() {
        celebrateNs = frameNs.coerceAtLeast(System.nanoTime())
        if (!running) wake++
    }

    /** The last beat that sounded, or -1. */
    var lastBeat = -1
        private set

    /** Beat [beat] will be heard at [atNs] (System.nanoTime base). */
    internal fun schedule(beat: Int, atNs: Long) {
        val now = System.nanoTime()
        pending.addLast(beat to atNs.coerceIn(now - MAX_LEAD_NS, now + MAX_LEAD_NS))
    }

    /** Beats that will never be heard (the metronome stopped). */
    internal fun dropPending() = pending.clear()

    /** Moves every beat heard by [frameNs] from [pending] to the bar. */
    internal fun advance() {
        while (pending.isNotEmpty() && pending.first().second <= frameNs) {
            val (beat, at) = pending.removeFirst()
            if (beat in hitNs.indices) hitNs[beat] = at
            lastBeat = beat
            lastStampNs = at
        }
    }

    /** Seconds since beat [i] sounded, or null if it has not (or long ago). */
    fun age(i: Int): Float? {
        val t = hitNs.getOrNull(i) ?: return null
        if (t == NEVER) return null
        val a = (frameNs - t) / 1e9f
        return if (a > 4f || a < 0f) null else a
    }

    /** 1 at the beat, fading over roughly a beat. */
    fun lit(i: Int): Float = age(i)?.let { exp(-it / (beatSec * 0.55f)) } ?: 0f

    /** The pop on a hit: up over 70 ms, settling over ~160 ms. */
    fun pop(i: Int): Float = age(i)?.let { if (it < 0.07f) it / 0.07f else exp(-(it - 0.07f) / 0.16f) } ?: 0f

    fun ring(i: Int): Float = age(i)?.let { if (it < RING_S) it / RING_S else -1f } ?: -1f
    fun glint(i: Int): Float = age(i)?.let { if (it < GLINT_S) it / GLINT_S else -1f } ?: -1f

    /** Seconds since [celebrate], or null. */
    fun sinceCelebrate(): Float? =
        if (celebrateNs == NEVER) null else ((frameNs - celebrateNs) / 1e9f).takeIf { it < 3f }?.coerceAtLeast(0f)

    /** Where the bar has got to, in beats (the last beat plus the time since it), or null. */
    fun barPosition(): Float? {
        if (lastBeat < 0 || lastStampNs == NEVER) return null
        val since = (frameNs - lastStampNs) / 1e9f
        if (since > beatSec * 1.5f) return null
        return lastBeat + (since / beatSec).coerceIn(0f, 1f)
    }

    internal fun idle(): Boolean {
        if (pending.isNotEmpty()) return false
        val quiet = lastStampNs == NEVER || frameNs - lastStampNs > 2_000_000_000L
        val calm = celebrateNs == NEVER || frameNs - celebrateNs > 3_000_000_000L
        return quiet && calm
    }
}

/**
 * A [BeatPulse] fed by [beats], ticking every frame while [playing], while [keepAwake], or
 * until the last animation settles; idle, it costs nothing.
 */
@Composable
fun rememberBeatPulse(beats: Flow<BeatEvent>?, playing: Boolean, bpm: Int, keepAwake: Boolean = false): BeatPulse {
    val pulse = remember { BeatPulse() }
    pulse.beatSec = 60f / bpm.coerceIn(20, 300)
    val awake by rememberUpdatedState(playing || keepAwake)
    val view = LocalView.current
    LaunchedEffect(beats) {
        beats?.collect { e ->
            // presentedMs is on the boot clock; frames are on System.nanoTime.
            val bootOffsetNs = SystemClock.elapsedRealtimeNanos() - System.nanoTime()
            pulse.schedule(e.beat, e.presentedMs * 1_000_000L - bootOffsetNs)
            if (!pulse.running) pulse.wake++
        }
    }
    LaunchedEffect(playing) { if (!playing) pulse.dropPending() }
    LaunchedEffect(playing, keepAwake, pulse.wake) {
        val refresh = view.display?.refreshRate?.takeIf { it >= 30f } ?: 60f
        // The rhythm stage's estimate of how far ahead of its vsync a frame reaches the glass.
        val lead = PresentationLead(fallbackNs = (2e9 / refresh).toLong())
        pulse.running = true
        try {
            while (true) {
                withFrameNanos { t ->
                    pulse.frameNs = t + lead.ns
                    pulse.advance()
                }
                if (!awake && pulse.idle()) break
            }
        } finally {
            lead.stop()
            pulse.running = false
        }
    }
    return pulse
}

private fun easeOut(p: Float) = 1f - (1f - p).pow(3)

/**
 * One beat gem at [c], radius [r]. [lit] 0..1 lights it; [ring] and [glint] are 0..1 progress
 * of the hit's ring and glints (negative for none); [alpha] dims the whole gem.
 */
internal fun DrawScope.drawBeatGem(
    c: Offset,
    r: Float,
    accent: Boolean,
    lit: Float = 0f,
    ring: Float = -1f,
    glint: Float = -1f,
    alpha: Float = 1f,
) {
    val dp = 1.dp.toPx()
    val hue = if (accent) AppColors.gold else AppColors.textAccent
    val l = lit.coerceIn(0f, 1f)

    if (l > 0.01f) {
        val halo = r * 2.8f
        drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = 0.40f * l * alpha), Color.Transparent), c, halo), halo, c)
    }

    // At rest the gems are quiet (the old dots were a 20-40% wash), so a lit beat reads at once.
    val restTop = if (accent) lerp(AppColors.gold, AppColors.card, 0.55f) else lerp(AppColors.textAccent, AppColors.card, 0.70f)
    val restBottom = if (accent) lerp(AppColors.amberDark, AppColors.card, 0.50f) else lerp(AppColors.deepPurple, AppColors.card, 0.45f)
    val litTop = if (accent) AppColors.amberLight else AppColors.beatLavender
    val top = lerp(restTop, litTop, l)
    val bottom = lerp(restBottom, hue, l)
    drawCircle(
        Brush.radialGradient(
            listOf(top.copy(alpha = alpha), bottom.copy(alpha = alpha)),
            Offset(c.x - r * 0.35f, c.y - r * 0.40f), r * 1.6f,
        ),
        r, c,
    )
    // Rim bright on the top edge, gone by the bottom: a uniform outline reads as a drawn border.
    drawCircle(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = (0.28f + 0.2f * l) * alpha),
            0.5f to Color.Transparent,
            startY = c.y - r, endY = c.y + r,
        ),
        r - 0.5f * dp, c,
        style = Stroke(width = min(1f * dp, r * 0.2f)),
    )
    drawCircle(Color.White.copy(alpha = (0.13f + 0.62f * l) * alpha), r * 0.26f, Offset(c.x - r * 0.34f, c.y - r * 0.38f))

    if (ring in 0f..1f) {
        drawCircle(
            hue.copy(alpha = 0.55f * (1f - ring).pow(1.5f) * alpha),
            r * (1.15f + 1.5f * easeOut(ring)), c,
            style = Stroke(width = (1.5f * (1f - ring) + 0.5f) * dp),
        )
    }
    if (accent && glint in 0f..1f) {
        val d = r * (1.5f + 1.9f * easeOut(glint))
        val s = 3f * dp * (1f - glint) * (0.6f + 0.4f * min(1f, r / (6f * dp)))
        for (k in 0..3) {
            val a = (PI / 4 + k * PI / 2).toFloat()
            drawSpark(Offset(c.x + cos(a) * d, c.y + sin(a) * d), s, ItemPalette.goldLight.copy(alpha = (1f - glint) * alpha))
        }
    }
}
