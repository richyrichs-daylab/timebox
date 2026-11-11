package com.example.dayplanner.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dayplanner.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetActions {
    const val PREV = "app.daylab.timebox.widget.PREV"
    const val NEXT = "app.daylab.timebox.widget.NEXT"
    const val LOCK = "app.daylab.timebox.widget.LOCK"
    const val EXTRA_ID = "widget_id"
}

/** Merkt sich pro Widget, um wie viele Tage es gegenüber heute weitergeblättert ist. */
object WidgetState {
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("widget_state", Context.MODE_PRIVATE)

    fun offset(context: Context, id: Int): Int = prefs(context).getInt("offset_$id", 0)

    fun setOffset(context: Context, id: Int, value: Int) {
        prefs(context).edit().putInt("offset_$id", value.coerceIn(-3650, 3650)).apply()
    }

    fun clear(context: Context, ids: IntArray) {
        val editor = prefs(context).edit()
        ids.forEach { editor.remove("offset_$it") }
        editor.apply()
    }

    fun resetAll(context: Context) {
        val p = prefs(context)
        val editor = p.edit()
        p.all.keys.filter { it.startsWith("offset_") }.forEach { editor.remove(it) }
        editor.apply()
    }
}

/** Pfeile und Schloss der Widgets. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getIntExtra(WidgetActions.EXTRA_ID, -1)
        val action = intent.action
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    WidgetActions.PREV -> shift(app, id, -1)
                    WidgetActions.NEXT -> shift(app, id, 1)
                    WidgetActions.LOCK -> toggleLock(app)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun shift(app: Context, id: Int, delta: Int) {
        if (id < 0) return
        WidgetState.setOffset(app, id, WidgetState.offset(app, id) + delta)
        val kind = WidgetUpdater.kindOf(app, id) ?: return
        WidgetUpdater.update(app, AppWidgetManager.getInstance(app), kind, intArrayOf(id))
    }

    /** Wie das Schloss in der App: wirkungslos, solange eine Zeit-/Tagessperre läuft. */
    private suspend fun toggleLock(app: Context) {
        SettingsRepository(app).update { s ->
            if (s.lockUntil > System.currentTimeMillis()) s else s.copy(locked = !s.locked)
        }
        WidgetUpdater.refreshAll(app)
    }
}
