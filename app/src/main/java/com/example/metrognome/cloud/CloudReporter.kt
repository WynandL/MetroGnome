package com.example.metrognome.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Reusable fire-and-forget writer for anonymous Firestore submissions.
 *
 * This is the shared plumbing for every "send some anonymous diagnostics to the
 * backend" feature: it signs the device in anonymously (one persistent-but-anon
 * identity, no user account), then adds a single document to a collection. It
 * never throws and never surfaces a failure - reporting is best-effort and must
 * never affect the user experience.
 *
 * Feature-specific reporters (e.g. [MicCheckReporter]) live alongside this file in
 * the `cloud` package, build their own document from data the app already
 * captures, and call [submit]. Keeping all of this in `cloud` keeps the online
 * layer out of the feature engines (audio, points, etc.) so it can grow and be
 * reused independently.
 *
 * Firestore security rules pattern (set per collection in the Firebase console):
 *
 *   match /<collection>/{doc} {
 *     allow create: if request.auth != null;
 *     allow read, update, delete: if false;
 *   }
 *
 * Any signed-in (including anonymous) device may append one document but can never
 * read or modify the collection.
 */
object CloudReporter {

    /**
     * Append [document] to [collection], signing in anonymously first if needed.
     * Safe to call from any thread; returns immediately.
     */
    fun submit(collection: String, document: Map<String, Any?>) {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener { write(collection, document) }
                    .addOnFailureListener { /* swallow - best effort */ }
            } else {
                write(collection, document)
            }
        } catch (_: Exception) {
            // Never surface a reporting failure.
        }
    }

    private fun write(collection: String, document: Map<String, Any?>) {
        try {
            FirebaseFirestore.getInstance().collection(collection).add(document)
        } catch (_: Exception) {
            // Never surface a reporting failure.
        }
    }
}
