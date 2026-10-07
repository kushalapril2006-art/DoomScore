package com.gridcc.doomscore.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gridcc.doomscore.android.DoomApplication
import com.gridcc.doomscore.android.MainActivity
import com.gridcc.doomscore.android.R

class CounterWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { updateAll(context) }
    companion object {
        fun updateAll(context: Context) {
            val store = (context.applicationContext as DoomApplication).store
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CounterWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.counter_widget)
            views.setTextViewText(R.id.widget_count, "${store.day().total} reels")
            views.setTextViewText(R.id.widget_detail, "today · ${store.streak().first}d scroll streak · best ${store.personalBest()}")
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            manager.updateAppWidget(ids, views)
        }
    }
}
