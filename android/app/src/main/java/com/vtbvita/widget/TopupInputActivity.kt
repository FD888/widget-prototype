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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.components.GradientButton
import com.vtbvita.widget.ui.components.SheetGradientHeader
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
                TopupSheet(
                    prefillPhone = prefillPhone,
                    prefillAmount = prefillAmount,
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
    onSuccess: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    SheetGradientHeader(title = "Пополнение телефона")

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Номер телефона") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            suffix = if (operator.isNotBlank()) ({ Text(operator) }) else null
                        )

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { v -> amountText = v.filter { it.isDigit() } },
                            label = { Text("Сумма, ₽") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            suffix = { Text("₽") }
                        )

                        // Быстрые суммы
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("100", "200", "300", "500").forEach { preset ->
                                FilterChip(
                                    selected = amountText == preset,
                                    onClick = { amountText = preset },
                                    label = { Text("$preset ₽") }
                                )
                            }
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
                                text = "Пополнить",
                                isLoading = isLoading,
                                enabled = !isLoading && phone.isNotBlank() && amountText.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val amt = amountText.toDoubleOrNull()
                                    if (amt == null || amt <= 0) { error = "Введите сумму"; return@GradientButton }
                                    if (phone.isBlank()) { error = "Введите номер"; return@GradientButton }
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

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
