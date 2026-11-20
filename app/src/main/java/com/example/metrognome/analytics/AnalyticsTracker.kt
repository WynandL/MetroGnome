package com.example.metrognome.analytics

import com.example.metrognome.ads.UserTier
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * Central analytics event log. All Firebase Analytics calls go through here — nothing
 * else in the codebase imports firebase.analytics directly.
 *
 * Events use snake_case names and Long/String params per Firebase convention.
 * Firebase silently drops events with >25 params or names >40 chars.
 */
object AnalyticsTracker {

    private var metronomeSessionStartMs = 0L
    private var tunerSessionStartMs     = 0L
    private var droneSessionStartMs     = 0L
    private var chordsSessionStartMs    = 0L

    // ── Metronome ─────────────────────────────────────────────────────────────

    /** [soundSlug] is the chosen voice's [com.example.metrognome.billing.ClickSound.slug]. */
    fun logMetronomeStarted(bpm: Int, soundSlug: String, timeSig: Int) {
        metronomeSessionStartMs = System.currentTimeMillis()
        Firebase.analytics.logEvent("metronome_started") {
            param("bpm",        bpm.toLong())
            param("sound_type", soundSlug)
            param("time_sig",   timeSig.toLong())
        }
    }

    fun logMetronomeStopped() {
        val durationSec = if (metronomeSessionStartMs > 0L)
            (System.currentTimeMillis() - metronomeSessionStartMs) / 1000L else 0L
        metronomeSessionStartMs = 0L
        Firebase.analytics.logEvent("metronome_stopped") {
            param("duration_seconds", durationSec)
        }
    }

    /** [soundSlug] is [com.example.metrognome.billing.ClickSound.slug], e.g. "kalimba". */
    fun logSoundChanged(soundSlug: String, source: String) {
        Firebase.analytics.logEvent("sound_changed") {
            param("sound_type", soundSlug)
            param("source",     source)
        }
    }

    // ── Polls ─────────────────────────────────────────────────────────────────

    /** One appearance of a poll card; the denominator the Firestore responses lacked. */
    fun logPollShown(pollId: String, showNumber: Int) {
        Firebase.analytics.logEvent("poll_shown") {
            param("poll_id",     pollId)
            param("show_number", showNumber.toLong())
        }
    }

    /** "up", "down", "dismissed", or "ignored" (retired after five unanswered shows). */
    fun logPollAnswered(pollId: String, response: String) {
        Firebase.analytics.logEvent("poll_answered") {
            param("poll_id",  pollId)
            param("response", response)
        }
    }

    // ── Tuner ─────────────────────────────────────────────────────────────────

    fun logTunerStarted(referenceHz: Float) {
        tunerSessionStartMs = System.currentTimeMillis()
        Firebase.analytics.logEvent("tuner_session_started") {
            param("reference_hz", referenceHz.toLong())
        }
    }

    fun logTunerStopped() {
        val durationSec = if (tunerSessionStartMs > 0L)
            (System.currentTimeMillis() - tunerSessionStartMs) / 1000L else 0L
        tunerSessionStartMs = 0L
        Firebase.analytics.logEvent("tuner_session_ended") {
            param("duration_seconds", durationSec)
        }
    }

    // ── Drone ─────────────────────────────────────────────────────────────────

    /**
     * A drone session began.
     *
     * The note is sent as its label ("A3") rather than a MIDI number so the report reads
     * without a lookup table, and it is the parameter worth watching: a pile of E, A and D
     * says guitarists, a pile of Bb says brass, and either would say more about who is
     * using this than any amount of duration data.
     */
    fun logDroneStarted(note: String, timbre: String, blend: String, referenceHz: Float) {
        droneSessionStartMs = System.currentTimeMillis()
        Firebase.analytics.logEvent("drone_started") {
            param("note",         note)
            param("timbre",       timbre)
            param("blend",        blend)
            param("reference_hz", referenceHz.toLong())
        }
    }

