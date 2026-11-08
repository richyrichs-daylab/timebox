package com.example.dayplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Ohne Buttons: Ein Tipp auf ein Datum wechselt sofort den Tag.
 * Geschlossen wird mit Enter oder durch Tippen auf die Fläche außerhalb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val skin = LocalSkin.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.selectedDateMillis) {
        state.selectedDateMillis?.let { ms ->
            val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
            if (picked != initial) onPick(picked)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Feste Breite wie beim Material-Dialog, sonst wird die Wochenleiste (Sonntag) gequetscht.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = skin.bg,
            border = BorderStroke(skin.frameWidth, skin.frame),
            modifier = Modifier
                .requiredWidth(360.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown &&
                        (e.key == Key.Enter || e.key == Key.NumPadEnter)
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .focusRequester(focusRequester)
                .focusable(),
        ) {
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = skin.bg,
                    titleContentColor = skin.ink,
                    headlineContentColor = skin.ink,
                    weekdayContentColor = skin.ink,
                    subheadContentColor = skin.ink,
                    navigationContentColor = skin.ink,
                    yearContentColor = skin.ink,
                    currentYearContentColor = skin.ink,
                    selectedYearContentColor = skin.bg,
                    selectedYearContainerColor = skin.accent,
                    dayContentColor = skin.ink,
                    selectedDayContentColor = skin.bg,
                    selectedDayContainerColor = skin.accent,
                    todayContentColor = skin.ink,
                    todayDateBorderColor = skin.ink,
                ),
            )
        }
    }
}
