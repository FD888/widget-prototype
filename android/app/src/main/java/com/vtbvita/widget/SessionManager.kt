package com.vtbvita.widget

import android.content.Context

object SessionManager {
    private const val PREFS = "vita_session"
    private const val KEY_PERSONA          = "persona_id"
    private const val KEY_APP_TOKEN        = "app_token"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    // ------------------------------------------------------------------
    // App token — выдаётся сервером после верификации номера телефона.
    // Живёт 30 дней. Используется как X-Api-Key в каждом запросе к API.
    // ------------------------------------------------------------------

    fun saveAppToken(context: Context, token: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_APP_TOKEN, token).apply()

    fun getAppToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APP_TOKEN, null)

    fun hasAppToken(context: Context): Boolean = getAppToken(context) != null

    // ------------------------------------------------------------------
    // Persona — профиль пользователя (Denis / Masha / Yana).
    // Сохраняется после выбора профиля, не зависит от app_token.
    // ------------------------------------------------------------------

    fun login(context: Context, personaId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PERSONA, personaId).apply()

    fun logout(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_PERSONA).apply()   // app_token НЕ сбрасываем при logout

    fun getPersonaId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PERSONA, null)

    fun isLoggedIn(context: Context): Boolean = getPersonaId(context) != null

    fun currentPersona(context: Context): Persona? =
        PERSONAS.find { it.id == getPersonaId(context) }

    // ------------------------------------------------------------------
    // Биометрия — разрешение входа по отпечатку/лицу вместо PIN
    // ------------------------------------------------------------------

    fun setBiometricEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun isBiometricEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BIOMETRIC_ENABLED, false)
}
