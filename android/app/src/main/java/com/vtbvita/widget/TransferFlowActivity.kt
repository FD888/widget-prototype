package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.R
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.AccountInfo
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaSheetScaffold
import com.vtbvita.widget.ui.components.OmegaTextField
import com.vtbvita.widget.ui.components.OmegaWarningCard
import com.vtbvita.widget.ui.components.SbpRow
import com.vtbvita.widget.ui.components.SuccessAction
import com.vtbvita.widget.ui.components.TransferDetailsSheet
import com.vtbvita.widget.ui.components.TransferSummary
import com.vtbvita.widget.ui.components.OmegaSuccessScreen
import com.vtbvita.widget.ui.components.formatRub
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferFlowActivity : ComponentActivity() {

    sealed class Screen {
        data class ContactSelection(
            val candidates: List<ContactCandidate>,
            val recipientRaw: String,
            val amount: Double?
        ) : Screen()

        data class TransferSetup(
            val contact: ContactCandidate,
            val amount: Double?,
            val candidates: List<ContactCandidate>,
            val recipientRaw: String
        ) : Screen()

        data class TransferConfirm(
            val contact: ContactCandidate,
            val data: ConfirmationData,
            val selectedAcc: AccountInfo?,
            val bank: String,
            val comment: String,
            val candidates: List<ContactCandidate>,
            val recipientRaw: String
        ) : Screen()

        data class Success(
            val contact: ContactCandidate,
            val amount: Double,
            val statusMsg: String,
            val selectedAcc: AccountInfo? = null,
            val comment: String = ""
        ) : Screen()
    }

    companion object {
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_START_CONFIRMED = "start_confirmed"

        var pendingCandidates: List<ContactCandidate> = emptyList()
        var pendingRecipientRaw: String = ""
        var pendingAutoContact: ContactCandidate? = null

        fun newIntentAmbiguous(context: Context, amount: Double? = null): Intent =
            Intent(context, TransferFlowActivity::class.java).apply {
                amount?.let { putExtra(EXTRA_AMOUNT, it) }
            }

        fun newIntentAutoResolved(context: Context, amount: Double? = null): Intent =
            Intent(context, TransferFlowActivity::class.java).apply {
                amount?.let { putExtra(EXTRA_AMOUNT, it) }
                putExtra(EXTRA_START_CONFIRMED, true)
            }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)

        val amount = if (intent.hasExtra(EXTRA_AMOUNT)) intent.getDoubleExtra(EXTRA_AMOUNT, 0.0) else null
        val startConfirmed = intent.getBooleanExtra(EXTRA_START_CONFIRMED, false)

        val candidates = pendingCandidates
        val recipientRaw = pendingRecipientRaw
        val autoContact = pendingAutoContact

        val initialScreen: Screen = if (startConfirmed && autoContact != null) {
            Screen.TransferSetup(autoContact, amount, candidates, recipientRaw)
        } else {
            Screen.ContactSelection(candidates, recipientRaw, amount)
        }

        setContent {
            VTBVitaTheme {
                var screen by remember { mutableStateOf<Screen>(initialScreen) }

                BackHandler(enabled = screen is Screen.TransferSetup) {
                    val s = screen as Screen.TransferSetup
                    screen = Screen.ContactSelection(s.candidates, s.recipientRaw, s.amount)
                }
                BackHandler(enabled = screen is Screen.TransferConfirm) {
                    val s = screen as Screen.TransferConfirm
                    screen = Screen.TransferSetup(s.contact, s.data.amount.takeIf { it > 0 }, s.candidates, s.recipientRaw)
                }
                BackHandler(enabled = screen is Screen.Success) { /* заблокировано */ }

                when (val s = screen) {
                    is Screen.ContactSelection -> {
                        ContactSelectionSheet(
                            candidates = s.candidates,
                            recipientRaw = s.recipientRaw,
                            onContactSelected = { contact ->
                                ContactMemory.recordPick(s.recipientRaw, contact.phone, this)
                                screen = Screen.TransferSetup(contact, s.amount, s.candidates, s.recipientRaw)
                            },
                            onDismiss = { finish() }
                        )
                    }
                    is Screen.TransferSetup -> {
                        TransferDetailsSheet(
                            recipientName = s.contact.displayName,
                            recipientPhone = s.contact.phone,
                            bankDisplayName = s.contact.bankDisplayName,
                            prefillAmount = s.amount ?: 0.0,
                            onDismiss = {
                                screen = Screen.ContactSelection(s.candidates, s.recipientRaw, s.amount)
                            },
                            onClose = { finish() },
                            onProceed = { data, selectedAcc, bank, comment ->
                                screen = Screen.TransferConfirm(
                                    contact = s.contact,
                                    data = data,
                                    selectedAcc = selectedAcc,
                                    bank = bank,
                                    comment = comment,
                                    candidates = s.candidates,
                                    recipientRaw = s.recipientRaw
                                )
                            }
                        )
                    }
                    is Screen.TransferConfirm -> {
                        TransferConfirmSheet(
                            screen = s,
                            onBack = {
                                screen = Screen.TransferSetup(
                                    s.contact,
                                    s.data.amount.takeIf { it > 0 },
                                    s.candidates,
                                    s.recipientRaw
                                )
                            },
                            onClose = { finish() },
                            onSuccess = { msg ->
                                screen = Screen.Success(
                                    contact = s.contact,
                                    amount = s.data.amount,
                                    statusMsg = msg,
                                    selectedAcc = s.selectedAcc,
                                    comment = s.comment
                                )
                            }
                        )
                    }
                    is Screen.Success -> {
                        OmegaSuccessScreen(
                            title = "Перевод выполнен",
                            subtitle = "По номеру телефона · ${s.contact.displayName}",
                            amount = s.amount,
                            actions = listOf(
                                SuccessAction(R.drawable.ic_receipt, "Получить чек"),
                                SuccessAction(R.drawable.ic_bookmark_plus, "Создать шаблон"),
                            ),
                            content = if (s.selectedAcc != null) ({
                                val balanceAfter = s.selectedAcc.balance - s.amount
                                TransferSummary(
                                    accountName = s.selectedAcc.name,
                                    accountMask = s.selectedAcc.masked,
                                    balanceAfter = balanceAfter,
                                    comment = s.comment
                                )
                            }) else null,
                            onDone = {
                                VitaWidgetProvider.showStatus(applicationContext, s.statusMsg)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── TransferConfirmSheet ─────────────────────────────────────────────────────

@Composable
private fun TransferConfirmSheet(
    screen: TransferFlowActivity.Screen.TransferConfirm,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val data = screen.data
    val amount = data.amount
    val selectedAcc = screen.selectedAcc
    val balanceAfter = (selectedAcc?.balance ?: 0.0) - amount

    OmegaSheetScaffold(
        title = "Подтверждение",
        onDismiss = onBack,
        onBack = onBack,
        onClose = onClose,
        footer = {
            if (error != null) {
                Text(
                    error!!,
                    color = OmegaError,
                    style = OmegaType.BodyTightS,
                    modifier = Modifier.padding(bottom = OmegaSpacing.sm)
                )
            }
            OmegaButton(
                text = "Перевести ${formatRub(amount)}",
                isLoading = isLoading,
                enabled = !isLoading,
                style = OmegaButtonStyle.Brand,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    isLoading = true; error = null
                    scope.launch {
                        runCatching {
                            MockApiService.confirm(
                                transactionId = data.transactionId,
                                sourceAccountId = selectedAcc?.id ?: data.defaultAccountId,
                                selectedBank = screen.bank,
                                context = context
                            )
                        }.onSuccess { result ->
                            onSuccess("✓ ${result.message}")
                        }.onFailure { e ->
                            error = e.message ?: "Ошибка"; isLoading = false
                        }
                    }
                }
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
            Text(
                text = formatRub(amount),
                style = OmegaType.DisplayL,
                color = OmegaTextPrimary
            )

            OmegaWarningCard(
                text = "Проверьте данные перед отправкой. Отменить перевод после подтверждения невозможно."
            )

            TransferSummary(
                accountName = selectedAcc?.name ?: data.defaultAccountId,
                accountMask = selectedAcc?.masked ?: "",
                balanceAfter = balanceAfter,
                comment = screen.comment
            )

            val isPhoneTransfer = screen.contact.phone.isNotBlank()
            if (isPhoneTransfer) {
                SbpRow()
            }

            Spacer(Modifier.height(OmegaSpacing.xs))
        }
    }
}

// ── ContactSelectionSheet ────────────────────────────────────────────────────

@Composable
private fun ContactSelectionSheet(
    candidates: List<ContactCandidate>,
    recipientRaw: String,
    onContactSelected: (ContactCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pickCounts = remember { ContactMemory.getPickCounts(recipientRaw, context) }

    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ContactCandidate>?>(null) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchText) {
        if (searchText.isBlank()) {
            searchResults = null
            isSearching = false
        } else {
            isSearching = true
            delay(150)
            searchResults = withContext(Dispatchers.IO) {
                ContactMatcher.search(searchText, context)
            }
            isSearching = false
        }
    }

    val displayedCandidates = searchResults ?: candidates

    OmegaSheetScaffold(
        title = "Кому переводим?",
        onDismiss = onDismiss,
        onBack = null,
        onClose = onDismiss
    ) {
        OmegaTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = "",
            placeholder = "Поиск по контактам",
            trailingContent = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = OmegaTextSecondary
                    )
                } else {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_magnifier),
                        contentDescription = null,
                        tint = OmegaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        Spacer(Modifier.height(OmegaSpacing.sm))

        LazyColumn(
            contentPadding = PaddingValues(vertical = OmegaSpacing.xs),
            modifier = Modifier.heightIn(min = 280.dp, max = 440.dp)
        ) {
            when {
                displayedCandidates.isEmpty() && searchText.isNotBlank() && !isSearching -> {
                    item {
                        Text(
                            "Контакты не найдены",
                            style = OmegaType.BodyM,
                            color = OmegaTextSecondary,
                            modifier = Modifier.padding(vertical = OmegaSpacing.xl)
                        )
                    }
                }
                displayedCandidates.isEmpty() && searchText.isBlank() -> {
                    item {
                        Text(
                            "Начните вводить имя",
                            style = OmegaType.BodyM,
                            color = OmegaTextSecondary,
                            modifier = Modifier.padding(vertical = OmegaSpacing.xl)
                        )
                    }
                }
                else -> {
                    items(displayedCandidates) { candidate ->
                        val picks = if (searchText.isBlank()) {
                            pickCounts[candidate.phone] ?: 0
                        } else {
                            candidate.pickCount
                        }
                        ContactCandidateRow(
                            candidate = candidate,
                            pickCount = picks,
                            onClick = { onContactSelected(candidate) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(OmegaSpacing.lg)) }
        }
    }
}

@Composable
private fun ContactCandidateRow(
    candidate: ContactCandidate,
    pickCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = OmegaSpacing.md + OmegaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(OmegaChip),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = candidate.displayName.firstOrNull()?.uppercase() ?: "#",
                color = OmegaTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.width(OmegaSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.displayName,
                    style = OmegaType.BodySemiBoldL,
                    color = OmegaTextPrimary
                )
                if (pickCount > 0) {
                    Spacer(Modifier.width(OmegaSpacing.xs + OmegaSpacing.xxs))
                    Text(
                        text = if (pickCount >= ContactMemory.AUTO_RESOLVE_AT) "★" else "↑",
                        fontSize = 11.sp,
                        color = OmegaBrandPrimary
                    )
                }
            }
            Text(
                ContactMatcher.maskPhone(candidate.phone),
                style = OmegaType.BodyTightM,
                color = OmegaTextSecondary
            )
        }
    }
}
