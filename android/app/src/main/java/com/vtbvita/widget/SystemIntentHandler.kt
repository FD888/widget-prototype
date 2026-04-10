package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import java.net.URLEncoder

/**
 * Исполнитель системных интентов.
 *
 * Раньше парсил текст сам — теперь только выполняет команды,
 * уже распознанные сервером (POST /parse → ParsedIntent).
 *
 * Вызывается из InputActivity после получения ParsedIntent от NlpService.
 */
object SystemIntentHandler {

    // Маппинг канонических имён приложений (от сервера) → package name
    private val appMap = mapOf(
        "telegram"      to "org.telegram.messenger",
        "whatsapp"      to "com.whatsapp",
        "vk"            to "com.vkontakte.android",
        "youtube"       to "com.google.android.youtube",
        "spotify"       to "com.spotify.music",
        "yandex_maps"   to "ru.yandex.yandexmaps",
        "yandex_music"  to "ru.yandex.yandexmaps",
        "instagram"     to "com.instagram.android",
        "tiktok"        to "com.zhiliaoapp.musically",
        "vtb"           to "ru.vtb24.mobilebanking.android",
        "sber"          to "ru.sberbankmobile",
        "tinkoff"       to "com.idamob.tinkoff.android",
    )

    /** Открыть приложение по каноническому имени от сервера. */
    fun openApp(appName: String, context: Context): Intent? {
        val pkg = appMap[appName.lowercase()] ?: return null
        return context.packageManager.getLaunchIntentForPackage(pkg)
    }

    /** Поставить будильник. */
    fun setAlarm(hour: Int, minute: Int): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

    /** Запустить таймер. */
    fun setTimer(durationSeconds: Int): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }

    /** Позвонить контакту по имени или номеру. */
    fun call(contact: String?, context: Context): Intent {
        if (contact != null) {
            // Если это номер телефона
            val digits = contact.filter { it.isDigit() }
            if (digits.length >= 7) {
                return Intent(Intent.ACTION_DIAL, Uri.parse("tel:$contact"))
            }
            // Если имя — ищем в контактах
            val phone = findContactPhone(context, contact)
            if (phone != null) {
                return Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            }
        }
        return Intent(Intent.ACTION_DIAL)
    }

    /**
     * Проложить маршрут до адреса/места.
     * Приоритет: Яндекс Карты (deep link) → стандартный geo: URI (любые карты).
     */
    fun navigate(destination: String, context: Context): Intent {
        val encoded = URLEncoder.encode(destination, "UTF-8")
        val yandexPkg = "ru.yandex.yandexmaps"
        val yandexInstalled = context.packageManager.getLaunchIntentForPackage(yandexPkg) != null
        return if (yandexInstalled) {
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("yandexmaps://maps.yandex.ru/?text=$encoded&rtt=mt")
            ).setPackage(yandexPkg)
        } else {
            // Открывает любое установленное приложение карт
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        }
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
                val idx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                it.getString(idx)?.replace(Regex("[\\s\\-()]"), "")
            } else null
        }
    }
}
