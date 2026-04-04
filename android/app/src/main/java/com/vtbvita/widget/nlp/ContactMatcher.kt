package com.vtbvita.widget.nlp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactCandidate(
    val displayName: String,
    val phone: String,
    val bankDisplayName: String,
    val score: Float
)

/**
 * Локальный нечёткий поиск контактов по тексту из NLP (с учётом русских склонений).
 *
 * "паше коноплеву" → токены ["паш", "коноплев"] → поиск в ContactsContract → scored list
 */
object ContactMatcher {

    private val ENDINGS = Regex("(ого|его|ому|ему|ой|ей|ом|ем|ью|ую|ю|я|е|у|а|ы|и)$")

    /** Ищет кандидатов в книге контактов. Возвращает пустой список если нет разрешения. */
    fun search(rawQuery: String, context: Context): List<ContactCandidate> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val tokens = tokenize(rawQuery)
        if (tokens.isEmpty()) return emptyList()

        val raw = queryByTokens(context, tokens)

        val best = mutableMapOf<String, Pair<RawContact, Float>>()
        for (c in raw) {
            val s = score(c, tokens)
            if (s >= 0.4f) {
                val prev = best[c.phone]
                if (prev == null || prev.second < s) best[c.phone] = c to s
            }
        }

        return best.values
            .sortedByDescending { it.second }
            .map { (c, s) ->
                ContactCandidate(
                    displayName = c.name,
                    phone = c.phone,
                    bankDisplayName = makeBankName(c.name),
                    score = s
                )
            }
    }

    /** True если первый кандидат явно лучше остальных → однозначное совпадение. */
    fun isHighConfidence(candidates: List<ContactCandidate>): Boolean {
        if (candidates.isEmpty()) return false
        if (candidates.size == 1) return candidates[0].score >= 0.4f
        return candidates[0].score >= 0.8f && (candidates[0].score - candidates[1].score) >= 0.3f
    }

    /** Маскированный телефон для отображения: +7 (918) ***-**-11 */
    fun maskPhone(phone: String): String {
        val d = phone.filter { it.isDigit() }
        val normalized = when {
            d.length == 11 -> d.drop(1)
            d.length == 10 -> d
            else -> return phone
        }
        return "+7 (${normalized.substring(0, 3)}) ***-**-${normalized.substring(8, 10)}"
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private data class RawContact(val name: String, val phone: String)

    private fun tokenize(raw: String): List<String> =
        raw.lowercase().replace('ё', 'е').trim()
            .split(Regex("\\s+"))
            .map { stripEndings(it) }
            .filter { it.length >= 2 }

    private fun stripEndings(word: String): String {
        val stripped = ENDINGS.replace(word, "")
        return if (stripped.length >= 2) stripped else word
    }

    private fun queryByTokens(context: Context, tokens: List<String>): List<RawContact> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<RawContact>()

        for (token in tokens) {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$token%"),
                null
            ) ?: continue

            cursor.use {
                val ni = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val pi = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(ni) ?: continue
                    val phone = it.getString(pi)?.replace(Regex("[\\s\\-()]"), "") ?: continue
                    if (seen.add("$name|$phone")) result.add(RawContact(name, phone))
                }
            }
        }
        return result
    }

    private fun score(contact: RawContact, tokens: List<String>): Float {
        val parts = contact.name.lowercase().replace('ё', 'е')
            .split(Regex("\\s+"))
            .map { stripEndings(it) }

        var total = 0f
        for ((qi, token) in tokens.withIndex()) {
            for ((pi, part) in parts.withIndex()) {
                if (part.contains(token) || token.contains(part)) {
                    total += when {
                        qi == 0 && pi == 0 -> 0.5f  // первое слово запроса = первое слово имени
                        qi == 1 && pi == 1 -> 0.4f  // второе слово запроса = второе слово имени
                        else -> 0.1f
                    }
                }
            }
        }
        return total.coerceAtMost(1f)
    }

    /** "Паша Коноплев СПБГУ" → "Паша К."  |  "мама" → "Клиент ВТБ" */
    private fun makeBankName(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) "${parts[0]} ${parts[1].first().uppercaseChar()}."
        else "Клиент ВТБ"
    }
}
