package com.example.metrognome.ui.overlays

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
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
 * It also counts the overlay in [ModalPresence] for as long as it is shown.
 *
 * **A finger's jitter is never consumed.** A real finger sends small moves between down and
 * up (a high-rate touch panel always does). Compose's click detection cancels a tap when a
 * parent consumes one of its moves, so consuming every change here made the card's buttons
 * dead to a real finger while an adb tap (down and up, no move) still worked: the "Sweet!
 * does nothing" bug of v7.3. Moves within touch slop of where the pointer went down pass
 * through untouched; downs, ups and real drags are still swallowed.
 */
fun Modifier.modalScrim(): Modifier = composed {
    // Every full-screen overlay passes through here, so this is where it is counted as a
    // modal (ModalPresence), for anything that waits for a calm moment.
    ModalPresence.Register()
    pointerInput(Unit) {
        val starts = HashMap<PointerId, Offset>()
        awaitPointerEventScope {
            while (true) {
                val slop = viewConfiguration.touchSlop
                for (change in awaitPointerEvent().changes) {
                    if (change.changedToDownIgnoreConsumed()) starts[change.id] = change.position
                    val start = starts[change.id]
                    val jitter = change.pressed && change.previousPressed && start != null &&
                        (change.position - start).getDistance() <= slop
                    if (!jitter) change.consume()
                    if (!change.pressed) starts.remove(change.id)
                }
            }
        }
    }
}
