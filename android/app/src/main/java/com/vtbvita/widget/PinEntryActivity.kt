package com.vtbvita.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBlueMid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PinEntryActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val personaId = intent.getStringExtra(EXTRA_PERSONA_ID) ?: "denis"
        val persona = PERSONAS.find { it.id == personaId } ?: PERSONAS.first()

        setContent {
            VTBVitaTheme {
                PinEntryScreen(
                    persona = persona,
                    onBack = { finish() },
                    onSuccess = {
                        SessionManager.login(applicationContext, persona.id)
                        // defaultViews теперь сам читает персону из сессии
                        val awm = android.appwidget.AppWidgetManager.getInstance(applicationContext)
                        val ids = awm.getAppWidgetIds(
                            android.content.ComponentName(applicationContext, VitaWidgetProvider::class.java)
                        )
                        ids.forEach { awm.updateAppWidget(it, VitaWidgetProvider.defaultViews(applicationContext)) }
                        startActivity(Intent(this, MockBankActivity::class.java).apply {
                            putExtra(MockBankActivity.EXTRA_PERSONA_ID, persona.id)
                        })
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PinEntryScreen(persona: Persona, onBack: () -> Unit, onSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    fun onDigit(d: String) {
        if (pin.length < 4) {
            pin += d
            if (pin.length == 4) {
                if (pin == persona.pin) {
                    onSuccess()
                } else {
                    scope.launch {
                        errorMsg = "Неверный PIN"
                        shake = true
                        delay(400)
                        pin = ""
                        shake = false
                        delay(800)
                        errorMsg = ""
                    }
                }
            }
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
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Назад
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(onClick = onBack)
                )
            }

            Spacer(Modifier.height(40.dp))

            // Аватар
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    persona.name.first().toString(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                persona.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                persona.role,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(40.dp))

            Text(
                "Введите PIN",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(20.dp))

            // 4 точки
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    val color = when {
                        errorMsg.isNotBlank() -> Color(0xFFE57373)
                        filled -> Color.White
                        else -> Color.White.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(color, CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (errorMsg.isNotBlank()) {
                Text(errorMsg, fontSize = 13.sp, color = Color(0xFFE57373))
            }

            Spacer(Modifier.height(40.dp))

            // Цифровой пад
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            PinKey(label = key) {
                                when (key) {
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    else -> onDigit(key)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.weight(1f))

            // Подсказка PIN
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Подсказка: PIN = ${persona.pin}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = if (label == "⌫") 22.sp else 26.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
