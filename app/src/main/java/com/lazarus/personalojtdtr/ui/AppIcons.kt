package com.lazarus.personalojtdtr.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp

// Small code-native stroke icons: no large icon dependency or bitmap assets.
enum class AppSymbol(private val path: String) {
    HOME("M3,11 L12,3 L21,11 M5,10 L5,21 L10,21 L10,15 L14,15 L14,21 L19,21 L19,10"),
    ATTENDANCE("M12,3 A9,9 0,1 1,11.99 3 M12,7 L12,12 L16,14"),
    ACTIVITIES("M5,3 L19,3 L19,21 L5,21 Z M9,7 L16,7 M9,11 L16,11 M9,15 L14,15"),
    HISTORY("M4,5 L4,10 L9,10 M4,10 C4,0 21,0 21,12 C21,22 8,25 4,17 M12,7 L12,12 L16,14"),
    PROFILE("M16,7 A4,4 0,1 1,8 7 A4,4 0,1 1,16 7 M4,22 C4,12 20,12 20,22"),
    ADD("M12,4 L12,20 M4,12 L20,12"), EDIT("M4,17 L17,4 L21,8 L8,21 L4,21 Z M14,7 L18,11"),
    DELETE("M4,6 L20,6 M9,6 L9,3 L15,3 L15,6 M6,6 L7,21 L17,21 L18,6 M10,10 L10,17 M14,10 L14,17"),
    SHARE("M7,12 L17,5 M7,12 L17,19 M9,12 A3,3 0,1 1,3 12 A3,3 0,1 1,9 12 M21,4 A3,3 0,1 1,15 4 A3,3 0,1 1,21 4 M21,20 A3,3 0,1 1,15 20 A3,3 0,1 1,21 20"),
    PHOTO("M3,3 L21,3 L21,21 L3,21 Z M3,17 L9,11 L13,15 L16,12 L21,17 M17,7 L17,7.1"),
    CAMERA("M3,7 L7,7 L9,4 L15,4 L17,7 L21,7 L21,21 L3,21 Z M17,14 A5,5 0,1 1,7 14 A5,5 0,1 1,17 14"),
    BACK("M15,4 L7,12 L15,20"), CLOSE("M5,5 L19,19 M19,5 L5,19"),
    LEFT("M15,5 L8,12 L15,19"), RIGHT("M9,5 L16,12 L9,19");
    val vector: ImageVector by lazy { ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build() }
}
@Composable fun AppIcon(symbol: AppSymbol, description: String? = null, modifier: Modifier = Modifier) { Icon(symbol.vector, description, modifier) }
