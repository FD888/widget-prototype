package com.vtbvita.widget

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin
import kotlin.math.abs

class InputActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INTENT_TYPE = "intent_type"
        const val EXTRA_MODE = "mode"
        const val MODE_TEXT = "text"
        const val MODE_RECORDING = "recording"
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
                    startInRecordingMode = startMode == MODE_RECORDING,
                    onDismiss = { finish() },
                    onBalance = {
                        val i = android.content.Intent(this, BalanceActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
                    onAmbiguousTransfer = { candidates, amount ->
                        ContactDisambiguationActivity.pendingCandidates = candidates
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
    startInRecordingMode: Boolean,
    onDismiss: () -> Unit,
    onBalance: () -> Unit,
    onTransfer: (name: String?, phone: String?, bankDisplayName: String?, amount: Double?) -> Unit,
    onAmbiguousTransfer: (List<ContactCandidate>, Double?) -> Unit,
    onTopup: (String?, Double?) -> Unit,
    onConfirm: (ConfirmationData) -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    // Инициализируем сразу — нет мигания текстового режима при открытии через микрофон
    var isRecording by remember { mutableStateOf(startInRecordingMode) }
    var recordingSeconds by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Banking PIN gate
    var pinRequired by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun requirePin(action: () -> Unit) {
        if (BankingSession.isValid()) {
            action()
        } else {
            pendingAction = action
            pinRequired = true
        }
    }

    // Запись
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var amplitudes by remember { mutableStateOf(List(30) { 0f }) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val result = startRecording(context)
            recorder = result.first
            recordingFile = result.second
            isRecording = true
        }
    }

    // Автозапуск записи если открыли через кнопку микрофона
    LaunchedEffect(startInRecordingMode) {
        if (startInRecordingMode) {
            delay(150)
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val result = startRecording(context)
                recorder = result.first
                recordingFile = result.second
                isRecording = true
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            delay(80)
            focusRequester.requestFocus()
        }
    }

    // Полинг амплитуды + таймер пока идёт запись
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            var tickMs = 0L
            while (isActive && isRecording) {
                val raw = recorder?.maxAmplitude?.toFloat() ?: 0f
                val norm = (raw / 32767f).coerceIn(0.08f, 1f)
                amplitudes = amplitudes.drop(1) + norm
                delay(80)
                tickMs += 80
                if (tickMs >= 1000) { recordingSeconds++; tickMs = 0 }
            }
        } else {
            amplitudes = List(30) { 0f }
            recordingSeconds = 0
        }
    }

    fun stopAndDiscard() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recordingFile?.delete()
        recordingFile = null
        isRecording = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isRecording) stopAndDiscard()
                    onDismiss()
                }
            )
    ) {
        val topOffset = maxHeight * 0.15f
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
                if (isRecording) {
                    // ── Режим записи ──────────────────────────────────
                    // Кнопка отмены (красный крестик)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFD32F2F), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                stopAndDiscard()
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

                    Spacer(Modifier.width(8.dp))

                    // Таймер
                    Text(
                        text = "%d:%02d".format(recordingSeconds / 60, recordingSeconds % 60),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        modifier = Modifier.width(32.dp)
                    )

                    Spacer(Modifier.width(4.dp))

                    // Вейвформ
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        drawWaveform(amplitudes, size)
                    }

                    Spacer(Modifier.width(10.dp))

                    // Кнопка готово (зелёный чекмарк)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF00875A), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                // Пока просто удаляем запись — NLP подключим позже
                                stopAndDiscard()
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Готово",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
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
                                    onAmbiguousTransfer = { candidates, a -> requirePin { onAmbiguousTransfer(candidates, a) } },
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
                                    fontSize = 17.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(Modifier.width(8.dp))

                    // Иконка микрофона — переключает в режим записи
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
                            .clickable {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    val result = startRecording(context)
                                    recorder = result.first
                                    recordingFile = result.second
                                    isRecording = true
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

            // ── Чипы (только в текстовом режиме) ──────────────────────
            if (!isRecording) {
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
    }
}

// ── Рисуем вейвформ на Canvas ─────────────────────────────────────────────

private fun DrawScope.drawWaveform(amplitudes: List<Float>, canvasSize: Size) {
    val barCount = amplitudes.size
    val gap = 3.dp.toPx()
    val barWidth = ((canvasSize.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
    val maxBarHeight = canvasSize.height * 0.85f
    val centerY = canvasSize.height / 2f

    amplitudes.forEachIndexed { i, amp ->
        val barHeight = (maxBarHeight * amp).coerceAtLeast(4.dp.toPx())
        val x = i * (barWidth + gap)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(x, centerY - barHeight / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
        )
    }
}

// ── Вспомогательные ──────────────────────────────────────────────────────────

private fun startRecording(context: android.content.Context): Pair<MediaRecorder, File> {
    val file = File(context.cacheDir, "vita_voice_${System.currentTimeMillis()}.m4a")
    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        MediaRecorder(context)
    else
        @Suppress("DEPRECATION") MediaRecorder()
    rec.apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setOutputFile(file.absolutePath)
        prepare()
        start()
    }
    return Pair(rec, file)
}

private fun submitText(
    text: String,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit,
    onBalance: () -> Unit,
    onTransfer: (name: String?, phone: String?, bankDisplayName: String?, amount: Double?) -> Unit,
    onAmbiguousTransfer: (List<ContactCandidate>, Double?) -> Unit,
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
                            onAmbiguousTransfer(candidates, parsed.amount)
                    }
                } else {
                    onTransfer(null, null, null, parsed.amount)
                }
            }

            "topup" -> onTopup(parsed.phone, parsed.amount)

            "open_app" -> {
                val appIntent = parsed.app?.let {
                    SystemIntentHandler.openApp(it, context)
                }
                if (appIntent != null) {
                    try { context.startActivity(appIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (_: Exception) { onError("Не удалось открыть приложение") }
                } else {
                    onError("Приложение не найдено")
                }
                onDismiss()
            }

            "alarm" -> {
                val h = parsed.hour ?: 8
                val m = parsed.minute ?: 0
                try {
                    context.startActivity(
                        SystemIntentHandler.setAlarm(h, m)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
                onDismiss()
            }

            "timer" -> {
                val secs = parsed.durationSeconds ?: 60
                try {
                    context.startActivity(
                        SystemIntentHandler.setTimer(secs)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
                onDismiss()
            }

            "call" -> {
                try {
                    context.startActivity(
                        SystemIntentHandler.call(parsed.contact, context)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
                onDismiss()
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
                        Spacer(Modifier.size(56.dp))
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
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp)
    }
}
