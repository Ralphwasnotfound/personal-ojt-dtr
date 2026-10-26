package com.lazarus.personalojtdtr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = OjtPalette.Maroon, onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E7E4), onPrimaryContainer = OjtPalette.DarkMaroon,
    secondary = OjtPalette.Secondary, onSecondary = Color.White,
    secondaryContainer = OjtPalette.Divider, onSecondaryContainer = OjtPalette.Text,
    tertiary = OjtPalette.DarkMaroon, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5EDD8), onTertiaryContainer = OjtPalette.DarkMaroon,
    background = OjtPalette.Background, onBackground = OjtPalette.Text,
    surface = OjtPalette.Surface, onSurface = OjtPalette.Text,
    surfaceVariant = OjtPalette.Background, onSurfaceVariant = OjtPalette.ReadableSecondary,
    outline = OjtPalette.Secondary, outlineVariant = OjtPalette.Divider,
    inverseSurface = OjtPalette.NightSurface, inverseOnSurface = Color.White, inversePrimary = OjtPalette.Gold,
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = OjtPalette.Surface, surfaceContainerLow = OjtPalette.Background,
    surfaceContainer = OjtPalette.Surface, surfaceContainerHigh = OjtPalette.Surface,
    surfaceContainerHighest = OjtPalette.Background,
)
private val DarkColors = darkColorScheme(
    primary = OjtPalette.NightAccent, onPrimary = Color.White,
    primaryContainer = OjtPalette.DarkMaroon, onPrimaryContainer = Color(0xFFE0787E),
    secondary = OjtPalette.NightSecondary, onSecondary = OjtPalette.NightBackground,
    secondaryContainer = OjtPalette.NightDivider, onSecondaryContainer = Color.White,
    tertiary = OjtPalette.Gold, onTertiary = OjtPalette.NightBackground,
    tertiaryContainer = OjtPalette.NightSurface, onTertiaryContainer = OjtPalette.NightSecondary,
    background = OjtPalette.NightBackground, onBackground = Color.White,
    surface = OjtPalette.NightSurface, onSurface = Color.White,
    surfaceVariant = OjtPalette.NightSurface, onSurfaceVariant = OjtPalette.NightSecondary,
    outline = OjtPalette.NightSecondary, outlineVariant = OjtPalette.NightDivider,
    inverseSurface = OjtPalette.Background, inverseOnSurface = OjtPalette.Text, inversePrimary = OjtPalette.Maroon,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = OjtPalette.NightBackground, surfaceContainerLow = OjtPalette.NightSurface,
    surfaceContainer = OjtPalette.NightSurface, surfaceContainerHigh = OjtPalette.NightSurface,
    surfaceContainerHighest = OjtPalette.NightDivider,
)
private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
)
private val AppShapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp))

val LocalOjtDarkTheme = staticCompositionLocalOf { false }

@Composable fun OjtTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(LocalOjtDarkTheme provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography, shapes = AppShapes, content = content)
    }
}
