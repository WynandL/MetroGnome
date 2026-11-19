package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import com.example.metrognome.ui.theme.AppRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    leadingIcon: AppGlyph? = null,
    onClick: (() -> Unit)? = null,
) {
    val hasIcon = leadingIcon != null
    val hPad = if (hasIcon) 12.dp else 8.dp
    val vPad = if (hasIcon) 6.dp else 4.dp
    val fontSize = if (hasIcon) 12.sp else 10.sp
    val fontWeight = if (hasIcon) FontWeight.Medium else FontWeight.Bold
    val border = BorderStroke(1.dp, if (hasIcon) AppColors.goldBorderStrong else AppColors.goldBorder)

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                AppIcon(
                    leadingIcon, contentDescription = null,
                    tint = AppColors.gold, modifier = Modifier.size(13.dp),
                )
            }
            // One line always: a pill given less width than its label (the Home pills beside
            // the BPM on a narrow phone with large fonts) shortens the label, never wraps it.
            Text(
                text, color = AppColors.gold, fontSize = fontSize, fontWeight = fontWeight,
                // No weight: a Row already measures its last child in the width left, so this
                // ellipsizes in a capped pill; a weight would collapse the label to nothing in
                // an unbounded parent (the Chords page's horizontally scrolling alternatives).
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = AppRadius.Pill,
            color = AppColors.goldTint,
            border = border,
            modifier = modifier,
            content = rowContent,
        )
    } else {
        Surface(
            shape = AppRadius.Pill,
            color = AppColors.goldTint,
            border = border,
            modifier = modifier,
            content = rowContent,
        )
    }
}
