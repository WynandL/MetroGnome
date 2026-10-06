package com.example.metrognome.ui.overlays

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Makes a full-screen overlay's dimmed backdrop a real boundary: every touch that lands
 * outside the card is caught here instead of falling through to the screen behind it.
 *
 * The result and announcement overlays (Practice, Speed Trainer, unlocks, What's New) are
 * plain Boxes drawn over their screen, and a Box with only a background is not a touch
 * target, so a tap on the dim area used to reach the Play button, a preset or the TAP key
 * underneath (UI audit U09). The card's own buttons still work: they sit above this layer
 * and handle their touches first. Pair it with a `BackHandler` that calls the same dismiss
 * as the overlay's button, so Back dismisses the overlay instead of leaving the screen.
 */
fun Modifier.modalScrim(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}
