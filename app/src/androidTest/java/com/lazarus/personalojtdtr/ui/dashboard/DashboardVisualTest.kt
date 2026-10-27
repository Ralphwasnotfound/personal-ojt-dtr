package com.lazarus.personalojtdtr.ui.dashboard

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.MainActivity
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.ui.OjtState
import com.lazarus.personalojtdtr.ui.navigation.DashboardBottomBar
import com.lazarus.personalojtdtr.ui.theme.OjtTheme
import java.io.File
import java.time.*

/** Nonpersistent reference fixtures: never insert records or change stored preferences. */
@Suppress("DEPRECATION")
class DashboardVisualTest : InstrumentationTestCase() {
    private lateinit var activity: MainActivity
    override fun setUp() {
        super.setUp()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }
    override fun tearDown() { instrumentation.runOnMainSync { activity.finish() }; super.tearDown() }
    private fun fixture(longName: Boolean = false) = OjtState(
        profile = PersonalProfile(displayName = if (longName) "Student with a longer display name" else "Sample Student", requiredHours = 500),
        sessions = listOf(AttendanceSession("visual-only", Instant.parse("2025-03-04T00:00:00Z"), Instant.parse("2025-03-04T00:01:00Z"))),
        activities = (1..2).map { DailyActivity("visual-only-$it", LocalDate.of(2025, 3, 4), "Sample description", title = "Sample journal task", time = LocalTime.of(8, 0)) },
        loaded = true)
    private fun render(mode: ThemeMode, state: OjtState = fixture(), fontScale: Float = 1f, compact: Boolean = false) {
        instrumentation.runOnMainSync {
            val photos = (activity.application as OjtApplication).journal.photos
            val dark = mode.isDark(activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x80171517.toInt()) { dark })
            activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    OjtTheme(mode) {
                        Box(if (compact) Modifier.width(320.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                            Scaffold(contentWindowInsets = WindowInsets.safeDrawing, bottomBar = {
                                DashboardBottomBar(listOf("dashboard" to "Home", "attendance" to "Attendance", "activities" to "Activities", "history" to "History", "profile" to "Profile"),
                                    { it == "dashboard" }, true, {})
                            }) { padding ->
                                Box(Modifier.fillMaxSize().padding(padding)) { DashboardScreen(state, photos, {}, {}, {}, {}) }
                            }
                        }
                    }
                }
            }
        }
        awaitText("OJT PROGRESS")
        instrumentation.waitForIdleSync()
    }
    private fun find(node: AccessibilityNodeInfo?, text: String): Boolean {
        node ?: return false
        if (node.text?.toString()?.contains(text) == true) return true
        return (0 until node.childCount).any { find(node.getChild(it), text) }
    }
    private fun awaitText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (true) {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            if (find(instrumentation.uiAutomation.rootInActiveWindow, text)) return
            if (SystemClock.uptimeMillis() > deadline) fail("Missing Dashboard content: $text")
            SystemClock.sleep(30)
        }
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(150, 4000)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(instrumentation.targetContext.cacheDir, name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { image.recycle() }
    }
    private fun scroll(): Boolean {
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) scrollable(node.getChild(i))?.let { return it }
            return null
        }
        // At normal display sizes all content may fit; compact/large-text cases must scroll.
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val moved = scrollable(instrumentation.uiAutomation.rootInActiveWindow)
            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        instrumentation.waitForIdleSync()
        return moved
    }
    fun testLightRefinedLayout() {
        render(ThemeMode.LIGHT); awaitText("Sample Student"); awaitText("499h 59m"); awaitText("0h 1m")
        screenshot("dashboard-v2-light.png")
        scroll(); awaitText("Sample journal task"); screenshot("dashboard-v2-light-lower.png")
    }
    fun testDarkLayoutAndEmptyRecords() {
        render(ThemeMode.DARK, OjtState(loaded = true))
        awaitText("0h 0m"); screenshot("dashboard-v2-dark.png")
        scroll(); awaitText("Your journal starts here")
    }
    fun testLargeTextAndLongNameRemainScrollable() {
        render(ThemeMode.LIGHT, fixture(longName = true), 1.5f)
        awaitText("Student with a longer display name")
        screenshot("dashboard-v2-large-text.png")
        repeat(2) { scroll() }
        awaitText("Sample journal task")
    }
    fun testSystemDefaultAndProgressValues() {
        render(ThemeMode.SYSTEM)
        awaitText("0h 1m"); awaitText("500h 0m"); awaitText("499h 59m")
        screenshot("dashboard-v2-system.png")
    }
    fun testRecordedTimeAndMissingPhoto() {
        val day = LocalDate.now(com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations.manila)
        val instant = day.atTime(8, 15).atZone(com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations.manila).toInstant()
        render(ThemeMode.LIGHT, fixture().copy(events = listOf(AttendanceEvent("visual-event", day,
            AttendanceEventType.MORNING_TIME_IN, instant, instant, "missing-visual.jpg", "missing-visual.jpg"))))
        scroll(); awaitText("8:15 AM"); awaitText("Recorded")
        screenshot("dashboard-v2-recorded.png")
    }
    fun testCompactLargeTextScrollKeepsNavigationFixed() {
        val original = fixture()
        val state = original.copy(activities = original.activities.dropLast(1) +
            original.activities.last().copy(title = "Final sample journal entry"))
        render(ThemeMode.LIGHT, state, fontScale = 1.5f, compact = true)
        fun node(text: String): AccessibilityNodeInfo? {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            fun locate(current: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                current ?: return null
                if (current.text?.toString()?.contains(text) == true) return current
                for (i in 0 until current.childCount) locate(current.getChild(i))?.let { return it }
                return null
            }
            return locate(instrumentation.uiAutomation.rootInActiveWindow)
        }
        fun tabBounds(): Rect {
            var home = requireNotNull(node("Home"))
            while (!home.isSelected && home.parent != null) home = home.parent
            assertTrue("Home remains selected while scrolling", home.isSelected)
            return Rect().also(home::getBoundsInScreen)
        }
        instrumentation.uiAutomation.waitForIdle(150, 4000)
        val before = tabBounds()
        assertTrue("Large text at 320dp requires vertical scrolling", scroll())
        repeat(3) { scroll() }
        awaitText("Final sample journal entry")
        instrumentation.uiAutomation.waitForIdle(150, 4000)
        val after = tabBounds()
        assertEquals("Bottom navigation must not move with Dashboard content", before, after)
        val last = Rect().also(requireNotNull(node("Final sample journal entry"))::getBoundsInScreen)
        assertTrue("Last entry is visible", last.height() > 0)
        assertTrue("Last entry stays above the navigation bar", last.bottom <= after.top)
        screenshot("dashboard-v2-scroll-bottom.png")
    }
}
