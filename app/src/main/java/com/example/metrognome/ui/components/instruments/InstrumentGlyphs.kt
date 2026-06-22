package com.example.metrognome.ui.components.instruments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Instrument glyphs rendered from Lucide icon path data (ISC licensed, see
 * THIRD_PARTY_LICENSES.md at the repo root). Each icon is authored on Lucide's 24x24 grid
 * with a 2px stroke; we parse the path strings once ([PARSED_PATHS]) and stroke them in a
 * single [color] so the caller owns the bright/dim state (see [InstrumentIcon]).
 *
 * Asset reuse: one stroke spec and one render path for every instrument.
 */

// Lucide path data on a 24x24 viewport. SVG <ellipse> and <rect> elements are pre-converted
// to path commands because Compose paths accept path commands only.
private val RAW_PATHS: Map<Instrument, List<String>> = mapOf(
    // lucide "drum"
    Instrument.DRUMS to listOf(
        "m2 2 8 8",
        "m22 2-8 8",
        "M2,9a10,5 0 1,0 20,0a10,5 0 1,0 -20,0Z",
        "M7 13.4v7.9",
        "M12 14v8",
        "M17 13.4v7.9",
        "M2 9v8a10 5 0 0 0 20 0V9",
    ),
    // lucide "piano"
    Instrument.KEYS to listOf(
        "M18.5 8c-1.4 0-2.6-.8-3.2-2A6.87 6.87 0 0 0 2 9v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-8.5C22 9.6 20.4 8 18.5 8",
        "M2 14h20",
        "M6 14v4",
        "M10 14v4",
        "M14 14v4",
        "M18 14v4",
    ),
    // lucide "guitar"
    Instrument.GUITAR to listOf(
        "m11.9 12.1 4.514-4.514",
        "M20.1 2.3a1 1 0 0 0-1.4 0l-1.114 1.114A2 2 0 0 0 17 4.828v1.344a2 2 0 0 1-.586 1.414A2 2 0 0 1 17.828 7h1.344a2 2 0 0 0 1.414-.586L21.7 5.3a1 1 0 0 0 0-1.4z",
        "m6 16 2 2",
        "M8.23 9.85A3 3 0 0 1 11 8a5 5 0 0 1 5 5 3 3 0 0 1-1.85 2.77l-.92.38A2 2 0 0 0 12 18a4 4 0 0 1-4 4 6 6 0 0 1-6-6 4 4 0 0 1 4-4 2 2 0 0 0 1.85-1.23z",
    ),
    // lucide "mic"
    Instrument.VOICE to listOf(
        "M12 19v3",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M9,5a3,3 0 0 1 6,0v7a3,3 0 0 1 -6,0z",
    ),
)

private val PARSED_PATHS: Map<Instrument, List<Path>> by lazy {
    RAW_PATHS.mapValues { (_, ds) -> ds.map { PathParser().parsePathString(it).toPath() } }
}

/** Strokes the [instrument]'s Lucide glyph in [color], scaled to fill the draw bounds. */
internal fun DrawScope.drawInstrumentGlyph(instrument: Instrument, color: Color) {
    val paths = PARSED_PATHS[instrument] ?: return
    val unit = size.minDimension / 24f
    val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    scale(unit, pivot = Offset.Zero) {
        paths.forEach { drawPath(it, color = color, style = stroke) }
    }
}
