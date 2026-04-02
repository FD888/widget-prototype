package com.vtbvita.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue

data class BankTab(val label: String, val drawableRes: Int)

class MockBankActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val personaId = intent.getStringExtra(EXTRA_PERSONA_ID)
            ?: SessionManager.getPersonaId(this)
            ?: "denis"

        setContent {
            VTBVitaTheme {
                MockBankScreen(
                    personaId = personaId,
                    onLogout = {
                        SessionManager.logout(applicationContext)
                        // Обновляем виджет → «Войдите в аккаунт»
                        val awm = android.appwidget.AppWidgetManager.getInstance(applicationContext)
                        val ids = awm.getAppWidgetIds(
                            android.content.ComponentName(applicationContext, VitaWidgetProvider::class.java)
                        )
                        ids.forEach { awm.updateAppWidget(it, VitaWidgetProvider.defaultViews(applicationContext)) }
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun MockBankScreen(personaId: String, onLogout: () -> Unit = {}) {
    val tabs = listOf(
        BankTab("Главная", R.drawable.screen_main),
        BankTab("Платежи", R.drawable.screen_payments),
        BankTab("Продукты", R.drawable.screen_products),
        BankTab("История", R.drawable.screen_history),
        BankTab("Чат", R.drawable.screen_chat),
    )

    var selectedTab by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Скриншот на весь экран
        Image(
            painter = painterResource(tabs[selectedTab].drawableRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Баннер «Vita» сверху с кнопкой выхода
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(VtbBlue.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "VTB Vita · демо-режим",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // Кнопка выхода
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable(onClick = onLogout),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Выйти",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Кликабельный таббар снизу
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xFF1A237E).copy(alpha = 0.95f))
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { i, tab ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { selectedTab = i }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                if (i == selectedTab) Color.White else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        color = if (i == selectedTab) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (i == selectedTab) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
