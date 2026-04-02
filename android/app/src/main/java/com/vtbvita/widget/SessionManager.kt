package com.vtbvita.widget

import android.content.Context

object SessionManager {
    private const val PREFS = "vita_session"
    private const val KEY_PERSONA = "persona_id"

    fun login(context: Context, personaId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PERSONA, personaId).apply()

    fun logout(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()

    fun getPersonaId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PERSONA, null)

    fun isLoggedIn(context: Context): Boolean = getPersonaId(context) != null

    fun currentPersona(context: Context): Persona? =
        PERSONAS.find { it.id == getPersonaId(context) }
}
