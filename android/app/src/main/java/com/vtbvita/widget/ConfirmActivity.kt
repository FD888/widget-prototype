package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.model.accountsFromJson
import com.vtbvita.widget.model.accountsToJson
import com.vtbvita.widget.ui.components.BankCarousel
import com.vtbvita.widget.ui.components.GradientButton
import com.vtbvita.widget.ui.components.SheetGradientHeader
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbGreen
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
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 280)
            ) + fadeIn(animationSpec = tween(200))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    // Градиентный заголовок с суммой
                    SheetGradientHeader(
                        title = data.title,
                        subtitle = formatRub(data.amount)
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Детали операции
                        data.recipientDisplayName?.let { DetailRow("Получатель", it) }
                        data.recipientPhone?.let { DetailRow("Телефон", it) }
                        data.topupPhone?.let { DetailRow("Номер", it) }
                        data.operator?.let { DetailRow("Оператор", it) }

                        // Счёт списания
                        val selectedAcc = data.sourceAccounts.find { it.id == selectedAccountId }
                            ?: data.sourceAccounts.firstOrNull()

                        if (data.sourceAccounts.size > 1) {
                            AccountDropdown(data.sourceAccounts, selectedAccountId) { selectedAccountId = it }
                        } else {
                            selectedAcc?.let { DetailRow("Счёт", "${it.name} ${it.masked}") }
                        }

                        selectedAcc?.let {
                            val afterAmount = it.balance - data.amount
                            DetailRowColored(
                                label = "После операции",
                                value = formatRub(afterAmount),
                                valueColor = if (afterAmount >= 0) VtbGreen else MaterialTheme.colorScheme.error
                            )
                        }

                        // Карусель банков (если несколько вариантов)
                        if (data.recipientBanks.size > 1) {
                            BankCarousel(selected = selectedBank, onSelect = { selectedBank = it })
                        }

                        error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Отмена") }

                            GradientButton(
                                text = "Подтвердить",
                                isLoading = isLoading,
                                enabled = !isLoading,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isLoading = true
                                    error = null
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

                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ── Вспомогательные composable ────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailRowColored(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<AccountInfo>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId } ?: accounts.first()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "${selected.name} ${selected.masked}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Счёт списания") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = { Text("${acc.name} ${acc.masked} · ${formatRub(acc.balance)}") },
                    onClick = { onSelect(acc.id); expanded = false }
                )
            }
        }
    }
}

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
