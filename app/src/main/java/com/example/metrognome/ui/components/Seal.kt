package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app-wide "seal" asset: a scalloped rosette, the visual language of a certification
 * mark. It means *earned and verified* everywhere it appears - the Groove Check pass, an
 * achieved streak day, an unlocked collection item - and every appearance is this one
 * element with a different [SealStyle], never a re-drawn copy.
 *
 * Three layers, so it composes into any context:
 *  - [SealStyle]      the knobs: colors, transparency, scallop count/depth, check stroke.
 *  - [drawSeal]       the DrawScope core. Use this when the seal is part of a larger
 *                     Canvas (the streak day dots draw their halo through it).
 *  - [Seal]           the self-contained composable element: sizes via [Modifier.size],
 *                     drifts on its own clock, optionally plays the stamp entrance.
 *
 * Motion. The seal is a *slowly drifting* mark by default ([SEAL_DRIFT_PERIOD_MS] per
 * revolution) - the drift is what makes it feel alive at rest. Only the scalloped
 * rosette rotates; the check mark always stays upright. The one-shot [Seal] `entrance`
 * (spring stamp + check stroke-in) is reserved for earned moments like the Groove Check
 * pass; ambient uses (badges, halos) drift only.
 */

/**
 * Visual parameters of a seal. All knobs in one place so new uses adapt variables
 * instead of redrawing the asset.
 *
 * [sealAlpha] multiplies [sealColor]'s own alpha (1 = the screenshot-solid badge,
 * low values give the translucent halo look). [checkColor] null omits the check
 * entirely (the streak halo form). [scallops]/[scallopDepth] trade legibility against
 * size: small renders need fewer, deeper scallops or the edge vanishes into
 * anti-aliasing. [checkStrokeWidth] is a fraction of the seal radius.
 */
data class SealStyle(
    val sealColor: Color = AppColors.gold,
    val sealAlpha: Float = 1f,
    val checkColor: Color? = AppColors.surfaceDeep,
    val scallops: Float = 12f,
    val scallopDepth: Float = 0.06f,
    val checkStrokeWidth: Float = 0.18f,
) {
    companion object {
        /** Large ceremonial form (Groove Check pass): fine scallops read at ~56dp. */
        val Emblem = SealStyle()

        /** Small badge form (~16dp corners): fewer, deeper scallops stay legible. */
        val Badge = SealStyle(scallops = 10f, scallopDepth = 0.10f)

        /** Translucent check-less ring form (streak day halo) in the caller's color. */
        fun halo(color: Color, alpha: Float) = SealStyle(
            sealColor = color,
            sealAlpha = alpha,
            checkColor = null,
            scallops = 10f,
            scallopDepth = 0.12f,
        )
    }
}

/** Default drift: one revolution per this many ms - a lazy, ambient rotation. */
const val SEAL_DRIFT_PERIOD_MS = 16_000

/**
 * The seal as a self-contained UI element. Size it with [Modifier.size]; everything
 * else is [style] plus motion:
 *
 *  - [rotationPeriodMs] ms per revolution of the rosette (null = static). Instances
 *    drift on their own clocks; give neighbours different [rotationPhaseDeg]s so a
 *    row/grid of seals doesn't rotate in lockstep.
 *  - [entrance] plays the one-shot stamp: spring scale-in with a settle-rotation
 *    riding the same spring (so it lands *stamped*, wobbling in sync), then the check
 *    strokes itself tip to tail. Leave false for ambient uses - a scrolling grid
 *    re-triggering entrances would be noise.
 */
