package com.example.dayplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dayplanner.data.Design

/** Alles, was ein Layout-Design ausmacht. */
@Immutable
class Skin(
    val design: Design,
    val dark: Boolean,
    val bg: Color,
    val ink: Color,          // Kopfzeile, Titel, Symbole
    val fieldInk: Color,     // Text in Prioritäten und Braindump
    val timeInk: Color,      // Text in den Timebox-Zellen
    val line: Color,
    val frame: Color,
    val frameWidth: Dp,
    val lineWidth: Dp,
    val accent: Color,
    val lineEnd: Color? = null,
    val frameBrain: Color = frame,
    val prioFill: Color = Color.Transparent,
    val brainFill: Color = Color.Transparent,
    val timeFill: Color = Color.Transparent,
    val hourFill: Color = Color.Transparent,
    val hourInk: Color = ink,
    val chip: Color? = null,
    val chipInk: Color = ink,
    val glow: Boolean = false,
    val upperTitles: Boolean = false,
) {
    fun lineAt(fraction: Float): Color =
        lineEnd?.let { lerp(line, it, fraction.coerceIn(0f, 1f)) } ?: line
}

fun Design.supportsMode(): Boolean =
    this == Design.STANDARD || this == Design.GRUNGE || this == Design.GOLD

private val Gold = Color(0xFFB8892B)
private val GoldLight = Color(0xFFEBCB7F)
private val Cream = Color(0xFFFFF8E6)

fun resolveSkin(design: Design, mode: Int, systemDark: Boolean): Skin {
    val dark = if (design.supportsMode()) {
        when (mode) {
            1 -> false
            2 -> true
            else -> systemDark
        }
    } else {
        design == Design.CANDY || design == Design.NEON
    }
    return when (design) {
        Design.STANDARD -> {
            val ink = if (dark) Color.White else Color.Black
            Skin(
                design, dark,
                bg = if (dark) Color.Black else Color.White,
                ink = ink, fieldInk = ink, timeInk = ink,
                line = ink, frame = ink,
                frameWidth = 1.dp, lineWidth = 1.dp, accent = ink,
            )
        }
        Design.GRUNGE -> {
            val ink = if (dark) Color.White else Color(0xFF111111)
            Skin(
                design, dark,
                bg = if (dark) Color(0xFF0B0B0B) else Color(0xFFFAFAF8),
                ink = ink, fieldInk = ink, timeInk = ink,
                line = ink, frame = ink,
                frameWidth = 4.dp, lineWidth = 2.dp, accent = ink,
                chip = ink,
                chipInk = if (dark) Color.Black else Color.White,
                upperTitles = true,
            )
        }
        Design.CANDY -> Skin(
            design, true,
            bg = Color(0xFF4A1355),
            ink = Color(0xFFEDE7F1),
            fieldInk = Color(0xFF220726),
            timeInk = Color(0xFF220726),
            line = Color(0xFF2B0A33),
            frame = Color(0xFFCFC8D8),
            frameWidth = 3.dp, lineWidth = 2.dp,
            accent = Color(0xFFF06EC3),
            prioFill = Color(0xFFF58AD0),
            brainFill = Color(0xFFF9A9DE),
            timeFill = Color(0xFFF8A6DC),
            hourFill = Color(0xFF3A0D44),
            hourInk = Color.White,
            chip = Color(0xFF34093D),
            chipInk = Color(0xFFEDE7F1),
            upperTitles = true,
        )
        Design.CHROME -> Skin(
            design, false,
            bg = Color(0xFF6A8BE3),
            ink = Color(0xFFF4F7FD),
            fieldInk = Color(0xFF0B1B4A),
            timeInk = Color(0xFF0B1B4A),
            line = Color(0xFFD8E1F3),
            frame = Color(0xFFC3CEE3),
            frameWidth = 2.dp, lineWidth = 1.5.dp,
            accent = Color(0xFFF4F7FD),
            prioFill = Color(0x40FFFFFF),
            brainFill = Color(0x33FFFFFF),
            timeFill = Color(0x1FFFFFFF),
            hourFill = Color(0x2EFFFFFF),
            chip = Color(0x33FFFFFF),
            upperTitles = true,
        )
        Design.GOLD -> if (dark) {
            Skin(
                design, true,
                bg = Color(0xFF050505),
                ink = GoldLight,
                fieldInk = Color(0xFF2A1F06),
                timeInk = GoldLight,
                line = Gold, frame = Gold,
                frameWidth = 3.dp, lineWidth = 1.5.dp,
                accent = GoldLight,
                prioFill = Cream, brainFill = Cream,
                chip = Color(0xFF141414), chipInk = GoldLight,
                upperTitles = true,
            )
        } else {
            Skin(
                design, false,
                bg = Cream,
                ink = Color(0xFF6B4E0F),
                fieldInk = Color(0xFF2A1F06),
                timeInk = Color(0xFF2A1F06),
                line = Gold, frame = Gold,
                frameWidth = 3.dp, lineWidth = 1.5.dp,
                accent = Gold,
                prioFill = Color(0xFFFFFDF4), brainFill = Color(0xFFFFFDF4),
                upperTitles = true,
            )
        }
        Design.NEON -> Skin(
            design, true,
            bg = Color(0xFF060818),
            ink = Color(0xFF19E6F7),
            fieldInk = Color(0xFFD7FCFF),
            timeInk = Color(0xFFD7FCFF),
            line = Color(0xFF19E6F7),
            lineEnd = Color(0xFFFF2BD6),
            frame = Color(0xFF19E6F7),
            frameBrain = Color(0xFFFF2BD6),
            frameWidth = 2.dp, lineWidth = 1.5.dp,
            accent = Color(0xFFFF2BD6),
            prioFill = Color(0xFF0A1030),
            brainFill = Color(0xFF0A1030),
            timeFill = Color(0xFF070B24),
            chip = Color(0xFF0C1437),
            glow = true,
            upperTitles = true,
        )
    }
}

