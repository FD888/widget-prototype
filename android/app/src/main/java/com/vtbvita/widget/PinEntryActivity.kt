package com.vtbvita.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.vtbvita.widget.R
import com.vtbvita.widget.api.BankingTokenResult
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.ui.components.OmegaTopBar
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaSize
import com.vtbvita.widget.ui.theme.OmegaSpacing
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaTextDisabled
import com.vtbvita.widget.ui.theme.OmegaTextHint
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.TitanGray
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PinEntryActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PERSONA_ID = "persona_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val personaId = intent.getStringExtra(EXTRA_PERSONA_ID) ?: "vitya"
        val persona = PERSONAS.find { it.id == personaId } ?: PERSONAS.first()

        setContent {
            VTBVitaTheme {
                PinEntryScreen(
                    persona = persona,
                    onBack = { finish() },
                    onLogout = {
                        SessionManager.logout(applicationContext)
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    },
                    onSuccess = { tokenResult ->
                        BankingSession.save(tokenResult.token, tokenResult.expiresInSeconds)
                        SessionManager.login(applicationContext, persona.id)
                        val awm = android.appwidget.AppWidgetManager.getInstance(applicationContext)
                        val ids = awm.getAppWidgetIds(
                            android.content.ComponentName(applicationContext, VitaWidgetProvider::class.java)
                        )
                        ids.forEach { awm.updateAppWidget(it, VitaWidgetProvider.defaultViews(applicationContext)) }
                        startActivity(Intent(this, MockBankActivity::class.java).apply {
                            putExtra(MockBankActivity.EXTRA_PERSONA_ID, persona.id)
                        })
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PinEntryScreen(
    persona: Persona,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
    onSuccess: (BankingTokenResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val biometricEnabled = remember { SessionManager.isBiometricEnabled(context) }
    val showBiometric = biometricEnabled && activity != null

    fun loginWithBiometric() {
        if (isLoading) return
        isLoading = true
        scope.launch {
            MockApiService.authBiometric(context, persona.id).fold(
                onSuccess = { tokenResult -> onSuccess(tokenResult) },
                onFailure = {
                    errorMsg = "Ошибка биометрии"
                    delay(1200)
                    errorMsg = ""
                    isLoading = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (showBiometric) {
            delay(300)
            BiometricHelper.authenticate(
                activity = activity!!,
                onSuccess = { loginWithBiometric() }
            )
        }
    }

    fun onDigit(d: String) {
        if (pin.length < 4 && !isLoading) {
            pin += d
            if (pin.length == 4) {
                isLoading = true
                scope.launch {
                    MockApiService.auth(pin, context).fold(
                        onSuccess = { tokenResult -> onSuccess(tokenResult) },
                        onFailure = {
                            errorMsg = "Неверный PIN"
                            delay(400)
                            pin = ""
                            delay(800)
                            errorMsg = ""
                            isLoading = false
                        }
                    )
                }
            }
        }
    }

    // Sheet-style layout: scrim + bottom sheet surface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(OmegaSurface)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = OmegaSpacing.sm)
                    .height(OmegaSpacing.sm + OmegaSpacing.xs),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(OmegaSize.handleBar)
                        .height(OmegaSpacing.xs)
                        .clip(RoundedCornerShape(OmegaSpacing.xxs))
                        .background(TitanGray.v700)
                )
            }

            // Top bar: centred title + close
            OmegaTopBar(
                title = "Введите PIN",
                onBack = null,
                onClose = onBack
            )

            Spacer(Modifier.height(OmegaSpacing.xl))

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.md)) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    val dotColor = when {
                        errorMsg.isNotBlank() -> OmegaError
                        filled -> OmegaTextPrimary
                        else -> OmegaTextHint
                    }
                    Box(
                        modifier = Modifier
                            .size(OmegaSize.pinDot)
                            .background(dotColor, CircleShape)
                    )
                }
            }

            Spacer(Modifier.height(OmegaSpacing.sm))

            Box(modifier = Modifier.height(OmegaSpacing.xl)) {
                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, fontSize = 13.sp, color = OmegaError)
                }
            }

            Spacer(Modifier.height(OmegaSpacing.md))

            // Keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫")
            )

            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(OmegaSpacing.xl)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(OmegaSize.pinKeySizeCompact))
                        } else {
                            PinKey(label = key, enabled = !isLoading) {
                                when (key) {
                                    "⌫" -> if (pin.isNotEmpty() && !isLoading) pin = pin.dropLast(1)
                                    else -> onDigit(key)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(OmegaSpacing.lg))
            }

            // Biometric
            if (showBiometric) {
                Spacer(Modifier.height(OmegaSpacing.xs))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            enabled = !isLoading
                        ) {
                            BiometricHelper.authenticate(
                                activity = activity!!,
                                onSuccess = { loginWithBiometric() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fingerprint),
                        contentDescription = "Войти по биометрии",
                        tint = OmegaTextSecondary,
                        modifier = Modifier.size(OmegaSize.iconXl)
                    )
                }
                Spacer(Modifier.height(OmegaSpacing.xs))
                Text("Биометрия", fontSize = 11.sp, color = OmegaTextHint)
            }

            // PIN hint
            Spacer(Modifier.height(OmegaSpacing.lg))
            Box(
                modifier = Modifier
                    .background(TitanGray.v800, OmegaRadius.sm)
                    .padding(horizontal = OmegaSpacing.lg, vertical = OmegaSpacing.sm)
            ) {
                Text(
                    "Подсказка: PIN = ${persona.pin}",
                    fontSize = 12.sp,
                    color = OmegaTextHint
                )
            }

            Spacer(Modifier.height(OmegaSpacing.xxl))
        }
    }
}

@Composable
fun PinKey(
    label: String,
    size: Dp = OmegaSize.pinKeySizeCompact,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = if (label == "⌫") 22.sp else 26.sp,
            color = if (enabled) OmegaTextPrimary else OmegaTextDisabled
        )
    }
}
