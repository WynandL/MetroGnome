package com.example.metrognome.ads

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

private val MESSAGES = listOf(
    "Metro needs a quick coffee break ☕",
    "Metro is tuning up... 🎵",
    "Metro's adjusting his hat 🎩",
    "A short intermission",
    "The gnome takes a bow... 🎶",
    "Metro's polishing his baton",
    "Stay in tempo - back shortly 🎵",
    "Metro needs a breather ☕",
)

/**
 * Bus for the pre-ad break banner. [AdManager] posts here just before showing
 * an interstitial; the UI collects [messages] and displays a transient pill.
 * Messages rotate through [MESSAGES] so the same line never appears twice in a row,
 * and the rotation starts at a random point each launch so the first message of a
 * session is not always the same one (the counter is in-memory, so it resets on restart).
 */
object AdBreakQueue {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val index = AtomicInteger(MESSAGES.indices.random())

    fun post() {
        val i = index.getAndIncrement() % MESSAGES.size
        _messages.tryEmit(MESSAGES[i])
    }
}
