package com.lazarus.personalojtdtr.ui.navigation

import android.test.InstrumentationTestCase
import androidx.lifecycle.*
import androidx.navigation.*
import androidx.navigation.compose.*

@Suppress("DEPRECATION")
class BottomNavigationTest : InstrumentationTestCase() {
    private val routes = listOf("dashboard", "attendance", "activities", "history", "profile")
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }
    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun controller(store: ViewModelStore = ViewModelStore(), saved: android.os.Bundle? = null): NavHostController {
        return NavHostController(instrumentation.targetContext).apply {
            setLifecycleOwner(Owner()); setViewModelStore(store)
            navigatorProvider.addNavigator(ComposeNavigator())
            saved?.let { restoreState(it) }
            graph = createGraph(startDestination = "dashboard") {
                routes.forEach { route -> composable(route) {} }
            }
        }
    }
    private fun select(nav: NavHostController, route: String) {
        val before = nav.currentBackStack.value.toList()
        nav.selectTopLevel(route)
        val navigator = nav.navigatorProvider.getNavigator<ComposeNavigator>("composable")
        (before + nav.currentBackStack.value).distinct().filter { it.destination is ComposeNavigator.Destination }.forEach(navigator::onTransitionComplete)
    }
    fun testEveryTabReturnsHome() = onMain {
        routes.drop(1).forEach { route ->
            val nav = controller()
            select(nav, route); assertEquals(route, nav.currentDestination?.route)
            select(nav, "dashboard"); assertEquals("Home from $route", "dashboard", nav.currentDestination?.route)
        }
    }
    fun testCrossTabSequences() = onMain {
        val nav = controller()
        repeat(4) {
            (routes.drop(1) + "dashboard").forEach { route ->
                select(nav, route); assertEquals("After $route cycle $it", route, nav.currentDestination?.route)
            }
        }
    }
    fun testDashboardShortcutsThenTabs() = onMain {
        routes.drop(1).forEach { shortcut -> routes.forEach { tab ->
            val nav = controller()
            nav.navigate(shortcut) { launchSingleTop = true }
            select(nav, tab)
            assertEquals("Shortcut $shortcut then $tab", tab, nav.currentDestination?.route)
            select(nav, "dashboard"); assertEquals("Shortcut $shortcut then $tab then Home", "dashboard", nav.currentDestination?.route)
        } }
    }
    fun testRapidSwitchingAndRepeatedActiveTab() = onMain {
        val nav = controller()
        repeat(50) {
            routes.forEach { route ->
                select(nav, route)
                assertEquals(route, nav.currentDestination?.route)
                val id = nav.currentBackStackEntry!!.id
                repeat(5) { select(nav, route) }
                assertEquals(id, nav.currentBackStackEntry!!.id)
                assertEquals(if (route == "dashboard") 1 else 2,
                    nav.currentBackStack.value.count { it.destination is ComposeNavigator.Destination })
            }
        }
    }
    fun testBackReturnsDashboard() = onMain {
        val nav = controller()
        routes.drop(1).forEach { route ->
            select(nav, route); assertTrue(nav.popBackStack())
            assertEquals("dashboard", nav.currentDestination?.route)
        }
    }
    fun testSavedTabStateAndControllerRecreation() = onMain {
        val store = ViewModelStore()
        val nav = controller(store)
        select(nav, "activities")
        nav.currentBackStackEntry!!.savedStateHandle["fixture"] = "retained"
        select(nav, "profile")
        val recreated = controller(store, nav.saveState())
        assertEquals("profile", recreated.currentDestination?.route)
        select(recreated, "dashboard"); assertEquals("dashboard", recreated.currentDestination?.route)
        select(recreated, "activities")
        assertEquals("retained", recreated.currentBackStackEntry!!.savedStateHandle.get<String>("fixture"))
        store.clear()
    }
    fun testNestedDestinationRestoresAndBackReturnsToParent() = onMain {
        val nav = controller()
        nav.graph = nav.createGraph(startDestination = "dashboard") {
            composable("dashboard") {}; composable("history") {}
            navigation(startDestination = "activities/list", route = "activities") {
                composable("activities/list") {}; composable("activities/detail") {}
            }
        }
        select(nav, "activities"); nav.navigate("activities/detail")
        select(nav, "activities") // Active parent tab must not add another detail/list.
        assertEquals("activities/detail", nav.currentDestination?.route)
        select(nav, "history"); select(nav, "dashboard"); select(nav, "activities")
        assertEquals("activities/detail", nav.currentDestination?.route)
        assertTrue(nav.popBackStack()); assertEquals("activities/list", nav.currentDestination?.route)
    }
}
