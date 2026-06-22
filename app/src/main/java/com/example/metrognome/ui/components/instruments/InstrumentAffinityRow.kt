package com.example.metrognome.ui.components.instruments

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * The instrument-affinity nudge row. Shows every [Instrument] evenly spaced left to right;
 * the ones the currently selected [soundType] is ideal for glow gold, the rest stay dim.
 * A soft, text-free hint of which sounds suit which instruments. See [InstrumentAffinity].
 */
@Composable
fun InstrumentAffinityRow(
    soundType: Int,
    modifier: Modifier = Modifier,
) {
    val ideal = remember(soundType) { InstrumentAffinity.instrumentsFor(soundType) }
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Instrument.entries.forEach { instrument ->
            InstrumentIcon(
                instrument = instrument,
                active = instrument in ideal,
                onClick = {
                    Toast.makeText(
                        context,
                        "Gold icons show which instruments best suit the metronome sound",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }
}
