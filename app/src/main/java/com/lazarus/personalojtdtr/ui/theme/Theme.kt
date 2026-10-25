package com.lazarus.personalojtdtr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF125C52), secondary = Color(0xFF52645E),
    tertiary = Color(0xFF946200), background = Color(0xFFF6FAF7), surface = Color(0xFFF6FAF7),
)
private val DarkColors = darkColorScheme(primary = Color(0xFF91D5C7), secondary = Color(0xFFB5CCC3))
@Composable fun OjtTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
