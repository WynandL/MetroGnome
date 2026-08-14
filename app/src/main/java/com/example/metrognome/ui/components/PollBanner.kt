package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.poll.PollConfig
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private enum class PollStep { RATING, THANKING }

/**
 * One-shot poll banner that slides up from the bottom of a screen, matching
 * the visual style of TunerFeedbackCard.
 *
 * [onResponse] fires with "up", "down", "dismissed", or "auto_dismissed".
 * The caller handles PollManager.recordResponse + PollReporter.submit and then
 * calls [onDismiss] to remove the banner from its parent.
 *
 * Auto-dismisses after 25 s if the user never interacts, which counts as
 * "auto_dismissed" - PollManager re-asks this a day later rather than
 * retiring it outright, since a silent timeout likely means it went unseen.
 */
@Composable
fun PollBanner(
    visible: Boolean,
    poll: PollConfig,
    onResponse: (response: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically { it },
        exit    = slideOutVertically { it },
        modifier = modifier,
    ) {
        var step by remember { mutableStateOf(PollStep.RATING) }

        LaunchedEffect(step) {
            when (step) {
                PollStep.RATING -> {
                    delay(25.seconds)
                    onResponse("auto_dismissed")
                    onDismiss()
                }
                PollStep.THANKING -> {
                    delay(1_800.milliseconds)
                    onDismiss()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(AppColors.surfaceDeep)
                .border(
                    width = 1.dp,
                    color = AppColors.mediumPurple,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                ),
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .width(3.dp)
                    .background(
                        AppColors.primaryPurple,
                        RoundedCornerShape(topStart = 18.dp, bottomEnd = 2.dp),
                    ),
            )

            // Diagonal shimmer sweep
            val shimmerTransition = rememberInfiniteTransition(label = "pollShimmer")
            val shimmerPhase by shimmerTransition.animateFloat(
                initialValue  = 0f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
                label = "shimmerPhase",
            )
            Canvas(modifier = Modifier.matchParentSize()) {
                val bandW = size.width * 0.45f
                val x = shimmerPhase * (size.width + bandW) - bandW
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        start = Offset(x, size.height),
                        end   = Offset(x + bandW, 0f),
                    ),
                    size = size,
                )
            }

            AnimatedContent(
                targetState  = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "poll_step",
            ) { current ->
                when (current) {
                    PollStep.RATING -> PollRatingRow(
                        poll         = poll,
                        onThumbsUp   = { onResponse("up");   step = PollStep.THANKING },
                        onThumbsDown = { onResponse("down"); step = PollStep.THANKING },
                        onDismiss    = { onResponse("dismissed"); onDismiss() },
                    )
                    PollStep.THANKING -> PollThankingRow()
                }
            }
        }
    }
}

@Composable
private fun PollRatingRow(
    poll: PollConfig,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = poll.question,
                color      = Color.White,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text     = poll.subtext,
                color    = AppColors.textSecondary,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onThumbsDown, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Filled.ThumbDown, contentDescription = "No",      tint = AppColors.textAccent, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onThumbsUp,   modifier = Modifier.size(38.dp)) {
            Icon(Icons.Filled.ThumbUp,   contentDescription = "Yes",     tint = AppColors.gold,       modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDismiss,    modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close,     contentDescription = "Dismiss", tint = AppColors.textDim,    modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PollThankingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Thank you!", color = AppColors.textSecondary, fontSize = 13.sp)
    }
}
