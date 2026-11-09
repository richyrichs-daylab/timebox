package com.example.dayplanner.data

import android.content.Context
import org.json.JSONObject

object BackupManager {
    /** Einstellungen (ohne Sperre) und alle Tage als JSON. */
    suspend fun build(context: Context): JSONObject {
        val app = context.applicationContext
        return JSONObject()
            .put("app", "dayplanner")
            .put("version", 1)
            .put("settings", SettingsRepository(app).current().toJson(forBackup = true))
            .put("days", DayRepository(app).exportAll())
    }
}
