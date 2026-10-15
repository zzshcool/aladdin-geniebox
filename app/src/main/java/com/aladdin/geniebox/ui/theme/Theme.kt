package com.aladdin.geniebox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AladdinGold,
    onPrimary = BgDark,
    primaryContainer = AladdinGoldLight,
    onPrimaryContainer = BgDark,
    secondary = AladdinGoldLight,
    onSecondary = BgDark,
    background = BgDark,
    onBackground = TextPrimary,
    surface = CardDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDarkFocused,
    onSurfaceVariant = TextSecondary,
    outline = DividerDark
)

@Composable
fun AladdinGenieBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
