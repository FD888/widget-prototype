package com.vtbvita.widget.nlp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Хранит историю выборов пользователя при disambiguation контактов.
 *
 * Ключ — нормализованный recipient_raw из NLP ("маме", "кате сестре" и т.д.).
 * Значение — список {phone, count}.
 *
 * Прогрессия буста:
 *   count=1 → +0.2 (disambiguation, контакт первый)
 *   count=2 → +0.3 (disambiguation, контакт первый)
 *   count≥3 → +0.5 (score ≥ 0.8 → disambiguation пропускается)
 */
object ContactMemory {

    private const val PREFS = "vita_contact_memory"
    private const val KEY_DATA = "picks"
    const val AUTO_RESOLVE_AT = 3   // сколько выборов нужно для автоперехода

    /** Нормализует ключ: "Маме" → "маме", "Кате Сестре" → "кате сестре" */
    fun normalizeKey(recipientRaw: String): String =
        recipientRaw.lowercase().replace('ё', 'е').trim()

    /**
     * Записывает выбор пользователя.
     * Вызывается из ContactDisambiguationActivity при тапе на контакт.
     */
    fun recordPick(recipientRaw: String, phone: String, context: Context) {
        val key = normalizeKey(recipientRaw)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = prefs.getString(KEY_DATA, "{}").parseJsonObject()

        val arr: JSONArray = if (root.has(key)) root.getJSONArray(key) else JSONArray()

        var found = false
        for (i in 0 until arr.length()) {
            val entry = arr.getJSONObject(i)
            if (entry.getString("phone") == phone) {
                entry.put("count", entry.getInt("count") + 1)
                found = true
                break
            }
        }
        if (!found) arr.put(JSONObject().apply { put("phone", phone); put("count", 1) })

        root.put(key, arr)
        prefs.edit().putString(KEY_DATA, root.toString()).apply()
    }

    /**
     * Возвращает map: phone → pick_count для данного запроса.
     * Пустой map если запрос ещё не встречался.
     */
    fun getPickCounts(recipientRaw: String, context: Context): Map<String, Int> {
        val key = normalizeKey(recipientRaw)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = prefs.getString(KEY_DATA, "{}").parseJsonObject()
        if (!root.has(key)) return emptyMap()

        val arr = root.getJSONArray(key)
        return buildMap {
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                put(e.getString("phone"), e.getInt("count"))
            }
        }
    }

    /** Буст к score в зависимости от количества выборов. */
    fun scoreBoost(count: Int): Float = when {
        count >= AUTO_RESOLVE_AT -> 0.5f  // пробивает порог 0.8 даже при base=0.5
        count == 2 -> 0.3f
        count == 1 -> 0.2f
        else -> 0f
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun String?.parseJsonObject(): JSONObject =
        runCatching { JSONObject(this ?: "{}") }.getOrDefault(JSONObject())
}
