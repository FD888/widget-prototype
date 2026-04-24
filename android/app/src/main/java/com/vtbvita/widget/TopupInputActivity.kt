package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.ui.components.AccountPickerSheet
import com.vtbvita.widget.ui.components.AmountInputCard
import com.vtbvita.widget.ui.components.OmegaAmountChip
import com.vtbvita.widget.ui.components.OmegaAvatar
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaCompactInfoCard
import com.vtbvita.widget.ui.components.OmegaSheetScaffold
import com.vtbvita.widget.ui.components.OmegaSuccessScreen
import com.vtbvita.widget.ui.components.OmegaTextField
import com.vtbvita.widget.ui.components.SuccessAction
import com.vtbvita.widget.ui.components.VtbCardWithBadge
import com.vtbvita.widget.ui.components.formatRub
import com.vtbvita.widget.ui.components.operatorLogoRes
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch

class TopupInputActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PHONE = "phone"
        private const val EXTRA_AMOUNT = "amount"

        fun newIntent(context: Context, phone: String = "", amount: Double = 0.0): Intent =
            Intent(context, TopupInputActivity::class.java).apply {
                putExtra(EXTRA_PHONE, phone)
                putExtra(EXTRA_AMOUNT, amount)
            }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        val prefillPhone = intent.getStringExtra(EXTRA_PHONE) ?: ""
        val prefillAmount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

        setContent {
            VTBVitaTheme {
                var successData by remember { mutableStateOf<Pair<Double, String>?>(null) }

                val success = successData
                if (success != null) {
                    OmegaSuccessScreen(
                        title = "Пополнение выполнено",
                        subtitle = "Мобильная связь · $prefillPhone".trimEnd(' ', '·'),
                        amount = success.first,
                        actions = listOf(
                            SuccessAction(R.drawable.ic_receipt, "Получить чек"),
                            SuccessAction(R.drawable.ic_repeat, "Включить автоплатёж"),
                        ),
                        onDone = {
                            VitaWidgetProvider.showStatus(applicationContext, success.second)
                            finish()
                        }
                    )
                } else {
                    TopupSheet(
                        prefillPhone = prefillPhone,
                        prefillAmount = prefillAmount,
                        onDismiss = { finish() },
                        onSuccess = { amount, msg -> successData = amount to msg }
                    )
                }
            }
        }
    }
}

private fun detectOperator(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    val code = if (digits.startsWith("7") || digits.startsWith("8"))
        digits.drop(1).take(3) else digits.take(3)
    return when (code) {
        in listOf("900", "901", "902", "908", "909", "910", "911", "912", "913",
                  "914", "915", "916", "917", "918", "919") -> "МТС"
        in listOf("920", "921", "922", "923", "924", "925", "926", "927",
                  "928", "929", "930", "931", "932", "933") -> "МегаФон"
        in listOf("950", "951", "952", "953", "960", "961", "962", "963",
                  "964", "965", "966", "967", "968", "969") -> "Билайн"
        in listOf("977", "978", "999", "958", "936", "937", "938", "939") -> "Tele2"
        else -> if (digits.length >= 4) "Оператор" else ""
    }
}

@Composable
private fun TopupSheet(
    prefillPhone: String,
    prefillAmount: Double,
    onDismiss: () -> Unit,
    onSuccess: (amount: Double, msg: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf(prefillPhone) }
    var amountText by remember {
        mutableStateOf(if (prefillAmount > 0.0) prefillAmount.toLong().toString() else "")
    }
    var accounts by remember { mutableStateOf<List<AccountInfo>>(emptyList()) }
    var selectedAccountId by remember { mutableStateOf("debit") }
    var showAccountPicker by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { MockApiService.getBalance(context) }
            .onSuccess { accs ->
                accounts = accs
                selectedAccountId = accs.firstOrNull { it.currency == "RUB" }?.id ?: "debit"
            }
    }

    val rubAccounts = accounts.filter { it.currency == "RUB" }
    val selectedAcc = rubAccounts.find { it.id == selectedAccountId } ?: rubAccounts.firstOrNull()
    val operator = remember(phone) { detectOperator(phone) }
    val amount = amountText.toLongOrNull() ?: 0L
    val ctaText = if (amount > 0) "Оплатить ${formatRub(amount.toDouble())}" else "Оплатить"

    OmegaSheetScaffold(
        title = "Мобильная связь",
        onDismiss = onDismiss,
        onBack = null,
        onClose = onDismiss,
        footer = {
            if (error != null) {
                Text(
                    error!!,
                    color = OmegaError,
                    style = OmegaType.BodyTightS,
                    modifier = Modifier.padding(bottom = OmegaSpacing.sm)
                )
            }
            OmegaButton(
                text = ctaText,
                isLoading = isLoading,
                enabled = !isLoading && phone.isNotBlank() && amountText.isNotBlank(),
                style = OmegaButtonStyle.Brand,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) { error = "Введите сумму"; return@OmegaButton }
                    if (phone.isBlank()) { error = "Введите номер"; return@OmegaButton }
                    isLoading = true; error = null
                    scope.launch {
                        runCatching {
                            MockApiService.command(
                                intent = "topup",
                                amount = amt,
                                recipient = null,
                                phone = phone,
                                context = context
                            )
                        }.onSuccess { data ->
                            runCatching {
                                MockApiService.confirm(
                                    transactionId = data.transactionId,
                                    sourceAccountId = selectedAcc?.id ?: "debit",
                                    selectedBank = null,
                                    context = context
                                )
                            }.onSuccess { result ->
                                onSuccess(amt, "✓ ${result.message}")
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
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)
        ) {
            // Откуда
            OmegaCompactInfoCard(
                label = "Откуда ▾",
                value = selectedAcc?.name ?: "Выберите счёт",
                subtitle = selectedAcc?.let { "${it.masked}  ${formatRub(it.balance)}" },
                subtitleStyle = OmegaType.BodyTightL,
                subtitleColor = Color.White,
                onClick = { showAccountPicker = true },
                trailingContent = selectedAcc?.let { acc -> {
                    VtbCardWithBadge(
                        accountType = acc.type,
                        paymentSystem = acc.paymentSystem,
                        cardSize = 48.dp
                    )
                }}
            )

            // Номер получателя (display card)
            val opLogoRes = operatorLogoRes(operator)
            OmegaCompactInfoCard(
                label = "Номер",
                value = phone.ifBlank { "Введите номер" },
                subtitle = operator.ifBlank { null },
                trailingContent = when {
                    opLogoRes != 0 -> ({
                        Image(
                            painter = painterResource(opLogoRes),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                    })
                    operator.isNotBlank() -> ({
                        OmegaAvatar(name = operator, avatarSize = 48.dp)
                    })
                    else -> null
                }
            )

            // Редактирование номера
            OmegaTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Номер для пополнения",
                placeholder = "+7 000 000-00-00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            // Ввод суммы
            AmountInputCard(
                value = amountText,
                onValueChange = { v -> amountText = v.filter { it.isDigit() } }
            )

            // Комиссия
            Text(
                text = "Комиссия: 0 ₽",
                style = OmegaType.BodyTightM,
                color = OmegaTextSecondary
            )

            // Quick-amount chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
                val presets = listOf(100L, 300L, 500L, 1_000L)
                items(presets.size) { i ->
                    val preset = presets[i]
                    OmegaAmountChip(
                        label = "+${formatRub(preset.toDouble())}",
                        selected = amountText == preset.toString(),
                        onClick = { amountText = preset.toString() }
                    )
                }
            }

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
