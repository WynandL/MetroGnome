package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * Centred label-over-value pair used in gauges, stat readouts, and score displays.
 *
 * Default sizing matches the tuner's ReadoutChip style (9sp label / 14sp value).
 * Pass custom sizes and colors to adapt to rhythm game scoreboards or any other context.
 */
@Composable
fun LabelValueBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelColor: Color = AppColors.textSubtle,
    valueColor: Color = AppColors.textSecondary,
    labelFontSize: TextUnit = 9.sp,
    valueFontSize: TextUnit = 14.sp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            label, color = labelColor, fontSize = labelFontSize,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
        Text(value, color = valueColor, fontSize = valueFontSize, fontWeight = FontWeight.Bold)
    }
}
