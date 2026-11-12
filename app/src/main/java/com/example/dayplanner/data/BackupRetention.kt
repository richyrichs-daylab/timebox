package com.example.dayplanner.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.JsonReader
import java.io.InputStreamReader

/**
 * Löscht überzählige automatische Backups. Angefasst werden nur Dateien, die diese App selbst
 * in Download/Timebox angelegt hat. Vor dem Löschen wird der Inhalt geprüft: Er muss das
 * Kennzeichen eines Timebox-Backups tragen, der Name allein genügt nicht.
 */
object BackupRetention {

    private class Candidate(val uri: Uri)

    fun apply(context: Context, keep: Int) {
        if (keep <= 0) return
        val app = context.applicationContext
        val resolver = app.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(app.packageName, "Download/Timebox%", "timebox-backup-%")
        val order = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"

        val candidates = ArrayList<Candidate>()
        resolver.query(collection, projection, selection, args, order)?.use { c ->
            while (c.moveToNext()) {
                candidates.add(Candidate(ContentUris.withAppendedId(collection, c.getLong(0))))
            }
        }

        val backups = candidates.filter { isOwnBackup(resolver, it.uri) }
        backups.drop(keep).forEach { runCatching { resolver.delete(it.uri, null, null) } }
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
