package com.example.metrognome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppRadius
import com.example.metrognome.ui.theme.AppColors

/**
 * The app's one text field: an inset on the card (the same surface, radius and hairline as
 * [AppInset]) whose rim turns gold when it has focus and amber when [error] is set. The label
 * is a [SectionLabel] above it, not a floating one.
 *
 * It replaced a Material `OutlinedTextField` restyled colour by colour (Save Preset), which kept
 * Material's own geometry: a 56 dp field, a label that cuts the border, a 4 dp radius family.
 *
 * The line under the field is always there, whether or not it holds an [error], so the dialog
 * below it never moves when a message appears.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    trailing: AppGlyph? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val rim by animateColorAsState(
        targetValue = when {
            error != null -> AppColors.warning
            focused -> AppColors.goldBorderStrong
            else -> AppColors.surfaceVariant
        },
        animationSpec = tween(150), label = "field_rim",
    )
    val style = TextStyle(color = AppColors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)

    Column(modifier = modifier) {
        if (label != null) {
            SectionLabel(label)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(AppColors.gold),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ActionButtonHeight)
                        .background(AppColors.surface, AppRadius.Inset)
                        .border(1.dp, rim, AppRadius.Inset)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = style, color = AppColors.textMuted, maxLines = 1)
                        }
                        field()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(10.dp))
                        AppIcon(
                            trailing, contentDescription = null,
                            tint = if (value.isEmpty()) AppColors.textMuted else AppColors.gold,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
        )
        // Always present, so a message appearing does not push the rest of the dialog down.
        Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
            if (error != null) {
                Text(error, color = AppColors.warning, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1)
            }
        }
    }
}
