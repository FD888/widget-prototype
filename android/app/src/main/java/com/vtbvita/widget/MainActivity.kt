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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.ui.theme.OmegaBrandGradientH
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import timber.log.Timber

data class Persona(
    val id: String,
    val name: String,
    val role: String,
    val description: String,
    val pin: String,
    val widgetPrompt: String,
    val gender: String = "male",   // "male" | "female"
    val birthday: String? = null,  // "MM-dd", напр. "03-15"
    val available: Boolean = true
)

val PERSONAS = listOf(
    Persona(
        id = "vitya",
        name = "Витя",
        role = "Студент · 22 года",
        description = "Сегмент Must-Have из исследования N=97. Пользуется метро, " +
                "часто пишет «скинь за пиццу». Предпочитает текстовый ввод — " +
                "боится, что голос распознает неправильно. Баланс проверяет " +
                "несколько раз в день.",
        pin = "1111",
        widgetPrompt = "Скинь Коле за пиццу?",
        gender = "male",
        birthday = "09-14",
        available = true
    ),
    Persona(
        id = "olga",
        name = "Ольга",
        role = "Финансовый менеджер · 38 лет",
        description = "Сегмент Nice-to-Have. Главная боль — забытые платежи: " +
                "ТТК Интернет снова просрочен. Ценит Face ID и умные " +
                "напоминания. Опасается, что кто-то увидит баланс на экране.",
        pin = "2222",
        widgetPrompt = "ТТК просрочен, Ольга — оплатить?",
        gender = "female",
        birthday = "11-03",
        available = true
    ),
    Persona(
        id = "artyom",
        name = "Артём",
        role = "Маркетолог · 29 лет",
        description = "Использует виджет как пассивный дашборд. Каждый вторник " +
                "платит репетитору Тамаре 2 000 ₽ — виджет об этом помнит. " +
                "Главный страх: «ещё один инструмент, который не приживётся».",
        pin = "3333",
        widgetPrompt = "Вторник — перевести Тамаре 2 000?",
        gender = "male",
        birthday = "06-22",
        available = true
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            WidgetState.getLastError(this)?.let { err ->
                android.widget.Toast.makeText(this, "Widget error:\n$err", android.widget.Toast.LENGTH_LONG).show()
                Timber.e("WIDGET_ERROR: %s", err)
                WidgetState.clearError(this)
            }
        }

        if (!SessionManager.hasAppToken(this)) {
            startActivity(Intent(this, PhoneVerificationActivity::class.java))
            finish()
            return
        }

        if (SessionManager.isLoggedIn(this)) {
            val personaId = SessionManager.getPersonaId(this) ?: "vitya"
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
            .background(OmegaBrandGradientH)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Text(
                "VTB Vita",
                style = OmegaType.DisplayS,
                fontWeight = FontWeight.Bold,
                color = OmegaTextPrimary,
                letterSpacing = 1.sp
            )
            Text(
                "демо-режим",
                style = OmegaType.BodyTightM,
                color = OmegaTextSecondary,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(40.dp))

            Text(
                "Выберите профиль",
                style = OmegaType.HeadlineM,
                color = OmegaTextPrimary.copy(alpha = 0.8f),
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
                style = OmegaType.BodyTightS,
                color = OmegaTextSecondary.copy(alpha = 0.6f),
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
        shape = OmegaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = OmegaTextPrimary.copy(alpha = 0.10f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (persona.available) OmegaTextPrimary.copy(alpha = 0.3f)
            else OmegaTextPrimary.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (persona.available) OmegaTextPrimary.copy(alpha = 0.25f)
                        else OmegaTextPrimary.copy(alpha = 0.10f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    persona.name.first().toString(),
                    style = OmegaType.HeadlineL,
                    fontWeight = FontWeight.Bold,
                    color = OmegaTextPrimary
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        persona.name,
                        style = OmegaType.HeadlineM,
                        fontWeight = FontWeight.SemiBold,
                        color = OmegaTextPrimary
                    )
                    if (!persona.available) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(OmegaTextPrimary.copy(alpha = 0.2f), OmegaRadius.xs)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("скоро", style = OmegaType.BodyTightS, color = OmegaTextSecondary)
                        }
                    }
                }
                Text(
                    persona.role,
                    style = OmegaType.BodyTightM,
                    color = OmegaTextSecondary.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    persona.description,
                    style = OmegaType.BodyParagraphM,
                    color = OmegaTextPrimary.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, OmegaTextPrimary.copy(alpha = 0.4f), OmegaRadius.sm)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "PIN: ${persona.pin}",
                            style = OmegaType.BodyTightM,
                            fontWeight = FontWeight.Medium,
                            color = OmegaTextPrimary,
                            letterSpacing = 1.5.sp
                        )
                    }
                    if (persona.available) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Войти →",
                            style = OmegaType.BodyTightM,
                            color = OmegaTextPrimary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}