package com.vtbvita.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.vtbvita.widget.BuildConfig
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBlueMid
import timber.log.Timber

data class Persona(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val pin: String,
    val widgetPrompt: String,
    val available: Boolean = true
)

val PERSONAS = listOf(
    Persona(
        id = "denis",
        name = "Денис",
        role = "Зарплатный клиент · 22 года",
        description = "Молодой активный пользователь. Получает зарплату на карту ВТБ, " +
                "регулярно переводит друзьям, пополняет телефон. " +
                "Интересуется инвестициями. Виджет предлагает контекстные " +
                "финансовые подсказки под его привычки.",
        pin = "1234",
        widgetPrompt = "Как настроение, Денис?",
        available = true
    ),
    Persona(
        id = "masha",
        name = "Маша",
        role = "Инвестор · 28 лет",
        description = "Активный инвестор, следит за портфелем. " +
                "Виджет будет показывать сигналы рынка и напоминания по вкладам.",
        pin = "—",
        widgetPrompt = "Рынок открылся, Маша",
        available = false
    ),
    Persona(
        id = "yana",
        name = "Яна",
        role = "Студент · 21 год",
        description = "Пользователь с небольшим оборотом, часто пополняет телефон " +
                "и переводит за учёбу. Виджет адаптируется под студенческий бюджет.",
        pin = "—",
        widgetPrompt = "Стипендия пришла, Яна?",
        available = false
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Телефон не верифицирован → экран верификации
        if (!SessionManager.hasAppToken(this)) {
            startActivity(Intent(this, PhoneVerificationActivity::class.java))
            finish()
            return
        }

        // Если persona сохранена — сразу PIN (banking JWT всегда запрашивается заново)
        if (SessionManager.isLoggedIn(this)) {
            val personaId = SessionManager.getPersonaId(this) ?: "denis"
            startActivity(
                Intent(this, PinEntryActivity::class.java).apply {
                    putExtra(PinEntryActivity.EXTRA_PERSONA_ID, personaId)
                }
            )
            finish()
            return
        }

        setContent {
            VTBVitaTheme {
                ProfileSelectionScreen(
                    onPersonaSelected = { persona ->
                        startActivity(
                            Intent(this, PinEntryActivity::class.java).apply {
                                putExtra(PinEntryActivity.EXTRA_PERSONA_ID, persona.id)
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileSelectionScreen(onPersonaSelected: (Persona) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF001A5E), VtbBlue, VtbBlueMid)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Логотип
            Text(
                "VTB Vita",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )
            Text(
                "демо-режим",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(40.dp))

            Text(
                "Выберите профиль",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(12.dp))

            PERSONAS.forEach { persona ->
                PersonaCard(
                    persona = persona,
                    onClick = { if (persona.available) onPersonaSelected(persona) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.weight(1f))

            Text(
                "PIN отображается открыто в целях демонстрации",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PersonaCard(persona: Persona, onClick: () -> Unit) {
    val alpha = if (persona.available) 1f else 0.5f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(enabled = persona.available, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.10f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (persona.available) Color.White.copy(alpha = 0.3f)
            else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Аватар
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (persona.available) Color.White.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.10f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    persona.name.first().toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        persona.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (!persona.available) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("скоро", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
                Text(
                    persona.role,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    persona.description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))

                // PIN-бейдж
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "PIN: ${persona.pin}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                    }
                    if (persona.available) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Войти →",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
