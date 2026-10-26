package com.lazarus.personalojtdtr.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.data.photos.LocalAttendancePhotos
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.attendance.*
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import com.lazarus.personalojtdtr.util.*

@Composable fun HistoryScreen(state: OjtState, photos: LocalAttendancePhotos, onAttendance: () -> Unit, onShare: (AttendanceEvent) -> Unit) {
    val legacyDaily = AttendanceCalculations.dailyHours(state.legacySessions)
    val dates = (state.events.map { it.date } + state.legacySessions.map { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() } + legacyDaily.keys + state.resolutions.map { it.date }).distinct().sortedDescending()
    var preview by remember { mutableStateOf<AttendanceEvent?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppSpacing.Page), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
        item { PageHeader("History", "Your attendance record · Asia/Manila · device clock") }
        if (dates.isEmpty()) item { EmptyState("No attendance yet", "Your recorded events and completed hours will appear here.", "Open attendance", onAttendance) }
        dates.forEach { date ->
            val events = state.events.filter { it.date == date }
            val (morning, afternoon) = DailyAttendance.daily(date, events, state.resolutions)
            item(key = "$date-heading") {
                Column(Modifier.padding(top = AppSpacing.Section), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                    SectionHeading(ActivityRules.dateLabel(date), AppSymbol.CALENDAR)
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                        AppIcon(AppSymbol.COMPLETED, modifier = Modifier.size(24.dp))
                        Text(morning.plus(afternoon).hoursLabel(), style = MaterialTheme.typography.headlineSmall)
                    }
                    Text("Four-event total · Morning ${morning.hoursLabel()} · Afternoon ${afternoon.hoursLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    legacyDaily[date]?.let { Text("Preserved legacy P1 hours: ${it.hoursLabel()}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Incomplete sessions add zero hours. Lunch gaps are excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.resolutions.filter { it.date == date }.forEach { action ->
                item(key = "resolution-${action.id}") {
                    Section("${action.session.label}: ${action.kind.label}") {
                        StatusPill(action.kind.label, AppSymbol.RESTORE)
                        Text("Audit action: ${action.actedAt.manilaLabel()}", style = MaterialTheme.typography.bodySmall)
                        Text("No additional credited hours. Original events and photos are preserved; no missing Time Out or photo was created.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AttendanceEventType.entries.forEach { type ->
                val event = events.firstOrNull { it.type == type }
                item(key = "$date-${type.code}") {
                    val resolution = state.resolutions.firstOrNull { it.date == date && it.session.code == type.code / 2 }
                    TimelineRow(type.label, event?.occurredAt?.manilaLabel() ?: "No timestamp or photo",
                        if (event != null) "Completed" else resolution?.kind?.label ?: "Not recorded", event != null,
                        last = type == AttendanceEventType.AFTERNOON_TIME_OUT,
                        symbol = if (type.code % 2 == 0) AppSymbol.TIME_IN else AppSymbol.TIME_OUT) {
                        if (event != null) {
                            PhotoEvidence(remember(event.timestampedPhotoPath) { runCatching { photos.resolve(event.timestampedPhotoPath) }.getOrNull() }, type.label,
                                onView = { preview = event }, onShare = { onShare(event) })
                        }
                    }
                }
            }
            state.legacySessions.filter { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == date }.forEach { session ->
                item(key = "legacy-${session.id}") {
                    Section("Legacy P1 session · preserved", AppSymbol.HISTORY) {
                        Text("In: ${session.timeIn.manilaLabel()}")
                        Text(session.timeOut?.let { "Out: ${it.manilaLabel()}" } ?: "Incomplete · Time Out not recorded")
                        Text("Break ${session.breakDuration.toMinutes()}m · Net ${AttendanceCalculations.duration(session).hoursLabel()}")
                        Text("This record has not been converted into four photographed events.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    preview?.let { event -> EventPhotosDialog(event, photos, { onShare(event) }, { preview = null }) }
}
