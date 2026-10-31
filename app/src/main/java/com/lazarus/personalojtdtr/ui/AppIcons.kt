package com.lazarus.personalojtdtr.ui

import androidx.compose.foundation.layout.size
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
    BELL("M5,17 L5,10 C5,2 19,2 19,10 L19,17 L21,19 L3,19 Z M10,22 L14,22 M12,2 L12,4"),
    SETTINGS("M14,3 L15,6 L17,7 L20,6 L21,8 L19,10 L19,14 L21,16 L20,18 L17,17 L15,18 L14,21 L10,21 L9,18 L7,17 L4,18 L3,16 L5,14 L5,10 L3,8 L4,6 L7,7 L9,6 L10,3 Z M15,12 A3,3 0,1 1,9 12 A3,3 0,1 1,15 12"),
    CHECK("M5,12 L10,17 L19,7"),
    RESTORE("M4,6 L4,11 L9,11 M4,11 C4,1 21,1 21,12 C21,22 7,24 4,17"),
    EXPORT("M12,15 L12,3 M7,8 L12,3 L17,8 M4,14 L4,21 L20,21 L20,14"),
    ADD("M12,4 L12,20 M4,12 L20,12"), EDIT("M4,17 L17,4 L21,8 L8,21 L4,21 Z M14,7 L18,11"),
    DELETE("M4,6 L20,6 M9,6 L9,3 L15,3 L15,6 M6,6 L7,21 L17,21 L18,6 M10,10 L10,17 M14,10 L14,17"),
    SHARE("M7,12 L17,5 M7,12 L17,19 M9,12 A3,3 0,1 1,3 12 A3,3 0,1 1,9 12 M21,4 A3,3 0,1 1,15 4 A3,3 0,1 1,21 4 M21,20 A3,3 0,1 1,15 20 A3,3 0,1 1,21 20"),
    PHOTO("M3,3 L21,3 L21,21 L3,21 Z M3,17 L9,11 L13,15 L16,12 L21,17 M17,7 L17,7.1"),
    CAMERA("M3,7 L7,7 L9,4 L15,4 L17,7 L21,7 L21,21 L3,21 Z M17,14 A5,5 0,1 1,7 14 A5,5 0,1 1,17 14"),
    SAVE("M4,3 L17,3 L21,7 L21,21 L3,21 L3,3 Z M7,3 L7,9 L16,9 L16,3 M7,21 L7,14 L17,14 L17,21"),
    TIME_IN("M13,3 L21,3 L21,21 L13,21 M3,12 L16,12 M11,7 L16,12 L11,17"),
    TIME_OUT("M11,3 L3,3 L3,21 L11,21 M9,12 L22,12 M17,7 L22,12 L17,17"),
    CALENDAR("M3,6 L21,6 L21,21 L3,21 Z M7,3 L7,8 M17,3 L17,8 M3,11 L21,11 M7,15 L10,15 M14,15 L17,15"),
    PROGRESS("M12,3 L12,12 L21,12 M9,3.5 A9,9 0,1 0,20.5 15 M15,3.5 A9,9 0,0 1,20.5 9"),
    COMPLETED("M21,11 A9,9 0,1 1,12 3 M8,11 L12,15 L21,5"),
    REMAINING("M6,3 L18,3 M6,21 L18,21 M7,3 L7,7 L17,17 L17,21 M17,3 L17,7 L7,17 L7,21"),
    BACKUP("M4,7 L20,7 L20,21 L4,21 Z M3,3 L21,3 L21,7 L3,7 Z M9,12 L15,12"),
    INFO("M21,12 A9,9 0,1 1,3 12 A9,9 0,1 1,21 12 M12,11 L12,17 M12,7 L12,7.1"),
    WARNING("M12,3 L22,21 L2,21 Z M12,9 L12,14 M12,18 L12,18.1"),
    APPEARANCE("M12,3 A9,9 0,1 0,21 12 C16,15 9,8 12,3 Z"),
    SWITCH_CAMERA("M4,8 L4,4 L8,4 M4,4 L9,9 M20,16 L20,20 L16,20 M20,20 L15,15 M9,4 L20,4 L20,12 M15,20 L4,20 L4,12"),
    FLASH_ON("M13,2 L5,14 L11,14 L10,22 L19,9 L13,9 Z"),
    FLASH_OFF("M13,2 L8,9 M6,12 L5,14 L11,14 L10,22 L16,14 M14,9 L19,9 L17,12 M3,3 L21,21"),
    FLASH_AUTO("M10,2 L3,13 L9,13 L8,22 L15,11 L10,11 Z M15,8 L18,2 L21,8 M16,6 L20,6"),
    BACK("M15,4 L7,12 L15,20"), CLOSE("M5,5 L19,19 M19,5 L5,19"),
    LEFT("M15,5 L8,12 L15,19"), RIGHT("M9,5 L16,12 L9,19");
    val vector: ImageVector by lazy { ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(path).toNodes(), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build() }
}
@Composable fun AppIcon(symbol: AppSymbol, description: String? = null, modifier: Modifier = Modifier.size(24.dp)) { Icon(symbol.vector, description, modifier) }
