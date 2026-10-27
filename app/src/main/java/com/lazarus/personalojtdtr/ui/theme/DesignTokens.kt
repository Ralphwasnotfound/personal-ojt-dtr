package com.lazarus.personalojtdtr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Palette is centralized; screens consume semantic MaterialTheme roles.
internal object OjtPalette {
    val Maroon = Color(0xFF8B1717)
    val DarkMaroon = Color(0xFF5A1018)
    val Gold = Color(0xFFD4A43A)
    val Background = Color(0xFFF8F6F4)
    val Surface = Color.White
    val Text = Color(0xFF202020)
    val Secondary = Color(0xFF757575)
    // Slightly stronger secondary ink for small text on the warm background.
    val ReadableSecondary = Color(0xFF6B6266)
    val Divider = Color(0xFFE7E1DE)
    val NightBackground = Color(0xFF171517)
    val NightSurface = Color(0xFF252124)
    val NightSecondary = Color(0xFFB8AFB1)
    val NightAccent = Color(0xFFC94B50)
    val NightDivider = Color(0xFF40383C)
}

object AppSpacing {
    val Micro = 4.dp
    val Related = 8.dp
    val Compact = 12.dp
    val Page = 16.dp
    val Section = 24.dp
    val Major = 32.dp
    val Touch = 48.dp
}

// A single high-emphasis surface; supporting surfaces use Material semantic roles.
object AppRefinement {
    val HeroContainer = Color(0xFF5A1018)
    val HeroContent = Color.White
    val HeroSecondary = Color(0xFFF4DDDF)
    val HeroTrack = Color(0xFF99545D)
    val GoldHighlight = Color(0xFFD4A43A)
    val PanelRadius = 24.dp
    val ThumbnailRadius = 12.dp
    val PanelElevation = 1.dp
}

/** Shared semantic status ink for screens with recorded/completed information. */
object AppStatusColors {
    val Success = Color(0xFF2E7D32)
    val SuccessDark = Color(0xFF81C784)
}

// The live-camera exception is centralized; review and permission surfaces use app colors.
internal object CameraPalette {
    val Background = Color.Black
    val Surface = Color(0xFF151315)
    val Content = Color.White
    val Disabled = Color.Gray
    val DisabledRing = Color.DarkGray
    val ShutterRing = OjtPalette.Maroon
    val Accent = OjtPalette.Gold
}
