package com.example.dayplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import com.example.dayplanner.data.DayState
import com.example.dayplanner.data.Settings

private val TitleHeight = 32.dp

@Composable
fun MainScreen(vm: PlannerViewModel, onOpenSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val dayState by vm.day.collectAsState()
    val s = settings ?: return
    val d = dayState ?: return

    val skin = LocalSkin.current
    val focus = LocalFocusManager.current
    var showCalendar by remember { mutableStateOf(false) }
    val gap = 8.dp // äußerer Rahmen = Abstand zwischen den Elementen

    Box(Modifier.fillMaxSize()) {
        SkinBackground(skin, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { focus.clearFocus() },
                        onLongPress = { focus.clearFocus() },
                    )
                }
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
            Content(s, d, vm, gap, Modifier.weight(1f).fillMaxWidth())
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

// ---------------------------------------------------------------- Inhalt

@Composable
private fun Content(s: Settings, d: DayState, vm: PlannerViewModel, gap: Dp, modifier: Modifier) {
    val skin = LocalSkin.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(s.zoom) {
        if (!s.zoom) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    // Zoomen: Pinch mit zwei Fingern, Verschieben mit einem Finger nur im gezoomten Zustand.
    val zoomModifier = if (s.zoom) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val consumed = event.changes.any { it.isConsumed }
                    if (!consumed && (event.changes.size > 1 || scale > 1f)) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan != Offset.Zero) {
                            val newScale = (scale * zoom).coerceIn(1f, 4f)
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                } while (!consumed && event.changes.any { it.pressed })
            }
        }
    } else {
        Modifier
    }

    BoxWithConstraints(modifier.clipToBounds().then(zoomModifier)) {
        val avail = maxHeight
        val rows = s.rows
        val titleBlock = if (s.showTimeboxTitle) TitleHeight + gap else 0.dp
        // Mindesthöhe einer Zeile: ohne Textumbruch füllt die Timebox genau die Höhe.
        val rowMin = ((avail - titleBlock - skin.frameWidth * 2 - skin.lineWidth * (rows - 1)) / rows)
            .coerceAtLeast(22.dp)

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .verticalScroll(rememberScrollState())
        ) {
            key(d.date) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = avail)
                        .height(IntrinsicSize.Min),
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
                            strokes = d.data.strokes,
                            locked = s.lockActive,
                            drawingEnabled = s.drawing,
                            ink = skin.fieldInk,
                            onCommit = { vm.setBraindump(d.date, it) },
                            onStroke = { vm.addStroke(d.date, it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .then(NoIntrinsicHeight)
                                .skinFrame(skin, skin.brainFill, skin.frameBrain),
                        )
                    }

                    // Rechte Spalte: Timebox
                    Column(
                        Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        if (s.showTimeboxTitle) SectionTitle(stringResource(R.string.timebox))
                        Timebox(s, d, vm, rowMin)
                    }
                }
            }
        }
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
    val skin = LocalSkin.current
    val ink = skin.ink
    val chip = skin.chip
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
            color = if (chip != null) skin.chipInk else ink,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 92.dp)
                .fillMaxSize()
                .then(if (chip != null) Modifier.padding(vertical = 3.dp).background(chip) else Modifier)
                .semantics { contentDescription = dateText; role = Role.Button }
                .tapOrHold(onDate),
        )

        Row(Modifier.align(Alignment.CenterEnd)) {
            if (s.showLock) {
                IconSlot(stringResource(R.string.cd_lock), onToggleLock) {
                    LockIcon(locked = s.lockActive, color = ink, modifier = Modifier.size(30.dp))
                }
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
private fun AutoSizeText(text: String, color: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.copy(fontWeight = FontWeight.ExtraBold, color = color)
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
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(TitleHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (skin.upperTitles) text.uppercase() else text,
            color = skin.ink,
            fontWeight = FontWeight.Bold,
            fontSize = if (skin.upperTitles) 18.sp else 22.sp,
            letterSpacing = if (skin.upperTitles) 0.5.sp else 0.sp,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Prioritäten

@Composable
private fun PrioritiesBox(count: Int, day: DayState, s: Settings, vm: PlannerViewModel) {
    val skin = LocalSkin.current
    Column(
        Modifier
            .fillMaxWidth()
            .skinFrame(skin, skin.prioFill)
    ) {
        repeat(count) { i ->
            if (i > 0) HLine(skin.line, skin.lineWidth)
            EditableCell(
                text = day.data.priorities[i].orEmpty(),
                locked = s.lockActive,
                wrap = s.wrapText,
                ink = skin.fieldInk,
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
private fun Timebox(s: Settings, day: DayState, vm: PlannerViewModel, rowMin: Dp) {
    val skin = LocalSkin.current
    val hourFont = (rowMin.value * 0.55f).coerceIn(9f, 17f).sp

    Column(
        Modifier
            .fillMaxWidth()
            .skinFrame(skin, Color.Transparent)
    ) {
        for (i in 0 until s.rows) {
            val hour = (s.dayStart + i) % 24
            if (i > 0) HLine(skin.lineAt(i.toFloat() / s.rows), skin.lineWidth)
            // Nur Zeilen mit mehrzeiligem Text werden höher, alle anderen behalten rowMin.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = rowMin)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .background(skin.hourFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = hour.toString(),
                        color = skin.hourInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = hourFont,
                        maxLines = 1,
                    )
                }
                for (c in 0 until s.columns) {
                    VLine(skin.lineAt(i.toFloat() / s.rows), skin.lineWidth)
                    val cellKey = hour * 2 + c
                    EditableCell(
                        text = day.data.timebox[cellKey].orEmpty(),
                        locked = s.lockActive,
                        wrap = s.wrapText,
                        ink = skin.timeInk,
                        onCommit = { vm.setTimebox(day.date, cellKey, it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(skin.timeFill),
                    )
                }
            }
        }
    }
}
