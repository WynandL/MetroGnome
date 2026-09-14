package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The app's one "we are asking you something" card: a bottom sheet that slides up
 * over a screen, with a question at reading size and a ghost / gold button pair.
 *
 * Used by [PollBanner] (in-app polls) and [TunerFeedbackCard] (was that reading
 * accurate?). Both started as separate one-line strips with 18 dp thumb icons, and
 * the poll's data showed why that fails: a strip reads as a toast that will go away
 * on its own, so people let it. This card is deliberately bigger and bolder than the
 * app's usual restraint, the balance the dev chose between getting any feedback at
 * all and nagging. Anything new that asks the user a question should use this, not
 * draw its own strip, so the two stay identical.
 *
 * [FeedbackCard] is the container (slide, surface, accent bar, shimmer). Content is
 * whatever the caller's state machine needs; [FeedbackQuestion] and [FeedbackThanks]
 * are the two shared steps, and the tuner adds its own reason-chip step.
 */
@Composable
fun FeedbackCard(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically { it },
        exit    = slideOutVertically { it },
        modifier = modifier,
    ) {
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
            // Left accent bar. matchParentSize does not inflate the outer Box; the Box
            // sizes itself from the content, then this fills that height.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .width(3.dp)
                    .background(
                        AppColors.primaryPurple,
                        RoundedCornerShape(topStart = 18.dp, bottomEnd = 2.dp),
                    ),
            )

            // Slow diagonal shimmer sweep, bottom-left to top-right.
            val shimmerTransition = rememberInfiniteTransition(label = "feedbackShimmer")
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

            content()
        }
    }
}

/**
 * The question step: an eyebrow line, the question, an optional subtext, an X, and
 * the same ghost "no" / gold "yes" pair NotificationOptInDialog uses.
 */
@Composable
fun FeedbackQuestion(
    eyebrow: String,
    question: String,
    subtext: String?,
    negativeLabel: String,
    positiveLabel: String,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    text       = eyebrow,
                    color      = AppColors.textAccent,
                    fontSize   = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text       = question,
                    color      = Color.White,
                    fontSize   = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtext != null) {
                    Text(
                        text       = subtext,
                        color      = AppColors.textSecondary,
                        fontSize   = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = AppColors.textDim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(end = 10.dp)) {
            FeedbackButton(
                label = negativeLabel,
                icon = Icons.Filled.ThumbDown,
                positive = false,
                onClick = onNegative,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            FeedbackButton(
                label = positiveLabel,
                icon = Icons.Filled.ThumbUp,
                positive = true,
                onClick = onPositive,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    positive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (positive) AppColors.gold else AppColors.textSecondary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (positive) AppColors.gold.copy(alpha = 0.10f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (positive) AppColors.gold.copy(alpha = 0.75f) else AppColors.textDim.copy(alpha = 0.5f),
        ),
        modifier = modifier.height(42.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = tint,
                fontSize = 14.sp,
                fontWeight = if (positive) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/** The closing step, shown briefly after an answer. */
@Composable
fun FeedbackThanks(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AppColors.textSecondary, fontSize = 14.sp)
    }
}
