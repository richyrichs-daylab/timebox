package com.example.dayplanner.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dayplanner.background.AutoBackup
import com.example.dayplanner.background.DailyNotifications
import com.example.dayplanner.data.BackupManager
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.DayState
import com.example.dayplanner.data.InkStroke
import com.example.dayplanner.data.PasswordInfo
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.data.keepRuntimeFrom
import com.example.dayplanner.data.settingsFromJson
import com.example.dayplanner.export.Exporter
import com.example.dayplanner.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

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
        viewModelScope.launch {
            applySchedules()
            runCatching { AutoBackup.runIfDue(getApplication<Application>()) }
        }
    }

    // ---------- Hintergrund: Widgets und Alarme ----------

    private suspend fun refreshWidgets() {
        withContext(Dispatchers.Default) {
            runCatching { WidgetUpdater.refreshAll(getApplication<Application>()) }
        }
    }

    private suspend fun applySchedules() {
        val s = settingsRepo.current()
        val app = getApplication<Application>()
        DailyNotifications.schedule(app, s)
        AutoBackup.schedule(app, s)
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

    fun toggleStrikePriority(date: LocalDate, index: Int) =
        edit(date) { it.copy(strikeP = if (index in it.strikeP) it.strikeP - index else it.strikeP + index) }

    fun toggleStrikeTimebox(date: LocalDate, key: Int) =
        edit(date) { it.copy(strikeT = if (key in it.strikeT) it.strikeT - key else it.strikeT + key) }

    private fun edit(date: LocalDate, change: (DayData) -> DayData) {
        val cur = _day.value
        if (cur != null && cur.date == date) {
            val updated = change(cur.data)
            _day.value = DayState(date, updated)
            viewModelScope.launch {
                dayRepo.save(date, updated)
                refreshWidgets()
            }
        } else {
            viewModelScope.launch {
                dayRepo.save(date, change(dayRepo.load(date)))
                refreshWidgets()
            }
        }
    }

    private fun Map<Int, String>.withEntry(key: Int, value: String): Map<Int, String> =
        if (value.isEmpty()) this - key else this + (key to value)

    // ---------- Einstellungen ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            settingsRepo.update(transform)
            applySchedules()
            refreshWidgets()
        }
    }

    /** Schloss-Symbol: wirkungslos, solange eine Zeit-/Tagessperre läuft. */
    fun toggleLock() = updateSettings { s ->
        if (s.lockUntil > System.currentTimeMillis()) s else s.copy(locked = !s.locked)
    }

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

    // ---------- Zeitschloss / Tagesschloss ----------

    private fun lockUntil(target: Long) = updateSettings { s ->
        val now = System.currentTimeMillis()
        val active = s.lockUntil > now
        s.copy(
            // eine laufende Sperre kann nur verlängert, nie verkürzt werden
            lockUntil = maxOf(s.lockUntil, target),
            lockReleasable = if (active) s.lockReleasable && s.allowRelease else s.allowRelease,
        )
    }

    fun startTimeLock(durationMs: Long) = lockUntil(System.currentTimeMillis() + durationMs)

    /** Sperre bis zum Ende des gewählten Tages. */
    fun startDayLock(endDate: LocalDate) {
        val end = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        lockUntil(end)
    }

    /** Reset-Kästen: hebt die Sperre auf, falls sie beim Start als aufhebbar festgelegt wurde. */
    fun releaseLock() = updateSettings { s ->
        if (s.lockUntil > System.currentTimeMillis()) {
            if (s.lockReleasable) s.copy(lockUntil = 0L, locked = false) else s
        } else {
            s.copy(locked = false)
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
                val root = BackupManager.build(getApplication<Application>())
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            onResult(ok)
        }
    }

    fun importBackup(uri: Uri, onResult: (Boolean) -> Unit) {
        // Während einer Zeit-/Tagessperre ist der Import gesperrt.
        if ((settings.value?.lockUntil ?: 0L) > System.currentTimeMillis()) {
            onResult(false)
            return
        }
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
                    settingsRepo.update { cur -> settingsFromJson(so).keepRuntimeFrom(cur) }
                }
                selectDate(currentDate)
                applySchedules()
                refreshWidgets()
            }.isSuccess
            onResult(ok)
        }
    }

    fun enableAutoBackup(n: Int, unit: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(autoBackup = true, autoBackupN = n, autoBackupUnit = unit, lastBackup = "")
            }
            applySchedules()
            // erstes Backup sofort, danach nach Intervall
            onResult(AutoBackup.runNow(getApplication<Application>()))
        }
    }

    fun disableAutoBackup() {
        viewModelScope.launch {
            settingsRepo.update { it.copy(autoBackup = false) }
            applySchedules()
        }
    }

    // ---------- Export und Teilen ----------

    fun exportPdf(dates: Set<LocalDate>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(Exporter.pdf(getApplication<Application>(), dates.sorted())) }
    }

    fun exportPng(dates: Set<LocalDate>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(Exporter.png(getApplication<Application>(), dates.sorted())) }
    }

    fun prepareShare(onReady: (Uri?) -> Unit) {
        val s = settings.value
        val d = _day.value
        if (s == null || d == null) {
            onReady(null)
            return
        }
        viewModelScope.launch {
            onReady(Exporter.shareUri(getApplication<Application>(), s, d.data, d.date))
        }
    }
}
