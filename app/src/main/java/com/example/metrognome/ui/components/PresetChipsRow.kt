package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import com.example.metrognome.presets.BpmPreset
import com.example.metrognome.ui.theme.AppColors

/**
 * Horizontal scrollable row of saved BPM presets.
 *
 * Tap a pill → apply its BPM. Long-press → request delete confirmation.
 * The active pill (preset.bpm == currentBpm) gets a gold border + bold gold label.
 * A "Tip: long-press to delete" hint shows underneath until [showLongPressHint] is false.
 */
@Composable
fun PresetChipsRow(
    presets: List<BpmPreset>,
    currentBpm: Int,
    showLongPressHint: Boolean,
    onPresetTap: (preset: BpmPreset) -> Unit,
    onPresetLongPress: (index: Int, preset: BpmPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEachIndexed { index, preset ->
                val isActive = preset.bpm == currentBpm
                PresetPill(
                    label = preset.name,
                    active = isActive,
                    interaction = Modifier
                            // combinedClickable rather than raw tap detection, so a screen
                            // reader gets the tap, a named delete action and which preset is
                            // active; raw gestures exposed none of them (UI audit U03).
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                // The app's own long-press haptic fires below instead.
                                hapticFeedbackEnabled = false,
                                onLongClickLabel = "Delete preset",
                                onLongClick = {
                                    haptics.fire(HapticPattern.LONG_PRESS)
                                    onPresetLongPress(index, preset)
                                },
                                onClick = { onPresetTap(preset) },
                            )
                            .semantics { selected = isActive },
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        FadingHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showLongPressHint && presets.isNotEmpty()) {
            Text(
                text = "Tip: long-press a preset to delete",
                color = AppColors.textDim.copy(alpha = 0.85f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

/**
 * One preset pill's face. The Home row draws its presets with it and Save Preset's "how it'll
 * look" preview draws the active one with it, so the preview cannot drift from the real thing
 * (it had: 13 sp text, its own padding and no fixed height; UI audit U19). [interaction] goes
 * on the clipped inner box, where the row puts its tap, long press and semantics.
 */
@Composable
fun PresetPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    interaction: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = if (active) AppColors.goldTint else AppColors.surface,
        shape = shape,
        border = BorderStroke(1.dp, if (active) AppColors.goldBorder else AppColors.surfaceVariant),
        modifier = modifier.height(30.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(shape)
                .then(interaction)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = label,
                color = if (active) AppColors.gold else Color.White,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
