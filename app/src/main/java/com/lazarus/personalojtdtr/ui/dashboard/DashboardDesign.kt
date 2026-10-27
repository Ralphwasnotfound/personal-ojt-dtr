package com.lazarus.personalojtdtr.ui.dashboard

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

/** Scoped contrast-safe brand ink; the app-wide theme and other screens remain unchanged. */
@Composable fun dashboardAccent(): Color = if (com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme.current)
    Color(0xFFF0B1B7) else androidx.compose.material3.MaterialTheme.colorScheme.primary

enum class DashboardGlyph(private val path: String, private val filled: Boolean = false) {
    PROGRESS("M12,3 A9,9 0,1 1,11.99 3 M12,3 L12,8 L15,5"),
    COMPLETED("M21,12 A9,9 0,1 1,3 12 A9,9 0,1 1,21 12 M7,12 L11,16 L17,9"),
    REQUIRED("M3,2 L21,2 L21,22 L3,22 Z M7,2 L7,14 L11,11 L15,14 L15,2"),
    REMAINING("M5,2 L19,2 M5,22 L19,22 M7,2 L7,7 L17,17 L17,22 M17,2 L17,7 L7,17 L7,22 M8,6 L16,6 M8,18 L16,18"),
    CLOCK("M21,12 A9,9 0,1 1,3 12 A9,9 0,1 1,21 12 M12,6 L12,12 L16,12"),
    CALENDAR("M3,5 L21,5 L21,22 L3,22 Z M7,2 L7,8 M17,2 L17,8 M3,10 L21,10 M7,15 L10,18 L16,12"),
    CAMERA("M7,6 L9,3 L15,3 L17,6 L20,6 Q22,6 22,8 L22,20 Q22,22 20,22 L4,22 Q2,22 2,20 L2,8 Q2,6 4,6 Z M17,14 A5,5 0,1 1,7 14 A5,5 0,1 1,17 14"),
    JOURNAL("M3,2 L21,2 L21,22 L3,22 Z M7,6 L17,6 M7,10 L17,10 M7,14 L13,14 M7,18 L12,18 M17,15 L21,15 L21,22 L17,22 Z"),
    ACTIVITIES("M4,3 L20,3 L20,21 L4,21 Z M8,7 L16,7 M8,11 L16,11 M8,15 L16,15"),
    HOME("M3,11 L12,3 L21,11 M6,10 L6,21 L18,21 L18,10"),
    HISTORY("M4,3 L4,9 L10,9 M4,9 A9,9 0,1 1,3,15 M12,6 L12,12 L16,14"),
    PROFILE("M17,7 A5,4 0,1 1,7 7 A5,4 0,1 1,17 7 M3,23 C3,12 21,12 21,23"),
    ARROW("M3,10 L14,10 L14,4 L22,12 L14,20 L14,14 L3,14 Z", true),
    NEXT("M9,5 L16,12 L9,19");

    val vector by lazy { ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(path).toNodes(),
        fill = if (filled) SolidColor(Color.White) else null,
        stroke = if (filled) null else SolidColor(Color.White), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build() }
}

@Composable fun DashboardIcon(glyph: DashboardGlyph, modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Icon(glyph.vector, contentDescription = null, modifier = modifier, tint = tint)
}
