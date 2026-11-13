package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.dayplanner.R
import com.example.dayplanner.data.PasswordInfo

private val KeySize = 76.dp
private const val MAX_DIGITS = 16

/** Einfarbiger Hintergrund im gewählten Design, darauf Ziffernfeld oder Passwortzeile. */
@Composable
fun LockScreen(info: PasswordInfo, vm: PlannerViewModel) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxSize()
            .background(skin.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (info.type == PasswordInfo.NUMERIC) NumericLock(vm) else TextLock(vm)
    }
}

@Composable
private fun NumericLock(vm: PlannerViewModel) {
    val skin = LocalSkin.current
    var entered by remember { mutableStateOf("") }

    fun press(digit: String) {
        if (entered.length >= MAX_DIGITS) return
        val next = entered + digit
        entered = next
        // Kein Enter nötig: bei jeder Eingabe wird geprüft.
        vm.tryUnlock(next) { if (next.length >= MAX_DIGITS && entered == next) entered = "" }
    }

    val undoText = stringResource(R.string.cd_undo)
    val digitsLabel = stringResource(R.string.a11y_digits_entered, entered.length)
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<"),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .height(48.dp)
                .clearAndSetSemantics { contentDescription = digitsLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text("•".repeat(entered.length), color = skin.ink, fontSize = 30.sp, maxLines = 1)
        }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { k ->
                    when (k) {
                        "" -> Spacer(Modifier.size(KeySize))
                        "<" -> PadKey("⌫", undoText) {
                            if (entered.isNotEmpty()) entered = entered.dropLast(1)
                        }
                        else -> PadKey(k, k) { press(k) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(label: String, description: String, onClick: () -> Unit) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .size(KeySize)
            .tapOrHold(onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .skinFrame(skin, skin.hourFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = skin.hourInk, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TextLock(vm: PlannerViewModel) {
    val skin = LocalSkin.current
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val passwordLabel = stringResource(R.string.a11y_password)
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        vm.tryUnlock(text) {
            wrong = true
            text = ""
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .skinFrame(skin, skin.prioFill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it; wrong = false },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(color = skin.fieldInk, fontSize = 20.sp, fontFamily = AppFont),
                cursorBrush = SolidColor(skin.fieldInk),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = passwordLabel }
                    .focusRequester(focus),
            )
        }
        // Ein falsches Passwort wird vom Screenreader automatisch angesagt.
        Box(
            Modifier
                .height(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            if (wrong) Text(stringResource(R.string.pw_wrong), color = skin.ink, fontSize = 14.sp)
        }
    }
}

/** Einfaches Eingabefeld mit „Abbruch" und „Bestätigung". */
@Composable
fun PasswordDialog(
    numeric: Boolean,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val skin = LocalSkin.current
    var text by remember { mutableStateOf("") }
    val valid = text.length >= 4
    val focus = remember { FocusRequester() }
    val passwordLabel = stringResource(R.string.a11y_password)
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(skin.bg)
                .skinFrame(skin, Color.Transparent)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .skinFrame(skin, skin.prioFill)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = if (numeric) it.filter(Char::isDigit).take(MAX_DIGITS) else it.take(64)
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(color = skin.fieldInk, fontSize = 20.sp, fontFamily = AppFont),
                    cursorBrush = SolidColor(skin.fieldInk),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = passwordLabel }
                        .focusRequester(focus),
                )
            }
            Text(stringResource(R.string.pw_hint_min), color = skin.ink, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WideButton(stringResource(R.string.pw_cancel), Modifier.weight(1f)) { onCancel() }
                WideButton(stringResource(R.string.pw_confirm), Modifier.weight(1f), enabled = valid) {
                    onConfirm(text)
                }
            }
        }
    }
}
