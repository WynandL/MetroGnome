package com.example.metrognome.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.metrognome.viewmodel.MetronomeViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MetronomeViewModel) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val accentFirst by vm.accentFirst.collectAsStateWithLifecycle()
    val soundType by vm.soundType.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B1E))
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
                valueText = "$bpm BPM",
                range = 20f..300f,
                onValueChange = { vm.setBpm(it.roundToInt()) }
            )

            // Time signature chips
            SettingsRow(label = "Time Signature") {
                Row {
                    listOf(2, 3, 4, 6, 7).forEach { sig ->
                        FilterChip(
                            selected = sig == timeSig,
                            onClick = { vm.setTimeSig(sig) },
                            label = { Text("$sig/4") },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5B2D8A),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E1B3A),
                                labelColor = Color(0xFFCCCCEE)
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF2A2550))
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Sound")

            // Sound type chips
            SettingsRow(label = "Click Sound") {
                Row {
                    listOf("Classic", "Hi-Hat", "Wood").forEachIndexed { index, name ->
                        FilterChip(
                            selected = index == soundType,
                            onClick = { vm.setSoundType(index) },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5B2D8A),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E1B3A),
                                labelColor = Color(0xFFCCCCEE)
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
            HorizontalDivider(color = Color(0xFF2A2550))
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("Visual")

            SettingsSwitchRow(
                label = "Accent Beat 1",
                description = "Louder click on the first beat",
                checked = accentFirst,
                onChecked = { vm.setAccentFirst(it) }
            )

            SettingsSwitchRow(
                label = "Flash on Beat",
                description = "Golden screen flash on each beat",
                checked = flashOnBeat,
                onChecked = { vm.setFlashOnBeat(it) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFF2A2550))
            Spacer(Modifier.height(8.dp))

            SettingsSectionTitle("About")

            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = Color(0xFF8080AA),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Build: ${if (BuildConfig.DEBUG) "Debug" else "Release"}",
                color = if (BuildConfig.DEBUG) Color(0xFFFFD700) else Color(0xFF8080AA),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))
        }

        // AdMob banner
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFFFFD700),
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
            Text(label, color = Color(0xFFEEEEFF), modifier = Modifier.weight(1f))
            Text(
                valueText,
                color = Color(0xFFAB7DE0),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD700),
                activeTrackColor = Color(0xFF7B4DB0),
                inactiveTrackColor = Color(0xFF2A2550)
            )
        )
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(label, color = Color(0xFFEEEEFF), modifier = Modifier.padding(bottom = 8.dp))
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
            Text(label, color = Color(0xFFEEEEFF), fontWeight = FontWeight.Medium)
            Text(description, color = Color(0xFF8080AA), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFFD700),
                checkedTrackColor = Color(0xFF5B2D8A),
                uncheckedThumbColor = Color(0xFF666688),
                uncheckedTrackColor = Color(0xFF2A2550)
            )
        )
    }
}
