package com.example.dayplanner.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dayplanner.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalEvent(val title: String, val begin: Long)

object CalendarSync {

    /** Termine des Tages aus allen Kalendern des Geräts (ohne ganztägige Termine). */
    fun eventsOn(context: Context, date: LocalDate): List<CalEvent> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, start)
        ContentUris.appendId(builder, end)

        val out = ArrayList<CalEvent>()
        context.contentResolver.query(
            builder.build(),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
            ),
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) == 1) continue
                val title = c.getString(0).orEmpty().ifBlank { "–" }
                out.add(CalEvent(title, c.getLong(1)))
            }
        }
        return out
    }

    /** Öffnet die Kalender-App mit einem vorausgefüllten neuen Termin (ohne Berechtigung). */
    fun insertIntent(title: String, begin: Long, end: Long): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
}

/** Dünne weiße Zeile direkt über der Tastatur mit „+ vom Kalender" und „+ zu Kalender". */
@Composable
fun CalendarBar(toEnabled: Boolean, onFrom: () -> Unit, onTo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color.White)
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .tapOrHold(onFrom),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.cal_from),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0xFFBDBDBD))
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .alpha(if (toEnabled) 1f else 0.35f)
                .then(if (toEnabled) Modifier.tapOrHold(onTo) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.cal_to),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/** Liste der Termine des Tages. Ein Tipp wählt den Termin aus. */
@Composable
fun EventPickerDialog(date: LocalDate, onPick: (CalEvent) -> Unit, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<CalEvent>?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    LaunchedEffect(date) {
        events = withContext(Dispatchers.IO) {
            runCatching { CalendarSync.eventsOn(context, date) }.getOrDefault(emptyList())
        }
    }

    DialogFrame(onDismiss) {
        val list = events
        if (list == null) {
            Text("…", color = skin.ink, fontSize = 16.sp)
        } else if (list.isEmpty()) {
            Text(stringResource(R.string.cal_none), color = skin.ink, fontSize = 16.sp)
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(list) { ev ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .tapOrHold { onPick(ev) }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = timeFormat.format(Instant.ofEpochMilli(ev.begin).atZone(zone)),
                                color = skin.ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(ev.title, color = skin.ink, fontSize = 15.sp, maxLines = 2)
                        }
                        HLine(skin.ink.copy(alpha = 0.25f), 1.dp)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        WideButton(stringResource(R.string.dlg_cancel), Modifier.fillMaxWidth()) { onDismiss() }
    }
}
