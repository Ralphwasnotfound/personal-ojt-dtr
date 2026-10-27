package com.lazarus.personalojtdtr.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.lazarus.personalojtdtr.MainActivity
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.ActivityRules
import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.ui.OjtState
import com.lazarus.personalojtdtr.ui.activity.ActivitiesScreen
import com.lazarus.personalojtdtr.ui.activity.ActivityViewModel
import com.lazarus.personalojtdtr.ui.history.HistoryScreen
import com.lazarus.personalojtdtr.ui.navigation.DashboardBottomBar
import com.lazarus.personalojtdtr.ui.profile.ProfileScreen
import com.lazarus.personalojtdtr.ui.profile.EditProfileScreen
import com.lazarus.personalojtdtr.ui.profile.SettingsScreen
import com.lazarus.personalojtdtr.ui.profile.AppearanceSettingsScreen
import com.lazarus.personalojtdtr.ui.profile.DataBackupSettingsScreen
import com.lazarus.personalojtdtr.ui.theme.OjtTheme
import com.lazarus.personalojtdtr.ui.theme.ThemeSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Suppress("DEPRECATION")
class RemainingTabsVisualTest : InstrumentationTestCase() {
    private lateinit var activity: MainActivity
    private var temporaryHistoryPhoto: File? = null

    override fun setUp() {
        super.setUp()
        activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    }

    override fun tearDown() {
        temporaryHistoryPhoto?.deleteRecursively()
        temporaryHistoryPhoto = null
        instrumentation.runOnMainSync { activity.finish() }
        super.tearDown()
    }

