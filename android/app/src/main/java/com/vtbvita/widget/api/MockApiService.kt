package com.vtbvita.widget.api

import android.content.Context
import com.vtbvita.widget.BankingSession
import com.vtbvita.widget.BuildConfig
import com.vtbvita.widget.SessionManager
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.model.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
        context: Context
    ): ConfirmationData = withContext(Dispatchers.IO) {
        val token = BankingSession.getToken() ?: throw Exception("Требуется PIN")
        val body = JSONObject().apply {
            put("intent", intent)
            put("amount", amount)
            recipient?.let { put("recipient", it) }
            phone?.let { put("phone", it) }
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
            requiresManualInput = json.optBoolean("requires_manual_input", false)
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
            throw Exception(detail)
        }
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
                    type = getString("type")
                )
            }
        }
}
