package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * Themed [FilterChip] with the app's standard purple-on-dark color scheme.
 *
 * The [String] overload handles the common case. The [label] composable overload supports
 * chips with composite content (icon + text, badges, etc.).
 *
 * All screens that render selection chips should use this instead of inlining
 * [FilterChipDefaults.filterChipColors] everywhere.
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) = AppFilterChip(selected = selected, onClick = onClick, modifier = modifier, label = { Text(label) })

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.padding(end = 6.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AppColors.primaryPurple,
            selectedLabelColor = Color.White,
            containerColor = AppColors.surface,
            labelColor = AppColors.textSecondary,
        ),
    )
}
