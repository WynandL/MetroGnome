package com.example.metrognome.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Material's emphasized easings: the container transform's open and close curves. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val EXPAND_MS = 460
private const val COLLAPSE_MS = 320

/** Tells the outer layout the pill's height, measured one level down in the same pass. */
private class PillHeight { var px = 0 }

/**
 * A pill that grows into a full-width card on tap, then shrinks back into the pill on its own.
 *
 * The card is one continuous surface with the pill: it grows from the pill's top-left corner
 * to the full width and the [panel]'s height, its corners relax from a capsule to the card
 * radius, and its fill and rim cross from the pill's gold wash to the card's. The pill's
 * words fade out early and the panel's content fades in late, revealed by the growing clip.
 *
 * **It never moves the page.** The component always reports the pill's size; the card draws
 * over whatever follows it. That needs two things from the caller: place this as a direct
 * child of the column whose later siblings it should cover (it raises its own z-index while
 * open), and give it the full width ([Modifier.fillMaxWidth]), which is the card's width.
 *
 * **It closes itself.** Left untouched it closes after [idleTimeoutMs]; once touched it stays
 * open while a finger is down anywhere on it, and closes [releaseTimeoutMs] after the last
 * lift. Touches are only observed, never consumed, so the panel's own controls work as usual.
 * Back closes it too, and so does a touch anywhere outside it inside the enclosing scroll
 * viewport; that touch is consumed (closing is all it does), the way a menu behaves.
 */
@Composable
fun MorphingPill(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    idleTimeoutMs: Long = 4_000L,
    releaseTimeoutMs: Long = 3_000L,
    pillColor: Color = AppColors.goldTint,
    pillBorderColor: Color = AppColors.gold.copy(alpha = 0.40f),
    panelColor: Color = AppColors.card,
    panelBorderColor: Color = AppColors.goldBorder,
    pill: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = if (expanded) tween(EXPAND_MS, easing = EmphasizedDecelerate)
                        else tween(COLLAPSE_MS, easing = EmphasizedAccelerate),
        label = "pillMorph",
    )

    var touching by remember { mutableStateOf(false) }
    var touchedSinceOpen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded, touching, touchedSinceOpen) {
        if (!expanded) {
            touchedSinceOpen = false
            return@LaunchedEffect
        }
        if (touching) return@LaunchedEffect
        delay(if (touchedSinceOpen) releaseTimeoutMs else idleTimeoutMs)
        onExpandedChange(false)
    }
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val cardRadiusPx = with(LocalDensity.current) { 20.dp.toPx() }
    val pillHeight = remember { PillHeight() }

    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val windowSize = LocalWindowInfo.current.containerSize
    var windowOrigin by remember { mutableStateOf(IntOffset.Zero) }

    // Outer: reports the pill's footprint only, so the page below never moves.
    Layout(
        modifier = modifier
            .zIndex(if (progress > 0f) 1f else 0f)
            .onGloballyPositioned { windowOrigin = it.positionInWindow().round() },
        content = {
            // Inner: the morphing surface, sized by the animation and clipped to its shape.
            Layout(
                modifier = Modifier.morphSurface(
                    progress = progress,
                    pillHeight = pillHeight,
                    cardRadiusPx = cardRadiusPx,
                    fill = lerp(pillColor, panelColor, progress),
                    rim = lerp(pillBorderColor, panelBorderColor, progress),
                ).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.any { it.pressed }
                            if (down) touchedSinceOpen = true
                            touching = down
                        }
                    }
                },
                content = {
                    Box(
                        Modifier.clickable(enabled = !expanded) { onExpandedChange(true) }
                    ) { pill() }
                    Box { panel() }
                },
            ) { measurables, constraints ->
                val fullWidth = constraints.maxWidth
                val pillPlaceable = measurables[0].measure(Constraints(maxWidth = fullWidth))
                val panelPlaceable = measurables[1].measure(Constraints.fixedWidth(fullWidth))
                pillHeight.px = pillPlaceable.height

                val width = lerp(pillPlaceable.width.toFloat(), fullWidth.toFloat(), progress).roundToInt()
                val height = lerp(pillPlaceable.height.toFloat(), panelPlaceable.height.toFloat(), progress).roundToInt()
                val pillAlpha = (1f - progress / 0.25f).coerceIn(0f, 1f)
                val panelAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val panelLift = (1f - panelAlpha) * 8.dp.toPx()

                layout(width, height) {
                    if (pillAlpha > 0f) pillPlaceable.placeWithLayer(0, 0) { alpha = pillAlpha }
                    // Not placed at rest, so the hidden panel can never take the pill's taps.
                    if (progress > 0f) {
                        panelPlaceable.placeWithLayer(0, 0) {
                            alpha = panelAlpha
                            translationY = panelLift
                        }
                    }
                }
            }
            // Outside-tap catcher: a transparent layer spanning the whole window, under the
            // surface, present only while open. Placed at minus this component's window
            // position, so it covers everything the enclosing scroll viewport shows.
            if (expanded) {
                Box(
                    Modifier
                        .layoutId(SCRIM_ID)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                currentOnExpandedChange(false)
                            }
                        }
                )
            }
        },
    ) { measurables, constraints ->
        val surface = measurables.first { it.layoutId != SCRIM_ID }
            .measure(Constraints(maxWidth = constraints.maxWidth))
        val scrim = measurables.firstOrNull { it.layoutId == SCRIM_ID }
            ?.measure(Constraints.fixed(windowSize.width, windowSize.height))
        layout(constraints.maxWidth, pillHeight.px) {
            scrim?.place(-windowOrigin.x, -windowOrigin.y)
            surface.place(0, 0)
        }
    }
}

private const val SCRIM_ID = "morphingPillScrim"

private fun Modifier.morphSurface(
    progress: Float,
    pillHeight: PillHeight,
    cardRadiusPx: Float,
    fill: Color,
    rim: Color,
): Modifier {
    // The pill's height is only known after measuring, so the radius is resolved at draw time.
    val shape = GenericShape { size, _ ->
        val capsule = if (pillHeight.px > 0) pillHeight.px / 2f else size.height / 2f
        val r = lerp(capsule, cardRadiusPx, progress)
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
    }
    return this
        .clip(shape)
        .background(fill, shape)
        .border(1.dp, rim, shape)
}
