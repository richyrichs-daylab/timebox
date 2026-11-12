package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dayplanner.R

/** Gemeinsamer Rahmen aller Pop-ups im Design der App. */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(16.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.92f)
                .clip(shape)
                .background(skin.bg)
                .border(skin.frameWidth, skin.frame, shape)
                .padding(16.dp),
            content = content,
        )
    }
}

/** Links „Abbruch", rechts die Bestätigung, optional in der Mitte eine dritte Aktion. */
@Composable
fun DialogButtons(
    cancel: String,
    confirm: String,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    middle: String? = null,
    onMiddle: () -> Unit = {},
) {
    val fs = if (middle != null) 13.sp else 15.sp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WideButton(cancel, Modifier.weight(1f), fontSize = fs) { onCancel() }
        if (middle != null) WideButton(middle, Modifier.weight(1f), fontSize = fs) { onMiddle() }
        WideButton(confirm, Modifier.weight(1f), enabled = confirmEnabled, fontSize = fs) { onConfirm() }
    }
}

// ---------------------------------------------------------------- Zeitschloss

@Composable
fun TimeLockDialog(onCancel: () -> Unit, onStart: (Long) -> Unit) {
    val skin = LocalSkin.current
    var hours by remember { mutableIntStateOf(1) }
    var minutes by remember { mutableIntStateOf(0) }

    DialogFrame(onCancel) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.lock_hours),
                    color = skin.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                NumberList(0..24, hours) { v ->
                    hours = v
                    if (v == 24) minutes = 0
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.lock_minutes),
                    color = skin.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                NumberList(0..59, minutes) { v -> minutes = if (hours == 24) 0 else v }
            }
        }
        Spacer(Modifier.height(16.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = stringResource(R.string.dlg_start),
            confirmEnabled = hours * 60 + minutes > 0,
            onCancel = onCancel,
            onConfirm = { onStart((hours * 60L + minutes) * 60_000L) },
        )
    }
}

@Composable
private fun NumberList(range: IntRange, value: Int, onSelect: (Int) -> Unit) {
    val skin = LocalSkin.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (value - range.first - 2).coerceAtLeast(0))
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(1.5.dp, skin.ink),
    ) {
        items(range.last - range.first + 1) { idx ->
            val n = range.first + idx
            val selected = n == value
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(if (selected) skin.accent else Color.Transparent)
                    .tapOrHold { onSelect(n) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    n.toString(),
                    color = if (selected) skin.bg else skin.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Backup automatisieren

/**
 * Intervall und Aufbewahrung der automatischen Backups.
 * keep in onConfirm: 0 = alle Backups behalten, sonst die Anzahl.
 */
@Composable
fun AutoBackupDialog(
    active: Boolean,
    initialN: Int,
    initialUnit: Int,
    initialKeep: Int,
    onCancel: () -> Unit,
    onDisable: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
) {
    val skin = LocalSkin.current
    var text by remember { mutableStateOf(initialN.toString()) }
    var unit by remember { mutableIntStateOf(initialUnit) }
    var keepAll by remember { mutableStateOf(initialKeep == 0) }
    var keepText by remember { mutableStateOf(if (initialKeep > 0) initialKeep.toString() else "1") }
    var keepFocusWanted by remember { mutableStateOf(false) }
    val keepFocus = remember { FocusRequester() }

    val daily = unit == 0
    val n = text.toIntOrNull() ?: 0
    val keepN = keepText.toIntOrNull() ?: 0
    val labels = listOf(
        stringResource(R.string.unit_daily),
        stringResource(R.string.unit_weekly),
        stringResource(R.string.unit_monthly),
        stringResource(R.string.unit_yearly),
    )

    // Erst nach einem Tipp auf das Zahlenfeld wird es zum Textfeld mit Fokus.
    LaunchedEffect(keepAll, keepFocusWanted) {
        if (!keepAll && keepFocusWanted) keepFocus.requestFocus()
    }

    DialogFrame(onCancel) {
        Text(
            stringResource(R.string.backup_auto),
            color = skin.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Links: nur Ziffern, nie nur Null
            BasicTextField(
                value = text,
                onValueChange = { input ->
                    val digits = input.filter { it.isDigit() }.take(3)
                    if (digits.isEmpty() || digits.toInt() > 0) text = digits
                },
                enabled = !daily,
                singleLine = true,
                textStyle = TextStyle(
                    color = skin.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(skin.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .size(72.dp)
                    .alpha(if (daily) 0.35f else 1f)
                    .border(1.5.dp, skin.ink),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
                },
            )
            // Rechts: Intervall
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .border(if (unit == i) 3.dp else 1.dp, skin.ink)
                            .tapOrHold { unit = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = skin.ink,
                            fontWeight = if (unit == i) FontWeight.ExtraBold else FontWeight.Normal,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        // ---------- Wieviele Backups behalten? ----------
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.keep_title),
            color = skin.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // „alle": standardmäßig gewählt
            Box(
                Modifier
                    .size(72.dp)
                    .alpha(if (keepAll) 1f else 0.35f)
                    .border(1.5.dp, skin.ink)
                    .tapOrHold { keepAll = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.keep_all),
                    color = skin.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                )
            }
            // Zahl: wird durch Tippen gewählt und zum Textfeld
            if (keepAll) {
                Box(
                    Modifier
                        .size(72.dp)
                        .alpha(0.35f)
                        .border(1.5.dp, skin.ink)
                        .tapOrHold {
                            keepAll = false
                            keepFocusWanted = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(keepText, color = skin.ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            } else {
                BasicTextField(
                    value = keepText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(3)
                        if (digits.isEmpty() || digits.toInt() > 0) keepText = digits
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = skin.ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(skin.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .size(72.dp)
                        .border(1.5.dp, skin.ink)
                        .focusRequester(keepFocus),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = stringResource(R.string.dlg_confirm),
            confirmEnabled = (daily || n >= 1) && (keepAll || keepN >= 1),
            onCancel = onCancel,
            onConfirm = { onConfirm(if (daily) 1 else n, unit, if (keepAll) 0 else keepN) },
            middle = if (active) stringResource(R.string.dlg_disable) else null,
            onMiddle = onDisable,
        )
    }
}
