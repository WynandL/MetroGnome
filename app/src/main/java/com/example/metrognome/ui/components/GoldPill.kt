package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * Gold-bordered pill for informational labels and tappable chips.
 *
 * Text-only (no [leadingIcon], no [onClick]): compact 10sp label — used for e.g. "A4 = 442 Hz".
 * With [leadingIcon]: slightly larger 12sp Medium weight — used for action chips.
 * With [onClick]: wraps in a clickable [Surface].
 */
@Composable
fun GoldPill(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val hasIcon = leadingIcon != null
    val hPad = if (hasIcon) 12.dp else 8.dp
    val vPad = if (hasIcon) 6.dp else 4.dp
    val fontSize = if (hasIcon) 12.sp else 10.sp
    val fontWeight = if (hasIcon) FontWeight.Medium else FontWeight.Bold
    val borderAlpha = if (hasIcon) 0.55f else 0.40f

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon, contentDescription = null,
                    tint = AppColors.gold, modifier = Modifier.size(13.dp),
                )
            }
            Text(text, color = AppColors.gold, fontSize = fontSize, fontWeight = fontWeight)
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50.dp),
            color = AppColors.gold.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, AppColors.gold.copy(alpha = borderAlpha)),
            modifier = modifier,
            content = rowContent,
        )
    } else {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = AppColors.gold.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, AppColors.gold.copy(alpha = borderAlpha)),
            modifier = modifier,
            content = rowContent,
        )
    }
}
