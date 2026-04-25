package com.vtbvita.widget

import android.content.Context

/**
 * Plain (unencrypted) SharedPreferences that mirror login state for the widget.
 * EncryptedSharedPreferences throws from BroadcastReceiver context on Android 10,
 * so the widget must not call SessionManager directly.
 */
object WidgetState {
    private const val PREFS = "vita_widget_state"
    private const val KEY_LOGGED_IN  = "logged_in"
    private const val KEY_PERSONA_ID = "persona_id"
    private const val KEY_WIDGET_ERROR = "last_error"

    fun setLoggedIn(context: Context, personaId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_PERSONA_ID, personaId)
            .apply()
    }

    fun setLoggedOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_PERSONA_ID)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED_IN, false)

    fun getPersonaId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PERSONA_ID, null)

    fun logError(context: Context, error: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_WIDGET_ERROR, error)
            .apply()
    }

    fun getLastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WIDGET_ERROR, null)

    fun clearError(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_WIDGET_ERROR)
            .apply()
    }
}
