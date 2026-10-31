package com.lazarus.personalojtdtr

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.activity.SystemBarStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme
import com.lazarus.personalojtdtr.ui.theme.ThemeSettingsViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazarus.personalojtdtr.ui.OjtViewModel
import com.lazarus.personalojtdtr.ui.activity.ActivityViewModel
import com.lazarus.personalojtdtr.ui.navigation.OjtNavigation
import com.lazarus.personalojtdtr.ui.theme.OjtTheme
import com.lazarus.personalojtdtr.domain.model.WidgetDestination

data class WidgetNavigationRequest(val destination: WidgetDestination, val sequence: Long, val camera: Boolean = false, val reminderDate: String? = null, val reminderSession: Int? = null, val skip: Boolean = false)

class MainActivity : ComponentActivity() {
    private var widgetNavigation by mutableStateOf<WidgetNavigationRequest?>(null)
    private var navigationSequence = 0L
    private fun acceptShortcut(intent: Intent) {
        com.lazarus.personalojtdtr.reminders.ReminderNavigation.parse(intent.action, intent.getStringExtra("reminder_date"), intent.getIntExtra("reminder_session", -1))?.let { link ->
            widgetNavigation = WidgetNavigationRequest(WidgetDestination.ATTENDANCE, ++navigationSequence,
                !link.skip, link.date.toString(), link.session.code, link.skip)
            return
        }
        WidgetDestination.fromAction(intent.action)?.let {
            widgetNavigation = WidgetNavigationRequest(it, ++navigationSequence,
                intent.getBooleanExtra("widget_camera", false))
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShortcut(intent)
    }
    override fun onStart() {
        super.onStart()
        (application as OjtApplication).widgetPublisher.refresh()
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.lazarus.personalojtdtr.reminders.AttendanceReminders.reconcile(this@MainActivity) }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptShortcut(intent)
        enableEdgeToEdge()
        setContent {
            val themeModel: ThemeSettingsViewModel = viewModel(factory = ThemeSettingsViewModel.factory((application as OjtApplication).themePreferences))
            val appearance by themeModel.state.collectAsStateWithLifecycle()
            // Wait for the persisted choice before composing navigation in the wrong theme.
            val mode = appearance.mode ?: return@setContent
            OjtTheme(mode) {
                val dark = LocalOjtDarkTheme.current
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x80171517.toInt()) { dark },
                    )
                }
                val model: OjtViewModel = viewModel(factory = OjtViewModel.factory((application as OjtApplication).repository, (application as OjtApplication).recorder, (application as OjtApplication).backup, (application as OjtApplication).profileAvatars))
                val activities: ActivityViewModel = viewModel(factory = ActivityViewModel.factory((application as OjtApplication).journal))
                OjtNavigation(model, activities, appearance, themeModel::select, widgetNavigation) {
                    widgetNavigation = null
                    // Do not replay a consumed shortcut on activity recreation.
                    setIntent(Intent(intent).setAction(Intent.ACTION_MAIN))
                }
            }
        }
    }
}
