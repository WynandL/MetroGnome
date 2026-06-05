package com.example.metrognome.audio.tuner

/**
 * Master switch for the tuner feedback feature.
 *
 * Set [ENABLED] to false to silence all prompts and disable Firestore
 * submission in any build without touching any other code.
 */
object TunerFeedbackConfig {
    const val ENABLED = true
}
