package com.vtbvita.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.vtbvita.widget.personalization.HintRepository
import com.vtbvita.widget.ui.effects.AuroraRenderer
import timber.log.Timber

class VitaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach {
            appWidgetManager.updateAppWidget(it, defaultViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_USER_PRESENT ||
            intent.action == Intent.ACTION_SCREEN_ON ||
            intent.action == "com.vtbvita.widget.ACTION_REFRESH_HINT"
        ) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, VitaWidgetProvider::class.java))
            onUpdate(context, awm, ids)
        }
    }

    companion object {

        private const val AURORA_BITMAP_W = 400
        private const val AURORA_BITMAP_H = 85

        private fun staticAuroraBitmap(context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val cornerPx = 16f * density * (AURORA_BITMAP_H.toFloat() / (68f * density))
            return AuroraRenderer.createBitmap(
                AURORA_BITMAP_W, AURORA_BITMAP_H,
                timeSec = 0f, amplitude = 0.08f,
                cornerRadiusPx = cornerPx
            )
        }

        // ── Состояния виджета ────────────────────────────────────────────────

        fun defaultViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setImageViewBitmap(R.id.capsule_bg, staticAuroraBitmap(context))
                setViewVisibility(R.id.capsule_bg, View.VISIBLE)
                setViewVisibility(R.id.capsule, View.VISIBLE)
                setViewVisibility(R.id.tv_prompt, View.VISIBLE)
                setViewVisibility(R.id.btn_mic, View.VISIBLE)
                setContentDescription(R.id.tv_prompt, context.getString(R.string.a11y_widget_idle_prompt))
                setViewVisibility(R.id.tv_status, View.GONE)
                setViewVisibility(R.id.btn_cancel_rec, View.GONE)
                setViewVisibility(R.id.pb_preparing, View.GONE)
                setViewVisibility(R.id.tv_recording_text, View.GONE)
                setViewVisibility(R.id.btn_stop, View.GONE)
                setViewVisibility(R.id.fl_recording_submit, View.GONE)
                setViewVisibility(R.id.iv_status_check, View.GONE)

                val fullyLoggedIn = SessionManager.hasAppToken(context) && SessionManager.isLoggedIn(context)
                if (fullyLoggedIn) {
                    val persona = SessionManager.currentPersona(context)
                    val hint = HintRepository.resolveHint(context, persona)
                    setTextViewText(R.id.tv_prompt, hint)
                    setFloat(R.id.tv_prompt, "setAlpha", 0.90f)
                    setFloat(R.id.capsule, "setAlpha", 1.0f)
                    setFloat(R.id.capsule_bg, "setAlpha", 1.0f)
                    setViewVisibility(R.id.btn_mic, View.VISIBLE)
                    setOnClickPendingIntent(R.id.tv_prompt, textInputPi(context))
                    setOnClickPendingIntent(R.id.btn_mic, voiceStartPi(context))
                    HintRepository.fetchAndApply(context)
                } else {
                    setTextViewText(R.id.tv_prompt, "Войдите в приложение")
                    setFloat(R.id.tv_prompt, "setAlpha", 0.75f)
                    setFloat(R.id.capsule, "setAlpha", 0.7f)
                    setFloat(R.id.capsule_bg, "setAlpha", 0.7f)
                    setViewVisibility(R.id.btn_mic, View.GONE)
                    setOnClickPendingIntent(R.id.tv_prompt, mainPi(context))
                }
            }

        /** PREPARING: спиннер + "Подготовка…" + кнопка отмены справа. */
        fun showPreparing(context: Context) {
            Timber.d("widget → PREPARING")
            val views = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setImageViewBitmap(R.id.capsule_bg, staticAuroraBitmap(context))
                setViewVisibility(R.id.capsule_bg, View.VISIBLE)
                setFloat(R.id.capsule_bg, "setAlpha", 1.0f)
                setViewVisibility(R.id.capsule, View.VISIBLE)
                setViewVisibility(R.id.tv_prompt, View.GONE)
                setViewVisibility(R.id.tv_status, View.GONE)
                setViewVisibility(R.id.btn_mic, View.GONE)
                setViewVisibility(R.id.btn_cancel_rec, View.GONE)
                setViewVisibility(R.id.fl_recording_submit, View.GONE)
                setViewVisibility(R.id.iv_status_check, View.GONE)
                setViewVisibility(R.id.pb_preparing, View.VISIBLE)
                setContentDescription(R.id.pb_preparing, context.getString(R.string.a11y_widget_preparing))
                setViewVisibility(R.id.tv_recording_text, View.VISIBLE)
                setTextViewText(R.id.tv_recording_text, "Подготовка…")
                setContentDescription(R.id.tv_recording_text, context.getString(R.string.a11y_widget_preparing))
                setFloat(R.id.tv_recording_text, "setAlpha", 0.65f)
                setViewVisibility(R.id.btn_stop, View.VISIBLE)
                setOnClickPendingIntent(R.id.btn_stop, voiceStopPi(context))
            }
            updateAll(context, views)
        }

        /**
         * RECORDING: кнопка ✕ слева + текст + кольца с кнопкой ✓ справа.
         * После этого вызова сервис запускает анимационный цикл через
         * partiallyUpdateAppWidget() для колец.
         */
        fun showRecording(context: Context, partialText: String) {
            Timber.d("widget → RECORDING")
            val views = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setViewVisibility(R.id.capsule_bg, View.VISIBLE)
                setViewVisibility(R.id.capsule, View.VISIBLE)
                setViewVisibility(R.id.tv_prompt, View.GONE)
                setViewVisibility(R.id.tv_status, View.GONE)
                setViewVisibility(R.id.btn_mic, View.GONE)
                setViewVisibility(R.id.pb_preparing, View.GONE)
                setViewVisibility(R.id.btn_stop, View.GONE)
                setViewVisibility(R.id.iv_status_check, View.GONE)
                setViewVisibility(R.id.btn_cancel_rec, View.VISIBLE)
                setOnClickPendingIntent(R.id.btn_cancel_rec, voiceStopPi(context))
                setViewVisibility(R.id.tv_recording_text, View.VISIBLE)
                val display = partialText.ifBlank { "Говорите…" }
                setTextViewText(R.id.tv_recording_text, display)
                setContentDescription(R.id.tv_recording_text, "Запись: $display")
                setFloat(R.id.tv_recording_text, "setAlpha", if (partialText.isBlank()) 0.50f else 0.92f)
                setViewVisibility(R.id.fl_recording_submit, View.VISIBLE)
                setOnClickPendingIntent(R.id.fl_recording_submit, voiceSubmitPi(context))
                // Сбрасываем кольца в начальное состояние (анимация стартует сразу после)
                for (id in intArrayOf(R.id.ring1, R.id.ring2, R.id.ring3)) {
                    setFloat(id, "setScaleX", 0f)
                    setFloat(id, "setScaleY", 0f)
                    setFloat(id, "setAlpha", 0f)
                }
            }
            updateAll(context, views)
        }

        /** Сброс в IDLE. */
        fun resetToIdle(context: Context) {
            Timber.d("widget → IDLE")
            updateAll(context, defaultViews(context))
        }

        /** Скрывает капсулу пока открыт InputActivity (текстовый режим). */
        fun hideWidget(context: Context) {
            incrementOpenCount(context)
            val views = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setViewVisibility(R.id.capsule, View.INVISIBLE)
                setViewVisibility(R.id.capsule_bg, View.INVISIBLE)
            }
            updateAll(context, views)
        }

        /** Показывает строку статуса на ~10 секунд, затем сбрасывает виджет. */
        fun showStatus(context: Context, text: String) {
            Timber.i("widget → STATUS: %s", text)
            val statusViews = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setImageViewBitmap(R.id.capsule_bg, staticAuroraBitmap(context))
                setViewVisibility(R.id.capsule_bg, View.VISIBLE)
                setFloat(R.id.capsule_bg, "setAlpha", 1.0f)
                setViewVisibility(R.id.capsule, View.VISIBLE)
                setViewVisibility(R.id.tv_prompt, View.GONE)
                setViewVisibility(R.id.btn_mic, View.GONE)
                setViewVisibility(R.id.btn_cancel_rec, View.GONE)
                setViewVisibility(R.id.pb_preparing, View.GONE)
                setViewVisibility(R.id.tv_recording_text, View.GONE)
                setViewVisibility(R.id.btn_stop, View.GONE)
                setViewVisibility(R.id.fl_recording_submit, View.GONE)
                setViewVisibility(R.id.iv_status_check, View.VISIBLE)
                setContentDescription(R.id.iv_status_check, context.getString(R.string.a11y_widget_success))
                setViewVisibility(R.id.tv_status, View.VISIBLE)
                setTextViewText(R.id.tv_status, text)
                setContentDescription(R.id.tv_status, text)
                setOnClickPendingIntent(R.id.tv_status, textInputPi(context))
                setOnClickPendingIntent(R.id.iv_status_check, textInputPi(context))
                setOnClickPendingIntent(R.id.capsule_bg, textInputPi(context))
            }
            updateAll(context, statusViews)
            Handler(Looper.getMainLooper()).postDelayed({
                updateAll(context, defaultViews(context))
            }, 3_000L)
        }

        // Подсказка: синхронный кэш в HintRepository + асинхронный запрос к серверу

        fun incrementOpenCount(context: Context) {
            val prefs = context.getSharedPreferences("vita_hints", 0)
            val count = prefs.getInt("open_count", 0) + 1
            prefs.edit().putInt("open_count", count).apply()
        }

        fun updatePrompt(context: Context, promptText: String) {
            val views = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setImageViewBitmap(R.id.capsule_bg, staticAuroraBitmap(context))
                setViewVisibility(R.id.capsule_bg, View.VISIBLE)
                setViewVisibility(R.id.capsule, View.VISIBLE)
                setViewVisibility(R.id.tv_prompt, View.VISIBLE)
                setViewVisibility(R.id.btn_mic, View.VISIBLE)
                setViewVisibility(R.id.tv_status, View.GONE)
                setViewVisibility(R.id.btn_cancel_rec, View.GONE)
                setViewVisibility(R.id.pb_preparing, View.GONE)
                setViewVisibility(R.id.tv_recording_text, View.GONE)
                setViewVisibility(R.id.btn_stop, View.GONE)
                setViewVisibility(R.id.fl_recording_submit, View.GONE)
                setViewVisibility(R.id.iv_status_check, View.GONE)
                setTextViewText(R.id.tv_prompt, promptText)
                setContentDescription(R.id.tv_prompt, promptText)
                setFloat(R.id.tv_prompt, "setAlpha", 0.90f)
                setFloat(R.id.capsule, "setAlpha", 1.0f)
                setFloat(R.id.capsule_bg, "setAlpha", 1.0f)
                setOnClickPendingIntent(R.id.tv_prompt, textInputPi(context))
                setOnClickPendingIntent(R.id.btn_mic, voiceStartPi(context))
            }
            updateAll(context, views)
        }

        // ── Хелперы ──────────────────────────────────────────────────────────

        fun updateAll(context: Context, views: RemoteViews) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, VitaWidgetProvider::class.java))
            ids.forEach { awm.updateAppWidget(it, views) }
        }

        fun getWidgetIds(context: Context): IntArray {
            val awm = AppWidgetManager.getInstance(context)
            return awm.getAppWidgetIds(ComponentName(context, VitaWidgetProvider::class.java))
        }

        private fun voiceStartPi(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context, 1,
                Intent(context, VoiceRecordingService::class.java).apply {
                    action = VoiceRecordingService.ACTION_START
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun voiceStopPi(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context, 2,
                Intent(context, VoiceRecordingService::class.java).apply {
                    action = VoiceRecordingService.ACTION_STOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun voiceSubmitPi(context: Context): PendingIntent =
            PendingIntent.getForegroundService(
                context, 3,
                Intent(context, VoiceRecordingService::class.java).apply {
                    action = VoiceRecordingService.ACTION_SUBMIT
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun textInputPi(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 0,
                Intent(context, InputActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(InputActivity.EXTRA_MODE, InputActivity.MODE_TEXT)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun mainPi(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 4,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
