package com.example.metrognome.viewmodel

/**
 * Single source of truth for the rhythm-game star rating (0..3).
 *
 * Both the end-of-game result panel and the Rhythm-page difficulty chips call
 * this, so the stars a player earns for a run and the stars shown next to that
 * difficulty's best score always agree.
 *
 * Derived purely from the score relative to a flawless run (every note PERFECT),
 * so nothing extra needs to be persisted - the stored best score is enough.
 */

// Points awarded for a PERFECT hit in RhythmGameViewModel.registerHit().
// A flawless run scores beats * this. Keep in sync if hit scoring changes.
private const val PERFECT_HIT_SCORE = 100

/** Star rating 0..3 for a [score] on a difficulty with [beats] notes. */
fun rhythmStars(score: Int, beats: Int): Int {
    if (score <= 0 || beats <= 0) return 0
    val fraction = score.toFloat() / (beats * PERFECT_HIT_SCORE)
    return when {
        fraction >= 0.92f -> 3   // near-flawless
        fraction >= 0.65f -> 2   // solid
        else -> 1                // played and scored
    }
}