    fun logDroneStopped() {
        val durationSec = if (droneSessionStartMs > 0L)
            (System.currentTimeMillis() - droneSessionStartMs) / 1000L else 0L
        droneSessionStartMs = 0L
        Firebase.analytics.logEvent("drone_stopped") {
            param("duration_seconds", durationSec)
        }
    }

    /** The drone's voice was changed, whether or not it was sounding at the time. */
    fun logDroneVoiceChanged(timbre: String, blend: String) {
        Firebase.analytics.logEvent("drone_voice_changed") {
            param("timbre", timbre)
            param("blend",  blend)
        }
    }

    /**
     * A paid drone voice was auditioned from its purchase dialog.
     *
     * Pair this with the Play Console's sales for the same product id and it answers the
     * only question a paywall really raises: of the people who got far enough to hear it,
     * how many bought it. A low preview count means the chip is not being found; a high
     * preview count with few sales means the voice is not worth the money to them.
     */
    fun logDroneVoicePreviewed(productId: String) {
        Firebase.analytics.logEvent("drone_voice_previewed") {
            param("product_id", productId)
        }
    }

    // ── Chord Finder ──────────────────────────────────────────────────────────

    /**
     * The Chords tab was opened. [mic] is "on", "off" (the user switched it off) or
     * "denied" (no permission), which is the first thing to know about how the feature is
     * used: a tab full of "off" says people tap chords in, and the mic path is a bonus.
     */
    fun logChordsSessionStarted(instrument: String, mic: String, engine: String) {
        chordsSessionStartMs = System.currentTimeMillis()
        Firebase.analytics.logEvent("chords_session_started") {
            param("instrument", instrument)
            param("mic",        mic)
            param("engine",     engine)
        }
    }

    /** The Chords tab's listening engine was switched (MCLEOD or BROSSIER). */
    fun logChordsEngineChanged(engine: String) {
        Firebase.analytics.logEvent("chords_engine_changed") {
            param("engine", engine)
        }
    }

    /**
     * The Chords tab was left. The three counts say what the visit was: notes that came
     * from the mic against notes that were tapped, and how many distinct chords ended up
     * named, so a long visit with nothing named is visible as a failure rather than as
     * engagement.
     */
    fun logChordsSessionEnded(tapNotes: Int, micNotes: Int, chordsNamed: Int) {
        val durationSec = if (chordsSessionStartMs > 0L)
            (System.currentTimeMillis() - chordsSessionStartMs) / 1000L else 0L
        chordsSessionStartMs = 0L
        Firebase.analytics.logEvent("chords_session_ended") {
            param("duration_seconds", durationSec)
            param("tap_notes",        tapNotes.toLong())
            param("mic_notes",        micNotes.toLong())
            param("chords_named",     chordsNamed.toLong())
        }
    }

    /**
     * A chord name held on screen long enough to have been read (see the ViewModel's
     * debounce), so the intermediate triad on the way to a seventh is not counted. The
     * symbol is the parameter worth watching, for the same reason as the drone's note: a
     * pile of E, A, D and G says guitarists, a pile of maj7 and m9 says jazz.
     */
    fun logChordNamed(symbol: String, noteCount: Int, source: String) {
        Firebase.analytics.logEvent("chord_named") {
            param("symbol",     symbol)
            param("note_count", noteCount.toLong())
            param("source",     source)
        }
    }

    /**
     * "Hear it" was tapped: the collected notes sounded as an arpeggio and then together.
     * [symbol] is null when the set had no dictionary name (a single note, an interval, an
     * unnamed cluster); [pace] is the user's SLOW/QUICK choice. Whether people want to hear
     * the chord back is the question the feature raises, so the tap is what is counted.
     */
    fun logChordPlayed(symbol: String?, noteCount: Int, pace: String) {
        Firebase.analytics.logEvent("chord_played") {
            param("symbol",     symbol ?: "none")
            param("note_count", noteCount.toLong())
            param("pace",       pace)
        }
    }

