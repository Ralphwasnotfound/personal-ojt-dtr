package com.lazarus.personalojtdtr.ui.attendance

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.lazarus.personalojtdtr.MainActivity
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.ui.OjtState
import com.lazarus.personalojtdtr.ui.navigation.DashboardBottomBar
import com.lazarus.personalojtdtr.ui.theme.OjtTheme
import com.lazarus.personalojtdtr.util.today
import java.time.LocalDate
import java.time.ZoneId

@Suppress("DEPRECATION")
class AttendanceScreenVisualTest : InstrumentationTestCase() {
    private lateinit var activity: MainActivity

    override fun setUp() {
        super.setUp()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }

    override fun tearDown() {
        instrumentation.runOnMainSync { activity.finish() }
        super.tearDown()
    }

    private fun render(state: OjtState = OjtState(loaded = true), mode: ThemeMode = ThemeMode.LIGHT,
        onCapture: (LocalDate, AttendanceEventType) -> Unit = { _, _ -> }) {
        instrumentation.runOnMainSync {
            val photos = (activity.application as OjtApplication).recorder.photos
            activity.setContent {
                OjtTheme(mode) {
                    Scaffold(bottomBar = {
                        DashboardBottomBar(listOf("dashboard" to "Home", "attendance" to "Attendance", "activities" to "Activities", "history" to "History", "profile" to "Profile"),
                            { it == "attendance" }, true, {})
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            AttendanceScreen(state, false, photos, onCapture, {}, {}, { _, _, _ -> })
                        }
                    }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(150, 4000)
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            activity.cacheDir.resolve(name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
    }

    private fun find(text: String): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        fun locate(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.text?.toString()?.contains(text) == true) return node
            for (index in 0 until node.childCount) locate(node.getChild(index))?.let { return it }
            return null
        }
        return locate(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun await(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() <= deadline) {
            find(text)?.let { return it }
            SystemClock.sleep(40)
        }
        fail("Missing Attendance content: $text")
        error("unreachable")
    }

    private fun scrollTo(text: String) {
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isScrollable) return node
            for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
            return null
        }
        repeat(6) {
            if (find(text) != null) return
            val container = scrollable(instrumentation.uiAutomation.rootInActiveWindow)
            if (container?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) return
            instrumentation.waitForIdleSync()
        }
        await(text)
    }

    fun testNextCaptureActionUsesExistingAttendanceDateAndSequence() {
        var selected: Pair<java.time.LocalDate, AttendanceEventType>? = null
        render(onCapture = { date, type -> selected = date to type })
        await("Attendance")
        await("Not started")
        screenshot("attendance-v2-light.png")
        val action = await("Capture Morning Time In")
        var clickable = action
        while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
        assertTrue("Capture action has a touch target", clickable.isClickable)
        assertTrue("Capture action responds", clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        assertEquals(today(), selected?.first)
        assertEquals(AttendanceEventType.MORNING_TIME_IN, selected?.second)
    }

    fun testRecordedEventsAndCompletedSummaryStayVisible() {
        val date = today()
        val zone = ZoneId.of("Asia/Manila")
        val events = listOf(
            AttendanceEvent("test-mi", date, AttendanceEventType.MORNING_TIME_IN, date.atTime(8, 0).atZone(zone).toInstant(), date.atTime(8, 0).atZone(zone).toInstant(), "missing-a", "missing-a"),
            AttendanceEvent("test-mo", date, AttendanceEventType.MORNING_TIME_OUT, date.atTime(12, 0).atZone(zone).toInstant(), date.atTime(12, 0).atZone(zone).toInstant(), "missing-b", "missing-b"),
            AttendanceEvent("test-ai", date, AttendanceEventType.AFTERNOON_TIME_IN, date.atTime(13, 0).atZone(zone).toInstant(), date.atTime(13, 0).atZone(zone).toInstant(), "missing-c", "missing-c"),
            AttendanceEvent("test-ao", date, AttendanceEventType.AFTERNOON_TIME_OUT, date.atTime(14, 0).atZone(zone).toInstant(), date.atTime(14, 0).atZone(zone).toInstant(), "missing-d", "missing-d"),
        )
        render(OjtState(events = events, loaded = true))
        await("Complete")
        await("5h 0m")
        await("Morning Time In")
        await("Morning Time Out")
        await("Afternoon Time In")
        await("Afternoon Time Out")
        scrollTo("All four events recorded")
    }

    fun testDarkThemeRecoveryRemainsReadable() {
        val previousDay = today().minusDays(1)
        val start = previousDay.atTime(8, 0).atZone(ZoneId.of("Asia/Manila")).toInstant()
        val state = OjtState(events = listOf(AttendanceEvent("unfinished-session", previousDay,
            AttendanceEventType.MORNING_TIME_IN, start, start, "missing-recovery.jpg", "missing-recovery.jpg")), loaded = true)
        render(state, ThemeMode.DARK)
        await("Recovery")
        await("Recovery · $previousDay")
        await("An earlier session has no Time Out")
        await("Mark incomplete / unverified")
        screenshot("attendance-v2-dark-recovery.png")
    }
}
