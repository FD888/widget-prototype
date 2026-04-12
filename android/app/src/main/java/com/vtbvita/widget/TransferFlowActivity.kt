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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.components.TransferDetailsSheet
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBluePale
import com.vtbvita.widget.ui.theme.VtbSecondary
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
 *
 * ContactSelection показывает отфильтрованных топ-кандидатов + inline поиск по всем контактам.
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
    }

    companion object {
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_START_CONFIRMED = "start_confirmed"

        /** Кандидаты для disambiguation / back-навигации. Устанавливается перед startActivity. */
        var pendingCandidates: List<ContactCandidate> = emptyList()
        var pendingRecipientRaw: String = ""

        /**
         * При авторезолве — контакт уже известен, стартуем на экране Confirmation.
         * pendingCandidates хранит альтернативы для back-навигации.
         */
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

                // Hardware/gesture back
                BackHandler(enabled = screen is Screen.Confirmation) {
                    val s = screen as Screen.Confirmation
                    screen = Screen.ContactSelection(s.candidates, s.recipientRaw, s.amount)
                }

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
                                VitaWidgetProvider.showStatus(applicationContext, msg)
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
            delay(150) // debounce
            searchResults = withContext(Dispatchers.IO) {
                ContactMatcher.search(searchText, context)
            }
            isSearching = false
        }
    }

    val displayedCandidates = searchResults ?: candidates

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Handle bar
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(2.dp)
                        )
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Кому переводим?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(12.dp))

                // Поиск
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("Поиск по контактам") },
                    leadingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 300.dp)
                ) {
                    if (displayedCandidates.isEmpty() && searchText.isNotBlank() && !isSearching) {
                        item {
                            Text(
                                "Контакты не найдены",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else if (displayedCandidates.isEmpty() && searchText.isBlank()) {
                        item {
                            Text(
                                "Начните вводить имя для поиска",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
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
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(VtbBluePale, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = candidate.displayName.firstOrNull()?.uppercase() ?: "#",
                color = VtbBlue,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(candidate.displayName, style = MaterialTheme.typography.bodyLarge)
                if (pickCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (pickCount >= ContactMemory.AUTO_RESOLVE_AT) "★" else "↑",
                        fontSize = 11.sp,
                        color = VtbBlue
                    )
                }
            }
            Text(
                ContactMatcher.maskPhone(candidate.phone),
                style = MaterialTheme.typography.bodySmall,
                color = VtbSecondary
            )
        }
    }
}
