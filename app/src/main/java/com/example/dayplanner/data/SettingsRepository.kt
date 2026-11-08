package com.example.dayplanner.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    private object K {
        val leftArrow = booleanPreferencesKey("left_arrow")
        val dateFormat = intPreferencesKey("date_format")
        val rows = intPreferencesKey("rows")
        val columns = intPreferencesKey("columns")
        val dayStart = intPreferencesKey("day_start")
        val priorities = intPreferencesKey("priorities")
        val priorityHistory = stringPreferencesKey("priority_history")
        val titlePriorities = booleanPreferencesKey("title_priorities")
        val titleBraindump = booleanPreferencesKey("title_braindump")
        val titleTimebox = booleanPreferencesKey("title_timebox")
        val wrap = booleanPreferencesKey("wrap")
        val locked = booleanPreferencesKey("locked")
    }

    val flow: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p -> p.write(transform(p.toSettings()).sanitized()) }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            showLeftArrow = this[K.leftArrow] ?: d.showLeftArrow,
            dateFormat = this[K.dateFormat] ?: d.dateFormat,
            rows = this[K.rows] ?: d.rows,
            columns = this[K.columns] ?: d.columns,
            dayStart = this[K.dayStart] ?: d.dayStart,
            priorities = this[K.priorities] ?: d.priorities,
            priorityHistory = this[K.priorityHistory]?.let(::historyFromJson) ?: emptyMap(),
            showPrioritiesTitle = this[K.titlePriorities] ?: d.showPrioritiesTitle,
            showBraindumpTitle = this[K.titleBraindump] ?: d.showBraindumpTitle,
            showTimeboxTitle = this[K.titleTimebox] ?: d.showTimeboxTitle,
            wrapText = this[K.wrap] ?: d.wrapText,
            locked = this[K.locked] ?: d.locked,
        ).sanitized()
    }

    private fun MutablePreferences.write(s: Settings) {
        this[K.leftArrow] = s.showLeftArrow
        this[K.dateFormat] = s.dateFormat
        this[K.rows] = s.rows
        this[K.columns] = s.columns
        this[K.dayStart] = s.dayStart
        this[K.priorities] = s.priorities
        this[K.priorityHistory] = JSONObject(s.priorityHistory as Map<*, *>).toString()
        this[K.titlePriorities] = s.showPrioritiesTitle
        this[K.titleBraindump] = s.showBraindumpTitle
        this[K.titleTimebox] = s.showTimeboxTitle
        this[K.wrap] = s.wrapText
        this[K.locked] = s.locked
    }

    private fun historyFromJson(json: String): Map<String, Int> =
        runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
}
