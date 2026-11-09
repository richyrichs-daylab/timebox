package com.example.dayplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dicker, minimalistischer Stift. Die Spitze zeigt nach links unten. */
@Composable
fun PenIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.96f, h * 0.20f)
            lineTo(w * 0.46f, h * 0.70f)
            lineTo(w * 0.08f, h * 0.92f)
            lineTo(w * 0.30f, h * 0.54f)
            lineTo(w * 0.80f, h * 0.04f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun EraserIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        rotate(-45f, Offset(w / 2f, h / 2f)) {
            val topLeft = Offset(w * 0.12f, h * 0.32f)
            val body = Size(w * 0.76f, h * 0.36f)
            val radius = CornerRadius(w * 0.07f)
            drawRoundRect(color, topLeft, body, radius, style = Stroke(w * 0.09f))
            drawRoundRect(color, topLeft, Size(body.width * 0.42f, body.height), radius)
        }
    }
}

/** Teilen-Pfeil: gebogener Pfeil nach rechts (Umriss). */
@Composable
fun ShareIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.58f, h * 0.10f)
            lineTo(w * 0.95f, h * 0.43f)
            lineTo(w * 0.58f, h * 0.76f)
            lineTo(w * 0.58f, h * 0.60f)
            cubicTo(w * 0.30f, h * 0.58f, w * 0.12f, h * 0.70f, w * 0.06f, h * 0.92f)
            cubicTo(w * 0.04f, h * 0.50f, w * 0.22f, h * 0.30f, w * 0.58f, h * 0.28f)
            close()
        }
        drawPath(
            p, color,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Runder Reset-Pfeil. */
@Composable
fun ResetIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = size.minDimension * 0.09f
        val c = center
        val r = size.minDimension * 0.34f
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        // Pfeilspitze am Ende des Bogens (240°), Richtung = Tangente im Uhrzeigersinn
        val th = Math.toRadians(240.0)
        val ex = c.x + (r * cos(th)).toFloat()
        val ey = c.y + (r * sin(th)).toFloat()
        val tx = (-sin(th)).toFloat()
        val ty = cos(th).toFloat()
        val tip = Offset(ex + tx * sw, ey + ty * sw)
        val len = r * 0.55f
        for (deg in listOf(150.0, -150.0)) {
            val a = Math.toRadians(deg)
            val dx = (tx * cos(a) - ty * sin(a)).toFloat()
            val dy = (tx * sin(a) + ty * cos(a)).toFloat()
            drawLine(color, tip, Offset(tip.x + dx * len, tip.y + dy * len), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

/** 0 = Standard (Halbkreis), 1 = hell (Sonne), 2 = dunkel (Mond). */
@Composable
fun ModeIcon(mode: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension * 0.30f
        val sw = size.minDimension * 0.08f
        when (mode) {
            1 -> {
                drawCircle(color, r * 0.8f)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    val s1 = r * 1.15f
                    val s2 = r * 1.55f
                    drawLine(
                        color,
                        Offset(c.x + (s1 * cos(a)).toFloat(), c.y + (s1 * sin(a)).toFloat()),
                        Offset(c.x + (s2 * cos(a)).toFloat(), c.y + (s2 * sin(a)).toFloat()),
                        strokeWidth = sw,
                        cap = StrokeCap.Round,
                    )
                }
            }
            2 -> {
                val outer = Path().apply { addOval(Rect(c, r * 1.2f)) }
                val cut = Path().apply { addOval(Rect(Offset(c.x + r * 0.7f, c.y - r * 0.4f), r)) }
                drawPath(Path.combine(PathOperation.Difference, outer, cut), color)
            }
            else -> {
                drawCircle(color, r * 1.2f, style = Stroke(sw))
                drawArc(
                    color, -90f, 180f, true,
                    Offset(c.x - r * 1.2f, c.y - r * 1.2f),
                    Size(r * 2.4f, r * 2.4f),
                )
            }
        }
    }
}
