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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.components.OmegaAmountChip
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaInfoCard
import com.vtbvita.widget.ui.components.OmegaSheetHeader
import com.vtbvita.widget.ui.components.OmegaSuccessScreen
import com.vtbvita.widget.ui.components.OmegaTextField
import com.vtbvita.widget.ui.components.SuccessAction
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaScrim
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch
import java.util.Locale

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
                var successData by remember {
                    androidx.compose.runtime.mutableStateOf<Pair<Double, String>?>(null)
                }

                val success = successData
                if (success != null) {
                    OmegaSuccessScreen(
                        title = "Пополнение выполнено",
                        subtitle = "Мобильная связь · $prefillPhone".trimEnd(' ', '·'),
                        amount = success.first,
                        actions = listOf(
                            SuccessAction("🧾", "Получить чек"),
                            SuccessAction("🔄", "Включить автоплатёж"),
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
                        onSuccess = { amount, msg ->
                            successData = amount to msg
                        }
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
        in listOf("977", "978", "999", "958", "936", "937",
                  "938", "939") -> "Tele2"
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
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    val operator = remember(phone) { detectOperator(phone) }

    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OmegaScrim)
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
                OmegaSheetHeader(title = "Мобильная связь")

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (operator.isNotBlank()) {
                        OmegaInfoCard(
                            label = "Оператор",
                            value = operator
                        )
                    }

                    OmegaTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Номер получателя",
                        placeholder = "+7 000 000-00-00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    OmegaTextField(
                        value = amountText,
                        onValueChange = { v -> amountText = v.filter { it.isDigit() } },
                        label = "Сумма",
                        placeholder = "0 ₽",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingContent = {
                            Text(
                                text = "Комиссия 0 ₽",
                                style = MaterialTheme.typography.bodySmall,
                                color = OmegaSuccess,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("100", "300", "500", "1000").forEach { preset ->
                            OmegaAmountChip(
                                label = "$preset ₽",
                                selected = amountText == preset,
                                onClick = { amountText = preset }
                            )
                        }
                    }

                    error?.let {
                        Text(it, color = OmegaError, style = MaterialTheme.typography.bodySmall)
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
                        text = "Оплатить ₽",
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
                                            sourceAccountId = "debit",
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

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
