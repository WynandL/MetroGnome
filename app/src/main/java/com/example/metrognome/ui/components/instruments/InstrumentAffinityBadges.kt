package com.example.metrognome.ui.components.instruments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shows ONLY the instruments the given [soundType] is ideal for, all lit gold. Unlike
 * [InstrumentAffinityRow] (which shows the full set with the matches highlighted), this is a
 * compact "great for" badge cluster for contexts where the sound cannot be auditioned in the
 * row, for example the premium paywall dialog of an unowned sound. See [InstrumentAffinity].
 */
@Composable
fun InstrumentAffinityBadges(
    soundType: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
) {
    val ideal = remember(soundType) { InstrumentAffinity.instrumentsFor(soundType) }
    if (ideal.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Filter through the enum so the badges keep the canonical left-to-right order.
        Instrument.entries.filter { it in ideal }.forEach { instrument ->
            InstrumentIcon(instrument = instrument, active = true, size = iconSize)
        }
    }
}
