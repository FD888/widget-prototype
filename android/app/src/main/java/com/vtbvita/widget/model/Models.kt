package com.vtbvita.widget.model

import org.json.JSONArray
import org.json.JSONObject

data class AccountInfo(
    val id: String,
    val name: String,
    val masked: String,
    val balance: Double,
    val type: String,
    val paymentSystem: String = "mir",
    val currency: String = "RUB"
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
    val requiresManualInput: Boolean,
    val comment: String? = null
)

data class HintResult(
    val type: String,           // "reminder" | "vygoda" | "custom" | "none"
    val widgetText: String? = null,
    val paymentId: String?,
    val name: String?,
    val amount: Double?,
    val daysUntilDue: Int?,
    val isOverdue: Boolean?,
    val urgency: String?,
    val label: String?,
    val paymentType: String? = null,
    val offerId: String?,
    val offerText: String?,
    val offerCta: String?,
    val offerAction: String?
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
            put("payment_system", a.paymentSystem); put("currency", a.currency)
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
                type = getString("type"),
                paymentSystem = optString("payment_system", "mir"),
                currency = optString("currency", "RUB")
            )
        }
    }
}
