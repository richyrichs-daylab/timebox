package com.example.dayplanner.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dayplanner.R
import com.example.dayplanner.data.DayState
import com.example.dayplanner.data.Settings
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val TitleHeight = 32.dp
private val MinRow = 22.dp

@OptIn(ExperimentalLayoutApi::class)
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
    var showEvents by remember { mutableStateOf(false) }
    var activeEdit by remember { mutableStateOf<EditHandle?>(null) }
    val imeVisible = WindowInsets.isImeVisible
    val gap = 8.dp // äußerer Rahmen = Abstand zwischen den Elementen

    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()

    val onActive: (EditHandle, Boolean) -> Unit = { handle, on ->
        if (on) {
            activeEdit = handle
        } else if (activeEdit === handle) {
            activeEdit = null
        }
    }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showEvents = true else toast(R.string.cal_denied)
    }

    fun fromCalendar() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) showEvents = true else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
    }

    /** Ein Termin dauert 1 Stunde (eine Spalte) bzw. 30 Minuten (zwei Spalten, Spalte 2 ab :30). */
    fun toCalendar() {
        val handle = activeEdit ?: return
        if (handle.kind != 1) return
        val text = handle.read().trim()
        if (text.isEmpty()) {
            toast(R.string.cal_empty)
            return
        }
        val dayStart = s.dayStartFor(d.date)
        val hour = handle.key / 2
        val column = if (s.columns == 2 && handle.key % 2 == 1) 1 else 0
        val offsetHours = (hour - dayStart + 24) % 24
        val begin = d.date.atTime(dayStart, 0)
            .plusHours(offsetHours.toLong())
            .plusMinutes(column * 30L)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val end = begin + (if (s.columns == 2) 30 else 60) * 60_000L
        try {
            context.startActivity(CalendarSync.insertIntent(text, begin, end))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.cal_no_app)
        }
    }

    /** Der Titel kommt in die Zelle der Startzeit des Termins. */
    fun insertEvent(ev: CalEvent) {
        val start = Instant.ofEpochMilli(ev.begin).atZone(ZoneId.systemDefault())
        val column = if (s.columns == 2 && start.minute >= 30) 1 else 0
        val key = start.hour * 2 + column
        val handle = activeEdit
        if (handle != null && handle.kind == 1 && handle.key == key) {
            val current = handle.read()
            handle.write(if (current.isBlank()) ev.title else "$current · ${ev.title}")
        } else {
            vm.appendTimebox(d.date, key, ev.title)
        }
    }

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
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                Content(s, d, vm, gap, lockActive, onActive, Modifier.weight(1f).fillMaxWidth())
            }

            // Direkt über der Tastatur
            if (imeVisible && activeEdit != null && !lockActive) {
                CalendarBar(
                    toEnabled = activeEdit?.kind == 1,
                    onFrom = { fromCalendar() },
                    onTo = { toCalendar() },
                )
            }
        }
    }

    if (showCalendar) {
        CalendarDialog(
            initial = d.date,
            onPick = { focus.clearFocus(); vm.selectDate(it) },
            onDismiss = { showCalendar = false },
        )
    }

    if (showEvents) {
        EventPickerDialog(
            date = d.date,
            onDismiss = { showEvents = false },
            onPick = { ev ->
                showEvents = false
                insertEvent(ev)
            },
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
    onActive: (EditHandle, Boolean) -> Unit,
    modifier: Modifier,
) {
    val skin = LocalSkin.current
    val density = LocalDensity.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val now = rememberMinuteClock(s.grayPast || s.highlightNow)
    val scroll = rememberScrollState()
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
        val availPx = constraints.maxHeight
        // Zeilenzahl gilt pro Tag: frühere Tage behalten ihren Wert.
        val rows = s.rowsFor(d.date)
        val titleBlock = if (s.showTimeboxTitle) TitleHeight + gap else 0.dp

        // Die Seite hat immer genau die verfügbare Höhe. Nur wenn der Platz (z. B. bei offener
        // Tastatur) für die Mindesthöhe der Zeilen nicht reicht, darf sie scrollen.
        val requiredDp = titleBlock + skin.frameWidth * 2 + MinRow * rows + skin.lineWidth * (rows - 1)
        val requiredPx = with(density) { requiredDp.roundToPx() }
        val contentPx = maxOf(availPx, requiredPx)
        val scrollable = contentPx > availPx
        val contentDp = with(density) { contentPx.toDp() }

        // Platz für den Schlagschatten (rechts und unten), damit er nicht abgeschnitten wird.
        val shadowPx = with(density) { skin.shadowOffset.roundToPx() }

        val rowEstimate = (contentDp - titleBlock - skin.frameWidth * 2 - skin.lineWidth * (rows - 1)) / rows
        val hourFont = (rowEstimate.value * 0.55f).coerceIn(9f, 17f).sp

        val leftTitles = (if (s.showPrioritiesTitle) TitleHeight else 0.dp) +
            (if (s.showBraindumpTitle) TitleHeight else 0.dp)
        val prioMax = ((contentDp - leftTitles - gap * 3) * 0.55f).coerceAtLeast(44.dp)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
        ) {
            key(d.date) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .layout { measurable, c ->
                            val w = (c.maxWidth - shadowPx).coerceAtLeast(0)
                            val h = (contentPx - shadowPx).coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(c.maxWidth, contentPx) { placeable.placeRelative(0, 0) }
                        },
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // Linke Spalte: Prioritäten + Braindump
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        if (s.showPrioritiesTitle) SectionTitle(stringResource(R.string.priorities), 0)
                        PrioritiesBox(s.prioritiesFor(d.date), d, s, lockActive, vm, prioMax, onActive)
                        if (s.showBraindumpTitle) SectionTitle(stringResource(R.string.braindump), 1)
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
                                .skinFrame(skin, skin.brainFill, skin.frameBrain),
                            onActive = onActive,
                        )
                    }

                    // Rechte Spalte: Timebox
                    Column(
                        Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        if (s.showTimeboxTitle) SectionTitle(stringResource(R.string.timebox), 2)
                        Timebox(
                            s = s,
                            day = d,
                            vm = vm,
                            lockActive = lockActive,
                            now = now,
                            hourFont = hourFont,
                            onActive = onActive,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
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
    val dateLabel = stringResource(R.string.cd_date)
    val lockState = stringResource(if (lockActive) R.string.a11y_active else R.string.a11y_inactive)

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
                .tapOrHold(onDate)
                .clearAndSetSemantics {
                    contentDescription = "$dateLabel: $dateText"
                    role = Role.Button
                },
        )

        Row(Modifier.align(Alignment.CenterEnd)) {
            if (s.showLock) {
                IconSlot(stringResource(R.string.cd_lock), onToggleLock, lockState) {
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
private fun IconSlot(
    description: String,
    onAction: () -> Unit,
    state: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
                if (state != null) stateDescription = state
            }
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

/** Titel über den Feldern (für Screenreader Überschriften). index: 0 Prioritäten, 1 Braindump, 2 Timebox. */
@Composable
private fun SectionTitle(text: String, index: Int) {
    val skin = LocalSkin.current
    val label = if (skin.upperTitles) text.uppercase() else text
    val fill = skin.titleFills.getOrNull(index) ?: skin.accent
    val inkColor = skin.titleInk ?: skin.ink

    when (skin.titleStyle) {
        // Wolken-Etikett: Kapsel mit weichem Schatten
        1 -> Box(
            Modifier
                .fillMaxWidth()
                .height(TitleHeight)
                .semantics(mergeDescendants = true) { heading() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .height(TitleHeight)
                    .shadow(2.dp, RoundedCornerShape(50), clip = false)
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = inkColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            }
        }
        // Bunter Reiter mit Schattenstreifen, reicht bis an den Rahmen darunter
        2 -> {
            val strip = skin.titleShadow ?: Color(0x33000000)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TitleHeight)
                    .semantics(mergeDescendants = true) { heading() }
            ) {
                Box(
                    Modifier
                        .padding(start = 12.dp)
                        .fillMaxWidth(0.72f)
                        .fillMaxHeight()
                        .drawBehind {
                            val reach = 8.dp.toPx()
                            val stripWidth = 8.dp.toPx()
                            val stripTop = 6.dp.toPx()
                            drawRect(
                                strip,
                                Offset(size.width, stripTop),
                                Size(stripWidth, size.height + reach - stripTop),
                            )
                            drawRect(fill, Offset.Zero, Size(size.width, size.height + reach))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = inkColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
                }
            }
        }
        else -> Box(
            Modifier
                .fillMaxWidth()
                .height(TitleHeight)
                .semantics(mergeDescendants = true) { heading() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = skin.ink,
                fontWeight = FontWeight.Bold,
                fontSize = if (skin.upperTitles) 18.sp else 22.sp,
                letterSpacing = if (skin.upperTitles) 0.5.sp else 0.sp,
                maxLines = 1,
            )
        }
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
    maxHeight: Dp,
    onActive: (EditHandle, Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .skinFrame(skin, skin.prioFill)
    ) {
        StableRows(
            rowCount = count,
            lineColor = { skin.line },
            lineWidth = skin.lineWidth,
            minRow = 44.dp,
            useNeeds = s.wrapText,
            fillHeight = false,
            modifier = Modifier.fillMaxWidth(),
        ) { i ->
            EditableCell(
                text = day.data.priorities[i].orEmpty(),
                locked = lockActive,
                wrap = s.wrapText,
                ink = skin.fieldInk,
                onCommit = { vm.setPriority(day.date, i, it) },
                modifier = Modifier.fillMaxSize(),
                struck = i in day.data.strikeP,
                onDoubleTap = if (s.strikeDouble) ({ vm.toggleStrikePriority(day.date, i) }) else null,
                handleKind = 0,
                handleKey = i,
                onActive = onActive,
                label = stringResource(R.string.a11y_priority, i + 1),
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
    lockActive: Boolean,
    now: Long,
    hourFont: TextUnit,
    onActive: (EditHandle, Boolean) -> Unit,
    modifier: Modifier,
) {
    val skin = LocalSkin.current
    val rows = s.rowsFor(day.date)
    Box(modifier.skinFrame(skin, Color.Transparent)) {
        StableRows(
            rowCount = rows,
            lineColor = { i -> skin.lineAt(i.toFloat() / rows) },
            lineWidth = skin.lineWidth,
            minRow = 0.dp,
            useNeeds = s.wrapText,
            fillHeight = true,
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            TimeRow(i, s, day, vm, lockActive, now, hourFont, onActive)
        }
    }
}

@Composable
private fun TimeRow(
    i: Int,
    s: Settings,
    day: DayState,
    vm: PlannerViewModel,
    lockActive: Boolean,
    now: Long,
    hourFont: TextUnit,
    onActive: (EditHandle, Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    // Tagesbeginn und Zeilenzahl gelten pro Tag: frühere Tage behalten ihre Werte.
    val dayStart = s.dayStartFor(day.date)
    val rows = s.rowsFor(day.date)
    val hour = (dayStart + i) % 24
    val state = rowState(day.date, dayStart, i, now)
    val gray = s.grayPast && state < 0
    val highlight = s.highlightNow && state == 0
    val nowWord = stringResource(R.string.a11y_now)
    val pastWord = stringResource(R.string.a11y_past)
    val statusWord = when {
        highlight -> nowWord
        gray -> pastWord
        else -> ""
    }

    // Hat das Design eine eigene Hervorhebung (z. B. pinke Stundenzelle), färbt sie nur die Stundenzelle.
    val hl = skin.hourHighlight
    val hourBg = if (highlight && hl != null) hl else skin.hourFill
    val tintRow = highlight && hl == null

    Row(
        Modifier
            .fillMaxSize()
            .then(if (gray) Modifier.alpha(0.4f) else Modifier)
            .then(
                if (tintRow) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(skin.accent.copy(alpha = 0.22f))
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Die Stundenzahl ist für Screenreader überflüssig: Jede Zelle nennt ihre Uhrzeit selbst.
        Box(
            Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(hourBg)
                .clearAndSetSemantics { },
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
            VLine(skin.lineAt(i.toFloat() / rows), skin.lineWidth)
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
                handleKind = 1,
                handleKey = cellKey,
                onActive = onActive,
                label = stringResource(R.string.a11y_timebox_cell, "%d:%02d".format(hour, c * 30)),
                status = statusWord,
            )
        }
    }
}
