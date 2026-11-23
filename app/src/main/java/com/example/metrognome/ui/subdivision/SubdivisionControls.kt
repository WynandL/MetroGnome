package com.example.metrognome.ui.subdivision

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.audio.metronome.subdivision.Subdivision
import com.example.metrognome.ui.components.AppFilterChip
import com.example.metrognome.ui.components.PinnedLines
import com.example.metrognome.ui.components.PinnedSlot
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.components.SectionLabel
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.AppRadius
import com.example.metrognome.viewmodel.SubdivisionHold
import com.example.metrognome.viewmodel.SubdivisionStatus

/*
 * The two ways to set the subdivision, from one file so they say the same thing:
 * - SubdivisionKey: Home's key (beside mute, screen-on, preset, practice, trainer); a tap
 *   steps Off, 2, 3, 4, Off. Its mark is the beat split into dots, a large one for the beat
 *   and a small one per sub-tick, so it needs no word and no note character.
 * - SubdivisionRow: the meter stage's row, the four choices as chips with a line that says
 *   what is sounding, or why it is resting.
 * Both read SubdivisionStatus (MetronomeViewModel), which mirrors the engine's own rule.
 */

/** One line for what the subdivision is doing; the stage's caption and Home's snackbar. */
fun SubdivisionStatus.caption(): String = when {
    chosen == Subdivision.OFF -> "One click per beat"
    hold == SubdivisionHold.SESSION -> "Off during your session, back after"
    hold == SubdivisionHold.TEMPO ->
        "Pauses above $maxBpm BPM on ${com.example.metrognome.billing.clickSoundFor(voice).displayName}"
    else -> "${chosen.clicks} clicks per beat"
}

@Composable
fun SubdivisionKey(
    status: SubdivisionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = status.chosen != Subdivision.OFF
    RaisedControl(
        onClick = onClick,
        shape = AppRadius.Inset,
        tint = if (on) AppColors.primaryPurple else AppColors.surfaceVariant,
        modifier = modifier.semantics {
            contentDescription = "Subdivision"
            stateDescription = if (on) status.chosen.label else "Off"
        },
    ) {
        SubdivisionMark(
            clicks = status.chosen.clicks,
            color = when {
                !on -> AppColors.textMuted
                status.sounding -> AppColors.gold
                else -> AppColors.textDim   // held: the setting shows, quietly
            },
            modifier = Modifier.align(Alignment.Center).size(width = 30.dp, height = 20.dp),
        )
    }
}

/** The beat as one large dot and [clicks] - 1 small ones after it, centred. */
@Composable
private fun SubdivisionMark(clicks: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val big = 3.2.dp.toPx()
        val small = 2.dp.toPx()
        val space = 3.dp.toPx()
        // Centres: the beat, then each sub-tick one small dot and a space further on.
        val step = 2 * small + space
        val first = big + space + small
        val width = if (clicks > 1) first + (clicks - 2) * step + small + big else 2 * big
        val left = (size.width - width) / 2f
        val y = size.height / 2f
        drawCircle(color, big, Offset(left + big, y))
        for (i in 0 until clicks - 1) drawCircle(color, small, Offset(left + big + first + i * step, y))
    }
}

@Composable
fun SubdivisionRow(
    status: SubdivisionStatus,
    onChoose: (Subdivision) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("SUBDIVISION", maxLines = 1)
            Spacer(Modifier.width(10.dp))
            for (s in Subdivision.entries) {
                AppFilterChip(
                    selected = s == status.chosen,
                    onClick = { onChoose(s) },
                    label = if (s == Subdivision.OFF) "Off" else "${s.clicks}",
                    endPadding = if (s == Subdivision.FOUR) 0.dp else 6.dp,
                    modifier = Modifier.semantics {
                        contentDescription = if (s == Subdivision.OFF) "No subdivision" else "${s.clicks} clicks per beat"
                    },
                )
            }
        }
        PinnedSlot(lineHeight = 16.sp, alignment = Alignment.Center) {
            androidx.compose.material3.Text(
                status.caption(),
                color = if (status.sounding) AppColors.goldSoft else AppColors.textMuted,
                fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = PinnedLines,
            )
        }
    }
}
