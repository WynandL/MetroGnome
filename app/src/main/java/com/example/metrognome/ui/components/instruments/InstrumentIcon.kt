package com.example.metrognome.ui.components.instruments

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * A single instrument glyph. Glows [AppColors.gold] when [active] (the current sound suits
 * this instrument) and fades to a muted tint otherwise. The tint transition is animated so
 * the affinity row reacts smoothly as the user taps through sounds.
 */
@Composable
fun InstrumentIcon(
    instrument: Instrument,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
) {
    val color by animateColorAsState(
        targetValue = if (active) AppColors.gold else AppColors.textDim,
        animationSpec = tween(durationMillis = 220),
        label = "instrumentTint",
    )
    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Canvas(
        modifier = modifier
            .then(clickableModifier)
            .size(size)
            .semantics { contentDescription = instrument.label },
    ) {
        drawInstrumentGlyph(instrument, color)
    }
}