@Composable
fun Seal(
    modifier: Modifier = Modifier,
    style: SealStyle = SealStyle.Badge,
    rotationPeriodMs: Int? = SEAL_DRIFT_PERIOD_MS,
    rotationPhaseDeg: Float = 0f,
    entrance: Boolean = false,
) {
    val stamp = remember { Animatable(if (entrance) 0f else 1f) }
    val check = remember { Animatable(if (entrance) 0f else 1f) }
    if (entrance) {
        LaunchedEffect(Unit) {
            launch {
                stamp.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            launch {
                delay(CHECK_DELAY_MS)   // let the stamp land first
                check.animateTo(1f, tween(CHECK_DRAW_MS, easing = FastOutSlowInEasing))
            }
        }
    }

    val drift: State<Float> = if (rotationPeriodMs != null) {
        rememberInfiniteTransition(label = "seal_drift").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(rotationPeriodMs, easing = LinearEasing)),
            label = "seal_drift_angle",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val s = stamp.value
        if (s <= 0f) return@Canvas
        // Stamp scale and settle-rotation ride the same spring, so the overshoot wobbles
        // rotation and scale together. The drift rotates only the rosette (inside
        // drawSeal); the check stays upright once the stamp has settled.
        scale(s, s, pivot = center) {
            rotate(degrees = (1f - s) * STAMP_ROTATION_DEG, pivot = center) {
                drawSeal(
                    style = style,
                    rotationDeg = drift.value + rotationPhaseDeg,
                    checkProgress = check.value,
                )
            }
        }
    }
}

/**
 * DrawScope core: one seal at [center]/[radius], rosette rotated by [rotationDeg]
 * (the check mark deliberately does NOT rotate - a spinning check reads as broken),
 * check drawn tip-to-tail up to [checkProgress]. The single draw path every form of
 * the seal goes through, so a geometry tweak here propagates app-wide.
 */
fun DrawScope.drawSeal(
    style: SealStyle,
    center: Offset = this.center,
    radius: Float = size.minDimension / 2f,
    rotationDeg: Float = 0f,
    checkProgress: Float = 1f,
) {
    val rosette = Path().apply {
        addSealOutline(center, radius, style.scallops, style.scallopDepth)
    }
    val fill = style.sealColor.copy(alpha = style.sealColor.alpha * style.sealAlpha)
    rotate(degrees = rotationDeg, pivot = center) {
        drawPath(rosette, fill)
    }

    val checkColor = style.checkColor ?: return
    if (checkProgress <= 0f) return
    val mark = Path().apply { addCheckMark(center, radius) }
    val drawn = if (checkProgress >= 1f) mark else Path().also { partial ->
        val measure = PathMeasure()
        measure.setPath(mark, forceClosed = false)
        measure.getSegment(0f, measure.length * checkProgress, partial, startWithMoveTo = true)
    }
    drawPath(
        drawn,
        checkColor,
        style = Stroke(
            width = radius * style.checkStrokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/**
 * Appends the scalloped-rosette outline: a closed path whose radius undulates
 * sinusoidally out to [outerRadius]. [scallops] bumps around the ring (12 reads as a
 * seal; fewer as a flower, more as a gear); [depth] is the amplitude as a fraction of
 * [outerRadius].
 */
private fun Path.addSealOutline(
    center: Offset,
    outerRadius: Float,
    scallops: Float,
    depth: Float,
    steps: Int = 96,
) {
    val amp = outerRadius * depth
    val base = outerRadius - amp
    for (i in 0..steps) {
        val theta = (i.toFloat() / steps) * TWO_PI
        val rad = base + amp * cos(scallops * theta)
        val x = center.x + rad * cos(theta)
        val y = center.y + rad * sin(theta)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * The seal's check mark, tip to tail; the elbow sits slightly low-left of centre so
 * the long stroke crosses the seal's optical middle.
 */
private fun Path.addCheckMark(center: Offset, r: Float) {
    moveTo(center.x - r * 0.34f, center.y + r * 0.02f)
    lineTo(center.x - r * 0.08f, center.y + r * 0.28f)
    lineTo(center.x + r * 0.38f, center.y - r * 0.22f)
}

private const val TWO_PI = (Math.PI * 2).toFloat()

/** Rotation (degrees) the entrance stamp lands through while its spring settles. */
private const val STAMP_ROTATION_DEG = -50f

/** Pause before the entrance check strokes in, letting the stamp mostly land. */
private const val CHECK_DELAY_MS = 260L

/** Duration of the entrance's tip-to-tail check draw. */
private const val CHECK_DRAW_MS = 340

@Preview
@Composable
private fun SealEmblemPreview() {
    Seal(modifier = Modifier.size(56.dp), style = SealStyle.Emblem, entrance = true)
}

@Preview
@Composable
private fun SealBadgePreview() {
    Seal(modifier = Modifier.size(16.dp), style = SealStyle.Badge)
}
