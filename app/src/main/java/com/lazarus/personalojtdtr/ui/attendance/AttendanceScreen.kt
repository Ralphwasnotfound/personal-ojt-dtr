package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.data.photos.LocalAttendancePhotos
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.theme.DialogSystemBars
import com.lazarus.personalojtdtr.util.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val attendanceClock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val AttendanceSuccess = Color(0xFF2E7D32)
private val AttendanceSuccessDark = Color(0xFF81C784)
private val AttendancePending = Color(0xFF9A6B12)
private val AttendancePendingDark = Color(0xFFE7C36A)

@Composable fun AttendanceScreen(state: OjtState, busy: Boolean, photos: LocalAttendancePhotos,
    onCapture: (LocalDate, AttendanceEventType) -> Unit, onShare: (AttendanceEvent) -> Unit,
    onCloseLegacy: (Long) -> Unit, onResolve: (LocalDate, OjtSession, ResolutionKind) -> Unit) {
    var date by remember { mutableStateOf(today()) }
    LaunchedEffect(Unit) { while (true) { date = today(); kotlinx.coroutines.delay(1000) } }
    val next = DailyAttendance.next(date, state.events, state.resolutions)
    val earlierOpen = DailyAttendance.sessions(state.events, state.resolutions).any {
        it.timeOut == null && it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() != date
    }
    val legacy = state.legacySessions.firstOrNull { it.timeOut == null }
    val (morning, afternoon) = DailyAttendance.daily(date, state.events, state.resolutions)
    var resolutionRequest by remember { mutableStateOf<Triple<LocalDate, OjtSession, ResolutionKind>?>(null) }
    val unfinished = AttendanceRecovery.unfinishedBefore(date, state.events, state.resolutions)
    var preview by remember { mutableStateOf<AttendanceEvent?>(null) }
    val todayEvents = state.events.filter { it.date == date }
    val page = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(page.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Attendance", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$date · Manila time", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = page.onSurfaceVariant)
                AttendanceStatus(statusLabel(next, todayEvents.size, earlierOpen, legacy != null, unfinished.isNotEmpty()),
                    recovery = earlierOpen || legacy != null || unfinished.isNotEmpty())
            }
        }

        AttendanceSummary(morning.plus(afternoon).hoursLabel(), morning.hoursLabel(), afternoon.hoursLabel())

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today’s events", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            AttendanceEventType.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top) {
                    row.forEach { type ->
                        val event = todayEvents.firstOrNull { it.type == type }
                        val resolution = state.resolutions.firstOrNull { it.date == date && it.session.code == type.code / 2 }
                        val available = event == null && resolution == null && next == type && !earlierOpen && legacy == null
                        AttendanceEventCard(type, event, resolution, available, Modifier.weight(1f), photos,
                            onView = { preview = event }, onShare = { event?.let(onShare) })
                    }
                }
            }
            val canCapture = next != null && legacy == null
            if (canCapture) {
                Button(onClick = { onCapture(date, next!!) }, enabled = !busy && !earlierOpen,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) {
                    ActionLabel(AppSymbol.CAMERA, "Capture ${next!!.label}")
                }
            } else if (next == null && todayEvents.size == 4) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(AppSymbol.CHECK, modifier = Modifier.size(20.dp))
                        Text("All four events recorded", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        unfinished.forEach { start ->
            RecoveryCard(start.date, state.events.filter { it.date == start.date }, photos, busy,
                onView = { preview = it }, onResolve = {
                    resolutionRequest = Triple(start.date, OjtSession.entries[start.type.code / 2], ResolutionKind.INCOMPLETE)
                })
        }

        if (legacy != null) {
            var breakText by rememberSaveable(legacy.id) { mutableStateOf(legacy.breakDuration.toMinutes().toString()) }
            val minutes = breakText.toLongOrNull()
            LegacyRecoveryCard(legacy, breakText, { breakText = it }, !busy && minutes != null && minutes in 0..1440) {
                minutes?.let(onCloseLegacy)
            }
        }

        if (state.resolutions.any { it.date == date } || OjtSession.entries.any { session ->
                todayEvents.none { it.type.code / 2 == session.code }
            }) {
            SessionExceptions(date, todayEvents, state.resolutions, busy) { session ->
                resolutionRequest = Triple(date, session, ResolutionKind.SKIPPED)
            }
        }
    }

    preview?.let { event -> EventPhotosDialog(event, photos, { onShare(event) }, { preview = null }) }
    resolutionRequest?.let { (day, session, kind) ->
        AlertDialog(onDismissRequest = { resolutionRequest = null },
            title = { DialogSystemBars(); Text("${session.label}: ${kind.label}?") },
            text = { Text("$day\nThis records an audit action at the current device time. It creates no attendance time or photo, preserves existing events, and awards no hours for this session. This action cannot be edited accidentally afterward.") },
            confirmButton = { QuietButton(enabled = !busy, onClick = { onResolve(day, session, kind); resolutionRequest = null }) {
                ActionLabel(AppSymbol.CHECK, "Confirm ${kind.label}", compact = true)
            } },
            dismissButton = { QuietButton(onClick = { resolutionRequest = null }) {
                ActionLabel(AppSymbol.CLOSE, "Cancel", compact = true)
            } })
    }
}

