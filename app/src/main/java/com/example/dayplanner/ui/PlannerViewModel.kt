package com.example.dayplanner.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.DayState
import com.example.dayplanner.data.InkStroke
import com.example.dayplanner.data.PasswordInfo
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.data.settingsFromJson
import com.example.dayplanner.data.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

class PlannerViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val dayRepo = DayRepository(app)

    val settings: StateFlow<Settings?> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val password: StateFlow<PasswordInfo?> =
        settingsRepo.passwordFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    private val _day = MutableStateFlow<DayState?>(null)
    val day: StateFlow<DayState?> = _day

    private var currentDate: LocalDate = LocalDate.now()

    init {
        selectDate(currentDate)
    }

    // ---------- Tagesnavigation ----------

    fun selectDate(date: LocalDate) {
        currentDate = date
        viewModelScope.launch {
            val data = dayRepo.load(date)
            if (date == currentDate) _day.value = DayState(date, data)
        }
    }

    fun shiftDay(delta: Long) = selectDate(currentDate.plusDays(delta))

    fun goToday() = selectDate(LocalDate.now())

    // ---------- Bearbeiten ----------

    fun setPriority(date: LocalDate, index: Int, text: String) =
        edit(date) { it.copy(priorities = it.priorities.withEntry(index, text)) }

    fun setTimebox(date: LocalDate, key: Int, text: String) =
        edit(date) { it.copy(timebox = it.timebox.withEntry(key, text)) }

    fun setBraindump(date: LocalDate, text: String) =
        edit(date) { it.copy(braindump = text) }

    fun addStroke(date: LocalDate, stroke: InkStroke) =
        edit(date) { it.copy(strokes = it.strokes + stroke) }

    private fun edit(date: LocalDate, change: (DayData) -> DayData) {
        val cur = _day.value
        if (cur != null && cur.date == date) {
            val updated = change(cur.data)
            _day.value = DayState(date, updated)
            viewModelScope.launch { dayRepo.save(date, updated) }
        } else {
            viewModelScope.launch {
                dayRepo.save(date, change(dayRepo.load(date)))
            }
        }
    }

    private fun Map<Int, String>.withEntry(key: Int, value: String): Map<Int, String> =
        if (value.isEmpty()) this - key else this + (key to value)

    // ---------- Einstellungen ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun toggleLock() = updateSettings { it.copy(locked = !it.locked) }

    /** Gilt ab heute. Zurückliegende Tage behalten ihre bisherige Anzahl. */
    fun setPriorityCount(n: Int) = updateSettings { s ->
        val count = n.coerceIn(1, 10)
        if (count == s.priorities) {
            s
        } else {
            val history = s.priorityHistory.toMutableMap()
            if (history.isEmpty()) history[Settings.EPOCH] = s.priorities
            history[LocalDate.now().toString()] = count
            s.copy(priorities = count, priorityHistory = history)
        }
    }

    // ---------- Passwort ----------

    fun createPassword(type: Int, secret: String) {
        viewModelScope.launch {
            settingsRepo.setPassword(type, secret)
            _unlocked.value = true
        }
    }

    fun deletePassword() {
        viewModelScope.launch { settingsRepo.clearPassword() }
    }

    fun tryUnlock(input: String, onWrong: () -> Unit) {
        val info = password.value ?: return
        viewModelScope.launch {
            if (settingsRepo.check(info, input)) _unlocked.value = true else onWrong()
        }
    }

    // ---------- Backup ----------

    fun exportBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val root = JSONObject()
                    .put("app", "dayplanner")
                    .put("version", 1)
                    .put("settings", settingsRepo.current().toJson())
                    .put("days", dayRepo.exportAll())
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            onResult(ok)
        }
    }

    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val root = JSONObject(text)
                require(root.optString("app") == "dayplanner")
                root.optJSONObject("days")?.let { dayRepo.importAll(it) }
                root.optJSONObject("settings")?.let { so ->
                    settingsRepo.update { settingsFromJson(so) }
                }
                selectDate(currentDate)
            }.isSuccess
            onResult(ok)
        }
    }
}
