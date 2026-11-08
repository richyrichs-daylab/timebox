package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: PlannerViewModel, onBack: () -> Unit) {
    val current by vm.settings.collectAsState()
    val s = current ?: return
    val focus = LocalFocusManager.current
    val ink = MaterialTheme.colorScheme.onBackground
    val preview = formatDate(LocalDate.now(), s.dateFormat)

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .tapOrHold(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Chevron(left = true, color = ink, modifier = Modifier.size(22.dp))
            }
            Text(
                text = stringResource(R.string.settings),
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // ---------- linke Spalte ----------
            Column(
                Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingRow(stringResource(R.string.set_left_arrow)) {
                    Switch(checked = s.showLeftArrow, onCheckedChange = { v -> vm.updateSettings { it.copy(showLeftArrow = v) } })
                }
                SettingRow(stringResource(R.string.set_date_format, preview)) {
                    SquareButton(s.dateFormat.toString()) {
                        vm.updateSettings { it.copy(dateFormat = it.dateFormat % 4 + 1) }
                    }
                }
                SettingRow(stringResource(R.string.set_rows)) {
                    NumberBox(s.rows, 1..24) { v -> vm.updateSettings { it.copy(rows = v) } }
                }
                SettingRow(stringResource(R.string.set_columns)) {
                    NumberBox(s.columns, 1..2) { v -> vm.updateSettings { it.copy(columns = v) } }
                }
                SettingRow(stringResource(R.string.set_day_start)) {
                    NumberBox(s.dayStart, 0..23) { v -> vm.updateSettings { it.copy(dayStart = v) } }
                }
            }

            // ---------- dünner schwarzer Strich ----------
            Box(Modifier.width(1.dp).fillMaxHeight().background(ink))

            // ---------- rechte Spalte ----------
            Column(
                Modifier.weight(1f).padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingRow(stringResource(R.string.set_priorities_count)) {
                    NumberBox(s.priorities, 1..10) { v -> vm.setPriorityCount(v) }
                }
                SettingRow(stringResource(R.string.set_title_priorities)) {
                    Switch(checked = s.showPrioritiesTitle, onCheckedChange = { v -> vm.updateSettings { it.copy(showPrioritiesTitle = v) } })
                }
                SettingRow(stringResource(R.string.set_title_braindump)) {
                    Switch(checked = s.showBraindumpTitle, onCheckedChange = { v -> vm.updateSettings { it.copy(showBraindumpTitle = v) } })
                }
                SettingRow(stringResource(R.string.set_title_timebox)) {
                    Switch(checked = s.showTimeboxTitle, onCheckedChange = { v -> vm.updateSettings { it.copy(showTimeboxTitle = v) } })
                }
                SettingRow(stringResource(R.string.set_wrap)) {
                    Switch(checked = s.wrapText, onCheckedChange = { v -> vm.updateSettings { it.copy(wrapText = v) } })
                }
            }
        }
    }
}

/** Kasten/Toggle links, Beschriftung rechts dahinter. */
@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { control() }
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
    }
}

@Composable
private fun SquareButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.onBackground)
            .tapOrHold(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun NumberBox(value: Int, range: IntRange, onValue: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    val ink = MaterialTheme.colorScheme.onBackground
    var text by remember(value) { mutableStateOf(value.toString()) }

    BasicTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(2)
            text = digits
            digits.toIntOrNull()?.let { if (it in range) onValue(it) }
        },
        singleLine = true,
        textStyle = TextStyle(
            color = ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .size(52.dp)
            .border(1.5.dp, ink)
            .onFocusChanged { if (!it.isFocused) text = value.toString() },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        },
    )
}
