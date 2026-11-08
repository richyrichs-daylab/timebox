package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Klick ODER Gedrückthalten löst dieselbe Aktion aus. */
fun Modifier.tapOrHold(onAction: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onAction)
    pointerInput(Unit) {
        detectTapGestures(onTap = { current() }, onLongPress = { current() })
    }
}

@Composable
fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onBackground)
    )
}

@Composable
fun VLine() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.onBackground)
    )
}

/** Blendet das rechte Ende neblig aus. */
private fun Modifier.fadeEnd(fade: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = fade.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = size.width - f,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun EditableCell(
    text: String,
    locked: Boolean,
    wrap: Boolean,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground,
    ),
) {
    var editing by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    Box(
        modifier
            .then(if (editing) Modifier else Modifier.tapOrHold { editing = true })
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (editing) {
            FieldEditor(
                initial = text,
                readOnly = locked,
                singleLine = !wrap,
                enterSaves = true,
                style = textStyle,
                modifier = Modifier.fillMaxWidth(),
                onDone = { value ->
                    editing = false
                    if (!locked && value != text) onCommit(value)
                },
            )
        } else {
            Text(
                text = text,
                style = textStyle,
                maxLines = if (wrap) Int.MAX_VALUE else 1,
                softWrap = wrap,
                overflow = TextOverflow.Clip,
                onTextLayout = { overflow = it.hasVisualOverflow },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!wrap && overflow) Modifier.fadeEnd() else Modifier),
            )
        }
    }
}

@Composable
fun BlockField(
    text: String,
    locked: Boolean,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onBackground,
    ),
) {
    var editing by remember { mutableStateOf(false) }

    Box(
        modifier
            .then(if (editing) Modifier else Modifier.tapOrHold { editing = true })
            .padding(8.dp)
    ) {
        if (editing) {
            FieldEditor(
                initial = text,
                readOnly = locked,
                singleLine = false,
                enterSaves = false,
                style = textStyle,
                modifier = Modifier.fillMaxSize(),
                onDone = { value ->
                    editing = false
                    if (!locked && value != text) onCommit(value)
                },
            )
        } else {
            Text(
                text = text,
                style = textStyle,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Textfeld, das beim Verlust des Fokus (Tippen auf freie Fläche, Enter, Tagwechsel,
 * Verlassen des Bildschirms) genau einmal speichert.
 */
@Composable
private fun FieldEditor(
    initial: String,
    readOnly: Boolean,
    singleLine: Boolean,
    enterSaves: Boolean,
    style: TextStyle,
    modifier: Modifier,
    onDone: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val latest by rememberUpdatedState(value)
    val onDoneLatest by rememberUpdatedState(onDone)
    var finished by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }

    fun finish() {
        if (!finished) {
            finished = true
            onDoneLatest(latest.text)
        }
    }

    DisposableEffect(Unit) { onDispose { finish() } }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = { value = if (readOnly) value.copy(selection = it.selection) else it },
        readOnly = readOnly,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (enterSaves) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .onPreviewKeyEvent { e ->
                if (enterSaves && e.type == KeyEventType.KeyDown &&
                    (e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    focusManager.clearFocus()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) hadFocus = true else if (hadFocus) finish()
            },
    )
}
