package com.lazarus.personalojtdtr.domain.model

/** Stable persisted values are independent of enum ordering and display labels. */
enum class ThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "System Default"), LIGHT("light", "Light"), DARK("dark", "Dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
