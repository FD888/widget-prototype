package com.vtbvita.widget.api

import android.content.Context
import com.vtbvita.widget.BankingSession
import com.vtbvita.widget.BuildConfig
import com.vtbvita.widget.SessionManager
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.model.HintResult
import com.vtbvita.widget.model.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class BankingTokenResult(val token: String, val expiresInSeconds: Int)

/**
 * HTTP-клиент к Mock API (ml/mock_api/main.py).
 * Базовый URL берётся из BuildConfig.MOCK_API_BASE_URL.
 *
 * Уровни авторизации:
 *   /verify-phone  — публичный, без заголовка
 *   /auth          — X-Api-Key: <app_token>    → выдаёт banking JWT
 *   /balance /command /confirm — X-Banking-Token: <banking_token>
 */
object MockApiService {

    private val baseUrl: String get() = BuildConfig.MOCK_API_BASE_URL

    /** POST /verify-phone — верификация номера телефона при первом запуске. */
    suspend fun verifyPhone(phone: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply { put("phone", phone) }
            val json = post("/verify-phone", body)  // публичный эндпоинт
            json.getString("app_token")
        }
    }

    /** POST /auth — валидация PIN → banking JWT (15 мин). */
    suspend fun auth(pin: String, context: Context): Result<BankingTokenResult> = withContext(Dispatchers.IO) {
        runCatching {
            val appToken = SessionManager.getAppToken(context)
                ?: throw Exception("Не авторизован")
            val body = JSONObject().apply { put("pin", pin) }
            val json = post("/auth", body, apiKey = appToken)
            BankingTokenResult(
                token = json.getString("banking_token"),
                expiresInSeconds = json.getInt("expires_in_seconds")
            )
        }
    }

    /** POST /auth/biometric — аутентификация через биометрию (без PIN). */
    suspend fun authBiometric(context: Context, userId: String = "vitya"): Result<BankingTokenResult> = withContext(Dispatchers.IO) {
        runCatching {
            val appToken = SessionManager.getAppToken(context)
                ?: throw Exception("Не авторизован")
            val body = JSONObject().apply { put("user_id", userId) }
            val json = post("/auth/biometric", body, apiKey = appToken)
            BankingTokenResult(
                token = json.getString("banking_token"),
                expiresInSeconds = json.getInt("expires_in_seconds")
            )
        }
    }

    /** GET /balance — список счетов. Требует banking JWT. */
    suspend fun getBalance(context: Context): List<AccountInfo> = withContext(Dispatchers.IO) {
        val token = BankingSession.getToken() ?: throw Exception("Требуется PIN")
        get("/balance", bankingToken = token).getJSONArray("accounts").toAccountList()
    }

    /** POST /command — создаёт pending-операцию, возвращает данные для модала. */
    suspend fun command(
        intent: String,
        amount: Double,
        recipient: String?,
        phone: String?,
        context: Context,
        comment: String? = null
    ): ConfirmationData = withContext(Dispatchers.IO) {
        val token = BankingSession.getToken() ?: throw Exception("Требуется PIN")
        val body = JSONObject().apply {
            put("intent", intent)
            put("amount", amount)
            recipient?.let { put("recipient", it) }
            phone?.let { put("phone", it) }
            comment?.let { put("comment", it) }
        }
        val json = post("/command", body, bankingToken = token)

        val banks = json.optJSONArray("recipient_banks")
            ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            ?: emptyList()

        ConfirmationData(
            transactionId = json.getString("transaction_id"),
            intent = json.getString("intent"),
            title = json.getString("title"),
            subtitle = json.optString("subtitle").takeIf { it.isNotEmpty() },
            amount = json.optDouble("amount", 0.0),
            sourceAccounts = json.getJSONArray("source_accounts").toAccountList(),
            defaultAccountId = json.getString("default_account_id"),
            recipientDisplayName = json.optString("recipient_display_name").takeIf { it.isNotEmpty() },
            recipientPhone = json.optString("recipient_phone").takeIf { it.isNotEmpty() },
            recipientBanks = banks,
            topupPhone = json.optString("topup_phone").takeIf { it.isNotEmpty() },
            operator = json.optString("operator").takeIf { it.isNotEmpty() },
            requiresManualInput = json.optBoolean("requires_manual_input", false),
            comment = json.optString("comment").takeIf { it.isNotEmpty() }
        )
    }

    /**
     * GET /hint — ближайшее напоминание о платеже для пользователя.
     * Требует только app_token (без banking JWT).
     * Возвращает null если нет активных напоминаний или запрос не удался.
     */
    suspend fun getHint(context: Context, userId: String): HintResult? = withContext(Dispatchers.IO) {
        runCatching {
            val appToken = SessionManager.getAppToken(context)
            if (appToken == null) {
                android.util.Log.e("MockApi", "getHint: appToken is null")
                return@runCatching null
            }
            val json = get("/hint?user_id=$userId", apiKey = appToken)
            android.util.Log.d("MockApi", "getHint response: type=${json.optString("type")} widget_text=${json.optString("widget_text")}")
            if (json.getString("type") == "none") return@runCatching null
            HintResult(
                type        = json.getString("type"),
                widgetText  = json.optString("widget_text").takeIf { it.isNotEmpty() },
                paymentId   = json.optString("payment_id").takeIf { it.isNotEmpty() },
                name        = json.optString("name").takeIf { it.isNotEmpty() },
                amount      = json.optDouble("amount").takeIf { !it.isNaN() },
                daysUntilDue = json.optInt("days_until_due").takeIf { json.has("days_until_due") && !json.isNull("days_until_due") },
                isOverdue   = json.optBoolean("is_overdue").takeIf { json.has("is_overdue") && !json.isNull("is_overdue") },
                urgency     = json.optString("urgency").takeIf { it.isNotEmpty() },
                label       = json.optString("label").takeIf { it.isNotEmpty() },
                paymentType = json.optString("payment_type").takeIf { it.isNotEmpty() },
                offerId     = json.optString("offer_id").takeIf { it.isNotEmpty() },
                offerText   = json.optString("offer_text").takeIf { it.isNotEmpty() },
                offerCta    = json.optString("offer_cta").takeIf { it.isNotEmpty() },
                offerAction = json.optString("offer_action").takeIf { it.isNotEmpty() },
            )
        }.onFailure { e ->
            android.util.Log.e("MockApi", "getHint FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
        }.getOrNull()
    }

    /** POST /command — pay_scheduled: оплата запланированного платежа. Требует banking JWT. */
    suspend fun commandPayScheduled(
        paymentId: String,
        amount: Double,
        context: Context
    ): ConfirmationData = withContext(Dispatchers.IO) {
        val token = BankingSession.getToken() ?: throw Exception("Требуется PIN")
        val body = JSONObject().apply {
            put("intent", "pay_scheduled")
            put("payment_id", paymentId)
            put("amount", amount)
        }
        val json = post("/command", body, bankingToken = token)
        val banks = json.optJSONArray("recipient_banks")
            ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            ?: emptyList()
        ConfirmationData(
            transactionId          = json.getString("transaction_id"),
            intent                 = json.getString("intent"),
            title                  = json.getString("title"),
            subtitle               = json.optString("subtitle").takeIf { it.isNotEmpty() },
            amount                 = json.optDouble("amount", 0.0),
            sourceAccounts         = json.getJSONArray("source_accounts").toAccountList(),
            defaultAccountId       = json.getString("default_account_id"),
            recipientDisplayName   = null,
            recipientPhone         = null,
            recipientBanks         = banks,
            topupPhone             = null,
            operator               = null,
            requiresManualInput    = false,
        )
    }

    /** POST /confirm/{id} — финализирует операцию. */
    suspend fun confirm(
        transactionId: String,
        sourceAccountId: String,
        selectedBank: String?,
        context: Context
    ): OperationResult = withContext(Dispatchers.IO) {
        val token = BankingSession.getToken() ?: throw Exception("Требуется PIN")
        val body = JSONObject().apply {
            put("source_account_id", sourceAccountId)
            selectedBank?.let { put("selected_bank", it) }
        }
        val json = post("/confirm/$transactionId", body, bankingToken = token)
        OperationResult(
            status = json.getString("status"),
            title = json.getString("title"),
            message = json.getString("message"),
            balanceAfter = json.optDouble("balance_after").takeIf { !it.isNaN() }
        )
    }

    // ---------- HTTP helpers ----------

    private fun get(
        path: String,
        apiKey: String? = null,
        bankingToken: String? = null
    ): JSONObject {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            apiKey?.let { setRequestProperty("X-Api-Key", it) }
            bankingToken?.let { setRequestProperty("X-Banking-Token", it) }
        }
        return conn.readResponse()
    }

    private fun post(
        path: String,
        body: JSONObject,
        apiKey: String? = null,
        bankingToken: String? = null
    ): JSONObject {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            apiKey?.let { setRequestProperty("X-Api-Key", it) }
            bankingToken?.let { setRequestProperty("X-Banking-Token", it) }
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        return conn.readResponse()
    }

    private fun HttpURLConnection.readResponse(): JSONObject {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.readText() ?: "{}"
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(text).getString("detail") }.getOrDefault(text)
            Timber.e("API error: %s %d — %s", url.path, code, detail)
            throw Exception(detail)
        }
        Timber.d("API ok: %s %d", url.path, code)
        return JSONObject(text)
    }

    private fun JSONArray.toAccountList(): List<AccountInfo> =
        (0 until length()).map { i ->
            getJSONObject(i).run {
                AccountInfo(
                    id = getString("id"),
                    name = getString("name"),
                    masked = getString("masked"),
                    balance = getDouble("balance"),
                    type = getString("type"),
                    paymentSystem = optString("payment_system", "mir"),
                    currency = optString("currency", "RUB")
                )
            }
        }
}
