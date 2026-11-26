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
    primary = CobaltLight,
    onPrimary = ObsidianBg,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = EmeraldMint,
    onSecondary = ObsidianBg,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = IndigoLight,
    onTertiary = ObsidianBg,
    tertiaryContainer = IndigoContainerDark,
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = ObsidianBg,
    onBackground = IceInk,
    surface = ObsidianSurface,
    onSurface = IceInk,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = IceMuted,
    outline = ObsidianOutline,
    error = Color(0xFFF87171),
    onError = ObsidianBg,
    errorContainer = CrimsonErrorDarkBg,
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = IndigoTertiary,
    onTertiary = Color.White,
    tertiaryContainer = IndigoContainerLight,
    onTertiaryContainer = Color(0xFF312E81),
    background = PorcelainBg,
    onBackground = SlateInk,
    surface = PorcelainSurface,
    onSurface = SlateInk,
    surfaceVariant = PorcelainSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = PorcelainOutline,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorLightBg,
    onErrorContainer = Color(0xFF7F1D1D)
)

val StrataShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun StrataSaaSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StrataTypography,
        shapes = StrataShapes,
        content = content
    )
}
