package com.vtbvita.widget.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaStroke
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaSurfaceAlt
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.TitanGray
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * @param onProceed When set: CTA = «Продолжить», calls command() only, invokes callback with
 *                  (ConfirmationData, selectedAcc, selectedBank, comment).
 *                  When null: CTA = «Перевести X ₽», calls command() + confirm() directly.
 */
@Composable
fun TransferDetailsSheet(
    recipientName: String,
    recipientPhone: String,
    bankDisplayName: String,
    prefillAmount: Double,
    prefillBank: String = "",
    onDismiss: () -> Unit,
    onClose: (() -> Unit)? = null,
    onProceed: ((data: ConfirmationData, selectedAcc: AccountInfo?, bank: String, comment: String) -> Unit)? = null,
    onSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var amountText by remember {
        mutableStateOf(if (prefillAmount > 0.0) prefillAmount.toLong().toString() else "")
    }
    var selectedBank by remember { mutableStateOf(prefillBank.ifBlank { "ВТБ" }) }
    var comment by remember { mutableStateOf("") }
    var accounts by remember { mutableStateOf<List<AccountInfo>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf("debit") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAccountPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { MockApiService.getBalance(context) }
            .onSuccess { accs ->
                accounts = accs
                selectedAccountId = accs.firstOrNull { it.currency == "RUB" }?.id ?: "debit"
            }
    }

    val rubAccounts = accounts.filter { it.currency == "RUB" }
    val selectedAcc = rubAccounts.find { it.id == selectedAccountId } ?: rubAccounts.firstOrNull()
    val balance = selectedAcc?.balance ?: 0.0
    val amount = amountText.toLongOrNull() ?: 0L

    val ctaText = if (onProceed != null) "Продолжить"
                  else if (amount > 0) "Перевести ${formatRub(amount.toDouble())}"
                  else "Перевести"

    OmegaSheetScaffold(
        title = "Перевод",
        onDismiss = onDismiss,
        onBack = onDismiss,
        onClose = onClose,
        footer = {
            Spacer(Modifier.height(OmegaSpacing.md))
            OmegaButton(
                text = ctaText,
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
                                context = context,
                                comment = comment
                            )
                        }.onSuccess { data ->
                            if (onProceed != null) {
                                isLoading = false
                                onProceed(data, selectedAcc, selectedBank, comment)
                            } else {
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
                            }
                        }.onFailure { e ->
                            error = e.message ?: "Ошибка соединения"; isLoading = false
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)
        ) {
            // Карточка «Откуда»
            OmegaCompactInfoCard(
                label = "Откуда ▾",
                value = selectedAcc?.name ?: "Выберите счёт",
                subtitle = selectedAcc?.let { "${it.masked}  ${formatRub(it.balance)}" },
                subtitleStyle = OmegaType.BodyTightL,
                subtitleColor = androidx.compose.ui.graphics.Color.White,
                onClick = { showAccountPicker = true },
                trailingContent = selectedAcc?.let { acc -> {
                    VtbCardWithBadge(
                        accountType = acc.type,
                        paymentSystem = acc.paymentSystem,
                        cardSize = 48.dp
                    )
                }}
            )

            // Карточка «Кому»
            OmegaCompactInfoCard(
                label = "Кому",
                value = recipientName.ifBlank { recipientPhone },
                subtitle = if (recipientName.isNotBlank() && recipientPhone.isNotBlank()) recipientPhone else null,
                subtitleStyle = OmegaType.BodyTightSemiBoldL,
                subtitleColor = androidx.compose.ui.graphics.Color.White,
                trailingContent = {
                    val bankOpt = BANK_OPTIONS.find { it.id == selectedBank }
                    if (bankOpt?.logoRes != null) {
                        Image(
                            painter = painterResource(bankOpt.logoRes),
                            contentDescription = bankOpt.label,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        OmegaAvatar(
                            name = recipientName.ifBlank { recipientPhone },
                            avatarSize = 40.dp
                        )
                    }
                }
            )

            // Банк получателя
            OmegaBankCarousel(selected = selectedBank, onSelect = { selectedBank = it }, showLabel = false)

            // Ввод суммы
            AmountInputCard(
                value = amountText,
                onValueChange = { v -> amountText = v.filter { it.isDigit() } }
            )

            // Quick-amount chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
                val quickAmounts = listOf(500L, 1_000L, 2_000L)
                items(quickAmounts.size) { i ->
                    val preset = quickAmounts[i]
                    OmegaAmountChip(
                        label = "+${formatRub(preset.toDouble())}",
                        selected = amountText == preset.toString(),
                        onClick = { amountText = preset.toString() }
                    )
                }
                item {
                    OmegaAmountChip(
                        label = "Всё",
                        selected = balance > 0 && amountText == balance.toLong().toString(),
                        onClick = { if (balance > 0) amountText = balance.toLong().toString() }
                    )
                }
            }

            // Сообщение
            OmegaTextField(
                value = comment,
                onValueChange = { comment = it },
                label = "Сообщение получателю",
                placeholder = "Необязательно",
                showBorder = true
            )

            // Emoji chips
            Row(horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
                listOf("🎂", "🎁", "🙏", "❤️", "💸").forEach { emoji ->
                    val isSelected = comment == emoji
                    Box(
                        modifier = Modifier
                            .clip(OmegaRadius.md)
                            .background(OmegaSurface)
                            .border(
                                BorderStroke(OmegaStroke.regular, if (isSelected) Color.White else TitanGray.v600),
                                OmegaRadius.md
                            )
                            .clickable { comment = if (isSelected) "" else emoji }
                            .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.xs + OmegaSpacing.xxs),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, style = OmegaType.BodyL)
                    }
                }
            }

            error?.let { Text(it, color = OmegaError, style = OmegaType.BodyTightM) }
            Spacer(Modifier.height(OmegaSpacing.xs))
        }
    }

    if (showAccountPicker) {
        AccountPickerSheet(
            accounts = accounts,
            selectedId = selectedAccountId,
            onSelect = { selectedAccountId = it.id },
            onDismiss = { showAccountPicker = false }
        )
    }
}

fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
