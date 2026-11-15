package com.example.metrognome.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope

/*
 * One ink: how a glyph made of several strokes is drawn in a translucent colour.
 *
 * Drawing each stroke straight onto the canvas at, say, 30% white composites every stroke on
 * its own, so wherever two of them meet (a trophy's handle on its cup, a note's stem on its
 * head, two grid lines crossing) the alpha stacks and the joint shows as a darker or brighter
 * knot. A pen does not do that: one ink is one shade. So the strokes are painted opaque into an
 * offscreen layer and the layer is composited once at the colour's alpha.
 *
 * Every multi-part glyph uses this: [drawGlyph] (every AppIcon), the hand-drawn tab icons,
 * the particles' note. A new one drawn from more than one shape must too: draw the parts in
 * [opaque] colour inside [oneInk], never in a colour carrying alpha.
 */

/** The family's faint secondary line, as a fraction of the glyph's own alpha. */
internal const val FAINT_INK = 0.38f

/** [this] colour at full alpha, for drawing inside [oneInk]. */
internal fun Color.opaque(): Color = copy(alpha = 1f)

/**
 * Runs [block] into an offscreen layer composited once at [alpha], so the parts it draws (in
 * opaque colour) read as one shade where they overlap. [bounds] is in the current coordinate
 * space and must contain everything [block] draws; keep it tight when drawn every frame.
 */
internal inline fun DrawScope.oneInk(
    alpha: Float,
    bounds: Rect = size.toRect(),
    block: DrawScope.() -> Unit,
) {
    when {
        alpha <= 0f -> return
        alpha >= 1f -> block()
        else -> {
            val canvas = drawContext.canvas
            canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
            try {
                block()
            } finally {
                canvas.restore()
            }
        }
    }
}
