package com.vtbvita.widget

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.ui.components.OmegaSheetHeader
import com.vtbvita.widget.ui.theme.OmegaBackground
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaScrim
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaSurfaceAlt
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import java.util.Locale

class BalanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        setContent {
            VTBVitaTheme {
                BalanceBottomSheet(onDismiss = { finish() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }
}

@Composable
private fun BalanceBottomSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var accounts by remember { mutableStateOf<List<AccountInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        runCatching { MockApiService.getBalance(context) }
            .onSuccess { accounts = it }
            .onFailure { e -> error = e.message ?: "Ошибка загрузки" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OmegaScrim)
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
                colors = CardDefaults.cardColors(containerColor = OmegaBackground)
            ) {
                Column {
                    OmegaSheetHeader(title = "Счета и балансы")

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when {
                            accounts == null && error == null -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = OmegaBrandPrimary)
                                }
                            }
                            error != null -> {
                                Text(
                                    text = "Ошибка: $error",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            else -> {
                                accounts!!.forEach { acc ->
                                    BalanceAccountCard(acc)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceAccountCard(acc: AccountInfo) {
    val isSavings = acc.id.contains("saving", ignoreCase = true) ||
                    acc.name.contains("накоп", ignoreCase = true) ||
                    acc.name.contains("вклад", ignoreCase = true)
    val accentColor = if (isSavings) OmegaSuccess else OmegaBrandPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmegaSurfaceAlt, RoundedCornerShape(14.dp))
            .padding(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(60.dp)
                .background(accentColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(acc.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    acc.masked,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatRub(acc.balance),
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    acc.balance < 0 -> MaterialTheme.colorScheme.error
                    isSavings -> OmegaSuccess
                    else -> OmegaBrandPrimary
                }
            )
        }
    }
}

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)