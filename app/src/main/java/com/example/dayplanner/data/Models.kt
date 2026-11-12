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
    val customDesign: String = "",        // ID eines importierten Designs, leer = eingebautes Design
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
    val widgetPrios: Int = 0,             // in Widgets angezeigte Prioritäten, 0 = so viele wie oben (folgt automatisch)
    val widgetShowEmpty: Boolean = true,  // leere Prioritäten-Felder in Widgets anzeigen
    val allowRelease: Boolean = true,     // Toggle "Lock-in-Aufhebung"
    val lockUntil: Long = 0L,             // Ende der Zeit-/Tagessperre (Epoch-Millis), 0 = keine
    val lockReleasable: Boolean = true,   // beim Start der Sperre festgehalten
    val autoBackup: Boolean = false,
    val autoBackupN: Int = 1,
    val autoBackupUnit: Int = 0,          // 0 täglich, 1 wöchentlich, 2 monatlich, 3 jährlich
    val autoBackupKeep: Int = 0,          // wie viele automatische Backups bleiben, 0 = alle
    val lastBackup: String = "",          // ISO-Datum
    val dailyBraindump: String = "",      // Vorlage "tägliche Fragen"
    val dailyFilledUntil: String = "",    // ISO-Datum, bis wohin die Vorlage eingetragen ist
    val backupSalt: String = "",          // Backup-Passwort: Salz (Base64), nur auf dem Gerät
    val backupKey: String = "",           // Backup-Passwort: abgeleiteter Schlüssel (Base64), nur auf dem Gerät
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
        widgetPrios = widgetPrios.coerceIn(0, 10),
        autoBackupN = autoBackupN.coerceIn(1, 999),
        autoBackupUnit = autoBackupUnit.coerceIn(0, 3),
        autoBackupKeep = autoBackupKeep.coerceIn(0, 999),
    )

    /** Anzahl der Prioritäten, die an einem bestimmten Tag gelten. */
    fun prioritiesFor(date: LocalDate): Int {
        if (priorityHistory.isEmpty()) return priorities
        val sorted = priorityHistory.toSortedMap()
        val key = date.toString()
        return sorted.entries.lastOrNull { it.key <= key }?.value ?: sorted.values.first()
    }

    /** Anzahl der in Widgets angezeigten Prioritäten für einen Tag (höchstens die Anzahl dieses Tages). */
    fun widgetPriosFor(date: LocalDate): Int {
        val total = prioritiesFor(date)
        return (if (widgetPrios <= 0) total else minOf(widgetPrios, total)).coerceAtLeast(1)
    }

    /** Die Zahl, die in den Einstellungen im Feld "angezeigte Prioritäten" steht. */
    val widgetPriosEffective: Int
        get() = (if (widgetPrios <= 0) priorities else minOf(widgetPrios, priorities)).coerceAtLeast(1)

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
