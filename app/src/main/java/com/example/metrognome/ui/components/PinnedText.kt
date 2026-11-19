package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit

/*
 * Text that must not move the layout around it (CLAUDE.md, the Typography trap). Moved out of
 * ChordFinderScreen on 2026-11-19 when the sound stage needed the same no-jump slots; a pinned
 * two-line caption needs all three parts: style = PinnedLines, minLines = maxLines = 2, and a
 * PinnedSlot of exactly two lines.
 */

/**
 * Makes `lineHeight` literal within the paragraph: a line is exactly that tall whatever
 * is on it. The app's ambient style leaves the default `Trim.Both`, under which a single
 * line is its *natural* height and `lineHeight` only spaces the lines between.
 */
val PinnedLines = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * A slot exactly [lines] x [lineHeight] tall, whatever the text inside it measures.
 *
 * [PinnedLines] alone is not enough: with font padding off, Compose adds a top and bottom
 * padding to a `Text` derived from the ink bounds of the glyphs on its first and last line
 * (`TextLayout.getVerticalPaddings`), so a 44 sp Black "C" and a 24 sp "Play or tap notes"
 * on the same 50 sp line still measure a pixel or two apart, and the card below them
 * shifted by that much between readings. Taking the height from the density instead of
 * the text is what finally pins it; a text that measures a hair taller than its slot
 * simply paints over the edge, which is invisible.
 */
@Composable
fun PinnedSlot(
    lineHeight: TextUnit,
    lines: Int = 1,
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) {
    val height = with(LocalDensity.current) { (lineHeight * lines).toDp() }
    Box(
        contentAlignment = alignment,
        modifier = Modifier.fillMaxWidth().height(height),
    ) { content() }
}
