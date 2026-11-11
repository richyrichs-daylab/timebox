package com.example.dayplanner.data

import android.content.Context
import android.util.Base64
import org.json.JSONObject

object BackupManager {
    /** Einstellungen (ohne Sperre und Passwörter) und alle Tage als JSON. */
    suspend fun build(context: Context): JSONObject {
        val app = context.applicationContext
        return JSONObject()
            .put("app", "dayplanner")
            .put("version", 1)
            .put("settings", SettingsRepository(app).current().toJson(forBackup = true))
            .put("days", DayRepository(app).exportAll())
    }

    /** Macht aus dem Backup den Dateitext. Ist ein Backup-Passwort gesetzt, wird er verschlüsselt. */
    suspend fun serialize(context: Context, root: JSONObject): String {
        val s = SettingsRepository(context.applicationContext).current()
        if (s.backupKey.isEmpty() || s.backupSalt.isEmpty()) return root.toString()
        val salt = Base64.decode(s.backupSalt, Base64.NO_WRAP)
        val key = Base64.decode(s.backupKey, Base64.NO_WRAP)
        return BackupCrypto.encrypt(root.toString(), salt, key)
    }
}
