package com.lazarus.personalojtdtr.widget

/** Height breakpoints matching the responsive sizes offered to Android launchers. */
internal enum class WidgetLayoutSize { COMPACT, MEDIUM, LARGE }

internal fun widgetLayoutSize(heightDp: Float, widthDp: Float = 320f): WidgetLayoutSize = when {
    heightDp >= 440f && widthDp >= 300f -> WidgetLayoutSize.LARGE
    heightDp >= 200f -> WidgetLayoutSize.MEDIUM
    else -> WidgetLayoutSize.COMPACT
}
