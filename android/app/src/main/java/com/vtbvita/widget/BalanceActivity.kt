package com.vtbvita.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.ui.components.AmountInputCard
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaCompactInfoCard
import com.vtbvita.widget.ui.components.OmegaSheetScaffold
import com.vtbvita.widget.ui.components.VtbCardWithBadge
import com.vtbvita.widget.ui.components.vtbCardRes
import com.vtbvita.widget.ui.theme.OmegaBrandGradientH
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaStroke
import com.vtbvita.widget.ui.theme.OmegaSurfaceAlt
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.TitanGray
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    var accounts by remember { mutableStateOf<List<AccountInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInternalTransfer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { MockApiService.getBalance(context) }
            .onSuccess { accounts = it }
            .onFailure { e -> error = e.message ?: "Ошибка загрузки" }
    }

    val rubAccounts = accounts?.filter { it.currency == "RUB" } ?: emptyList()
    val pagerState = rememberPagerState(pageCount = { rubAccounts.size.coerceAtLeast(1) })
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val cardSize = screenWidthDp - 64.dp

    OmegaSheetScaffold(
        title = "",
        onDismiss = onDismiss,
        onBack = null,
        onClose = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = OmegaSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Мои счета",
                style = OmegaType.HeadlineL,
                color = OmegaTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Все ›",
                style = OmegaType.BodySemiBoldL,
                color = OmegaBrandPrimary,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }

        when {
            accounts == null && error == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = OmegaSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = OmegaBrandPrimary)
                }
            }
            error != null -> {
                Text(text = "Ошибка: $error", color = OmegaError, style = OmegaType.BodyL)
            }
            rubAccounts.isEmpty() -> {
                Text(text = "Нет счетов", color = OmegaTextSecondary, style = OmegaType.BodyL)
            }
            else -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = OmegaSpacing.md
                ) { page ->
                    AccountCard(acc = rubAccounts[page], size = cardSize)
                }

                Spacer(Modifier.height(OmegaSpacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(rubAccounts.size) { index ->
                        val isActive = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isActive) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color.White else TitanGray.v600)
                        )
                    }
                }

                Spacer(Modifier.height(OmegaSpacing.lg))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.md)
                ) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_action_transfer,
                        label = "Перевод",
                        onClick = {
                            context.startActivity(Intent(context, TransferFlowActivity::class.java))
                        }
                    )
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        iconRes = R.drawable.ic_action_internal,
                        label = "Между своими",
                        onClick = { showInternalTransfer = true }
                    )
                }

                Spacer(Modifier.height(OmegaSpacing.sm))
            }
        }
    }

    if (showInternalTransfer && rubAccounts.size >= 2) {
        InternalTransferSheet(
            accounts = rubAccounts,
            defaultFromIndex = pagerState.currentPage,
            onDismiss = { showInternalTransfer = false }
        )
    }
}

