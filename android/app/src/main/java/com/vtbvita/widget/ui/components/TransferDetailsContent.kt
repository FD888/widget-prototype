package com.vtbvita.widget.ui.components

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.theme.VtbGreen
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Shared composable для экрана деталей перевода.
 * Используется из TransferDetailsActivity (путь через ContactPicker)
 * и TransferFlowActivity (путь через NLP → disambiguation).
 */
@Composable
fun TransferDetailsSheet(
    recipientName: String,
    recipientPhone: String,
    bankDisplayName: String,
    prefillAmount: Double,
    prefillBank: String = "",
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amountText by remember {
        mutableStateOf(if (prefillAmount > 0.0) prefillAmount.toLong().toString() else "")
    }
    var selectedBank by remember { mutableStateOf(prefillBank.ifBlank { "ВТБ" }) }
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
                                tint = Color.White
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TransferDetailRow("Получатель", recipientName.ifBlank { recipientPhone })
                        if (recipientName.isNotBlank() && recipientPhone.isNotBlank()) {
                            TransferDetailRow("Телефон", recipientPhone)
                        }
                        if (bankDisplayName.isNotBlank()) {
                            TransferDetailRow(
                                "Имя в банке", bankDisplayName,
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(thickness = 0.5.dp)

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { v -> amountText = v.filter { it.isDigit() } },
                            label = { Text("Сумма, ₽") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            suffix = { Text("₽") }
                        )

                        BankCarousel(selected = selectedBank, onSelect = { selectedBank = it })

                        if (accounts.size > 1) {
                            TransferAccountDropdown(accounts, selectedAccountId) { selectedAccountId = it }
                        } else if (accounts.isNotEmpty()) {
                            val acc = accounts.first()
                            TransferDetailRow("Счёт списания", "${acc.name} ${acc.masked}")
                        }

                        val selectedAcc = accounts.find { it.id == selectedAccountId }
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        selectedAcc?.let {
                            val after = it.balance - amount
                            TransferDetailRow(
                                "После перевода",
                                formatRub(after),
                                valueColor = if (after >= 0) VtbGreen else MaterialTheme.colorScheme.error
                            )
                        }

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

@Composable
fun TransferDetailRow(
    label: String,
    value: String,
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
fun TransferAccountDropdown(
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

fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
