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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBluePale
import com.vtbvita.widget.ui.theme.VtbSecondary

data class Contact(val name: String, val phone: String)

/**
 * Шаг 1 флоу перевода: выбор получателя из контактов телефона.
 * После выбора запускает TransferDetailsActivity (ConfirmActivity с пустой суммой).
 */
class ContactPickerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_AMOUNT = "amount"

        fun newIntent(context: Context, amount: Double? = null): Intent =
            Intent(context, ContactPickerActivity::class.java).apply {
                amount?.let { putExtra(EXTRA_AMOUNT, it) }
            }
    }

    // Флаг: мы сами запустили дочерний экран — onStop не должен убивать activity
    private var startingTransfer = false

    override fun onStop() {
        super.onStop()
        if (!startingTransfer) finish()
    }

    override fun onResume() {
        super.onResume()
        startingTransfer = false  // вернулись из TransferDetails — снова готовы к выбору
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
                        // finish() убран — activity остаётся в стеке для возврата
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
                .fillMaxHeight(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Кому перевести?",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(12.dp))

                    // Поиск
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Имя или номер телефона") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                when {
                    !hasPermission -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Нужен доступ к контактам",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }) { Text("Разрешить") }
                            }
                        }
                    }
                    filtered.isEmpty() && query.isNotBlank() -> {
                        // Если ничего не найдено — можно ввести номер вручную
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onContactSelected(Contact("", query))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Перевести на «$query»")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
                        ) {
                            items(filtered) { contact ->
                                ContactRow(contact = contact, onClick = { onContactSelected(contact) })
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар-инициалы
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(VtbBluePale, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.firstOrNull()?.uppercase() ?: "#",
                color = VtbBlue,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                contact.phone,
                style = MaterialTheme.typography.bodySmall,
                color = VtbSecondary
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
