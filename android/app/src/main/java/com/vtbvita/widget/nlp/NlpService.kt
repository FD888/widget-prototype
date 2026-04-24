package com.vtbvita.widget.nlp

import com.vtbvita.widget.BuildConfig
import com.vtbvita.widget.SessionManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ParsedIntent(
    val intent: String,           // transfer|balance|topup|open_app|alarm|timer|call|navigate|unknown
    val recipient: String? = null,
    val amount: Double? = null,
    val phone: String? = null,
    val app: String? = null,      // telegram|youtube|vk|...
    val hour: Int? = null,
    val minute: Int? = null,
    val durationSeconds: Int? = null,
    val contact: String? = null,
    val destination: String? = null,
    val comment: String? = null,
    val botRedirect: Boolean = false,
    val originalText: String? = null,
    val confidence: Double = 0.9,
)

object NlpService {

    suspend fun parse(text: String, context: Context): Result<ParsedIntent> =
        withContext(Dispatchers.IO) {
            runCatching {
                Timber.d("NLP parse: text='%s'", text)
                val appToken = SessionManager.getAppToken(context)
                val body = JSONObject().apply { put("text", text) }
                val conn = URL("${BuildConfig.MOCK_API_BASE_URL}/parse")
                    .openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    appToken?.let { setRequestProperty("X-Api-Key", it) }
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8)
                    .use { it.write(body.toString()) }

                val json = JSONObject(conn.inputStream.bufferedReader().readText())

                val intentStr = json.optString("intent", "unknown")
                Timber.i("NLP result: intent=%s confidence=%.2f", intentStr, json.optDouble("confidence", 0.0))
                ParsedIntent(
                    intent          = intentStr,
                    recipient       = json.optString("recipient").takeIf { it.isNotEmpty() },
                    amount          = json.optDouble("amount").takeIf { !it.isNaN() },
                    phone           = json.optString("phone").takeIf { it.isNotEmpty() },
                    app             = json.optString("app").takeIf { it.isNotEmpty() },
                    hour            = json.optInt("hour").takeIf { json.has("hour") && !json.isNull("hour") },
                    minute          = json.optInt("minute").takeIf { json.has("minute") && !json.isNull("minute") },
                    durationSeconds = json.optInt("duration_seconds").takeIf { json.has("duration_seconds") && !json.isNull("duration_seconds") },
                    contact         = json.optString("contact").takeIf { it.isNotEmpty() },
                    destination     = json.optString("destination").takeIf { it.isNotEmpty() },
                    comment         = json.optString("comment").takeIf { it.isNotEmpty() },
                    botRedirect     = json.optBoolean("bot_redirect", false),
                    originalText    = json.optString("original_text").takeIf { it.isNotEmpty() },
                    confidence      = json.optDouble("confidence", 0.9),
                )
            }
        }
}
