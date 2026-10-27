package com.lazarus.personalojtdtr.ui.navigation

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.domain.model.ThemeMode
import com.lazarus.personalojtdtr.ui.OjtViewModel
import com.lazarus.personalojtdtr.ui.activity.ActivityViewModel
import com.lazarus.personalojtdtr.ui.theme.OjtTheme
import com.lazarus.personalojtdtr.ui.theme.ThemeSettings
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.lazarus.personalojtdtr.MainActivity

/** Read-only navigation against the installed app; never edits records, photos or preferences. */
@Suppress("DEPRECATION")
class BottomNavigationTouchTest : InstrumentationTestCase() {
    private lateinit var activity: MainActivity
    private val labels = listOf("Home", "Attendance", "Activities", "History", "Profile")
    override fun setUp() {
        super.setUp()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitFor { node("Home")?.let { clickable(it).isEnabled } == true }
    }
    override fun tearDown() { instrumentation.runOnMainSync { activity.finish() }; super.tearDown() }
    private fun walk(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        root ?: return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) walk(root.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun node(text: String): AccessibilityNodeInfo? {
        // Profile also appears as a Dashboard shortcut; choose the lowest matching label for tabs.
        val matches = mutableListOf<AccessibilityNodeInfo>()
        fun collect(root: AccessibilityNodeInfo?) {
            root ?: return
            if (root.text?.toString() == text) matches += root
            for (i in 0 until root.childCount) collect(root.getChild(i))
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        collect(instrumentation.uiAutomation.rootInActiveWindow)
        return matches.maxByOrNull { Rect().also(it::getBoundsInScreen).bottom }
    }
    private fun visibleLabels(): String {
        val labels = mutableListOf<String>()
        fun collect(root: AccessibilityNodeInfo?) {
            root ?: return
            root.text?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
            root.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(labels::add)
            for (i in 0 until root.childCount) collect(root.getChild(i))
        }
        collect(instrumentation.uiAutomation.rootInActiveWindow)
        return labels.distinct().joinToString(" | ")
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = node
        while (!current.isClickable && !current.isSelected && current.parent != null) current = current.parent
        return current
    }
    private fun waitFor(check: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 8000
        while (!check()) { if (SystemClock.uptimeMillis() > until) fail("Navigation UI did not reach expected state: " + labels.joinToString { label -> "$label=${node(label)?.let { clickable(it).isSelected }}" }); SystemClock.sleep(30) }
        instrumentation.waitForIdleSync()
    }
    private fun tap(x: Float, y: Float) {
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    private fun select(label: String, icon: Boolean = false) {
        val labelNode = requireNotNull(node(label)) { "No tab $label" }
        val item = clickable(labelNode)
        assertTrue("$label enabled", item.isEnabled)
        val bounds = Rect(); item.getBoundsInScreen(bounds)
        val density = activity.resources.displayMetrics.density
        assertTrue("$label touch width", bounds.width() >= 48 * density)
        assertTrue("$label touch height", bounds.height() >= 48 * density)
        tap(bounds.exactCenterX(), if (icon) bounds.top + 24 * density else bounds.bottom - 16 * density)
        // Accessibility selection-change events can lag the Compose frame during animation.
        waitFor {
            labels.all { tab -> node(tab)?.let { clickable(it).isSelected == (tab == label) } == true }
        }
        if (label == "Home") waitFor { node("OJT PROGRESS") != null }
    }
    fun testIconsAndLabelsReturnHome() {
        labels.drop(1).forEach { label -> select(label); select("Home", icon = true); select(label, icon = true); select("Home") }
    }
    fun testCrossTabSequence() { (labels.drop(1) + "Home").forEach { select(it) } }
    fun testDashboardShortcutThenHome() {
        select("Home")
        val shortcut = walk(instrumentation.uiAutomation.rootInActiveWindow) {
            it.contentDescription?.toString() == "Profile"
        } ?: error("Missing Dashboard profile shortcut")
        assertTrue(clickable(shortcut).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor { node("Profile")?.let { clickable(it).isSelected } == true }
        select("Home", icon = true)
    }
    fun testRepeatedTapsAndBack() {
        select("History")
        repeat(8) { select("History", icon = it % 2 == 0) }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor { node("OJT PROGRESS") != null }
    }
    fun testNestedExportDialogReturnsToActivities() {
        select("Activities")
        assertTrue(clickable(requireNotNull(node("Export Journal"))).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor { node("Close")?.let { clickable(it).isEnabled } == true }
        assertTrue(clickable(requireNotNull(node("Close"))).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        waitFor { node("Activities")?.let { clickable(it).isSelected } == true }
        select("Home")
    }
    fun testProfileSettingsNestedRoutesAndBack() {
        fun open(label: String) {
            val target = clickable(requireNotNull(node(label)) { "Missing $label. Visible: ${visibleLabels()}" })
            assertTrue("$label is actionable", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
        }
        select("Profile")
        open("Edit profile")
        waitFor { node("Required OJT hours") != null }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor { node("Settings") != null && node("Edit profile") != null }
        open("Settings")
        waitFor { node("Appearance") != null }
        open("Appearance")
        waitFor { node("System Default") != null && node("Light") != null && node("Dark") != null }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor { node("Data & Backup") != null }
        open("Data & Backup")
        waitFor { node("Export backup") != null && node("Restore backup") != null }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor { node("Appearance") != null && node("Data & Backup") != null }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor { node("Edit profile") != null && node("Settings") != null }
        assertTrue("Profile remains the selected bottom tab", clickable(requireNotNull(node("Profile"))).isSelected)
        select("Home")
        select("Profile")
        waitFor { node("Settings") != null }
    }
    fun testActivityRecreationKeepsSelectedTab() {
        select("Profile")
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            instrumentation.runOnMainSync { activity.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 8000) as? MainActivity ?: error("Activity did not recreate")
            waitFor { node("Profile")?.let { clickable(it).isSelected } == true }
            select("Home")
        } finally { instrumentation.removeMonitor(monitor) }
    }
    fun testLightDarkRecompositionPreservesNavigationAndTouch() {
        val mode = mutableStateOf(ThemeMode.LIGHT)
        instrumentation.runOnMainSync {
            val app = activity.application as OjtApplication
            // Override only this test's composition; never write the stored theme preference.
            activity.setContent {
                OjtTheme(mode.value) {
                    val model: OjtViewModel = viewModel(factory = OjtViewModel.factory(app.repository, app.recorder, app.backup))
                    val journal: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(app.journal))
                    OjtNavigation(model, journal, ThemeSettings(mode.value), {})
                }
            }
        }
        waitFor { node("Home")?.let { clickable(it).isEnabled } == true }
        select("Activities")
        instrumentation.runOnMainSync { mode.value = ThemeMode.DARK }
        instrumentation.waitForIdleSync()
        assertTrue(clickable(requireNotNull(node("Activities"))).isSelected)
        select("Home", icon = true); select("History")
        instrumentation.runOnMainSync { mode.value = ThemeMode.LIGHT }
        instrumentation.waitForIdleSync()
        assertTrue(clickable(requireNotNull(node("History"))).isSelected)
        select("Home")
    }
}
