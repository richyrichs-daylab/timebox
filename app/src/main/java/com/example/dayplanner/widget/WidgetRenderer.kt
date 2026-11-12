package com.example.dayplanner.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings as SystemSettings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.example.dayplanner.R
import com.example.dayplanner.data.DayData
import com.example.dayplanner.data.DayRepository
import com.example.dayplanner.data.Design
import com.example.dayplanner.data.InkStroke
import com.example.dayplanner.data.Settings
import com.example.dayplanner.data.SettingsRepository
import com.example.dayplanner.ui.Skin
import com.example.dayplanner.ui.formatDate
import com.example.dayplanner.ui.resolveSkinFor
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class WidgetSnapshot(val settings: Settings, val day: DayData, val date: LocalDate)

/** Art einer antippbaren Fläche im Widget. */
object HitType {
    const val PRIO = 0
    const val TIME = 1
    const val BRAIN = 2
    const val PREV = 3
    const val NEXT = 4
    const val GEAR = 5
    const val LOCK = 6
}

/** Antippbare Fläche in dp (links, oben, rechts, unten) relativ zum Widget. */
class Hit(val l: Float, val t: Float, val r: Float, val b: Float, val type: Int, val key: Int)

class WidgetImage(val bitmap: Bitmap, val hits: List<Hit>)

object WidgetRenderer {

    suspend fun snapshot(context: Context): WidgetSnapshot {
        val app = context.applicationContext
        val date = LocalDate.now()
        return WidgetSnapshot(SettingsRepository(app).current(), DayRepository(app).load(date), date)
    }

    /** Zeichnet das Widget im gewählten Design. Alle Maße sind in dp, die Canvas wird skaliert. */
    fun render(ctx: Context, kind: WidgetKind, wDp: Float, hDp: Float, snap: WidgetSnapshot): WidgetImage {
        val dm = ctx.resources.displayMetrics
        var density = dm.density
        val maxPx = dm.widthPixels.toFloat() * dm.heightPixels
        val px = wDp * hDp * density * density
        if (px > maxPx) density *= sqrt(maxPx / px)

        val bitmap = Bitmap.createBitmap(
            max(1, (wDp * density).roundToInt()),
            max(1, (hDp * density).roundToInt()),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)

        val hits = ArrayList<Hit>()
        val skin = resolveSkinFor(ctx, snap.settings, isNight(ctx))
        Painter(ctx, skin, snap, canvas, wDp, hDp, live = true, hits = hits).draw(kind)
        return WidgetImage(bitmap, hits)
    }

