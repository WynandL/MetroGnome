package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.metrognome.poll.PollConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private enum class PollStep { RATING, THANKING }

/**
 * One-question poll on the shared [FeedbackCard].
 *
 * [onResponse] fires with "up", "down" or "dismissed". The caller handles
 * PollManager.recordAnswered + PollReporter.submit and then calls [onDismiss]
 * to remove the card from its parent.
 *
 * It never times out. A poll the user has not touched is hidden by the caller
 * (metronome started, tab left) and nothing is recorded; PollManager counts the
 * show instead. The first version was a one-line strip with a 25 s auto-dismiss,
 * and three of its four responses were the timeout.
 */
@Composable
fun PollBanner(
    visible: Boolean,
    poll: PollConfig,
    onResponse: (response: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedbackCard(visible = visible, modifier = modifier) {
        var step by remember { mutableStateOf(PollStep.RATING) }

        LaunchedEffect(step) {
            if (step == PollStep.THANKING) {
                delay(1_800.milliseconds)
                onDismiss()
            }
        }

        AnimatedContent(
            targetState  = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "poll_step",
        ) { current ->
            when (current) {
                PollStep.RATING -> FeedbackQuestion(
                    eyebrow       = "Quick question",
                    question      = poll.question,
                    subtext       = poll.subtext,
                    negativeLabel = "No thanks",
                    positiveLabel = "Yes please",
                    onNegative    = { onResponse("down"); step = PollStep.THANKING },
                    onPositive    = { onResponse("up");   step = PollStep.THANKING },
                    onDismiss     = { onResponse("dismissed"); onDismiss() },
                )
                PollStep.THANKING -> FeedbackThanks("Thank you!")
            }
        }
    }
}
