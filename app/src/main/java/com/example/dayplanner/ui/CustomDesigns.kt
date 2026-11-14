package com.example.dayplanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.dayplanner.data.Design
import com.example.dayplanner.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class CustomEntry(
    val id: String,
    val name: String,
    val skin: Skin,
    val names: Map<String, String> = emptyMap(),
) {
    /** Name für Screenreader in der Systemsprache, sonst Englisch, sonst der Wert "name". */
    fun displayName(): String {
        val lang = Locale.getDefault().language
        return names[lang] ?: names["en"] ?: name
    }
}

/**
 * Design-Dateien (reine Daten, kein Code). Format: JSON mit "format": "timebox-design" und "version": 1.
 *
 * Die App kopiert nie eine Datei. Sie merkt sich einen Design-Ordner (dauerhafter Zugriff) und liest
 * alle Designs direkt dort. Zusätzlich bleiben früher einzeln importierte Dateien erhalten, solange
 * sie lesbar sind. Fehlt die Datei eines Designs, ist es nicht verfügbar und die App nimmt das
 * Standarddesign. Die App löscht oder vergisst nie etwas von sich aus.
 */
object CustomDesigns {
    private const val MAX_BYTES = 200_000
    private val ID_REGEX = Regex("[a-z0-9_-]{1,40}")
    private val cache = ConcurrentHashMap<String, Skin>()
    private var migrated = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("designs_prefs", Context.MODE_PRIVATE)

    /** Verzeichnis: Design-ID zu Ort der Datei (aus dem Design-Ordner oder früher einzeln importiert). */
    private fun indexFile(context: Context) =
        File(context.applicationContext.filesDir, "designs_index.json")

    @Synchronized
    private fun readIndex(context: Context): MutableMap<String, String> {
        val map = LinkedHashMap<String, String>()
        val f = indexFile(context)
        if (!f.exists()) return map
        runCatching {
            val o = JSONObject(f.readText())
            o.keys().forEach { k -> map[k] = o.getString(k) }
        }
        return map
    }

    @Synchronized
    private fun writeIndex(context: Context, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        indexFile(context).writeText(o.toString())
    }

    /** Früher angelegte Kopien im App-Speicher werden entfernt. */
    @Synchronized
    private fun migrate(context: Context) {
        if (migrated) return
        migrated = true
        File(context.applicationContext.filesDir, "designs").deleteRecursively()
    }

    /** Vergisst alle gelesenen Designs: beim nächsten Zugriff wird die Datei wieder gelesen. */
    fun invalidate() {
        cache.clear()
    }

    private fun colorOf(s: String): Color = Color(android.graphics.Color.parseColor(s.trim()))

    /** Liest eine Design-Datei. Gibt null zurück, wenn sie ungültig ist. */
    fun parse(text: String): CustomEntry? = try {
        val o = JSONObject(text)
        require(o.optString("format") == "timebox-design")
        require(o.optInt("version", 0) == 1)
        val id = o.getString("id")
        require(ID_REGEX.matches(id))
        val name = o.optString("name", id).take(40)

        val names = HashMap<String, String>()
        val namesObj = o.optJSONObject("names")
        if (namesObj != null) {
            namesObj.keys().forEach { k ->
                val v = namesObj.optString(k, "").take(40)
                if (v.isNotBlank()) names[k.lowercase()] = v
            }
        }

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
        CustomEntry(id, name, skin, names)
    } catch (e: Exception) {
        null
    }

    /** Liest die Datei am angegebenen Ort. Wirft FileNotFoundException oder SecurityException, wenn sie weg ist. */
    private fun readBounded(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) return null
                out.write(buf, 0, n)
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    private class Loaded(val entry: CustomEntry?, val gone: Boolean)

    private fun loadFrom(context: Context, uriString: String): Loaded = try {
        val text = readBounded(context, Uri.parse(uriString))
        Loaded(if (text == null) null else parse(text), false)
    } catch (e: FileNotFoundException) {
        Loaded(null, true)
    } catch (e: SecurityException) {
        Loaded(null, true)
    } catch (e: Exception) {
        Loaded(null, false)
    }

    // ------------------------------------------------------------ Design-Ordner

    /**
     * Merkt sich den Design-Ordner (dauerhafter Zugriff, es wird nichts kopiert) und liest ihn ein.
     * Gibt die Zahl der gefundenen Designs zurück.
     */
    fun setTree(context: Context, tree: Uri): Int {
        runCatching {
            context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs(context).edit().putString("tree", tree.toString()).apply()
        cache.clear()
        return scanTree(context)
    }

    /**
     * Liest alle Dateien im Design-Ordner und trägt jede gültige Design-Datei samt Ort ins Verzeichnis ein.
     * Gibt die Zahl der gültigen Designs im Ordner zurück. Ist der Ordner nicht erreichbar, passiert nichts.
     */
    private fun scanTree(context: Context): Int {
        val treeString = prefs(context).getString("tree", null) ?: return 0
        val tree = Uri.parse(treeString)
        var found = 0
        try {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
            val map = readIndex(context)
            var changed = false
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    if (!name.endsWith(".json", ignoreCase = true)) continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString()
                    val entry = loadFrom(context, docUri).entry ?: continue
                    found++
                    cache[entry.id] = entry.skin
                    if (map[entry.id] != docUri) {
                        map[entry.id] = docUri
                        changed = true
                    }
                }
            }
            if (changed) writeIndex(context, map)
        } catch (e: Exception) {
            // Ordner nicht erreichbar: bereits bekannte Designs bleiben, solange ihre Dateien lesbar sind.
        }
        return found
    }

    // ------------------------------------------------------------ Einzelne Dateien (früher)

    /** Merkt sich den Ort einer einzelnen Datei. Es wird nichts kopiert. */
    suspend fun import(context: Context, uri: Uri): CustomEntry? = withContext(Dispatchers.IO) {
        try {
            migrate(context)
            val text = readBounded(context, uri) ?: return@withContext null
            val entry = parse(text) ?: return@withContext null
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val map = readIndex(context)
            map[entry.id] = uri.toString()
            writeIndex(context, map)
            cache[entry.id] = entry.skin
            entry
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ Abfragen

    /** Alle Designs, deren Datei gerade tatsächlich lesbar ist. */
    fun list(context: Context): List<CustomEntry> {
        migrate(context)
        scanTree(context)
        val out = ArrayList<CustomEntry>()
        for ((id, uriString) in readIndex(context).entries.toList()) {
            val entry = loadFrom(context, uriString).entry
            if (entry != null && entry.id == id) {
                cache[id] = entry.skin
                out.add(entry)
            } else {
                cache.remove(id)
            }
        }
        return out.sortedBy { it.id }
    }

    fun skinFor(context: Context, id: String): Skin? {
        if (!ID_REGEX.matches(id)) return null
        cache[id]?.let { return it }
        migrate(context)
        val uriString = readIndex(context)[id] ?: return null
        val entry = loadFrom(context, uriString).entry
        if (entry != null && entry.id == id) {
            cache[id] = entry.skin
            return entry.skin
        }
        return null
    }
}

/**
 * Das gewählte Design: importiert oder eingebaut. Fehlt die Datei eines importierten Designs,
 * gilt das Standarddesign.
 */
fun resolveSkinFor(context: Context, s: Settings, systemDark: Boolean): Skin {
    if (s.customDesign.isNotEmpty()) {
        return CustomDesigns.skinFor(context, s.customDesign)
            ?: resolveSkin(Design.STANDARD, s.themeMode, systemDark)
    }
    return resolveSkin(s.designId, s.themeMode, systemDark)
}
