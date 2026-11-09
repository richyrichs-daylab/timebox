package com.example.dayplanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.dayplanner.R
import com.example.dayplanner.data.Design
import com.example.dayplanner.data.PasswordInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingsScreen(vm: PlannerViewModel, onBack: () -> Unit) {
    val current by vm.settings.collectAsState()
    val pwInfo by vm.password.collectAsState()
    val s = current ?: return
    val skin = LocalSkin.current
    val ink = skin.ink
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val now = rememberNow(s.lockUntil)
    val hardLock = s.lockUntil > now
    val resetEnabled = !hardLock || s.lockReleasable

    var pwDialog by remember { mutableStateOf<Int?>(null) }
    var showPdf by remember { mutableStateOf(false) }
    var showPng by remember { mutableStateOf(false) }
    var showTimeLock by remember { mutableStateOf(false) }
    var showDayLock by remember { mutableStateOf(false) }
    var showAuto by remember { mutableStateOf(false) }

    val preview = formatDate(LocalDate.now(), s.dateFormat)
    val modeName = stringResource(
        when (s.themeMode) {
            1 -> R.string.mode_light
            2 -> R.string.mode_dark
            else -> R.string.mode_system
        }
    )
    val modeEnabled = s.designId.supportsMode()

    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportBackup(uri) { ok -> toast(if (ok) R.string.backup_export_ok else R.string.backup_failed) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importBackup(uri) { ok -> toast(if (ok) R.string.backup_import_ok else R.string.backup_failed) }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp)
                        .semantics { contentDescription = "Back"; role = Role.Button }
                        .tapOrHold(onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Chevron(left = true, color = ink, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = stringResource(R.string.settings),
                    color = ink,
                    modifier = Modifier.align(Alignment.Center),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                )
            }
            Spacer(Modifier.height(16.dp))

            // ---------- Zwei Spalten ----------
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Column(
                    Modifier.weight(1f).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SettingRow(stringResource(R.string.set_left_arrow)) {
                        SkinSwitch(s.showLeftArrow) { v -> vm.updateSettings { it.copy(showLeftArrow = v) } }
                    }
                    SettingRow(stringResource(R.string.set_lock)) {
                        // Abwählen hebt einen aktiven Lock-in-Modus (ohne Zeitsperre) auf.
                        SkinSwitch(s.showLock) { v ->
                            vm.updateSettings { it.copy(showLock = v, locked = if (v) it.locked else false) }
                        }
                    }
                    SettingRow(stringResource(R.string.set_share)) {
                        SkinSwitch(s.showShare) { v -> vm.updateSettings { it.copy(showShare = v) } }
                    }
                    SettingRow(stringResource(R.string.set_date_format, preview)) {
                        SquareButton(s.dateFormat.toString()) {
                            vm.updateSettings { it.copy(dateFormat = it.dateFormat % 4 + 1) }
                        }
                    }
                    SettingRow(stringResource(R.string.set_rows)) {
                        NumberBox(s.rows, 1..24) { v -> vm.updateSettings { it.copy(rows = v) } }
                    }
                    SettingRow(stringResource(R.string.set_columns)) {
                        SquareButton(s.columns.toString()) {
                            vm.updateSettings { it.copy(columns = it.columns % 2 + 1) }
                        }
                    }
                    SettingRow(stringResource(R.string.set_day_start)) {
                        NumberBox(s.dayStart, 0..23) { v -> vm.updateSettings { it.copy(dayStart = v) } }
                    }
                    SettingRow(stringResource(R.string.set_mode, modeName)) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .alpha(if (modeEnabled) 1f else 0.35f)
                                .border(1.5.dp, ink)
                                .then(
                                    if (modeEnabled) {
                                        Modifier.tapOrHold {
                                            vm.updateSettings { it.copy(themeMode = (it.themeMode + 1) % 3) }
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ModeIcon(s.themeMode, ink, Modifier.size(30.dp))
                        }
                    }
                    SettingRow(stringResource(R.string.set_gray_past)) {
                        SkinSwitch(s.grayPast) { v -> vm.updateSettings { it.copy(grayPast = v) } }
                    }
                }

                // ---------- dünner schwarzer Strich ----------
                Box(Modifier.width(1.dp).fillMaxHeight().background(ink))

                Column(
                    Modifier.weight(1f).padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    SettingRow(stringResource(R.string.set_priorities_count)) {
                        NumberBox(s.priorities, 1..10) { v -> vm.setPriorityCount(v) }
                    }
                    SettingRow(stringResource(R.string.set_title_priorities)) {
                        SkinSwitch(s.showPrioritiesTitle) { v -> vm.updateSettings { it.copy(showPrioritiesTitle = v) } }
                    }
                    SettingRow(stringResource(R.string.set_title_braindump)) {
                        SkinSwitch(s.showBraindumpTitle) { v -> vm.updateSettings { it.copy(showBraindumpTitle = v) } }
                    }
                    SettingRow(stringResource(R.string.set_title_timebox)) {
                        SkinSwitch(s.showTimeboxTitle) { v -> vm.updateSettings { it.copy(showTimeboxTitle = v) } }
                    }
                    SettingRow(stringResource(R.string.set_wrap)) {
                        SkinSwitch(s.wrapText) { v -> vm.updateSettings { it.copy(wrapText = v) } }
                    }
                    SettingRow(stringResource(R.string.set_zoom)) {
                        SkinSwitch(s.zoom) { v -> vm.updateSettings { it.copy(zoom = v) } }
                    }
                    SettingRow(stringResource(R.string.set_drawing)) {
                        SkinSwitch(s.drawing) { v -> vm.updateSettings { it.copy(drawing = v) } }
                    }
                    SettingRow(stringResource(R.string.set_highlight_now)) {
                        SkinSwitch(s.highlightNow) { v -> vm.updateSettings { it.copy(highlightNow = v) } }
                    }
                    SettingRow(stringResource(R.string.set_strike)) {
                        SkinSwitch(s.strikeDouble) { v -> vm.updateSettings { it.copy(strikeDouble = v) } }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Benachrichtigungen ----------
            Text(
                stringResource(R.string.section_notifications),
                color = ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow(stringResource(R.string.notif_plan)) {
                    SkinSwitch(s.notifPlan) { v ->
                        if (v) ensureNotifPermission()
                        vm.updateSettings { it.copy(notifPlan = v) }
                    }
                }
                SettingRow(stringResource(R.string.notif_prios)) {
                    SkinSwitch(s.notifPrios) { v ->
                        if (v) ensureNotifPermission()
                        vm.updateSettings { it.copy(notifPrios = v) }
                    }
                }
                SettingRow(stringResource(R.string.notif_both)) {
                    SkinSwitch(s.notifBoth) { v ->
                        if (v) ensureNotifPermission()
                        vm.updateSettings { it.copy(notifBoth = v) }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Zeitschloss / Tagesschloss ----------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WideButton(stringResource(R.string.lock_time), Modifier.weight(1f), minHeight = 44.dp) {
                        showTimeLock = true
                    }
                    ResetBox(resetEnabled) { vm.releaseLock() }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WideButton(stringResource(R.string.lock_day), Modifier.weight(1f), minHeight = 44.dp) {
                        showDayLock = true
                    }
                    ResetBox(resetEnabled) { vm.releaseLock() }
                }
                SettingRow(stringResource(R.string.set_allow_release)) {
                    SkinSwitch(s.allowRelease) { v -> vm.updateSettings { it.copy(allowRelease = v) } }
                }
                if (hardLock) {
                    val until = Instant.ofEpochMilli(s.lockUntil).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                    Text(stringResource(R.string.lock_until, until), color = ink, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Export und Backup ----------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WideButton(stringResource(R.string.export_pdf), Modifier.fillMaxWidth()) { showPdf = true }
                WideButton(stringResource(R.string.export_png), Modifier.fillMaxWidth()) { showPng = true }
                Spacer(Modifier.height(4.dp))
                WideButton(stringResource(R.string.backup_export), Modifier.fillMaxWidth()) {
                    exportLauncher.launch("timebox-${LocalDate.now()}.json")
                }
                WideButton(
                    stringResource(R.string.backup_import),
                    Modifier.fillMaxWidth(),
                    enabled = !hardLock,
                ) {
                    importLauncher.launch(arrayOf("*/*"))
                }
                WideButton(stringResource(R.string.backup_auto), Modifier.fillMaxWidth()) { showAuto = true }
            }

            Spacer(Modifier.height(18.dp))

            // ---------- Passwort ----------
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WideButton(stringResource(R.string.pw_numeric), Modifier.fillMaxWidth()) {
                    pwDialog = PasswordInfo.NUMERIC
                }
                WideButton(stringResource(R.string.pw_text), Modifier.fillMaxWidth()) {
                    pwDialog = PasswordInfo.TEXT
                }
                WideButton(
                    stringResource(R.string.pw_delete),
                    Modifier.fillMaxWidth(),
                    enabled = (pwInfo?.type ?: PasswordInfo.NONE) != PasswordInfo.NONE,
                ) {
                    vm.deletePassword()
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- Layout-Designs, ohne Beschriftung ----------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Design.entries.forEachIndexed { index, design ->
                    DesignTile(
                        design = design,
                        selected = s.design == index,
                        mode = s.themeMode,
                        systemDark = systemDark,
                        modifier = Modifier.weight(1f).aspectRatio(0.6f),
                    ) { vm.updateSettings { it.copy(design = index) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ---------- Pop-ups ----------

    pwDialog?.let { type ->
        PasswordDialog(
            numeric = type == PasswordInfo.NUMERIC,
            onCancel = { pwDialog = null },
            onConfirm = { secret ->
                vm.createPassword(type, secret)
                pwDialog = null
            },
        )
    }

    if (showPdf) {
        MultiDayDialog(
            confirmText = stringResource(R.string.dlg_export),
            onCancel = { showPdf = false },
            onConfirm = { dates ->
                showPdf = false
                vm.exportPdf(dates) { ok -> toast(if (ok) R.string.export_ok else R.string.backup_failed) }
            },
        )
    }

    if (showPng) {
        MultiDayDialog(
            confirmText = stringResource(R.string.dlg_export),
            onCancel = { showPng = false },
            onConfirm = { dates ->
                showPng = false
                vm.exportPng(dates) { ok -> toast(if (ok) R.string.export_ok else R.string.backup_failed) }
            },
        )
    }

    if (showTimeLock) {
        TimeLockDialog(
            onCancel = { showTimeLock = false },
            onStart = { ms ->
                showTimeLock = false
                vm.startTimeLock(ms)
            },
        )
    }

    if (showDayLock) {
        SingleDayDialog(
            minDate = LocalDate.now(),
            confirmText = stringResource(R.string.dlg_start),
            onCancel = { showDayLock = false },
            onConfirm = { date ->
                showDayLock = false
                vm.startDayLock(date)
            },
        )
    }

    if (showAuto) {
        AutoBackupDialog(
            active = s.autoBackup,
            initialN = s.autoBackupN,
            initialUnit = s.autoBackupUnit,
            onCancel = { showAuto = false },
            onDisable = {
                showAuto = false
                vm.disableAutoBackup()
                toast(R.string.backup_auto_off)
            },
            onConfirm = { n, unit ->
                showAuto = false
                vm.enableAutoBackup(n, unit) { ok ->
                    toast(if (ok) R.string.backup_auto_on else R.string.backup_failed)
                }
            },
        )
    }
}

/** Kasten/Toggle links, Beschriftung rechts dahinter. */
@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    val skin = LocalSkin.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { control() }
        Text(label, Modifier.weight(1f), color = skin.ink, fontSize = 14.sp)
    }
}

@Composable
private fun SkinSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val skin = LocalSkin.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = skin.bg,
            checkedTrackColor = skin.accent,
            checkedBorderColor = skin.accent,
            uncheckedThumbColor = skin.ink,
            uncheckedTrackColor = Color.Transparent,
            uncheckedBorderColor = skin.ink,
        ),
    )
}

@Composable
private fun SquareButton(text: String, onClick: () -> Unit) {
    val ink = LocalSkin.current.ink
    Box(
        Modifier
            .size(52.dp)
            .border(1.5.dp, ink)
            .tapOrHold(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

/** Quadratischer Kasten mit rundem Reset-Pfeil. */
@Composable
private fun ResetBox(enabled: Boolean, onClick: () -> Unit) {
    val ink = LocalSkin.current.ink
    val description = stringResource(R.string.cd_reset)
    Box(
        Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .border(1.5.dp, ink)
            .semantics { contentDescription = description; role = Role.Button }
            .then(if (enabled) Modifier.tapOrHold(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        ResetIcon(ink, Modifier.size(28.dp))
    }
}

@Composable
private fun NumberBox(value: Int, range: IntRange, onValue: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    val ink = LocalSkin.current.ink
    var text by remember(value) { mutableStateOf(value.toString()) }

    BasicTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(2)
            text = digits
            digits.toIntOrNull()?.let { if (it in range) onValue(it) }
        },
        singleLine = true,
        textStyle = TextStyle(
            color = ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .size(52.dp)
            .border(1.5.dp, ink)
            .onFocusChanged { if (!it.isFocused) text = value.toString() },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        },
    )
}

@Composable
private fun DesignTile(
    design: Design,
    selected: Boolean,
    mode: Int,
    systemDark: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val current = LocalSkin.current
    val sk = remember(design, mode, systemDark) { resolveSkin(design, mode, systemDark) }
    Box(
        modifier
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) current.ink else current.ink.copy(alpha = 0.35f),
            )
            .padding(3.dp)
            .semantics { contentDescription = design.name; role = Role.Button }
            .tapOrHold(onClick)
    ) {
        SkinBackground(sk, Modifier.fillMaxSize(), unit = 10.dp)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = w * 0.1f
            val g = w * 0.06f
            val colW = (w - 2 * m - g) / 2f
            val top = h * 0.2f
            val sw = (w * 0.035f).coerceAtLeast(1f)

            fun box(x: Float, y: Float, bw: Float, bh: Float, fill: Color) {
                drawRect(fill, Offset(x, y), Size(bw, bh))
                drawRect(sk.frame, Offset(x, y), Size(bw, bh), style = Stroke(sw))
            }

            drawRect(sk.chip ?: sk.ink, Offset(m, h * 0.07f), Size(w - 2 * m, h * 0.07f))
            box(m, top, colW, h * 0.16f, sk.prioFill)
            box(m, top + h * 0.16f + g, colW, h - top - h * 0.16f - g - m, sk.brainFill)
            box(m + colW + g, top, colW, h - top - m, sk.timeFill)
        }
    }
}
