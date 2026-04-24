package com.vtbvita.widget.personalization

import java.util.Calendar

object NeutralHints {

    private val MORNING = listOf(
        "доброе утро!",
        "с добрым утром",
        "хорошего дня впереди"
    )

    private val DAY = listOf(
        "на связи, если что",
        "чем могу помочь?",
        "всё под контролем",
        "привет, я тут",
        "как дела?"
    )

    private val EVENING = listOf(
        "добрый вечер!",
        "как прошёл день?",
        "на связи"
    )

    private val NIGHT = listOf(
        "я на связи, если что",
        "тихо сегодня",
        "всё под контролем"
    )

    private val HOLIDAYS_ANY = mapOf(
        "12-31" to listOf("с наступающим!", "скоро новый год"),
        "01-01" to listOf("с новым годом!", "счастливого нового года"),
        "05-09" to listOf("с днём победы", "с праздником"),
        "06-12" to listOf("с днём России"),
        "09-01" to listOf("с новым учебным годом", "первое сентября")
    )

    private val HOLIDAYS_MALE = mapOf(
        "02-23" to listOf("с днём защитника!", "с праздником")
    )

    private val HOLIDAYS_FEMALE = mapOf(
        "03-08" to listOf("с 8 марта!", "с весенним праздником")
    )

    private val BIRTHDAY_TODAY = listOf("с днём рождения!", "сегодня твой день")
    private const val BIRTHDAY_TOMORROW = "завтра день рождения"

    fun pick(
        gender: String = "male",
        birthday: String? = null,
        lastShown: String = ""
    ): String {
        val now = Calendar.getInstance()
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val todayKey = "%02d-%02d".format(month, day)

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowKey = "%02d-%02d".format(
            tomorrow.get(Calendar.MONTH) + 1,
            tomorrow.get(Calendar.DAY_OF_MONTH)
        )

        if (birthday != null) {
            if (birthday == todayKey) return pickFrom(BIRTHDAY_TODAY, lastShown)
            if (birthday == tomorrowKey) return BIRTHDAY_TOMORROW
        }

        val genderHolidays = if (gender == "female") HOLIDAYS_FEMALE else HOLIDAYS_MALE
        genderHolidays[todayKey]?.let { return pickFrom(it, lastShown) }

        HOLIDAYS_ANY[todayKey]?.let { return pickFrom(it, lastShown) }

        val pool = when (hour) {
            in 6..10  -> MORNING
            in 11..16 -> DAY
            in 17..21 -> EVENING
            else      -> NIGHT
        }
        return pickFrom(pool, lastShown)
    }

    private fun pickFrom(pool: List<String>, exclude: String): String {
        val filtered = pool.filter { it != exclude }
        return (if (filtered.isEmpty()) pool else filtered).random()
    }
}