package com.example.dayplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Chevron(left: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (left) {
                moveTo(w * 0.72f, h * 0.12f); lineTo(w * 0.28f, h * 0.5f); lineTo(w * 0.72f, h * 0.88f)
            } else {
                moveTo(w * 0.28f, h * 0.12f); lineTo(w * 0.72f, h * 0.5f); lineTo(w * 0.28f, h * 0.88f)
            }
        }
        drawPath(path, color, style = Stroke(width = w * 0.24f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2f * 0.94f
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
                val x = c.x + (rad * cos(ang)).toFloat()
                val y = c.y + (rad * sin(ang)).toFloat()
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            }
        }
        path.close()
        val sw = size.minDimension * 0.07f
        drawPath(path, color, style = Stroke(width = sw, join = StrokeJoin.Round))
        drawCircle(color, radius = outer * 0.34f, style = Stroke(width = sw))
    }
}

@Composable
fun LockIcon(locked: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        val bodyTop = h * 0.45f

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.15f, bodyTop),
            size = Size(w * 0.70f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.10f),
            style = Stroke(width = sw),
        )
        drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.5f, bodyTop + h * 0.16f))
        drawLine(
            color, Offset(w * 0.5f, bodyTop + h * 0.16f), Offset(w * 0.5f, bodyTop + h * 0.29f),
            strokeWidth = sw * 0.9f, cap = StrokeCap.Round,
        )

        val left = w * 0.28f
        val right = w * 0.72f
        val top = h * 0.06f
        val d = right - left
        val cy = top + d / 2f
        val rightEnd = if (locked) bodyTop else cy + (bodyTop - cy) * 0.3f
        val shackle = Path().apply {
            moveTo(left, bodyTop)
            lineTo(left, cy)
            arcTo(Rect(left, top, right, top + d), 180f, 180f, false)
            lineTo(right, rightEnd)
        }
        drawPath(shackle, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
