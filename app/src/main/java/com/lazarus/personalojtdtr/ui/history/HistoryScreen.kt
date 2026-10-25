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
import com.lazarus.personalojtdtr.util.*

@Composable fun HistoryScreen(state: OjtState, photos: LocalAttendancePhotos, onShare: (AttendanceEvent) -> Unit) {
    val legacyDaily = AttendanceCalculations.dailyHours(state.legacySessions)
    val dates = (state.events.map { it.date } + state.legacySessions.map { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() } + legacyDaily.keys + state.resolutions.map { it.date }).distinct().sortedDescending()
    var preview by remember { mutableStateOf<AttendanceEvent?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Text("Actual event times • Asia/Manila • device clock")
            }
        }
        if (dates.isEmpty()) item { Text("No attendance has been recorded yet.") }
        dates.forEach { date ->
            val events = state.events.filter { it.date == date }
            val (morning, afternoon) = DailyAttendance.daily(date, events, state.resolutions)
            item(key = "$date-heading") { Text(date.toString(), style = MaterialTheme.typography.headlineSmall) }
            item(key = "$date-hours") { InfoCard("Completed hours") {
                Text("Morning: ${morning.hoursLabel()}")
                Text("Afternoon: ${afternoon.hoursLabel()}")
                Text("Four-event daily total: ${morning.plus(afternoon).hoursLabel()}")
                legacyDaily[date]?.let { Text("Preserved legacy P1 hours: ${it.hoursLabel()}") }
                Text("Incomplete sessions contribute zero. Lunch gaps are excluded.")
            } }
            state.resolutions.filter { it.date == date }.forEach { action ->
                item(key = "resolution-${action.id}") { InfoCard("${action.session.label}: ${action.kind.label}") {
                    Text("Audit action: ${action.actedAt.manilaLabel()}")
                    Text("No additional credited hours. Original events/photos are preserved; no missing Time Out or photo was fabricated.")
                } }
            }
            AttendanceEventType.entries.forEach { type ->
                val event = events.firstOrNull { it.type == type }
                item(key = "$date-${type.code}") { InfoCard(type.label) {
                    if (event == null) {
                        val resolution = state.resolutions.firstOrNull { it.date == date && it.session.code == type.code / 2 }
                        Text("${resolution?.kind?.label ?: "Not recorded"} • no timestamp or photo")
                    }
                    else {
                        Text("${event.occurredAt.manilaLabel()} • completed")
                        LocalPhoto(remember(event.timestampedPhotoPath) { runCatching { photos.resolve(event.timestampedPhotoPath) }.getOrNull() }, type.label, Modifier.fillMaxWidth().height(140.dp))
                        Row {
                            TextButton(onClick = { preview = event }) { Text("View photos") }
                            TextButton(onClick = { onShare(event) }) { Text("Share photo") }
                        }
                    }
                } }
            }
            state.legacySessions.filter { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == date }.forEach { session ->
                item(key = "legacy-${session.id}") { InfoCard("Legacy P1 session • preserved") {
                    Text("In: ${session.timeIn.manilaLabel()}")
                    Text(session.timeOut?.let { "Out: ${it.manilaLabel()}" } ?: "Incomplete • Time Out not recorded")
                    Text("Legacy break: ${session.breakDuration.toMinutes()}m • Net: ${AttendanceCalculations.duration(session).hoursLabel()}")
                    Text("This record has not been converted into four photographed events.")
                } }
            }
        }
    }
    preview?.let { event -> EventPhotosDialog(event, photos, { onShare(event) }, { preview = null }) }
}
