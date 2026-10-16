package com.aladdin.geniebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF99C6AB),
    onPrimary = Color(0xFF131615),
    primaryContainer = Color(0xFF293F32),
    onPrimaryContainer = Color(0xFFCBE3D4),
    secondary = Color(0xFFCBE3D4),
    onSecondary = Color(0xFF131615),
    background = Color(0xFF131615),
    onBackground = Color(0xFFF0F2F5),
    surface = Color(0xFF1D2320),
    onSurface = Color(0xFFF0F2F5),
    surfaceVariant = Color(0xFF29342E),
    onSurfaceVariant = Color(0xFFADB8B0),
    outline = Color(0xFF3B4840),
    secondaryContainer = Color(0xFF304A3A),
    onSecondaryContainer = Color(0xFFCBE3D4)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF34664C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EADB),
    onPrimaryContainer = Color(0xFF173C28),
    secondary = Color(0xFF486454),
    onSecondary = Color.White,
    background = Color(0xFFF5F7F3),
    onBackground = Color(0xFF1B241E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B241E),
    surfaceVariant = Color(0xFFE5ECE5),
    onSurfaceVariant = Color(0xFF526157),
    outline = Color(0xFFC3CFC5),
    secondaryContainer = Color(0xFFD4EADB),
    onSecondaryContainer = Color(0xFF173C28)
)

@Composable
fun AladdinGenieBoxTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
