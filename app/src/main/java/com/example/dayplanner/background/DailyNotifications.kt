package com.example.dayplanner.background

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.dayplanner.MainActivity
import com.example.dayplanner.R
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.ui.formatDate
import java.time.LocalDate
import java.time.ZonedDateTime

object DailyNotifications {
    private const val CHANNEL = "daily"

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        100,
        Intent(context, NotifyReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Plant die nächste Benachrichtigung zur Stunde des Tagesbeginns (oder bricht sie ab). */
    fun schedule(context: Context, s: Settings) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = pending(app)
        am.cancel(pi)
        if (!(s.notifPlan || s.notifPrios || s.notifBoth)) return

        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(s.dayStart, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
    }

    private fun planLines(s: Settings, d: DayData): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until s.rows) {
            val hour = (s.dayStart + i) % 24
            for (c in 0 until s.columns) {
                val t = d.timebox[hour * 2 + c].orEmpty().trim()
                if (t.isNotEmpty()) out.add((if (c == 0) "$hour" else "$hour:30") + ": " + t)
            }
        }
        return out
    }

    private fun priorityLines(s: Settings, d: DayData, date: LocalDate): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until s.prioritiesFor(date)) {
            val t = d.priorities[i].orEmpty().trim()
            if (t.isNotEmpty()) out.add("${i + 1}: $t")
        }
        return out
    }

    suspend fun post(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        val s = SettingsRepository(app).current()
        val date = LocalDate.now()
        val day = DayRepository(app).load(date)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )

        val plan = planLines(s, day)
        val prios = priorityLines(s, day, date)

        if (s.notifPlan && plan.isNotEmpty()) show(app, nm, 1, s, date, plan.joinToString("\n"))
        if (s.notifPrios && prios.isNotEmpty()) show(app, nm, 2, s, date, prios.joinToString("\n"))
        if (s.notifBoth && (plan.isNotEmpty() || prios.isNotEmpty())) {
            val text = listOf(prios, plan)
                .filter { it.isNotEmpty() }
                .joinToString("\n\n") { it.joinToString("\n") }
            show(app, nm, 3, s, date, text)
        }
    }

    private fun show(
        app: Context,
        nm: NotificationManager,
        id: Int,
        s: Settings,
        date: LocalDate,
        text: String,
    ) {
        val tap = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timebox)
            .setContentTitle(app.getString(R.string.app_name))
            .setSubText(formatDate(date, s.dateFormat))
            .setContentText(text.lineSequence().first())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
