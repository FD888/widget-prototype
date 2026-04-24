package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.components.OmegaAvatar
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaSheetScaffold
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaSize
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
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
    OmegaSheetScaffold(
        title = "Кому",
        onDismiss = onDismiss,
        onBack = null,
        onClose = onDismiss,
        footer = {
            OmegaButton(
                text = "Другой получатель",
                style = OmegaButtonStyle.Neutral,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOtherContact
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = OmegaSpacing.xs),
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            items(candidates) { candidate ->
                val pickCount = pickCounts[candidate.phone] ?: 0
                CandidateRow(
                    candidate = candidate,
                    pickCount = pickCount,
                    onClick = { onContactSelected(candidate) }
                )
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
            .padding(vertical = OmegaSpacing.sm + OmegaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OmegaAvatar(name = candidate.displayName, avatarSize = OmegaSize.avatarMd)
        Spacer(Modifier.width(OmegaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.displayName,
                    style = OmegaType.BodyTightSemiBoldL,
                    color = OmegaTextPrimary
                )
                if (pickCount > 0) {
                    Spacer(Modifier.width(OmegaSpacing.xs))
                    Text(
                        text = if (pickCount >= ContactMemory.AUTO_RESOLVE_AT) "★" else "↑",
                        style = OmegaType.BodyTightM,
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
