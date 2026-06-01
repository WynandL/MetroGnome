package com.example.metrognome.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.theme.AppColors

/**
 * Dialog for saving a new BPM preset.
 *
 * Springs in with a bounce. A live preview chip underneath the text field
 * shows exactly how the preset will appear in the chips row once saved —
 * updating as you type. Shows an inline amber warning (non-blocking) when
 * [existingNames] already contains the entered name; the user can still save,
 * since they may intentionally want the same label.
 */
@Composable
fun SavePresetDialog(
    bpm: Int,
    existingNames: Set<String>,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("♩ $bpm") }
    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() &&
            existingNames.any { it.equals(trimmed, ignoreCase = true) }
    // Mirrors BpmPresetsManager.savePreset's fallback when the name is blank.
    val previewLabel = trimmed.ifEmpty { "♩ $bpm" }

    val cardScale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(min = 280.dp, max = 380.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    DialogCloseButton(onClick = onDismiss)
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Save Preset",
                    color = AppColors.gold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "$bpm BPM",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = isDuplicate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDuplicate) AppColors.warning else AppColors.gold,
                        unfocusedBorderColor = if (isDuplicate) AppColors.warning.copy(alpha = 0.7f) else AppColors.surfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = AppColors.textSecondary,
                        cursorColor = AppColors.gold,
                        errorBorderColor = AppColors.warning,
                        errorCursorColor = AppColors.gold,
                    ),
                    supportingText = if (isDuplicate) {
                        { Text("A preset with this name already exists", color = AppColors.warning, fontSize = 11.sp) }
                    } else null,
                )

                Spacer(Modifier.height(16.dp))

                // ── Live chip preview ───────────────────────────────────────
                Text(
                    text = "HOW IT'LL LOOK",
                    color = AppColors.gold.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.height(8.dp))
                PresetChipPreview(label = previewLabel)

                Spacer(Modifier.height(18.dp))

                Surface(
                    onClick = { onSave(name) },
                    shape = RoundedCornerShape(14.dp),
                    color = AppColors.primaryPurple,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "SAVE PRESET",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A non-interactive copy of the saved-preset chip, styled as it looks when active. */
@Composable
private fun PresetChipPreview(label: String) {
    Surface(
        color = AppColors.surfaceActive,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, AppColors.gold),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = AppColors.gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
