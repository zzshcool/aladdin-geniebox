package com.aladdin.geniebox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ConsoleFocus,
    onPrimary = ConsoleNight,
    primaryContainer = Color(0xFF1B3048),
    onPrimaryContainer = ConsoleText,
    secondary = Color(0xFFB1BDCC),
    onSecondary = ConsoleNight,
    background = ConsoleNight,
    onBackground = ConsoleText,
    surface = ConsolePanel,
    onSurface = ConsoleText,
    surfaceVariant = Color(0xFF172333),
    onSurfaceVariant = Color(0xFF9AA9BD),
    outline = Color(0xFF2C3B50),
    secondaryContainer = Color(0xFF1C2D43),
    onSecondaryContainer = ConsoleText
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF355978),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E5EF),
    onPrimaryContainer = Color(0xFF162D42),
    secondary = Color(0xFF4A6076),
    onSecondary = Color.White,
    background = Color(0xFFF0F3F6),
    onBackground = Color(0xFF172331),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172331),
    surfaceVariant = Color(0xFFE1E8EF),
    onSurfaceVariant = Color(0xFF4E6175),
    outline = Color(0xFFBAC8D6),
    secondaryContainer = Color(0xFFD8E5EF),
    onSecondaryContainer = Color(0xFF162D42)
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
        shapes = Shapes(extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp),
            medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(12.dp)),
        content = content
    )
}
