package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.layout.Row
import com.example.metrognome.ui.components.AppTextField
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.SectionLabel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    // Starts empty: a blank name saves an unnamed preset, which the pill (and the live preview
    // below) draws as the Gnome tab's note mark beside the BPM.
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() &&
            existingNames.any { it.equals(trimmed, ignoreCase = true) }

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

        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "PRESET NAME",
            placeholder = "Optional, e.g. Verse or Warm-up",
            error = if (isDuplicate) "A preset with this name already exists" else null,
            trailing = AppIcons.Edit,
            // Done saves: whether the platform resizes or pans this dialog for the keyboard
            // varies, and a pan can leave the Save button under it.
            onImeAction = { onSave(name) },
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Live chip preview ───────────────────────────────────────
        SectionLabel("HOW IT'LL LOOK", color = AppColors.goldSoft)
        Spacer(Modifier.height(8.dp))
        PresetPill(name = trimmed, bpm = bpm, active = true)

        Spacer(Modifier.height(18.dp))

        PrimaryButton("SAVE PRESET", { onSave(name) }, Modifier.fillMaxWidth(), icon = AppIcons.Heart)
    }
}