    /** The drawn instrument was switched, like the drone's voice change. */
    fun logChordsInstrumentChanged(instrument: String) {
        Firebase.analytics.logEvent("chords_instrument_changed") {
            param("instrument", instrument)
        }
    }

    // ── Rhythm game ───────────────────────────────────────────────────────────

    fun logGameStarted(difficulty: String, bpm: Int) {
        Firebase.analytics.logEvent("game_started") {
            param("difficulty", difficulty)
            param("bpm",        bpm.toLong())
        }
    }

    fun logGameCompleted(
        difficulty: String,
        score: Int,
        perfect: Int,
        good: Int,
        almost: Int,
        miss: Int,
        isNewRecord: Boolean
    ) {
        Firebase.analytics.logEvent("game_completed") {
            param("difficulty",  difficulty)
            param("score",       score.toLong())
            param("perfect",     perfect.toLong())
            param("good",        good.toLong())
            param("almost",      almost.toLong())
            param("miss",        miss.toLong())
            param("new_record",  if (isNewRecord) "true" else "false")
        }
    }

    // ── Practice ──────────────────────────────────────────────────────────────

    fun logPracticeStarted(goalMinutes: Int) {
        Firebase.analytics.logEvent("practice_started") {
            param("goal_minutes", goalMinutes.toLong())
        }
    }

    /**
     * [clapOffsetMs]: the session's median clap offset from the nearest beat (+ late), sent only
     * when Groove Check scored a confident rhythm, with [clapCount] the claps behind it. It checks
     * in the field that beat timing is centred on every phone model, not just the dev's.
     */
    fun logPracticeCompleted(
        durationMinutes: Int,
        streak: Int,
        totalSessions: Int,
        clapOffsetMs: Int? = null,
        clapCount: Int = 0,
    ) {
        Firebase.analytics.logEvent("practice_completed") {
            param("duration_minutes", durationMinutes.toLong())
            param("streak",           streak.toLong())
            param("total_sessions",   totalSessions.toLong())
            if (clapOffsetMs != null) {
                param("clap_offset_ms", clapOffsetMs.toLong())
                param("clap_count",     clapCount.toLong())
            }
        }
    }

    fun logPracticeCancelled(secondsElapsed: Int, goalSeconds: Int) {
        Firebase.analytics.logEvent("practice_cancelled") {
            param("seconds_elapsed", secondsElapsed.toLong())
            param("goal_seconds",    goalSeconds.toLong())
        }
    }

    // ── BPM Presets ───────────────────────────────────────────────────────────

    fun logPresetSaved(bpm: Int) {
        Firebase.analytics.logEvent("preset_saved") {
            param("bpm", bpm.toLong())
        }
    }

    fun logPresetLoaded(bpm: Int) {
        Firebase.analytics.logEvent("preset_loaded") {
            param("bpm", bpm.toLong())
        }
    }

    // ── Speed Trainer ─────────────────────────────────────────────────────────

    fun logSpeedTrainerStarted(startBpm: Int, targetBpm: Int, stepCount: Int, micEnabled: Boolean) {
        Firebase.analytics.logEvent("speed_trainer_started") {
            param("start_bpm",   startBpm.toLong())
            param("target_bpm",  targetBpm.toLong())
            param("step_count",  stepCount.toLong())
            param("mic_enabled", if (micEnabled) "true" else "false")
        }
    }

    fun logSpeedTrainerCompleted(
        startBpm: Int,
        targetBpm: Int,
        reachedBpm: Int,
        totalSessions: Int,
        micEnabled: Boolean,
        clapOffsetMs: Int? = null,
        clapCount: Int = 0,
    ) {
        Firebase.analytics.logEvent("speed_trainer_completed") {
            param("start_bpm",      startBpm.toLong())
            param("target_bpm",     targetBpm.toLong())
            param("reached_bpm",    reachedBpm.toLong())
            param("total_sessions", totalSessions.toLong())
            param("mic_enabled",    if (micEnabled) "true" else "false")
            // Same pair as practice_completed; see logPracticeCompleted.
            if (clapOffsetMs != null) {
                param("clap_offset_ms", clapOffsetMs.toLong())
                param("clap_count",     clapCount.toLong())
            }
        }
    }

