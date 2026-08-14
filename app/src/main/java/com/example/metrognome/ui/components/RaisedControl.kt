package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * A control that reads as a raised physical key rather than a flat coloured rectangle.
 *
 * Three layers do the work, and they are deliberately subtle — the effect should register as
 * "this is a button" before the viewer notices any shading at all:
 *
 *  1. A vertical gradient across the face: lifted at the top, sunk at the bottom. This is the
 *     top-lit convention the rest of the app already uses (see GnomeCanvas), applied to UI.
 *  2. A hairline rim, bright along the top edge and fading out by the middle, then a dark
 *     under-edge at the very bottom. A real bevel catches light on its upper lip and shades
 *     on its lower one; one uniform outline around the whole shape would read as a drawn
 *     border instead, which is the same trap the character's edge lighting kept falling into.
 *  3. Nothing else. No blur, no glow, no drop shadow — on a background this dark a shadow is
 *     invisible anyway, and reaching for one is what turns "raised" into "glassy".
 *
 * Both shading colours are DERIVED from [tint] rather than fixed, so one component serves the
 * whole row whatever each key's base colour is — neutral steppers, purple TAP, and a play key
 * that animates between purple and red without the highlight ever drifting out of agreement
 * with it.
 */
@Composable
fun RaisedControl(
    onClick: () -> Unit,
    shape: Shape,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    0.00f to lerp(tint, Color.White, FACE_TOP_LIFT),
                    0.52f to tint,
                    1.00f to lerp(tint, Color.Black, FACE_BOTTOM_SINK),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0.00f to Color.White.copy(alpha = RIM_TOP_ALPHA),
                    0.45f to Color.Transparent,
                    0.80f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = RIM_BOTTOM_ALPHA),
                ),
                shape = shape,
            ),
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

private const val FACE_TOP_LIFT = 0.13f      // how far the top of the face lifts toward white
private const val FACE_BOTTOM_SINK = 0.20f   // ...and how far the bottom sinks toward black
private const val RIM_TOP_ALPHA = 0.20f      // light caught on the upper lip of the bevel
private const val RIM_BOTTOM_ALPHA = 0.22f   // shade on the lower lip

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun RaisedControlPreview() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RaisedControl(
            onClick = {},
            shape = RoundedCornerShape(12.dp),
            tint = AppColors.surfaceVariant,
            modifier = Modifier.height(44.dp).size(width = 56.dp, height = 44.dp),
        ) {
            Text("-5", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        RaisedControl(
            onClick = {},
            shape = CircleShape,
            tint = AppColors.primaryPurple,
            modifier = Modifier.size(64.dp),
        ) {
            Text("▶", color = Color.White, fontSize = 20.sp)
        }
        RaisedControl(
            onClick = {},
            shape = RoundedCornerShape(12.dp),
            tint = AppColors.primaryPurple,
            modifier = Modifier.size(width = 56.dp, height = 44.dp),
        ) {
            Text("TAP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
