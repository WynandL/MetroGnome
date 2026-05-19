package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import com.example.metrognome.audio.tuner.TunerSessionSnapshot
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay

// Distinct background — darker purple-black so the card clearly pops against the tuner screen.
private val CARD_BG     = Color(0xFF180F2E)
private val CARD_BORDER = Color(0xFF6040B0)
private val ACCENT_BAR  = AppColors.primaryPurple

private enum class CardStep { RATING, REASON, THANKING }

/**
 * Non-blocking feedback strip that slides up from the bottom of the tuner screen.
 *
 * State machine:
 *   RATING  — thumbs up / down — auto-dismisses in 20 s if ignored
 *   REASON  — follows a thumbs-down; reason chips or auto-dismiss in 10 s with null reason
 *   THANKING— brief confirmation; always auto-dismisses in 1.8 s
 *
 * Only one Firestore write per session: [onThumbsDown] is not called until the
 * reason is known (or the reason timeout fires), so both the rating and the
 * reason land in a single document.
 */
@Composable
fun TunerFeedbackCard(
    visible: Boolean,
    snapshot: TunerSessionSnapshot?,
    onThumbsUp: (TunerSessionSnapshot) -> Unit,
    onThumbsDown: (TunerSessionSnapshot, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && snapshot != null,
        enter = slideInVertically { it },
        exit  = slideOutVertically { it },
        modifier = modifier,
    ) {
        snapshot ?: return@AnimatedVisibility

        var step by remember(snapshot) { mutableStateOf(CardStep.RATING) }

        LaunchedEffect(step) {
            when (step) {
                CardStep.RATING   -> { delay(20_000); onDismiss() }
                CardStep.REASON   -> {
                    delay(10_000)
                    onThumbsDown(snapshot, null)
                    step = CardStep.THANKING
                }
                CardStep.THANKING -> { delay(1_800); onDismiss() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(CARD_BG)
                .border(
                    width = 1.dp,
                    color = CARD_BORDER,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                ),
        ) {
            // Left accent bar — matchParentSize does NOT inflate the outer Box;
            // the Box sizes itself from AnimatedContent, then this fills that height.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .width(3.dp)
                    .background(
                        ACCENT_BAR,
                        RoundedCornerShape(topStart = 18.dp, bottomEnd = 2.dp),
                    ),
            )

            // Slow diagonal shimmer sweep — subtle, non-intrusive
            val shimmerTransition = rememberInfiniteTransition(label = "cardShimmer")
            val shimmerPhase by shimmerTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
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
                        // Diagonal: band sweeps from bottom-left to top-right
                        start = Offset(x, size.height),
                        end   = Offset(x + bandW, 0f),
                    ),
                    size = size,
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "feedback_step",
            ) { current ->
                when (current) {
                    CardStep.RATING -> RatingRow(
                        snapshot = snapshot,
                        onThumbsUp = {
                            onThumbsUp(snapshot)
                            step = CardStep.THANKING
                        },
                        onThumbsDown = { step = CardStep.REASON },
                        onDismiss = onDismiss,
                    )

                    CardStep.REASON -> ReasonRow(
                        onReason = { reason ->
                            onThumbsDown(snapshot, reason)
                            step = CardStep.THANKING
                        },
                        onDismiss = onDismiss,
                    )

                    CardStep.THANKING -> ThankingRow()
                }
            }
        }
    }
}

// ── Rating ────────────────────────────────────────────────────────────────────

@Composable
private fun RatingRow(
    snapshot: TunerSessionSnapshot,
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
        Text(snapshot.noteName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text("${snapshot.detectedHz.toInt()} Hz", color = AppColors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("Accurate?", color = AppColors.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onThumbsDown, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Filled.ThumbDown, contentDescription = "No", tint = AppColors.textAccent, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onThumbsUp, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Filled.ThumbUp, contentDescription = "Yes", tint = AppColors.gold, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Reason ────────────────────────────────────────────────────────────────────

private val REASONS = listOf(
    "too_high"   to "Pitch high",
    "too_low"    to "Pitch low",
    "wrong_note" to "Wrong note",
    "noise"      to "Noisy room",
)

@Composable
private fun ReasonRow(
    onReason: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
    ) {
        // Label + dismiss on one line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("What was off?", color = AppColors.textMuted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
            }
        }
        // Chips wrap to a second line on narrow screens
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            REASONS.forEach { (key, label) ->
                ReasonChip(label = label, onClick = { onReason(key) })
            }
        }
    }
}

@Composable
private fun ReasonChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(AppColors.surfaceVariant)
            .border(1.dp, AppColors.mediumPurple, RoundedCornerShape(50.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Thanking ──────────────────────────────────────────────────────────────────

@Composable
private fun ThankingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Got it — thank you", color = AppColors.textSecondary, fontSize = 13.sp)
    }
}
