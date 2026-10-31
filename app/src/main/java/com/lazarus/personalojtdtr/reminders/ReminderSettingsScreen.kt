package com.lazarus.personalojtdtr.reminders

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.domain.model.OjtSession
import kotlinx.coroutines.launch

@Composable fun ReminderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as OjtApplication).reminderPreferences
    val settings by prefs.settings.collectAsState(initial = ReminderSettings())
    val asked by prefs.permissionAsked.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var statusRevision by remember { mutableStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { statusRevision++ }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) statusRevision++ }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val allowed = remember(statusRevision) { AttendanceReminders.allowed(context) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(value: ReminderSettings) {
        if (!value.valid()) { error = "Use times from 00:30 with follow-ups before midnight."; return }
        error = null; scope.launch { prefs.save(value) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back to Settings") }
        Text("Attendance Reminders", style = MaterialTheme.typography.headlineSmall)
        Text("Local notifications are optional. Review the editable defaults (Morning 08:00, Afternoon 13:00) before enabling. Android may delay delivery.")
        Toggle("Enable reminders with these times", settings.enabled) { enabled ->
            save(settings.copy(enabled = enabled))
            if (enabled && !allowed && !asked && Build.VERSION.SDK_INT >= 33) scope.launch {
                prefs.askedPermission(); permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Text(if (allowed) "Notifications allowed" else "Notifications disabled or permission denied. Attendance remains available.")
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OjtSession.entries.forEach { session ->
            val morning = session == OjtSession.MORNING
            Text("${session.label} session", style = MaterialTheme.typography.titleMedium)
            Toggle("${session.label} reminders", if (morning) settings.morningEnabled else settings.afternoonEnabled) {
                save(if (morning) settings.copy(morningEnabled = it) else settings.copy(afternoonEnabled = it))
            }
            val start = settings.start(session)
            val time = "%02d:%02d".format(start / 60, start % 60)
            OutlinedButton(onClick = { TimePickerDialog(context, { _, hour, minute ->
                save(if (morning) settings.copy(morningStart = hour * 60 + minute) else settings.copy(afternoonStart = hour * 60 + minute))
            }, start / 60, start % 60, true).show() }) { Text("First attendance reminder: $time") }
            Toggle("${session.label} Get Ready", if (morning) settings.morningReady else settings.afternoonReady) {
                save(if (morning) settings.copy(morningReady = it) else settings.copy(afternoonReady = it))
            }
            Text("Get Ready lead time")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(5, 10, 15, 30).forEach { lead ->
                    FilterChip(selected = lead == if (morning) settings.morningLead else settings.afternoonLead,
                        onClick = { save(if (morning) settings.copy(morningLead = lead) else settings.copy(afternoonLead = lead)) }, label = { Text("${lead}m") })
                }
            }
        }
        HorizontalDivider()
        Toggle("Enable second reminders", settings.secondEnabled) { save(settings.copy(secondEnabled = it)) }
        Text("Second reminder delay after each session start")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(15, 30, 45, 60).forEach { delay -> FilterChip(selected = settings.secondDelay == delay,
                onClick = { save(settings.copy(secondDelay = delay)) }, label = { Text("${delay}m") }) }
        }
        Text("Remind Later adds one 10-minute snooze per session per day. Reminders expire 90 minutes after session start. A delayed first reminder is skipped when the follow-up is already due.", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onCheckedChange = onChange)
    }
}
