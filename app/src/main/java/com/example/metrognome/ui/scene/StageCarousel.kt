package com.example.metrognome.ui.scene

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Ease-out cubic: leaves at three times the average speed and comes to rest with zero speed,
 * so a glide that starts at the finger's speed slows smoothly to a stop on the medallion.
 */
private val GlideEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** The easing's starting slope: initial speed = this x distance / duration. */
private const val GLIDE_START_SLOPE = 1f / 0.33f

/** Below this release speed a swipe is a step, decided by how far it was dragged. */
private val STEP_VELOCITY = 400.dp

/** A slow drag released this far towards the next medallion goes there. */
private const val STEP_THRESHOLD = 0.35f

private const val GLIDE_MIN_MS = 180
private const val GLIDE_MAX_MS = 1400
private const val STEP_MS = 280

/**
 * How every stage carousel (the sound stages and the meter stage) moves under a finger.
 *
 * Compose's pager default lets a fling travel one page at most, and even when allowed further
 * it lands with a spring that ignores the finger's speed, so a hard flick stopped rather than
 * slowed (dev, 2026-11-23). Here a fling coasts as far as the platform's own decay says it
 * would (as in any scrolling list), rounded to a whole medallion, and gets there on an
 * ease-out cubic Bezier whose starting speed matches the finger's, so the motion carries on
 * from the hand and slows to rest on the medallion. A gentle swipe steps exactly one, and a
 * slow drag released [STEP_THRESHOLD] of the way to the next medallion goes there.
 */
@Composable
fun stageFlingBehavior(pager: PagerState): TargetedFlingBehavior {
    val decay = rememberSplineBasedDecay<Float>()
    val stepVelocityPx = with(LocalDensity.current) { STEP_VELOCITY.toPx() }
    return remember(pager, decay, stepVelocityPx) { StageFling(pager, decay, stepVelocityPx) }
}

private class StageFling(
    private val pager: PagerState,
    private val decay: DecayAnimationSpec<Float>,
    private val stepVelocityPx: Float,
) : TargetedFlingBehavior {

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val info = pager.layoutInfo
        val pagePx = (info.pageSize + info.pageSpacing).toFloat()
        if (pagePx <= 0f || pager.pageCount == 0) return initialVelocity

        val at = pager.currentPage + pager.currentPageOffsetFraction
        val coastPages = decay.calculateTargetValue(0f, initialVelocity) / pagePx
        val target = stageFlingTarget(at, initialVelocity, coastPages, stepVelocityPx, pager.pageCount).toFloat()

        val distance = (target - at) * pagePx
        if (abs(distance) < 0.5f) return 0f

        // A glide's duration makes its starting speed the finger's: v0 = slope x d / T.
        val ms = if (abs(initialVelocity) < stepVelocityPx) STEP_MS
        else (GLIDE_START_SLOPE * abs(distance) / abs(initialVelocity) * 1000f)
            .roundToInt().coerceIn(GLIDE_MIN_MS, GLIDE_MAX_MS)

        var scrolled = 0f
        animate(0f, distance, animationSpec = tween(ms, easing = GlideEasing)) { value, _ ->
            val consumed = scrollBy(value - scrolled)
            scrolled += consumed
            onRemainingDistanceUpdated(distance - scrolled)
        }
        return 0f
    }
}

/**
 * The page a fling released at [at] (in pages, 2.4 = four tenths from page 2 to 3) with
 * [velocity] lands on. Below [stepVelocity] it is a step decided by how far it was dragged
 * ([STEP_THRESHOLD] towards the release direction); otherwise it coasts [coastPages] (the
 * platform decay's distance) to the nearest whole page, never short of the next page in the
 * flick's direction. Pure, so the landing rules are tested without a finger.
 */
internal fun stageFlingTarget(at: Float, velocity: Float, coastPages: Float, stepVelocity: Float, pageCount: Int): Int {
    val base = floor(at)
    val past = at - base
    val target = if (abs(velocity) < stepVelocity) {
        when {
            velocity > 0f -> if (past >= STEP_THRESHOLD) base + 1 else base
            velocity < 0f -> if (past > 0f && 1f - past >= STEP_THRESHOLD) base else if (past > 0f) base + 1 else base
            else -> (at + 0.5f).let(::floor)
        }
    } else {
        val landing = floor(at + coastPages + 0.5f)
        if (velocity > 0f) maxOf(landing, base + 1)
        else minOf(landing, if (past > 0f) base else base - 1)
    }
    return target.toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
}

/** Closest two detent ticks may come, so a hard flick reads as a run of clicks, not a buzz. */
private const val DETENT_MIN_GAP_MS = 45L

/**
 * A light tick each time a new item takes the centre of a stage carousel, like a dial
 * clicking past its detents: a flick runs a quick ratchet, a one-step swipe gives one click.
 * Opening the stage is silent (only changes after the first page count).
 *
 * [quiet] silences it while a mic-scored session could hear the vibration (the phone's
 * motor reaches the mic; the Rhythm game has no haptics during play for the same reason).
 * Fires through [LocalHaptics], so the system haptics setting is honoured.
 */
@Composable
fun StageDetents(pager: PagerState, quiet: Boolean) {
    val haptics = LocalHaptics.current
    val silent by rememberUpdatedState(quiet)
    LaunchedEffect(pager) {
        var last = 0L
        snapshotFlow { pager.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                val now = android.os.SystemClock.uptimeMillis()
                if (!silent && now - last >= DETENT_MIN_GAP_MS) {
                    last = now
                    haptics.fire(HapticPattern.TICK)
                }
            }
    }
}
