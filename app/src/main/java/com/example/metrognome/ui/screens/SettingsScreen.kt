package com.example.metrognome.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.BuildConfig
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.UnlockCelebrationOverlay
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.ui.components.metro_items.UnlockCondition
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.MetronomeViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MetronomeViewModel) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val accentBeat by vm.accentBeat.collectAsStateWithLifecycle()
    val soundType by vm.soundType.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    val cheatModeEnabled by vm.cheatModeEnabled.collectAsStateWithLifecycle()

    val unlockQueue = remember { mutableStateListOf<MetroItemEntry>() }
    var previewIndex by remember { mutableIntStateOf(0) }
    var showUnlockRules by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.newlyUnlocked.collect { entry -> unlockQueue.add(entry) } }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            SettingsSectionTitle("Tempo & Rhythm")

            // BPM slider
            SettingsSliderRow(
                label = "Tempo",
                value = bpm.toFloat(),
                valueText = "$bpm BPM · ${tempoLabel(bpm)}",
                range = 20f..300f,
                onValueChange = { vm.setBpm(it.roundToInt()) }
            )

            // Time signature chips
            SettingsRow(label = "Time Signature") {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(2, 3, 4, 6, 7).forEach { sig ->
                        FilterChip(
                            selected = sig == timeSig,
                            onClick = { vm.setTimeSig(sig) },
                            label = { Text("$sig/4") },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Sound")

            // Sound type chips
            SettingsRow(label = "Click Sound") {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Classic", "Hi-Hat", "Wood", "Warm").forEachIndexed { index, name ->
                        FilterChip(
                            selected = index == soundType,
                            onClick = { vm.setSoundType(index) },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
                        )
                    }
                }
            }

            // Volume slider
            SettingsSliderRow(
                label = "Click Volume",
                value = volume,
                valueText = "${(volume * 100).roundToInt()}%",
                range = 0f..1f,
                onValueChange = { vm.setVolume(it) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Visual")

            SettingsRow(label = "Accent Beat") {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = accentBeat == 0,
                        onClick = { vm.setAccentBeat(0) },
                        label = { Text("None") },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.primaryPurple,
                            selectedLabelColor = Color.White,
                            containerColor = AppColors.surface,
                            labelColor = AppColors.textSecondary
                        )
                    )
                    for (beat in 1..timeSig) {
                        FilterChip(
                            selected = beat == accentBeat,
                            onClick = { vm.setAccentBeat(beat) },
                            label = { Text("$beat") },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.primaryPurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppColors.surface,
                                labelColor = AppColors.textSecondary
                            )
                        )
                    }
                }
            }

            SettingsSwitchRow(
                label = "Flash on Beat",
                description = "Golden screen flash on each beat",
                checked = flashOnBeat,
                onChecked = { vm.setFlashOnBeat(it) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = AppColors.surfaceVariant)
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("About")

            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = AppColors.textMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Build: ${if (BuildConfig.DEBUG) "Debug" else "Release"}",
                color = if (BuildConfig.DEBUG) AppColors.gold else AppColors.textMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            if (BuildConfig.DEBUG) {
            // ── DEV ONLY — hidden automatically in release builds ─────────────────
            OutlinedButton(
                onClick = { vm.toggleCheatMode() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (cheatModeEnabled) AppColors.gold else AppColors.devGrey
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (cheatModeEnabled) AppColors.gold else AppColors.devDarkBorder
                )
            ) {
                Text(
                    if (cheatModeEnabled) "DEV: All Items ON" else "DEV: All Items OFF",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { vm.previewUnlockCelebration(previewIndex) },
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.mediumPurple),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.deepPurple)
                ) {
                    Text("DEV: Preview Popup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { previewIndex = (previewIndex + 1) % METRO_ITEM_REGISTRY.size },
                    modifier = Modifier.padding(start = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devGrey),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.surfaceVariant)
                ) {
                    Text(
                        "#${previewIndex + 1}/${METRO_ITEM_REGISTRY.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = { showUnlockRules = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devBlueBorder)
            ) {
                Text("DEV: Show Unlock Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick = { vm.resetAllProgress() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.devRedBorder)
            ) {
                Text("DEV: Reset All Progress", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            } // end DEBUG block

            Spacer(Modifier.height(8.dp))
        }

        // AdMob banner
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }

    if (showUnlockRules) {
        AlertDialog(
            onDismissRequest = { showUnlockRules = false },
            containerColor = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor = AppColors.textSecondary,
            title = { Text("Unlock Rules", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    METRO_ITEM_REGISTRY.sortedBy { entry ->
                        when (val c = entry.condition) {
                            is UnlockCondition.MetronomeSeconds     -> c.required.toDouble()
                            is UnlockCondition.RhythmGamesCompleted -> c.required * 300.0
                            is UnlockCondition.DaysSinceFirstLaunch -> c.required * 86_400.0
                            UnlockCondition.Always                  -> -1.0
                        }
                    }.forEach { entry ->
                        Text(
                            text = entry.item.displayName,
                            color = AppColors.textAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                        Text(
                            text = entry.item.unlockCondition,
                            fontSize = 12.sp,
                            color = AppColors.textSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUnlockRules = false }) {
                    Text("OK", color = AppColors.textAccent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    unlockQueue.firstOrNull()?.let { entry ->
        UnlockCelebrationOverlay(
            entry = entry,
            onDismiss = { vm.markCelebrated(entry.item.id); unlockQueue.removeAt(0) },
        )
    }
    } // close outer Box
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = AppColors.gold,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingsSliderRow(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = AppColors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                valueText,
                color = AppColors.textAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.gold,
                activeTrackColor = AppColors.mediumPurple,
                inactiveTrackColor = AppColors.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(label, color = AppColors.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.textPrimary, fontWeight = FontWeight.Medium)
            Text(description, color = AppColors.textMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.gold,
                checkedTrackColor = AppColors.primaryPurple,
                uncheckedThumbColor = AppColors.controlInactive,
                uncheckedTrackColor = AppColors.surfaceVariant
            )
        )
    }
}
