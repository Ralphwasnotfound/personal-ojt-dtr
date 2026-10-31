package com.lazarus.personalojtdtr.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lazarus.personalojtdtr.data.preferences.OjtSchedule
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.attendance.OjtCalendarControls
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import com.lazarus.personalojtdtr.util.hoursLabel

@Composable fun ForecastSettingsScreen(state: OjtState, schedule: OjtSchedule, busy: Boolean,
    onSaveSchedule: (Int?, Set<DayOfWeek>) -> Unit, onRequirement: () -> Unit, onSchedule: () -> Unit, onBack: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) now = Instant.now() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(now) {
        delay(Duration.between(now, WidgetSnapshot.nextMidnight(now)).toMillis().coerceAtLeast(1))
        now = Instant.now()
    }
    // Same read model, saved durations and forecast engine as the home-screen widget.
    val snapshot = remember(state, schedule, now) { WidgetSnapshot.from(WidgetInputs(state.profile.requiredHours,
        state.legacySessions, state.events, state.resolutions, schedule.hoursPerDay, schedule.weekdays, state.calendarOverrides), now) }
    val result = snapshot.forecast
    val label = when (result.method) {
        ForecastMethod.AUTOMATIC, ForecastMethod.MANUAL_OVERRIDE -> result.estimatedCompletionDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: "Estimate unavailable"
        ForecastMethod.COMPLETED -> "OJT completed"
        ForecastMethod.NO_REQUIREMENT -> "Set required hours"
        ForecastMethod.SCHEDULE_REQUIRED -> "Set schedule"
        ForecastMethod.INSUFFICIENT_HISTORY -> "Collecting attendance data"
        ForecastMethod.PROJECTION_LIMIT -> "Estimate unavailable"
    }
    val explanation = when (result.method) {
        ForecastMethod.AUTOMATIC -> "Based on credited attendance and your working calendar. The completion date is an estimate."
        ForecastMethod.MANUAL_OVERRIDE -> "Uses your optional daily-hours override and working calendar. The completion date is an estimate."
        ForecastMethod.COMPLETED -> "Your configured OJT requirement has been reached."
        ForecastMethod.NO_REQUIREMENT -> "Set a nonzero required-hour goal using the existing profile editor."
        ForecastMethod.SCHEDULE_REQUIRED -> "Select your working weekdays in Work Schedule."
        ForecastMethod.INSUFFICIENT_HISTORY -> "Automatic forecasting needs at least ${CompletionForecasts.MINIMUM_AUTOMATIC_DAYS} distinct credited attendance days. You can configure optional manual daily hours below."
        ForecastMethod.PROJECTION_LIMIT -> "Review your working weekdays and calendar exceptions. A date could not be projected within the supported calendar horizon."
    }
    ProfileSubPage("Completion Forecast", "Your progress and estimated completion", "Back", onBack) {
        ProfileGroup("Current forecast", AppSymbol.PROGRESS) {
            Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Completed ${snapshot.completedLabel} · Remaining ${snapshot.remainingLabel}", style = MaterialTheme.typography.bodySmall)
            result.averageDailyCredit?.let { Text("Observed average: ${it.hoursLabel()} per attended day", style = MaterialTheme.typography.bodySmall) }
            result.estimatedRemainingWorkdays?.let { Text("Estimated remaining working dates: $it", style = MaterialTheme.typography.bodySmall) }
            result.earliestCreditedDate?.let { Text("First credited date: $it", style = MaterialTheme.typography.bodySmall) }
            if (schedule.weekdays.isEmpty() && result.projectedWeekdays.isNotEmpty()) Text("Tentative weekdays inferred from attendance: ${weekdayLabel(result.projectedWeekdays)}. Configure Work Schedule to confirm.", style = MaterialTheme.typography.bodySmall)
        }
        ProfileGroup("OJT requirement", AppSymbol.REMAINING) {
            SettingsActionRow("Required OJT hours", "${snapshot.requiredLabel} · Edit profile requirement", AppSymbol.EDIT, enabled = !busy, onClick = onRequirement)
        }
        ProfileGroup("Working calendar", AppSymbol.CALENDAR) {
            Text("Selected weekdays: ${weekdayLabel(schedule.weekdays)}", style = MaterialTheme.typography.bodyMedium)
            SettingsActionRow("Work Schedule", "Edit weekdays and calendar exceptions", AppSymbol.CALENDAR, onClick = onSchedule)
        }
        OjtScheduleEditor(schedule, onSaveSchedule, editWeekdays = false, busy = busy)
    }
}

private fun weekdayLabel(days: Set<DayOfWeek>) = days.sortedBy { it.value }.joinToString { it.name.lowercase().replaceFirstChar(Char::uppercase) }.ifEmpty { "Not configured" }

@Composable fun WorkScheduleSettingsScreen(state: OjtState, schedule: OjtSchedule, busy: Boolean,
    onSaveSchedule: (Int?, Set<DayOfWeek>) -> Unit,
    onSet: (LocalDate, CalendarOverrideKind, String?, Boolean, WorkdayScope) -> Unit,
    onClear: (LocalDate, WorkdayScope) -> Unit, onBack: () -> Unit) {
    var calendarRequest by remember { mutableStateOf(0L) }
    ProfileSubPage("Work Schedule", "Regular weekdays and date exceptions", "Back", onBack) {
        OjtScheduleEditor(schedule, onSaveSchedule, editWeekdays = true, busy = busy)
        ProfileGroup("OJT Calendar", AppSymbol.CALENDAR) {
            Text("Review rest days, company closures, session exceptions and special working dates. Daily actions remain on Attendance.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { calendarRequest++ }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Open OJT Calendar") }
        }
    }
    OjtCalendarControls(LocalDate.now(AttendanceCalculations.manila), state, schedule, busy, onSet, onClear, calendarRequest)
}
