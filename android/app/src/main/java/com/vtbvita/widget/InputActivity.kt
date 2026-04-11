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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vtbvita.widget.BankingSession
import com.vtbvita.widget.api.MockApiService
import com.vtbvita.widget.model.ConfirmationData
import com.vtbvita.widget.nlp.ContactCandidate
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.ui.theme.VTBVitaTheme
import com.vtbvita.widget.ui.theme.VtbBlue
import com.vtbvita.widget.ui.theme.VtbBlueMid
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
                            TransferDetailsActivity.newIntent(
                                this,
                                recipientName = name ?: "",
                                recipientPhone = phone ?: "",
                                amount = amount ?: 0.0,
                                bankDisplayName = bankDisplayName ?: ""
                            )
                        } else {
                            ContactPickerActivity.newIntent(this, amount)
                        }
                        BankingSession.putInIntent(i)
                        startActivity(i)
                        finish()
                    },
                    onAmbiguousTransfer = { candidates, recipientRaw, amount ->
                        ContactDisambiguationActivity.pendingCandidates = candidates
                        ContactDisambiguationActivity.pendingRecipientRaw = recipientRaw
                        val i = ContactDisambiguationActivity.newIntent(this, amount)
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
    val isVoiceResult = startMode == InputActivity.MODE_VOICE_RESULT
    // Инициализируем сразу — нет мигания текстового режима при открытии через микрофон
    var isRecording by remember { mutableStateOf(startMode == InputActivity.MODE_RECORDING) }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Banking PIN gate
    var pinRequired by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Скрываем клавиатуру, когда появляется PIN-оверлей
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
            onError      = { errorMsg = it }
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
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isRecording) discardRecording()
                    onDismiss()
                }
            )
    ) {
        val topOffset = maxHeight * 0.15f
        AnimatedVisibility(
            visible = contentVisible,
            enter = slideInVertically(
                initialOffsetY = { -80 },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 250))
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topOffset)
                .padding(horizontal = 4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
        ) {
            // ── Пилюля ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.horizontalGradient(listOf(VtbBlue, VtbBlueMid)),
                        RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isVoiceResult) {
                    // ── Режим voice result: голос уже распознан, ждём NLP ────
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isLoading) "Обрабатываю…" else startVoiceText ?: "",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else if (isRecording) {
                    // ── Режим записи ──────────────────────────────────
                    // Кнопка отмены (красный крестик)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFD32F2F), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                discardRecording()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Отмена",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Partial text / placeholder
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text     = if (partialText.isEmpty()) "Говорите..." else partialText,
                            color    = Color.White.copy(alpha = if (partialText.isEmpty()) 0.40f else 0.92f),
                            fontSize = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Кнопка готово с ripple-кольцами (глубокий синий)
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
                        // Ripple-кольца вокруг кнопки
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
                        // Кнопка отправки (стрелка вверх)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF001F6E), CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    val lastText = partialText
                                    discardRecording()
                                    if (lastText.isNotBlank()) submitVoice(lastText) else onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_up),
                                contentDescription = "Отправить",
                                tint     = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // ── Режим текста ──────────────────────────────────
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(color = Color.White, fontSize = 19.sp),
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
                                    onError = { errorMsg = it }
                                )
                            }
                        }),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    "Как настроение?",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 19.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    if (text.isNotEmpty()) {
                        // Текст введён — кнопка отправки (стрелка вверх)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
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
                                            onError = { errorMsg = it }
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_up),
                                contentDescription = "Отправить",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        // Поле пустое — кнопка микрофона
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
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
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // ── Сообщение об ошибке ───────────────────────────────────
            if (errorMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = errorMsg,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // ── Чипы (только в текстовом режиме) — fade-in при появлении ──
            AnimatedVisibility(
                visible = !isRecording,
                enter = fadeIn(animationSpec = tween(durationMillis = 220))
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickChip("Перевод", Modifier.weight(1f), enabled = !isLoading) { requirePin { onTransfer(null, null, null, null) } }
                        QuickChip("Баланс", Modifier.weight(1f), enabled = !isLoading) { requirePin(onBalance) }
                        QuickChip("Пополнить", Modifier.weight(1f), enabled = !isLoading) { requirePin { onTopup(null, null) } }
                    }
                }
            }

            // ── Inline PIN overlay ──────────────────────────────────────
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
                if (recipientRaw != null) {
                    val candidates = ContactMatcher.search(recipientRaw, context)
                    when {
                        candidates.isEmpty() ->
                            // Контакт не найден — открываем полный список с предзаполненной суммой
                            onTransfer(null, null, null, parsed.amount)
                        ContactMatcher.isHighConfidence(candidates) -> {
                            val c = candidates[0]
                            onTransfer(c.displayName, c.phone, c.bankDisplayName, parsed.amount)
                        }
                        else ->
                            // Несколько кандидатов — показываем выбор
                            onAmbiguousTransfer(candidates, recipientRaw, parsed.amount)
                    }
                } else {
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

            else -> onError("Не понял команду, попробуй иначе")
        }
    }
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
            .background(Color(0xFF001A5E).copy(alpha = 0.97f), RoundedCornerShape(20.dp))
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
                color = Color.White,
                fontSize = 15.sp
            )
            androidx.compose.material3.Text(
                "Отмена",
                color = Color.White.copy(alpha = 0.6f),
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
                                errorMsg.isNotBlank() -> Color(0xFFE57373)
                                i < pin.length -> Color.White
                                else -> Color.White.copy(alpha = 0.3f)
                            },
                            CircleShape
                        )
                )
            }
        }

        if (errorMsg.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(errorMsg, fontSize = 11.sp, color = Color(0xFFE57373))
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
                                    .background(Color.White.copy(alpha = 0.10f), CircleShape)
                                    .clickable(enabled = !isLoading) {
                                        BiometricHelper.authenticate(
                                            activity = activity!!,
                                            onSuccess = { loginWithBiometric() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = "Биометрия",
                                    tint = Color.White.copy(alpha = 0.75f),
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
                Color.White.copy(alpha = if (enabled) 0.18f else 0.08f),
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 16.sp)
    }
}
