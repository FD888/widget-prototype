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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.R
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.vtbvita.widget.ui.theme.GalaxyPurple
import com.vtbvita.widget.ui.theme.OmegaBackground
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaErrorBg
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaSurfaceAlt
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaTextDisabled
import com.vtbvita.widget.ui.theme.OmegaTextHint
import com.vtbvita.widget.ui.theme.OmegaTextOnBrand
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.OmegaWarning
import com.vtbvita.widget.ui.theme.GalaxyPurple.v500
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import java.util.Locale

private val TAB_NAMES = listOf("Главная", "Платежи", "Продукты", "История", "Чат")

class MockBankActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
        const val EXTRA_TAB = "tab"
        const val EXTRA_CHAT_TEXT = "chat_text"
        const val TAB_PRODUCTS = 2
        const val TAB_CHAT = 4

        fun productsIntent(context: Context): Intent =
            Intent(context, MockBankActivity::class.java).apply {
                putExtra(EXTRA_TAB, TAB_PRODUCTS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

        fun chatIntent(context: Context, prefillText: String? = null): Intent =
            Intent(context, MockBankActivity::class.java).apply {
                putExtra(EXTRA_TAB, TAB_CHAT)
                prefillText?.let { putExtra(EXTRA_CHAT_TEXT, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
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
        val initialTab = intent.getIntExtra(EXTRA_TAB, 0)
        val chatPrefillText = intent.getStringExtra(EXTRA_CHAT_TEXT)

        setContent {
            VTBVitaTheme {
                MockBankScreen(
                    personaId = personaId,
                    initialTab = initialTab,
                    chatPrefillText = chatPrefillText,
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
fun MockBankScreen(personaId: String, initialTab: Int = 0, chatPrefillText: String? = null, onLogout: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(OmegaBackground)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(OmegaBrandPrimary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦", color = OmegaTextOnBrand, style = OmegaType.BodyTightM)
            Spacer(Modifier.width(8.dp))
            Text(
                "VTB Vita · демо-режим",
                color = OmegaTextOnBrand,
                style = OmegaType.BodyTightM,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(OmegaTextOnBrand.copy(alpha = 0.15f), CircleShape)
                    .clickable(onClick = onLogout),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_exit),
                    contentDescription = "Выйти",
                    tint = OmegaTextOnBrand,
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
                4 -> ChatTab(prefillText = chatPrefillText)
            }
        }

        // Bottom tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OmegaBrandPrimary)
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
                        .semantics {
                            role = Role.Tab
                            stateDescription = if (i == selectedTab) "Выбрано" else "Не выбрано"
                            contentDescription = label
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                if (i == selectedTab) OmegaTextOnBrand else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        label,
                        style = OmegaType.BodyTightS,
                        color = if (i == selectedTab) OmegaTextOnBrand else OmegaTextOnBrand.copy(alpha = 0.5f),
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
                style = OmegaType.HeadlineL,
                fontWeight = FontWeight.Bold,
                color = OmegaTextPrimary,
                modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
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
                    CircularProgressIndicator(color = OmegaBrandPrimary)
                }
            }
            error != null -> item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OmegaErrorBg)
                ) {
                    Text(
                        "Не удалось загрузить счета: $error",
                        modifier = Modifier.padding(16.dp),
                        color = OmegaError,
                        style = OmegaType.BodyTightM
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
                colors = ButtonDefaults.buttonColors(containerColor = OmegaBrandPrimary)
            ) {
                Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = "Открыть Vita", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Открыть Vita")
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            WidgetSettingsCard(context)
        }
    }
}

@Composable
private fun WidgetSettingsCard(context: Context) {
    var biometricEnabled by remember {
        mutableStateOf(SessionManager.isBiometricEnabled(context))
    }
    val biometricAvailable = remember {
        val mgr = androidx.biometric.BiometricManager.from(context)
        val code = mgr.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        code != androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = OmegaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = OmegaSurfaceAlt),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Настройки виджета",
                style = OmegaType.BodyTightMediumL,
                fontWeight = FontWeight.SemiBold,
                color = OmegaTextPrimary
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fingerprint),
                    contentDescription = "Биометрия",
                    tint = if (biometricAvailable) OmegaBrandPrimary else OmegaTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Биометрия",
                        style = OmegaType.BodyTightL,
                        fontWeight = FontWeight.Medium,
                        color = OmegaTextPrimary
                    )
                    Text(
                        if (biometricAvailable) "Вход по отпечатку / лицу вместо PIN"
                        else "Биометрический сенсор не обнаружен",
                        style = OmegaType.BodyTightM,
                        color = OmegaTextSecondary
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = { enabled ->
                        biometricEnabled = enabled
                        SessionManager.setBiometricEnabled(context, enabled)
                    },
                    enabled = true
                )
            }
        }
    }
}

