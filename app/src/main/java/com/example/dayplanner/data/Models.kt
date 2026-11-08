package com.example.dayplanner.data

import java.time.LocalDate

data class Settings(
    val showLeftArrow: Boolean = true,
    val dateFormat: Int = 1,              // 1..4
    val rows: Int = 18,                   // 1..24
    val columns: Int = 1,                 // 1 = :00, 2 = :00 + :30
    val dayStart: Int = 6,                // 0..23
    val priorities: Int = 3,              // 1..10 (aktueller Wert)
    val priorityHistory: Map<String, Int> = emptyMap(), // ISO-Datum -> Anzahl ab diesem Tag
    val showPrioritiesTitle: Boolean = true,
    val showBraindumpTitle: Boolean = true,
    val showTimeboxTitle: Boolean = false,
    val wrapText: Boolean = false,
    val locked: Boolean = false,
) {
    fun sanitized() = copy(
        dateFormat = dateFormat.coerceIn(1, 4),
        rows = rows.coerceIn(1, 24),
        columns = columns.coerceIn(1, 2),
        dayStart = dayStart.coerceIn(0, 23),
        priorities = priorities.coerceIn(1, 10),
    )

    /** Anzahl der Prioritäten, die an einem bestimmten Tag gelten (zurückliegende Tage bleiben unverändert). */
    fun prioritiesFor(date: LocalDate): Int {
        if (priorityHistory.isEmpty()) return priorities
        val sorted = priorityHistory.toSortedMap()
        val key = date.toString()
        return sorted.entries.lastOrNull { it.key <= key }?.value ?: sorted.values.first()
    }

    companion object {
        const val EPOCH = "0000-01-01"
    }
}

data class DayData(
    val priorities: Map<Int, String> = emptyMap(),
    val timebox: Map<Int, String> = emptyMap(),   // Schlüssel = Stunde * 2 + Spalte
    val braindump: String = "",
)

data class DayState(val date: LocalDate, val data: DayData)
