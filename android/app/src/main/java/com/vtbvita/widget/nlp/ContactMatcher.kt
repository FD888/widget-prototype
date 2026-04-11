package com.vtbvita.widget.nlp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import timber.log.Timber

data class ContactCandidate(
    val displayName: String,
    val phone: String,
    val bankDisplayName: String,
    val score: Float,
    val pickCount: Int = 0
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
        Timber.d("[ContactMatcher] query='$rawQuery' → tokens=$tokens")
        if (tokens.isEmpty()) return emptyList()

        // Грузим все контакты и скорим в Kotlin — избегаем проблем с LIKE и кириллицей
        val allContacts = queryAllContacts(context)
        Timber.d("[ContactMatcher] total contacts in phonebook: ${allContacts.size}")

        val best = mutableMapOf<String, Pair<RawContact, Float>>()
        for (c in allContacts) {
            val s = score(c, tokens)
            if (s >= 0.4f) {
                val prev = best[c.phone]
                if (prev == null || prev.second < s) best[c.phone] = c to s
            }
        }

        Timber.d("[ContactMatcher] candidates above 0.4 threshold: ${best.size}")
        best.values.forEach { (c, s) ->
            Timber.d("[ContactMatcher]   '${c.name}' (${c.phone}) → base_score=$s")
        }

        // Применяем буст из истории выборов
        val pickCounts = ContactMemory.getPickCounts(rawQuery, context)
        Timber.d("[ContactMatcher] pickCounts for key='${ContactMemory.normalizeKey(rawQuery)}': $pickCounts")

        return best.values
            .map { (c, s) ->
                val picks = pickCounts[c.phone] ?: 0
                val boost = ContactMemory.scoreBoost(picks)
                val finalScore = (s + boost).coerceAtMost(1f)
                Timber.d("[ContactMatcher]   '${c.name}' picks=$picks boost=$boost final=$finalScore")
                ContactCandidate(
                    displayName = c.name,
                    phone = c.phone,
                    bankDisplayName = makeBankName(c.name),
                    score = finalScore,
                    pickCount = picks
                )
            }
            .sortedWith(compareByDescending<ContactCandidate> { it.score }.thenByDescending { it.pickCount })
    }

    /** True если первый кандидат явно лучше остальных → однозначное совпадение. */
    fun isHighConfidence(candidates: List<ContactCandidate>): Boolean {
        if (candidates.isEmpty()) return false
        if (candidates.size == 1) {
            val result = candidates[0].score >= 0.4f
            Timber.d("[ContactMatcher] isHighConfidence: single candidate score=${candidates[0].score} → $result")
            return result
        }
        // Memory-based: пользователь достаточно раз выбирал этот контакт → авторезолв
        if (candidates[0].pickCount >= ContactMemory.AUTO_RESOLVE_AT) {
            Timber.d("[ContactMatcher] isHighConfidence: pickCount=${candidates[0].pickCount} >= ${ContactMemory.AUTO_RESOLVE_AT} → true (memory)")
            return true
        }
        val top = candidates[0].score
        val second = candidates[1].score
        val gap = top - second
        val result = top >= 0.8f && gap >= 0.3f
        Timber.d("[ContactMatcher] isHighConfidence: top=$top second=$second gap=$gap → $result")
        return result
    }

    /**
     * Срезает слабых кандидатов перед показом disambiguation.
     * Правило: показывать только тех, чей score >= topScore - 0.4, не более MAX_CANDIDATES.
     *
     * Примеры:
     *   ["Мама"=1.0, "Мама Арсика"=0.5, ...] → min=0.6 → только ["Мама"=1.0, "Мама"=1.0] (дубли)
     *   ["Маша"=0.8, "Маша Иванова"=0.7]     → min=0.4 → оба остаются
     */
    fun filterCandidates(sorted: List<ContactCandidate>): List<ContactCandidate> {
        if (sorted.isEmpty()) return sorted
        val minScore = (sorted[0].score - MAX_SHOW_GAP).coerceAtLeast(0.4f)
        return sorted.filter { it.score >= minScore }.take(MAX_CANDIDATES)
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

    private const val MAX_CANDIDATES = 5
    private const val MAX_SHOW_GAP = 0.4f  // максимальный разрыв с лидером для показа

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
                val phone = normalizePhone(it.getString(pi) ?: continue)
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

    /**
     * Нормализует номер телефона: убирает пробелы/тире, приводит 8/7/+7 к единому +7XXX-формату.
     * Это устраняет дубли "Мама" когда один номер сохранён как +79... а другой как 89...
     */
    private fun normalizePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8")) ->
                "+7${digits.drop(1)}"
            digits.length == 10 -> "+7$digits"
            else -> raw.replace(Regex("[\\s\\-()]"), "")
        }
    }

    /** "Паша Коноплев СПБГУ" → "Паша К."  |  "мама" → "Клиент ВТБ" */
    private fun makeBankName(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+"))
        return if (parts.size >= 2) "${parts[0]} ${parts[1].first().uppercaseChar()}."
        else "Клиент ВТБ"
    }
}
