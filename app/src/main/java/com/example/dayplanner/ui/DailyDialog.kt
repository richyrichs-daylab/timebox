package com.example.dayplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R

/** Das Braindump-Feld als Pop-up: Der Text kommt in alle zukünftigen Tage (ab morgen). */
@Composable
fun DailyDialog(initial: String, onCancel: () -> Unit, onConfirm: (String) -> Unit) {
    val skin = LocalSkin.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    val title = stringResource(R.string.set_daily)
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
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .skinFrame(skin, skin.brainFill, skin.frameBrain)
                .padding(8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = TextStyle(color = skin.fieldInk, fontSize = 15.sp, fontFamily = AppFont),
                cursorBrush = SolidColor(skin.fieldInk),
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = title }
                    .focusRequester(focus),
            )
        }
        Spacer(Modifier.height(14.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = stringResource(R.string.dlg_confirm),
            confirmEnabled = true,
            onCancel = onCancel,
            onConfirm = { onConfirm(value.text) },
        )
    }
}
