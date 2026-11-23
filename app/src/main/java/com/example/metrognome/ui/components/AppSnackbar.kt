package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.AppRadius
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * The one way the app tells the user something in passing. It replaced every `Toast`
 * (2026-11-23): a Toast is drawn by the system, outside the app's look, and on recent Android
 * versions it is clipped to two lines with the app's icon beside it.
 *
 * Call [show] from anywhere (a click handler, a ViewModel collector, `MainActivity`). One
 * [AppSnackbarHost] in `MetroGnomeApp` shows it above the tab's pinned banner ad (never over
 * it) or, when there is no banner, above the navigation bar. A new message replaces the one
 * showing rather than queueing behind it, the way a Toast did, so a run of taps on a toggle
 * reads its last state at once.
 *
 * A dialog window sits above the main window, so a message posted while one is open would
 * land underneath its scrim. A dialog that posts messages adds its own [AppSnackbarHost];
 * the most recently composed host takes each message and the others stay quiet.
 */
object AppSnackbar {
    internal class Message(val text: String, val long: Boolean, val mainWindow: Boolean)

    internal val messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)

    /** Hosts in composition order; the last one is on top and shows the messages. */
    internal val hosts = mutableStateListOf<Any>()

    /** Window y of the top of the pinned banner ad on the current tab, or NaN if none is up. */
    internal var bannerTopPx by mutableFloatStateOf(Float.NaN)

    /** Window y of the bottom of the tab content (the top of the navigation bar). */
    internal var contentBottomPx by mutableFloatStateOf(Float.NaN)

    /**
     * Shows [text] briefly (about four seconds), or for longer with [long]. Safe on any thread.
     * [mainWindow] sends it past any open dialog's host, for a message posted as the dialog
     * closes (it would otherwise go down with the dialog).
     */
    fun show(text: String, long: Boolean = false, mainWindow: Boolean = false) {
        messages.tryEmit(Message(text, long, mainWindow))
    }
}

/** Reports the tab content's bottom edge to [AppSnackbar], for `MetroGnomeApp`'s content box. */
fun Modifier.snackbarContentBounds(): Modifier = onGloballyPositioned {
    AppSnackbar.contentBottomPx = it.positionInWindow().y + it.size.height
}

/** Reports a pinned banner ad's top edge to [AppSnackbar] while it is composed. */
@Composable
internal fun Modifier.snackbarBannerBounds(): Modifier {
    DisposableEffect(Unit) { onDispose { AppSnackbar.bannerTopPx = Float.NaN } }
    return onGloballyPositioned { AppSnackbar.bannerTopPx = it.positionInWindow().y }
}

/**
 * Shows [AppSnackbar] messages. Fill the window (or the dialog) with it: it lays its card at
 * the bottom and takes no touches outside the card. [avoidBars] marks the main window's one
 * host (in `MetroGnomeApp`), which lifts the card clear of the banner ad and navigation bar;
 * a dialog's host passes false.
 */
@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier, avoidBars: Boolean = true) {
    val token = remember { Any() }
    DisposableEffect(token) {
        AppSnackbar.hosts.add(token)
        onDispose { AppSnackbar.hosts.remove(token) }
    }
    val state = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        AppSnackbar.messages.collectLatest { message ->
            val mine = if (message.mainWindow) avoidBars else AppSnackbar.hosts.lastOrNull() === token
            if (!mine) return@collectLatest
            // collectLatest cancels the previous showSnackbar, which takes its card down.
            state.showSnackbar(
                message = message.text,
                duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }

    var hostBottomPx by remember { mutableStateOf(Float.NaN) }
    val liftPx = if (!avoidBars || hostBottomPx.isNaN()) 0f else {
        val floor = listOf(AppSnackbar.bannerTopPx, AppSnackbar.contentBottomPx)
            .filter { !it.isNaN() }
            .minOrNull() ?: hostBottomPx
        (hostBottomPx - floor).coerceAtLeast(0f)
    }
    val lift = with(LocalDensity.current) { liftPx.toDp() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostBottomPx = it.positionInWindow().y + it.size.height },
        contentAlignment = Alignment.BottomCenter,
    ) {
        SnackbarHost(
            hostState = state,
            modifier = Modifier.padding(bottom = lift + 12.dp, start = 16.dp, end = 16.dp),
        ) { data ->
            AppSnackbarCard(data.visuals.message)
        }
    }
}

@Composable
private fun AppSnackbarCard(text: String) {
    Box(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .background(AppColors.card, AppRadius.Inset)
            .border(1.dp, AppColors.goldBorder, AppRadius.Inset)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            color = AppColors.textPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
