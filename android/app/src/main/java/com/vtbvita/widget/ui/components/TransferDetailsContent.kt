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
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.R
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaOverlay
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import kotlinx.coroutines.launch
import java.util.Locale

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
            .background(OmegaOverlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(220))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(OmegaSurface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OmegaSheetHeader(
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
                            painter = painterResource(R.drawable.ic_arrow_up),
                            contentDescription = "Назад",
                            tint = OmegaTextPrimary
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OmegaInfoCard(
                        label = "Получатель",
                        value = recipientName.ifBlank { recipientPhone }
                    )
                    if (recipientName.isNotBlank() && recipientPhone.isNotBlank()) {
                        OmegaInfoCard(label = "Телефон", value = recipientPhone)
                    }
                    if (bankDisplayName.isNotBlank()) {
                        OmegaInfoCard(
                            label = "Банк получателя",
                            value = bankDisplayName,
                            valueColor = OmegaTextSecondary
                        )
                    }

                    val selectedAcc = accounts.find { it.id == selectedAccountId }
                    if (accounts.size > 1) {
                        OmegaInfoCard(
                            label = "Счёт списания",
                            value = selectedAcc?.let { "${it.name} •• ${it.masked.takeLast(4)}" }
                                ?: "Загрузка...",
                            trailingContent = {
                                Text("▼", color = OmegaTextSecondary)
                            },
                            onClick = {
                                val idx = accounts.indexOfFirst { it.id == selectedAccountId }
                                selectedAccountId = accounts.getOrNull(idx + 1)?.id
                                    ?: accounts.firstOrNull()?.id ?: selectedAccountId
                            }
                        )
                    } else if (selectedAcc != null) {
                        OmegaInfoCard(
                            label = "Счёт списания",
                            value = "${selectedAcc.name} •• ${selectedAcc.masked.takeLast(4)}"
                        )
                    }

                    OmegaBankCarousel(
                        selected = selectedBank,
                        onSelect = { selectedBank = it }
                    )

                    OmegaTextField(
                        value = amountText,
                        onValueChange = { v -> amountText = v.filter { it.isDigit() } },
                        label = "Сумма",
                        placeholder = "0 ₽",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingContent = {
                            Text("₽", color = OmegaTextSecondary, fontWeight = FontWeight.Medium)
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("500", "1000", "2000", "5000").forEach { preset ->
                            OmegaAmountChip(
                                label = "+$preset ₽",
                                selected = amountText == preset,
                                onClick = { amountText = preset }
                            )
                        }
                    }

                    selectedAcc?.let { acc ->
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        val after = acc.balance - amount
                        OmegaInfoCard(
                            label = "После перевода",
                            value = formatRub(after),
                            valueColor = if (after >= 0) OmegaSuccess else OmegaError
                        )
                    }

                    OmegaTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = "Сообщение получателю",
                        placeholder = "Необязательно"
                    )

                    error?.let {
                        Text(
                            it,
                            color = OmegaError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmegaSurface)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OmegaButton(
                        text = "Перевести",
                        isLoading = isLoading,
                        enabled = !isLoading && amountText.isNotBlank(),
                        style = OmegaButtonStyle.Brand,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (amt == null || amt <= 0) { error = "Введите сумму"; return@OmegaButton }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Отмена",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmegaTextSecondary
                        )
                    }
                }
            }
        }
    }
}

fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)