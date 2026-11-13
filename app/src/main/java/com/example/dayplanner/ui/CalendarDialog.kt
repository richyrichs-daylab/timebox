package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Hauptseite: Ein Tipp auf ein Datum wechselt sofort den Tag. Schließen mit Enter oder außerhalb. */
@Composable
fun CalendarDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DialogFrame(
        onDismiss = onDismiss,
        modifier = Modifier
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        CalendarBody(initial, setOf(current), null) { d ->
            current = d
            onPick(d)
        }
    }
}

/** Mehrere Tage per Tippen wählen und abwählen, unten „Abbruch" und die Aktion. */
@Composable
fun MultiDayDialog(
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: (Set<LocalDate>) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<LocalDate>()) }
    DialogFrame(onCancel) {
        CalendarBody(LocalDate.now(), selected, null) { d ->
            selected = if (d in selected) selected - d else selected + d
        }
        Spacer(Modifier.height(12.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = confirmText,
            confirmEnabled = selected.isNotEmpty(),
            onCancel = onCancel,
            onConfirm = { onConfirm(selected) },
        )
    }
}

/** Genau einen Tag wählen (optional ab einem frühesten Datum). */
@Composable
fun SingleDayDialog(
    minDate: LocalDate?,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    DialogFrame(onCancel) {
        CalendarBody(minDate ?: LocalDate.now(), setOfNotNull(selected), minDate) { d -> selected = d }
        Spacer(Modifier.height(12.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = confirmText,
            confirmEnabled = selected != null,
            onCancel = onCancel,
            onConfirm = { selected?.let(onConfirm) },
        )
    }
}

@Composable
private fun CalendarBody(
    initial: LocalDate,
    selected: Set<LocalDate>,
    minDate: LocalDate?,
    onDay: (LocalDate) -> Unit,
) {
    val skin = LocalSkin.current
    val locale = Locale.getDefault()
    var month by remember { mutableStateOf(YearMonth.from(initial)) }
    val today = LocalDate.now()
    val first = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val fullFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val todayWord = stringResource(R.string.a11y_today)
    val prevMonthLabel = stringResource(R.string.cd_prev_month)
    val nextMonthLabel = stringResource(R.string.cd_next_month)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Monat und Navigation
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .semantics { contentDescription = prevMonthLabel; role = Role.Button }
                    .tapOrHold { month = month.minusMonths(1) },
                contentAlignment = Alignment.Center,
            ) { Chevron(left = true, color = skin.ink, modifier = Modifier.size(20.dp)) }
            Text(
                text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                color = skin.ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                Modifier
                    .size(44.dp)
                    .semantics { contentDescription = nextMonthLabel; role = Role.Button }
                    .tapOrHold { month = month.plusMonths(1) },
                contentAlignment = Alignment.Center,
            ) { Chevron(left = false, color = skin.ink, modifier = Modifier.size(20.dp)) }
        }

        // Wochentage (jede Spalte gleich breit, auch der Sonntag). Für Screenreader überflüssig:
        // Jeder Tag nennt sein volles Datum.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            for (k in 0 until 7) {
                val dow = first.plus(k.toLong())
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        dow.getDisplayName(java.time.format.TextStyle.SHORT, locale),
                        color = skin.ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Tage: immer 6 Zeilen, damit das Fenster beim Blättern nicht springt
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (k in 0 until 7) {
                    val dayNum = week * 7 + k - offset + 1
                    Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNum)
                            val isSel = date in selected
                            val enabled = minDate == null || !date.isBefore(minDate)
                            val description = date.format(fullFormat) +
                                if (date == today) ", $todayWord" else ""
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .alpha(if (enabled) 1f else 0.3f)
                                    .clip(CircleShape)
                                    .background(if (isSel) skin.accent else Color.Transparent)
                                    .then(
                                        if (date == today && !isSel) Modifier.border(1.5.dp, skin.ink, CircleShape)
                                        else Modifier
                                    )
                                    .then(if (enabled) Modifier.tapOrHold { onDay(date) } else Modifier)
                                    .clearAndSetSemantics {
                                        contentDescription = description
                                        role = Role.Button
                                        this.selected = isSel
                                        if (!enabled) disabled()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayNum.toString(),
                                    color = if (isSel) skin.bg else skin.ink,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
