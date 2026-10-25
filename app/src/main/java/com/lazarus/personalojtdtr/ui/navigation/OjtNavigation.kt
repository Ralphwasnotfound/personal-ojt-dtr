package com.lazarus.personalojtdtr.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import com.lazarus.personalojtdtr.util.CreateLocalBackup
import com.lazarus.personalojtdtr.util.OpenLocalBackup
import java.time.Instant
import com.lazarus.personalojtdtr.util.today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.lazarus.personalojtdtr.ui.attendance.CameraCaptureScreen
import com.lazarus.personalojtdtr.util.manilaLabel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.dashboard.DashboardScreen
import com.lazarus.personalojtdtr.ui.attendance.AttendanceScreen
import com.lazarus.personalojtdtr.ui.activity.ActivitiesScreen
import com.lazarus.personalojtdtr.ui.activity.ActivityViewModel
import com.lazarus.personalojtdtr.ui.history.HistoryScreen
import com.lazarus.personalojtdtr.ui.profile.ProfileScreen

private val destinations = listOf("dashboard" to "Home", "attendance" to "Attend", "activities" to "Activities", "history" to "History", "profile" to "Profile")
@Composable fun OjtNavigation(model: OjtViewModel, activitiesModel: ActivityViewModel) {
    val journalMessage by activitiesModel.message.collectAsStateWithLifecycle()
    val journalBusy by activitiesModel.busy.collectAsStateWithLifecycle()
    val restoreRevision by model.restoreRevision.collectAsStateWithLifecycle()
    val backupUi by model.backupUi.collectAsStateWithLifecycle()
    val exportDocument = rememberLauncherForActivityResult(remember { CreateLocalBackup() }) { it?.let(model::exportBackup) }
    val importDocument = rememberLauncherForActivityResult(remember { OpenLocalBackup() }) { it?.let(model::inspectBackup) }
    val context = LocalContext.current
    val capture by model.capture.collectAsStateWithLifecycle()
    val confirmation by model.confirmation.collectAsStateWithLifecycle()
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val nav = key(restoreRevision) { rememberNavController() }
    val entry by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(restoreRevision) { if (restoreRevision > 0) activitiesModel.restored() }
    LaunchedEffect(journalMessage) { journalMessage?.let { snackbar.showSnackbar(it); activitiesModel.dismissMessage() } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        NavigationBar {
            destinations.forEach { (route, label) ->
                NavigationBarItem(selected = entry?.destination?.route == route,
                    enabled = state.loaded && state.loadError == null && !busy && !journalBusy,
                    onClick = { nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    } }, icon = { AppIcon(when (route) { "dashboard" -> AppSymbol.HOME; "attendance" -> AppSymbol.ATTENDANCE; "activities" -> AppSymbol.ACTIVITIES; "history" -> AppSymbol.HISTORY; else -> AppSymbol.PROFILE }, null) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loadError != null -> Page("Local data unavailable", state.loadError.orEmpty()) {
                    Text("Restart the app to retry. Your records have not been deleted.")
                }
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(state, activitiesModel.photos,
                        onProfile = { nav.navigate("profile") { launchSingleTop = true } },
                        onActivities = { activitiesModel.detail(null); nav.navigate("activities") { launchSingleTop = true } },
                        onActivity = { activitiesModel.detail(it); nav.navigate("activities") { launchSingleTop = true } }) }
                    composable("attendance") { AttendanceScreen(state, busy, model.photos, model::openCapture, { model.share(context, it) }, model::timeOut, model::resolve) }
                    composable("activities") { ActivitiesScreen(state.activities, busy, activitiesModel) }
                    composable("history") { HistoryScreen(state, model.photos) { model.share(context, it) } }
                    composable("profile") { ProfileScreen(state.profile, busy, model::saveProfile,
                        { exportDocument.launch("MyOJTCompanion-${today()}.zip") },
                        { importDocument.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }) }
                }
            }
        }
    }

    capture?.let {
        CameraCaptureScreen(it, model::captureStarted, model::captureSaved, model::captureFailed, model::retake, model::confirmCapture, model::cancelCapture)
    }
    confirmation?.let { event ->
        AlertDialog(onDismissRequest = model::dismissConfirmation, title = { Text("Attendance saved") },
            text = { Text("${event.type.label}\n${event.occurredAt.manilaLabel()}\nSharing is optional. Choose your destination and press Send in the selected app.") },
            confirmButton = { TextButton(onClick = { model.share(context, event); model.dismissConfirmation() }) { Text("Share photo") } },
            dismissButton = { TextButton(onClick = model::dismissConfirmation) { Text("Done") } })
    }

    backupUi.label?.let { label ->
        AlertDialog(onDismissRequest = { if (!backupUi.applying) model.cancelBackup() }, title = { Text(label) },
            text = { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (backupUi.applying) "Finishing an atomic replacement. Please wait." else "No current attendance is changed during validation/export.") } },
            confirmButton = { if (!backupUi.applying) TextButton(onClick = model::cancelBackup) { Text("Cancel operation") } })
    }
    backupUi.stage?.let { stage ->
        val preview = stage.records.preview()
        AlertDialog(onDismissRequest = model::cancelRestorePreview, title = { Text("Replace local data?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                backupUi.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Backup: ${Instant.ofEpochMilli(preview.createdAt).manilaLabel()}")
                Text("Profile: ${preview.displayName ?: "Not configured"}")
                Text("Required hours: ${preview.requiredHours}")
                stage.records.profile?.let {
                    Text("Start date: ${it.startDate?.let(java.time.LocalDate::ofEpochDay) ?: "Not set"}")
                    Text("Legacy default break: ${it.defaultBreakSeconds / 60} minutes")
                }
                Text("Attendance records: ${preview.attendanceCount}")
                Text("Activities: ${preview.activityCount} • Photos: ${preview.photoCount}")
                Text("Recovery/exception actions: ${preview.resolutionCount}")
                Text("REPLACE mode: all current profile/settings, attendance, activities and photo references will be replaced. Export a backup first if you want to keep them. There is no automatic merge.")
                Text("Backup archives are not automatically encrypted. Keep the source archive secure; restore does not delete it.")
            } }, confirmButton = { TextButton(onClick = model::confirmRestore, enabled = !busy) { Text("Replace all local data") } },
            dismissButton = { TextButton(onClick = model::cancelRestorePreview, enabled = !busy) { Text("Cancel restore") } })
    }
}
