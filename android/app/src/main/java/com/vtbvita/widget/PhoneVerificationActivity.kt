package com.vtbvita.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.theme.OmegaBrandGradient
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaTextDisabled
import com.vtbvita.widget.ui.theme.OmegaTextHint
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch

class PhoneVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VTBVitaTheme {
                PhoneVerificationScreen(
                    onSuccess = { token ->
                        SessionManager.saveAppToken(applicationContext, token)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PhoneVerificationScreen(onSuccess: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun submit() {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) {
            errorMsg = "Введите корректный номер"
            return
        }
        val formatted = "+7${digits.takeLast(10)}"
        isLoading = true
        errorMsg = ""
        scope.launch {
            val result = MockApiService.verifyPhone(formatted)
            isLoading = false
            result.fold(
                onSuccess = { token -> onSuccess(token) },
                onFailure = { e ->
                    errorMsg = when {
                        e.message?.contains("403") == true ||
                        e.message?.contains("Forbidden") == true -> "Номер не найден"
                        e.message?.contains("UnknownHost") == true ||
                        e.message?.contains("connect") == true -> "Нет соединения с сервером"
                        else -> "Ошибка: ${e.message}"
                    }
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OmegaBrandGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "VTB Vita",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = OmegaTextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Введите номер телефона",
                fontSize = 15.sp,
                color = OmegaTextSecondary
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(11)
                    phone = digits
                    errorMsg = ""
                },
                placeholder = {
                    Text("900 000 00 00", color = OmegaTextHint)
                },
                prefix = { Text("+7 ", color = OmegaTextPrimary, fontWeight = FontWeight.Medium) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OmegaTextPrimary,
                    unfocusedTextColor = OmegaTextPrimary,
                    focusedBorderColor = OmegaTextPrimary,
                    unfocusedBorderColor = OmegaTextDisabled,
                    cursorColor = OmegaTextPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            if (errorMsg.isNotBlank()) {
                Text(
                    errorMsg,
                    fontSize = 13.sp,
                    color = OmegaError,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(Modifier.height(24.dp))

            OmegaButton(
                text = "Продолжить",
                onClick = { submit() },
                enabled = !isLoading && phone.filter { it.isDigit() }.length >= 10,
                modifier = Modifier.fillMaxWidth(),
                isLoading = isLoading,
                style = OmegaButtonStyle.Brand
            )
        }
    }
}
