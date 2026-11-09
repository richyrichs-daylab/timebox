package com.example.dayplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import com.example.dayplanner.data.InkStroke
import kotlin.math.roundToInt

/** Klick ODER Gedrückthalten löst dieselbe Aktion aus. */
fun Modifier.tapOrHold(onAction: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onAction)
    pointerInput(Unit) {
        detectTapGestures(onTap = { current() }, onLongPress = { current() })
    }
}

/** Meldet für die Höhenberechnung 0 und verhindert, dass langer Text das Layout aufbläht. */
object NoIntrinsicHeight : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = 0
}

@Composable
fun HLine(color: Color, thickness: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

@Composable
fun VLine(color: Color, thickness: Dp) {
    Box(
        Modifier
            .width(thickness)
            .fillMaxHeight()
            .background(color)
    )
}

@Composable
fun WideButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 46.dp)
            .then(if (enabled) Modifier.tapOrHold(onClick) else Modifier)
            .skinFrame(skin, skin.hourFill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = skin.hourInk,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
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

// ---------------------------------------------------------------- Zelle

@Composable
fun EditableCell(
    text: String,
    locked: Boolean,
    wrap: Boolean,
    ink: Color,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalTextStyle.current.copy(fontSize = 15.sp, color = ink)
    var editing by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }

    Box(
        modifier
            .then(if (editing) Modifier else Modifier.tapOrHold { editing = true })
            .padding(horizontal = 6.dp, vertical = if (wrap) 4.dp else 0.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (editing) {
            FieldEditor(
                initial = text,
                readOnly = locked,
                singleLine = !wrap,
                enterSaves = true,
                style = style,
                modifier = Modifier.fillMaxWidth(),
                onDone = { value ->
                    editing = false
                    if (!locked && value != text) onCommit(value)
                },
            )
        } else {
            Text(
                text = text,
                style = style,
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

// ---------------------------------------------------------------- Braindump

@Composable
fun BlockField(
    text: String,
    strokes: List<InkStroke>,
    locked: Boolean,
    drawingEnabled: Boolean,
    ink: Color,
    onCommit: (String) -> Unit,
    onStroke: (InkStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalTextStyle.current.copy(fontSize = 15.sp, color = ink)
    var editing by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var erasing by remember { mutableStateOf(false) }
    val canDraw = drawingEnabled && !locked
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editing, canDraw) {
        if (!editing || !canDraw) {
            drawing = false
            erasing = false
        }
    }

    Box(modifier.clipToBounds()) {
        // Textebene
        Box(
            Modifier
                .fillMaxSize()
                .then(if (editing) Modifier else Modifier.tapOrHold { editing = true })
                .padding(8.dp)
        ) {
            if (editing) {
                FieldEditor(
                    initial = text,
                    readOnly = locked,
                    singleLine = false,
                    enterSaves = false,
                    style = style,
                    modifier = Modifier.fillMaxSize(),
                    onDone = { value ->
                        editing = false
                        if (!locked && value != text) onCommit(value)
                    },
                )
            } else {
                Text(
                    text = text,
                    style = style,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Zeichenebene (zeigt Striche immer, nimmt nur im Zeichenmodus Eingaben an)
        DrawingLayer(
            strokes = strokes,
            ink = ink,
            active = drawing,
            erasing = erasing,
            onStroke = onStroke,
            modifier = Modifier.matchParentSize(),
        )

        // Werkzeuge unten rechts
        if (editing && canDraw) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                if (drawing) {
                    ToolButton(
                        selected = erasing,
                        description = stringResource(R.string.cd_eraser),
                        onClick = { erasing = !erasing },
                    ) { EraserIcon(ink, Modifier.size(26.dp)) }
                }
                ToolButton(
                    selected = drawing && !erasing,
                    description = stringResource(R.string.cd_pen),
                    onClick = {
                        if (!drawing) {
                            drawing = true
                            erasing = false
                            keyboard?.hide()
                        } else if (erasing) {
                            erasing = false
                        } else {
                            drawing = false
                        }
                    },
                ) { PenIcon(ink, Modifier.size(26.dp)) }
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) Color.Gray.copy(alpha = 0.22f) else Color.Transparent)
            .semantics { contentDescription = description; role = Role.Button }
            .tapOrHold(onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun DrawingLayer(
    strokes: List<InkStroke>,
    ink: Color,
    active: Boolean,
    erasing: Boolean,
    onStroke: (InkStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalDensity.current.density
    val live = remember { mutableStateListOf<Float>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val erasingNow by rememberUpdatedState(erasing)
    val onStrokeNow by rememberUpdatedState(onStroke)

    // Sobald der fertige Strich in der Liste angekommen ist, wird der Live-Strich verworfen.
    LaunchedEffect(strokes) { live.clear() }

    Box(modifier) {
        if (active) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Gray.copy(alpha = 0.07f))
            )
        }
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .then(
                    if (active) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                live.clear()
                                val erase = erasingNow
                                live.add(down.position.x / scale)
                                live.add(down.position.y / scale)
                                finger = down.position
                                drag(down.id) { change ->
                                    change.consume()
                                    live.add(change.position.x / scale)
                                    live.add(change.position.y / scale)
                                    finger = change.position
                                }
                                finger = null
                                if (live.isNotEmpty()) {
                                    onStrokeNow(
                                        InkStroke(erase, live.map { (it * 10f).roundToInt() / 10f })
                                    )
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            strokes.forEach { drawInk(it.points, it.erase, ink, scale) }
            if (live.isNotEmpty()) drawInk(live, erasing, ink, scale)
        }

        // Stift bzw. Radierer an der Fingerposition
        val f = finger
        if (active && f != null) {
            val iconPx = with(LocalDensity.current) { 30.dp.toPx() }
            val pos = if (erasing) {
                IntOffset((f.x - iconPx / 2).roundToInt(), (f.y - iconPx / 2).roundToInt())
            } else {
                IntOffset(f.x.roundToInt(), (f.y - iconPx).roundToInt())
            }
            Box(Modifier.offset { pos }.size(30.dp)) {
                if (erasing) EraserIcon(ink, Modifier.fillMaxSize())
                else PenIcon(ink, Modifier.fillMaxSize())
            }
        }
    }
}

private fun DrawScope.drawInk(points: List<Float>, erase: Boolean, ink: Color, d: Float) {
    if (points.size < 2) return
    val w = (if (erase) 22f else 2.5f) * d
    val mode = if (erase) BlendMode.Clear else BlendMode.SrcOver
    if (points.size < 4) {
        drawCircle(ink, w / 2f, Offset(points[0] * d, points[1] * d), blendMode = mode)
        return
    }
    val path = Path()
    path.moveTo(points[0] * d, points[1] * d)
    var i = 2
    while (i + 1 < points.size) {
        path.lineTo(points[i] * d, points[i + 1] * d)
        i += 2
    }
    drawPath(
        path,
        ink,
        style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = mode,
    )
}

// ---------------------------------------------------------------- Texteditor

/**
 * Textfeld, das beim Verlust des Fokus (Tippen auf freie Fläche, Enter, Tagwechsel,
 * Verlassen des Bildschirms) genau einmal speichert. Im Lock-in-Modus ist es schreibgeschützt,
 * lässt sich aber markieren und scrollen.
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
        cursorBrush = SolidColor(style.color),
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
