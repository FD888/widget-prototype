package com.vtbvita.widget.nlp

/**
 * NLP intent parser interface.
 *
 * Реализация: C-02 (Яна) — подключится к POST /parse на FastAPI.
 * До подключения C-02 InputActivity использует кнопочный UI без вызова этого интерфейса.
 *
 * Когда C-02 готов:
 *   1. Создать NlpServiceNetwork : NlpService, передать baseUrl FastAPI.
 *   2. В InputActivity добавить TextField + вызов nlpService.parse(text).
 *   3. Убрать кнопочный выбор intent.
 */
interface NlpService {
    suspend fun parse(text: String): ParsedIntent?
}

data class ParsedIntent(
    val intent: String,     // "transfer" | "balance" | "topup"
    val amount: Double?,
    val recipient: String?, // transfer: имя или телефон
    val phone: String?,     // topup: номер телефона
)

/** Заглушка — возвращает null пока C-02 не подключён. */
class NlpServiceStub : NlpService {
    override suspend fun parse(text: String): ParsedIntent? = null
}
