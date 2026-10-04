package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/** Every dialog and overlay action button is this tall with these corners. */
val ActionButtonHeight = 46.dp
val ActionButtonShape = RoundedCornerShape(14.dp)

/**
 * The three secondary action buttons of a dialog, so no dialog draws its own:
 *  - [GhostButton]: dismiss / cancel / the quieter of two choices (outline only).
 *  - [GoldButton]: the confirming choice when [PrimaryButton] (raised purple) is not the
 *    dialog's one big action: a gold wash and a gold rim, the Rhythm page's recipe.
 *  - [DangerButton]: a destructive confirm.
 * Pair two of them in a `Row` with `Modifier.weight(1f)` and a 10 dp spacer.
 */
@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    ActionButton(label, onClick, modifier, enabled, Color.Transparent,
        BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)), AppColors.textSecondary, FontWeight.Medium)

@Composable
fun GoldButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    ActionButton(label, onClick, modifier, enabled, AppColors.goldTint,
        BorderStroke(1.dp, AppColors.gold), AppColors.gold, FontWeight.Bold)

@Composable
fun DangerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    ActionButton(label, onClick, modifier, enabled, AppColors.stopRed.copy(alpha = 0.15f),
        BorderStroke(1.dp, AppColors.stopRedBorder), AppColors.stopRed, FontWeight.Bold)

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    fill: Color,
    border: BorderStroke,
    textColor: Color,
    weight: FontWeight,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ActionButtonShape,
        color = fill,
        border = border,
        modifier = modifier.height(ActionButtonHeight).alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = textColor, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = weight)
        }
    }
}
