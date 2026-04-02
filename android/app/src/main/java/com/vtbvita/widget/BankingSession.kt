package com.vtbvita.widget

/**
 * In-memory banking session. Stores the JWT banking token returned by /auth.
 * Lives only in RAM — cleared automatically on process kill or InputActivity.onPause().
 */
object BankingSession {
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
}
