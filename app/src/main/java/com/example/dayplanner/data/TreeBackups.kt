package com.example.dayplanner.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.JsonReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader

/**
 * Automatische Backups in einem vom Nutzer gewählten Ordner. Jede selbst angelegte Datei wird
 * in einer kleinen Liste vermerkt. Gelöscht werden nur Dateien aus dieser Liste, und nur wenn
 * ihr Inhalt noch das Kennzeichen eines Timebox-Backups trägt.
 */
object TreeBackups {
    private val lock = Any()

    private class Entry(val uri: String, val time: Long)

    private fun file(context: Context) = File(context.applicationContext.filesDir, "auto_backups.json")

    private fun read(context: Context): MutableList<Entry> {
        val f = file(context)
        if (!f.exists()) return ArrayList()
        return runCatching {
            val arr = JSONArray(f.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("u"), o.getLong("t"))
            }
        }.getOrDefault(ArrayList())
    }

    private fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("u", it.uri).put("t", it.time)) }
        file(context).writeText(arr.toString())
    }

    /** Legt die Backup-Datei im gewählten Ordner an. Gibt false zurück, wenn das nicht möglich ist. */
    fun write(context: Context, tree: Uri, name: String, text: String): Boolean {
        val app = context.applicationContext
        val resolver = app.contentResolver
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name)
                ?: return false
            resolver.openOutputStream(doc, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            synchronized(lock) {
                val list = read(app)
                list.add(Entry(doc.toString(), System.currentTimeMillis()))
                save(app, list)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Behält die neuesten [keep] selbst angelegten Backups, löscht die übrigen. keep = 0: nichts löschen. */
    fun applyKeep(context: Context, keep: Int) {
        if (keep <= 0) return
        val app = context.applicationContext
        val resolver = app.contentResolver
        synchronized(lock) {
            val list = read(app).sortedByDescending { it.time }
            list.drop(keep).forEach { e ->
                val uri = Uri.parse(e.uri)
                if (isOwnBackup(resolver, uri)) {
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                }
            }
            save(app, list.take(keep))
        }
    }

    /** Liest nur den Anfang des JSON: Das Feld "app" muss "dayplanner" sein (auch bei verschlüsselten Backups). */
    private fun isOwnBackup(resolver: ContentResolver, uri: Uri): Boolean = try {
        resolver.openInputStream(uri)?.use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                var ok = false
                while (reader.hasNext()) {
                    if (reader.nextName() == "app") {
                        ok = reader.nextString() == "dayplanner"
                        break
                    } else {
                        reader.skipValue()
                    }
                }
                ok
            }
        } ?: false
    } catch (e: Exception) {
        false
    }
}
