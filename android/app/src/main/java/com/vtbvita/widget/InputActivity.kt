package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch

/**
 * Экран ввода параметров операции.
 *
 * Запускается из виджета кнопками «Перевод» или «Пополнить».
 * После заполнения полей вызывает POST /command → открывает ConfirmActivity.
 *
 * NLP-подключение (C-02): когда NlpService готов — убрать кнопочный UI,
 * добавить TextField + вызов NlpService.parse(text).
 */
class InputActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INTENT_TYPE = "intent_type"

        fun newIntent(context: Context, intentType: String): Intent =
            Intent(context, InputActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_INTENT_TYPE, intentType)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentType = intent.getStringExtra(EXTRA_INTENT_TYPE) ?: "transfer"
        enableEdgeToEdge()
        setContent {
            VTBVitaTheme {
                InputScreen(
                    intentType = intentType,
                    onConfirmationData = { data ->
                        startActivity(ConfirmActivity.newIntent(this, data))
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputScreen(
    intentType: String,
    onConfirmationData: (ConfirmationData) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var secondField by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val screenTitle = when (intentType) {
        "transfer" -> "Перевод"
        "topup" -> "Пополнение телефона"
        else -> "VTB Vita"
    }
    val secondLabel = if (intentType == "transfer") "Кому (маша, яна, +7 916…)" else "Номер телефона"
    val secondKeyboard = if (intentType == "transfer") KeyboardType.Text else KeyboardType.Phone

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Сумма, ₽") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = secondField,
                onValueChange = { secondField = it },
                label = { Text(secondLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = secondKeyboard),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    val amountVal = amount.toDoubleOrNull()
                    if (amountVal == null || amountVal <= 0) {
                        error = "Введите корректную сумму"
                        return@Button
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        runCatching {
                            MockApiService.command(
                                intent = intentType,
                                amount = amountVal,
                                recipient = secondField.takeIf { it.isNotBlank() },
                                phone = if (intentType == "topup") secondField.takeIf { it.isNotBlank() } else null
                            )
                        }.onSuccess { data ->
                            onConfirmationData(data)
                        }.onFailure { e ->
                            error = e.message ?: "Ошибка соединения с сервером"
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading && amount.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Продолжить")
                }
            }
        }
    }
}
