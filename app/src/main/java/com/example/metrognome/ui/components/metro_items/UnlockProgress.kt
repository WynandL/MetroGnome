package com.example.metrognome.ui.components.metro_items

/**
 * Progress reporting for unlock conditions.
 *
 * Two extension functions on [UnlockCondition]:
 *  - [unlockProgress] — fraction [0f, 1f] toward satisfying the condition
 *  - [progressLabel]  — compact "current / required unit" string for display
 *
 * Both read live counters from [MetroItemTracker]. Call inside a remember {}
 * block if the result is used in a long-lived composable (e.g. a full-screen
 * dialog) so the reads happen once rather than on every recomposition.
 *
 * When a new [UnlockCondition] variant is added the compiler will flag both
 * when-expressions here as non-exhaustive, keeping this file in sync.
 */

/** Fraction [0f, 1f] of the way toward satisfying this condition. */
fun UnlockCondition.unlockProgress(tracker: MetroItemTracker): Float = when (this) {
    is UnlockCondition.MetronomeSeconds ->
        (tracker.metronomeSeconds().toFloat() / required).coerceIn(0f, 1f)

    is UnlockCondition.TunerSeconds ->
        (tracker.tunerSeconds().toFloat() / required).coerceIn(0f, 1f)

    is UnlockCondition.RhythmGamesCompleted ->
        (tracker.gamesCompleted().toFloat() / required).coerceIn(0f, 1f)

    is UnlockCondition.DaysSinceFirstLaunch ->
        (tracker.daysSinceFirstLaunch().toFloat() / required).coerceIn(0f, 1f)

    is UnlockCondition.PracticeSessionsCompleted ->
        (tracker.practiceSessionsCompleted().toFloat() / required).coerceIn(0f, 1f)

    is UnlockCondition.TunerFeedbackGiven ->
        (tracker.tunerFeedbackGiven().toFloat() / required).coerceIn(0f, 1f)

    is UnlockCondition.SpeedTrainingSessionsCompleted ->
        (tracker.speedTrainingSessionsCompleted().toFloat() / required).coerceIn(0f, 1f)

    UnlockCondition.Always -> 1f
}

/**
 * Specific "current / required unit · activity" label for the progress bar.
 *
 * Always names the activity so there is zero ambiguity — "5 / 30 min metronome"
 * rather than just "5 / 30 min". The unit matches the requirement scale (minutes
 * for short thresholds, hours for long ones) so the numbers read naturally.
 *
 * This is the single source of truth for progress labels. Do not duplicate this
 * text in MetroItem or elsewhere — the condition type already carries the context.
 *
 * Returns an empty string for [UnlockCondition.Always] (never shown; item is
 * always unlocked and therefore never in the locked state of the catalog).
 */
fun UnlockCondition.progressLabel(tracker: MetroItemTracker): String = when (this) {
    is UnlockCondition.MetronomeSeconds -> {
        val reqMins = required / 60L
        if (reqMins >= 60L) {
            val curH = tracker.metronomeSeconds() / 3600L
            val reqH = required / 3600L
            "$curH / $reqH h metronome"
        } else {
            val curMin = tracker.metronomeSeconds() / 60L
            "$curMin / $reqMins min metronome"
        }
    }

    is UnlockCondition.TunerSeconds -> {
        val reqMins = required / 60L
        if (reqMins >= 60L) {
            val curH = tracker.tunerSeconds() / 3600L
            val reqH = required / 3600L
            "$curH / $reqH h tuning"
        } else {
            val curMin = tracker.tunerSeconds() / 60L
            "$curMin / $reqMins min tuning"
        }
    }

    is UnlockCondition.RhythmGamesCompleted -> {
        val cur = tracker.gamesCompleted()
        "$cur / $required rhythm game${if (required != 1) "s" else ""}"
    }

    is UnlockCondition.DaysSinceFirstLaunch -> {
        val cur = tracker.daysSinceFirstLaunch()
        "Day $cur of $required since install"
    }

    is UnlockCondition.PracticeSessionsCompleted -> {
        val cur = tracker.practiceSessionsCompleted()
        "$cur / $required practice session${if (required != 1) "s" else ""}"
    }

    is UnlockCondition.TunerFeedbackGiven -> {
        val cur = tracker.tunerFeedbackGiven()
        "$cur / $required tuner rating${if (required != 1) "s" else ""}"
    }

    is UnlockCondition.SpeedTrainingSessionsCompleted -> {
        val cur = tracker.speedTrainingSessionsCompleted()
        "$cur / $required speed session${if (required != 1) "s" else ""}"
    }

    UnlockCondition.Always -> ""
}
