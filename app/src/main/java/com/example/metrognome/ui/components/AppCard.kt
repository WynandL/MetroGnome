package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * Geometry of the app's one card. Every page's cards (Rhythm, Tuner, Chords, Home, Settings)
 * are this shape, this surface and this rim, so a card on one tab is indistinguishable in
 * construction from a card on another. Do not draw a `Surface(color = ..., shape = ...)`
 * for a card; use [AppCard].
 */
object AppCardDefaults {
    val Shape = RoundedCornerShape(20.dp)
    /** Every dialog, popup and overlay card. */
    val DialogShape = RoundedCornerShape(24.dp)
    val ContentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)
    val Border = BorderStroke(1.dp, AppColors.goldBorder)
}

/**
 * The app's card: [AppColors.card] fill, 20 dp corners, a hairline of gold at 35%.
 * Pass [onClick] for a tappable card. [contentPadding] defaults to the standard 18/16;
 * pass `PaddingValues(0.dp)` when the content sizes itself edge to edge.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = AppCardDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = AppColors.card,
            shape = AppCardDefaults.Shape,
            border = AppCardDefaults.Border,
            modifier = modifier.fillMaxWidth(),
            content = inner,
        )
    } else {
        Surface(
            color = AppColors.card,
            shape = AppCardDefaults.Shape,
            border = AppCardDefaults.Border,
            modifier = modifier.fillMaxWidth(),
            content = inner,
        )
    }
}

/**
 * Card header, tier one of the two caps tiers (12 sp, bold, 1 sp tracking, `textDim`), with an
 * optional right-aligned value (14 sp bold). The value says only what the controls below do
 * not; [valueColor] is gold only for something that matters right now.
 */
@Composable
fun CardHeader(
    text: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color = AppColors.textSecondary,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = AppColors.textDim,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        if (value != null) {
            Spacer(Modifier.weight(1f))
            Text(
                value,
                color = valueColor,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * A control or readout sitting *inside* a card: a rounded inset on [AppColors.surface] with a
 * `surfaceVariant` hairline, or, when [highlighted], the gold wash and rim the Rhythm page
 * gives a played difficulty or a reached target. One recipe for chips, meters and rows.
 */
@Composable
fun AppInset(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit,
) {
    val color = if (highlighted) AppColors.goldTint else AppColors.surface
    val border = BorderStroke(1.dp, if (highlighted) AppColors.goldBorder else AppColors.surfaceVariant)
    if (onClick != null) {
        Surface(onClick = onClick, color = color, shape = shape, border = border, modifier = modifier, content = content)
    } else {
        Surface(color = color, shape = shape, border = border, modifier = modifier, content = content)
    }
}

/** Gap between stacked cards on every page. */
val CardGap: Dp = 12.dp

/** The hairline between rows inside a card or dialog. One colour, one weight, everywhere. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(modifier = modifier, color = AppColors.surfaceVariant)
}
