package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.R
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.components.OmegaSheetHeader
import com.vtbvita.widget.ui.components.OmegaTextField
import com.vtbvita.widget.ui.components.SuccessAction
import com.vtbvita.widget.ui.components.TransferDetailsSheet
import com.vtbvita.widget.ui.components.OmegaSuccessScreen
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaScrim
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Единый модал для флоу перевода через NLP.
 *
 * Внутренняя навигация (Compose state):
 *   ContactSelection → (выбор контакта) → Confirmation
 *   Confirmation     → (back)            → ContactSelection
 *   ContactSelection → (back)            → finish()
 */
class TransferFlowActivity : ComponentActivity() {

    sealed class Screen {
        data class ContactSelection(
            val candidates: List<ContactCandidate>,
            val recipientRaw: String,
            val amount: Double?
        ) : Screen()

        data class Confirmation(
            val contact: ContactCandidate,
            val amount: Double?,
            val candidates: List<ContactCandidate>,
            val recipientRaw: String
        ) : Screen()

        data class Success(
            val contact: ContactCandidate,
            val amount: Double,
            val statusMsg: String
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
            Screen.Confirmation(autoContact, amount, candidates, recipientRaw)
        } else {
            Screen.ContactSelection(candidates, recipientRaw, amount)
        }

        setContent {
            VTBVitaTheme {
                var screen by remember { mutableStateOf<Screen>(initialScreen) }

                BackHandler(enabled = screen is Screen.Confirmation) {
                    val s = screen as Screen.Confirmation
                    screen = Screen.ContactSelection(s.candidates, s.recipientRaw, s.amount)
                }
                // На Success-экране back отключён — только кнопка «На главную»
                BackHandler(enabled = screen is Screen.Success) { /* заблокировано */ }

                when (val s = screen) {
                    is Screen.ContactSelection -> {
                        ContactSelectionSheet(
                            candidates = s.candidates,
                            recipientRaw = s.recipientRaw,
                            onContactSelected = { contact ->
                                ContactMemory.recordPick(s.recipientRaw, contact.phone, this)
                                screen = Screen.Confirmation(contact, s.amount, s.candidates, s.recipientRaw)
                            },
                            onDismiss = { finish() }
                        )
                    }
                    is Screen.Confirmation -> {
                        TransferDetailsSheet(
                            recipientName = s.contact.displayName,
                            recipientPhone = s.contact.phone,
                            bankDisplayName = s.contact.bankDisplayName,
                            prefillAmount = s.amount ?: 0.0,
                            onDismiss = {
                                screen = Screen.ContactSelection(s.candidates, s.recipientRaw, s.amount)
                            },
                            onSuccess = { msg ->
                                screen = Screen.Success(
                                    contact = s.contact,
                                    amount = s.amount ?: 0.0,
                                    statusMsg = msg
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
                                SuccessAction("🧾", "Получить чек"),
                                SuccessAction("🔁", "Создать шаблон"),
                            ),
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

    // Dim overlay
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
        // Sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(OmegaSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            OmegaSheetHeader(title = "Кому переводим?")

            // Поиск
            OmegaTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "",
                placeholder = "Поиск по контактам",
                modifier = Modifier.padding(horizontal = 16.dp),
                trailingContent = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = OmegaTextSecondary
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_magnifier),
                            contentDescription = null,
                            tint = OmegaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 280.dp, max = 440.dp)
            ) {
                when {
                    displayedCandidates.isEmpty() && searchText.isNotBlank() && !isSearching -> {
                        item {
                            Text(
                                "Контакты не найдены",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OmegaTextSecondary,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    }
                    displayedCandidates.isEmpty() && searchText.isBlank() -> {
                        item {
                            Text(
                                "Начните вводить имя",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OmegaTextSecondary,
                                modifier = Modifier.padding(vertical = 20.dp)
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
                item { Spacer(Modifier.height(16.dp)) }
            }
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
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар-инициалы — тёмный chip стиль ВТБ
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

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OmegaTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                if (pickCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (pickCount >= ContactMemory.AUTO_RESOLVE_AT) "★" else "↑",
                        fontSize = 11.sp,
                        color = OmegaBrandPrimary
                    )
                }
            }
            Text(
                ContactMatcher.maskPhone(candidate.phone),
                style = MaterialTheme.typography.bodySmall,
                color = OmegaTextSecondary
            )
        }
    }
}