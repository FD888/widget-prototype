package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.ui.theme.*
import java.util.Locale

private val TAB_NAMES = listOf("Главная", "Платежи", "Продукты", "История", "Чат")

class MockBankActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
    }

    override fun onStop() {
        super.onStop()
        val awm = android.appwidget.AppWidgetManager.getInstance(applicationContext)
        val ids = awm.getAppWidgetIds(
            android.content.ComponentName(applicationContext, VitaWidgetProvider::class.java)
        )
        ids.forEach { awm.updateAppWidget(it, VitaWidgetProvider.defaultViews(applicationContext)) }
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
    var selectedTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(VtbSurfaceBg)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(VtbBlue)
                .padding(horizontal = 16.dp, vertical = 12.dp),
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

        // Tab content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> MainTab(context)
                1 -> PaymentsTab(context)
                2 -> ProductsTab(context)
                3 -> HistoryTab()
                4 -> ChatTab()
            }
        }

        // Bottom tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VtbBlue)
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TAB_NAMES.forEachIndexed { i, label ->
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
                        label,
                        fontSize = 11.sp,
                        color = if (i == selectedTab) Color.White else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (i == selectedTab) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// ─── ГЛАВНАЯ ────────────────────────────────────────────────────────────────

@Composable
fun MainTab(context: Context) {
    var accounts by remember { mutableStateOf<List<AccountInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { MockApiService.getBalance(context) }
            .onSuccess { accounts = it; loading = false }
            .onFailure { error = it.message; loading = false }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Мои счета",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VtbOnSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        when {
            loading -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VtbBlue)
                }
            }
            error != null -> item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text(
                        "Не удалось загрузить счета: $error",
                        modifier = Modifier.padding(16.dp),
                        color = VtbRed,
                        fontSize = 13.sp
                    )
                }
            }
            else -> items(accounts) { acc -> AccountCard(acc) }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { context.startActivity(Intent(context, InputActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = VtbBlue)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Открыть Vita")
            }
        }
    }
}

