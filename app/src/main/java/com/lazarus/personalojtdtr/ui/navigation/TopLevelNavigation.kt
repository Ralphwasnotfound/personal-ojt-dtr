package com.lazarus.personalojtdtr.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination

/** One policy for tabs, dashboard shortcuts and widget destinations. */
fun NavController.selectTopLevel(route: String) {
    if (currentDestination?.hierarchy?.any { it.route == route } == true) return
    val start = graph.findStartDestination()
    navigate(route) {
        popUpTo(start.id) { saveState = true }
        launchSingleTop = true
        // Home is already retained below the popped entries. Restoring its saved-stack
        // alias can resurrect a shortcut destination instead of showing Dashboard.
        restoreState = route != start.route
    }
}