@Composable
private fun AccountCard(acc: AccountInfo, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(accountGradient(acc.type))
            .padding(OmegaSpacing.lg)
    ) {
        Image(
            painter = painterResource(vtbCardRes(acc.type)),
            contentDescription = null,
            modifier = Modifier.size(48.dp).align(Alignment.TopEnd),
            contentScale = ContentScale.Fit
        )

        Column(modifier = Modifier.align(Alignment.TopStart)) {
            Text(
                text = acc.name,
                style = OmegaType.BodyTightSemiBoldL,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = acc.masked,
                style = OmegaType.BodyTightM,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = formatAccountBalance(acc),
                style = OmegaType.HeadlineL,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (acc.paymentSystem.lowercase() == "mir") {
                Spacer(Modifier.height(4.dp))
                Image(
                    painter = painterResource(R.drawable.ps_mir_inv),
                    contentDescription = "МИР",
                    modifier = Modifier.width(20.dp).height(14.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(Modifier.height(OmegaSpacing.xs))
        Text(text = label, style = OmegaType.BodyTightM, color = OmegaTextPrimary)
    }
}

@Composable
private fun InternalTransferSheet(
    accounts: List<AccountInfo>,
    defaultFromIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fromIndex = defaultFromIndex.coerceIn(0, accounts.lastIndex)
    val fromAcc = accounts[fromIndex]
    val toOptions = accounts.filterIndexed { i, _ -> i != fromIndex }

    var toIndex by remember { mutableIntStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    val toAcc = toOptions.getOrNull(toIndex)

    OmegaSheetScaffold(
        title = "Между своими",
        onDismiss = onDismiss,
        onBack = onDismiss,
        footer = {
            Spacer(Modifier.height(OmegaSpacing.md))
            OmegaButton(
                text = if (amountText.isNotBlank()) "Перевести ${formatRub(amountText.toDoubleOrNull() ?: 0.0)}" else "Перевести",
                enabled = !isLoading && amountText.isNotBlank() && toAcc != null,
                isLoading = isLoading,
                style = OmegaButtonStyle.Brand,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0 || toAcc == null) return@OmegaButton
                    isLoading = true; error = null
                    scope.launch {
                        runCatching {
                            MockApiService.command(
                                intent = "transfer",
                                amount = amt,
                                recipient = toAcc.name,
                                phone = null,
                                context = context,
                                comment = ""
                            )
                        }.onSuccess { data ->
                            runCatching {
                                MockApiService.confirm(
                                    transactionId = data.transactionId,
                                    sourceAccountId = fromAcc.id,
                                    selectedBank = "ВТБ",
                                    context = context
                                )
                            }.onSuccess { result ->
                                successMsg = "✓ ${result.message}"
                                isLoading = false
                            }.onFailure { e ->
                                error = e.message ?: "Ошибка"; isLoading = false
                            }
                        }.onFailure { e ->
                            error = e.message ?: "Ошибка"; isLoading = false
                        }
                    }
                }
            )
            Spacer(Modifier.height(OmegaSpacing.sm))
        }
    ) {
        if (successMsg != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = OmegaSpacing.xl),
                contentAlignment = Alignment.Center
            ) {
                Text(successMsg!!, color = OmegaSuccess, style = OmegaType.HeadlineS)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
                OmegaCompactInfoCard(
                    label = "Откуда",
                    value = fromAcc.name,
                    subtitle = "${fromAcc.masked}  ${formatRub(fromAcc.balance)}",
                    subtitleStyle = OmegaType.BodyTightL,
                    subtitleColor = Color.White,
                    trailingContent = {
                        VtbCardWithBadge(
                            accountType = fromAcc.type,
                            paymentSystem = fromAcc.paymentSystem,
                            cardSize = 48.dp
                        )
                    }
                )

                Text("Куда", style = OmegaType.BodyTightM, color = OmegaTextSecondary)

                toOptions.forEachIndexed { i, acc ->
                    val isSelected = i == toIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(OmegaRadius.md)
                            .background(OmegaSurfaceAlt)
                            .border(
                                BorderStroke(OmegaStroke.regular, if (isSelected) Color.White else TitanGray.v600),
                                OmegaRadius.md
                            )
                            .clickable { toIndex = i }
                            .padding(horizontal = OmegaSpacing.md, vertical = OmegaSpacing.sm + OmegaSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VtbCardWithBadge(
                            accountType = acc.type,
                            paymentSystem = acc.paymentSystem,
                            cardSize = 40.dp
                        )
                        Spacer(Modifier.width(OmegaSpacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(acc.name, style = OmegaType.BodyTightSemiBoldL, color = Color.White)
                            Text(acc.masked, style = OmegaType.BodyTightM, color = OmegaTextSecondary)
                        }
                        if (isSelected) {
                            Text("✓", color = OmegaSuccess, style = OmegaType.BodyTightSemiBoldL)
                        }
                    }
                }

                AmountInputCard(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() } }
                )

                error?.let { Text(it, color = OmegaError, style = OmegaType.BodyTightM) }
                Spacer(Modifier.height(OmegaSpacing.xs))
            }
        }
    }
}

private fun accountGradient(type: String): Brush = when (type.lowercase()) {
    "savingsaccount" -> Brush.horizontalGradient(listOf(Color(0xFF1B7A4B), Color(0xFF009D1C)))
    "credit_card"    -> Brush.horizontalGradient(listOf(Color(0xFF1A1D23), Color(0xFF2F343C)))
    else             -> OmegaBrandGradientH
}

private fun formatAccountBalance(acc: AccountInfo): String =
    if (acc.currency == "RUB") String.format(Locale("ru"), "%,.0f ₽", acc.balance)
    else "${acc.balance.toLong()} ${acc.currency}"

private fun formatRub(amount: Double): String =
    String.format(Locale("ru"), "%,.0f ₽", amount)
