package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF002117),
    primaryContainer = EmeraldDark,
    onPrimaryContainer = MintSurface,
    secondary = GoldAmber,
    onSecondary = Color(0xFF281700),
    secondaryContainer = Color(0xFF452B00),
    onSecondaryContainer = GoldLight,
    tertiary = Color(0xFF4FD1C5),
    background = DeepSlate,
    onBackground = Color(0xFFEAF2EF),
    surface = Color(0xFF15201E),
    onSurface = Color(0xFFEAF2EF),
    surfaceVariant = Color(0xFF1E2D2A),
    onSurfaceVariant = Color(0xFF9EB5AF),
    error = Color(0xFFF87171)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = MintSurface,
    onPrimaryContainer = EmeraldDark,
    secondary = GoldAmber,
    onSecondary = Color.White,
    secondaryContainer = GoldLight,
    onSecondaryContainer = Color(0xFF5A3700),
    tertiary = EmeraldLight,
    background = SoftGrayBg,
    onBackground = DeepSlate,
    surface = CardWhite,
    onSurface = DeepSlate,
    surfaceVariant = Color(0xFFEBF1EF),
    onSurfaceVariant = Color(0xFF4B5E59),
    error = ErrorCrimson
)

val RaabtaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun RaabtaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        shapes = RaabtaShapes,
        content = content
    )
}
