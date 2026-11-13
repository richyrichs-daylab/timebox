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
import com.example.dayplanner.ui.formatDate
import com.example.dayplanner.wallpaper.TimeboxWallpaperService
import java.time.LocalDate
import kotlin.math.roundToInt

enum class WidgetKind { PRIOS, BRAINDUMP, TIMEBOX, PAGE }

object WidgetUpdater {

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private val providers = listOf(
        WidgetKind.PRIOS to WidgetPriosProvider::class.java,
        WidgetKind.BRAINDUMP to WidgetBraindumpProvider::class.java,
        WidgetKind.TIMEBOX to WidgetTimeboxProvider::class.java,
        WidgetKind.PAGE to WidgetPageProvider::class.java,
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
        val navigable = kind == WidgetKind.PAGE
        val interactive = s.widgetInteractive

        for (id in ids) {
            // Nur das Seiten-Widget lässt sich mit den Pfeilen durch die Tage blättern.
            val date = if (navigable) today.plusDays(WidgetState.offset(app, id).toLong()) else today
            val snap = WidgetSnapshot(s, repo.load(date), date)
            val (w, h) = sizeDp(app, manager, id)
            val image = WidgetRenderer.render(app, kind, w, h, snap)

            val views = RemoteViews(app.packageName, R.layout.widget_planner)
            views.setImageViewBitmap(R.id.widget_image, image.bitmap)
            views.setOnClickPendingIntent(R.id.widget_root, appIntent(app, id, "app", false))
            // Interaktiv: Name und Datum, die Felder beschriften sich selbst.
            // Nicht interaktiv: der ganze Inhalt für den Screenreader.
            views.setContentDescription(R.id.widget_root, summary(app, kind, snap, full = !interactive))

            // Antippbare Flächen über dem Bild: je Feld und Symbol ein eigenes Rechteck.
            // Ist "interaktiv" aus, bleibt nur das Zahnrad.
            views.removeAllViews(R.id.hit_container)
            val hits = if (interactive) image.hits else image.hits.filter { it.type == HitType.GEAR }
            for (hit in hits) {
                val child = RemoteViews(app.packageName, R.layout.widget_hit)
                child.setViewPadding(
                    R.id.hit_outer,
                    (hit.l * density).roundToInt().coerceAtLeast(0),
                    (hit.t * density).roundToInt().coerceAtLeast(0),
                    ((w - hit.r) * density).roundToInt().coerceAtLeast(0),
                    ((h - hit.b) * density).roundToInt().coerceAtLeast(0),
                )
                child.setOnClickPendingIntent(R.id.hit_inner, hitIntent(app, id, hit, date))
                child.setContentDescription(R.id.hit_inner, describeHit(app, hit, snap))
                views.addView(R.id.hit_container, child)
            }

            manager.updateAppWidget(id, views)
        }
    }

    // ------------------------------------------------------------ Beschriftungen für Screenreader

    private fun timeLabel(key: Int): String = "%d:%02d".format(key / 2, if (key % 2 == 1) 30 else 0)

    private fun cellDescription(app: Context, label: String, text: String, struck: Boolean): String {
        val base = if (text.isEmpty()) {
            "$label, ${app.getString(R.string.a11y_empty)}"
        } else {
            "$label: ${text.take(300)}"
        }
        return if (struck) base + ", " + app.getString(R.string.a11y_struck) else base
    }

    /** Beschriftung einer antippbaren Fläche. */
    private fun describeHit(app: Context, hit: Hit, snap: WidgetSnapshot): String {
        val d = snap.day
        return when (hit.type) {
            HitType.PREV -> app.getString(R.string.cd_prev)
            HitType.NEXT -> app.getString(R.string.cd_next)
            HitType.GEAR -> app.getString(R.string.cd_settings)
            HitType.LOCK -> app.getString(R.string.cd_lock) + ", " +
                app.getString(if (snap.settings.lockActive) R.string.a11y_active else R.string.a11y_inactive)
            HitType.PRIO -> cellDescription(
                app,
                app.getString(R.string.a11y_priority, hit.key + 1),
                d.priorities[hit.key].orEmpty(),
                hit.key in d.strikeP,
            )
            HitType.TIME -> cellDescription(
                app,
                app.getString(R.string.a11y_timebox_cell, timeLabel(hit.key)),
                d.timebox[hit.key].orEmpty(),
                hit.key in d.strikeT,
            )
            HitType.BRAIN -> cellDescription(app, app.getString(R.string.braindump), d.braindump, false)
            else -> ""
        }
    }

    /** Name und Datum des Widgets, bei full = true zusätzlich alle gefüllten Einträge. */
    private fun summary(app: Context, kind: WidgetKind, snap: WidgetSnapshot, full: Boolean): String {
        val s = snap.settings
        val d = snap.day
        val kindLabel = app.getString(
            when (kind) {
                WidgetKind.PRIOS -> R.string.widget_prios
                WidgetKind.BRAINDUMP -> R.string.widget_braindump
                WidgetKind.TIMEBOX -> R.string.widget_timebox
                WidgetKind.PAGE -> R.string.app_name
            }
        )
        val title = kindLabel + ", " + formatDate(snap.date, s.dateFormat)
        if (!full) return title

        val page = kind == WidgetKind.PAGE
        val parts = ArrayList<String>()
        parts.add(title)
        if (kind == WidgetKind.PRIOS || page) {
            for (i in 0 until s.widgetPriosFor(snap.date)) {
                val t = d.priorities[i].orEmpty()
                if (t.isNotBlank()) parts.add(app.getString(R.string.a11y_priority, i + 1) + ": " + t.take(300))
            }
        }
        if (kind == WidgetKind.TIMEBOX || page) {
            val dayStart = s.dayStartFor(snap.date)
            for (i in 0 until s.rowsFor(snap.date)) {
                val hour = (dayStart + i) % 24
                for (c in 0 until s.columns) {
                    val key = hour * 2 + c
                    val t = d.timebox[key].orEmpty()
                    if (t.isNotBlank()) {
                        parts.add(app.getString(R.string.a11y_timebox_cell, timeLabel(key)) + ": " + t.take(300))
                    }
                }
            }
        }
        if ((kind == WidgetKind.BRAINDUMP || page) && d.braindump.isNotBlank()) {
            parts.add(app.getString(R.string.braindump) + ": " + d.braindump.take(500))
        }
        if (parts.size == 1) parts.add(app.getString(R.string.a11y_widget_none))
        return parts.joinToString(". ")
    }

    // ------------------------------------------------------------ Aktionen

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
