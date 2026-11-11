package com.example.dayplanner.widget

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.ui.AppFont
import com.example.dayplanner.ui.DialogButtons
import com.example.dayplanner.ui.DialogFrame
import com.example.dayplanner.ui.LocalSkin
import com.example.dayplanner.ui.SkinTheme
import com.example.dayplanner.ui.resolveSkinFor
import com.example.dayplanner.ui.skinFrame
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Kleines Eingabefenster über dem Startbildschirm: Tippen auf ein Feld im Widget öffnet es.
 * Im Lock-in-Modus lässt sich der Text nur ansehen.
 */
class WidgetEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getIntExtra(EXTRA_TYPE, -1)
        val key = intent.getIntExtra(EXTRA_KEY, 0)
        val date = runCatching { LocalDate.parse(intent.getStringExtra(EXTRA_DATE)) }.getOrNull()
        if (date == null || type < 0) {
            finish()
            return
        }
        setContent { EditRoot(type, key, date) { finish() } }
    }

    companion object {
        const val EXTRA_TYPE = "edit_type"
        const val EXTRA_KEY = "edit_key"
        const val EXTRA_DATE = "edit_date"
    }
}

private class Loaded(val settings: Settings, val day: DayData)

@Composable
private fun EditRoot(type: Int, key: Int, date: LocalDate, onClose: () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Loaded?>(null) }

    LaunchedEffect(Unit) {
        val app = context.applicationContext
        state = Loaded(SettingsRepository(app).current(), DayRepository(app).load(date))
    }

    val loaded = state ?: return
    val s = loaded.settings
    val skin = remember(s.customDesign, s.design, s.themeMode, dark) { resolveSkinFor(context, s, dark) }

    SkinTheme(skin) {
        val initial = when (type) {
            HitType.PRIO -> loaded.day.priorities[key].orEmpty()
            HitType.TIME -> loaded.day.timebox[key].orEmpty()
            else -> loaded.day.braindump
        }
        val title = when (type) {
            HitType.PRIO -> stringResource(R.string.priorities) + " " + (key + 1)
            HitType.TIME -> (key / 2).toString() + ":" + (if (key % 2 == 1) "30" else "00")
            else -> stringResource(R.string.braindump)
        }
        EditDialog(
            title = title,
            initial = initial,
            singleLine = type != HitType.BRAIN,
            readOnly = s.lockActive,
            onCancel = onClose,
            onConfirm = { text ->
                scope.launch {
                    saveEdit(context, type, key, date, text)
                    onClose()
                }
            },
        )
    }
}

private suspend fun saveEdit(context: Context, type: Int, key: Int, date: LocalDate, text: String) {
    val app = context.applicationContext
    val repo = DayRepository(app)
    val d = repo.load(date)
    val updated = when (type) {
        HitType.PRIO -> d.copy(
            priorities = if (text.isEmpty()) d.priorities - key else d.priorities + (key to text)
        )
        HitType.TIME -> d.copy(
            timebox = if (text.isEmpty()) d.timebox - key else d.timebox + (key to text)
        )
        else -> d.copy(braindump = text)
    }
    repo.save(date, updated)
    WidgetUpdater.refreshAll(app)
}

@Composable
private fun EditDialog(
    title: String,
    initial: String,
    singleLine: Boolean,
    readOnly: Boolean,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val skin = LocalSkin.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    DialogFrame(onCancel) {
        Text(
            title,
            color = skin.ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (singleLine) 56.dp else 200.dp)
                .skinFrame(
                    skin,
                    if (singleLine) skin.prioFill else skin.brainFill,
                    if (singleLine) skin.frame else skin.frameBrain,
                )
                .padding(8.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (!readOnly) value = it },
                readOnly = readOnly,
                singleLine = singleLine,
                textStyle = TextStyle(color = skin.fieldInk, fontSize = 16.sp, fontFamily = AppFont),
                cursorBrush = SolidColor(skin.fieldInk),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (readOnly) onCancel() else onConfirm(value.text) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier else Modifier.fillMaxHeight())
                    .focusRequester(focus),
            )
        }
        Spacer(Modifier.height(14.dp))
        DialogButtons(
            cancel = stringResource(R.string.dlg_cancel),
            confirm = stringResource(R.string.dlg_confirm),
            confirmEnabled = !readOnly,
            onCancel = onCancel,
            onConfirm = { onConfirm(value.text) },
        )
    }
}
