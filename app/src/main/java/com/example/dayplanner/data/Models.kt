package com.example.dayplanner.data

import java.time.LocalDate

enum class Design { STANDARD, GRUNGE, CANDY, CHROME, GOLD, NEON }

/** Ein Strich im Braindump. Punkte als x0,y0,x1,y1,... in dp (links oben = 0,0). */
data class InkStroke(val erase: Boolean, val points: List<Float>)

data class Settings(
    val showLeftArrow: Boolean = true,
    val showLock: Boolean = true,
    val dateFormat: Int = 1,              // 1..4
    val rows: Int = 18,                   // 1..24
    val columns: Int = 1,                 // 1 = :00, 2 = :00 + :30
    val dayStart: Int = 6,                // 0..23
    val priorities: Int = 3,              // 1..10 (aktueller Wert)
    val priorityHistory: Map<String, Int> = emptyMap(),
    val showPrioritiesTitle: Boolean = true,
    val showBraindumpTitle: Boolean = true,
    val showTimeboxTitle: Boolean = false,
    val wrapText: Boolean = false,
    val locked: Boolean = false,
    val design: Int = 0,                  // Index in Design
    val themeMode: Int = 0,               // 0 Standard, 1 hell, 2 dunkel
    val zoom: Boolean = false,
    val drawing: Boolean = true,
) {
    /** Lock-in-Modus ist nur aktiv, wenn auch das Schloss-Symbol eingeschaltet ist. */
    val lockActive: Boolean get() = locked && showLock

    val designId: Design get() = Design.entries.getOrElse(design) { Design.STANDARD }

    fun sanitized() = copy(
        dateFormat = dateFormat.coerceIn(1, 4),
        rows = rows.coerceIn(1, 24),
        columns = columns.coerceIn(1, 2),
        dayStart = dayStart.coerceIn(0, 23),
        priorities = priorities.coerceIn(1, 10),
        design = design.coerceIn(0, Design.entries.size - 1),
        themeMode = themeMode.coerceIn(0, 2),
    )

    /** Anzahl der Prioritäten, die an einem bestimmten Tag gelten. */
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
    val strokes: List<InkStroke> = emptyList(),
)

data class DayState(val date: LocalDate, val data: DayData)

data class PasswordInfo(
    val type: Int = NONE,
    val salt: String = "",
    val hash: String = "",
) {
    companion object {
        const val NONE = 0
        const val NUMERIC = 1
        const val TEXT = 2
    }
}