@Composable private fun AttendanceSummary(total: String, morning: String, afternoon: String) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = colors.surface,
        border = BorderStroke(0.5.dp, colors.outlineVariant), shadowElevation = 1.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Completed today", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Text(total, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.size(44.dp).background(colors.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    AppIcon(AppSymbol.COMPLETED, modifier = Modifier.size(23.dp))
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SessionTotal("Morning", morning, Modifier.weight(1f))
                SessionTotal("Afternoon", afternoon, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun SessionTotal(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun AttendanceStatus(statusLabel: String, recovery: Boolean) {
    val colors = MaterialTheme.colorScheme
    val dark = com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme.current
    val tint = when {
        recovery -> colors.error
        statusLabel == "Complete" -> if (dark) AttendanceSuccessDark else AttendanceSuccess
        statusLabel == "In progress" -> colors.primary
        else -> if (dark) AttendancePendingDark else AttendancePending
    }
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.10f), border = BorderStroke(0.5.dp, tint.copy(alpha = 0.35f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(tint, CircleShape))
            Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}

private fun statusLabel(next: AttendanceEventType?, count: Int, earlierOpen: Boolean, legacyOpen: Boolean, recovery: Boolean): String = when {
    recovery || earlierOpen || legacyOpen -> "Recovery"
    next == null && count == 4 -> "Complete"
    count == 0 -> "Not started"
    else -> "In progress"
}

@Composable private fun AttendanceEventCard(type: AttendanceEventType, event: AttendanceEvent?, resolution: SessionResolution?,
    available: Boolean, modifier: Modifier, photos: LocalAttendancePhotos, onView: () -> Unit, onShare: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme.current
    val recorded = event != null
    val stateColor = when {
        recorded -> if (dark) AttendanceSuccessDark else AttendanceSuccess
        resolution != null -> colors.onSurfaceVariant
        available -> colors.primary
        else -> if (dark) AttendancePendingDark else AttendancePending
    }
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = colors.surface,
        border = BorderStroke(0.7.dp, if (available) colors.primary.copy(alpha = .5f) else colors.outlineVariant),
        shadowElevation = if (available) 1.dp else 0.dp) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(if (type.code % 2 == 0) AppSymbol.TIME_IN else AppSymbol.TIME_OUT,
                    modifier = Modifier.size(17.dp))
                Text(type.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface, maxLines = 2)
            }
            Text(event?.occurredAt?.atZone(AttendanceCalculations.manila)?.format(attendanceClock) ?: "—",
                style = MaterialTheme.typography.titleMedium, color = if (recorded) colors.onSurface else colors.onSurfaceVariant,
                fontWeight = if (recorded) FontWeight.SemiBold else FontWeight.Normal)
            Text(when {
                event != null -> "Recorded"
                resolution != null -> resolution.kind.label
                available -> "Next event"
                else -> "Pending"
            }, style = MaterialTheme.typography.labelMedium, color = stateColor)
            if (event != null) {
                PhotoEvidence(remember(event.timestampedPhotoPath) {
                    runCatching { photos.resolve(event.timestampedPhotoPath) }.getOrNull()
                }, type.label, onView, onShare)
            }
        }
    }
}

@Composable private fun RecoveryCard(date: LocalDate, events: List<AttendanceEvent>, photos: LocalAttendancePhotos,
    busy: Boolean, onView: (AttendanceEvent) -> Unit, onResolve: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = .35f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(AppSymbol.RESTORE, modifier = Modifier.size(20.dp))
                Text("Recovery · $date", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text("An earlier session has no Time Out. Resolve it to continue; it earns no hours.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            events.forEach { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LocalPhoto(remember(event.originalPhotoPath) { runCatching { photos.resolve(event.originalPhotoPath) }.getOrNull() },
                        event.type.label, Modifier.size(48.dp), maxEdge = 320)
                    Column(Modifier.weight(1f)) {
                        Text(event.type.label, style = MaterialTheme.typography.labelLarge)
                        Text(event.occurredAt.manilaLabel(), style = MaterialTheme.typography.bodySmall)
                    }
                    QuietButton(onClick = { onView(event) }) { ActionLabel(AppSymbol.PHOTO, "View", compact = true) }
                }
            }
            Button(onClick = onResolve, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                ActionLabel(AppSymbol.RESTORE, "Mark incomplete / unverified", compact = true)
            }
        }
    }
}

@Composable private fun LegacyRecoveryCard(legacy: AttendanceSession, breakText: String, onBreakChange: (String) -> Unit,
    enabled: Boolean, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Legacy session", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text("Close this earlier session before starting four-event attendance.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Time In · ${legacy.timeIn.manilaLabel()}", style = MaterialTheme.typography.bodyMedium)
        TextField(value = breakText, onValueChange = onBreakChange, label = { Text("Actual break · minutes") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = appTextFieldColors(), shape = MaterialTheme.shapes.medium)
        Button(onClick = onClose, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            ActionLabel(AppSymbol.TIME_OUT, "Close legacy session", compact = true)
        }
    }
}

@Composable private fun SessionExceptions(date: LocalDate, events: List<AttendanceEvent>, resolutions: List<SessionResolution>,
    busy: Boolean, onSkip: (OjtSession) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Session exceptions", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        OjtSession.entries.forEach { session ->
            val action = resolutions.firstOrNull { it.date == date && it.session == session }
            if (action != null) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(AppSymbol.WARNING, modifier = Modifier.size(19.dp))
                        Column {
                            Text("${session.label} · ${action.kind.label}", style = MaterialTheme.typography.labelLarge)
                            Text("${action.actedAt.manilaLabel()} · no hours credited", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (events.none { it.type.code / 2 == session.code }) {
                val previousResolved = session == OjtSession.MORNING ||
                    events.any { it.type == AttendanceEventType.MORNING_TIME_OUT } ||
                    resolutions.any { it.date == date && it.session == OjtSession.MORNING }
                OutlinedButton(onClick = { onSkip(session) }, enabled = !busy && previousResolved,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    ActionLabel(AppSymbol.RESTORE, "${session.label}: skip / not worked", compact = true)
                }
            }
        }
    }
}
