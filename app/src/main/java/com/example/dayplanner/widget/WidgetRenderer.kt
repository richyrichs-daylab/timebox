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
import com.example.dayplanner.ui.resolveSkin
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class WidgetSnapshot(val settings: Settings, val day: DayData, val date: LocalDate)

object WidgetRenderer {

    suspend fun snapshot(context: Context): WidgetSnapshot {
        val app = context.applicationContext
        val date = LocalDate.now()
        return WidgetSnapshot(SettingsRepository(app).current(), DayRepository(app).load(date), date)
    }

    /** Zeichnet das Widget im App-Design. Alle Maße sind in dp, die Canvas wird skaliert. */
    fun render(ctx: Context, kind: WidgetKind, wDp: Float, hDp: Float, snap: WidgetSnapshot): Bitmap {
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

        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val skin = resolveSkin(snap.settings.designId, snap.settings.themeMode, night)
        Painter(ctx, skin, snap, canvas, wDp, hDp).draw(kind)
        return bitmap
    }
}

private class Painter(
    private val ctx: Context,
    private val skin: Skin,
    private val snap: WidgetSnapshot,
    private val c: Canvas,
    private val w: Float,
    private val h: Float,
) {
    private val s = snap.settings
    private val regular: Typeface = ctx.resources.getFont(R.font.inter_regular)
    private val bold: Typeface = ctx.resources.getFont(R.font.inter_bold)
    private val extra: Typeface = ctx.resources.getFont(R.font.inter_extrabold)

    private val fw = skin.frameWidth.value
    private val lw = skin.lineWidth.value
    private val gap = 8f

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fadeP = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    // ------------------------------------------------------------ Gesamtbild

    fun draw(kind: WidgetKind) {
        val layer = c.saveLayer(0f, 0f, w, h, null)
        background()

        val area = RectF(gap, gap, w - gap, h - gap)
        when (kind) {
            WidgetKind.PRIOS -> prios(area, min(3, s.prioritiesFor(snap.date)))
            WidgetKind.BRAINDUMP -> braindump(area)
            WidgetKind.TIMEBOX -> timebox(area)
            WidgetKind.PAGE -> page(true)
            WidgetKind.PAGE_NOGEAR -> page(false)
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

    private fun page(withGear: Boolean) {
        val headerH = 52f
        val head = RectF(gap, gap, w - gap, gap + headerH)
        header(head, withGear)

        val content = RectF(gap, head.bottom + gap, w - gap, h - gap)
        val leftW = (content.width() - gap) / (1f + 1.15f)
        val left = RectF(content.left, content.top, content.left + leftW, content.bottom)
        val right = RectF(left.right + gap, content.top, content.right, content.bottom)
        val titleH = 32f

        var y = left.top
        if (s.showPrioritiesTitle) {
            title(ctx.getString(R.string.priorities), RectF(left.left, y, left.right, y + titleH))
            y += titleH + gap
        }
        val count = s.prioritiesFor(snap.date)
        val prioH = min(count * 44f + (count - 1) * lw + 2 * fw, (left.bottom - y) * 0.5f)
        prios(RectF(left.left, y, left.right, y + prioH), count)
        y += prioH + gap

        if (s.showBraindumpTitle) {
            title(ctx.getString(R.string.braindump), RectF(left.left, y, left.right, y + titleH))
            y += titleH + gap
        }
        if (left.bottom - y > 2 * fw + 4f) braindump(RectF(left.left, y, left.right, left.bottom))

        var ry = right.top
        if (s.showTimeboxTitle) {
            title(ctx.getString(R.string.timebox), RectF(right.left, ry, right.right, ry + titleH))
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
                fillP.color = skin.bg.toArgb()
                c.drawRect(0f, 0f, w, h, fillP)
            }
        }
    }

    // ------------------------------------------------------------ Bausteine

    /** Füllung, Rahmen (innen), bei Neon mit Leuchten. Gibt die Innenfläche zurück. */
    private fun frame(r: RectF, fill: Int, color: Int): RectF {
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

    private fun title(text: String, r: RectF) {
        val up = skin.upperTitles
        val p = textPaint(bold, if (up) 18f else 22f, skin.ink.toArgb())
        if (up) p.letterSpacing = 0.5f / 18f
        centerText(if (up) text.uppercase() else text, r, p)
    }

    /** Zelltext: einzeilig mit nebeligem Ende oder (bei Textumbruch) mehrzeilig. */
    private fun cellText(text: String, r: RectF, color: Int, size: Float) {
        if (text.isEmpty()) return
        val p = textPaint(regular, size, color)
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

    private fun prios(r: RectF, count: Int) {
        val inner = frame(r, skin.prioFill.toArgb(), skin.frame.toArgb())
        val n = max(1, count)
        val ch = (inner.height() - (n - 1) * lw) / n
        for (i in 0 until n) {
            val top = inner.top + i * (ch + lw)
            if (i > 0) hLine(inner.left, inner.right, top - lw, skin.line.toArgb())
            cellText(
                snap.day.priorities[i].orEmpty(),
                RectF(inner.left, top, inner.right, top + ch),
                skin.fieldInk.toArgb(),
                min(15f, ch * 0.55f),
            )
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

        for (i in 0 until rows) {
            val top = inner.top + i * (rowH + lw)
            val hour = (s.dayStart + i) % 24
            val lineColor = skin.lineAt(i.toFloat() / rows).toArgb()
            if (i > 0) hLine(inner.left, inner.right, top - lw, lineColor)

            val hr = RectF(inner.left, top, inner.left + hourW, top + rowH)
            fillP.color = skin.hourFill.toArgb()
            c.drawRect(hr, fillP)
            centerText(hour.toString(), hr, textPaint(bold, hourFont, skin.hourInk.toArgb()))

            val cellW = (inner.right - hr.right - cols * lw) / cols
            var x = hr.right
            for (col in 0 until cols) {
                vLine(x, top, top + rowH, lineColor)
                val cr = RectF(x + lw, top, x + lw + cellW, top + rowH)
                fillP.color = skin.timeFill.toArgb()
                c.drawRect(cr, fillP)
                cellText(
                    snap.day.timebox[hour * 2 + col].orEmpty(),
                    cr,
                    skin.timeInk.toArgb(),
                    cellFont,
                )
                x += lw + cellW
            }
        }
    }

    // ------------------------------------------------------------ Kopfzeile

    private fun header(r: RectF, withGear: Boolean) {
        val ink = skin.ink.toArgb()
        val slot = 44f

        var x = r.left
        if (s.showLeftArrow) {
            chevron(RectF(x, r.top, x + slot, r.bottom), true, ink)
            x += slot
        }
        if (withGear) {
            gear(RectF(x, r.top, x + slot, r.bottom), ink)
            x += slot
        }
        val sideLeft = x - r.left

        var rx = r.right
        if (s.showLock) {
            rx -= slot
            lock(RectF(rx, r.top, rx + slot, r.bottom), ink)
        }
        rx -= slot
        chevron(RectF(rx, r.top, rx + slot, r.bottom), false, ink)
        val sideRight = r.right - rx

        // Datum immer mittig: links und rechts gleich viel Platz
        val side = max(sideLeft, sideRight)
        val dateArea = RectF(r.left + side, r.top, r.right - side, r.bottom)
        val chip = skin.chip
        val box = if (chip != null) RectF(dateArea.left, dateArea.top + 3f, dateArea.right, dateArea.bottom - 3f) else dateArea
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
