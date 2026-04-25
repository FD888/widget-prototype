package com.vtbvita.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SessionManager {
    private const val PREFS = "vita_session"
    private const val KEY_PERSONA           = "persona_id"
    private const val KEY_APP_TOKEN         = "app_token"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ------------------------------------------------------------------
    // App token — выдаётся сервером после верификации номера телефона.
    // Живёт 30 дней. Используется как X-Api-Key в каждом запросе к API.
    // ------------------------------------------------------------------

    fun saveAppToken(context: Context, token: String) =
        prefs(context).edit().putString(KEY_APP_TOKEN, token).apply()

    fun getAppToken(context: Context): String? =
        prefs(context).getString(KEY_APP_TOKEN, null)

    fun hasAppToken(context: Context): Boolean = getAppToken(context) != null

    // ------------------------------------------------------------------
    // Persona — профиль пользователя (Denis / Masha / Yana).
    // Сохраняется после выбора профиля, не зависит от app_token.
    // ------------------------------------------------------------------

    fun login(context: Context, personaId: String) {
        prefs(context).edit().putString(KEY_PERSONA, personaId).apply()
        WidgetState.setLoggedIn(context, personaId)
    }

    fun logout(context: Context) {
        prefs(context).edit().remove(KEY_PERSONA).apply()   // app_token НЕ сбрасываем при logout
        WidgetState.setLoggedOut(context)
    }

    fun getPersonaId(context: Context): String? =
        prefs(context).getString(KEY_PERSONA, null)

    fun isLoggedIn(context: Context): Boolean = getPersonaId(context) != null

    fun currentPersona(context: Context): Persona? =
        PERSONAS.find { it.id == getPersonaId(context) }

    // ------------------------------------------------------------------
    // Биометрия — разрешение входа по отпечатку/лицу вместо PIN
    // ------------------------------------------------------------------

    fun setBiometricEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
}
