package com.example.metrognome.adfree

import android.content.Context
import com.example.metrognome.analytics.AnalyticsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ad-free trial: holds [TrialState], saves every change at once, runs [AdFreeTrialPolicy]
 * and publishes what the app needs. Owned by MetronomeViewModel, like the one BillingManager,
 * which folds [active] into `isAdFree` and calls [tick] every 20 s and on every return to the
 * foreground; expiry therefore never waits for a particular event, and a trial that ran out
 * while the app was closed is seen the moment it opens.
 *
 * The facts it does not own come in as functions, read only when needed: whether Remove Ads
 * is owned, whether the daily-Gnotes ad-free days cover the user, how many distinct days the
 * app has been opened, and today's epoch day.
 */
class AdFreeTrialManager(
    context: Context,
    private val purchased: () -> Boolean,
    private val otherwiseAdFree: () -> Boolean,
    private val usageDays: () -> Int,
    private val today: () -> Long,
    private val time: () -> TimeReading = SystemTime(context)::read,
) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var state: TrialState = load()

    private val _active = MutableStateFlow(false)
    /** The trial's 24 hours are running. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _endsAtWallMs = MutableStateFlow<Long?>(null)
    /** When the running trial ends, on the wall clock; null when none is running. Display only. */
    val endsAtWallMs: StateFlow<Long?> = _endsAtWallMs.asStateFlow()

    private val _card = MutableStateFlow<TrialCard?>(null)
    /** The card the trial wants shown. The app-wide host shows it at a calm moment. */
    val card: StateFlow<TrialCard?> = _card.asStateFlow()

    /**
     * A trial card has been on screen in this process. The review prompt stays quiet for the
     * rest of the session: both become due on the third day, and two asks at once is one too many.
     */
    @Volatile var cardShownThisSession = false
        private set

    init {
        // Only the trial's own clock here; eligibility (usage days) is read on the first tick,
        // after the ViewModel has finished constructing what those functions read.
        advanceClock()
        publish(includeCard = false)
    }

    /** Brings everything up to date. Cheap; safe to call as often as wanted. */
    fun tick() {
        advanceClock()
        val cleaned = AdFreeTrialPolicy.dropStaleNotice(state, purchased(), otherwiseAdFree())
        if (cleaned != state) save(cleaned)
        publish(includeCard = true)
    }

    /** The host put [card] on screen. */
    fun onCardShown(card: TrialCard) {
        cardShownThisSession = true
        if (card == TrialCard.OFFER) AnalyticsTracker.logAdFreeTrialOffered(AdFreeTrialPolicy.offerNumber(state))
    }

    /** "Try it". */
    fun accept() {
        if (state.window != null || purchased()) { publish(includeCard = true); return }
        val offer = AdFreeTrialPolicy.offerNumber(state)
        save(AdFreeTrialPolicy.accept(state, time()))
        AnalyticsTracker.logAdFreeTrialAccepted(offer)
        publish(includeCard = true)
    }

    /** "Not now", or Back on the offer. */
    fun decline() {
        val offer = AdFreeTrialPolicy.offerNumber(state)
        save(AdFreeTrialPolicy.decline(state, today()))
        AnalyticsTracker.logAdFreeTrialDeclined(offer)
        publish(includeCard = true)
    }

    /** The "ads are back" card was dismissed, by either button or Back. */
    fun endNoticeSeen() {
        save(AdFreeTrialPolicy.noticeSeen(state))
        publish(includeCard = true)
    }

    private fun advanceClock() {
        val step = AdFreeTrialPolicy.advance(state, time(), purchased())
        if (step.state != state) save(step.state)
        step.endedNow?.let { AnalyticsTracker.logAdFreeTrialEnded(it.analyticsName) }
    }

    private fun publish(includeCard: Boolean) {
        val running = AdFreeTrialPolicy.active(state)
        _active.value = running
        _endsAtWallMs.value = if (running) state.window?.endsAtWallMs(time()) else null
        if (includeCard) {
            _card.value = AdFreeTrialPolicy.cardDue(state, usageDays(), today(), purchased(), otherwiseAdFree())
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────────────

    private fun load() = TrialState(
        declines = prefs.getInt(KEY_DECLINES, 0),
        lastDeclineDay = prefs.getLong(KEY_LAST_DECLINE_DAY, -1L),
        window = WindowPrefs.load(appContext, prefs, WINDOW),
        ended = prefs.getString(KEY_ENDED, null)?.let { name -> TrialEndReason.entries.firstOrNull { it.name == name } },
        endNoticePending = prefs.getBoolean(KEY_NOTICE_PENDING, false),
    )

    /**
     * Saves [s]. Anything the user did (Try it, Not now, a notice seen) and any change of
     * outcome is written with `commit`, so a process killed the next moment cannot forget it;
     * the clock's 20-second ratchet alone uses `apply`, off the main thread, since losing one
     * tick costs at most 20 s of measured time.
     */
    private fun save(s: TrialState) {
        val onlyTheClockMoved = s.window != null && state.window != null &&
            s.copy(window = null) == state.copy(window = null) &&
            s.window.startWallMs == state.window?.startWallMs &&
            s.window.durationMs == state.window?.durationMs
        val durable = !onlyTheClockMoved
        state = s
        val e = prefs.edit()
        e.putInt(KEY_DECLINES, s.declines)
        e.putLong(KEY_LAST_DECLINE_DAY, s.lastDeclineDay)
        WindowPrefs.save(appContext, e, WINDOW, s.window, durable)
        if (s.ended == null) e.remove(KEY_ENDED) else e.putString(KEY_ENDED, s.ended.name)
        e.putBoolean(KEY_NOTICE_PENDING, s.endNoticePending)
        if (durable) e.commit() else e.apply()
    }

    /** The offer on screen now is the re-offer, the last there will be. */
    fun isLastOffer(): Boolean = AdFreeTrialPolicy.offerNumber(state) >= AdFreeTrialPolicy.MAX_DECLINES

    // ── Dev tools (debug builds only; the buttons are behind BuildConfig.DEBUG) ──────

    /**
     * Wipes the trial and starts a real one of 2 minutes, through the same accept path as
     * "Try it": ads go, Settings counts down, and at the end the real "ads are back" card
     * comes at the next calm moment. Only its end is logged.
     */
    fun debugRunShortTrial() {
        save(AdFreeTrialPolicy.accept(TrialState(), time(), 2 * 60_000L))
        tick()
    }

    private val _preview = MutableStateFlow<TrialCard?>(null)
    /**
     * A card shown as a dev preview: the real card, through the real host, but answering it
     * changes nothing and logs nothing.
     */
    val preview: StateFlow<TrialCard?> = _preview.asStateFlow()
    fun debugPreview(card: TrialCard) { _preview.value = card }
    fun closePreview() { _preview.value = null }

    /** Back to a never-offered install. */
    fun debugReset() {
        save(TrialState())
        tick()
    }

    fun debugSummary(): String {
        val s = state
        return when {
            AdFreeTrialPolicy.active(s) -> "Trial running, ${(s.window!!.remainingMs / 60_000L)} min left"
            s.ended != null -> "Trial ended (${s.ended.analyticsName})" + if (s.endNoticePending) ", notice pending" else ""
            else -> "Not taken, ${s.declines} decline(s), ${usageDays()} usage day(s)"
        }
    }

    companion object {
        private const val PREFS = "ad_free_trial"
        private const val WINDOW = "trial"
        private const val KEY_DECLINES = "declines"
        private const val KEY_LAST_DECLINE_DAY = "last_decline_day"
        private const val KEY_ENDED = "ended"
        private const val KEY_NOTICE_PENDING = "end_notice_pending"
    }
}
