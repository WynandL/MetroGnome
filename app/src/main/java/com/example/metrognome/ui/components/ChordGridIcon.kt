package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Chord-diagram icon for the bottom navigation bar: a small fret grid with two fingered
 * notes, the shape every guitarist knows from a chord chart.
 *
 * Sized like [TunerNeedleIcon] beside it: the grid spans the 24 dp canvas with about
 * 1.5 dp of air top and bottom, so it reads at the same visual weight as the Material
 * icons on the other tabs. Kept to three strings, three fret spaces and two dots: the
 * first version had four of each and three dots, and beside the needle and the pulse it
 * read as the busiest thing on the bar. The nut is the only heavy line; the grid itself
 * is faint, so the dots and the nut carry the glyph.
 */
@Composable
fun ChordGridIcon() {
    val color = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val strings = 3
        val fretSpaces = 3
        val left = size.width * 0.20f
        val right = size.width * 0.80f
        val top = size.height * 0.10f
        val bottom = size.height * 0.94f
        val stringGap = (right - left) / (strings - 1)
        val fretGap = (bottom - top) / fretSpaces
        val thin = 1.1.dp.toPx()

        // Nut: the heavy line across the top.
        drawLine(color, Offset(left, top), Offset(right, top), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)

        // Frets and strings, muted like the needle icon's arc track.
        val grid = color.copy(alpha = 0.38f)
        for (i in 1..fretSpaces) {
            val y = top + i * fretGap
            drawLine(grid, Offset(left, y), Offset(right, y), strokeWidth = thin, cap = StrokeCap.Round)
        }
        for (i in 0 until strings) {
            val x = left + i * stringGap
            drawLine(grid, Offset(x, top), Offset(x, bottom), strokeWidth = thin, cap = StrokeCap.Round)
        }

        // Two fingered notes: (string index, fret space). Neither sits in the first space,
        // where a dot crowds the nut; the right one takes the middle space, the left one
        // the bottom, so the pair steps down the grid.
        val dotR = 2.2.dp.toPx()
        listOf(2 to 2, 0 to 3).forEach { (string, fret) ->
            drawCircle(
                color = color,
                radius = dotR,
                center = Offset(left + string * stringGap, top + (fret - 0.5f) * fretGap),
            )
        }
    }
}
