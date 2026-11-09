package com.example.dayplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Zeilen mit Trennlinien, deren Höhen pixelgenau verteilt werden.
 *
 * fillHeight = true: Die Zeilen füllen genau die verfügbare Höhe (Timebox).
 * fillHeight = false: Die Zeilen nehmen nur so viel Platz wie nötig, höchstens die verfügbare Höhe.
 * useNeeds = true (Textumbruch): Eine Zeile mit mehrzeiligem Text wächst auf Kosten der anderen,
 * die Gesamthöhe ändert sich nie. Reicht der Platz gar nicht, wird proportional verkleinert.
 */
@Composable
fun StableRows(
    rowCount: Int,
    lineColor: (Int) -> Color,
    lineWidth: Dp,
    minRow: Dp,
    useNeeds: Boolean,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
    row: @Composable (Int) -> Unit,
) {
    Layout(
        content = {
            for (i in 0 until rowCount) {
                if (i > 0) HLine(lineColor(i), lineWidth)
                row(i)
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val linePx = lineWidth.roundToPx().coerceAtLeast(1)
        val minPx = minRow.roundToPx()

        val rows = ArrayList<Measurable>()
        val lines = ArrayList<Measurable>()
        measurables.forEachIndexed { index, m -> if (index % 2 == 0) rows.add(m) else lines.add(m) }

        val bounded = constraints.hasBoundedHeight
        val avail = if (bounded) (constraints.maxHeight - linePx * lines.size).coerceAtLeast(0) else Int.MAX_VALUE / 4

        val lower = IntArray(rows.size) { i ->
            val need = if (useNeeds) rows[i].minIntrinsicHeight(width) else 0
            maxOf(minPx, need)
        }
        val total = if (fillHeight && bounded) avail else minOf(lower.sum(), avail)
        val heights = allocate(total, lower)

        val rowPlaceables = rows.mapIndexed { i, m -> m.measure(Constraints.fixed(width, heights[i])) }
        val linePlaceables = lines.map { it.measure(Constraints.fixed(width, linePx)) }
        val totalHeight = heights.sum() + linePx * lines.size

        layout(width, totalHeight) {
            var y = 0
            rowPlaceables.forEachIndexed { i, p ->
                if (i > 0) {
                    linePlaceables[i - 1].placeRelative(0, y)
                    y += linePx
                }
                p.placeRelative(0, y)
                y += heights[i]
            }
        }
    }
}

/** Verteilt [total] Pixel auf Zeilen mit Mindesthöhen [lower]. Die Summe stimmt immer exakt. */
private fun allocate(total: Int, lower: IntArray): IntArray {
    val n = lower.size
    if (n == 0) return IntArray(0)
    val sum = lower.sum()

    if (sum >= total) {
        // Zu wenig Platz: proportional verkleinern, Text wird dann abgeschnitten.
        if (sum == 0) return IntArray(n) { total / n }
        val out = IntArray(n) { (lower[it].toLong() * total / sum).toInt() }
        var rest = total - out.sum()
        var i = 0
        while (rest > 0) {
            out[i % n]++
            rest--
            i++
        }
        return out
    }

    // Genug Platz: Zeilen mit großem Bedarf behalten ihren Bedarf, der Rest wird gleich verteilt.
    val out = IntArray(n) { -1 }
    var remaining = total
    var free = n
    var changed = true
    while (changed && free > 0) {
        changed = false
        val share = remaining / free
        for (i in 0 until n) {
            if (out[i] < 0 && lower[i] > share) {
                out[i] = lower[i]
                remaining -= lower[i]
                free--
                changed = true
            }
        }
    }
    if (free > 0) {
        val share = remaining / free
        var extra = remaining - share * free
        for (i in 0 until n) {
            if (out[i] < 0) {
                out[i] = share + if (extra > 0) {
                    extra--
                    1
                } else {
                    0
                }
            }
        }
    }
    return out
}
