package com.radaralert.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF5252),       // Rouge radar
    onPrimary = Color.White,
    secondary = Color(0xFFFFAB40),     // Orange alerte
    onSecondary = Color.Black,
    background = Color(0xFF121212),    // Fond sombre (conduite de nuit)
    surface = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFCF6679)
)

@Composable
fun RadarAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
