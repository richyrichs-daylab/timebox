package com.example.dayplanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import com.example.dayplanner.MainActivity
import com.example.dayplanner.R

enum class WidgetKind { PRIOS, BRAINDUMP, TIMEBOX, PAGE, PAGE_NOGEAR }

object WidgetUpdater {

    private val providers = listOf(
        WidgetKind.PRIOS to WidgetPriosProvider::class.java,
        WidgetKind.BRAINDUMP to WidgetBraindumpProvider::class.java,
        WidgetKind.TIMEBOX to WidgetTimeboxProvider::class.java,
        WidgetKind.PAGE to WidgetPageProvider::class.java,
        WidgetKind.PAGE_NOGEAR to WidgetPageNoGearProvider::class.java,
    )

    /** Zeichnet alle platzierten Widgets neu (nach Änderungen in der App). */
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        var snap: WidgetSnapshot? = null
        for ((kind, cls) in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(app, cls))
            if (ids.isEmpty()) continue
            val current = snap ?: WidgetRenderer.snapshot(app).also { snap = it }
            update(app, manager, kind, ids, current)
        }
    }

    fun update(
        context: Context,
        manager: AppWidgetManager,
        kind: WidgetKind,
        ids: IntArray,
        snap: WidgetSnapshot,
    ) {
        for (id in ids) {
            val (w, h) = sizeDp(context, manager, id)
            val bitmap = WidgetRenderer.render(context, kind, w, h, snap)

            val views = RemoteViews(context.packageName, R.layout.widget_planner)
            views.setImageViewBitmap(R.id.widget_image, bitmap)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context, false))

            val withGear = kind == WidgetKind.PAGE
            views.setViewVisibility(R.id.header_row, if (withGear) View.VISIBLE else View.GONE)
            views.setViewVisibility(
                R.id.spacer_arrow,
                if (snap.settings.showLeftArrow) View.VISIBLE else View.GONE,
            )
            views.setOnClickPendingIntent(R.id.click_gear, openApp(context, true))

            manager.updateAppWidget(id, views)
        }
    }

    private fun openApp(context: Context, settings: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (settings) putExtra(MainActivity.EXTRA_SETTINGS, true)
        }
        return PendingIntent.getActivity(
            context,
            if (settings) 2 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun sizeDp(context: Context, manager: AppWidgetManager, id: Int): Pair<Float, Float> {
        val o = manager.getAppWidgetOptions(id)
        val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = if (landscape) maxW else minW
        val h = if (landscape) minH else maxH
        return (if (w > 0) w else 250).toFloat() to (if (h > 0) h else 250).toFloat()
    }
}
