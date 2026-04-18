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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaSheetHeader
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaScrim
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.VTBVitaTheme

class ContactDisambiguationActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_AMOUNT = "amount"

        var pendingCandidates: List<ContactCandidate> = emptyList()
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
        val pickCounts = ContactMemory.getPickCounts(recipientRaw, this)

        setContent {
            VTBVitaTheme {
                DisambiguationSheet(
                    candidates = candidates,
                    pickCounts = pickCounts,
                    onContactSelected = { contact ->
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
            .background(OmegaScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
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

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(max = 480.dp)
            ) {
                items(candidates) { candidate ->
                    val pickCount = pickCounts[candidate.phone] ?: 0
                    CandidateRow(
                        candidate = candidate,
                        pickCount = pickCount,
                        onClick = { onContactSelected(candidate) }
                    )
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    OmegaButton(
                        text = "Другой получатель",
                        style = OmegaButtonStyle.Brand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = onOtherContact
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
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
