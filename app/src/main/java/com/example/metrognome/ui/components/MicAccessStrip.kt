package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The one way a screen asks for microphone access: a slim tappable strip, the same
 * shape and height as the Chords tab's mic strip (which shows this in place of its
 * level meter until the grant arrives). A crossed-out mic in the warning colour, one
 * line saying what is needed and that tapping does it, and a chevron.
 *
 * Replaced the tuner's card-with-a-button (a sentence plus "Grant Microphone Access"),
 * which took three times the height to say the same thing and looked like a different
 * app's idea of a prompt beside the Chords strip. [onClick] should launch the runtime
 * request, or open App Settings when [permanentlyDenied], since a denied-forever grant
 * cannot be asked for again in-app; the wording changes to match.
 *
 * `MicCheckOverlay` keeps its own dialog copy (it is mid-flow, with retry and cancel),
 * and `MicTimingNudge` its gold pill (one state among several in that slot).
 */
@Composable
fun MicAccessStrip(
    permanentlyDenied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = if (permanentlyDenied) "Microphone blocked, tap to open App Settings"
        else "Microphone access needed, tap to grant"

    Surface(
        onClick = onClick,
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = message },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Filled.MicOff,
                    contentDescription = null,
                    tint = AppColors.warning,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                color = AppColors.textMuted,
                fontSize = 11.sp, lineHeight = 15.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.textDim,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun MicAccessStripPreview() {
    MicAccessStrip(permanentlyDenied = false, onClick = {}, modifier = Modifier.padding(16.dp))
}
