package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import com.example.dayplanner.data.DayState
import com.example.dayplanner.data.Settings

@Composable
fun MainScreen(vm: PlannerViewModel, onOpenSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val dayState by vm.day.collectAsState()
    val s = settings ?: return
    val d = dayState ?: return

    val focus = LocalFocusManager.current
    var showCalendar by remember { mutableStateOf(false) }
    val gap = 8.dp // äußerer Rahmen = Abstand zwischen den Elementen

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { focus.clearFocus() },
                    onLongPress = { focus.clearFocus() },
                )
            }
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Header(
            dateText = formatDate(d.date, s.dateFormat),
            s = s,
            onPrev = { focus.clearFocus(); vm.shiftDay(-1) },
            onNext = { focus.clearFocus(); vm.shiftDay(1) },
            onSettings = onOpenSettings,
            onToggleLock = { vm.toggleLock() },
            onDate = { showCalendar = true },
        )

        key(d.date) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // Linke Spalte: Prioritäten + Braindump
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    if (s.showPrioritiesTitle) SectionTitle(stringResource(R.string.priorities))
                    PrioritiesBox(s.prioritiesFor(d.date), d, s, vm)
                    if (s.showBraindumpTitle) SectionTitle(stringResource(R.string.braindump))
                    BlockField(
                        text = d.data.braindump,
                        locked = s.locked,
                        onCommit = { vm.setBraindump(d.date, it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.onBackground),
                    )
                }

                // Rechte Spalte: Timebox
                Column(
                    Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    if (s.showTimeboxTitle) SectionTitle(stringResource(R.string.timebox))
                    Timebox(s, d, vm, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    if (showCalendar) {
        CalendarDialog(
            initial = d.date,
            onPick = { focus.clearFocus(); vm.selectDate(it) },
            onDismiss = { showCalendar = false },
        )
    }
}

// ---------------------------------------------------------------- Kopfzeile

@Composable
private fun Header(
    dateText: String,
    s: Settings,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
    onToggleLock: () -> Unit,
    onDate: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Row(Modifier.align(Alignment.CenterStart)) {
            if (s.showLeftArrow) {
                IconSlot(stringResource(R.string.cd_prev), onPrev) {
                    Chevron(left = true, color = ink, modifier = Modifier.size(22.dp))
                }
            }
            IconSlot(stringResource(R.string.cd_settings), onSettings) {
                GearIcon(color = ink, modifier = Modifier.size(30.dp))
            }
        }

        // Datum: immer exakt mittig (links und rechts gleich viel Platz reserviert)
        AutoSizeText(
            text = dateText,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 92.dp)
                .fillMaxHeight()
                .semantics { contentDescription = dateText; role = Role.Button }
                .tapOrHold(onDate),
        )

        Row(Modifier.align(Alignment.CenterEnd)) {
            IconSlot(stringResource(R.string.cd_lock), onToggleLock) {
                LockIcon(locked = s.locked, color = ink, modifier = Modifier.size(30.dp))
            }
            IconSlot(stringResource(R.string.cd_next), onNext) {
                Chevron(left = false, color = ink, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun IconSlot(description: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = description; role = Role.Button }
            .tapOrHold(onAction),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun AutoSizeText(text: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.copy(fontWeight = FontWeight.ExtraBold)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth
        val size = remember(text, maxW) {
            var sp = 34f
            while (sp > 12f &&
                measurer.measure(text, base.copy(fontSize = sp.sp), softWrap = false, maxLines = 1)
                    .size.width > maxW
            ) sp -= 1f
            sp
        }
        Text(text, style = base.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------- Prioritäten

@Composable
private fun PrioritiesBox(count: Int, day: DayState, s: Settings, vm: PlannerViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onBackground)
    ) {
        repeat(count) { i ->
            if (i > 0) Hairline()
            EditableCell(
                text = day.data.priorities[i].orEmpty(),
                locked = s.locked,
                wrap = s.wrapText,
                onCommit = { vm.setPriority(day.date, i, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Timebox

@Composable
private fun Timebox(s: Settings, day: DayState, vm: PlannerViewModel, modifier: Modifier) {
    val wrap = s.wrapText
    BoxWithConstraints(modifier.border(1.dp, MaterialTheme.colorScheme.onBackground)) {
        val rowHeight = maxHeight / s.rows
        val minRow = if (wrap) rowHeight else 0.dp
        val hourFont = (rowHeight.value * 0.55f).coerceIn(9f, 17f).sp

        Column(
            if (wrap) Modifier.verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
        ) {
            for (i in 0 until s.rows) {
                val hour = (s.dayStart + i) % 24
                if (i > 0) Hairline()
                Row(
                    if (wrap) Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    else Modifier.fillMaxWidth().weight(1f)
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .heightIn(min = minRow)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = hour.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = hourFont,
                            maxLines = 1,
                        )
                    }
                    for (c in 0 until s.columns) {
                        VLine()
                        val cellKey = hour * 2 + c
                        EditableCell(
                            text = day.data.timebox[cellKey].orEmpty(),
                            locked = s.locked,
                            wrap = wrap,
                            onCommit = { vm.setTimebox(day.date, cellKey, it) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = minRow)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
