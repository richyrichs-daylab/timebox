package com.example.dayplanner.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.Downloads
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Exporter {
    private const val PAGE_W_DP = 400f
    private const val PAGE_H_DP = 400f * 842f / 595f   // A4-Seitenverhältnis

    private fun fileName(dates: List<LocalDate>, ext: String) =
        if (dates.size == 1) "timebox-${dates[0]}.$ext"
        else "timebox-${dates.first()}_${dates.last()}.$ext"

    /** A4-PDF, eine Seite pro Tag, im Download-Ordner. */
    suspend fun pdf(context: Context, dates: List<LocalDate>): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val app = context.applicationContext
            val settings = SettingsRepository(app).current()
            val repo = DayRepository(app)
            val sorted = dates.sorted()
            val doc = PdfDocument()
            try {
                sorted.forEachIndexed { i, date ->
                    val bmp = WidgetRenderer.renderPage(app, settings, repo.load(date), date, PAGE_W_DP, PAGE_H_DP, 1240)
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
                    page.canvas.drawBitmap(bmp, null, Rect(0, 0, 595, 842), Paint(Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(page)
                    bmp.recycle()
                }
                Downloads.save(app, fileName(sorted, "pdf"), "application/pdf") { out -> doc.writeTo(out) }
            } finally {
                doc.close()
            }
        }.getOrDefault(false)
    }

    /** Ein PNG, die Tage stehen untereinander, im Download-Ordner. */
    suspend fun png(context: Context, dates: List<LocalDate>): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val app = context.applicationContext
            val settings = SettingsRepository(app).current()
            val repo = DayRepository(app)
            val sorted = dates.sorted()
            val n = sorted.size

            val baseW = 800
            val basePageH = baseW * PAGE_H_DP / PAGE_W_DP
            val shrink = min(1f, 16000f / (n * basePageH))
            val pxW = max(240, (baseW * shrink).roundToInt())

            var out: Bitmap? = null
            sorted.forEachIndexed { i, date ->
                val bmp = WidgetRenderer.renderPage(app, settings, repo.load(date), date, PAGE_W_DP, PAGE_H_DP, pxW)
                val target = out ?: Bitmap.createBitmap(pxW, bmp.height * n, Bitmap.Config.ARGB_8888).also { out = it }
                Canvas(target).drawBitmap(bmp, 0f, (i * bmp.height).toFloat(), null)
                bmp.recycle()
            }
            val result = out!!
            val ok = Downloads.save(app, fileName(sorted, "png"), "image/png") { stream ->
                result.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            result.recycle()
            ok
        }.getOrDefault(false)
    }

    /** PNG des angezeigten Tages im Cache, als Uri für das Teilen-Fenster. */
    suspend fun shareUri(context: Context, settings: Settings, data: DayData, date: LocalDate): Uri? =
        withContext(Dispatchers.Default) {
            runCatching {
                val app = context.applicationContext
                val bmp = WidgetRenderer.renderPage(app, settings, data, date, PAGE_W_DP, PAGE_H_DP, 1080)
                val dir = File(app.cacheDir, "share").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "timebox-$date.png")
                FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            }.getOrNull()
        }
}
