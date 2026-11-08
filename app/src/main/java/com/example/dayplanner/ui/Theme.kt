package com.example.dayplanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** Hier später die endgültige Schriftart eintragen (siehe Hinweis zur Schriftart). */
val AppFont: FontFamily = FontFamily.SansSerif

@Composable
fun PlannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color.Black,
            onPrimary = Color.White,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
        )
    ) {
        ProvideTextStyle(TextStyle(fontFamily = AppFont, color = Color.Black)) {
            content()
        }
    }
}
