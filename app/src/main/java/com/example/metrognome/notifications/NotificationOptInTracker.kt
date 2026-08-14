package com.example.metrognome.notifications

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks whether the user has already been shown the one contextual notification
 * soft-ask (fired the first time the app is opened on a 2nd distinct calendar day -
 * see MainActivity). We only ever spend that strategic ask once, on purpose - a
 * "not now" answer is not re-litigated by nagging the user again later; from then on
 * the Notifications row in Settings is the only way back in.
 *
 * Stored in its own SharedPreferences file so it is independent of any feature's
 * settings and trivially clearable.
 */
class NotificationOptInTracker(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var hasShownContextualAsk: Boolean
        get() = prefs.getBoolean(KEY_SHOWN, false)
        set(value) { prefs.edit { putBoolean(KEY_SHOWN, value) } }

    companion object {
        private const val PREFS = "notification_opt_in"
        private const val KEY_SHOWN = "shown_contextual_ask"
    }
}
