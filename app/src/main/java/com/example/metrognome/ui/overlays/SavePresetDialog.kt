package com.example.metrognome.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.components.DialogCloseButton
import com.example.metrognome.ui.theme.AppColors

/**
 * Dialog for saving a new BPM preset.
 *
 * Shows an inline amber warning (non-blocking) when [existingNames] already contains the
 * entered name. The user can still save — they may intentionally want the same label.
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
                .widthIn(min = 280.dp, max = 380.dp),
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

                Surface(
                    onClick = { onSave(name) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, AppColors.gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Save Preset",
                            color = AppColors.gold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Cancel",
                    color = AppColors.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                )
            }
        }
    }
}
