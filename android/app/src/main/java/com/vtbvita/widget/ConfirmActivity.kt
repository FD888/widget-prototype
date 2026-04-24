package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.model.accountsFromJson
import com.vtbvita.widget.model.accountsToJson
import com.vtbvita.widget.ui.components.OmegaBankCarousel
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaInfoCard
import com.vtbvita.widget.ui.components.OmegaSheetScaffold
import com.vtbvita.widget.ui.components.OmegaWarningCard
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

class ConfirmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        val data = unpackIntent(intent) ?: run { finish(); return }

        setContent {
            VTBVitaTheme {
                ConfirmBottomSheet(
                    data = data,
                    onDismiss = { finish() },
                    onSuccess = { msg ->
                        VitaWidgetProvider.showStatus(applicationContext, msg)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context, data: ConfirmationData): Intent =
            Intent(context, ConfirmActivity::class.java).apply {
                putExtra("txn_id", data.transactionId)
                putExtra("intent_type", data.intent)
                putExtra("title", data.title)
                putExtra("subtitle", data.subtitle)
                putExtra("amount", data.amount)
                putExtra("default_account_id", data.defaultAccountId)
                putExtra("recipient_name", data.recipientDisplayName)
                putExtra("recipient_phone", data.recipientPhone)
                putExtra("topup_phone", data.topupPhone)
                putExtra("operator_name", data.operator)
                putExtra("requires_manual", data.requiresManualInput)
                putExtra("banks_json", JSONArray(data.recipientBanks).toString())
                putExtra("accounts_json", accountsToJson(data.sourceAccounts))
            }

        private fun unpackIntent(intent: Intent): ConfirmationData? {
            val txnId = intent.getStringExtra("txn_id") ?: return null
            return ConfirmationData(
                transactionId = txnId,
                intent = intent.getStringExtra("intent_type") ?: "transfer",
                title = intent.getStringExtra("title") ?: "",
                subtitle = intent.getStringExtra("subtitle"),
                amount = intent.getDoubleExtra("amount", 0.0),
                defaultAccountId = intent.getStringExtra("default_account_id") ?: "debit",
                recipientDisplayName = intent.getStringExtra("recipient_name"),
                recipientPhone = intent.getStringExtra("recipient_phone"),
                recipientBanks = intent.getStringExtra("banks_json")
                    ?.let { json -> JSONArray(json).let { arr -> (0 until arr.length()).map { arr.getString(it) } } }
                    ?: emptyList(),
                sourceAccounts = intent.getStringExtra("accounts_json")
                    ?.let { accountsFromJson(it) } ?: emptyList(),
                topupPhone = intent.getStringExtra("topup_phone"),
                operator = intent.getStringExtra("operator_name"),
                requiresManualInput = intent.getBooleanExtra("requires_manual", false)
            )
        }
    }
}

@Composable
private fun ConfirmBottomSheet(
    data: ConfirmationData,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedAccountId by remember { mutableStateOf(data.defaultAccountId) }
    var selectedBank by remember { mutableStateOf(data.recipientBanks.firstOrNull() ?: "ВТБ") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isTransfer = data.intent == "transfer"
    val ctaText = if (isTransfer) "Перевести ${formatRub(data.amount)}"
                  else "Оплатить ${formatRub(data.amount)}"

    OmegaSheetScaffold(
        title = data.title,
        onDismiss = onDismiss,
        onBack = null,
        onClose = onDismiss,
        footer = {
            OmegaButton(
                text = ctaText,
                isLoading = isLoading,
                enabled = !isLoading,
                style = OmegaButtonStyle.Brand,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isLoading = true; error = null
                    scope.launch {
                        runCatching {
                            MockApiService.confirm(
                                transactionId = data.transactionId,
                                sourceAccountId = selectedAccountId,
                                selectedBank = selectedBank,
                                context = context
                            )
                        }.onSuccess { result ->
                            onSuccess("✓ ${result.message}")
                        }.onFailure { e ->
                            error = e.message ?: "Ошибка подтверждения"
                            isLoading = false
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OmegaSpacing.md)
        ) {
            // DisplayL сумма под топ-баром
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatRub(data.amount),
                    style = OmegaType.DisplayL,
                    color = OmegaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (data.recipientDisplayName != null) {
                    Spacer(Modifier.height(OmegaSpacing.xs))
                    Text(
                        text = data.recipientDisplayName,
                        style = OmegaType.BodyM,
                        color = OmegaTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(OmegaSpacing.xs))

            data.recipientPhone?.let { OmegaInfoCard(label = "Телефон", value = it) }
            data.topupPhone?.let { OmegaInfoCard(label = "Номер", value = it) }
            data.operator?.let { OmegaInfoCard(label = "Оператор", value = it) }

            if (isTransfer && data.recipientDisplayName != null) {
                OmegaWarningCard(
                    text = "Проверьте ФИО и номер получателя. После перевода банк не сможет вернуть деньги."
                )
            }

            val selectedAcc = data.sourceAccounts.find { it.id == selectedAccountId }
                ?: data.sourceAccounts.firstOrNull()

            if (data.sourceAccounts.size > 1) {
                OmegaInfoCard(
                    label = "Счёт списания",
                    value = selectedAcc?.let { "${it.name} •• ${it.masked.takeLast(4)}" } ?: "",
                    trailingContent = { Text("▼", color = OmegaTextSecondary) },
                    onClick = {
                        val idx = data.sourceAccounts.indexOfFirst { it.id == selectedAccountId }
                        selectedAccountId = data.sourceAccounts.getOrNull(idx + 1)?.id
                            ?: data.sourceAccounts.firstOrNull()?.id ?: selectedAccountId
                    }
                )
            } else {
                selectedAcc?.let {
                    OmegaInfoCard(label = "Счёт списания", value = "${it.name} •• ${it.masked.takeLast(4)}")
                }
            }

            selectedAcc?.let {
                val after = it.balance - data.amount
                OmegaInfoCard(
                    label = "После операции",
                    value = formatRub(after),
                    valueColor = if (after >= 0) OmegaSuccess else OmegaError
                )
            }

            if (data.recipientBanks.size > 1) {
                OmegaBankCarousel(selected = selectedBank, onSelect = { selectedBank = it })
            }

            error?.let {
                Text(it, color = OmegaError, style = OmegaType.BodyTightM)
            }

            Spacer(Modifier.height(OmegaSpacing.xs))
        }
    }
}

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