    fun logSpeedTrainerCancelled(stepIndex: Int, totalSteps: Int) {
        Firebase.analytics.logEvent("speed_trainer_cancelled") {
            param("step_index",  stepIndex.toLong())
            param("total_steps", totalSteps.toLong())
        }
    }

    // ── Sound stage ───────────────────────────────────────────────────────────

    /**
     * The click-sound stage opened. [source] is "home" (the pill; "settings" before 2026-11-20, when Settings'
     * click chips were removed). Against
     * active users this is the reach of the sounds; against sound_previewed and iap_started
     * per product, it is the funnel the stage exists to fill.
     */
    fun logSoundStageOpened(source: String) {
        Firebase.analytics.logEvent("sound_stage_opened") {
            param("source", source)
        }
    }

    /**
     * A click voice was auditioned on the stage. [productId] is "free" for a free voice, so
     * the registered product_id dimension carries the paid/free split without a new param.
     */
    fun logSoundPreviewed(soundSlug: String, productId: String?, source: String) {
        Firebase.analytics.logEvent("sound_previewed") {
            param("sound_type", soundSlug)
            param("product_id", productId ?: "free")
            param("source",     source)
        }
    }

    // ── Purchases ─────────────────────────────────────────────────────────────
    //
    // Named iap_* on purpose: `in_app_purchase` is a Firebase automatic event and `purchase`
    // is a reserved ecommerce event, and logging either by hand would mix with or be
    // rejected by Google's own handling. The Play Console remains the record of money; these
    // say where in the funnel people stop. Both params (product_id, response) are already
    // registered as custom dimensions, so reports show them with no extra setup.

    /** Google Play's purchase sheet opened for [productId]. */
    fun logPurchaseStarted(productId: String) {
        Firebase.analytics.logEvent("iap_started") {
            param("product_id", productId)
        }
    }

