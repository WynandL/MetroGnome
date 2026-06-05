package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val shape = RoundedCornerShape(20.dp)
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
                Surface(
                    color = AppColors.surfaceActive,
                    shape = shape,
                    border = if (isActive) BorderStroke(1.5.dp, AppColors.gold) else null,
                    modifier = Modifier.height(30.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(shape)
                            .pointerInput(index, preset.bpm) {
                                detectTapGestures(
                                    onTap = { onPresetTap(preset) },
                                    onLongPress = {
                                        haptics.fire(HapticPattern.LONG_PRESS)
                                        onPresetLongPress(index, preset)
                                    },
                                )
                            }
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isActive) AppColors.gold else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
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
