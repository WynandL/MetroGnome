package com.example.metrognome.points

/**
 * Pure, stateless Gnotes calculator.
 *
 * Takes lifetime usage counters and an optional [DailyActivity] snapshot and
 * produces a [PointsSnapshot]. When today is provided, today's contribution
 * is capped per [PointsLimits]; historical contributions (all previous days)
 * are never retroactively capped, preserving backward compatibility for
 * existing users.
 *
 * No Android dependencies — trivially unit-testable and portable to a server.
 *
 * Only non-zero contributions appear in the list, so new users see a clean
 * empty state rather than a wall of zero rows.
 */
object PointsCalculator {

    /**
     * @param today  Today's activity delta from [DailyActivityLog]. Pass null to
     *               calculate from lifetime totals with no daily caps applied
     *               (useful for unit tests or before [DailyActivityLog] is wired up).
     */
    fun calculate(
        metronomeSeconds: Long,
        tunerNotesLocked: Int,
        totalGameScore: Int,
        totalPracticeMinutes: Int,
        speedTrainerSeconds: Long,
        tunerFeedbackGiven: Int,
        /** Lifetime graded timing-bonus points from Practice + Speed Trainer → "Timing Bonus". */
        performanceBonusPoints: Int,
        /** Distinct days the user actually opened the app → "Loyalty" contribution. */
        loyaltyActiveDays: Int,
        /** Calendar days since first launch (whether opened or not) → "Installed" contribution. */
        installedDays: Int,
        today: DailyActivity? = null,
        /** Already daily-capped at earn time by RewardedAdManager; pass through directly. */
        rewardedAdGnotes: Int = 0,
    ): PointsSnapshot {

        // Effective counted minutes for a time-based activity.
        // Historical minutes (before today) are uncapped; today is capped at the daily limit.
        fun countedMinutes(lifetimeSec: Long, todaySec: Long, limitMin: Int): Long {
            val historicalMin = (lifetimeSec - todaySec) / 60
            val todayMin = todaySec / 60
            return historicalMin + todayMin.coerceAtMost(limitMin.toLong())
        }

        // Effective counted units for an event-based activity.
        fun countedEvents(lifetimeTotal: Int, todayTotal: Int, limitPerDay: Int): Int {
            val historical = lifetimeTotal - todayTotal
            return historical + todayTotal.coerceAtMost(limitPerDay)
        }

        // Gnotes from a score-based activity: historical score uncapped, today's score capped at maxBeatsPerDay.
        fun beatsFromScore(lifetimeScore: Int, todayScore: Int, divisor: Int, maxBeatsPerDay: Int): Int {
            val historicalBeats = (lifetimeScore - todayScore) / divisor
            val todayBeats      = (todayScore / divisor).coerceAtMost(maxBeatsPerDay)
            return historicalBeats + todayBeats
        }

        val t = today ?: DailyActivity.EMPTY
        val applyLimits = today != null

        val metronomeMinutes = if (applyLimits)
            countedMinutes(metronomeSeconds, t.metronomeSeconds, PointsLimits.METRONOME_MINUTES_PER_DAY)
        else
            metronomeSeconds / 60

        val tunerNotes = if (applyLimits)
            countedEvents(tunerNotesLocked, t.tunerNotesLocked, PointsLimits.TUNER_NOTES_PER_DAY)
        else
            tunerNotesLocked

        val gameBeats = if (applyLimits)
            beatsFromScore(totalGameScore, t.gameScoreToday, PointsConfig.GAME_SCORE_DIVISOR, PointsLimits.RHYTHM_BEATS_PER_DAY)
        else
            totalGameScore / PointsConfig.GAME_SCORE_DIVISOR

        val practiceMinutes = if (applyLimits)
            countedEvents(totalPracticeMinutes, t.practiceMinutesToday, PointsLimits.PRACTICE_MINUTES_PER_DAY)
        else
            totalPracticeMinutes

        val speedTrainerMins = if (applyLimits)
            countedMinutes(speedTrainerSeconds, t.speedTrainerSecondsToday, PointsLimits.SPEED_TRAINER_MINUTES_PER_DAY)
        else
            speedTrainerSeconds / 60

        val feedback = if (applyLimits)
            countedEvents(tunerFeedbackGiven, t.tunerFeedbackGiven, PointsLimits.TUNER_FEEDBACK_PER_DAY)
        else
            tunerFeedbackGiven

        val performanceBonus = if (applyLimits)
            countedEvents(performanceBonusPoints, t.performanceBonusToday, PointsLimits.PERFORMANCE_BONUS_PER_DAY)
        else
            performanceBonusPoints

        val contributions = buildList {
            if (metronomeMinutes > 0L) add(
                PointsContribution(
                    label    = "Metronome",
                    rawValue = metronomeMinutes,
                    rawUnit  = "min",
                    points   = (metronomeMinutes * PointsConfig.METRONOME_PER_MINUTE).toInt(),
                )
            )
            if (tunerNotes > 0) add(
                PointsContribution(
                    label    = "Tuner",
                    rawValue = tunerNotes.toLong(),
                    rawUnit  = if (tunerNotes == 1) "note" else "notes",
                    points   = tunerNotes * PointsConfig.PER_TUNER_NOTE,
                )
            )
            if (gameBeats > 0) add(
                PointsContribution(
                    label    = "Rhythm Game",
                    rawValue = gameBeats.toLong(),
                    rawUnit  = "score pts",
                    points   = gameBeats,
                )
            )
            if (practiceMinutes > 0) add(
                PointsContribution(
                    label    = "Practice",
                    rawValue = practiceMinutes.toLong(),
                    rawUnit  = "min",
                    points   = practiceMinutes * PointsConfig.PER_PRACTICE_MINUTE,
                )
            )
            if (speedTrainerMins > 0) add(
                PointsContribution(
                    label    = "Speed Trainer",
                    rawValue = speedTrainerMins,
                    rawUnit  = "min",
                    points   = (speedTrainerMins * PointsConfig.PER_SPEED_TRAINER_MINUTE).toInt(),
                )
            )
            if (feedback > 0) add(
                PointsContribution(
                    label    = "Tuner Feedback",
                    rawValue = feedback.toLong(),
                    rawUnit  = "submitted",
                    points   = feedback * PointsConfig.PER_TUNER_FEEDBACK,
                )
            )
            if (performanceBonus > 0) add(
                PointsContribution(
                    label    = "Timing Bonus",
                    rawValue = performanceBonus.toLong(),
                    rawUnit  = "in time",
                    points   = performanceBonus * PointsConfig.PER_PERFORMANCE_BONUS,
                )
            )
            if (loyaltyActiveDays > 0) add(
                PointsContribution(
                    label    = "Loyalty",
                    rawValue = loyaltyActiveDays.toLong(),
                    rawUnit  = if (loyaltyActiveDays == 1) "day" else "days",
                    points   = loyaltyActiveDays * PointsConfig.LOYALTY_PER_DAY,
                )
            )
            if (installedDays > 0) add(
                PointsContribution(
                    label    = "Installed",
                    rawValue = installedDays.toLong(),
                    rawUnit  = if (installedDays == 1) "day" else "days",
                    points   = installedDays * PointsConfig.INSTALLED_DAY_BONUS,
                )
            )
            if (rewardedAdGnotes > 0) add(
                PointsContribution(
                    label    = "Ad Bonus",
                    rawValue = rewardedAdGnotes.toLong(),
                    rawUnit  = "pts",
                    points   = rewardedAdGnotes,
                )
            )
        }

        return PointsSnapshot(contributions)
    }