    /**
     * Ganze Tagesseite für PDF, PNG und Teilen: oben nur das Datum, ohne runde Ecken.
     * Breite in Pixeln, die Höhe ergibt sich aus dem Seitenverhältnis wDp:hDp.
     */
    fun renderPage(
        ctx: Context,
        settings: Settings,
        data: DayData,
        date: LocalDate,
        wDp: Float,
        hDp: Float,
        pxWidth: Int,
    ): Bitmap {
        val scale = pxWidth / wDp
        val bitmap = Bitmap.createBitmap(pxWidth, max(1, (hDp * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val skin = resolveSkinFor(ctx, settings, isNight(ctx))
        Painter(ctx, skin, WidgetSnapshot(settings, data, date), canvas, wDp, hDp, live = false, hits = null)
            .drawExport()
        return bitmap
    }

    /** Live-Hintergrund: der ganze Tag in Bildschirmgröße, mit Abstand zu Status- und Navigationsleiste. */
    fun renderWallpaper(
        ctx: Context,
        settings: Settings,
        data: DayData,
        date: LocalDate,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val density = ctx.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap(max(1, widthPx), max(1, heightPx), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val skin = resolveSkinFor(ctx, settings, isNight(ctx))
        Painter(
            ctx, skin, WidgetSnapshot(settings, data, date), canvas,
            widthPx / density, heightPx / density, live = true, hits = null,
        ).drawWallpaper(statusBarDp(ctx), navigationBarDp(ctx))
        return bitmap
    }

    private fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dimenDp(ctx: Context, name: String): Float {
        val id = ctx.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) / ctx.resources.displayMetrics.density else 0f
    }

    private fun statusBarDp(ctx: Context): Float = dimenDp(ctx, "status_bar_height")

    private fun navigationBarDp(ctx: Context): Float {
        val gestural = runCatching {
            SystemSettings.Secure.getInt(ctx.contentResolver, "navigation_mode", 0) == 2
        }.getOrDefault(false)
        val gesture = dimenDp(ctx, "navigation_bar_gesture_height")
        return if (gestural && gesture > 0f) gesture else dimenDp(ctx, "navigation_bar_height")
    }
}

/** -1 = vergangen, 0 = aktuelle Stunde, 1 = kommend. */
private fun rowState(date: LocalDate, dayStart: Int, i: Int, nowMillis: Long): Int {
    val zone = ZoneId.systemDefault()
    val base = date.atTime(dayStart, 0)
    val start = base.plusHours(i.toLong()).atZone(zone).toInstant().toEpochMilli()
    val end = base.plusHours(i + 1L).atZone(zone).toInstant().toEpochMilli()
    return when {
        nowMillis >= end -> -1
        nowMillis >= start -> 0
        else -> 1
    }
}

private class Painter(
    private val ctx: Context,
    private val skin: Skin,
    private val snap: WidgetSnapshot,
    private val c: Canvas,
    private val w: Float,
    private val h: Float,
    private val live: Boolean,
    private val hits: MutableList<Hit>?,
) {
    private val s = snap.settings
    private val regular: Typeface = ctx.resources.getFont(R.font.inter_regular)
    private val bold: Typeface = ctx.resources.getFont(R.font.inter_bold)
    private val extra: Typeface = ctx.resources.getFont(R.font.inter_extrabold)

    private val fw = skin.frameWidth.value
    private val lw = skin.lineWidth.value
    private val gap = 8f
    private val nowMillis = System.currentTimeMillis()

    // Abstand zu Status- und Navigationsleiste (nur Live-Hintergrund)
    private var insetTop = 0f
    private var insetBottom = 0f

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fadeP = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    private fun addHit(r: RectF, type: Int, key: Int = 0) {
        hits?.add(Hit(r.left, r.top, r.right, r.bottom, type, key))
    }

    /**
     * Welche Prioritäten angezeigt werden. Widgets folgen den Widget-Einstellungen (Anzahl, leere Felder),
     * Hauptseite, Export und Live-Hintergrund zeigen alle.
     */
    private fun prioIndexes(widget: Boolean): List<Int> {
        val total = s.prioritiesFor(snap.date)
        if (!widget) return (0 until total).toList()
        val first = (0 until s.widgetPriosFor(snap.date)).toList()
        if (s.widgetShowEmpty) return first
        val filled = first.filter { snap.day.priorities[it].orEmpty().isNotBlank() }
        return filled.ifEmpty { listOf(0) }
    }

    // ------------------------------------------------------------ Gesamtbild

    fun draw(kind: WidgetKind) {
        val layer = c.saveLayer(0f, 0f, w, h, null)
        background()

        val area = RectF(gap, gap, w - gap, h - gap)
        when (kind) {
            WidgetKind.PRIOS -> prios(area, prioIndexes(true))
            WidgetKind.BRAINDUMP -> braindump(area)
            WidgetKind.TIMEBOX -> timebox(area)
            WidgetKind.PAGE -> page(0)
            WidgetKind.PAGE_NOGEAR -> page(1)
        }

        // Runde Ecken
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val outside = Path().apply {
            fillType = Path.FillType.INVERSE_WINDING
            addRoundRect(RectF(0f, 0f, w, h), 16f, 16f, Path.Direction.CW)
        }
        c.drawPath(outside, clear)
        c.restoreToCount(layer)
    }

    /** Export-Seite: nur das Datum oben, keine runden Ecken. */
    fun drawExport() {
        background()
        page(2)
    }

    /** Live-Hintergrund: wie die Hauptseite, ohne Zahnrad, Schloss und Pfeile. */
    fun drawWallpaper(top: Float, bottom: Float) {
        insetTop = top
        insetBottom = bottom
        background()
        page(3)
    }

    /** mode: 0 = mit Zahnrad, 1 = ohne Zahnrad, 2 = Export (nur Datum), 3 = Live-Hintergrund (nur Datum). */
    private fun page(mode: Int) {
        val headerH = 52f
        val head = RectF(gap, gap + insetTop, w - gap, gap + insetTop + headerH)
        if (mode >= 2) {
            dateBox(RectF(head.left + 40f, head.top, head.right - 40f, head.bottom))
        } else {
            header(head, mode == 0)
        }

        val content = RectF(gap, head.bottom + gap, w - gap, h - gap - insetBottom)
        val leftW = (content.width() - gap) / (1f + 1.15f)
        val left = RectF(content.left, content.top, content.left + leftW, content.bottom)
        val right = RectF(left.right + gap, content.top, content.right, content.bottom)
        val titleH = 32f

        var y = left.top
        if (s.showPrioritiesTitle) {
            title(ctx.getString(R.string.priorities), RectF(left.left, y, left.right, y + titleH), 0)
            y += titleH + gap
        }
        val idx = prioIndexes(mode <= 1)
        val count = idx.size
        val prioH = min(count * 44f + (count - 1) * lw + 2 * fw, (left.bottom - y) * 0.5f)
        prios(RectF(left.left, y, left.right, y + prioH), idx)
        y += prioH + gap

        if (s.showBraindumpTitle) {
            title(ctx.getString(R.string.braindump), RectF(left.left, y, left.right, y + titleH), 1)
            y += titleH + gap
        }
        if (left.bottom - y > 2 * fw + 4f) braindump(RectF(left.left, y, left.right, left.bottom))

        var ry = right.top
        if (s.showTimeboxTitle) {
            title(ctx.getString(R.string.timebox), RectF(right.left, ry, right.right, ry + titleH), 2)
            ry += titleH + gap
        }
        timebox(RectF(right.left, ry, right.right, right.bottom))
    }

    // ------------------------------------------------------------ Hintergrund

    private fun background() {
        when (skin.design) {
            Design.CANDY -> {
                fillP.color = skin.bg.toArgb()
                c.drawRect(0f, 0f, w, h, fillP)
                fillP.color = 0xFF16081C.toInt()
                val u = 30f
                var r = 0
                while (r * u < h) {
                    var col = 0
                    while (col * u < w) {
                        if ((r + col) % 2 == 0) c.drawRect(col * u, r * u, (col + 1) * u, (r + 1) * u, fillP)
                        col++
                    }
                    r++
                }
            }
            Design.CHROME -> {
                fillP.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0xFF2E90DD.toInt(), 0xFF6C8FE6.toInt(), 0xFFA27AE4.toInt()),
                    null,
                    Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, w, h, fillP)
                fillP.shader = null
                fillP.color = 0x38FFFFFF
                fun puff(cx: Float, cy: Float, r: Float) {
                    c.drawCircle(w * cx, h * cy, r, fillP)
                    c.drawCircle(w * (cx - 0.08f), h * (cy + 0.015f), r * 0.8f, fillP)
                    c.drawCircle(w * (cx + 0.09f), h * (cy + 0.02f), r * 0.7f, fillP)
                }
                puff(0.88f, 0.06f, w * 0.15f)
                puff(0.80f, 0.50f, w * 0.20f)
                puff(0.10f, 0.85f, w * 0.22f)
                puff(0.85f, 0.95f, w * 0.18f)
            }
            else -> {
                val g = skin.gradient
                if (g != null) {
                    fillP.shader = LinearGradient(
                        0f, 0f, 0f, h,
                        g.map { it.toArgb() }.toIntArray(),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                    c.drawRect(0f, 0f, w, h, fillP)
                    fillP.shader = null
                } else {
                    fillP.color = skin.bg.toArgb()
                    c.drawRect(0f, 0f, w, h, fillP)
                }
            }
        }
        if (skin.sun) drawSun()
        if (skin.clouds) drawSoftClouds()
    }

    private fun drawSun() {
        val cx = w * 0.86f
        val cy = h * 0.07f
        val r = w * 0.075f
        iconP.color = 0xFFFFE9A0.toInt()
        iconP.strokeWidth = r * 0.28f
        for (i in 0 until 9) {
            val a = 2.0 * PI * i / 9
            c.drawLine(
                cx + (r * 1.25f * cos(a)).toFloat(), cy + (r * 1.25f * sin(a)).toFloat(),
                cx + (r * 1.8f * cos(a)).toFloat(), cy + (r * 1.8f * sin(a)).toFloat(),
                iconP,
            )
        }
        fillP.color = 0xFFFFE27A.toInt()
        c.drawCircle(cx, cy, r, fillP)
    }

    private fun drawSoftClouds() {
        fillP.color = 0x8CFFFFFF.toInt()
        fun puff(cx: Float, cy: Float, r: Float) {
            c.drawCircle(w * cx, h * cy, r, fillP)
            c.drawCircle(w * (cx - 0.09f), h * (cy + 0.012f), r * 0.8f, fillP)
            c.drawCircle(w * (cx + 0.10f), h * (cy + 0.016f), r * 0.75f, fillP)
            c.drawCircle(w * (cx + 0.02f), h * (cy - 0.018f), r * 0.6f, fillP)
        }
        puff(0.84f, 0.12f, w * 0.10f)
        puff(0.10f, 0.40f, w * 0.12f)
        puff(0.12f, 0.70f, w * 0.15f)
        puff(0.88f, 0.64f, w * 0.14f)
        puff(0.30f, 0.97f, w * 0.17f)
        puff(0.84f, 0.97f, w * 0.15f)
    }

    // ------------------------------------------------------------ Bausteine

    /** Schatten, Füllung, Rahmen (innen), bei Neon mit Leuchten. Gibt die Innenfläche zurück. */
    private fun frame(r: RectF, fill: Int, color: Int): RectF {
        val shadow = skin.shadowColor
        if (shadow != null && skin.shadowOffset.value > 0f) {
            val o = skin.shadowOffset.value
            fillP.color = shadow.toArgb()
            c.drawRect(r.left + o, r.top + o, r.right + o, r.bottom + o, fillP)
        }
        if (skin.glow) {
            strokeP.color = color
            strokeP.alpha = 71
            strokeP.strokeWidth = fw * 3f
            c.drawRect(r.left - fw, r.top - fw, r.right + fw, r.bottom + fw, strokeP)
        }
        if ((fill ushr 24) != 0) {
            fillP.color = fill
            c.drawRect(r, fillP)
        }
        strokeP.color = color
        strokeP.strokeWidth = fw
        c.drawRect(r.left + fw / 2f, r.top + fw / 2f, r.right - fw / 2f, r.bottom - fw / 2f, strokeP)
        return RectF(r.left + fw, r.top + fw, r.right - fw, r.bottom - fw)
    }

    private fun hLine(x1: Float, x2: Float, y: Float, color: Int) {
        fillP.color = color
        c.drawRect(x1, y, x2, y + lw, fillP)
    }

    private fun vLine(x: Float, y1: Float, y2: Float, color: Int) {
        fillP.color = color
        c.drawRect(x, y1, x + lw, y2, fillP)
    }

    private fun textPaint(tf: Typeface, size: Float, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf
        textSize = size
        this.color = color
    }

    private fun centerText(text: String, r: RectF, p: TextPaint) {
        val fm = p.fontMetrics
        c.drawText(text, r.centerX() - p.measureText(text) / 2f, r.centerY() - (fm.ascent + fm.descent) / 2f, p)
    }

    /** Titel über den Feldern. index: 0 Prioritäten, 1 Braindump, 2 Timebox. */
    private fun title(text: String, r: RectF, index: Int) {
        val label = if (skin.upperTitles) text.uppercase() else text
        val fill = (skin.titleFills.getOrNull(index) ?: skin.accent).toArgb()
        val inkColor = (skin.titleInk ?: skin.ink).toArgb()

        when (skin.titleStyle) {
            // Wolken-Etikett: Kapsel mit weichem Schatten
            1 -> {
                val p = textPaint(bold, 18f, inkColor)
                val pillW = p.measureText(label) + 36f
                val pill = RectF(r.centerX() - pillW / 2f, r.top, r.centerX() + pillW / 2f, r.bottom)
                val radius = r.height() / 2f
                fillP.color = 0x26000000
                c.drawRoundRect(RectF(pill.left, pill.top + 1.5f, pill.right, pill.bottom + 1.5f), radius, radius, fillP)
                fillP.color = fill
                c.drawRoundRect(pill, radius, radius, fillP)
                centerText(label, pill, p)
            }
            // Bunter Reiter mit Schattenstreifen, reicht bis an den Rahmen darunter
            2 -> {
                val tabLeft = r.left + 12f
                val tabWidth = 0.72f * (r.width() - 12f)
                val tab = RectF(tabLeft, r.top, tabLeft + tabWidth, r.bottom)
                fillP.color = skin.titleShadow?.toArgb() ?: 0x33000000
                c.drawRect(tab.right, r.top + 6f, tab.right + 8f, r.bottom + 8f, fillP)
                fillP.color = fill
                c.drawRect(tab.left, tab.top, tab.right, tab.bottom + 8f, fillP)
                centerText(label, tab, textPaint(bold, 18f, inkColor))
            }
            else -> {
                val up = skin.upperTitles
                val p = textPaint(bold, if (up) 18f else 22f, skin.ink.toArgb())
                if (up) p.letterSpacing = 0.5f / 18f
                centerText(label, r, p)
            }
        }
    }

    /** Zelltext: einzeilig mit nebeligem Ende oder (bei Textumbruch) mehrzeilig. */
    private fun cellText(text: String, r: RectF, color: Int, size: Float, struck: Boolean) {
        if (text.isEmpty()) return
        val p = textPaint(regular, size, color)
        p.isStrikeThruText = struck
        val padX = 6f
        c.save()
        c.clipRect(r)
        if (s.wrapText) {
            val width = (r.width() - 2 * padX).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            c.translate(r.left + padX, r.top + max(0f, (r.height() - layout.height) / 2f))
            layout.draw(c)
        } else {
            val fm = p.fontMetrics
            val base = r.centerY() - (fm.ascent + fm.descent) / 2f
            if (p.measureText(text) > r.width() - 2 * padX) {
                val l = c.saveLayer(r, null)
                c.drawText(text, r.left + padX, base, p)
                fadeP.shader = LinearGradient(
                    r.right - 28f, 0f, r.right, 0f,
                    0xFF000000.toInt(), 0, Shader.TileMode.CLAMP,
                )
                c.drawRect(r, fadeP)
                fadeP.shader = null
                c.restoreToCount(l)
            } else {
                c.drawText(text, r.left + padX, base, p)
            }
        }
        c.restore()
    }

    // ------------------------------------------------------------ Prioritäten

    private fun prios(r: RectF, idx: List<Int>) {
        val inner = frame(r, skin.prioFill.toArgb(), skin.frame.toArgb())
        val n = max(1, idx.size)
        val ch = (inner.height() - (n - 1) * lw) / n
        for (i in 0 until n) {
            val slot = idx.getOrElse(i) { i }
            val top = inner.top + i * (ch + lw)
            if (i > 0) hLine(inner.left, inner.right, top - lw, skin.line.toArgb())
            val cell = RectF(inner.left, top, inner.right, top + ch)
            cellText(
                snap.day.priorities[slot].orEmpty(),
                cell,
                skin.fieldInk.toArgb(),
                min(15f, ch * 0.55f),
                snap.day.strikeP.contains(slot),
            )
            addHit(cell, HitType.PRIO, slot)
        }
    }

    // ------------------------------------------------------------ Braindump

    private fun braindump(r: RectF) {
        val inner = frame(r, skin.brainFill.toArgb(), skin.frameBrain.toArgb())
        c.save()
        c.clipRect(inner)

        val text = snap.day.braindump
        if (text.isNotEmpty()) {
            val p = textPaint(regular, 15f, skin.fieldInk.toArgb())
            val width = (inner.width() - 16f).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width).build()
            c.save()
            c.translate(inner.left + 8f, inner.top + 8f)
            layout.draw(c)
            c.restore()
        }

        if (snap.day.strokes.isNotEmpty()) {
            val l = c.saveLayer(inner, null)
            c.translate(inner.left, inner.top)
            snap.day.strokes.forEach { drawStroke(it) }
            c.restoreToCount(l)
        }
        c.restore()
        addHit(inner, HitType.BRAIN)
    }

    private fun drawStroke(st: InkStroke) {
        val pts = st.points
        if (pts.size < 2) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = skin.fieldInk.toArgb()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = if (st.erase) 22f else 2.5f
            if (st.erase) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        if (pts.size < 4) {
            p.style = Paint.Style.FILL
            c.drawCircle(pts[0], pts[1], p.strokeWidth / 2f, p)
            return
        }
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        c.drawPath(path, p)
    }

    // ------------------------------------------------------------ Timebox

    private fun timebox(r: RectF) {
        val inner = frame(r, 0, skin.frame.toArgb())
        val rows = s.rows
        val cols = s.columns
        val rowH = (inner.height() - (rows - 1) * lw) / rows
        val hourFont = (rowH * 0.55f).coerceIn(6f, 17f)
        val cellFont = min(15f, rowH * 0.6f)
        val hourW = 40f
        val hl = skin.hourHighlight

        for (i in 0 until rows) {
            val top = inner.top + i * (rowH + lw)
            val hour = (s.dayStart + i) % 24
            val lineColor = skin.lineAt(i.toFloat() / rows).toArgb()
            if (i > 0) hLine(inner.left, inner.right, top - lw, lineColor)

            // Ausgrauen und Hervorheben gelten für Widgets und Live-Hintergrund, nicht für den Export.
            val state = if (live) rowState(snap.date, s.dayStart, i, nowMillis) else 1
            val gray = s.grayPast && state < 0
            val highlight = s.highlightNow && state == 0
            val rowRect = RectF(inner.left, top, inner.right, top + rowH)
            val layer = if (gray) c.saveLayerAlpha(rowRect, 102) else -1

            val hr = RectF(inner.left, top, inner.left + hourW, top + rowH)
            fillP.color = if (highlight && hl != null) hl.toArgb() else skin.hourFill.toArgb()
            c.drawRect(hr, fillP)
            centerText(hour.toString(), hr, textPaint(bold, hourFont, skin.hourInk.toArgb()))

            val cellW = (inner.right - hr.right - cols * lw) / cols
            var x = hr.right
            for (col in 0 until cols) {
                vLine(x, top, top + rowH, lineColor)
                val cr = RectF(x + lw, top, x + lw + cellW, top + rowH)
                fillP.color = skin.timeFill.toArgb()
                c.drawRect(cr, fillP)
                val key = hour * 2 + col
                cellText(
                    snap.day.timebox[key].orEmpty(),
                    cr,
                    skin.timeInk.toArgb(),
                    cellFont,
                    snap.day.strikeT.contains(key),
                )
                addHit(cr, HitType.TIME, key)
                x += lw + cellW
            }

            if (highlight && hl == null) {
                fillP.color = skin.accent.copy(alpha = 0.22f).toArgb()
                c.drawRect(rowRect, fillP)
            }
            if (layer >= 0) c.restoreToCount(layer)
        }
    }

    // ------------------------------------------------------------ Kopfzeile

    private fun header(r: RectF, withGear: Boolean) {
        val ink = skin.ink.toArgb()
        val slot = 44f

        var x = r.left
        if (s.showLeftArrow) {
            val sr = RectF(x, r.top, x + slot, r.bottom)
            chevron(sr, true, ink)
            addHit(sr, HitType.PREV)
            x += slot
        }
        if (withGear) {
            val sr = RectF(x, r.top, x + slot, r.bottom)
            gear(sr, ink)
            addHit(sr, HitType.GEAR)
            x += slot
        }
        val sideLeft = x - r.left

        // Rechts wie auf der Hauptseite: erst das Schloss, ganz außen der Rechtspfeil.
        var rx = r.right
        rx -= slot
        val nextRect = RectF(rx, r.top, rx + slot, r.bottom)
        chevron(nextRect, false, ink)
        addHit(nextRect, HitType.NEXT)
        if (s.showLock) {
            rx -= slot
            val sr = RectF(rx, r.top, rx + slot, r.bottom)
            lock(sr, ink)
            addHit(sr, HitType.LOCK)
        }
        val sideRight = r.right - rx

        // Datum immer mittig: links und rechts gleich viel Platz
        val side = max(sideLeft, sideRight)
        dateBox(RectF(r.left + side, r.top, r.right - side, r.bottom))
    }

    private fun dateBox(dateArea: RectF) {
        val chip = skin.chip
        val box = if (chip != null) {
            RectF(dateArea.left, dateArea.top + 3f, dateArea.right, dateArea.bottom - 3f)
        } else {
            dateArea
        }
        if (chip != null) {
            fillP.color = chip.toArgb()
            c.drawRect(box, fillP)
        }
        val color = (if (chip != null) skin.chipInk else skin.ink).toArgb()
        val text = formatDate(snap.date, s.dateFormat)
        var size = 34f
        val p = textPaint(extra, size, color)
        while (size > 12f && p.measureText(text) > box.width()) {
            size -= 1f
            p.textSize = size
        }
        c.save()
        c.clipRect(box)
        centerText(text, box, p)
        c.restore()
    }

    private fun chevron(slot: RectF, left: Boolean, color: Int) {
        val sz = 22f
        val ox = slot.centerX() - sz / 2f
        val oy = slot.centerY() - sz / 2f
        val path = Path()
        if (left) {
            path.moveTo(ox + sz * 0.72f, oy + sz * 0.12f)
            path.lineTo(ox + sz * 0.28f, oy + sz * 0.5f)
            path.lineTo(ox + sz * 0.72f, oy + sz * 0.88f)
        } else {
            path.moveTo(ox + sz * 0.28f, oy + sz * 0.12f)
            path.lineTo(ox + sz * 0.72f, oy + sz * 0.5f)
            path.lineTo(ox + sz * 0.28f, oy + sz * 0.88f)
        }
        iconP.color = color
        iconP.strokeWidth = sz * 0.24f
        c.drawPath(path, iconP)
    }

    private fun gear(slot: RectF, color: Int) {
        val sz = 30f
        val cx = slot.centerX()
        val cy = slot.centerY()
        val outer = sz / 2f * 0.94f
        val inner = outer * 0.78f
        val teeth = 8
        val step = 2.0 * PI / teeth
        val path = Path()
        var first = true
        for (i in 0 until teeth) {
            val a = i * step
            val pts = listOf(
                (a - step * 0.30) to inner,
                (a - step * 0.17) to outer,
                (a + step * 0.17) to outer,
                (a + step * 0.30) to inner,
            )
            for ((ang, rad) in pts) {
                val px = cx + (rad * cos(ang)).toFloat()
                val py = cy + (rad * sin(ang)).toFloat()
                if (first) {
                    path.moveTo(px, py)
                    first = false
                } else {
                    path.lineTo(px, py)
                }
            }
        }
        path.close()
        iconP.color = color
        iconP.strokeWidth = sz * 0.07f
        c.drawPath(path, iconP)
        c.drawCircle(cx, cy, outer * 0.34f, iconP)
    }

    private fun lock(slot: RectF, color: Int) {
        val sz = 30f
        val ox = slot.centerX() - sz / 2f
        val oy = slot.centerY() - sz / 2f
        val sw = sz * 0.09f
        val bodyTop = oy + sz * 0.45f

        iconP.color = color
        iconP.strokeWidth = sw
        c.drawRoundRect(
            RectF(ox + sz * 0.15f, bodyTop, ox + sz * 0.85f, bodyTop + sz * 0.45f),
            sz * 0.10f, sz * 0.10f, iconP,
        )
        fillP.color = color
        c.drawCircle(ox + sz * 0.5f, bodyTop + sz * 0.16f, sz * 0.06f, fillP)
        iconP.strokeWidth = sw * 0.9f
        c.drawLine(ox + sz * 0.5f, bodyTop + sz * 0.16f, ox + sz * 0.5f, bodyTop + sz * 0.29f, iconP)

        iconP.strokeWidth = sw
        val left = ox + sz * 0.28f
        val right = ox + sz * 0.72f
        val top = oy + sz * 0.06f
        val d = right - left
        val cy = top + d / 2f
        val rightEnd = if (s.lockActive) bodyTop else cy + (bodyTop - cy) * 0.3f
        val shackle = Path()
        shackle.moveTo(left, bodyTop)
        shackle.lineTo(left, cy)
        shackle.arcTo(left, top, right, top + d, 180f, 180f, false)
        shackle.lineTo(right, rightEnd)
        c.drawPath(shackle, iconP)
    }
}
