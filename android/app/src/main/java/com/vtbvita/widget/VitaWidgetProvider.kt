package com.vtbvita.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews

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

    companion object {

        /** Строит RemoteViews в зависимости от состояния сессии. */
        fun defaultViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setViewVisibility(R.id.capsule, View.VISIBLE)
                setViewVisibility(R.id.tv_status, View.GONE)
                setViewVisibility(R.id.tv_prompt, View.VISIBLE)

                if (SessionManager.isLoggedIn(context)) {
                    val prompt = SessionManager.currentPersona(context)?.widgetPrompt
                        ?: context.getString(R.string.widget_prompt)
                    setTextViewText(R.id.tv_prompt, prompt)
                    setFloat(R.id.tv_prompt, "setAlpha", 0.80f)
                    setOnClickPendingIntent(R.id.tv_prompt, inputPi(context, recording = false))
                    setOnClickPendingIntent(R.id.btn_mic, inputPi(context, recording = true))
                } else {
                    setTextViewText(R.id.tv_prompt, "Войдите в аккаунт")
                    setFloat(R.id.tv_prompt, "setAlpha", 0.65f)
                    setOnClickPendingIntent(R.id.tv_prompt, mainPi(context))
                    setOnClickPendingIntent(R.id.btn_mic, mainPi(context))
                }
            }

        /** Скрывает капсулу виджета пока открыт InputActivity. */
        fun hideWidget(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, VitaWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setViewVisibility(R.id.capsule, View.INVISIBLE)
            }
            ids.forEach { awm.updateAppWidget(it, views) }
        }

        /**
         * Обновляет текст подсказки виджета (динамическая персонализация).
         * Например: "Платёж по кредитке завтра" или "Как настроение?"
         */
        fun updatePrompt(context: Context, promptText: String) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, VitaWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = defaultViews(context).apply {
                setTextViewText(R.id.tv_prompt, promptText)
            }
            ids.forEach { awm.updateAppWidget(it, views) }
        }

        /**
         * Показывает строку статуса на ~10 секунд, затем сбрасывает виджет.
         * Вызывается из ConfirmActivity после успешного подтверждения.
         */
        fun showStatus(context: Context, text: String) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, VitaWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val statusViews = RemoteViews(context.packageName, R.layout.widget_vita).apply {
                setViewVisibility(R.id.tv_prompt, View.GONE)
                setViewVisibility(R.id.tv_status, View.VISIBLE)
                setTextViewText(R.id.tv_status, text)
            }
            ids.forEach { awm.updateAppWidget(it, statusViews) }

            Handler(Looper.getMainLooper()).postDelayed({
                ids.forEach { awm.updateAppWidget(it, defaultViews(context)) }
            }, 10_000L)
        }

        private fun mainPi(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun inputPi(context: Context, recording: Boolean): PendingIntent {
            val intent = Intent(context, InputActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(InputActivity.EXTRA_MODE, if (recording) InputActivity.MODE_RECORDING else InputActivity.MODE_TEXT)
            }
            return PendingIntent.getActivity(
                context,
                if (recording) 1 else 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