    /**
     * How a purchase attempt ended: "purchased", "pending", "cancelled" (closed the Play
     * sheet), "already_owned", or "error_<code>" ("error_not_ready" when the store was not
     * connected, "error_no_details" when Play did not return the product).
     */
    fun logPurchaseResult(productId: String, response: String) {
        Firebase.analytics.logEvent("iap_result") {
            param("product_id", productId)
            param("response",   response)
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    /**
     * The one way to log `item_unlocked`: every screen that queues an unlock celebration
     * passes the new entries here, and [MetroItemTracker.claimUnreported] lets each item
     * through once per install. So event count = users who earned it (from 2026-11-19 on;
     * earlier counts are inflated by repeats and miss every Rhythm-tab unlock).
     */
    fun logItemsUnlocked(tracker: MetroItemTracker, entries: List<MetroItemEntry>) {
        if (entries.isEmpty()) return
        val fresh = tracker.claimUnreported(entries.map { it.item.id }).toSet()
        entries.filter { it.item.id in fresh }.distinctBy { it.item.id }.forEach { entry ->
            Firebase.analytics.logEvent("item_unlocked") {
                param("item_id",   entry.item.id)
                param("item_name", entry.item.displayName)
            }
        }
    }

    // ── Gnotes economy ────────────────────────────────────────────────────────

    /** One Gnotes-earning event. Logged centrally for every banner the app posts. */
    fun logGnotesEarned(
        activity: String,
        amount: Int,
        todayTotal: Int,
        dailyLimit: Int,
        limitReached: Boolean,
    ) {
        Firebase.analytics.logEvent("gnotes_earned") {
            param("activity",      activity)
            param("amount",        amount.toLong())
            param("today_total",   todayTotal.toLong())
            param("daily_limit",   dailyLimit.toLong())
            param("limit_reached", if (limitReached) "true" else "false")
        }
    }

    /** Graded mic Timing Bonus credited after a Speed Trainer or Practice session. */
    fun logTimingBonusEarned(source: String, bonus: Int, fraction: Float, hitCount: Int) {
        Firebase.analytics.logEvent("timing_bonus_earned") {
            param("source",    source)        // "speed_trainer" | "practice"
            param("bonus",     bonus.toLong())
            param("fraction",  fraction.toDouble())
            param("hit_count", hitCount.toLong())
        }
    }

    // ── Mic check (user-facing self-test) ─────────────────────────────────────

    /** Outcome of a user-facing microphone check. Detailed device data goes to Firestore separately. */
    fun logMicCheckCompleted(verdict: String, grade: String) {
        Firebase.analytics.logEvent("mic_check_completed") {
            param("verdict", verdict)   // PASS | FAIL | ABORT
            param("grade",   grade)     // GOOD_FIT | USABLE | NOT_FIT
        }
    }

    // ── Rewards ───────────────────────────────────────────────────────────────

    /** A non-billing reward was granted (e.g. ad-free days for hitting the daily Gnotes cap). */
    fun logRewardGranted(type: String, days: Int) {
        Firebase.analytics.logEvent("reward_granted") {
            param("type", type)         // "ad_free"
            param("days", days.toLong())
        }
    }

    // ── Ad-free trial (adfree/) ───────────────────────────────────────────────
    //
    // The funnel: offered -> accepted or declined -> ended (expired or purchased), and
    // ad_free_trial_buy for a Remove Ads purchase started from the trial's own surfaces;
    // iap_started / iap_result (product remove_ads) carry the rest of that purchase.
    // offer_number (1 or 2: the re-offer), reason and source need registering in Firebase.

    /** The trial's offer card came on screen. */
    fun logAdFreeTrialOffered(offerNumber: Int) {
        Firebase.analytics.logEvent("ad_free_trial_offered") {
            param("offer_number", offerNumber.toLong())
        }
    }

    /** "Try it". */
    fun logAdFreeTrialAccepted(offerNumber: Int) {
        Firebase.analytics.logEvent("ad_free_trial_accepted") {
            param("offer_number", offerNumber.toLong())
        }
    }

    /** "Not now" (or Back) on the offer. */
    fun logAdFreeTrialDeclined(offerNumber: Int) {
        Firebase.analytics.logEvent("ad_free_trial_declined") {
            param("offer_number", offerNumber.toLong())
        }
    }

    /** The trial ended: [reason] "expired" or "purchased" (Remove Ads bought during it). */
    fun logAdFreeTrialEnded(reason: String) {
        Firebase.analytics.logEvent("ad_free_trial_ended") {
            param("reason", reason)
        }
    }

    /** Remove Ads tapped from a trial surface: [source] "end_card" or "settings_trial". */
    fun logAdFreeTrialBuy(source: String) {
        Firebase.analytics.logEvent("ad_free_trial_buy") {
            param("source", source)
        }
    }

    /** A rewarded ad was watched to completion and Gnotes were granted. */
    fun logRewardedAdWatched(gnotes: Int) {
        Firebase.analytics.logEvent("rewarded_ad_watched") {
            param("gnotes", gnotes.toLong())
        }
    }

    // ── User tier ────────────────────────────────────────────────────────────

    /**
     * Sets the "user_tier" Firebase user property (explorer / casual / engaged).
     * User properties persist on the device and tag every subsequent analytics event,
     * so you can filter or segment any report or the real-time view by tier.
     * Call once per app launch — tier changes only happen after days of usage.
     */
    fun updateUserTier(tier: UserTier) {
        Firebase.analytics.setUserProperty("user_tier", tier.name.lowercase())
    }

    // ── Loyalty ───────────────────────────────────────────────────────────────

    /** A loyalty day-count milestone (7, 30, 60, 100, 365…) was reached. */
    fun logLoyaltyMilestone(days: Int) {
        Firebase.analytics.logEvent("loyalty_milestone") {
            param("days", days.toLong())
        }
    }
}
