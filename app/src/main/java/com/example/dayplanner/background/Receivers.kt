package com.example.dayplanner.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dayplanner.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                DailyNotifications.post(app)
                DailyNotifications.schedule(app, SettingsRepository(app).current())
            } finally {
                pending.finish()
            }
        }
    }
}

class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AutoBackup.runIfDue(app)
                AutoBackup.schedule(app, SettingsRepository(app).current())
            } finally {
                pending.finish()
            }
        }
    }
}

/** Nach Neustart und nach App-Updates gehen Alarme verloren und werden hier neu geplant. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = SettingsRepository(app).current()
                DailyNotifications.schedule(app, s)
                AutoBackup.schedule(app, s)
            } finally {
                pending.finish()
            }
        }
    }
}
