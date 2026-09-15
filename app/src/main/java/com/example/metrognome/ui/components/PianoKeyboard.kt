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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.abs

/** Pitch classes of the seven naturals, left to right. */
private val WHITE_PITCH_CLASSES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/**
 * Which white keys have a black key on their right shoulder, and its pitch class.
 * The gap after E and after B is what gives a keyboard its two-then-three grouping.
 */
private val BLACK_KEYS = listOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)

/** Black key width as a fraction of a white key's. */
private const val BLACK_KEY_WIDTH = 0.62f

/** Black key length as a fraction of the keyboard's height. */
private const val BLACK_KEY_HEIGHT = 0.62f

/**
 * A drawn piano keyboard of whole octaves, starting on the C at [lowestMidi].
 *
 * Drawn with a real piano's value contrast: light naturals, near-black sharps. That is not
 * decoration, it is the whole reason the keyboard needs no letters. A dark-on-dark version
 * was built first (for the drone) and read as an unexplained row of blocks, because the
 * groups of two and three sharps only orient the eye when the sharps are clearly the dark
 * ones. The one concession on a keyboard wider than an octave is [octaveLabels]: a small
 * "C3" at the foot of each C, which is how a real keyboard is found by feel as well.
 *
 * Keys in [litMidi] are painted in [accent]. [glow] is a short-lived halo on one key
 * (MIDI to alpha), for the moment a note is captured from the microphone.
 *
 * Hit testing checks the sharps first, since they overlap the naturals and are drawn on
 * top; a natural is only hit where no sharp covers it, exactly as the instrument behaves.
 * The drone's one-octave picker passes pitch classes as MIDI 0..11.
 */
@Composable
fun PianoKeyboard(
    octaves: Int,
    lowestMidi: Int,
    litMidi: Set<Int>,
    accent: Color,
    onKeyTap: (midi: Int) -> Unit,
    modifier: Modifier = Modifier,
    octaveLabels: Boolean = false,
    glow: Pair<Int, Float>? = null,
) {
    require(lowestMidi % 12 == 0) { "the keyboard must start on a C (was $lowestMidi)" }
    val naturalCount = WHITE_PITCH_CLASSES.size * octaves
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier.pointerInput(octaves, lowestMidi) {
            detectTapGestures { offset ->
                val naturalWidth = size.width / naturalCount
                val sharpWidth = naturalWidth * BLACK_KEY_WIDTH
                val sharpHeight = size.height * BLACK_KEY_HEIGHT

                if (offset.y <= sharpHeight) {
                    for (octave in 0 until octaves) {
                        val sharp = BLACK_KEYS.firstOrNull { (naturalIndex, _) ->
                            val shoulder = (octave * 7 + naturalIndex + 1) * naturalWidth
                            abs(offset.x - shoulder) <= sharpWidth / 2f
                        }
                        if (sharp != null) {
                            onKeyTap(lowestMidi + octave * 12 + sharp.second)
                            return@detectTapGestures
                        }
                    }
                }
                val index = (offset.x / naturalWidth).toInt().coerceIn(0, naturalCount - 1)
                onKeyTap(lowestMidi + (index / 7) * 12 + WHITE_PITCH_CLASSES[index % 7])
            }
        },
    ) {
        val naturalWidth = size.width / naturalCount
        // The gap is the card showing through, so the keys separate without a drawn border.
        val gap = 1.5.dp.toPx()
        val radius = CornerRadius(3.dp.toPx())

        /** Top-lit down the face, like every other raised surface in the app. */
        fun faceBrush(selected: Boolean, lit: Color, shade: Color) = Brush.verticalGradient(
            colors = if (selected) listOf(accent, accent.copy(alpha = 0.78f)) else listOf(lit, shade),
        )

        for (index in 0 until naturalCount) {
            val midi = lowestMidi + (index / 7) * 12 + WHITE_PITCH_CLASSES[index % 7]
            val left = index * naturalWidth + gap / 2f
            drawRoundRect(
                brush = faceBrush(midi in litMidi, AppColors.keyNatural, AppColors.keyNaturalShade),
                topLeft = Offset(left, 0f),
                size = Size(naturalWidth - gap, size.height),
                cornerRadius = radius,
            )
            if (octaveLabels && index % 7 == 0) {
                val label = textMeasurer.measure(
                    NoteNames.labelOf(midi),
                    style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                )
                drawText(
                    textLayoutResult = label,
                    color = if (midi in litMidi) Color.White else AppColors.keySharp.copy(alpha = 0.55f),
                    topLeft = Offset(
                        left + (naturalWidth - gap - label.size.width) / 2f,
                        size.height - label.size.height - 3.dp.toPx(),
                    ),
                )
            }
        }

        val sharpWidth = naturalWidth * BLACK_KEY_WIDTH
        val sharpHeight = size.height * BLACK_KEY_HEIGHT
        for (octave in 0 until octaves) {
            BLACK_KEYS.forEach { (naturalIndex, pitch) ->
                val midi = lowestMidi + octave * 12 + pitch
                drawRoundRect(
                    brush = faceBrush(midi in litMidi, AppColors.keySharp, AppColors.keySharpShade),
                    topLeft = Offset((octave * 7 + naturalIndex + 1) * naturalWidth - sharpWidth / 2f, 0f),
                    size = Size(sharpWidth, sharpHeight),
                    cornerRadius = radius,
                )
            }
        }

        // Capture halo: a soft radial bloom centred on the key, fading out under the caller's
        // animation. Drawn last so it sits over both kinds of key.
        if (glow != null && glow.second > 0f) {
            val (midi, alpha) = glow
            val octave = (midi - lowestMidi) / 12
            val pitch = ((midi - lowestMidi) % 12 + 12) % 12
            if (octave in 0 until octaves) {
                val sharpIndex = BLACK_KEYS.indexOfFirst { it.second == pitch }
                val centre = if (sharpIndex >= 0) {
                    Offset((octave * 7 + BLACK_KEYS[sharpIndex].first + 1) * naturalWidth, sharpHeight * 0.5f)
                } else {
                    val index = octave * 7 + WHITE_PITCH_CLASSES.indexOf(pitch)
                    Offset((index + 0.5f) * naturalWidth, size.height * 0.75f)
                }
                val r = naturalWidth * 1.6f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.55f * alpha), accent.copy(alpha = 0f)),
                        center = centre,
                        radius = r,
                    ),
                    radius = r,
                    center = centre,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1838, widthDp = 360)
@Composable
private fun PianoKeyboardPreview() {
    PianoKeyboard(
        octaves = 2,
        lowestMidi = 48,
        litMidi = setOf(48, 52, 55, 59),
        accent = AppColors.mediumPurple,
        onKeyTap = {},
        octaveLabels = true,
        modifier = Modifier.padding(12.dp).fillMaxWidth().height(96.dp),
    )
}