@Composable
fun AccountCard(account: AccountInfo) {
    val cardColor = when (account.type) {
        "debit"   -> VtbBlue
        "credit"  -> Color(0xFFE65100)
        "savings" -> VtbGreen
        else      -> VtbBlueMid
    }
    val typeLabel = when (account.type) {
        "debit"   -> "Дебетовая карта"
        "credit"  -> "Кредитная карта"
        "savings" -> "Накопительный счёт"
        else      -> account.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(typeLabel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                formatRub(account.balance),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(account.masked, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.2f ₽", amount)

// ─── ПЛАТЕЖИ ────────────────────────────────────────────────────────────────

private data class PayCategory(val label: String, val icon: ImageVector, val topup: Boolean = false)

@Composable
fun PaymentsTab(context: Context) {
    val categories = listOf(
        PayCategory("ЖКХ",       Icons.Default.Home),
        PayCategory("Телефон",   Icons.Default.Phone, topup = true),
        PayCategory("Интернет",  Icons.Default.Wifi),
        PayCategory("Транспорт", Icons.Default.DirectionsBus),
        PayCategory("Штрафы",    Icons.Default.Warning),
        PayCategory("Ещё",       Icons.Default.MoreHoriz),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Платежи",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VtbOnSurface
            )
        }
        item {
            val rows = categories.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { cat ->
                            PayCategoryCell(
                                category = cat,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (cat.topup) {
                                        context.startActivity(Intent(context, TopupInputActivity::class.java))
                                    } else {
                                        Toast.makeText(context, "Недоступно в демо-режиме", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayCategoryCell(category: PayCategory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(VtbBluePale, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = VtbBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(category.label, fontSize = 12.sp, color = VtbOnSurface, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── ПРОДУКТЫ ───────────────────────────────────────────────────────────────

private data class BankProduct(val name: String, val subtitle: String, val icon: ImageVector, val color: Color)

@Composable
fun ProductsTab(context: Context) {
    val products = listOf(
        BankProduct("Дебетовая карта",     "****5678 · 47 230 ₽",        Icons.Default.CreditCard,  VtbBlue),
        BankProduct("Кредитная карта",     "Лимит 50 000 ₽ · ****9012",  Icons.Default.Payment,     Color(0xFFE65100)),
        BankProduct("Накопительный счёт",  "6% годовых · 120 000 ₽",     Icons.Default.Savings,     VtbGreen),
        BankProduct("Инвестиции",          "Откройте брокерский счёт",    Icons.Default.TrendingUp,  Color(0xFF6A1B9A)),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Продукты",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VtbOnSurface
            )
        }
        items(products) { product ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(product.color.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            product.icon,
                            contentDescription = null,
                            tint = product.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = VtbOnSurface)
                        Text(product.subtitle, fontSize = 12.sp, color = VtbSecondary)
                    }
                    TextButton(
                        onClick = {
                            Toast.makeText(context, "Недоступно в демо-режиме", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Подробнее", fontSize = 12.sp, color = VtbBlue)
                    }
                }
            }
        }
    }
}

// ─── ИСТОРИЯ ────────────────────────────────────────────────────────────────

private data class TxItem(
    val title: String,
    val subtitle: String,
    val amount: String,
    val income: Boolean,
    val icon: ImageVector
)

@Composable
fun HistoryTab() {
    val transactions = listOf(
        TxItem("Мария К.",     "Перевод · 3 апр",    "−1 500 ₽",   false, Icons.AutoMirrored.Filled.Send),
        TxItem("Зачисление",   "Зарплата · 2 апр",   "+50 000 ₽",  true,  Icons.Default.AccountBalance),
        TxItem("МТС",          "Телефон · 1 апр",    "−300 ₽",     false, Icons.Default.Phone),
        TxItem("Яна С.",       "Перевод · 31 мар",   "−2 000 ₽",   false, Icons.AutoMirrored.Filled.Send),
        TxItem("Пятёрочка",    "Покупка · 30 мар",   "−847 ₽",     false, Icons.Default.ShoppingCart),
        TxItem("Кэшбэк",       "Возврат · 30 мар",   "+42 ₽",      true,  Icons.Default.Refresh),
        TxItem("Денис В.",     "Перевод · 29 мар",   "+5 000 ₽",   true,  Icons.Default.AccountBalance),
        TxItem("ЖД Билеты",    "Покупка · 28 мар",   "−3 200 ₽",   false, Icons.Default.Train),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "История операций",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VtbOnSurface
            )
        }
        items(transactions) { tx ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconColor = if (tx.income) VtbGreen else VtbSecondary
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(iconColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(tx.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VtbOnSurface)
                        Text(tx.subtitle, fontSize = 12.sp, color = VtbSecondary)
                    }
                    Text(
                        tx.amount,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tx.income) VtbGreen else VtbOnSurface
                    )
                }
            }
        }
    }
}

// ─── ЧАТ ────────────────────────────────────────────────────────────────────

private data class ChatMsg(val text: String, val fromBot: Boolean)

@Composable
fun ChatTab() {
    val messages = listOf(
        ChatMsg("Добро пожаловать в чат поддержки ВТБ. Чем могу помочь?", fromBot = true),
        ChatMsg("Мне нужна помощь с переводом", fromBot = false),
        ChatMsg("Для переводов воспользуйтесь голосовым помощником Vita — просто скажите «Переведи Маше 1000 рублей».", fromBot = true),
        ChatMsg("Как открыть накопительный счёт с лучшей ставкой?", fromBot = false),
        ChatMsg("Накопительный счёт «ВТБ Копилка» сейчас даёт до 8% годовых. Открыть можно в разделе Продукты.", fromBot = true),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Чат поддержки",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VtbOnSurface
                )
            }
            items(messages) { msg -> ChatBubble(msg) }
        }

        // Disabled input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                placeholder = { Text("Чат недоступен в демо-режиме", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = VtbDivider,
                    disabledPlaceholderColor = VtbSecondary
                )
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.fromBot) Alignment.Start else Alignment.End
    ) {
        if (msg.fromBot) {
            Text(
                "ВТБ",
                fontSize = 10.sp,
                color = VtbSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(
                topStart = if (msg.fromBot) 4.dp else 16.dp,
                topEnd = if (msg.fromBot) 16.dp else 4.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (msg.fromBot) Color.White else VtbBlue
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (msg.fromBot) 1.dp else 0.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                msg.text,
                fontSize = 14.sp,
                color = if (msg.fromBot) VtbOnSurface else Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
