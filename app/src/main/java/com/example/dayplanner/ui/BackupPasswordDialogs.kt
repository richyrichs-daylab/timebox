package com.example.dayplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R

/** Eingabefeld im Stil des Passwort-Fensters, Text verdeckt. */
@Composable
private fun SecretField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    focus: FocusRequester,
) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .skinFrame(skin, skin.prioFill)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(64)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = skin.fieldInk, fontSize = 20.sp, fontFamily = AppFont),
            cursorBrush = SolidColor(skin.fieldInk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
                .focusRequester(focus),
        )
    }
}

/** Backup-Passwort setzen. Ist schon eines aktiv, steht "Deaktivieren" in der Mitte. */
@Composable
fun BackupPasswordDialog(
    active: Boolean,
    onCancel: () -> Unit,
    onDisable: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val skin = LocalSkin.current
    var text by remember { mutableStateOf("") }
    val valid = text.length >= 4
    val focus = remember { FocusRequester() }
    val title = stringResource(R.string.backup_pw)
    LaunchedEffect(Unit) { focus.requestFocus() }

    DialogFrame(onCancel) {
        Text(
            title,
            color = skin.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        SecretField(text, title, { text = it }, { if (valid) onConfirm(text) }, focus)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pw_hint_min), color = skin.ink, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = stringResource(R.string.dlg_confirm),
            confirmEnabled = valid,
            onCancel = onCancel,
            onConfirm = { onConfirm(text) },
            middle = if (active) stringResource(R.string.dlg_disable) else null,
            onMiddle = onDisable,
        )
    }
}

/** Passwortabfrage beim Import eines verschlüsselten Backups. */
@Composable
fun BackupImportPasswordDialog(onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    val skin = LocalSkin.current
    var text by remember { mutableStateOf("") }
    val valid = text.isNotEmpty()
    val focus = remember { FocusRequester() }
    val title = stringResource(R.string.backup_pw_enter)
    LaunchedEffect(Unit) { focus.requestFocus() }

    DialogFrame(onCancel) {
        Text(
            title,
            color = skin.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        SecretField(text, title, { text = it }, { if (valid) onConfirm(text) }, focus)
        Spacer(Modifier.height(14.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = stringResource(R.string.dlg_confirm),
            confirmEnabled = valid,
            onCancel = onCancel,
            onConfirm = { onConfirm(text) },
        )
    }
}
