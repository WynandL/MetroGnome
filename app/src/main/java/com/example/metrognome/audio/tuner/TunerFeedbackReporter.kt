package com.example.metrognome.audio.tuner

import android.os.Build
import com.example.metrognome.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Diagnostic snapshot collected when the tuner locks on a stable note.
 *
 * Every field is intentionally ML-friendly: raw numeric values rather than
 * formatted strings, so downstream training pipelines can ingest without
 * pre-processing.  The [ambientState] enum name is the one exception — it
 * is human-readable and stable across app versions.
 */
data class TunerSessionSnapshot(
    val noteName: String,
    val detectedHz: Float,
    val centsOffset: Float,
    val clarity: Float,
    val stableSeconds: Int,
    val referenceHz: Float,
    val calibrationFactor: Float,
    val calibrationMode: String?,
    val calibrationAccuracyCents: Float,
    val ambientState: String,
)

/**
 * Fire-and-forget Firestore reporter for tuner feedback.
 *
 * Uses anonymous Firebase Auth so submissions are attributable to a
 * persistent-but-anonymous session — no user account needed.
 *
 * Firestore security rules (set these in the Firebase console):
 *
 *   match /tuner_feedback/{doc} {
 *     allow create: if request.auth != null;
 *     allow read, update, delete: if false;
 *   }
 *
 * This allows any signed-in (including anonymous) user to write one
 * document but never read or modify the collection.
 */
object TunerFeedbackReporter {

    private const val COLLECTION = "tuner_feedback"

    fun submit(snapshot: TunerSessionSnapshot, thumbsUp: Boolean, reason: String? = null) {
        if (!TunerFeedbackConfig.ENABLED) return
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnSuccessListener { write(snapshot, thumbsUp, reason) }
        } else {
            write(snapshot, thumbsUp, reason)
        }
    }

    private fun write(snapshot: TunerSessionSnapshot, thumbsUp: Boolean, reason: String?) {
        try {
            val doc = hashMapOf(
                "thumbs_up"                  to thumbsUp,
                "thumbs_down_reason"         to reason,
                "note_name"                  to snapshot.noteName,
                "detected_hz"                to snapshot.detectedHz.toDouble(),
                "cents_offset"               to snapshot.centsOffset.toDouble(),
                "clarity"                    to snapshot.clarity.toDouble(),
                "stable_seconds"             to snapshot.stableSeconds,
                "reference_hz"               to snapshot.referenceHz.toDouble(),
                "calibration_factor"         to snapshot.calibrationFactor.toDouble(),
                "calibration_mode"           to snapshot.calibrationMode,
                "calibration_accuracy_cents" to snapshot.calibrationAccuracyCents
                    .takeIf { !it.isNaN() }?.toDouble(),
                "ambient_state"              to snapshot.ambientState,
                "app_version_code"           to BuildConfig.VERSION_CODE,
                "app_version_name"           to BuildConfig.VERSION_NAME,
                "android_api"                to Build.VERSION.SDK_INT,
                "timestamp_ms"               to System.currentTimeMillis(),
            )
            FirebaseFirestore.getInstance().collection(COLLECTION).add(doc)
        } catch (_: Exception) {
            // Never surface a feedback submission failure to the user
        }
    }
}
