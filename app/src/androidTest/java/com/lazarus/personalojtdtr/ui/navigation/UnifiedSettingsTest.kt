package com.lazarus.personalojtdtr.ui.navigation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.lazarus.personalojtdtr.MainActivity
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.data.local.OjtDatabase
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.ui.OjtViewModel
import com.lazarus.personalojtdtr.ui.activity.ActivityViewModel
import com.lazarus.personalojtdtr.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*

/** UI routes are read-only; persistence assertions use a separate in-memory database. */
@Suppress("DEPRECATION")
class UnifiedSettingsTest : InstrumentationTestCase() {
    private var activity: MainActivity? = null
    private var passed = false
    override fun tearDown() { if (!passed && activity != null) runCatching { screenshot("p119-test-error.png") }; activity?.let { screen -> instrumentation.runOnMainSync { screen.finish() } }; super.tearDown() }
    private fun render(mode: ThemeMode) {
        val screen = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        activity = screen
        instrumentation.waitForIdleSync(); instrumentation.uiAutomation.waitForIdle(300, 5000)
        instrumentation.runOnMainSync {
            screen.enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { mode == ThemeMode.DARK },
                navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x80171517.toInt()) { mode == ThemeMode.DARK })
            val app = screen.application as OjtApplication
            screen.setContent { OjtTheme(mode) {
                val model: OjtViewModel = viewModel(factory = OjtViewModel.factory(app.repository, app.recorder, app.backup))
                val journal: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(app.journal))
                OjtNavigation(model, journal, ThemeSettings(mode), {})
            } }
        }
    }
    private fun matches(text: String): List<AccessibilityNodeInfo> {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            node ?: return
            if (node.text?.toString() == text && node.isVisibleToUser) found += node
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(instrumentation.uiAutomation.rootInActiveWindow)
        return found
    }
    private fun await(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) { matches(text).firstOrNull()?.let { return it }; SystemClock.sleep(50) }
        error("Missing settings content: $text")
    }
    private fun scroll(text: String) {
        if (matches(text).isNotEmpty()) return
        fun container(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) container(node.getChild(i))?.let { return it }
            return null
        }
        for (direction in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            for (attempt in 0 until 8) {
                if (matches(text).isNotEmpty()) return
                if (container(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(direction) != true) break
                instrumentation.waitForIdleSync(); instrumentation.uiAutomation.waitForIdle(150, 3000)
            }
        }
        await(text)
    }
    private fun click(text: String, tab: Boolean = false) {
        if (!tab) scroll(text)
        var node = if (tab) matches(text).maxByOrNull { Rect().also(it::getBoundsInScreen).bottom } ?: await(text) else await(text)
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue("Action responds: $text", node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync(); instrumentation.uiAutomation.waitForIdle(700, 4000)
    }
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); instrumentation.waitForIdleSync(); instrumentation.uiAutomation.waitForIdle(700, 4000) }
    private fun pageBack() {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.contentDescription?.toString() == "Back" && node.isVisibleToUser) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val labels = mutableListOf<String>()
        fun list(node: AccessibilityNodeInfo?) { node ?: return; labels += "${node.className}:${node.text}:${node.contentDescription}:${node.isVisibleToUser}"; for (i in 0 until node.childCount) list(node.getChild(i)) }
        list(root)
        var node = requireNotNull(find(root)) { "Back action missing: ${labels.take(40)}" }
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync(); instrumentation.uiAutomation.waitForIdle(700, 4000)
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(200, 4000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { activity!!.cacheDir.resolve(name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
    private fun home() { click("Profile", true); click("Settings"); await("OJT CONFIGURATION") }

    fun testAllSettingsRoutesAndBackWithoutEditing() {
        render(ThemeMode.LIGHT); await("Home"); home()
        scroll("Data & Backup"); screenshot("p119-settings-light.png")
        // Return the settings list to its top before opening the first entry.
        scroll("Completion Forecast")
        click("Completion Forecast"); await("Current forecast"); screenshot("p119-forecast-light.png")
        scroll("Save forecast pace"); screenshot("p119-forecast-pace-light.png")
        click("Required OJT hours"); await("Display name"); back(); await("Working calendar")
        click("Work Schedule"); await("Working weekdays")
        click("Open OJT Calendar"); await("Select Date"); click("Select Date"); await("Continue")
        click("Cancel"); click("Done"); pageBack(); await("Working calendar"); back(); scroll("OJT CONFIGURATION")
        click("Attendance Reminders"); await("Enable reminders with these times"); back(); scroll("OJT CONFIGURATION")
        click("Work Schedule"); await("Save working weekdays"); back(); scroll("OJT CONFIGURATION")
        click("Appearance"); await("System Default"); await("Light"); await("Dark"); back()
        click("Data & Backup"); await("Export backup"); await("Restore backup"); back()
        await("APP PREFERENCES"); back(); await("Edit profile"); passed = true
    }
    fun testDarkSettingsAndForecastRemainReadable() {
        render(ThemeMode.DARK); await("Home"); home()
        scroll("Data & Backup"); screenshot("p119-settings-dark.png")
        click("Completion Forecast"); await("Current forecast")
        screenshot("p119-forecast-dark.png")
        scroll("Save forecast pace"); screenshot("p119-forecast-pace-dark.png")
        click("Work Schedule"); await("Working weekdays"); screenshot("p119-work-schedule-dark.png"); passed = true
    }
    fun testForecastSettingsPersistAndEmitFreshWidgetInputs() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, OjtDatabase::class.java).build()
        try {
            val repo = OjtRepository(db)
            val days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            val now = Instant.parse("2026-11-23T08:00:00Z")
            repo.saveProfile(PersonalProfile(displayName = "Isolated", requiredHours = 40))
            repo.saveSchedule(8, days)
            val first = WidgetSnapshot.from(repo.widgetInputs(), now)
            val observed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { repo.widgetChanges.first { it.expectedHoursPerDay == 4 } } }
            repo.saveSchedule(4, days)
            val changed = WidgetSnapshot.from(observed.await(), now)
            assertNotNull(first.estimatedCompletionDate)
            assertTrue(first.estimatedCompletionDate != changed.estimatedCompletionDate)
            assertEquals(4, repo.schedule.first().hoursPerDay)
            assertEquals(days, repo.schedule.first().weekdays)
            val profileChange = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { repo.widgetChanges.first { it.requiredHours == 64L } } }
            repo.saveProfile(PersonalProfile(displayName = "Isolated", requiredHours = 64))
            val updated = profileChange.await()
            assertEquals(64L, repo.profile.first().requiredHours)
            assertTrue(changed.estimatedCompletionDate != WidgetSnapshot.from(updated, now).estimatedCompletionDate)
        } finally { db.close() }
    }
}
