package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.theory.Meter
import com.example.metrognome.theory.MeterTheory
import com.example.metrognome.ui.theme.AppColors

/**
 * Free-but-guided time-signature control: quick-pick presets, a custom top/bottom stepper,
 * a live classification readout, and a per-beat accent editor.
 *
 * It owns no state. The caller supplies the current meter plus accents and reacts to
 * [onMeterChange] (top, bottom) and [onToggleAccent] (0-based pulse). Changing the meter is
 * expected to reset accents to the natural grouping; toggling edits a single accent. All
 * music-theory rules live in [MeterTheory], so this file is purely presentation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeSignaturePicker(
    top: Int,
    bottom: Int,
    accentBeats: Set<Int>,
    onMeterChange: (top: Int, bottom: Int) -> Unit,
    onToggleAccent: (beatIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meter = Meter(top, bottom)

    Column(modifier = modifier.fillMaxWidth()) {

        // One cluster: the custom fraction on the left, a thin divider, then the everyday-meter
        // presets as a two-row carousel that scrolls horizontally only if they overflow. Keeping
        // them on the same row reads as "two ways to pick one thing" rather than two settings.
        // The denominator stepper is restricted to sensible note values, the single guard rail
        // that keeps any combination meaningful without ever blocking the user.
        Row(verticalAlignment = Alignment.CenterVertically) {
            FractionStepper(top = top, bottom = bottom, onMeterChange = onMeterChange)
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(64.dp)
                    .background(AppColors.surfaceVariant)
            )
            Spacer(Modifier.width(14.dp))
            PresetCarousel(
                top = top,
                bottom = bottom,
                onMeterChange = onMeterChange,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // (The classification label, e.g. "Compound triple", is rendered by the caller next to
        // the "Time Signature" heading, so it is intentionally not repeated here.) The plain-
        // English feel readout ("Felt in 2+2+3") sits here instead, right above the accent cells
        // it explains - most useful on odd meters, where the grouping is not obvious from the
        // fraction alone.
        Text(
            MeterTheory.description(meter),
            color = AppColors.textMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))

        // Reset sits beside the heading, not in the chip row, so it never shifts the chips. The
        // row is a fixed height so the button toggling in/out causes no layout jump. It appears
        // only once the pattern differs from the meter's natural accents; re-applying the current
        // meter (setMeter) is what restores the default, so reset just calls onMeterChange.
        val isCustom = accentBeats != MeterTheory.defaultAccents(meter)
        Row(
            modifier = Modifier.height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Accents", color = AppColors.textPrimary, fontWeight = FontWeight.Medium)
            if (isCustom) {
                Spacer(Modifier.width(10.dp))
                ResetAccentsButton(onClick = { onMeterChange(top, bottom) })
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow {
            for (i in 0 until top.coerceAtLeast(1)) {
                AccentCell(
                    number = i + 1,
                    accented = i in accentBeats,
                    onClick = { onToggleAccent(i) },
                )
            }
        }
    }
}

/**
 * The preset meters laid out in two rows that scroll together horizontally. With the fraction
 * stepper taking the left, the chips rarely fit a phone width, so the carousel keeps them on a
 * tidy two-row band instead of pushing the cluster taller. Splits the list so the first (more
 * common) meters sit on the top row.
 */
@Composable
private fun PresetCarousel(
    top: Int,
    bottom: Int,
    onMeterChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = MeterTheory.COMMON_METERS
    val split = (presets.size + 1) / 2
    val rows = listOf(presets.take(split), presets.drop(split))
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Column {
                rows.forEachIndexed { index, rowPresets ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Row {
                        rowPresets.forEach { preset ->
                            AppFilterChip(
                                selected = preset.top == top && preset.bottom == bottom,
                                onClick = { onMeterChange(preset.top, preset.bottom) },
                                label = preset.display,
                            )
                        }
                    }
                }
            }
        }
        // Thin fading bar that advertises horizontal scrollability. It self-hides when the row
        // does not overflow, and sits in the slack left by the taller fraction stepper, so it
        // neither squeezes the chips nor shifts the content below the cluster.
        Spacer(Modifier.height(3.dp))
        FadingHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FractionStepper(top: Int, bottom: Int, onMeterChange: (Int, Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepperLine(
            value = top,
            onMinus = { onMeterChange((top - 1).coerceAtLeast(MeterTheory.TOP_RANGE.first), bottom) },
            onPlus = { onMeterChange((top + 1).coerceAtMost(MeterTheory.TOP_RANGE.last), bottom) },
        )
        Box(
            Modifier
                .padding(vertical = 3.dp)
                .width(40.dp)
                .height(2.dp)
                .background(AppColors.textMuted)
        )
        StepperLine(
            value = bottom,
            onMinus = { onMeterChange(top, stepDenominator(bottom, -1)) },
            onPlus = { onMeterChange(top, stepDenominator(bottom, +1)) },
        )
    }
}

@Composable
private fun StepperLine(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("-", onMinus)
        Text(
            "$value",
            color = AppColors.gold,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(AppColors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = AppColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun AccentCell(number: Int, accented: Boolean, onClick: () -> Unit) {
    // Sized to read like the preset FilterChips: 32dp tall, ~14sp label. Accented beats use gold
    // (matching the gold beat dots and gnome flash) rather than the chips' selection purple.
    Box(
        modifier = Modifier
            .padding(end = 6.dp, bottom = 6.dp)
            .heightIn(min = 32.dp)
            .widthIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (accented) AppColors.gold else AppColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$number",
            color = if (accented) Color.Black else AppColors.textSecondary,
            fontWeight = if (accented) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ResetAccentsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Reset", color = AppColors.textMuted, fontSize = 13.sp)
    }
}

/** Step the denominator through [MeterTheory.SENSIBLE_DENOMINATORS], clamped at the ends. */
private fun stepDenominator(current: Int, direction: Int): Int {
    val options = MeterTheory.SENSIBLE_DENOMINATORS
    val index = options.indexOf(current).let { if (it < 0) options.indexOf(4) else it }
    return options[(index + direction).coerceIn(0, options.lastIndex)]
}
