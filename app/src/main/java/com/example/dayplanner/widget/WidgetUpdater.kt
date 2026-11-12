package com.example.dayplanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.RemoteViews
import com.example.dayplanner.MainActivity
import com.example.dayplanner.R
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.wallpaper.TimeboxWallpaperService
import java.time.LocalDate
import kotlin.math.roundToInt

enum class WidgetKind { PRIOS, BRAINDUMP, TIMEBOX, PAGE, PAGE_NOGEAR }

object WidgetUpdater {

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private val providers = listOf(
        WidgetKind.PRIOS to WidgetPriosProvider::class.java,
        WidgetKind.BRAINDUMP to WidgetBraindumpProvider::class.java,
        WidgetKind.TIMEBOX to WidgetTimeboxProvider::class.java,
        WidgetKind.PAGE to WidgetPageProvider::class.java,
        WidgetKind.PAGE_NOGEAR to WidgetPageNoGearProvider::class.java,
    )

    /** Welche Art von Widget gehört zu dieser Widget-ID? */
    fun kindOf(context: Context, id: Int): WidgetKind? {
        val className = AppWidgetManager.getInstance(context.applicationContext)
            .getAppWidgetInfo(id)?.provider?.className ?: return null
        return providers.firstOrNull { it.second.name == className }?.first
    }

    /** Zeichnet alle platzierten Widgets und den Live-Hintergrund neu (nach Änderungen in der App). */
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val settings = SettingsRepository(app).current()
        for ((kind, cls) in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(app, cls))
            if (ids.isEmpty()) continue
            update(app, manager, kind, ids, settings)
        }
        TimeboxWallpaperService.redrawAll()
    }

    suspend fun update(
        context: Context,
        manager: AppWidgetManager,
        kind: WidgetKind,
        ids: IntArray,
        settings: Settings? = null,
    ) {
        val app = context.applicationContext
        val s = settings ?: SettingsRepository(app).current()
        val repo = DayRepository(app)
        val today = LocalDate.now()
        val density = app.resources.displayMetrics.density
        val navigable = kind == WidgetKind.PAGE || kind == WidgetKind.PAGE_NOGEAR

        for (id in ids) {
            // Nur die Seiten-Widgets lassen sich mit den Pfeilen durch die Tage blättern.
            val date = if (navigable) today.plusDays(WidgetState.offset(app, id).toLong()) else today
            val snap = WidgetSnapshot(s, repo.load(date), date)
            val (w, h) = sizeDp(app, manager, id)
            val image = WidgetRenderer.render(app, kind, w, h, snap)

            val views = RemoteViews(app.packageName, R.layout.widget_planner)
            views.setImageViewBitmap(R.id.widget_image, image.bitmap)
            views.setOnClickPendingIntent(R.id.widget_root, appIntent(app, id, "app", false))

            // Antippbare Flächen über dem Bild: je Feld und Symbol ein eigenes Rechteck.
            views.removeAllViews(R.id.hit_container)
            for (hit in image.hits) {
                val child = RemoteViews(app.packageName, R.layout.widget_hit)
                child.setViewPadding(
                    R.id.hit_outer,
                    (hit.l * density).roundToInt().coerceAtLeast(0),
                    (hit.t * density).roundToInt().coerceAtLeast(0),
                    ((w - hit.r) * density).roundToInt().coerceAtLeast(0),
                    ((h - hit.b) * density).roundToInt().coerceAtLeast(0),
                )
                child.setOnClickPendingIntent(R.id.hit_inner, hitIntent(app, id, hit, date))
                views.addView(R.id.hit_container, child)
            }

            manager.updateAppWidget(id, views)
        }
    }

    private fun appIntent(context: Context, id: Int, tag: String, settings: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = Uri.parse("timebox://widget/$id/$tag")
            if (settings) putExtra(MainActivity.EXTRA_SETTINGS, true)
        }
        return PendingIntent.getActivity(context, 0, intent, FLAGS)
    }

    private fun broadcast(context: Context, id: Int, action: String, uri: Uri): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply {
            this.action = action
            data = uri
            putExtra(WidgetActions.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(context, 0, intent, FLAGS)
    }

    private fun hitIntent(context: Context, id: Int, hit: Hit, date: LocalDate): PendingIntent {
        val uri = Uri.parse("timebox://widget/$id/${hit.type}/${hit.key}")
        return when (hit.type) {
            HitType.PREV -> broadcast(context, id, WidgetActions.PREV, uri)
            HitType.NEXT -> broadcast(context, id, WidgetActions.NEXT, uri)
            HitType.LOCK -> broadcast(context, id, WidgetActions.LOCK, uri)
            HitType.GEAR -> appIntent(context, id, "gear", true)
            HitType.PRIO, HitType.TIME, HitType.BRAIN -> {
                val intent = Intent(context, WidgetEditActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = uri
                    putExtra(WidgetEditActivity.EXTRA_TYPE, hit.type)
                    putExtra(WidgetEditActivity.EXTRA_KEY, hit.key)
                    putExtra(WidgetEditActivity.EXTRA_DATE, date.toString())
                }
                PendingIntent.getActivity(context, 0, intent, FLAGS)
            }
            else -> appIntent(context, id, "app", false)
        }
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
