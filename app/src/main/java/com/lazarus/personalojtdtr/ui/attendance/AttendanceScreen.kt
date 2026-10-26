package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import com.lazarus.personalojtdtr.data.photos.LocalAttendancePhotos
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.util.*
import java.time.LocalDate

@Composable fun AttendanceScreen(state: OjtState, busy: Boolean, photos: LocalAttendancePhotos,
    onCapture: (LocalDate, AttendanceEventType) -> Unit, onShare: (AttendanceEvent) -> Unit, onCloseLegacy: (Long) -> Unit, onResolve: (LocalDate, OjtSession, ResolutionKind) -> Unit) {
    var date by remember { mutableStateOf(today()) }
    LaunchedEffect(Unit) { while (true) { date = today(); kotlinx.coroutines.delay(1000) } }
    val next = DailyAttendance.next(date, state.events, state.resolutions)
    val earlierOpen = DailyAttendance.sessions(state.events, state.resolutions).any { it.timeOut == null && it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() != date }
    val legacy = state.legacySessions.firstOrNull { it.timeOut == null }
    val (morning, afternoon) = DailyAttendance.daily(date, state.events, state.resolutions)
    var resolutionRequest by remember { mutableStateOf<Triple<LocalDate, OjtSession, ResolutionKind>?>(null) }
    val unfinished = AttendanceRecovery.unfinishedBefore(date, state.events, state.resolutions)
    var preview by remember { mutableStateOf<AttendanceEvent?>(null) }
    Page("Attendance", "$date · Asia/Manila · device clock") {
        RefinedSurface {
            SectionHeading("Today's completed hours", AppSymbol.COMPLETED)
            Text(morning.plus(afternoon).hoursLabel(), style = MaterialTheme.typography.headlineMedium)
            Text("Morning ${morning.hoursLabel()} · Afternoon ${afternoon.hoursLabel()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Actual completed intervals only. The lunch gap is excluded without an extra deduction.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        unfinished.forEach { start ->
            HorizontalDivider()
            Section("Recovery required · ${start.date}", AppSymbol.RESTORE) {
                StatusPill("Action needed", AppSymbol.RESTORE, emphasized = true)
                Text("${OjtSession.entries[start.type.code / 2].label} has no Time Out. Mark it incomplete/unverified to continue. It earns zero hours; the original events and photos stay unchanged.")
                state.events.filter { it.date == start.date }.forEach { event ->
                    Text("${event.type.label}: ${event.occurredAt.manilaLabel()}", style = MaterialTheme.typography.bodyMedium)
                    LocalPhoto(remember(event.originalPhotoPath) { runCatching { photos.resolve(event.originalPhotoPath) }.getOrNull() }, event.type.label, Modifier.fillMaxWidth().height(96.dp), maxEdge = 320)
                    QuietButton(onClick = { preview = event }) { ActionLabel(AppSymbol.PHOTO, "View recorded photos", compact = true) }
                }
                QuietButton(enabled = !busy, onClick = { resolutionRequest = Triple(start.date, OjtSession.entries[start.type.code / 2], ResolutionKind.INCOMPLETE) }) { ActionLabel(AppSymbol.RESTORE, "Mark incomplete / unverified", compact = true) }
            }
        }
        if (legacy != null) {
            var breakText by rememberSaveable(legacy.id) { mutableStateOf(legacy.breakDuration.toMinutes().toString()) }
            val minutes = breakText.toLongOrNull()
            Section("Unfinished legacy P1 session", AppSymbol.HISTORY) {
                Text("Time In: ${legacy.timeIn.manilaLabel()}. Close this original session before starting four-event attendance.")
                TextField(value = breakText, onValueChange = { breakText = it }, label = { Text("Actual legacy break (minutes)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
                Button(onClick = { minutes?.let(onCloseLegacy) }, enabled = !busy && minutes != null && minutes in 0..1440,
                    modifier = Modifier.fillMaxWidth()) { ActionLabel(AppSymbol.TIME_OUT, "Close legacy P1 session", compact = true) }
            }
        }
        HorizontalDivider()
        Column {
            SectionHeading("Today's timeline", AppSymbol.ATTENDANCE)
            Text("Record each event at its actual time. Morning and afternoon have no noon cutoff.", Modifier.padding(vertical = AppSpacing.Related), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AttendanceEventType.entries.forEach { type ->
                val event = state.events.firstOrNull { it.date == date && it.type == type }
                val resolution = state.resolutions.firstOrNull { it.date == date && it.session.code == type.code / 2 }
                val available = event == null && next == type && !earlierOpen && legacy == null
                val status = when {
                    event != null -> "Completed"
                    resolution != null -> resolution.kind.label
                    next == type && (earlierOpen || legacy != null) -> "Recovery required"
                    available -> "Available · next event"
                    else -> "Pending"
                }
                TimelineRow(type.label, event?.occurredAt?.manilaLabel().orEmpty(), status, event != null,
                    available, last = type == AttendanceEventType.AFTERNOON_TIME_OUT,
                    symbol = if (type.code % 2 == 0) AppSymbol.TIME_IN else AppSymbol.TIME_OUT) {
                    if (event != null) {
                        PhotoEvidence(remember(event.timestampedPhotoPath) { runCatching { photos.resolve(event.timestampedPhotoPath) }.getOrNull() }, type.label,
                            onView = { preview = event }, onShare = { onShare(event) })
                    }
                }
            }
            if (next != null && legacy == null) Button(onClick = { onCapture(date, next) },
                enabled = !busy && !earlierOpen, modifier = Modifier.fillMaxWidth().heightIn(min = AppSpacing.Touch)) {
                ActionLabel(AppSymbol.CAMERA, "Capture ${next.label}")
            }
            if (next == null && state.events.count { it.date == date } == 4) Text("All four attendance events are complete.")
        }
        if (state.resolutions.any { it.date == date } || OjtSession.entries.any { session -> state.events.none { it.date == date && it.type.code / 2 == session.code } }) Section("Session exceptions", AppSymbol.WARNING) {
            OjtSession.entries.forEach { session ->
                val action = state.resolutions.firstOrNull { it.date == date && it.session == session }
                if (action != null) {
                    Text("${session.label}: ${action.kind.label}", style = MaterialTheme.typography.titleSmall)
                    Text("Recorded ${action.actedAt.manilaLabel()} · no additional credited hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (state.events.none { it.date == date && it.type.code / 2 == session.code }) {
                    val previousResolved = session == OjtSession.MORNING || state.events.any { it.date == date && it.type == AttendanceEventType.MORNING_TIME_OUT } || state.resolutions.any { it.date == date && it.session == OjtSession.MORNING }
                    QuietButton(enabled = !busy && previousResolved, onClick = { resolutionRequest = Triple(date, session, ResolutionKind.SKIPPED) }) { ActionLabel(AppSymbol.RESTORE, "${session.label}: skip / not worked", compact = true) }
                }
            }
        }
    }
    preview?.let { event -> EventPhotosDialog(event, photos, { onShare(event) }, { preview = null }) }

    resolutionRequest?.let { (day, session, kind) ->
        AlertDialog(onDismissRequest = { resolutionRequest = null }, title = { DialogSystemBars(); Text("${session.label}: ${kind.label}?") },
            text = { Text("$day\nThis records an explicit audit action at the current device time. It creates no attendance timestamp or photograph, preserves existing events, and awards no hours for this session. This action cannot be edited accidentally afterward.") },
            confirmButton = { QuietButton(enabled = !busy, onClick = { onResolve(day, session, kind); resolutionRequest = null }) { ActionLabel(AppSymbol.CHECK, "Confirm ${kind.label}", compact = true) } },
            dismissButton = { QuietButton(onClick = { resolutionRequest = null }) { ActionLabel(AppSymbol.CLOSE, "Cancel", compact = true) } })
    }
}
