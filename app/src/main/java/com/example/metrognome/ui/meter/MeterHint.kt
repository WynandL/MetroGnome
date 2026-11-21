package com.example.metrognome.ui.meter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.AppRadius

private val NOTCH_W = 14.dp
private val NOTCH_H = 7.dp

/**
 * The meter strip's one-time hint: a small card hanging from the beats, its notch pointing up
 * at them. Home shows it once per launch, only while the player is idle on a stopped
 * metronome, until it is acknowledged for good ("Got it", or opening the stage from it or
 * from the beats). Anything else the player does puts it away for the rest of the launch.
 *
 * Opaque card colour with the gold hairline, not a gold wash: it sits over Metro's scene,
 * and the words have to read over his hat.
 */
@Composable
fun MeterHintCard(
    visible: Boolean,
    onOpen: () -> Unit,
    onGotIt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 3 },
        exit = fadeOut() + slideOutVertically { -it / 3 },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The notch: the card's own fill and rim, drawn to overlap the card's top edge.
            Canvas(Modifier.size(NOTCH_W, NOTCH_H).offset(y = 1.dp)) {
                val p = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width / 2f, 0f)
                    lineTo(size.width, size.height)
                }
                drawPath(Path().apply { addPath(p); close() }, AppColors.card)
                drawPath(p, AppColors.goldBorderStrong, style = Stroke(width = 1.dp.toPx()))
            }
            Surface(
                onClick = onOpen,
                color = AppColors.card,
                shape = AppRadius.Inset,
                border = BorderStroke(1.dp, AppColors.goldBorderStrong),
                modifier = Modifier
                    .widthIn(max = 236.dp)
                    .semantics { contentDescription = "Tap the beats to change the time signature" },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 14.dp, end = 4.dp),
                ) {
                    Text(
                        text = "Tap to change the time signature",
                        color = AppColors.textPrimary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f, fill = false).padding(vertical = 9.dp),
                    )
                    Surface(
                        onClick = onGotIt,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        shape = AppRadius.Small,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text(
                            text = "Got it",
                            color = AppColors.gold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
