package com.vtbvita.widget

import android.content.Intent

/**
 * In-memory banking session. Stores the JWT banking token returned by /auth.
 * Lives only in RAM — cleared automatically on process kill or InputActivity.onPause().
 *
 * Передача токена дочерним Activity: перед startActivity() вызови putInIntent(),
 * в дочерней Activity.onCreate() вызови restoreFromIntent(intent).
 */
object BankingSession {
    private const val EXTRA_TOKEN = "banking_session_token"
    private const val EXTRA_EXPIRES_AT = "banking_session_expires_at"

    private var token: String? = null
    private var expiresAt: Long = 0L

    fun save(token: String, expiresInSeconds: Int) {
        this.token = token
        this.expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L
    }

    fun getToken(): String? =
        if (System.currentTimeMillis() < expiresAt) token else null

    fun isValid(): Boolean = getToken() != null

    fun clear() {
        token = null
        expiresAt = 0L
    }

    /** Кладёт токен в Intent, чтобы дочерняя Activity могла восстановить сессию. */
    fun putInIntent(intent: Intent) {
        val t = token ?: return
        intent.putExtra(EXTRA_TOKEN, t)
        intent.putExtra(EXTRA_EXPIRES_AT, expiresAt)
    }

    /** Восстанавливает сессию из Intent (вызывать в onCreate дочерней Activity). */
    fun restoreFromIntent(intent: Intent?) {
        val t = intent?.getStringExtra(EXTRA_TOKEN) ?: return
        val exp = intent.getLongExtra(EXTRA_EXPIRES_AT, 0L)
        if (System.currentTimeMillis() < exp) {
            token = t
            expiresAt = exp
        }
    }
}
