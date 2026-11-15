package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An [AppFilterChip] carrying the gold star that marks a paid feature.
 *
 * The star means "premium", not "locked", so it stays on after the purchase. That is
 * deliberate: it tells the owner which of their options they paid for, and it stops the row
 * rearranging itself the moment money changes hands.
 *
 * Selecting a chip the user does not own is the caller's business, not this component's.
 * Every premium chip in the app reports its tap the same way, and the screen decides
 * whether that means "select it" or "open the purchase dialog".
 */
@Composable
fun PremiumChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    endPadding: Dp = 6.dp,
) {
    AppFilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        endPadding = endPadding,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label)
            Spacer(Modifier.width(6.dp))
            StarGlyph(9.dp)
        }
    }
}