    /**
     * Today's capped Gnotes total from a [DailyActivity] snapshot.
     *
     * Used by [com.example.metrognome.points.rewards.RewardManager] to detect whether
     * the user has reached the daily maximum without touching historical totals.
     * Loyalty is included unconditionally — if the app is running, the day is earned.
     * Keep in sync with the capping logic in [calculate].
     */
    /**
     * Today's capped rhythm-game Gnotes only - for the Rhythm page "daily target" meter.
     * Mirrors the game branch of [calculate]/[todayBeats] so the meter agrees with the total.
     */
    fun rhythmBeatsToday(today: DailyActivity): Int =
        (today.gameScoreToday / PointsConfig.GAME_SCORE_DIVISOR).coerceAtMost(PointsLimits.RHYTHM_BEATS_PER_DAY)

    fun todayBeats(today: DailyActivity): Int {
        val metMins    = (today.metronomeSeconds / 60).coerceAtMost(PointsLimits.METRONOME_MINUTES_PER_DAY.toLong())
        val tunerNotes = today.tunerNotesLocked.coerceAtMost(PointsLimits.TUNER_NOTES_PER_DAY)
        val gameBeats  = (today.gameScoreToday / PointsConfig.GAME_SCORE_DIVISOR).coerceAtMost(PointsLimits.RHYTHM_BEATS_PER_DAY)
        val pracMins   = today.practiceMinutesToday.coerceAtMost(PointsLimits.PRACTICE_MINUTES_PER_DAY)
        val speedMins  = (today.speedTrainerSecondsToday / 60).coerceAtMost(PointsLimits.SPEED_TRAINER_MINUTES_PER_DAY.toLong()).toInt()
        val feedback   = today.tunerFeedbackGiven.coerceAtMost(PointsLimits.TUNER_FEEDBACK_PER_DAY)
        val perfBonus  = today.performanceBonusToday.coerceAtMost(PointsLimits.PERFORMANCE_BONUS_PER_DAY)
        return (metMins * PointsConfig.METRONOME_PER_MINUTE).toInt() +
               tunerNotes * PointsConfig.PER_TUNER_NOTE +
               gameBeats +
               pracMins * PointsConfig.PER_PRACTICE_MINUTE +
               speedMins * PointsConfig.PER_SPEED_TRAINER_MINUTE +
               feedback * PointsConfig.PER_TUNER_FEEDBACK +
               perfBonus * PointsConfig.PER_PERFORMANCE_BONUS +
               PointsConfig.LOYALTY_PER_DAY
    }
}
