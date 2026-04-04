package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Шаг 2 флоу перевода: детали операции.
 * Получатель уже известен (из ContactPickerActivity или из текстовой команды).
 * Пользователь вводит сумму, банк, счёт списания, опциональный комментарий.
 */
class TransferDetailsActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_RECIPIENT_NAME = "recipient_name"
        private const val EXTRA_RECIPIENT_PHONE = "recipient_phone"
        private const val EXTRA_BANK_DISPLAY_NAME = "bank_display_name"
        private const val EXTRA_AMOUNT = "amount"          // 0.0 = пусто (ввод вручную)
        private const val EXTRA_BANK = "bank"              // "" = пусто

        fun newIntent(
            context: Context,
            recipientName: String,
            recipientPhone: String,
            amount: Double = 0.0,
            bank: String = "",
            bankDisplayName: String = ""
        ): Intent = Intent(context, TransferDetailsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_RECIPIENT_NAME, recipientName)
            putExtra(EXTRA_RECIPIENT_PHONE, recipientPhone)
            putExtra(EXTRA_BANK_DISPLAY_NAME, bankDisplayName)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK, bank)
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME) ?: ""
        val recipientPhone = intent.getStringExtra(EXTRA_RECIPIENT_PHONE) ?: ""
        val bankDisplayName = intent.getStringExtra(EXTRA_BANK_DISPLAY_NAME) ?: ""
        val prefillAmount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val prefillBank = intent.getStringExtra(EXTRA_BANK) ?: ""

        setContent {
            VTBVitaTheme {
                TransferDetailsSheet(
                    recipientName = recipientName,
                    recipientPhone = recipientPhone,
                    bankDisplayName = bankDisplayName,
                    prefillAmount = prefillAmount,
                    prefillBank = prefillBank,
                    onDismiss = { finish() },
                    onSuccess = { msg ->
                        VitaWidgetProvider.showStatus(applicationContext, msg)
                        finish()
                    }
                )
            }
        }
    }
}

private val BANKS = listOf("ВТБ", "Сбербанк", "Тинькофф", "Альфа-банк", "Райффайзен", "Другой банк")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferDetailsSheet(
    recipientName: String,
    recipientPhone: String,
    bankDisplayName: String,
    prefillAmount: Double,
    prefillBank: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var amountText by remember {
        mutableStateOf(if (prefillAmount > 0.0) prefillAmount.toLong().toString() else "")
    }
    var selectedBank by remember {
        mutableStateOf(prefillBank.ifBlank { BANKS.first() })
    }
    var comment by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf<List<com.vtbvita.widget.model.AccountInfo>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf("debit") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Загружаем счета
    LaunchedEffect(Unit) {
        runCatching { MockApiService.getBalance(context) }
            .onSuccess { accs ->
                accounts = accs
                selectedAccountId = accs.firstOrNull()?.id ?: "debit"
            }
    }

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
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .width(40.dp).height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(2.dp)
                        )
                        .align(Alignment.CenterHorizontally)
                )

                Text("Перевод", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()

                // Получатель (зафиксирован)
                DetailRow("Получатель", recipientName.ifBlank { recipientPhone })
                if (recipientName.isNotBlank() && recipientPhone.isNotBlank()) {
                    DetailRow("Телефон", recipientPhone)
                }
                if (bankDisplayName.isNotBlank()) {
                    DetailRow(
                        "Имя в банке",
                        bankDisplayName,
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(thickness = 0.5.dp)

                // Сумма — редактируемая
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() } },
                    label = { Text("Сумма, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text("₽") }
                )

                // Банк получателя
                BankDropdown(BANKS, selectedBank) { selectedBank = it }

                // Счёт списания
                if (accounts.size > 1) {
                    AccountDropdown(accounts, selectedAccountId) { selectedAccountId = it }
                } else if (accounts.isNotEmpty()) {
                    val acc = accounts.first()
                    DetailRow("Счёт списания", "${acc.name} ${acc.masked}")
                }

                // Остаток после
                val selectedAcc = accounts.find { it.id == selectedAccountId }
                val amount = amountText.toDoubleOrNull() ?: 0.0
                selectedAcc?.let {
                    val after = it.balance - amount
                    DetailRow(
                        "После перевода",
                        formatRub(after),
                        valueColor = if (after >= 0)
                            com.vtbvita.widget.ui.theme.VtbGreen
                        else MaterialTheme.colorScheme.error
                    )
                }

                // Комментарий (необязательно)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (amt == null || amt <= 0) {
                                error = "Введите сумму"; return@Button
                            }
                            isLoading = true; error = null
                            scope.launch {
                                runCatching {
                                    MockApiService.command(
                                        intent = "transfer",
                                        amount = amt,
                                        recipient = recipientPhone.ifBlank { recipientName },
                                        phone = null,
                                        context = context
                                    )
                                }.onSuccess { data ->
                                    // confirm сразу через API
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
                                        error = e.message ?: "Ошибка"; isLoading = false
                                    }
                                }.onFailure { e ->
                                    error = e.message ?: "Ошибка соединения"; isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && amountText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoading) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        ) else Text("Перевести")
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ── Вспомогательные ──────────────────────────────────────────────────────────

@Composable
private fun DetailRow(
    label: String, value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
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
private fun BankDropdown(banks: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true,
            label = { Text("Банк получателя") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            banks.forEach { bank ->
                DropdownMenuItem(text = { Text(bank) }, onClick = { onSelect(bank); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<com.vtbvita.widget.model.AccountInfo>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.find { it.id == selectedId } ?: accounts.first()
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = "${selected.name} ${selected.masked}", onValueChange = {}, readOnly = true,
            label = { Text("Счёт списания") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
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
