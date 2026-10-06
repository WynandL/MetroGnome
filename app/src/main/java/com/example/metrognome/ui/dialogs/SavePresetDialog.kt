package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.components.PresetPill
import com.example.metrognome.ui.components.PrimaryButton
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
    // Starts empty: a blank name saves as "♩ <bpm>" (BpmPresetsManager), shown live in the preview
    // below. Pre-filling "♩ 120" put the note glyph in the field, where the text font draws it as a dot.
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() &&
            existingNames.any { it.equals(trimmed, ignoreCase = true) }
    // Mirrors BpmPresetsManager.savePreset's fallback when the name is blank.
    val previewLabel = trimmed.ifEmpty { "♩ $bpm" }

    // Scrollable: with the keyboard up, the dialog's window shrinks to what is left above it,
    // and the fixed column used to push the Save button off the bottom (UI audit U13).
    AppDialog(onDismiss = onDismiss, maxWidth = 380.dp, scrollable = true) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            DialogCloseButton(onClick = onDismiss)
        }

        Spacer(Modifier.height(2.dp))

        DialogTitle("Save Preset")

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
            // Done saves: whether the platform resizes or pans this dialog for the keyboard
            // varies, and a pan can leave the Save button under it.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(name) }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            label = { Text("Preset name") },
            placeholder = { Text("Optional, e.g. Verse or Warm-up", fontSize = 13.sp) },
            trailingIcon = {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = if (name.isEmpty()) AppColors.textMuted else AppColors.gold,
                    modifier = Modifier.size(18.dp),
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppColors.background,
                unfocusedContainerColor = AppColors.background,
                errorContainerColor = AppColors.background,
                focusedBorderColor = if (isDuplicate) AppColors.warning else AppColors.gold,
                unfocusedBorderColor = if (isDuplicate) AppColors.warning.copy(alpha = 0.7f) else AppColors.textDim,
                focusedLabelColor = AppColors.gold,
                unfocusedLabelColor = AppColors.textMuted,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = AppColors.textMuted,
                unfocusedPlaceholderColor = AppColors.textMuted,
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
        PresetPill(label = previewLabel, active = true)

        Spacer(Modifier.height(18.dp))

        PrimaryButton("SAVE PRESET", { onSave(name) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Favorite)
    }
}
