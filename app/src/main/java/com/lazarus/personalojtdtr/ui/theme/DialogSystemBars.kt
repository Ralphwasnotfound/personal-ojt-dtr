package com.lazarus.personalojtdtr.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Dialogs have their own Window; app theme can differ from the device's uiMode. */
@Composable fun DialogSystemBars(dark: Boolean = LocalOjtDarkTheme.current, edgeToEdge: Boolean = false) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.let {
            if (edgeToEdge) WindowCompat.enableEdgeToEdge(it)
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}
