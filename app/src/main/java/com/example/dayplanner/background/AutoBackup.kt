package com.example.dayplanner.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.dayplanner.data.BackupManager
import com.example.dayplanner.data.BackupRetention
import com.example.dayplanner.data.Downloads
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.data.TreeBackups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZonedDateTime

object AutoBackup {

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        101,
        Intent(context, BackupReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Tägliche Prüfung gegen 3 Uhr, solange das automatische Backup aktiv ist. */
    fun schedule(context: Context, s: Settings) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = pending(app)
        am.cancel(pi)
        if (!s.autoBackup) return

        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(3, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
    }

    fun isDue(s: Settings, today: LocalDate): Boolean {
        if (!s.autoBackup) return false
        val last = runCatching { LocalDate.parse(s.lastBackup) }.getOrNull() ?: return true
        val n = s.autoBackupN.toLong()
        val due = when (s.autoBackupUnit) {
            0 -> last.plusDays(1)
            1 -> last.plusWeeks(n)
            2 -> last.plusMonths(n)
            else -> last.plusYears(n)
        }
        return !today.isBefore(due)
    }

    suspend fun runIfDue(context: Context): Boolean {
        val app = context.applicationContext
        val s = SettingsRepository(app).current()
        return if (isDue(s, LocalDate.now())) runNow(app) else false
    }

    /**
     * Schreibt das Backup in den gewählten Ordner (sonst oder bei einem Fehler nach Download/Timebox),
     * mit Backup-Passwort verschlüsselt, falls gesetzt. Danach werden die überzähligen eigenen Backups gelöscht.
     */
    suspend fun runNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext
            val repo = SettingsRepository(app)
            val s = repo.current()
            val today = LocalDate.now()
            val name = "timebox-backup-$today.json"
            val text = BackupManager.serialize(app, BackupManager.build(app))

            var ok = false
            var usedTree = false
            if (s.autoBackupTree.isNotEmpty()) {
                ok = TreeBackups.write(app, Uri.parse(s.autoBackupTree), name, text)
                usedTree = ok
            }
            if (!ok) {
                ok = Downloads.save(app, name, "application/json", "Timebox") { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            if (ok) {
                repo.update { it.copy(lastBackup = today.toString()) }
                if (usedTree) {
                    TreeBackups.applyKeep(app, s.autoBackupKeep)
                } else {
                    BackupRetention.apply(app, s.autoBackupKeep)
                }
            }
            ok
        } catch (e: Exception) {
            false
        }
    }
}