    private fun createHistoryPhoto(date: LocalDate): String {
        val relativePath = "photos/attendance/$date/morning_time_in/${UUID.randomUUID()}/timestamped.jpg"
        val file = File(activity.filesDir, relativePath)
        check(file.parentFile!!.mkdirs())
        temporaryHistoryPhoto = file.parentFile
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(126, 34, 64))
        FileOutputStream(file).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output) }
        bitmap.recycle()
        return relativePath
    }

    private fun render(route: String, mode: ThemeMode = ThemeMode.LIGHT, fontScale: Float = 1f,
        content: @Composable (PaddingValues) -> Unit) {
        instrumentation.runOnMainSync {
            activity.setContent {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    OjtTheme(mode) {
                        Scaffold(bottomBar = {
                            DashboardBottomBar(listOf("dashboard" to "Home", "attendance" to "Attendance",
                                "activities" to "Activities", "history" to "History", "profile" to "Profile"),
                                { it == route }, true, {})
                        }) { padding -> content(padding) }
                    }
                }
            }
        }
    }

    private fun find(text: String): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return node
            for (index in 0 until node.childCount) visit(node.getChild(index))?.let { return it }
            return null
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }

    private fun await(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() <= deadline) {
            find(text)?.let { return it }
            SystemClock.sleep(35)
        }
        fail("Missing screen content: $text")
        error("unreachable")
    }

    private fun click(text: String) {
        var target = await(text)
        while (!target.isClickable && target.parent != null) target = target.parent
        assertTrue("$text responds", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    private fun scrollTo(text: String) {
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isScrollable) return node
            for (index in 0 until node.childCount) scrollable(node.getChild(index))?.let { return it }
            return null
        }
        repeat(8) {
            if (find(text) != null) return
            val container = scrollable(instrumentation.uiAutomation.rootInActiveWindow)
            if (container?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) return
            instrumentation.waitForIdleSync()
        }
        await(text)
    }

    private fun fixtureActivity() = DailyActivity("ui-fixture-activity", LocalDate.now(),
        "Fixture description for a saved activity entry.", (1..5).map { "missing-fixture-$it.jpg" },
        "Fixture electrical maintenance", LocalTime.of(9, 15), revision = 1)

    fun testActivitiesJournalDetailsEditDeleteAndPhotoLimit() {
        val app = activity.application as OjtApplication
        val model = ActivityViewModel(app.journal, SavedStateHandle())
        render("activities") { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                ActivitiesScreen(listOf(fixtureActivity()), false, model)
            }
        }
        await("Activities")
        await("1 entry")
        await("Export Journal")
        await("Add activity")
        await("5")
        click("Add activity")
        await("New activity")
        await("Camera")
        await("Choose photos")
        click("Cancel editing")
        await("Discard unsaved changes?")
        click("Discard changes")
        await("Activities")
        click("Fixture electrical maintenance")
        await("Fixture description for a saved activity entry.")
        await("Photos · 5")
        click("View photo 1")
        await("1 / 5")
        await("Share photo with activity text")
        click("Back to activity")
        click("Delete activity")
        await("Delete activity?")
        click("Cancel")
        click("Edit activity")
        await("Attachments")
        await("5 / 5 photos")
        val choosePhotos = await("Choose photos")
        var chooseTarget = choosePhotos
        while (!chooseTarget.isClickable && chooseTarget.parent != null) chooseTarget = chooseTarget.parent
        assertFalse("Photo picker is disabled at the five-photo limit", chooseTarget.isEnabled)
        scrollTo("Move photo 1 earlier")
        await("Remove photo 1")
    }

    fun testHistorySummaryEvidenceAndDtrExportEntry() {
        val date = LocalDate.now(AttendanceCalculations.manila)
        val previousDate = date.minusDays(1)
        val zone = ZoneId.of("Asia/Manila")
        fun instant(hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant()
        val timestampedPhoto = createHistoryPhoto(date)
        val events = listOf(
            AttendanceEvent("history-mi", date, AttendanceEventType.MORNING_TIME_IN, instant(8), instant(8), "missing-hmi-original", timestampedPhoto),
            AttendanceEvent("history-mo", date, AttendanceEventType.MORNING_TIME_OUT, instant(12), instant(12), "missing-hmo", "missing-hmo"),
            AttendanceEvent("history-ai", date, AttendanceEventType.AFTERNOON_TIME_IN, instant(13), instant(13), "missing-hai", "missing-hai"),
            AttendanceEvent("history-ao", date, AttendanceEventType.AFTERNOON_TIME_OUT, instant(14), instant(14), "missing-hao", "missing-hao"),
        ) + AttendanceEvent("history-recovery", previousDate, AttendanceEventType.MORNING_TIME_IN,
            previousDate.atTime(7, 0).atZone(zone).toInstant(), previousDate.atTime(7, 0).atZone(zone).toInstant(),
            "missing-recovery", "missing-recovery")
        val recovery = SessionResolution("history-recovery-action", previousDate, OjtSession.MORNING,
            ResolutionKind.INCOMPLETE, date.atTime(9, 0).atZone(zone).toInstant())
        val sessions = listOf(AttendanceSession("history-morning", instant(8), instant(12)),
            AttendanceSession("history-afternoon", instant(13), instant(14)))
        val state = OjtState(profile = PersonalProfile("Fixture Student", 500), sessions = sessions,
            events = events, resolutions = listOf(recovery), loaded = true)
        val app = activity.application as OjtApplication
        var shared: String? = null
        render("history", fontScale = 1.2f) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                HistoryScreen(state, app.recorder.photos, {}, { shared = it.id })
            }
        }
        await("History")
        await("5h 0m")
        await("Export DTR")
        await("Complete")
        await("Recovery")
        assertNull("Event details start collapsed", find("8:00:00 AM"))
        assertNull("Photo actions start collapsed", find("View"))
        assertNull("Recovery audit details start collapsed", find("Audit action"))
        click("Export DTR")
        await("Preview period")
        click("Close")
        val todayLabel = ActivityRules.dateLabel(date)
        val previousLabel = ActivityRules.dateLabel(previousDate)
        click(todayLabel)
        await("Morning")
        await("Afternoon")
        await("Time In")
        await("8:00:00 AM")
        scrollTo("8:00:00 AM")
        click("Open Morning Time In photo")
        await("Original")
        click("Share timestamped photo")
        assertEquals("history-mi", shared)
        click("Close")
        click("Share Morning Time In photo")
        assertEquals("history-mi", shared)
        click("Open Morning Time Out photo")
        await("Photo unavailable")
        click("Close")
        scrollTo(previousLabel)
        click(previousLabel)
        await("Audit action")
        await("Incomplete / unverified")
        assertNull("Opening another date collapses the first", find("8:00:00 AM"))
        click(previousLabel)
        assertNull("A second tap collapses the date", find("Audit action"))

        render("history", ThemeMode.DARK, fontScale = 1.2f) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                HistoryScreen(state, app.recorder.photos, {}, { shared = it.id })
            }
        }
        await("History")
        click(todayLabel)
        await("Morning")
        await("8:00:00 AM")
    }

    fun testProfileEditSettingsAppearanceAndBackupAtLargeText() {
        val page = mutableStateOf("profile")
        var saved: PersonalProfile? = null
        var themeSelected: ThemeMode? = null
        var exports = 0
        var restores = 0
        val profile = PersonalProfile("Fixture Student", 500, LocalDate.of(2025, 1, 15), Duration.ofMinutes(30))
        render("profile", ThemeMode.DARK, fontScale = 1.35f) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (page.value) {
                    "profile" -> ProfileScreen(profile, { page.value = "edit" }, { page.value = "settings" })
                    "edit" -> EditProfileScreen(profile, false, { saved = it }, { page.value = "profile" })
                    "settings" -> SettingsScreen({ page.value = "appearance" }, { page.value = "backup" },
                        { page.value = "profile" })
                    "appearance" -> AppearanceSettingsScreen(ThemeSettings(ThemeMode.SYSTEM),
                        { themeSelected = it }, { page.value = "settings" })
                    "backup" -> DataBackupSettingsScreen(false, { exports++ }, { restores++ },
                        { page.value = "settings" })
                }
            }
        }
        await("Fixture Student")
        await("500")
        await("OJT information")
        click("Edit profile")
        await("Display name")
        await("Required OJT hours")
        await("Legacy P1 break")
        scrollTo("Save profile")
        click("Save profile")
        assertEquals(profile, saved)
        click("Back to profile")
        click("Settings")
        await("Appearance")
        await("Data & Backup")
        click("Appearance")
        await("System default")
        await("Light")
        await("Dark")
        click("Dark")
        assertEquals(ThemeMode.DARK, themeSelected)
        click("Back to settings")
        click("Data & Backup")
        await("Export backup")
        await("Restore backup")
        click("Export backup")
        assertEquals(1, exports)
        click("Restore backup")
        assertEquals(1, restores)
        click("Back to settings")
        click("Back to profile")
        await("My OJT Companion")
    }
}
