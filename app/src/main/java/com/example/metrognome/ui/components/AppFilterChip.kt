package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The app's selection chip. Selected is a raised purple key (the same lift/sink gradient and
 * bevel as the play button, via [raisedFace]); unselected is a quiet inset on the card.
 *
 * The [String] overload handles the common case. The [label] composable overload supports
 * chips with composite content (icon + text, badges, etc.).
 *
 * [endPadding] is the trailing gap used to space chips in a left-packed FlowRow; pass
 * `0.dp` when positioning chips yourself (e.g. flush-aligned in a Box) so the padding does
 * not offset the alignment.
 *
 * All screens that render selection chips use this instead of Material's `FilterChip`.
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    endPadding: Dp = 6.dp,
) = AppFilterChip(selected = selected, onClick = onClick, modifier = modifier, endPadding = endPadding, label = { Text(label) })

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    endPadding: Dp = 6.dp,
    label: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    // One structure for both states: same height, same 1 dp rim, same text style. Only the
    // face differs (raised purple vs. inset), so a row of chips can never differ in size.
    val face = if (selected) Modifier.raisedFace(shape, AppColors.primaryPurple)
        else Modifier
            .background(AppColors.surface, shape)
            .border(1.dp, AppColors.surfaceVariant, shape)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(end = endPadding)
            .height(CHIP_HEIGHT)
            .clip(shape)
            .then(face)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) Color.White else AppColors.textSecondary,
            LocalTextStyle provides TextStyle(
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
        ) { label() }
    }
}

/** Every chip in the app is this tall. */
val CHIP_HEIGHT = 34.dp

/** Vertical gap between wrapped rows of chips (the horizontal gap is each chip's `endPadding`). */
val CHIP_ROW_GAP = 8.dp
