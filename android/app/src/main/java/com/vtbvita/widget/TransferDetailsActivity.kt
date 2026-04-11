package com.vtbvita.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vtbvita.widget.ui.components.TransferDetailsSheet
import com.vtbvita.widget.ui.theme.VTBVitaTheme

class TransferDetailsActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_RECIPIENT_NAME = "recipient_name"
        private const val EXTRA_RECIPIENT_PHONE = "recipient_phone"
        private const val EXTRA_BANK_DISPLAY_NAME = "bank_display_name"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_BANK = "bank"
        private const val EXTRA_HAS_CONTACT_PICKER = "has_contact_picker"

        fun newIntent(
            context: Context,
            recipientName: String,
            recipientPhone: String,
            amount: Double = 0.0,
            bank: String = "",
            bankDisplayName: String = "",
            hasContactPicker: Boolean = false
        ): Intent = Intent(context, TransferDetailsActivity::class.java).apply {
            putExtra(EXTRA_RECIPIENT_NAME, recipientName)
            putExtra(EXTRA_RECIPIENT_PHONE, recipientPhone)
            putExtra(EXTRA_BANK_DISPLAY_NAME, bankDisplayName)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_BANK, bank)
            putExtra(EXTRA_HAS_CONTACT_PICKER, hasContactPicker)
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.restoreFromIntent(intent)
        val recipientName = intent.getStringExtra(EXTRA_RECIPIENT_NAME) ?: ""
        val recipientPhone = intent.getStringExtra(EXTRA_RECIPIENT_PHONE) ?: ""
        val bankDisplayName = intent.getStringExtra(EXTRA_BANK_DISPLAY_NAME) ?: ""
        val prefillAmount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val prefillBank = intent.getStringExtra(EXTRA_BANK) ?: ""
        val hasContactPicker = intent.getBooleanExtra(EXTRA_HAS_CONTACT_PICKER, false)

        val onDismiss: () -> Unit = if (hasContactPicker) {
            { finish() }
        } else {
            {
                val i = ContactPickerActivity.newIntent(this, prefillAmount)
                BankingSession.putInIntent(i)
                startActivity(i)
                finish()
            }
        }

        setContent {
            VTBVitaTheme {
                TransferDetailsSheet(
                    recipientName = recipientName,
                    recipientPhone = recipientPhone,
                    bankDisplayName = bankDisplayName,
                    prefillAmount = prefillAmount,
                    prefillBank = prefillBank,
                    onDismiss = onDismiss,
                    onSuccess = { msg ->
                        VitaWidgetProvider.showStatus(applicationContext, msg)
                        finish()
                    }
                )
            }
        }
    }
}
