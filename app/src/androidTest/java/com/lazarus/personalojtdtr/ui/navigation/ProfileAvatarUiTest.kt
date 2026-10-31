package com.lazarus.personalojtdtr.ui.navigation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.runBlocking
import com.lazarus.personalojtdtr.data.backup.RoomBackupStore
import com.lazarus.personalojtdtr.MainActivity
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.ui.profile.*
import com.lazarus.personalojtdtr.ui.dashboard.DashboardScreen
import com.lazarus.personalojtdtr.ui.OjtState
import com.lazarus.personalojtdtr.ui.theme.OjtTheme
import java.io.File
import java.util.UUID

/** UI callbacks operate on generated images and counters, never on the personal repository. */
@Suppress("DEPRECATION")
class ProfileAvatarUiTest : InstrumentationTestCase() {
    private var screen: MainActivity? = null
    override fun tearDown() { screen?.let { instrumentation.runOnMainSync { it.finish() } }; super.tearDown() }
    private fun await(label: String): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + 8000
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isVisibleToUser && (node.text?.toString() == label || node.contentDescription?.toString() == label)) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        while (SystemClock.uptimeMillis() < end) {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            find(instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }; SystemClock.sleep(50)
        }
        error("Missing UI: $label")
    }
    private fun assertAbsent(label: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
        fun present(node: AccessibilityNodeInfo?): Boolean {
            node ?: return false
            if (node.isVisibleToUser && node.text?.toString() == label) return true
            return (0 until node.childCount).any { present(node.getChild(it)) }
        }
        assertFalse("Main Profile must not contain $label", present(instrumentation.uiAutomation.rootInActiveWindow))
    }
    private fun click(label: String) {
        var node = await(label)
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync()
    }
    private fun screenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(300, 4000)
        val shot = instrumentation.uiAutomation.takeScreenshot()!!
        try { File(instrumentation.targetContext.cacheDir, name).outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { shot.recycle() }
    }
    fun testLauncherHasTransparentAdaptiveBackgroundAndPreservedBrand() {
        val context = instrumentation.targetContext
        val icon = context.applicationInfo.loadIcon(context.packageManager)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val adaptive = icon as android.graphics.drawable.AdaptiveIconDrawable
        val background = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
        try {
            adaptive.background.setBounds(0, 0, 108, 108)
            adaptive.background.draw(android.graphics.Canvas(background))
            assertEquals(0, Color.alpha(background.getPixel(54, 54)))
            assertEquals(0, Color.alpha(background.getPixel(0, 0)))
        } finally { background.recycle() }
        assertEquals("OJT Companion", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals("com.lazarus.personalojtdtr", context.packageName)
    }
    fun testProfilePreviewCancelAndDashboardInBothThemes() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        screen = activity; instrumentation.waitForIdleSync(); instrumentation.uiAutomation.waitForIdle(300, 5000)
        val app = activity.application as OjtApplication
        val personalStore = RoomBackupStore(app.repository.database, app.repository.avatarFiles)
        fun personalSnapshot() = runBlocking { personalStore.snapshot() }.let { (data, paths) ->
            data.copy(createdAt = 0,
                events = data.events.map { it.copy(original = paths.getValue(it.original), stamped = paths.getValue(it.stamped)) },
                legacy = data.legacy.map { it.copy(original = it.original?.let(paths::getValue), outPhoto = it.outPhoto?.let(paths::getValue)) },
                activities = data.activities.map { it.copy(photos = it.photos.map(paths::getValue)) },
                avatar = data.avatar?.let(paths::getValue)) to paths.values.toSet()
        }
        val before = personalSnapshot()
        assertEquals("OJT Companion", activity.applicationInfo.loadLabel(activity.packageManager).toString())
        val photo = File(activity.cacheDir, "avatar-ui-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(139, 23, 23))
            android.graphics.Canvas(this).drawCircle(256f, 256f, 150f, android.graphics.Paint().apply { color = Color.rgb(212, 164, 58) })
        }
        try {
            try { AvatarImages.encode(bitmap, photo) } finally { bitmap.recycle() }
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                var confirmed = 0
                var removed = 0
                instrumentation.runOnMainSync { activity.setContent { OjtTheme(mode) { androidx.compose.material3.Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var preview by remember(mode) { mutableStateOf(false) }
                    var editing by remember(mode) { mutableStateOf(false) }
                    val profile = PersonalProfile(displayName = "Preview Student", requiredHours = 486)
                    if (editing) EditProfileScreen(profile, false, {}, { editing = false }, avatar = photo,
                        onChangePhoto = { preview = true }, onRemovePhoto = { removed++ })
                    else ProfileScreen(profile, { editing = true }, {}, avatar = photo)
                    if (preview) AvatarPreviewDialog(AvatarDraft("fixture", photo), false, { _, _, _ -> confirmed++; preview = false }, { preview = false })
                } } } }
                val avatar = await("Profile photo")
                assertFalse("Main Profile avatar has no click action", avatar.isClickable)
                assertFalse("Main Profile avatar cannot launch an editor", avatar.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertAbsent("Change Photo"); assertAbsent("Remove Photo"); assertAbsent("Preview profile photo")
                screenshot("p1111-profile-${mode.name.lowercase()}.png")
                click("Edit profile"); await("Display name"); await("Change Photo")
                screenshot("p1111-editor-${mode.name.lowercase()}.png")
                click("Change Photo"); await("Confirm Photo"); screenshot("p1111-preview-${mode.name.lowercase()}.png")
                click("Cancel"); await("Change Photo"); assertEquals(0, confirmed)
                click("Change Photo"); await("Profile photo preview"); SystemClock.sleep(300); click("Confirm Photo"); await("Change Photo"); assertEquals(1, confirmed)
                click("Remove Photo"); await("Remove profile photo?"); click("Cancel"); await("Change Photo"); assertEquals(0, removed)
                click("Remove Photo"); await("Remove profile photo?"); click("Remove Photo"); await("Change Photo"); assertEquals(1, removed)
                instrumentation.runOnMainSync { activity.setContent { OjtTheme(mode) { androidx.compose.material3.Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    DashboardScreen(OjtState(profile = PersonalProfile(displayName = "Preview Student", requiredHours = 486)), (activity.application as OjtApplication).journal.photos, {}, {}, {}, {}, avatar = photo)
                } } } }
                await("Your OJT progress at a glance"); await("Profile photo"); screenshot("p1111-dashboard-${mode.name.lowercase()}.png")
            }
            instrumentation.runOnMainSync { activity.setContent { OjtTheme(ThemeMode.LIGHT) { ProfileScreen(PersonalProfile(displayName = "Default Student"), {}, {}, avatar = File(photo.parentFile, "missing.jpg")) } } }
            await("DS"); screenshot("p1111-profile-fallback.png")
            val after = personalSnapshot()
            assertTrue("Personal records and photo references unchanged", before == after)
        } finally { photo.delete() }
    }
}
