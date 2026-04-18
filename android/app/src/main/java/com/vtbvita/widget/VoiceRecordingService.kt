package com.vtbvita.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import com.vtbvita.widget.ui.effects.AuroraRenderer
import timber.log.Timber
import androidx.core.app.NotificationCompat
import com.vtbvita.widget.nlp.ContactMatcher
import com.vtbvita.widget.nlp.NlpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class VoiceRecordingService : Service() {

    companion object {
        const val ACTION_START  = "com.vtbvita.widget.ACTION_VOICE_START"
        const val ACTION_STOP   = "com.vtbvita.widget.ACTION_VOICE_STOP"   // отмена
        const val ACTION_SUBMIT = "com.vtbvita.widget.ACTION_VOICE_SUBMIT" // стоп + отправить

        private const val NOTIF_ID   = 9001
        private const val CHANNEL_ID = "vita_recording"

        // id колец для анимации
        private val RING_IDS = intArrayOf(R.id.ring1, R.id.ring2, R.id.ring3)
        private const val ANIM_PERIOD_MS = 1400L
        private const val ANIM_FRAME_MS  = 80L   // ~12 fps

        // VAD — клиентский детектор тишины
        private const val VAD_SILENCE_THRESHOLD = 0.20f   // ниже → тишина (фон ~0.08–0.15)
        private const val VAD_SILENCE_MS        = 1200L   // тишина дольше 1.2 сек → submit
        private const val VAD_MIN_SPEECH_MS     = 600L    // нужно хотя бы 0.6 сек речи до VAD
        private const val VAD_POLL_MS           = 60L     // интервал опроса
    }

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var recorder:  VoiceStreamingRecorder? = null
    private var animJob:   Job? = null
    private var vadJob:    Job? = null

    /** Флаг: запись отменена — игнорируем onFinal от сервера. */
    @Volatile private var cancelled  = false
    /** Флаг: submit уже инициирован (ручной или VAD) — защита от двойного вызова. */
    @Volatile private var submitted  = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> beginRecording()
            ACTION_STOP   -> cancelRecording()
            ACTION_SUBMIT -> submitRecording()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAnimation()
        stopVAD()
        recorder?.stop()
        recorder = null
        job.cancel()
        super.onDestroy()
    }

    // ── Запись ──────────────────────────────────────────────────────────────

    private fun beginRecording() {
        if (recorder != null) return
        cancelled = false
        submitted = false
        Timber.i("beginRecording")

        startForeground(NOTIF_ID, buildNotification())
        VitaWidgetProvider.showPreparing(this)

        recorder = VoiceStreamingRecorder(
            onReady   = {
                Timber.i("recorder ready → RECORDING")
                VitaWidgetProvider.showRecording(this, "")
                startAnimation()
                startVAD()
            },
            onPartial = { text ->
                updatePartialText(text)
            },
            onFinal   = { text ->
                if (!cancelled) {
                    Timber.i("onFinal: '%s'", text)
                    stopAnimation()
                    finishRecording(text)
                }
            },
            onError   = { msg ->
                if (!cancelled) {
                    Timber.e("recorder error: %s", msg)
                    stopAnimation()
                    @Suppress("DEPRECATION") stopForeground(true)
                    VitaWidgetProvider.resetToIdle(this)
                    stopSelf()
                }
            }
        )
        recorder?.start(scope)
    }

    private fun cancelRecording() {
        Timber.i("cancelRecording")
        cancelled = true
        stopAnimation()
        stopVAD()
        recorder?.stop()
        recorder = null
        @Suppress("DEPRECATION") stopForeground(true)
        VitaWidgetProvider.resetToIdle(this)
        stopSelf()
    }

    private fun submitRecording() {
        if (submitted) return
        submitted = true
        Timber.i("submitRecording")
        stopAnimation()
        stopVAD()
        // Показываем спиннер пока сервер обрабатывает финальный аудио
        VitaWidgetProvider.showPreparing(this)
        // Отправляем "DONE" — WebSocket остаётся открытым.
        // Сервер выходит из receive-цикла, вызывает Yandex STT, отвечает final.
        // onFinal сработает и вызовет finishRecording().
        recorder?.stopAndSubmit()
        // recorder НЕ обнуляем, stopSelf() НЕ вызываем — ждём onFinal
    }

    private fun finishRecording(text: String) {
        recorder?.stop()
        recorder = null
        @Suppress("DEPRECATION") stopForeground(true)

        if (text.isBlank()) {
            VitaWidgetProvider.resetToIdle(this)
            stopSelf()
            return
        }

        scope.launch {
            val result = NlpService.parse(text, this@VoiceRecordingService)
            VitaWidgetProvider.resetToIdle(this@VoiceRecordingService)

            result.onFailure {
                Timber.e("NLP failed: %s", it.message)
                stopSelf()
                return@launch
            }

            val parsed = result.getOrThrow()
            Timber.i("NLP intent=%s confidence=%.2f", parsed.intent, parsed.confidence)

            when (parsed.intent) {
                "open_app" -> {
                    parsed.app?.let { SystemIntentHandler.openApp(it, this@VoiceRecordingService) }
                        ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                "alarm" -> startActivity(
                    SystemIntentHandler.setAlarm(parsed.hour ?: 8, parsed.minute ?: 0)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "timer" -> startActivity(
                    SystemIntentHandler.setTimer(parsed.durationSeconds ?: 60)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "call" -> startActivity(
                    SystemIntentHandler.call(parsed.contact, this@VoiceRecordingService)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "navigate" -> startActivity(
                    SystemIntentHandler.navigate(parsed.destination ?: "", this@VoiceRecordingService)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "balance", "transfer", "topup" -> {
                    startActivity(
                        Intent(this@VoiceRecordingService, InputActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra(InputActivity.EXTRA_MODE, InputActivity.MODE_VOICE_RESULT)
                            putExtra(InputActivity.EXTRA_VOICE_TEXT, text)
                        }
                    )
                }
                else -> Timber.w("Unknown intent '%s', discarding", parsed.intent)
            }

            stopSelf()
        }
    }

    // ── VAD — автоматическое завершение по тишине ───────────────────────────

    /**
     * Корутина-детектор тишины.
     * Логика:
     *  1. Ждём MIN_SPEECH_MS суммарной "речи" (amplitude > порог) — чтобы не триггерить
     *     на паузу до того, как пользователь вообще начал говорить.
     *  2. После первой речи: если amplitude < порог дольше SILENCE_MS → auto-submit.
     *  3. Любое движение амплитуды выше порога — сбрасывает таймер тишины.
     */
    private fun startVAD() {
        stopVAD()
        vadJob = scope.launch(Dispatchers.IO) {
            var speechAccumulatedMs = 0L
            var silenceStartMs      = 0L
            var inSilence           = false

            var logCounter = 0
            Timber.d("VAD started, threshold=%s", VAD_SILENCE_THRESHOLD)
            while (isActive) {
                delay(VAD_POLL_MS)
                val amp = recorder?.amplitude ?: 0.08f

                // Логируем каждые ~300мс для калибровки
                if (++logCounter % 5 == 0) {
                    Timber.d("VAD amp=%.3f speech=%dms inSilence=%b", amp, speechAccumulatedMs, inSilence)
                }

                if (amp >= VAD_SILENCE_THRESHOLD) {
                    // Речь
                    speechAccumulatedMs += VAD_POLL_MS
                    inSilence = false
                    silenceStartMs = 0L
                } else {
                    // Тишина
                    if (speechAccumulatedMs >= VAD_MIN_SPEECH_MS) {
                        if (!inSilence) {
                            inSilence = true
                            silenceStartMs = SystemClock.uptimeMillis()
                            Timber.d("VAD: silence started (speech=%dms)", speechAccumulatedMs)
                        } else {
                            val silenceDuration = SystemClock.uptimeMillis() - silenceStartMs
                            if (silenceDuration >= VAD_SILENCE_MS) {
                                Timber.i("VAD: silence %dms → auto-submit", silenceDuration)
                                submitRecording()
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopVAD() {
        vadJob?.cancel()
        vadJob = null
    }

    // ── Анимация колец ──────────────────────────────────────────────────────

    /**
     * Цикл ~12fps: обновляет scaleX/scaleY/alpha трёх колец через
     * partiallyUpdateAppWidget() — минимальный IPC, только float-значения.
     * Кольца органические: scaleX ≠ scaleY за счёт фазовой модуляции.
     */
    private fun startAnimation() {
        stopAnimation()
        animJob = scope.launch {
            val awm     = AppWidgetManager.getInstance(this@VoiceRecordingService)
            val cn      = ComponentName(this@VoiceRecordingService, VitaWidgetProvider::class.java)
            val density = resources.displayMetrics.density

            // Размер bitmap для aurora: фиксированное разрешение (достаточно для мягкого эффекта)
            val bitmapW = 400
            val bitmapH = 80
            // Радиус скругления в пикселях пропорционально реальному dp (32dp)
            val cornerPx = 32f * density * (bitmapH.toFloat() / (64f * density))

            val animStart = SystemClock.uptimeMillis()
            var frameCount = 0

            while (isActive) {
                val now = SystemClock.uptimeMillis()
                val amp = (recorder?.amplitude ?: 0.08f).coerceIn(0.08f, 1f)

                val partial = RemoteViews(packageName, R.layout.widget_vita)

                // ── Кольца (каждый кадр) ─────────────────────────────────────
                RING_IDS.forEachIndexed { i, id ->
                    val phase = ((now % ANIM_PERIOD_MS).toFloat() / ANIM_PERIOD_MS + i / 3f) % 1f
                    val base  = phase * amp.coerceIn(0.3f, 1f)
                    val alpha = (1f - phase) * 0.48f

                    val wobbleX =  0.14f * sin(phase * 2f * PI.toFloat() * 1.3f + i * 1.1f)
                    val wobbleY = -0.10f * sin(phase * 2f * PI.toFloat() * 0.9f + i * 0.7f)

                    partial.setFloat(id, "setScaleX", (base * (1f + wobbleX)).coerceAtLeast(0f))
                    partial.setFloat(id, "setScaleY", (base * (1f + wobbleY)).coerceAtLeast(0f))
                    partial.setFloat(id, "setAlpha",  alpha.coerceIn(0f, 1f))
                }

                // ── Aurora bitmap (каждые 2 кадра ≈ 160ms, ~6fps) ───────────
                // Aurora медленная, 6fps достаточно; снижает размер IPC
                if (frameCount % 2 == 0) {
                    val timeSec = (now - animStart) / 1000f
                    val bitmap  = AuroraRenderer.createBitmap(bitmapW, bitmapH, timeSec, amp, cornerPx)
                    partial.setImageViewBitmap(R.id.capsule_bg, bitmap)
                }
                frameCount++

                val ids = awm.getAppWidgetIds(cn)
                if (ids.isNotEmpty()) {
                    awm.partiallyUpdateAppWidget(ids, partial)
                }

                delay(ANIM_FRAME_MS)
            }
        }
    }

    private fun stopAnimation() {
        animJob?.cancel()
        animJob = null
    }

    /** Обновляет только текст в виджете без сброса анимации (partial update). */
    private fun updatePartialText(text: String) {
        val awm = AppWidgetManager.getInstance(this)
        val cn  = ComponentName(this, VitaWidgetProvider::class.java)
        val partial = RemoteViews(packageName, R.layout.widget_vita).apply {
            setTextViewText(R.id.tv_recording_text, text.ifBlank { "Говорите…" })
            setFloat(R.id.tv_recording_text, "setAlpha", if (text.isBlank()) 0.50f else 0.92f)
        }
        awm.partiallyUpdateAppWidget(awm.getAppWidgetIds(cn), partial)
    }

    // ── Уведомление ─────────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Запись голоса", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("VTB Vita")
            .setContentText("Запись голоса…")
            .addAction(R.drawable.ic_close, "Остановить", stopPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
