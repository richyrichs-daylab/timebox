package com.example.dayplanner.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

class DayRepository(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "days").apply { mkdirs() }
    private val mutex = Mutex()

    private fun file(date: LocalDate) = File(dir, "$date.json")

    suspend fun load(date: LocalDate): DayData = mutex.withLock {
        withContext(Dispatchers.IO) {
            val f = file(date)
            if (!f.exists()) {
                DayData()
            } else {
                runCatching {
                    val o = JSONObject(f.readText())
                    DayData(
                        priorities = o.optJSONObject("p").toIntMap(),
                        timebox = o.optJSONObject("t").toIntMap(),
                        braindump = o.optString("b", ""),
                    )
                }.getOrDefault(DayData())
            }
        }
    }

    suspend fun save(date: LocalDate, data: DayData) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val f = file(date)
            if (data == DayData()) {
                f.delete()
            } else {
                val o = JSONObject()
                    .put("p", data.priorities.toJson())
                    .put("t", data.timebox.toJson())
                    .put("b", data.braindump)
                f.writeText(o.toString())
            }
        }
    }

    private fun Map<Int, String>.toJson(): JSONObject {
        val o = JSONObject()
        forEach { (k, v) -> o.put(k.toString(), v) }
        return o
    }

    private fun JSONObject?.toIntMap(): Map<Int, String> =
        if (this == null) emptyMap()
        else keys().asSequence().associate { it.toInt() to getString(it) }
}
