package com.example.dayplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.DayState
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlannerViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val dayRepo = DayRepository(app)

    val settings: StateFlow<Settings?> =
        settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    // ---------- Bearbeiten ----------

    fun setPriority(date: LocalDate, index: Int, text: String) =
        edit(date) { it.copy(priorities = it.priorities.withEntry(index, text)) }

    fun setTimebox(date: LocalDate, key: Int, text: String) =
        edit(date) { it.copy(timebox = it.timebox.withEntry(key, text)) }

    fun setBraindump(date: LocalDate, text: String) =
        edit(date) { it.copy(braindump = text) }

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
}
