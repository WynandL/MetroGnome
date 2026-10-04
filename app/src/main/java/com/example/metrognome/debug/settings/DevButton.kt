package com.example.metrognome.debug.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.theme.AppColors

/**
 * What a developer button does, told by colour. Every kind is the same raised key as the
 * app's primary button ([RaisedControl]), differing only in tint and label colour:
 *  - [ACTION]:      run or show something (blue)
 *  - [PREVIEW]:     preview app UI/art (purple, the app's own key colour)
 *  - [DESTRUCTIVE]: wipes or resets state (red)
 *  - [HIGHLIGHT]:   a toggle that is on, or the main verb of a tool (gold)
 *  - [NEUTRAL]:     a toggle that is off, or a minor control (grey)
 */
enum class DevButtonKind(val tint: Color, val label: Color) {
    ACTION(AppColors.devBlueFill, AppColors.devBlue),
    PREVIEW(AppColors.primaryPurple, Color.White),
    DESTRUCTIVE(AppColors.devRedFill, AppColors.devRed),
    HIGHLIGHT(AppColors.devGoldFill, AppColors.gold),
    NEUTRAL(AppColors.surfaceVariant, AppColors.textSecondary),
}

/** The one developer-tools button. Pass `Modifier.weight(1f)` / `fillMaxWidth()` as needed. */
@Composable
fun DevButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: DevButtonKind = DevButtonKind.ACTION,
    enabled: Boolean = true,
    maxLines: Int = 2,
    /** Small variant for steppers and inline controls. */
    compact: Boolean = false,
) {
    RaisedControl(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(12.dp),
        tint = kind.tint,
        modifier = modifier.heightIn(min = if (compact) 36.dp else 40.dp).alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(
            label,
            color = kind.label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 8.dp),
        )
    }
}
