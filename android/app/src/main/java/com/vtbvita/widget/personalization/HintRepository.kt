package com.vtbvita.widget.personalization

import android.content.Context
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.SessionManager
import com.vtbvita.widget.VitaWidgetProvider
import com.vtbvita.widget.model.HintResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

object HintRepository {

    private const val PREFS_NAME = "vita_hints"
    private const val KEY_HINT_TEXT = "current_hint"
    private const val KEY_HINT_TYPE = "hint_type"
    private const val KEY_HINT_TIME = "hint_time"
    private const val KEY_HINT_OPEN_COUNT = "open_count"
    private const val KEY_SERVER_HINT_TYPE = "server_hint_type"
    private const val KEY_SERVER_HINT_NAME = "server_hint_name"
    private const val KEY_SERVER_HINT_OFFER_TEXT = "server_hint_offer_text"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val HINT_MAX_OPENS = 8

    fun resolveHint(context: Context, persona: com.vtbvita.widget.Persona?): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val savedHint = prefs.getString(KEY_HINT_TEXT, null)
        val openCount = prefs.getInt(KEY_HINT_OPEN_COUNT, 0)

        val tooManyOpens = openCount >= HINT_MAX_OPENS

        if (savedHint != null && !tooManyOpens) {
            return savedHint
        }

        val newHint = NeutralHints.pick(
            gender = persona?.gender ?: "male",
            birthday = persona?.birthday,
            lastShown = savedHint ?: ""
        )
        prefs.edit()
            .putString(KEY_HINT_TEXT, newHint)
            .putString(KEY_HINT_TYPE, "neutral")
            .putLong(KEY_HINT_TIME, System.currentTimeMillis())
            .putInt(KEY_HINT_OPEN_COUNT, 0)
            .apply()
        return newHint
    }

    fun resolveHintType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        return prefs.getString(KEY_HINT_TYPE, "neutral") ?: "neutral"
    }

    fun getServerHint(context: Context): HintResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val type = prefs.getString(KEY_SERVER_HINT_TYPE, null) ?: return null
        if (type == "none") return null
        return HintResult(
            type = type,
            widgetText = null,
            paymentId = null,
            name = prefs.getString(KEY_SERVER_HINT_NAME, null),
            amount = null,
            daysUntilDue = null,
            isOverdue = null,
            urgency = null,
            label = null,
            offerId = null,
            offerText = prefs.getString(KEY_SERVER_HINT_OFFER_TEXT, null),
            offerCta = null,
            offerAction = null,
        )
    }

    fun fetchAndApply(context: Context) {
        val personaId = SessionManager.getPersonaId(context) ?: return
        scope.launch {
            try {
                val hint = MockApiService.getHint(context, personaId)
                val prefs = context.getSharedPreferences(PREFS_NAME, 0)

                if (hint != null) {
                    val hintType = hint.type
                    val widgetText = hint.widgetText
                        ?: WidgetHintTexts.toWidgetText(hint)

                    prefs.edit()
                        .putString(KEY_SERVER_HINT_TYPE, hintType)
                        .putString(KEY_SERVER_HINT_NAME, hint.name ?: "")
                        .putString(KEY_SERVER_HINT_OFFER_TEXT, hint.offerText ?: "")
                        .apply()

                    if (widgetText != null) {
                        prefs.edit()
                            .putString(KEY_HINT_TEXT, widgetText)
                            .putString(KEY_HINT_TYPE, hintType)
                            .putLong(KEY_HINT_TIME, System.currentTimeMillis())
                            .putInt(KEY_HINT_OPEN_COUNT, 0)
                            .apply()

                        Timber.d("hint-repo: server hint applied type=%s text=%s", hintType, widgetText)
                        VitaWidgetProvider.updatePrompt(context, widgetText)
                    } else {
                        Timber.d("hint-repo: server hint type=%s, widgetText=null, keeping current", hintType)
                    }
                } else {
                    prefs.edit()
                        .putString(KEY_SERVER_HINT_TYPE, "none")
                        .putString(KEY_SERVER_HINT_NAME, "")
                        .putString(KEY_SERVER_HINT_OFFER_TEXT, "")
                        .apply()

                    Timber.d("hint-repo: no server hint, keeping current")
                }
            } catch (e: Exception) {
                Timber.w(e, "hint-repo: failed to fetch hint")
            }
        }
    }

    fun incrementOpenCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val count = prefs.getInt(KEY_HINT_OPEN_COUNT, 0)
        prefs.edit().putInt(KEY_HINT_OPEN_COUNT, count + 1).apply()
    }

    fun saveHintToPrefs(context: Context, hint: HintResult, widgetText: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        prefs.edit()
            .putString(KEY_HINT_TEXT, widgetText)
            .putString(KEY_HINT_TYPE, hint.type)
            .putString(KEY_SERVER_HINT_TYPE, hint.type)
            .putString(KEY_SERVER_HINT_NAME, hint.name ?: "")
            .putString(KEY_SERVER_HINT_OFFER_TEXT, hint.offerText ?: "")
            .putLong(KEY_HINT_TIME, System.currentTimeMillis())
            .putInt(KEY_HINT_OPEN_COUNT, 0)
            .apply()
    }
}