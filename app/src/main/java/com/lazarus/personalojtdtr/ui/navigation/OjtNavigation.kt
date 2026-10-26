package com.lazarus.personalojtdtr.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import com.lazarus.personalojtdtr.util.CreateLocalBackup
import com.lazarus.personalojtdtr.util.OpenLocalBackup
import java.time.Instant
import com.lazarus.personalojtdtr.util.today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import androidx.compose.ui.platform.LocalContext
import com.lazarus.personalojtdtr.ui.attendance.CameraCaptureScreen
import com.lazarus.personalojtdtr.util.manilaLabel
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
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
import com.lazarus.personalojtdtr.WidgetNavigationRequest

private val destinations = listOf("dashboard" to "Home", "attendance" to "Attend", "activities" to "Activities", "history" to "History", "profile" to "Profile")
@Composable fun OjtNavigation(model: OjtViewModel, activitiesModel: ActivityViewModel,
    appearance: com.lazarus.personalojtdtr.ui.theme.ThemeSettings,
    onThemeSelected: (com.lazarus.personalojtdtr.domain.model.ThemeMode) -> Unit,
    widgetNavigation: WidgetNavigationRequest? = null, onWidgetNavigationConsumed: () -> Unit = {}) {
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
    LaunchedEffect(widgetNavigation, state.loaded, state.loadError, busy, journalBusy, restoreRevision) {
        if (widgetNavigation != null && state.loaded && state.loadError == null && !busy && !journalBusy) {
            if (widgetNavigation.destination.route == "activities") activitiesModel.detail(null)
            nav.navigate(widgetNavigation.destination.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onWidgetNavigationConsumed()
        }
    }
    LaunchedEffect(restoreRevision) { if (restoreRevision > 0) activitiesModel.restored() }
    LaunchedEffect(journalMessage) { journalMessage?.let { snackbar.showSnackbar(it); activitiesModel.dismissMessage() } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            destinations.forEach { (route, label) ->
                NavigationBarItem(colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant), selected = entry?.destination?.route == route,
                    enabled = state.loaded && state.loadError == null && !busy && !journalBusy,
                    onClick = { nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    } }, icon = { AppIcon(when (route) { "dashboard" -> AppSymbol.HOME; "attendance" -> AppSymbol.ATTENDANCE; "activities" -> AppSymbol.ACTIVITIES; "history" -> AppSymbol.HISTORY; else -> AppSymbol.PROFILE }, null) }, label = { Text(label) })
            }
        }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                state.loadError != null -> Page("Local data unavailable", state.loadError.orEmpty()) {
                    Text("Restart the app to retry. Your records have not been deleted.")
                }
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(state, activitiesModel.photos,
                        onProfile = { nav.navigate("profile") { launchSingleTop = true } },
                        onAttendance = { nav.navigate("attendance") { launchSingleTop = true } },
                        onActivities = { activitiesModel.detail(null); nav.navigate("activities") { launchSingleTop = true } },
                        onActivity = { activitiesModel.detail(it); nav.navigate("activities") { launchSingleTop = true } }) }
                    composable("attendance") { AttendanceScreen(state, busy, model.photos, model::openCapture, { model.share(context, it) }, model::timeOut, model::resolve) }
                    composable("activities") { ActivitiesScreen(state.activities, busy, activitiesModel) }
                    composable("history") { HistoryScreen(state, model.photos, onAttendance = { nav.navigate("attendance") { launchSingleTop = true } }) { model.share(context, it) } }
                    composable("profile") { ProfileScreen(state.profile, busy, model::saveProfile,
                        { exportDocument.launch("MyOJTCompanion-${today()}.zip") },
                        { importDocument.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, appearance, onThemeSelected) }
                }
            }
        }
    }

    capture?.let {
        CameraCaptureScreen(it, model::captureStarted, model::captureSaved, model::captureFailed, model::retake, model::confirmCapture, model::cancelCapture)
    }
    confirmation?.let { event ->
        AlertDialog(onDismissRequest = model::dismissConfirmation, title = { DialogSystemBars(); Text("Attendance saved") },
            text = { Text("${event.type.label}\n${event.occurredAt.manilaLabel()}\nSharing is optional. Choose your destination and press Send in the selected app.") },
            confirmButton = { QuietButton(onClick = { model.share(context, event); model.dismissConfirmation() }) { ActionLabel(AppSymbol.SHARE, "Share photo", compact = true) } },
            dismissButton = { QuietButton(onClick = model::dismissConfirmation) { ActionLabel(AppSymbol.CHECK, "Done", compact = true) } })
    }

    backupUi.label?.let { label ->
        AlertDialog(onDismissRequest = { if (!backupUi.applying) model.cancelBackup() }, title = { DialogSystemBars(); Text(label) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(if (backupUi.applying) "Finishing an atomic replacement. Please wait." else "No current attendance is changed during validation/export.") } },
            confirmButton = { if (!backupUi.applying) QuietButton(onClick = model::cancelBackup) { ActionLabel(AppSymbol.CLOSE, "Cancel operation", compact = true) } })
    }
    backupUi.stage?.let { stage ->
        val preview = stage.records.preview()
        AlertDialog(onDismissRequest = model::cancelRestorePreview, title = { DialogSystemBars(); Text("Replace local data?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
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
                Text("REPLACE mode: the current profile/OJT settings, attendance, activities and photo references will be replaced. Export a backup first if you want to keep them. There is no automatic merge. Appearance is kept unchanged.")
                Text("Backup archives are not automatically encrypted. Keep the source archive secure; restore does not delete it.")
            } }, confirmButton = { DestructiveButton(onClick = model::confirmRestore, enabled = !busy, text = "Replace all local data", symbol = AppSymbol.RESTORE) },
            dismissButton = { QuietButton(onClick = model::cancelRestorePreview, enabled = !busy) { ActionLabel(AppSymbol.CLOSE, "Cancel restore", compact = true) } })
    }
}
