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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.components.BankCarousel
import com.vtbvita.widget.ui.components.GradientButton
import com.vtbvita.widget.ui.components.SheetGradientHeader
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbGreen
import kotlinx.coroutines.launch
import java.util.Locale

class TransferDetailsActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_RECIPIENT_NAME = "recipient_name"
        private const val EXTRA_RECIPIENT_PHONE = "recipient_phone"
        private const val EXTRA_BANK_DISPLAY_NAME = "bank_display_name"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_BANK = "bank"
        private const val EXTRA_HAS_CONTACT_PICKER = "has_contact_picker"

        fun newIntent(
            context: Context,
            recipientName: String,
            recipientPhone: String,
            amount: Double = 0.0,
            bank: String = "",
            bankDisplayName: String = "",
            hasContactPicker: Boolean = false
        ): Intent = Intent(context, TransferDetailsActivity::class.java).apply {
            putExtra(EXTRA_RECIPIENT_NAME, recipientName)
            putExtra(EXTRA_RECIPIENT_PHONE, recipientPhone)
            putExtra(EXTRA_BANK_DISPLAY_NAME, bankDisplayName)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK, bank)
            putExtra(EXTRA_HAS_CONTACT_PICKER, hasContactPicker)
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
        val hasContactPicker = intent.getBooleanExtra(EXTRA_HAS_CONTACT_PICKER, false)

        val onDismiss: () -> Unit = if (hasContactPicker) {
            { finish() }
        } else {
            {
                val i = ContactPickerActivity.newIntent(this, prefillAmount)
                BankingSession.putInIntent(i)
                startActivity(i)
                finish()
            }
        }

        setContent {
            VTBVitaTheme {
                TransferDetailsSheet(
                    recipientName = recipientName,
                    recipientPhone = recipientPhone,
                    bankDisplayName = bankDisplayName,
                    prefillAmount = prefillAmount,
                    prefillBank = prefillBank,
                    onDismiss = onDismiss,
                    onSuccess = { msg ->
                        VitaWidgetProvider.showStatus(applicationContext, msg)
                        finish()
                    }
                )
            }
        }
    }
}

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
        mutableStateOf(prefillBank.ifBlank { "ВТБ" })
    }
    var comment by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf<List<com.vtbvita.widget.model.AccountInfo>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf("debit") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
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
                    // Заголовок с кнопкой назад поверх градиента
                    Box {
                        SheetGradientHeader(
                            title = "Перевод",
                            subtitle = recipientName.ifBlank { recipientPhone }.ifBlank { null }
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp, top = 20.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад к контактам",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Получатель
                        DetailRow("Получатель", recipientName.ifBlank { recipientPhone })
                        if (recipientName.isNotBlank() && recipientPhone.isNotBlank()) {
                            DetailRow("Телефон", recipientPhone)
                        }
                        if (bankDisplayName.isNotBlank()) {
                            DetailRow(
                                "Имя в банке", bankDisplayName,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp)

                        // Сумма
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { v -> amountText = v.filter { it.isDigit() } },
                            label = { Text("Сумма, ₽") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            suffix = { Text("₽") }
                        )

                        // Карусель банков
                        BankCarousel(selected = selectedBank, onSelect = { selectedBank = it })

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
                                valueColor = if (after >= 0) VtbGreen else MaterialTheme.colorScheme.error
                            )
                        }

                        // Комментарий
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
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("Отмена") }

                            GradientButton(
                                text = "Перевести",
                                isLoading = isLoading,
                                enabled = !isLoading && amountText.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val amt = amountText.toDoubleOrNull()
                                    if (amt == null || amt <= 0) { error = "Введите сумму"; return@GradientButton }
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

// ── Вспомогательные ──────────────────────────────────────────────────────────

@Composable
private fun DetailRow(
    label: String, value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
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
