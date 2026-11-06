package com.dsha.sagittarius.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dsha.sagittarius.R
import com.dsha.sagittarius.data.BudgetMath
import com.dsha.sagittarius.data.BudgetRepository
import com.dsha.sagittarius.ui.MainActivity
import com.dsha.sagittarius.util.BudgetPalette
import com.dsha.sagittarius.util.DateText
import com.dsha.sagittarius.util.Money
import com.dsha.sagittarius.util.WidgetTier

/**
 * 小部件只读取 BudgetRepository.getActiveSnapshot()，与悬浮窗/主界面共用同一套计算。
 * RemoteViews 只使用 setTextViewText / setTextColor / setInt / setOnClickPendingIntent。
 */
object WidgetUpdateManager {

    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(appContext, BudgetWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        val snapshot = BudgetRepository.getInstance(appContext).getActiveSnapshot()
        update(appContext, appWidgetManager, ids, snapshot)
    }

    fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        ids: IntArray,
        snapshot: com.dsha.sagittarius.data.ActiveBudgetSnapshot
    ) {
        if (ids.isEmpty()) return
        val ratio = BudgetMath.remainingRatio(snapshot)
        val tier = BudgetPalette.widgetTier(ratio)
        val views = RemoteViews(context.packageName, R.layout.widget_budget)

        val background = when (tier) {
            WidgetTier.GREEN -> R.drawable.widget_bg_green
            WidgetTier.BLUE -> R.drawable.widget_bg_blue
            WidgetTier.ORANGE -> R.drawable.widget_bg_orange
            WidgetTier.RED -> R.drawable.widget_bg_red
            WidgetTier.DEEP_RED -> R.drawable.widget_bg_deep_red
        }
        views.setInt(R.id.widgetRoot, "setBackgroundResource", background)

        val textColors = when (tier) {
            WidgetTier.GREEN -> Triple("#FFFFFFFF", "#E6FFFFFF", "#CCFFFFFF")
            WidgetTier.BLUE -> Triple("#FFFFFFFF", "#E6EFF6FF", "#CCDBEAFE")
            WidgetTier.ORANGE -> Triple("#FFFFFFFF", "#FFFFF7ED", "#CCFFEDD5")
            WidgetTier.RED -> Triple("#FFFFFFFF", "#FFFEF2F2", "#CCFEE2E2")
            WidgetTier.DEEP_RED -> Triple("#FFFFFFFF", "#FFFEF2F2", "#CCFECACA")
        }
        views.setTextColor(R.id.widgetTitle, android.graphics.Color.parseColor(textColors.third))
        views.setTextColor(R.id.widgetRange, android.graphics.Color.parseColor(textColors.third))
        views.setTextColor(R.id.widgetRemaining, android.graphics.Color.parseColor(textColors.first))
        views.setTextColor(R.id.widgetPercent, android.graphics.Color.parseColor(textColors.second))

        if (snapshot.isConfigured) {
            views.setTextViewText(
                R.id.widgetTitle,
                context.getString(R.string.current_remaining)
            )
            views.setTextViewText(
                R.id.widgetRemaining,
                Money.formatSignedCents(snapshot.remainingCents ?: 0L)
            )
            views.setTextViewText(
                R.id.widgetPercent,
                context.getString(
                    R.string.widget_used,
                    Money.usedPercent(snapshot.usedRatio)
                )
            )
        } else {
            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_no_budget))
            views.setTextViewText(
                R.id.widgetRemaining,
                context.getString(R.string.widget_no_budget)
            )
            views.setTextViewText(R.id.widgetPercent, "先设置预算")
        }

        val state = snapshot.state
        views.setTextViewText(
            R.id.widgetRange,
            if (state == null) "" else DateText.rangeLabelCompact(
                state.activeRangeType,
                state.activeStartDate,
                state.activeEndDate
            )
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_SHOW_FLOATING, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        appWidgetManager.updateAppWidget(ids, views)
    }
}
