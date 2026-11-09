package com.example.dayplanner.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/** Speichert Dateien im Download-Ordner des Geräts (ohne Speicher-Berechtigung). */
object Downloads {
    fun save(
        context: Context,
        name: String,
        mime: String,
        subDir: String? = null,
        write: (OutputStream) -> Unit,
    ): Boolean {
        val resolver = context.applicationContext.contentResolver
        val path = if (subDir == null) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/" + subDir
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)!!.use(write)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }
}
