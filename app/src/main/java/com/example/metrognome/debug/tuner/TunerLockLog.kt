package com.example.metrognome.debug.tuner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton ring buffer that records the lifecycle of each tuner *lock* so the
 * noise-robustness work can actually be measured rather than guessed at.
 *
 * One [LockSession] is appended every time a confirmed lock ends. The two fields that
 * prove the new machinery is doing its job are [LockSession.presenceSaves] (frames held
 * alive by the targeted presence probe when the global pitch was gone) and
 * [LockSession.hijackSurvivals] (frames a louder wrong pitch arrived but the lock held).
 *
 * Written from the [com.example.metrognome.audio.tuner.Tuner] capture thread, read from
 * the Compose main thread; [MutableStateFlow] is thread-safe by construction.
 *
 * Debug-only viewer. The recording itself is cheap and harmless; delete this file, its
 * call sites in Tuner, and the dev viewer to remove the feature entirely.
 */
object TunerLockLog {

    private const val MAX_SESSIONS = 60

    /**
     * One completed lock.
     *
     * @property noteLabel       note the lock was on, e.g. "A4"
     * @property holdMs          how long the lock was held, milliseconds
     * @property minClarity      lowest global-pitch clarity seen while locked (1 if never measured)
     * @property minPresence     lowest presence-probe value seen while locked
     * @property presenceSaves   frames kept alive purely by the presence probe
     * @property hijackSurvivals frames a far/wrong global pitch arrived yet the lock survived
     * @property maxLevel        loudest RMS observed during the hold
     * @property ambientLevel    learned room band at drop (QUIET/MODERATE/NOISY)
     * @property dropReason      state the lock fell to (QUIET/NOISE/UNSTABLE/STOP)
     */
    data class LockSession(
        val noteLabel: String,
        val holdMs: Long,
        val minClarity: Float,
        val minPresence: Float,
        val presenceSaves: Int,
        val hijackSurvivals: Int,
        val maxLevel: Float,
        val ambientLevel: String,
        val dropReason: String,
    )

    private val _sessions = MutableStateFlow<List<LockSession>>(emptyList())
    val sessions: StateFlow<List<LockSession>> = _sessions.asStateFlow()

    /** Begin a new capture session: clears any prior locks. */
    fun startSession() {
        _sessions.value = emptyList()
    }

    fun endSession() {}

    fun record(session: LockSession) {
        val current = _sessions.value
        _sessions.value =
            if (current.size >= MAX_SESSIONS) current.drop(1) + session else current + session
    }

    fun clear() {
        _sessions.value = emptyList()
    }
}
