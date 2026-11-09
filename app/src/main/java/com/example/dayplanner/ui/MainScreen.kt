package com.example.dayplanner.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

private val TitleHeight = 32.dp

@Composable
fun MainScreen(vm: PlannerViewModel, onOpenSettings: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val dayState by vm.day.collectAsState()
    val s = settings ?: return
    val d = dayState ?: return

    val skin = LocalSkin.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val lockActive = rememberLockActive(s)
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
                lockActive = lockActive,
                onPrev = { focus.clearFocus(); vm.shiftDay(-1) },
                onNext = { focus.clearFocus(); vm.shiftDay(1) },
                onSettings = onOpenSettings,
                onToggleLock = { vm.toggleLock() },
                onShare = {
                    focus.clearFocus()
                    vm.prepareShare { uri -> if (uri != null) shareImage(context, uri) }
                },
                onDate = { showCalendar = true },
            )
            Content(s, d, vm, gap, lockActive, Modifier.weight(1f).fillMaxWidth())
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

/** Öffnet das typische Android-Teilen-Fenster für das PNG. */
private fun shareImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Aktuelle Zeit, minütlich aktualisiert (nur wenn Ausgrauen/Hervorheben aktiv ist). */
@Composable
private fun rememberMinuteClock(enabled: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        while (enabled) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)
        }
    }
    return now
}

/** -1 = vergangen, 0 = aktuelle Stunde, 1 = kommend. */
private fun rowState(date: LocalDate, dayStart: Int, i: Int, nowMillis: Long): Int {
    val zone = ZoneId.systemDefault()
    val base = date.atTime(dayStart, 0)
    val start = base.plusHours(i.toLong()).atZone(zone).toInstant().toEpochMilli()
    val end = base.plusHours(i + 1L).atZone(zone).toInstant().toEpochMilli()
    return when {
        nowMillis >= end -> -1
        nowMillis >= start -> 0
        else -> 1
    }
}

// ---------------------------------------------------------------- Inhalt

@Composable
private fun Content(
    s: Settings,
    d: DayState,
    vm: PlannerViewModel,
    gap: Dp,
    lockActive: Boolean,
    modifier: Modifier,
) {
    val skin = LocalSkin.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val now = rememberMinuteClock(s.grayPast || s.highlightNow)
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
                        PrioritiesBox(s.prioritiesFor(d.date), d, s, lockActive, vm)
                        if (s.showBraindumpTitle) SectionTitle(stringResource(R.string.braindump))
                        BlockField(
                            text = d.data.braindump,
                            strokes = d.data.strokes,
                            locked = lockActive,
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
                        Timebox(s, d, vm, rowMin, lockActive, now)
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
    lockActive: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
    onToggleLock: () -> Unit,
    onShare: () -> Unit,
    onDate: () -> Unit,
) {
    val skin = LocalSkin.current
    val ink = skin.ink
    val chip = skin.chip

    // Datum IMMER mittig: links und rechts wird gleich viel Platz reserviert.
    val slot = 44
    val sideLeft = (if (s.showLeftArrow) slot else 0) + slot
    val sideRight = (if (s.showLock) slot else 0) + (if (s.showShare) slot else 0) + slot
    val side = maxOf(sideLeft, sideRight).dp

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

        AutoSizeText(
            text = dateText,
            color = if (chip != null) skin.chipInk else ink,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = side)
                .fillMaxSize()
                .then(if (chip != null) Modifier.padding(vertical = 3.dp).background(chip) else Modifier)
                .semantics { contentDescription = dateText; role = Role.Button }
                .tapOrHold(onDate),
        )

        Row(Modifier.align(Alignment.CenterEnd)) {
            if (s.showLock) {
                IconSlot(stringResource(R.string.cd_lock), onToggleLock) {
                    LockIcon(locked = lockActive, color = ink, modifier = Modifier.size(30.dp))
                }
            }
            if (s.showShare) {
                IconSlot(stringResource(R.string.cd_share), onShare) {
                    ShareIcon(color = ink, modifier = Modifier.size(26.dp))
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
private fun PrioritiesBox(
    count: Int,
    day: DayState,
    s: Settings,
    lockActive: Boolean,
    vm: PlannerViewModel,
) {
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
                locked = lockActive,
                wrap = s.wrapText,
                ink = skin.fieldInk,
                onCommit = { vm.setPriority(day.date, i, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                struck = i in day.data.strikeP,
                onDoubleTap = if (s.strikeDouble) ({ vm.toggleStrikePriority(day.date, i) }) else null,
            )
        }
    }
}

// ---------------------------------------------------------------- Timebox

@Composable
private fun Timebox(
    s: Settings,
    day: DayState,
    vm: PlannerViewModel,
    rowMin: Dp,
    lockActive: Boolean,
    now: Long,
) {
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

            val state = rowState(day.date, s.dayStart, i, now)
            val gray = s.grayPast && state < 0
            val highlight = s.highlightNow && state == 0

            // Nur Zeilen mit mehrzeiligem Text werden höher, alle anderen behalten rowMin.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = rowMin)
                    .height(IntrinsicSize.Min)
                    .then(if (gray) Modifier.alpha(0.4f) else Modifier)
                    .then(
                        if (highlight) {
                            Modifier.drawWithContent {
                                drawContent()
                                drawRect(skin.accent.copy(alpha = 0.22f))
                            }
                        } else {
                            Modifier
                        }
                    )
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
                        locked = lockActive,
                        wrap = s.wrapText,
                        ink = skin.timeInk,
                        onCommit = { vm.setTimebox(day.date, cellKey, it) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(skin.timeFill),
                        struck = cellKey in day.data.strikeT,
                        onDoubleTap = if (s.strikeDouble) ({ vm.toggleStrikeTimebox(day.date, cellKey) }) else null,
                    )
                }
            }
        }
    }
}
