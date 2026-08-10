package com.example.metrognome.notifications

import com.google.firebase.messaging.FirebaseMessaging

/**
 * The broadcast-to-everyone topic: subscribing every consenting device to this one
 * topic is what lets a message composed in the Firebase Console (or triggered from
 * a Firestore write) reach all users with no per-device targeting or backend needed.
 */
object NotificationTopics {
    const val ALL_USERS = "all_users"

    /** Safe to call repeatedly - FCM treats a repeat subscribe as a no-op. */
    fun subscribeToAllUsers() {
        FirebaseMessaging.getInstance().subscribeToTopic(ALL_USERS)
    }

    fun unsubscribeFromAllUsers() {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(ALL_USERS)
    }
}
