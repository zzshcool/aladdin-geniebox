package com.aladdin.geniebox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val BgDark: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val CardDark: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface
val CardDarkFocused: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val AladdinGold: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val AladdinGoldLight: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextTertiary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
val DividerDark: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

val AccentRed = Color(0xFFDC4444)
val AccentGreen = Color(0xFF238653)
val AccentBlue = Color(0xFF3B82F6)
