package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.audio.tuner.TunerSessionSnapshot
import com.example.metrognome.ui.theme.AppColors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

private enum class CardStep { RATING, REASON, THANKING }

/**
 * "Was that reading accurate?" on the shared [FeedbackCard], after a tuner session.
 *
 * State machine:
 *   RATING  — spot on / not quite — auto-dismisses in 20 s if ignored
 *   REASON  — follows a thumbs-down; reason chips or auto-dismiss in 10 s with null reason
 *   THANKING— brief confirmation; always auto-dismisses in 1.8 s
 *
 * Unlike the poll, this one keeps its timeouts: it is about one specific reading,
 * and a reading the user has walked away from is stale, not merely unanswered.
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
    FeedbackCard(visible = visible && snapshot != null, modifier = modifier) {
        snapshot ?: return@FeedbackCard

        var step by remember(snapshot) { mutableStateOf(CardStep.RATING) }

        LaunchedEffect(step) {
            when (step) {
                CardStep.RATING   -> { delay(20.seconds); onDismiss() }
                CardStep.REASON   -> {
                    delay(10.seconds)
                    onThumbsDown(snapshot, null)
                    step = CardStep.THANKING
                }
                CardStep.THANKING -> { delay(1_800.milliseconds); onDismiss() }
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "feedback_step",
        ) { current ->
            when (current) {
                CardStep.RATING -> FeedbackQuestion(
                    eyebrow       = "${snapshot.noteName} · ${snapshot.detectedHz.toInt()} Hz",
                    question      = "Was that reading accurate?",
                    subtext       = "One tap. It helps make the tuner better.",
                    negativeLabel = "Not quite",
                    positiveLabel = "Spot on",
                    onNegative    = { step = CardStep.REASON },
                    onPositive    = { onThumbsUp(snapshot); step = CardStep.THANKING },
                    onDismiss     = onDismiss,
                )

                CardStep.REASON -> ReasonStep(
                    onReason = { reason ->
                        onThumbsDown(snapshot, reason)
                        step = CardStep.THANKING
                    },
                    onDismiss = onDismiss,
                )

                CardStep.THANKING -> FeedbackThanks("Got it, thank you")
            }
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
private fun ReasonStep(
    onReason: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "What was off?",
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        // Chips wrap to a second line on narrow screens
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.surfaceVariant)
            .border(1.dp, AppColors.mediumPurple, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