val LocalSkin = staticCompositionLocalOf { resolveSkin(Design.STANDARD, 0, false) }

/** Hintergrund des Designs (Karo, Himmel, einfarbig). */
@Composable
fun SkinBackground(skin: Skin, modifier: Modifier = Modifier, unit: Dp = 36.dp) {
    Canvas(modifier) {
        when (skin.design) {
            Design.CANDY -> {
                drawRect(skin.bg)
                val u = unit.toPx()
                val checker = Color(0xFF16081C)
                val cols = (size.width / u).toInt() + 1
                val rows = (size.height / u).toInt() + 1
                for (r in 0..rows) for (c in 0..cols) {
                    if ((r + c) % 2 == 0) drawRect(checker, Offset(c * u, r * u), Size(u, u))
                }
            }
            Design.CHROME -> {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2E90DD), Color(0xFF6C8FE6), Color(0xFFA27AE4))
                    )
                )
                val cloud = Color.White.copy(alpha = 0.22f)
                fun puff(cx: Float, cy: Float, r: Float) {
                    drawCircle(cloud, r, Offset(size.width * cx, size.height * cy))
                    drawCircle(cloud, r * 0.8f, Offset(size.width * (cx - 0.08f), size.height * (cy + 0.015f)))
                    drawCircle(cloud, r * 0.7f, Offset(size.width * (cx + 0.09f), size.height * (cy + 0.02f)))
                }
                puff(0.88f, 0.06f, size.width * 0.15f)
                puff(0.80f, 0.50f, size.width * 0.20f)
                puff(0.10f, 0.85f, size.width * 0.22f)
                puff(0.85f, 0.95f, size.width * 0.18f)
            }
            else -> drawRect(skin.bg)
        }
    }
}

/** Rahmen des Designs: Füllung, Außenlinie, bei Neon mit Leuchten. */
fun Modifier.skinFrame(skin: Skin, fill: Color, color: Color = skin.frame): Modifier {
    var m = this
    if (skin.glow) {
        m = m.drawBehind {
            val fw = skin.frameWidth.toPx()
            drawRect(
                color.copy(alpha = 0.28f),
                topLeft = Offset(-fw, -fw),
                size = Size(size.width + 2 * fw, size.height + 2 * fw),
                style = Stroke(width = fw * 3f),
            )
        }
    }
    return m
        .background(fill)
        .border(skin.frameWidth, color)
        .padding(skin.frameWidth)
}
