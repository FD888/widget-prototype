package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract

/**
 * Парсит свободный текст и возвращает системный Intent если распознал команду.
 * Вызывается до банковской логики — приоритет системных действий выше.
 *
 * Поддерживает:
 *  - Будильник: "поставь будильник на 7 утра", "разбуди в 8:30"
 *  - Таймер:    "таймер на 10 минут", "через 30 минут напомни"
 *  - Открыть приложение: "открой spotify", "запусти карты"
 *  - Звонок:   "позвони маше", "набери +7 916..."
 */
object SystemIntentHandler {

    fun parse(text: String, context: Context): Intent? {
        val t = text.trim().lowercase()
        return tryAlarm(t)
            ?: tryTimer(t)
            ?: tryOpenApp(t, context)
            ?: tryCall(t, context)
    }

    // ── Будильник ─────────────────────────────────────────────────────────────

    private val alarmTriggers = listOf(
        "будильник", "разбуди", "буди", "просыпаться", "поставь на"
    )

    private fun tryAlarm(text: String): Intent? {
        if (alarmTriggers.none { text.contains(it) }) return null

        val (hour, minute) = parseTime(text) ?: return Intent().apply {
            action = AlarmClock.ACTION_SHOW_ALARMS  // нет времени — просто открываем список
        }

        return Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    // ── Таймер ────────────────────────────────────────────────────────────────

    private val timerTriggers = listOf("таймер", "через", "напомни через", "минут", "ремайндер")

    private fun tryTimer(text: String): Intent? {
        if (timerTriggers.none { text.contains(it) }) return null

        // "через 10 минут" / "таймер 1 час 30 минут"
        val totalSeconds = parseDuration(text) ?: return null

        return Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, totalSeconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
    }

    // ── Открыть приложение ────────────────────────────────────────────────────

    private val openTriggers = listOf("открой", "запусти", "включи", "покажи")

    // Ключевые слова → package name
    private val appMap = mapOf(
        "spotify"         to "com.spotify.music",
        "спотифай"        to "com.spotify.music",
        "youtube"         to "com.google.android.youtube",
        "ютуб"            to "com.google.android.youtube",
        "карты"           to "ru.yandex.yandexmaps",
        "яндекс карты"    to "ru.yandex.yandexmaps",
        "2гис"            to "ru.dublgis.dgismobile",
        "2 гис"           to "ru.dublgis.dgismobile",
        "telegram"        to "org.telegram.messenger",
        "телеграм"        to "org.telegram.messenger",
        "whatsapp"        to "com.whatsapp",
        "ватсап"          to "com.whatsapp",
        "вотсап"          to "com.whatsapp",
        "инстаграм"       to "com.instagram.android",
        "instagram"       to "com.instagram.android",
        "вк"              to "com.vkontakte.android",
        "вконтакте"       to "com.vkontakte.android",
        "камера"          to "android.media.action.IMAGE_CAPTURE",
        "калькулятор"     to "com.android.calculator2",
        "настройки"       to "android.provider.Settings.ACTION_SETTINGS",
        "погода"          to "ru.yandex.weatherplugin",
        "втб"             to "ru.vtb24.mobilebanking.android",
    )

    private fun tryOpenApp(text: String, context: Context): Intent? {
        if (openTriggers.none { text.contains(it) }) return null

        // Ищем совпадение по ключевым словам
        val matched = appMap.entries
            .filter { (keyword, _) -> text.contains(keyword) }
            .maxByOrNull { (keyword, _) -> keyword.length } // берём самое длинное совпадение

        if (matched != null) {
            val pkg = matched.value
            val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) return launch
        }

        // Не нашли — открываем поиск в Play Store / список приложений
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
    }

    // ── Звонок ────────────────────────────────────────────────────────────────

    private val callTriggers = listOf("позвони", "набери", "звонок", "позвоните", "вызови")

    private fun tryCall(text: String, context: Context): Intent? {
        if (callTriggers.none { text.contains(it) }) return null

        // Ищем номер телефона в тексте
        val phoneRegex = Regex("[+7|8][\\s\\-]?\\(?\\d{3}\\)?[\\s\\-]?\\d{3}[\\s\\-]?\\d{2}[\\s\\-]?\\d{2}")
        val phone = phoneRegex.find(text)?.value?.replace(Regex("[\\s\\-()]"), "")

        if (phone != null) {
            return Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        }

        // Имя — ищем в контактах
        val triggerWord = callTriggers.firstOrNull { text.contains(it) } ?: return null
        val nameQuery = text.substringAfter(triggerWord).trim()
            .replace(Regex("^(на|в|по)\\s+"), "")
            .trim()

        if (nameQuery.isNotBlank()) {
            val contactPhone = findContactPhone(context, nameQuery)
            if (contactPhone != null) {
                return Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contactPhone"))
            }
        }

        // Ничего не нашли — открываем набор номера
        return Intent(Intent.ACTION_DIAL)
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    /**
     * Парсит время из строки. Возвращает Pair(hour, minute).
     * Примеры: "на 7 утра" → (7,0), "в 19:30" → (19,30), "в 8:00 вечера" → (20,0)
     */
    private fun parseTime(text: String): Pair<Int, Int>? {
        val timeRegex = Regex("(?:в|на)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(утра|вечера|ночи|дня)?")
        val match = timeRegex.find(text) ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val period = match.groupValues[3]

        if (period == "вечера" || period == "дня") {
            if (hour < 12) hour += 12
        } else if (period == "ночи") {
            if (hour == 12) hour = 0
        }

        return if (hour in 0..23 && minute in 0..59) Pair(hour, minute) else null
    }

    /**
     * Парсит длительность. Возвращает секунды.
     * Примеры: "через 10 минут" → 600, "1 час 30 минут" → 5400, "2 часа" → 7200
     */
    private fun parseDuration(text: String): Int? {
        var total = 0
        val hoursMatch = Regex("(\\d+)\\s*час").find(text)
        val minsMatch = Regex("(\\d+)\\s*мин").find(text)
        val secsMatch = Regex("(\\d+)\\s*сек").find(text)

        hoursMatch?.groupValues?.get(1)?.toIntOrNull()?.let { total += it * 3600 }
        minsMatch?.groupValues?.get(1)?.toIntOrNull()?.let { total += it * 60 }
        secsMatch?.groupValues?.get(1)?.toIntOrNull()?.let { total += it }

        return if (total > 0) total else null
    }

    private fun findContactPhone(context: Context, nameQuery: String): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameQuery%"),
            null
        ) ?: return null

        return cursor.use {
            if (it.moveToFirst()) {
                val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                it.getString(phoneIdx)?.replace(Regex("[\\s\\-()]"), "")
            } else null
        }
    }
}
