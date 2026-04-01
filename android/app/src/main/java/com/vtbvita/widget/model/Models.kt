package com.vtbvita.widget.model

import org.json.JSONArray
import org.json.JSONObject

data class AccountInfo(
    val id: String,
    val name: String,
    val masked: String,
    val balance: Double,
    val type: String
)

data class ConfirmationData(
    val transactionId: String,
    val intent: String,
    val title: String,
    val subtitle: String?,
    val amount: Double,
    val sourceAccounts: List<AccountInfo>,
    val defaultAccountId: String,
    val recipientDisplayName: String?,
    val recipientPhone: String?,
    val recipientBanks: List<String>,
    val topupPhone: String?,
    val operator: String?,
    val requiresManualInput: Boolean
)

data class OperationResult(
    val status: String,
    val title: String,
    val message: String,
    val balanceAfter: Double?
)

// ---------- JSON helpers ----------

fun accountsToJson(accounts: List<AccountInfo>): String {
    val arr = JSONArray()
    accounts.forEach { a ->
        arr.put(JSONObject().apply {
            put("id", a.id); put("name", a.name); put("masked", a.masked)
            put("balance", a.balance); put("type", a.type)
        })
    }
    return arr.toString()
}

fun accountsFromJson(json: String): List<AccountInfo> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        arr.getJSONObject(i).run {
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
