package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    Page("Attendance", "$date • Asia/Manila • device clock") {
        Text("Morning and afternoon are session labels. Record each event at its actual time; there is no noon cutoff.")
        InfoCard("Today's completed hours") {
            Text("Morning: ${morning.hoursLabel()}")
            Text("Afternoon: ${afternoon.hoursLabel()}")
            Text("Daily total: ${morning.plus(afternoon).hoursLabel()}")
            Text("The gap between sessions is excluded. No extra lunch break is deducted.")
        }
        unfinished.forEach { start ->
            InfoCard("Resolve ${start.date} • ${OjtSession.entries[start.type.code / 2].label}") {
                Text("A previous-day session has no Time Out. Mark it incomplete/unverified to unblock attendance. It earns zero hours; recorded events and photos stay unchanged.")
                state.events.filter { it.date == start.date }.forEach { event ->
                    Text("${event.type.label}: ${event.occurredAt.manilaLabel()}")
                    LocalPhoto(remember(event.originalPhotoPath) { runCatching { photos.resolve(event.originalPhotoPath) }.getOrNull() }, event.type.label, Modifier.fillMaxWidth().height(120.dp))
                    TextButton(onClick = { preview = event }) { Text("View recorded photos") }
                }
                Button(enabled = !busy, onClick = { resolutionRequest = Triple(start.date, OjtSession.entries[start.type.code / 2], ResolutionKind.INCOMPLETE) }) { Text("Mark incomplete / unverified") }
            }
        }
        OjtSession.entries.forEach { session ->
            val action = state.resolutions.firstOrNull { it.date == date && it.session == session }
            if (action != null) InfoCard("${session.label}: ${action.kind.label}") { Text("Action recorded ${action.actedAt.manilaLabel()} • no additional credited hours") }
            else if (state.events.none { it.date == date && it.type.code / 2 == session.code }) {
                val previousResolved = session == OjtSession.MORNING || state.events.any { it.date == date && it.type == AttendanceEventType.MORNING_TIME_OUT } || state.resolutions.any { it.date == date && it.session == OjtSession.MORNING }
                OutlinedButton(enabled = !busy && previousResolved, onClick = { resolutionRequest = Triple(date, session, ResolutionKind.SKIPPED) }) { Text("${session.label}: skip / not worked") }
            }
        }
        if (legacy != null) {
            var breakText by rememberSaveable(legacy.id) { mutableStateOf(legacy.breakDuration.toMinutes().toString()) }
            val minutes = breakText.toLongOrNull()
            InfoCard("Unfinished legacy P1 session") {
                Text("Time In: ${legacy.timeIn.manilaLabel()}. Close this original session before starting four-event attendance.")
                OutlinedTextField(value = breakText, onValueChange = { breakText = it }, label = { Text("Actual legacy break (minutes)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(onClick = { minutes?.let(onCloseLegacy) }, enabled = !busy && minutes != null && minutes in 0..1440) { Text("Close legacy P1 session") }
            }
        }
        AttendanceEventType.entries.forEach { type ->
            val event = state.events.firstOrNull { it.date == date && it.type == type }
            InfoCard(type.label) {
                if (event == null) {
                    val resolution = state.resolutions.firstOrNull { it.date == date && it.session.code == type.code / 2 }
                    Text(resolution?.kind?.label ?: if (next == type) "Next event • not recorded" else "Not recorded")
                    Button(onClick = { onCapture(date, type) }, enabled = !busy && next == type && !earlierOpen && legacy == null) { Text("Capture ${type.label}") }
                } else {
                    Text("Completed • ${event.occurredAt.manilaLabel()}")
                    LocalPhoto(remember(event.timestampedPhotoPath) { runCatching { photos.resolve(event.timestampedPhotoPath) }.getOrNull() }, type.label, Modifier.fillMaxWidth().height(160.dp))
                    Row {
                        TextButton(onClick = { preview = event }) { Text("View photos") }
                        TextButton(onClick = { onShare(event) }) { Text("Share photo") }
                    }
                }
            }
        }
        if (next == null && state.events.count { it.date == date } == 4) Text("All four attendance events are complete.")
    }
    preview?.let { event -> EventPhotosDialog(event, photos, { onShare(event) }, { preview = null }) }

    resolutionRequest?.let { (day, session, kind) ->
        AlertDialog(onDismissRequest = { resolutionRequest = null }, title = { Text("${session.label}: ${kind.label}?") },
            text = { Text("$day\nThis records an explicit audit action at the current device time. It creates no attendance timestamp or photograph, preserves existing events, and awards no hours for this session. This action cannot be edited accidentally afterward.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onResolve(day, session, kind); resolutionRequest = null }) { Text("Confirm ${kind.label}") } },
            dismissButton = { TextButton(onClick = { resolutionRequest = null }) { Text("Cancel") } })
    }
}
