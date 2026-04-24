package com.vtbvita.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vtbvita.widget.BankingSession
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.model.HintResult
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.effects.auroraBackground
import com.vtbvita.widget.ui.theme.OmegaBrandDeep
import com.vtbvita.widget.ui.theme.OmegaBrandPrimary
import com.vtbvita.widget.ui.theme.OmegaChip
import com.vtbvita.widget.ui.theme.OmegaError
import com.vtbvita.widget.ui.theme.OmegaOverlay
import com.vtbvita.widget.ui.theme.OmegaReminderBorder
import com.vtbvita.widget.ui.theme.OmegaReminderBorderEnd
import com.vtbvita.widget.ui.theme.OmegaReminderBadge
import com.vtbvita.widget.ui.theme.OmegaVygodaBorder
import com.vtbvita.widget.ui.theme.OmegaVygodaBorderEnd
import com.vtbvita.widget.ui.theme.OmegaRadius
import com.vtbvita.widget.ui.theme.OmegaSurface
import com.vtbvita.widget.ui.theme.OmegaTextDisabled
import com.vtbvita.widget.ui.theme.OmegaTextHint
import com.vtbvita.widget.ui.theme.OmegaSuccess
import com.vtbvita.widget.ui.theme.OmegaTextPrimary
import com.vtbvita.widget.ui.theme.OmegaTextSecondary
import com.vtbvita.widget.ui.theme.OmegaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InputActivity : FragmentActivity() {

    companion object {
        const val EXTRA_INTENT_TYPE = "intent_type"
        const val EXTRA_MODE        = "mode"
        const val EXTRA_VOICE_TEXT  = "voice_text"
        const val MODE_TEXT         = "text"
        const val MODE_RECORDING    = "recording"
        /** Голос уже распознан сервисом — сразу отправляем на NLP, показываем PIN если нужно. */
        const val MODE_VOICE_RESULT = "voice_result"
    }

    override fun onPause() {
        super.onPause()
        BankingSession.clear()  // каждый выход из виджета сбрасывает banking-сессию
        restoreWidget()
    }

    private fun restoreWidget() {
        val awm = android.appwidget.AppWidgetManager.getInstance(applicationContext)
        val ids = awm.getAppWidgetIds(
            android.content.ComponentName(applicationContext, VitaWidgetProvider::class.java)
        )
        ids.forEach { awm.updateAppWidget(it, VitaWidgetProvider.defaultViews(applicationContext)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BankingSession.clear()  // сбрасываем сессию от предыдущих флоу (ContactPicker/TransferDetails не чистят)
        val startMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_TEXT
        window.setSoftInputMode(
            if (startMode == MODE_RECORDING)
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            else
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        VitaWidgetProvider.hideWidget(applicationContext)
        setContent {
            VTBVitaTheme {
                InputOverlay(
                    startMode = startMode,
                    startVoiceText = intent.getStringExtra(EXTRA_VOICE_TEXT),
                    onDismiss = { finish() },
                    onBalance = {
                        val i = android.content.Intent(this, BalanceActivity::class.java)
                        BankingSession.putInIntent(i)
                        startActivity(i)
                        finish()
                    },
                    onTransfer = { name, phone, bankDisplayName, amount ->
                        val i = if (name != null || phone != null) {
                            // Авторезолв — стартуем сразу на экране подтверждения.
                            // Кандидаты уже сохранены в TransferFlowActivity.pendingCandidates
                            // (устанавливаются в handleTransferIntent перед вызовом onTransfer).
                            TransferFlowActivity.newIntentAutoResolved(this, amount)
                        } else {
                            ContactPickerActivity.newIntent(this, amount)
                        }
                        BankingSession.putInIntent(i)
                        startActivity(i)
                        finish()
                    },
                    onAmbiguousTransfer = { candidates, recipientRaw, amount ->
                        TransferFlowActivity.pendingCandidates = candidates
                        TransferFlowActivity.pendingRecipientRaw = recipientRaw
                        TransferFlowActivity.pendingAutoContact = null
                        val i = TransferFlowActivity.newIntentAmbiguous(this, amount)
                        BankingSession.putInIntent(i)
                        startActivity(i)
                        finish()
                    },
                    onTopup = { phone, amount ->
                        val i = TopupInputActivity.newIntent(this, phone ?: "", amount ?: 0.0)
                        BankingSession.putInIntent(i)
                        startActivity(i)
                        finish()
                    },
                    onConfirm = { data ->
                        val i = ConfirmActivity.newIntent(this, data)
                        BankingSession.putInIntent(i)
                        startActivity(i)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun InputOverlay(
    startMode: String,
    startVoiceText: String?,
    onDismiss: () -> Unit,
    onBalance: () -> Unit,
    onTransfer: (name: String?, phone: String?, bankDisplayName: String?, amount: Double?) -> Unit,
    onAmbiguousTransfer: (List<ContactCandidate>, String /* recipientRaw */, Double?) -> Unit,
    onTopup: (String?, Double?) -> Unit,
    onConfirm: (ConfirmationData) -> Unit
) {
    val scope = rememberCoroutineScope()
    var contentVisible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var showBotRedirect by remember { mutableStateOf(false) }
    var botRedirectText by remember { mutableStateOf("") }
    val isVoiceResult = startMode == InputActivity.MODE_VOICE_RESULT
    // Инициализируем сразу — нет мигания текстового режима при открытии через микрофон
    var isRecording by remember { mutableStateOf(startMode == InputActivity.MODE_RECORDING) }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Banking PIN gate
    var pinRequired by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showTemplates by remember { mutableStateOf(false) }

    // Напоминание/ВЫГОДА — всегда свежий запрос к серверу для баннера
    var reminderHint by remember { mutableStateOf<HintResult?>(null) }
    LaunchedEffect(Unit) {
        val personaId = com.vtbvita.widget.SessionManager.getPersonaId(context) ?: return@LaunchedEffect
        val hint = runCatching { MockApiService.getHint(context, personaId) }.getOrNull()
        reminderHint = hint
        if (hint != null && hint.type != "none") {
            val widgetText = hint.widgetText
                ?: com.vtbvita.widget.personalization.WidgetHintTexts.toWidgetText(hint)
            if (widgetText != null) {
                com.vtbvita.widget.personalization.HintRepository.saveHintToPrefs(context, hint, widgetText)
            }
        }
    }

    // Скрываем клавиатуру, когда появляется PIN-оверлей
    LaunchedEffect(isRecording, isVoiceResult) {
        if (isRecording || isVoiceResult) showTemplates = false
    }
    LaunchedEffect(pinRequired) {
        if (pinRequired) keyboardController?.hide()
    }

    fun requirePin(action: () -> Unit) {
        if (BankingSession.isValid()) {
            action()
        } else {
            pendingAction = action
            pinRequired = true
        }
    }

    // STT streaming
    var streamingRecorder by remember { mutableStateOf<VoiceStreamingRecorder?>(null) }
    var partialText by remember { mutableStateOf("") }
    var voiceAmplitude by remember { mutableStateOf(0.08f) }

    // Освобождаем ресурсы при уходе из composable
    DisposableEffect(Unit) {
        onDispose { streamingRecorder?.stop() }
    }

    fun discardRecording() {
        streamingRecorder?.stop()
        streamingRecorder = null
        isRecording = false
        partialText = ""
    }

    fun submitVoice(voiceText: String) {
        if (voiceText.isBlank() || isLoading) return
        errorMsg = ""
        submitText(
            voiceText, context, scope, onDismiss,
            onBalance    = { requirePin(onBalance) },
            onTransfer   = { n, p, b, a -> requirePin { onTransfer(n, p, b, a) } },
            onAmbiguousTransfer = { candidates, raw, a -> requirePin { onAmbiguousTransfer(candidates, raw, a) } },
            onTopup      = { p, a -> requirePin { onTopup(p, a) } },
            onConfirm    = onConfirm,
            setLoading   = { isLoading = it },
            onError      = { errorMsg = it },
            onBotRedirect = { text -> botRedirectText = text; showBotRedirect = true }
        )
    }

    fun startStreaming() {
        val rec = VoiceStreamingRecorder(
            onPartial = { t -> scope.launch(Dispatchers.Main) { partialText = t } },
            onFinal   = { t -> scope.launch(Dispatchers.Main) {
                val captured = if (t.isNotBlank()) t else partialText
                discardRecording()
                if (captured.isNotBlank()) submitVoice(captured) else onDismiss()
            }},
            onError   = { msg -> scope.launch(Dispatchers.Main) {
                discardRecording()
                errorMsg = msg
            }}
        )
        streamingRecorder = rec
        rec.start(scope)
        isRecording = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startStreaming()
    }

    // Автозапуск в зависимости от режима
    LaunchedEffect(startMode) {
        when (startMode) {
            InputActivity.MODE_RECORDING -> {
                delay(150)
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) startStreaming()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            InputActivity.MODE_VOICE_RESULT -> {
                // Голос уже распознан — сразу отправляем на NLP
                val vt = startVoiceText
                if (!vt.isNullOrBlank()) submitVoice(vt) else onDismiss()
            }
            else -> {
                delay(80)
                focusRequester.requestFocus()
            }
        }
    }

    // Полинг амплитуды для анимации ripple-колец
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isActive && isRecording) {
                voiceAmplitude = streamingRecorder?.amplitude ?: 0.08f
                delay(80)
            }
        } else {
            voiceAmplitude = 0.08f
        }
    }

    LaunchedEffect(Unit) { contentVisible = true }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OmegaOverlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isRecording) discardRecording()
                    onDismiss()
                }
            )
    ) {
        val topOffset = maxHeight * 0.10f
        AnimatedVisibility(
            visible = contentVisible,
            enter = scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 250))
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = topOffset)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // ── Banner (relative to card top, fades in/out, reserves space) ──
            val hint = reminderHint
            val hasActiveHint = hint != null && hint.type != "none"
            val showBanner = !isRecording && !isVoiceResult && hasActiveHint
            val bannerAlpha by animateFloatAsState(
                targetValue = if (showBanner) 1f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "bannerAlpha"
            )
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = bannerAlpha }
                    .padding(horizontal = 10.dp)
            ) {
                if (hint?.type == "reminder") {
                    ReminderBanner(hint = hint, onTap = {
                        if (showBanner) {
                            val pid = hint.paymentId ?: return@ReminderBanner
                            val amt = hint.amount ?: return@ReminderBanner
                            requirePin {
                                scope.launch {
                                    isLoading = true
                                    val data = runCatching {
                                        MockApiService.commandPayScheduled(pid, amt, context)
                                    }.getOrElse { e ->
                                        isLoading = false
                                        errorMsg = e.message ?: "Ошибка"
                                        return@launch
                                    }
                                    isLoading = false
                                    onConfirm(data)
                                }
                            }
                        }
                    })
                } else if (hint?.type == "vygoda") {
                    VygodaBanner(
                        text = hint.offerText ?: "Выбери категории — вернёшь ~600 ₽ в месяц",
                        onTap = {
                            if (showBanner) {
                                context.startActivity(MockBankActivity.productsIntent(context))
                                onDismiss()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Widget Card (OmegaSurface, widget flush to top and sides) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OmegaSurface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                Column {
                    // ── Widget Capsule (68dp, Aurora, 16dp corners, flush with card) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .auroraBackground(
                                isRecording = isRecording,
                                amplitude   = voiceAmplitude,
                                cornerRadius = 16.dp
                            )
                            .padding(start = 16.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                if (isVoiceResult) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isLoading) "Обрабатываю…" else startVoiceText ?: "",
                        color = Color.White,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(OmegaError, CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                discardRecording()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cross_big),
                            contentDescription = "Отмена",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text     = if (partialText.isEmpty()) "Говорите..." else partialText,
                            color    = if (partialText.isEmpty()) Color.White.copy(alpha = 0.5f) else Color.White,
                            fontSize = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    val rippleTransition = rememberInfiniteTransition(label = "ripple")
                    val ripplePhase by rippleTransition.animateFloat(
                        initialValue  = 0f,
                        targetValue   = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1400, easing = LinearEasing)
                        ),
                        label = "ripplePhase"
                    )
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val amp = voiceAmplitude
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val maxR  = size.minDimension / 2f
                            val scale = amp.coerceIn(0.25f, 1f)
                            for (i in 0..2) {
                                val rPhase = (ripplePhase + i / 3f) % 1f
                                drawCircle(
                                    color  = Color.White.copy(alpha = (1f - rPhase) * 0.45f),
                                    radius = rPhase * maxR * scale,
                                    style  = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    val lastText = partialText
                                    discardRecording()
                                    if (lastText.isNotBlank()) submitVoice(lastText) else onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_big_top),
                                contentDescription = "Отправить",
                                tint     = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (text.isNotBlank() && !isLoading) {
                                errorMsg = ""
                                submitText(
                                    text, context, scope, onDismiss,
                                    onBalance = { requirePin(onBalance) },
                                    onTransfer = { n, p, b, a -> requirePin { onTransfer(n, p, b, a) } },
                                    onAmbiguousTransfer = { candidates, raw, a -> requirePin { onAmbiguousTransfer(candidates, raw, a) } },
                                    onTopup = { p, a -> requirePin { onTopup(p, a) } },
                                    onConfirm = onConfirm,
                                    setLoading = { isLoading = it },
                                    onError = { errorMsg = it },
                                    onBotRedirect = { t -> botRedirectText = t; showBotRedirect = true }
                                )
                            }
                        }),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    "Как настроение?",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 17.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    if (text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable {
                                    if (!isLoading) {
                                        errorMsg = ""
                                        submitText(
                                            text, context, scope, onDismiss,
                                            onBalance = { requirePin(onBalance) },
                                            onTransfer = { n, p, b, a -> requirePin { onTransfer(n, p, b, a) } },
                                            onAmbiguousTransfer = { candidates, raw, a -> requirePin { onAmbiguousTransfer(candidates, raw, a) } },
                                            onTopup = { p, a -> requirePin { onTopup(p, a) } },
                                            onConfirm = onConfirm,
                                            setLoading = { isLoading = it },
                                            onError = { errorMsg = it },
                                            onBotRedirect = { t -> botRedirectText = t; showBotRedirect = true }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_big_top),
                                contentDescription = "Отправить",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .clickable {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        startStreaming()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                    } // Row (capsule)

                    // ── NavItems ──
                    if (!isRecording) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            NavItem(R.drawable.ic_nav_templates, "Шаблоны") {
                                keyboardController?.hide()
                                showTemplates = !showTemplates
                            }
                            NavItem(R.drawable.ic_nav_transfer, "Перевод") { showTemplates = false; requirePin { onTransfer(null, null, null, null) } }
                            NavItem(R.drawable.ic_nav_balance, "Баланс") { showTemplates = false; requirePin(onBalance) }
                            NavItem(R.drawable.ic_nav_qr, "QR-скан") {
                                android.widget.Toast.makeText(context, "QR-скан — скоро", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } // Column inside card
            } // Box (card)

            // ── Error message ──
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorMsg,
                    color = OmegaError,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ── Bot redirect dialog ──
            if (showBotRedirect) {
                BotRedirectDialog(
                    originalText = botRedirectText,
                    onAccept = {
                        showBotRedirect = false
                        context.startActivity(
                            MockBankActivity.chatIntent(context, botRedirectText)
                        )
                        onDismiss()
                    },
                    onDismiss = {
                        showBotRedirect = false
                    }
                )
            }

            // ── PIN overlay (below card) ──
            if (pinRequired) {
                Spacer(Modifier.height(8.dp))
                InlinePinOverlay(
                    scope = scope,
                    context = context,
                    onSuccess = { token, expiresIn ->
                        BankingSession.save(token, expiresIn)
                        pinRequired = false
                        pendingAction?.invoke()
                        pendingAction = null
                    },
                    onCancel = {
                        pinRequired = false
                        pendingAction = null
                    }
                )
            }

            // ── Templates overlay (below card) ──
            if (showTemplates && !pinRequired) {
                Spacer(Modifier.height(8.dp))
                TemplatesCard(
                    onTemplateClick = { template ->
                        showTemplates = false
                        when (template.type) {
                            TemplateType.TRANSFER -> requirePin { onTransfer(template.name, template.phone, null, template.amount) }
                            TemplateType.TOPUP -> requirePin { onTopup(template.phone, template.amount) }
                            TemplateType.HOUSING -> requirePin { onTransfer(template.name, null, null, template.amount) }
                            TemplateType.CREDIT -> requirePin { onTransfer(template.name, null, null, template.amount) }
                            TemplateType.CARD_TOPUP -> requirePin { onTransfer(template.name, null, null, template.amount) }
                        }
                    },
                    onClose = { showTemplates = false }
                )
            }
        }
        } // AnimatedVisibility
    }
}

// ── Вспомогательные ──────────────────────────────────────────────────────────

private fun submitText(
    text: String,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
    onBalance: () -> Unit,
    onTransfer: (name: String?, phone: String?, bankDisplayName: String?, amount: Double?) -> Unit,
    onAmbiguousTransfer: (List<ContactCandidate>, String /* recipientRaw */, Double?) -> Unit,
    onTopup: (String? /* phone */, Double? /* amount */) -> Unit,
    onConfirm: (ConfirmationData) -> Unit,
    setLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onBotRedirect: (String) -> Unit = {},
) {
    setLoading(true)
    scope.launch {
        // Все команды идут на сервер — сервер решает что делать
        val result = com.vtbvita.widget.nlp.NlpService.parse(text, context)

        result.onFailure {
            setLoading(false)
            onError("Нет соединения, попробуй позже")
            return@launch
        }

        val parsed = result.getOrThrow()
        setLoading(false)

        when (parsed.intent) {
            "balance" -> onBalance()

            "transfer" -> {
                val recipientRaw = parsed.recipient
                android.util.Log.d("VitaTransfer", "NLP recipient_raw='$recipientRaw' amount=${parsed.amount}")
                if (recipientRaw != null) {
                    // allCandidates — все выше 0.4, без фильтрации (для back-навигации)
                    val allCandidates = ContactMatcher.search(recipientRaw, context)
                    // filtered — топ-кандидаты для отображения (gap ≤ 0.4 от лидера, ≤ 5)
                    val filtered = ContactMatcher.filterCandidates(allCandidates)
                    android.util.Log.d("VitaTransfer", "all(${allCandidates.size}) filtered(${filtered.size}): ${filtered.map { "'${it.displayName}' score=${it.score}" }}")
                    when {
                        filtered.isEmpty() -> {
                            android.util.Log.d("VitaTransfer", "→ no contacts found, opening picker")
                            onTransfer(null, null, null, parsed.amount)
                        }
                        ContactMatcher.isHighConfidence(filtered) -> {
                            val c = filtered[0]
                            android.util.Log.d("VitaTransfer", "→ HIGH CONFIDENCE, auto-resolving to '${c.displayName}'")
                            // Для back-навигации: авто-выбранный первым, затем остальные альтернативы.
                            val alternatives = listOf(c) +
                                (filtered + allCandidates)
                                    .distinctBy { it.phone }
                                    .filter { it.phone != c.phone }
                                    .take(4)
                            TransferFlowActivity.pendingCandidates = alternatives
                            TransferFlowActivity.pendingRecipientRaw = recipientRaw
                            TransferFlowActivity.pendingAutoContact = c
                            onTransfer(c.displayName, c.phone, c.bankDisplayName, parsed.amount)
                        }
                        else -> {
                            android.util.Log.d("VitaTransfer", "→ AMBIGUOUS, showing disambiguation with ${filtered.size} candidates")
                            onAmbiguousTransfer(filtered, recipientRaw, parsed.amount)
                        }
                    }
                } else {
                    android.util.Log.d("VitaTransfer", "→ recipient is null, opening transfer without contact")
                    onTransfer(null, null, null, parsed.amount)
                }
            }

            "topup" -> onTopup(parsed.phone, parsed.amount)

            "open_app" -> {
                val appName = parsed.app
                if (appName == null) {
                    onError("Не понял, какое приложение открыть")
                } else {
                    val appIntent = SystemIntentHandler.openApp(appName, context)
                    if (appIntent == null) {
                        onError("Приложение не установлено")
                    } else {
                        try {
                            context.startActivity(appIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            onDismiss()
                        } catch (_: Exception) {
                            onError("Не удалось открыть приложение")
                        }
                    }
                }
            }

            "alarm" -> {
                val h = parsed.hour ?: 8
                val m = parsed.minute ?: 0
                try {
                    context.startActivity(
                        SystemIntentHandler.setAlarm(h, m)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    onDismiss()
                } catch (_: Exception) {
                    onError("Не удалось поставить будильник")
                }
            }

            "timer" -> {
                val secs = parsed.durationSeconds ?: 60
                try {
                    context.startActivity(
                        SystemIntentHandler.setTimer(secs)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    onDismiss()
                } catch (_: Exception) {
                    onError("Не удалось запустить таймер")
                }
            }

            "call" -> {
                try {
                    context.startActivity(
                        SystemIntentHandler.call(parsed.contact, context)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    onDismiss()
                } catch (_: Exception) {
                    onError("Не удалось открыть набор номера")
                }
            }

            "navigate" -> {
                val dest = parsed.destination
                if (dest.isNullOrBlank()) {
                    onError("Не понял, куда ехать")
                } else {
                    try {
                        context.startActivity(
                            SystemIntentHandler.navigate(dest, context)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        onDismiss()
                    } catch (_: Exception) {
                        onError("Не удалось открыть карты")
                    }
                }
            }

            else -> {
                if (parsed.botRedirect) {
                    onBotRedirect(parsed.originalText ?: text)
                } else {
                    onError("Не понял команду, попробуй иначе")
                }
            }
        }
    }
}

@Composable
private fun BotRedirectDialog(
    originalText: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Открыть чат с ботом?", style = OmegaType.BodyTightM, fontWeight = FontWeight.SemiBold) },
        text = { Text("Не удалось распознать команду через виджет.\nБот ВТБ может помочь с: «${originalText.take(50)}»", style = OmegaType.BodyParagraphM) },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Открыть чат", color = OmegaBrandPrimary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = OmegaTextSecondary)
            }
        },
        containerColor = OmegaSurface,
        shape = OmegaRadius.lg
    )
}

@Composable
private fun InlinePinOverlay(
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onSuccess: (String, Int) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val activity = context as? FragmentActivity
    val biometricEnabled = remember { SessionManager.isBiometricEnabled(context) }
    val showBiometric = biometricEnabled && activity != null

    fun loginWithBiometric() {
        if (isLoading) return
        isLoading = true
        scope.launch {
            MockApiService.authBiometric(context).fold(
                onSuccess = { r -> onSuccess(r.token, r.expiresInSeconds) },
                onFailure = {
                    errorMsg = "Ошибка биометрии"
                    delay(1200)
                    errorMsg = ""
                    isLoading = false
                }
            )
        }
    }

    // Автоматически предлагаем биометрию при появлении оверлея
    LaunchedEffect(Unit) {
        if (showBiometric) {
            delay(150)
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
                    val result = MockApiService.auth(pin, context)
                    result.fold(
                        onSuccess = { tokenResult ->
                            onSuccess(tokenResult.token, tokenResult.expiresInSeconds)
                        },
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmegaSurface, OmegaRadius.xl)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text(
                "Введите PIN",
                color = OmegaTextPrimary,
                fontSize = 15.sp
            )
            androidx.compose.material3.Text(
                "Отмена",
                color = OmegaTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onCancel)
            )
        }

        Spacer(Modifier.height(14.dp))

        // 4 точки
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            when {
                                errorMsg.isNotBlank() -> OmegaError
                                i < pin.length -> OmegaTextPrimary
                                else -> OmegaTextDisabled
                            },
                            CircleShape
                        )
                )
            }
        }

        if (errorMsg.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(errorMsg, fontSize = 11.sp, color = OmegaError)
        }

        Spacer(Modifier.height(12.dp))

        // Цифровой пад (компактный)
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        // Ячейка слева от «0»: биометрия или пустышка
                        if (showBiometric) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(OmegaSurface, CircleShape)
                                    .clickable(enabled = !isLoading) {
                                        BiometricHelper.authenticate(
                                            activity = activity!!,
                                            onSuccess = { loginWithBiometric() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_fingerprint),
                                    contentDescription = "Биометрия",
                                    tint = OmegaTextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.size(56.dp))
                        }
                    } else {
                        PinKey(label = key, size = 56.dp, enabled = !isLoading) {
                            when (key) {
                                "⌫" -> if (pin.isNotEmpty() && !isLoading) pin = pin.dropLast(1)
                                else -> onDigit(key)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReminderBanner(hint: HintResult, onTap: () -> Unit) {
    val amountText = hint.amount?.let { " · %,.0f ₽".format(it).replace(",", " ") } ?: ""
    val bodyText = "${hint.name ?: "Платёж"} — ${hint.label ?: "скоро"}$amountText"
    val borderBrush = Brush.linearGradient(
        listOf(OmegaReminderBorder, OmegaReminderBorderEnd),
        start = Offset.Zero,
        end = Offset(1000f, 1000f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                .background(OmegaSurface, OmegaRadius.lg)
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bodyText,
                    color = OmegaTextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "›",
                    color = OmegaReminderBorder,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VygodaBanner(text: String, onTap: () -> Unit) {
    val borderBrush = Brush.linearGradient(
        listOf(OmegaVygodaBorder, OmegaVygodaBorderEnd),
        start = Offset.Zero,
        end = Offset(1000f, 1000f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                .background(OmegaSurface, OmegaRadius.lg)
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text,
                    color = OmegaTextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "›",
                    color = OmegaVygodaBorder,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

enum class TemplateType { TRANSFER, TOPUP, HOUSING, CREDIT, CARD_TOPUP }

data class TemplateItem(
    val type: TemplateType,
    val name: String,
    val iconRes: Int,
    val subtitle: String,
    val phone: String? = null,
    val amount: Double? = null
)

@Composable
private fun TemplatesCard(
    onTemplateClick: (TemplateItem) -> Unit,
    onClose: () -> Unit
) {
    val templates = remember {
        listOf(
            TemplateItem(TemplateType.TOPUP, "Мобильная связь", R.drawable.ic_template_topup, "МТС · +7 916 ••• 28 47", phone = "+79161232847", amount = 500.0),
            TemplateItem(TemplateType.HOUSING, "Коммунальные", R.drawable.ic_template_housing, "ЖКХ · 3 412 ₽", amount = 3412.0),
            TemplateItem(TemplateType.TRANSFER, "Мария С.", R.drawable.ic_template_transfer, "Перевод · 2 500 ₽", phone = "+79031234567", amount = 2500.0),
            TemplateItem(TemplateType.CREDIT, "Кредит ВТБ", R.drawable.ic_template_credit, "Платёж · 15 430 ₽", amount = 15430.0),
            TemplateItem(TemplateType.CARD_TOPUP, "Пополнение карты", R.drawable.ic_template_card, "Visa ••• 4521 · 3 000 ₽", amount = 3000.0),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmegaSurface, OmegaRadius.lg)
            .padding(horizontal = 0.dp, vertical = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Шаблоны",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_cross_big),
                    contentDescription = "Закрыть",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose
                        )
                )
            }
            templates.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTemplateClick(template) }
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(template.iconRes),
                        contentDescription = template.name,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = template.subtitle,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun NavItem(
    @androidx.annotation.DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (isPressed) OmegaBrandPrimary else Color.White,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isPressed) OmegaBrandPrimary else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isPressed) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(
                if (enabled) OmegaChip else OmegaSurface,
                OmegaRadius.md
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) OmegaTextPrimary else OmegaTextDisabled, fontSize = 16.sp)
    }
}