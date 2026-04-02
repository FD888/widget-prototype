package com.vtbvita.widget.api

import com.vtbvita.widget.BuildConfig
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

/**
 * HTTP-клиент к Mock API (ml/mock_api/main.py).
 * Базовый URL берётся из BuildConfig.MOCK_API_BASE_URL,
 * который задаётся в local.properties → MOCK_API_BASE_URL=http://10.0.2.2:8000
 */
object MockApiService {

    private val baseUrl: String get() = BuildConfig.MOCK_API_BASE_URL

    /** GET /balance — список счетов без confirm. */
    suspend fun getBalance(): List<AccountInfo> = withContext(Dispatchers.IO) {
        get("/balance").getJSONArray("accounts").toAccountList()
    }

    /** POST /command — создаёт pending-операцию, возвращает данные для модала. */
    suspend fun command(
        intent: String,
        amount: Double,
        recipient: String?,
        phone: String?
    ): ConfirmationData = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("intent", intent)
            put("amount", amount)
            recipient?.let { put("recipient", it) }
            phone?.let { put("phone", it) }
        }
        val json = post("/command", body)

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
        selectedBank: String?
    ): OperationResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("source_account_id", sourceAccountId)
            selectedBank?.let { put("selected_bank", it) }
        }
        val json = post("/confirm/$transactionId", body)
        OperationResult(
            status = json.getString("status"),
            title = json.getString("title"),
            message = json.getString("message"),
            balanceAfter = json.optDouble("balance_after").takeIf { !it.isNaN() }
        )
    }

    // ---------- HTTP helpers ----------

    private fun get(path: String): JSONObject {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.apply { requestMethod = "GET"; connectTimeout = 5000; readTimeout = 5000 }
        return JSONObject(conn.inputStream.bufferedReader().readText())
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        conn.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
            connectTimeout = 5000
            readTimeout = 5000
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        return JSONObject(conn.inputStream.bufferedReader().readText())
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
