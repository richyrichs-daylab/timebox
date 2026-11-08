package com.example.dayplanner.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatDate(date: LocalDate, format: Int, locale: Locale = Locale.getDefault()): String {
    val formatter = when (format) {
        1 -> DateTimeFormatter.ofPattern("d.M.yyyy", locale)
        2 -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        3 -> DateTimeFormatter.ofPattern("EEE, d.M.yyyy", locale)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    return date.format(formatter)
}
