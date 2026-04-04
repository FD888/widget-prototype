package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBluePale
import com.vtbvita.widget.ui.theme.VtbSecondary

/**
 * Экран выбора получателя когда NLP-поиск вернул несколько кандидатов.
 * Отображается как bottom sheet поверх предыдущего контента.
 *
 * Кандидаты передаются через companion object (in-memory, протип).
 */
class ContactDisambiguationActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_AMOUNT = "amount"

        /** Временное хранилище кандидатов — устанавливается перед startActivity. */
        var pendingCandidates: List<ContactCandidate> = emptyList()

        /**
         * Исходный recipient_raw из NLP (напр. "маме") — нужен для записи выбора в ContactMemory.
         * Устанавливается одновременно с pendingCandidates.
         */
        var pendingRecipientRaw: String = ""

        fun newIntent(context: Context, amount: Double? = null): Intent =
            Intent(context, ContactDisambiguationActivity::class.java).apply {
                amount?.let { putExtra(EXTRA_AMOUNT, it) }
            }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        val amount = if (intent.hasExtra(EXTRA_AMOUNT))
            intent.getDoubleExtra(EXTRA_AMOUNT, 0.0) else null
        val candidates = pendingCandidates
        val recipientRaw = pendingRecipientRaw

        // Узнаём сколько раз выбирали каждый контакт (для метки "Недавно")
        val pickCounts = ContactMemory.getPickCounts(recipientRaw, this)

        setContent {
            VTBVitaTheme {
                DisambiguationSheet(
                    candidates = candidates,
                    pickCounts = pickCounts,
                    onContactSelected = { contact ->
                        // Записываем выбор — в следующий раз score будет выше
                        ContactMemory.recordPick(recipientRaw, contact.phone, this)
                        startActivity(
                            TransferDetailsActivity.newIntent(
                                this,
                                recipientName = contact.displayName,
                                recipientPhone = contact.phone,
                                amount = amount ?: 0.0,
                                bankDisplayName = contact.bankDisplayName
                            ).also { BankingSession.putInIntent(it) }
                        )
                        finish()
                    },
                    onOtherContact = {
                        startActivity(
                            ContactPickerActivity.newIntent(this, amount)
                                .also { BankingSession.putInIntent(it) }
                        )
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun DisambiguationSheet(
    candidates: List<ContactCandidate>,
    pickCounts: Map<String, Int>,
    onContactSelected: (ContactCandidate) -> Unit,
    onOtherContact: () -> Unit,
    onDismiss: () -> Unit
) {
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
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    items(candidates) { candidate ->
                        val pickCount = pickCounts[candidate.phone] ?: 0
                        CandidateRow(
                            candidate = candidate,
                            pickCount = pickCount,
                            onClick = { onContactSelected(candidate) }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOtherContact,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text("Другой получатель")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: ContactCandidate, pickCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар-инициал
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