@Composable
fun AccountCard(account: AccountInfo) {
    val cardColor = when (account.type) {
        "debit"   -> OmegaBrandPrimary
        "credit"  -> OmegaWarning
        "savings" -> OmegaSuccess
        else      -> OmegaBrandPrimary
    }
    val typeLabel = when (account.type) {
        "debit"   -> "Дебетовая карта"
        "credit"  -> "Кредитная карта"
        "savings" -> "Накопительный счёт"
        else      -> account.type
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = OmegaRadius.lg,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_card_arrow_right),
                    contentDescription = typeLabel,
                    tint = OmegaTextOnBrand.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(typeLabel, style = OmegaType.BodyTightM, color = OmegaTextOnBrand.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                formatRub(account.balance),
                style = OmegaType.PinDigit,
                fontWeight = FontWeight.Bold,
                color = OmegaTextOnBrand
            )
            Spacer(Modifier.height(4.dp))
            Text(account.masked, style = OmegaType.BodyTightM, color = OmegaTextOnBrand.copy(alpha = 0.65f))
        }
    }
}

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.2f ₽", amount)

// ─── ПЛАТЕЖИ ────────────────────────────────────────────────────────────────

private data class PayCategory(
    val label: String,
    val iconRes: Int,
    val topup: Boolean = false
)

