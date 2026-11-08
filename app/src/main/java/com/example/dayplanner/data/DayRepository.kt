package com.example.dayplanner.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

private fun Map<Int, String>.toJsonObj(): JSONObject {
    val o = JSONObject()
    forEach { (k, v) -> o.put(k.toString(), v) }
    return o
}

private fun JSONObject?.toIntMap(): Map<Int, String> =
    if (this == null) emptyMap()
    else keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to getString(k) } }.toMap()

internal fun dayToJson(d: DayData): JSONObject {
    val o = JSONObject()
        .put("p", d.priorities.toJsonObj())
        .put("t", d.timebox.toJsonObj())
        .put("b", d.braindump)
    if (d.strokes.isNotEmpty()) {
        val arr = JSONArray()
        d.strokes.forEach { s ->
            val pts = JSONArray()
            s.points.forEach { pts.put(Math.round(it * 10f) / 10.0) }
            arr.put(JSONObject().put("e", s.erase).put("p", pts))
        }
        o.put("d", arr)
    }
    return o
}

internal fun dayFromJson(o: JSONObject): DayData {
    val strokes = ArrayList<InkStroke>()
    val arr = o.optJSONArray("d")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val so = arr.optJSONObject(i) ?: continue
            val pa = so.optJSONArray("p") ?: continue
            strokes.add(
                InkStroke(so.optBoolean("e", false), List(pa.length()) { pa.getDouble(it).toFloat() })
            )
        }
    }
    return DayData(
        priorities = o.optJSONObject("p").toIntMap(),
        timebox = o.optJSONObject("t").toIntMap(),
        braindump = o.optString("b", ""),
        strokes = strokes,
    )
}

class DayRepository(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "days").apply { mkdirs() }
    private val mutex = Mutex()

    private fun file(date: LocalDate) = File(dir, "$date.json")

    suspend fun load(date: LocalDate): DayData = mutex.withLock {
        withContext(Dispatchers.IO) {
            val f = file(date)
            if (!f.exists()) DayData()
            else runCatching { dayFromJson(JSONObject(f.readText())) }.getOrDefault(DayData())
        }
    }

    suspend fun save(date: LocalDate, data: DayData) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val f = file(date)
            if (data == DayData()) f.delete() else f.writeText(dayToJson(data).toString())
        }
    }

    /** Alle Tage als JSON-Objekt (Datum -> Tag) für das Backup. */
    suspend fun exportAll(): JSONObject = mutex.withLock {
        withContext(Dispatchers.IO) {
            val out = JSONObject()
            dir.listFiles()?.forEach { f ->
                if (f.extension == "json") {
                    runCatching { out.put(f.nameWithoutExtension, JSONObject(f.readText())) }
                }
            }
            out
        }
    }

    /** Überschreibt Tage mit gleichem Datum, andere Tage bleiben erhalten. */
    suspend fun importAll(days: JSONObject) = mutex.withLock {
        withContext(Dispatchers.IO) {
            days.keys().forEach { key ->
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
                val o = days.optJSONObject(key) ?: return@forEach
                File(dir, "$date.json").writeText(o.toString())
            }
        }
    }
}
