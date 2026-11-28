package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The app always uses the black and gold look, whatever the phone's theme is.
private val BlackGoldColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = MatteBlack,
    primaryContainer = DeepGold,
    onPrimaryContainer = ChampagneGold,
    inversePrimary = BorderGold,
    secondary = ChampagneGold,
    onSecondary = MatteBlack,
    secondaryContainer = DeepGold,
    onSecondaryContainer = ChampagneGold,
    tertiary = ChampagneGold,
    onTertiary = MatteBlack,
    tertiaryContainer = DeepGold,
    onTertiaryContainer = ChampagneGold,
    background = MatteBlack,
    onBackground = PureWhite,
    surface = MatteBlack,
    onSurface = PureWhite,
    surfaceVariant = BlackRaised,
    onSurfaceVariant = SoftWhite,
    surfaceTint = RoyalGold,
    inverseSurface = PureWhite,
    inverseOnSurface = MatteBlack,
    surfaceDim = MatteBlack,
    surfaceBright = Color(0xFF2A2A2A),
    surfaceContainerLowest = MatteBlack,
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = BlackSurface,
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = BlackRaised,
    outline = BorderGold,
    outlineVariant = DeepGold,
    error = Color(0xFFF87171),
    onError = MatteBlack,
    errorContainer = Color(0xFF5C1616),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000)
)

val RaabtaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun RaabtaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlackGoldColorScheme,
        typography = AppTypography,
        shapes = RaabtaShapes,
        content = content
    )
}
