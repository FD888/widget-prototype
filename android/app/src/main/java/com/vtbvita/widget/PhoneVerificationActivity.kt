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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBlueMid
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
            .background(Brush.verticalGradient(listOf(Color(0xFF001A5E), VtbBlue, VtbBlueMid)))
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
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Введите номер телефона",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { input ->
                    // Только цифры, не больше 11 символов
                    val digits = input.filter { it.isDigit() }.take(11)
                    phone = digits
                    errorMsg = ""
                },
                placeholder = {
                    Text("900 000 00 00", color = Color.White.copy(alpha = 0.35f))
                },
                prefix = { Text("+7 ", color = Color.White, fontWeight = FontWeight.Medium) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White.copy(alpha = 0.8f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            if (errorMsg.isNotBlank()) {
                Text(
                    errorMsg,
                    fontSize = 13.sp,
                    color = Color(0xFFE57373),
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { submit() },
                enabled = !isLoading && phone.filter { it.isDigit() }.length >= 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = VtbBlue,
                    disabledContainerColor = Color.White.copy(alpha = 0.3f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = VtbBlue,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Продолжить", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
