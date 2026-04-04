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

    // Падежные окончания — срезаем, чтобы "маме" → "мам", "коноплеву" → "коноплев"
    private val ENDINGS = Regex("(ого|его|ому|ему|ой|ей|ом|ем|ью|ую|ю|я|е|у|а|ы|и)$")

    /**
     * Ищет кандидатов в книге контактов.
     * Применяет буст из ContactMemory — предыдущие выборы пользователя повышают score.
     */
    fun search(rawQuery: String, context: Context): List<ContactCandidate> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val tokens = tokenize(rawQuery)
        if (tokens.isEmpty()) return emptyList()

        // Грузим все контакты и скорим в Kotlin — избегаем проблем с LIKE и кириллицей
        val allContacts = queryAllContacts(context)

        val best = mutableMapOf<String, Pair<RawContact, Float>>()
        for (c in allContacts) {
            val s = score(c, tokens)
            if (s >= 0.4f) {
                val prev = best[c.phone]
                if (prev == null || prev.second < s) best[c.phone] = c to s
            }
        }

        // Применяем буст из истории выборов
        val pickCounts = ContactMemory.getPickCounts(rawQuery, context)

        return best.values
            .map { (c, s) ->
                val boost = ContactMemory.scoreBoost(pickCounts[c.phone] ?: 0)
                ContactCandidate(
                    displayName = c.name,
                    phone = c.phone,
                    bankDisplayName = makeBankName(c.name),
                    score = (s + boost).coerceAtMost(1f)
                )
            }
            .sortedByDescending { it.score }
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

    /** Загружает все контакты телефона за один запрос (без фильтрации). */
    private fun queryAllContacts(context: Context): List<RawContact> {
        val result = mutableListOf<RawContact>()
        val seen = mutableSetOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: return result

        cursor.use {
            val ni = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val pi = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(ni) ?: continue
                val phone = it.getString(pi)?.replace(Regex("[\\s\\-()]"), "") ?: continue
                if (seen.add("$name|$phone")) result.add(RawContact(name, phone))
            }
        }
        return result
    }

    /**
     * Jaccard-подобный scoring: matched_pairs / max(query_tokens, contact_parts).
     *
     * Примеры (запрос "маме" → ["мам"]):
     *   "Мама"       → 1/max(1,1) = 1.0
     *   "Мама Саши"  → 1/max(1,2) = 0.5   ← правильно разрешается как неоднозначное
     *   "Мама Игоря" → 1/max(1,2) = 0.5
     *
     * Примеры (запрос "маше коноплевой" → ["маш","коноплев"]):
     *   "Маша Коноплева" → 2/max(2,2) = 1.0
     *   "Маша"           → 1/max(2,1) = 0.5
     */
    private fun score(contact: RawContact, tokens: List<String>): Float {
        val parts = contact.name.lowercase().replace('ё', 'е')
            .split(Regex("\\s+"))
            .map { stripEndings(it) }
            .filter { it.length >= 2 }

        if (parts.isEmpty()) return 0f

        var matchedPairs = 0
        val usedParts = mutableSetOf<Int>()

        for (token in tokens) {
            for ((pi, part) in parts.withIndex()) {
                if (pi in usedParts) continue
                if (part.contains(token) || token.contains(part)) {
                    matchedPairs++
                    usedParts.add(pi)
                    break
                }
            }
        }

        return matchedPairs.toFloat() / maxOf(tokens.size, parts.size).toFloat()
    }

    /** "Паша Коноплев СПБГУ" → "Паша К."  |  "мама" → "Клиент ВТБ" */
    private fun makeBankName(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) "${parts[0]} ${parts[1].first().uppercaseChar()}."
        else "Клиент ВТБ"
    }
}
