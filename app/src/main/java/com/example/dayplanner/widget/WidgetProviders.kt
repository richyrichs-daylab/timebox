package com.example.dayplanner.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class PlannerWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, manager, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, this::class.java))
                refresh(context, manager, ids)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val snap = WidgetRenderer.snapshot(app)
                WidgetUpdater.update(app, manager, kind, ids, snap)
            } finally {
                pending.finish()
            }
        }
    }
}

class WidgetPriosProvider : PlannerWidgetProvider(WidgetKind.PRIOS)
class WidgetBraindumpProvider : PlannerWidgetProvider(WidgetKind.BRAINDUMP)
class WidgetTimeboxProvider : PlannerWidgetProvider(WidgetKind.TIMEBOX)
class WidgetPageProvider : PlannerWidgetProvider(WidgetKind.PAGE)
class WidgetPageNoGearProvider : PlannerWidgetProvider(WidgetKind.PAGE_NOGEAR)
