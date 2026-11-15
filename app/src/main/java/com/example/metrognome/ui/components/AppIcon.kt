package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * How the app's icons are drawn. One family, one line: every small icon in the app is a
 * single-colour glyph drawn with a 1.5 dp line, whatever size it is shown at (see CLAUDE.md,
 * "Icons"), and in one ink (see OneInk.kt): its paths never darken where they meet. The path
 * data is in the generated AppIcons.kt.
 *
 * Use [AppIcon] exactly as Material's Icon was used; pass an [AppGlyph] wherever an icon
 * travels as a value (a button's leading icon, a banner model). Inside a Canvas that already
 * exists (the rhythm lane's notes), call [drawGlyph].
 */

/**
 * One icon: path data on a 24 x 24 grid, parsed on first use. [filled] fills every path and is
 * for transport shapes only. The app's own marks can fill single paths ([filledPaths]) and draw
 * others as the family's faint secondary line ([faintPaths], 38% and dropped under 14 dp).
 */
@Immutable
class AppGlyph internal constructor(
    private val data: List<String>,
    internal val filled: Boolean = false,
    internal val filledPaths: Set<Int> = emptySet(),
    internal val faintPaths: Set<Int> = emptySet(),
) {
    internal val paths: List<Path> by lazy(LazyThreadSafetyMode.NONE) {
        data.map { PathParser().parsePathString(it).toPath() }
    }
}

private const val GRID = 24f

/** The glyph's own box on the grid, plus room for the line's round caps. */
private val GRID_BOUNDS = Rect(-2f, -2f, GRID + 2f, GRID + 2f)

/**
 * Draws [glyph] in [tint]. 24 dp unless [modifier] sets a size. The line is 1.5 dp at every
 * size (1.25 dp under 14 dp, where 1.5 closes the counters up): it is never scaled with the
 * icon, which is what keeps a 13 dp icon and a 26 dp icon looking like the same pen.
 */
@Composable
fun AppIcon(
    glyph: AppGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }
    } else Modifier
    Canvas(modifier = modifier.size(24.dp).then(semantics)) {
        val side = size.minDimension
        val small = side < 14.dp.toPx()
        drawGlyph(
            glyph = glyph,
            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
            side = side,
            tint = tint,
            linePx = (if (small) 1.25f else 1.5f).dp.toPx(),
            dropFaint = small,
        )
    }
}

/**
 * Draws [glyph] in a [side] x [side] px square at [topLeft], with a [linePx] line, in one ink:
 * every path is painted opaque and [tint]'s alpha is applied once, so a handle meeting its cup
 * or a stem meeting its head is the same shade as the rest. Faint paths are their own layer at
 * [FAINT_INK] underneath the solid ones, so they do not knot where they cross each other
 * either. [dropFaint] leaves them out (AppIcon does under 14 dp).
 */
internal fun DrawScope.drawGlyph(
    glyph: AppGlyph,
    topLeft: Offset,
    side: Float,
    tint: Color,
    linePx: Float,
    dropFaint: Boolean = false,
) {
    val s = side / GRID
    val ink = tint.opaque()
    translate(topLeft.x, topLeft.y) {
        scale(s, s, pivot = Offset.Zero) {
            oneInk(tint.alpha, GRID_BOUNDS) {
                val stroke = Stroke(width = linePx / s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                fun draw(i: Int, path: Path) {
                    if (glyph.filled || i in glyph.filledPaths) drawPath(path, ink)
                    drawPath(path, ink, style = stroke)
                }
                if (!dropFaint && glyph.faintPaths.isNotEmpty()) {
                    oneInk(FAINT_INK, GRID_BOUNDS) {
                        glyph.paths.forEachIndexed { i, path -> if (i in glyph.faintPaths) draw(i, path) }
                    }
                }
                glyph.paths.forEachIndexed { i, path -> if (i !in glyph.faintPaths) draw(i, path) }
            }
        }
    }
}
