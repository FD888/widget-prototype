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

class PulseWidgetProvider : AppWidgetProvider() {

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

        /** Строит RemoteViews с тремя кнопками действий (исходное состояние). */
        fun defaultViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_pulse).apply {
                setViewVisibility(R.id.ll_actions, View.VISIBLE)
                setViewVisibility(R.id.tv_status, View.GONE)
                setOnClickPendingIntent(R.id.btn_transfer, actionPi(context, "transfer"))
                setOnClickPendingIntent(R.id.btn_balance, actionPi(context, "balance"))
                setOnClickPendingIntent(R.id.btn_topup, actionPi(context, "topup"))
            }

        /**
         * Показывает строку статуса на ~10 секунд, затем сбрасывает виджет.
         * Вызывается из ConfirmActivity после успешного подтверждения.
         */
        fun showStatus(context: Context, text: String) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, PulseWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val statusViews = RemoteViews(context.packageName, R.layout.widget_pulse).apply {
                setViewVisibility(R.id.ll_actions, View.GONE)
                setViewVisibility(R.id.tv_status, View.VISIBLE)
                setTextViewText(R.id.tv_status, text)
            }
            ids.forEach { awm.updateAppWidget(it, statusViews) }

            Handler(Looper.getMainLooper()).postDelayed({
                ids.forEach { awm.updateAppWidget(it, defaultViews(context)) }
            }, 10_000L)
        }

        private fun actionPi(context: Context, action: String): PendingIntent {
            val intent = when (action) {
                "balance" -> Intent(context, BalanceActivity::class.java)
                else -> Intent(context, InputActivity::class.java).apply {
                    putExtra(InputActivity.EXTRA_INTENT_TYPE, action)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
