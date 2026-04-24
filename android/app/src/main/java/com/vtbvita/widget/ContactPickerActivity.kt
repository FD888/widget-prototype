package com.vtbvita.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMemory
import com.vtbvita.widget.ui.components.OmegaAvatar
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaSearchField
import com.vtbvita.widget.ui.components.OmegaSelectableChip
import com.vtbvita.widget.ui.components.OmegaSheetScaffold
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaSize
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color

data class Contact(val name: String, val phone: String)

class ContactPickerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_AMOUNT = "amount"

        fun newIntent(context: Context, amount: Double? = null): Intent =
            Intent(context, ContactPickerActivity::class.java).apply {
                amount?.let { putExtra(EXTRA_AMOUNT, it) }
            }
    }

    private var startingTransfer = false

    override fun onStop() {
        super.onStop()
        if (!startingTransfer) finish()
    }

    override fun onResume() {
        super.onResume()
        startingTransfer = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        val prefillAmount = if (intent.hasExtra(EXTRA_AMOUNT))
            intent.getDoubleExtra(EXTRA_AMOUNT, 0.0) else null
        setContent {
            VTBVitaTheme {
                ContactPickerSheet(
                    onDismiss = { finish() },
                    onContactSelected = { contact ->
                        val candidate = ContactCandidate(
                            displayName = contact.name.ifBlank { contact.phone },
                            phone = contact.phone,
                            bankDisplayName = "ВТБ",
                            score = 1f,
                            pickCount = 0
                        )
                        TransferFlowActivity.pendingCandidates = listOf(candidate)
                        TransferFlowActivity.pendingRecipientRaw = contact.name
                        TransferFlowActivity.pendingAutoContact = candidate
                        val i = TransferFlowActivity.newIntentAutoResolved(this, prefillAmount)
                        BankingSession.putInIntent(i)
                        startingTransfer = true
                        startActivity(i)
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactPickerSheet(
    onDismiss: () -> Unit,
    onContactSelected: (Contact) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var recentPhones by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeTab by remember { mutableStateOf("all") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            contacts = loadContacts(context)
            recentPhones = ContactMemory.getAllPickedPhones(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val hasRecent = recentPhones.isNotEmpty()

    val filtered = remember(contacts, query, activeTab, recentPhones) {
        val base = if (query.isBlank() && activeTab == "recent" && hasRecent) {
            contacts.filter { it.phone in recentPhones }
        } else contacts
        if (query.isBlank()) base
        else base.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
    }

    OmegaSheetScaffold(
        title = "Кому",
        onDismiss = onDismiss,
        onBack = null,
        onClose = onDismiss
    ) {
        OmegaSearchField(
            value = query,
            onValueChange = { query = it }
        )

        if (query.isBlank() && hasRecent) {
            Spacer(Modifier.height(OmegaSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.sm)) {
                OmegaSelectableChip(
                    label = "Недавние",
                    selected = activeTab == "recent",
                    onClick = { activeTab = "recent" }
                )
                OmegaSelectableChip(
                    label = "Все",
                    selected = activeTab == "all",
                    onClick = { activeTab = "all" }
                )
            }
        }

        Spacer(Modifier.height(OmegaSpacing.sm))

        when {
            !hasPermission -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = OmegaSpacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(OmegaSpacing.md)
                    ) {
                        Text(
                            "Нужен доступ к контактам",
                            style = OmegaType.BodyTightL,
                            color = OmegaTextPrimary
                        )
                        OmegaButton(
                            text = "Разрешить",
                            style = OmegaButtonStyle.Brand,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                        )
                    }
                }
            }
            filtered.isEmpty() && query.isNotBlank() -> {
                OmegaButton(
                    text = "Перевести на «$query»",
                    style = OmegaButtonStyle.Brand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = OmegaSpacing.xs),
                    onClick = { onContactSelected(Contact("", query)) }
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = OmegaSpacing.xs),
                    modifier = Modifier.height(420.dp)
                ) {
                    items(filtered) { contact ->
                        ContactRow(contact = contact, onClick = { onContactSelected(contact) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = OmegaSpacing.sm + OmegaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OmegaAvatar(
            name = contact.name.ifBlank { contact.phone },
            avatarSize = OmegaSize.avatarMd,
            bgColor = OmegaChip,
            textColor = Color.White
        )
        Spacer(Modifier.width(OmegaSpacing.md))
        Column {
            Text(
                contact.name,
                style = OmegaType.BodyTightSemiBoldL,
                color = OmegaTextPrimary
            )
            Text(
                contact.phone,
                style = OmegaType.BodyTightM,
                color = OmegaTextSecondary
            )
        }
    }
}

private fun loadContacts(context: Context): List<Contact> {
    val result = mutableListOf<Contact>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ),
        null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    ) ?: return result

    cursor.use {
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val seen = mutableSetOf<String>()
        while (it.moveToNext()) {
            val name = it.getString(nameIdx) ?: continue
            val phone = it.getString(phoneIdx)?.replace(Regex("[\\s\\-()]"), "") ?: continue
            val key = "$name|$phone"
            if (seen.add(key)) result.add(Contact(name, phone))
        }
    }
    return result
}
