package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/** Standard tuning, low to high: E2 A2 D3 G3 B3 E4. */
val GUITAR_STANDARD_TUNING = intArrayOf(40, 45, 50, 55, 59, 64)

/** Frets drawn after the nut. The twelfth fret is where the open strings repeat. */
const val GUITAR_FRETS = 12

/** Frets that carry a position marker on a real neck; the twelfth gets two. */
private val INLAY_FRETS = setOf(3, 5, 7, 9, 12)

/** The open-string column's width as a fraction of one fret. */
private const val NUT_COLUMN = 0.72f

/**
 * A drawn guitar neck, nut on the left, low E at the bottom, the way a right-handed player
 * sees it looking down. Every position sounding a MIDI note in [litMidi] is marked, so a
 * note that lives in two places (open E4 and the B string's fifth fret) shows in both; the
 * finder cannot know which one was meant and a player is glad of the second anyway.
 *
 * [labels] puts short text inside a marker (the note's degree in the chord). [glow] is a
 * short-lived halo on every position of one note, for the moment the microphone adds it.
 *
 * Taps toggle a position: the string comes from the row, the fret from the column, and the
 * column left of the nut is the open string.
 */
@Composable
fun GuitarFretboard(
    litMidi: Set<Int>,
    accent: Color,
    onPositionTap: (midi: Int) -> Unit,
    modifier: Modifier = Modifier,
    labels: Map<Int, String> = emptyMap(),
    glow: Pair<Int, Float>? = null,
    tuning: IntArray = GUITAR_STANDARD_TUNING,
) {
    val textMeasurer = rememberTextMeasurer()
    val strings = tuning.size

    Canvas(
        modifier = modifier.pointerInput(tuning) {
            detectTapGestures { offset ->
                val fretWidth = size.width / (GUITAR_FRETS + NUT_COLUMN)
                val fret = if (offset.x < fretWidth * NUT_COLUMN) 0
                    else ((offset.x - fretWidth * NUT_COLUMN) / fretWidth).toInt() + 1
                val rowHeight = size.height / strings
                // Row 0 is the top of the canvas, which is the highest string.
                val row = (offset.y / rowHeight).toInt().coerceIn(0, strings - 1)
                val string = strings - 1 - row
                onPositionTap(tuning[string] + fret.coerceIn(0, GUITAR_FRETS))
            }
        },
    ) {
        val fretWidth = size.width / (GUITAR_FRETS + NUT_COLUMN)
        val nutX = fretWidth * NUT_COLUMN
        val rowHeight = size.height / strings
        val boardTop = rowHeight * 0.5f
        val boardBottom = size.height - rowHeight * 0.5f

        fun stringY(string: Int) = boardBottom - string * rowHeight
        fun fretX(fret: Int) = if (fret == 0) nutX * 0.5f else nutX + (fret - 0.5f) * fretWidth

        // Board: darker than the card so the strings read as lit metal over it.
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(AppColors.surfaceDeep, AppColors.background)),
            topLeft = Offset(nutX, boardTop - rowHeight * 0.35f),
            size = Size(size.width - nutX, boardBottom - boardTop + rowHeight * 0.7f),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )

        // Position inlays, drawn under the strings.
        for (fret in INLAY_FRETS) {
            val x = fretX(fret)
            val r = rowHeight * 0.16f
            if (fret == 12) {
                drawCircle(AppColors.textDim.copy(alpha = 0.35f), r, Offset(x, stringY(strings - 2) + rowHeight * 0.5f))
                drawCircle(AppColors.textDim.copy(alpha = 0.35f), r, Offset(x, stringY(1) + rowHeight * 0.5f))
            } else {
                drawCircle(AppColors.textDim.copy(alpha = 0.35f), r, Offset(x, (boardTop + boardBottom) / 2f))
            }
        }

        // Nut and fret wires.
        drawLine(
            color = AppColors.keyNatural,
            start = Offset(nutX, boardTop - rowHeight * 0.35f),
            end = Offset(nutX, boardBottom + rowHeight * 0.35f),
            strokeWidth = 3.dp.toPx(),
        )
        for (fret in 1..GUITAR_FRETS) {
            val x = nutX + fret * fretWidth
            drawLine(
                color = AppColors.textDim.copy(alpha = 0.7f),
                start = Offset(x, boardTop - rowHeight * 0.35f),
                end = Offset(x, boardBottom + rowHeight * 0.35f),
                strokeWidth = 1.2.dp.toPx(),
            )
        }

        // Strings: heavier at the bottom, as wound strings are.
        for (string in 0 until strings) {
            val y = stringY(string)
            val thickness = (2.4f - string * 0.3f).dp.toPx()
            drawLine(
                color = AppColors.keyNaturalShade,
                start = Offset(nutX * 0.15f, y),
                end = Offset(size.width, y),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
        }

        // Fret numbers under the board, on the marked frets only, so the neck can be read
        // without counting.
        for (fret in INLAY_FRETS) {
            val label = textMeasurer.measure(
                fret.toString(),
                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold),
            )
            drawText(
                textLayoutResult = label,
                color = AppColors.textDim,
                topLeft = Offset(fretX(fret) - label.size.width / 2f, size.height - label.size.height),
            )
        }

        // Markers: a filled disc on a fretted note, a ring on an open string. The glow first,
        // so it sits under the disc it belongs to.
        val markerR = rowHeight * 0.36f
        for (string in 0 until strings) {
            for (fret in 0..GUITAR_FRETS) {
                val midi = tuning[string] + fret
                val centre = Offset(fretX(fret), stringY(string))
                if (glow != null && glow.first == midi && glow.second > 0f) {
                    val r = markerR * 2.6f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.55f * glow.second), accent.copy(alpha = 0f)),
                            center = centre, radius = r,
                        ),
                        radius = r, center = centre,
                    )
                }
                if (midi !in litMidi) continue
                if (fret == 0) {
                    drawCircle(accent, markerR * 0.85f, centre, style = Stroke(width = 2.dp.toPx()))
                } else {
                    drawCircle(
                        brush = Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.78f)),
                            startY = centre.y - markerR, endY = centre.y + markerR,
                        ),
                        radius = markerR, center = centre,
                    )
                }
                val text = labels[midi] ?: continue
                val label = textMeasurer.measure(
                    text,
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black),
                )
                drawText(
                    textLayoutResult = label,
                    color = if (fret == 0) accent else Color.White,
                    topLeft = Offset(centre.x - label.size.width / 2f, centre.y - label.size.height / 2f),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1838, widthDp = 600)
@Composable
private fun GuitarFretboardPreview() {
    // Open C major: x 3 2 0 1 0.
    GuitarFretboard(
        litMidi = setOf(48, 52, 55, 60, 64),
        accent = AppColors.mediumPurple,
        onPositionTap = {},
        labels = mapOf(48 to "R", 52 to "3", 55 to "5", 60 to "R", 64 to "3"),
        modifier = Modifier.padding(12.dp).fillMaxWidth().height(150.dp),
    )
}
