package com.musictuner.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC83D),
    onPrimary = Color(0xFF17110A),
    secondary = Color(0xFFFF8A3D),
    onSecondary = Color(0xFF17110A),
    background = Color(0xFF17110A),
    onBackground = Color(0xFFFFF4DC),
    surface = Color(0xFF241A0E),
    onSurface = Color(0xFFFFF4DC),
    onSurfaceVariant = Color(0xFFB79F78),
    outline = Color(0xFF4A3516)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD98E00),
    onPrimary = Color.White,
    secondary = Color(0xFFC2570C),
    onSecondary = Color.White,
    background = Color(0xFFFFF1CC),
    onBackground = Color(0xFF3A2600),
    surface = Color.White,
    onSurface = Color(0xFF3A2600),
    onSurfaceVariant = Color(0xFF8A6A2C),
    outline = Color(0xFFF0D58A)
)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