@Composable
fun PaymentsTab(context: Context) {
    val categories = listOf(
        PayCategory("ЖКХ",       R.drawable.ic_home),
        PayCategory("Телефон",   R.drawable.ic_phone, topup = true),
        PayCategory("Интернет",  R.drawable.ic_globe),
        PayCategory("Транспорт", R.drawable.ic_bus),
        PayCategory("Штрафы",    R.drawable.ic_shield_fingerprint),
        PayCategory("Ещё",       R.drawable.ic_magnifier),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Платежи",
                style = OmegaType.HeadlineL,
                fontWeight = FontWeight.Bold,
                color = OmegaTextPrimary,
                modifier = Modifier.semantics { heading() }
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
        shape = OmegaRadius.md,
        colors = CardDefaults.cardColors(containerColor = OmegaSurfaceAlt),
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
                    .background(OmegaChip, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(category.iconRes),
                    contentDescription = category.label,
                    tint = OmegaBrandPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(category.label, style = OmegaType.BodyTightM, color = OmegaTextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── ПРОДУКТЫ ───────────────────────────────────────────────────────────────

private data class BankProduct(
    val name: String,
    val subtitle: String,
    val iconRes: Int,
    val color: Color
)

@Composable
fun ProductsTab(context: Context) {
    val products = listOf(
        BankProduct("Дебетовая карта",     "****5678 · 47 230 ₽",        R.drawable.ic_card_arrow_right, OmegaBrandPrimary),
        BankProduct("Кредитная карта",     "Лимит 50 000 ₽ · ****9012",  R.drawable.ic_card_line,        OmegaWarning),
        BankProduct("Накопительный счёт",  "6% годовых · 120 000 ₽",     R.drawable.ic_wallet,           OmegaSuccess),
        BankProduct("Инвестиции",          "Откройте брокерский счёт",    R.drawable.ic_chart_zigzag,     GalaxyPurple.v500),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Продукты",
                style = OmegaType.HeadlineL,
                fontWeight = FontWeight.Bold,
                color = OmegaTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }
        items(products) { product ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = OmegaRadius.md,
                colors = CardDefaults.cardColors(containerColor = OmegaSurfaceAlt),
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
                            painter = painterResource(product.iconRes),
                            contentDescription = product.name,
                            tint = product.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, style = OmegaType.BodyTightL, fontWeight = FontWeight.SemiBold, color = OmegaTextPrimary)
                        Text(product.subtitle, style = OmegaType.BodyTightM, color = OmegaTextSecondary)
                    }
                    TextButton(
                        onClick = {
                            Toast.makeText(context, "Недоступно в демо-режиме", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Подробнее", style = OmegaType.BodyTightM, color = OmegaBrandPrimary)
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
    val iconRes: Int
)

@Composable
fun HistoryTab() {
    val transactions = listOf(
        TxItem("Мария К.",     "Перевод · 3 апр",    "−1 500 ₽",   false, R.drawable.ic_vtb_arrow_right),
        TxItem("Зачисление",   "Зарплата · 2 апр",   "+50 000 ₽",  true,  R.drawable.ic_card_line),
        TxItem("МТС",          "Телефон · 1 апр",    "−300 ₽",     false, R.drawable.ic_phone),
        TxItem("Яна С.",       "Перевод · 31 мар",   "−2 000 ₽",   false, R.drawable.ic_vtb_arrow_right),
        TxItem("Пятёрочка",    "Покупка · 30 мар",   "−847 ₽",     false, R.drawable.ic_cart),
        TxItem("Кэшбэк",       "Возврат · 30 мар",   "+42 ₽",      true,  R.drawable.ic_repeat),
        TxItem("Денис В.",     "Перевод · 29 мар",   "+5 000 ₽",   true,  R.drawable.ic_card_line),
        TxItem("ЖД Билеты",    "Покупка · 28 мар",   "−3 200 ₽",   false, R.drawable.ic_train),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "История операций",
                style = OmegaType.HeadlineL,
                fontWeight = FontWeight.Bold,
                color = OmegaTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
        }
        items(transactions) { tx ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = OmegaRadius.md,
                colors = CardDefaults.cardColors(containerColor = OmegaSurfaceAlt),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconColor = if (tx.income) OmegaSuccess else OmegaTextSecondary
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(iconColor.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painter = painterResource(tx.iconRes), contentDescription = tx.title, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tx.title, style = OmegaType.BodyTightL, fontWeight = FontWeight.Medium, color = OmegaTextPrimary)
                        Text(tx.subtitle, style = OmegaType.BodyTightM, color = OmegaTextSecondary)
                    }
                    Text(
                        tx.amount,
                        style = OmegaType.BodyTightL,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tx.income) OmegaSuccess else OmegaTextPrimary
                    )
                }
            }
        }
    }
}

// ─── ЧАТ ────────────────────────────────────────────────────────────────────

private data class ChatMsg(val text: String, val fromBot: Boolean)

@Composable
fun ChatTab(prefillText: String? = null) {
    var inputText by remember { mutableStateOf(prefillText ?: "") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(prefillText) {
        if (!prefillText.isNullOrBlank()) {
            focusRequester.requestFocus()
        }
    }

    val botResponse = when {
        inputText.contains("перевод", ignoreCase = true) -> "Для переводов используйте голосового помощника Vita — скажите «Переведи Маше 1000 рублей» в виджете."
        inputText.contains("баланс", ignoreCase = true) -> "Ваш баланс можно узнать через виджет Vita или в разделе «Главная»."
        inputText.contains("кредит", ignoreCase = true) -> "Кредитные продукты доступны в разделе «Продукты». Хотите оформить заявку?"
        inputText.contains("карт", ignoreCase = true) -> "Информация по картам — в разделе «Главная». Могу ли я ещё чем-то помочь?"
        else -> null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Чат поддержки",
                    style = OmegaType.HeadlineL,
                    fontWeight = FontWeight.Bold,
                    color = OmegaTextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
            }
            item { ChatBubble(ChatMsg("Здравствуйте! Я — виртуальный ассистент ВТБ. Чем могу помочь?", fromBot = true)) }
            if (!prefillText.isNullOrBlank()) {
                item { ChatBubble(ChatMsg(prefillText, fromBot = false)) }
                item {
                    ChatBubble(ChatMsg(
                        "Могу помочь с этим вопросом. Подскажите подробности, или воспользуйтесь голосовым помощником Vita.",
                        fromBot = true
                    ))
                }
            }
            if (botResponse != null && inputText.isNotBlank() && inputText != prefillText) {
                item { ChatBubble(ChatMsg(inputText, fromBot = false)) }
                item { ChatBubble(ChatMsg(botResponse, fromBot = true)) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OmegaSurfaceAlt)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Напишите сообщение...", style = OmegaType.BodyTightM, color = OmegaTextHint) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                shape = OmegaRadius.xxl,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    cursorColor = OmegaBrandPrimary,
                    focusedBorderColor = OmegaBrandPrimary,
                    unfocusedBorderColor = OmegaChip,
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
                style = OmegaType.BodyTightS,
                color = OmegaTextSecondary,
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
                containerColor = if (msg.fromBot) OmegaSurfaceAlt else OmegaBrandPrimary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (msg.fromBot) 1.dp else 0.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                msg.text,
                style = OmegaType.BodyParagraphM,
                color = if (msg.fromBot) OmegaTextPrimary else OmegaTextOnBrand,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}