package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * Subtle ownership confirmation shown in place of a buy button.
 *
 * Deliberately low-key — a quiet nod, not a banner. The [message] should
 * feel personal and affirming rather than transactional ("you bought this").
 */
@Composable
fun OwnedBadge(message: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 4.dp),
    ) {
        Text(
            text = "✓  ",
            color = AppColors.gold.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )
        Text(
            text = message,
            color = AppColors.textMuted,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic,
        )
    }
}
