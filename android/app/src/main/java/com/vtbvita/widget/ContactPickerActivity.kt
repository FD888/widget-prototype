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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vtbvita.widget.ui.components.OmegaButton
import com.vtbvita.widget.ui.components.OmegaButtonStyle
import com.vtbvita.widget.ui.components.OmegaSheetHeader
import com.vtbvita.widget.ui.components.OmegaTextField
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaScrim
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.VTBVitaTheme

data class Contact(val name: String, val phone: String)

/**
 * Шаг 1 флоу перевода: выбор получателя из контактов телефона.
 */
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
                        val i = TransferDetailsActivity.newIntent(
                            this, contact.name, contact.phone,
                            amount = prefillAmount ?: 0.0,
                            hasContactPicker = true
                        )
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
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
        if (hasPermission) contacts = loadContacts(context)
        else permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter {
            it.name.contains(query, ignoreCase = true) || it.phone.contains(query)
        }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(OmegaSurface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            OmegaSheetHeader(title = "Кому перевести?")

            OmegaTextField(
                value = query,
                onValueChange = { query = it },
                label = "",
                placeholder = "Имя или номер телефона",
                modifier = Modifier.padding(horizontal = 16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            Spacer(Modifier.height(8.dp))

            when {
                !hasPermission -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Нужен доступ к контактам",
                                style = MaterialTheme.typography.bodyLarge,
                                color = OmegaTextPrimary
                            )
                            OmegaButton(
                                text = "Разрешить",
                                style = OmegaButtonStyle.Brand,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                            )
                        }
                    }
                }
                filtered.isEmpty() && query.isNotBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OmegaButton(
                            text = "Перевести на «$query»",
                            style = OmegaButtonStyle.Brand,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onContactSelected(Contact("", query)) }
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered) { contact ->
                            ContactRow(contact = contact, onClick = { onContactSelected(contact) })
                        }
                        item { Spacer(Modifier.height(16.dp)) }
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
                text = contact.name.firstOrNull()?.uppercase() ?: "#",
                color = OmegaTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                contact.name,
                style = MaterialTheme.typography.bodyLarge,
                color = OmegaTextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                contact.phone,
                style = MaterialTheme.typography.bodySmall,
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
