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
    val showShare: Boolean = false,
    val grayPast: Boolean = false,
    val highlightNow: Boolean = false,
    val strikeDouble: Boolean = false,
    val notifPlan: Boolean = false,
    val notifPrios: Boolean = false,
    val notifBoth: Boolean = false,
    val allowRelease: Boolean = true,     // Toggle "Lock-in-Aufhebung"
    val lockUntil: Long = 0L,             // Ende der Zeit-/Tagessperre (Epoch-Millis), 0 = keine
    val lockReleasable: Boolean = true,   // beim Start der Sperre festgehalten
    val autoBackup: Boolean = false,
    val autoBackupN: Int = 1,
    val autoBackupUnit: Int = 0,          // 0 täglich, 1 wöchentlich, 2 monatlich, 3 jährlich
    val lastBackup: String = "",          // ISO-Datum
) {
    /** Lock-in-Modus: manuell (Schloss-Symbol) oder durch eine laufende Zeit-/Tagessperre. */
    fun lockActiveAt(now: Long): Boolean = (locked && showLock) || lockUntil > now

    val lockActive: Boolean get() = lockActiveAt(System.currentTimeMillis())

    val designId: Design get() = Design.entries.getOrElse(design) { Design.STANDARD }

    fun sanitized() = copy(
        dateFormat = dateFormat.coerceIn(1, 4),
        rows = rows.coerceIn(1, 24),
        columns = columns.coerceIn(1, 2),
        dayStart = dayStart.coerceIn(0, 23),
        priorities = priorities.coerceIn(1, 10),
        design = design.coerceIn(0, Design.entries.size - 1),
        themeMode = themeMode.coerceIn(0, 2),
        autoBackupN = autoBackupN.coerceIn(1, 999),
        autoBackupUnit = autoBackupUnit.coerceIn(0, 3),
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
    val strikeP: Set<Int> = emptySet(),           // durchgestrichene Prioritäten (Index)
    val strikeT: Set<Int> = emptySet(),           // durchgestrichene Timebox-Zellen (Schlüssel)
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
