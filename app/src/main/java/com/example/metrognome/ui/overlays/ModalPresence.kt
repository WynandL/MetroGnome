package com.example.metrognome.ui.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Whether any dialog or full-screen overlay is on screen, app-wide.
 *
 * Something that must wait for a calm moment (the ad-free trial's cards) needs to know that
 * nothing else is up, on any tab, without knowing every screen's local `showX` flags. Every
 * modal registers here instead, through two shared doors that cover all of them: [AppWindowDialog]
 * (the one way to open a Compose `Dialog` window) and [modalScrim] (every full-screen overlay
 * drawn in the screen's own tree). A new modal that uses either is counted with no extra code.
 * Never call `androidx.compose.ui.window.Dialog` directly.
 */
object ModalPresence {
    private val open = mutableIntStateOf(0)

    /** True while at least one modal is showing. Compose state: reading it subscribes. */
    val anyOpen: Boolean get() = open.intValue > 0

    /** Counts the calling composable as a modal for as long as it is composed. */
    @Composable
    fun Register() {
        DisposableEffect(Unit) {
            open.intValue += 1
            onDispose { open.intValue -= 1 }
        }
    }
}

/**
 * `androidx.compose.ui.window.Dialog`, counted by [ModalPresence]. Same signature; use it for
 * every dialog window in the app.
 */
@Composable
fun AppWindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    ModalPresence.Register()
    Dialog(onDismissRequest = onDismissRequest, properties = properties, content = content)
}
