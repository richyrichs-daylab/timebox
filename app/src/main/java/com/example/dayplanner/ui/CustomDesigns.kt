package com.example.dayplanner.ui

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.dayplanner.data.Design
import com.example.dayplanner.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CustomEntry(val id: String, val name: String, val skin: Skin)

/**
 * Importierte Design-Dateien (reine Daten, kein Code). Format: JSON mit
 * "format": "timebox-design" und "version": 1. Ablage: App-Speicher, Ordner "designs".
 */
object CustomDesigns {
    private const val MAX_BYTES = 200_000
    private val ID_REGEX = Regex("[a-z0-9_-]{1,40}")
    private val cache = ConcurrentHashMap<String, Skin>()

    private fun dir(context: Context): File =
        File(context.applicationContext.filesDir, "designs").apply { mkdirs() }

    private fun colorOf(s: String): Color = Color(android.graphics.Color.parseColor(s.trim()))

    /** Liest eine Design-Datei. Gibt null zurück, wenn sie ungültig ist. */
    fun parse(text: String): CustomEntry? = try {
        val o = JSONObject(text)
        require(o.optString("format") == "timebox-design")
        require(o.optInt("version", 0) == 1)
        val id = o.getString("id")
        require(ID_REGEX.matches(id))
        val name = o.optString("name", id).take(40)

        fun col(key: String): Color? =
            o.optString(key, "").takeIf { it.isNotEmpty() }?.let { colorOf(it) }

        fun colors(key: String): List<Color> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return List(arr.length()) { colorOf(arr.getString(it)) }
        }

        val bg = col("bg") ?: throw IllegalArgumentException("bg fehlt")
        val ink = col("ink") ?: throw IllegalArgumentException("ink fehlt")
        val fieldInk = col("fieldInk") ?: ink
        val timeInk = col("timeInk") ?: fieldInk
        val line = col("line") ?: ink
        val frame = col("frame") ?: line

        val skin = Skin(
            design = Design.STANDARD,
            dark = o.optBoolean("dark", false),
            bg = bg,
            ink = ink,
            fieldInk = fieldInk,
            timeInk = timeInk,
            line = line,
            frame = frame,
            frameWidth = o.optDouble("frameWidth", 1.0).coerceIn(0.0, 6.0).toFloat().dp,
            lineWidth = o.optDouble("lineWidth", 1.0).coerceIn(0.5, 4.0).toFloat().dp,
            accent = col("accent") ?: ink,
            lineEnd = col("lineEnd"),
            frameBrain = col("frameBrain") ?: frame,
            prioFill = col("prioFill") ?: Color.Transparent,
            brainFill = col("brainFill") ?: Color.Transparent,
            timeFill = col("timeFill") ?: Color.Transparent,
            hourFill = col("hourFill") ?: Color.Transparent,
            hourInk = col("hourInk") ?: ink,
            chip = col("chip"),
            chipInk = col("chipInk") ?: ink,
            glow = o.optBoolean("glow", false),
            upperTitles = o.optBoolean("upperTitles", false),
            gradient = colors("gradient").takeIf { it.size >= 2 },
            clouds = o.optBoolean("clouds", false),
            sun = o.optBoolean("sun", false),
            titleStyle = when (o.optString("titleStyle", "plain")) {
                "pill" -> 1
                "tab" -> 2
                else -> 0
            },
            titleFills = colors("titleFill"),
            titleInk = col("titleInk"),
            titleShadow = col("titleShadow"),
            shadowColor = col("shadow"),
            shadowOffset = o.optDouble("shadowOffset", 0.0).coerceIn(0.0, 12.0).toFloat().dp,
            hourHighlight = col("hourHighlight"),
            customId = id,
        )
        CustomEntry(id, name, skin)
    } catch (e: Exception) {
        null
    }

    /** Importiert eine Design-Datei und speichert sie im App-Speicher. */
    suspend fun import(context: Context, uri: Uri): CustomEntry? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_BYTES) return@withContext null
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: return@withContext null
            val text = String(bytes, Charsets.UTF_8)
            val entry = parse(text) ?: return@withContext null
            File(dir(context), "${entry.id}.json").writeText(text)
            cache[entry.id] = entry.skin
            entry
        } catch (e: Exception) {
            null
        }
    }

    /** Alle importierten Designs, alphabetisch nach Dateiname. */
    fun list(context: Context): List<CustomEntry> =
        (dir(context).listFiles() ?: emptyArray())
            .filter { it.extension == "json" }
            .sortedBy { it.name }
            .mapNotNull { f -> runCatching { parse(f.readText()) }.getOrNull() }
            .also { entries -> entries.forEach { cache[it.id] = it.skin } }

    fun skinFor(context: Context, id: String): Skin? {
        if (!ID_REGEX.matches(id)) return null
        cache[id]?.let { return it }
        val f = File(dir(context), "$id.json")
        if (!f.exists()) return null
        val entry = runCatching { parse(f.readText()) }.getOrNull() ?: return null
        cache[id] = entry.skin
        return entry.skin
    }
}

/** Das gewählte Design: importiert oder eingebaut. Fehlt die Datei, gilt das eingebaute Design. */
fun resolveSkinFor(context: Context, s: Settings, systemDark: Boolean): Skin =
    (if (s.customDesign.isNotEmpty()) CustomDesigns.skinFor(context, s.customDesign) else null)
        ?: resolveSkin(s.designId, s.themeMode, systemDark)
