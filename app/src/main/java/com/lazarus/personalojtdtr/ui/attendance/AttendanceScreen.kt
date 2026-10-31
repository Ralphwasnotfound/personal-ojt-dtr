package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.ZoneOffset
import com.lazarus.personalojtdtr.data.preferences.OjtSchedule

private val attendanceClock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val AttendanceSuccess = Color(0xFF2E7D32)
private val AttendanceSuccessDark = Color(0xFF81C784)
private val AttendancePending = Color(0xFF9A6B12)
private val AttendancePendingDark = Color(0xFFE7C36A)

@Composable fun AttendanceScreen(state: OjtState, busy: Boolean, photos: LocalAttendancePhotos,
    onCapture: (LocalDate, AttendanceEventType) -> Unit, onShare: (AttendanceEvent) -> Unit,
    onCloseLegacy: (Long) -> Unit, onResolve: (LocalDate, OjtSession, ResolutionKind) -> Unit,
    schedule: OjtSchedule, onSetCalendarOverride: (LocalDate, CalendarOverrideKind, String?, Boolean, WorkdayScope) -> Unit,
    onClearCalendarOverride: (LocalDate, WorkdayScope) -> Unit, quickDayOffRequest: Long = 0, onQuickDayOffConsumed: () -> Unit = {}, initialScope: WorkdayScope = WorkdayScope.WHOLE_DAY, onReminderSettings: () -> Unit = {}) {
    var date by remember { mutableStateOf(today()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumeRevision by remember { mutableStateOf(0L) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { date = today(); resumeRevision++ }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeRevision) { while (true) {
        val now = java.time.Instant.now()
        date = now.atZone(AttendanceCalculations.manila).toLocalDate()
        kotlinx.coroutines.delay(java.time.Duration.between(now, WidgetSnapshot.nextMidnight(now)).toMillis().coerceAtLeast(1))
    } }
    var manageScope by remember { mutableStateOf(initialScope) }
    var quickDayOff by remember { mutableStateOf(false) }
    var calendarRequest by remember { mutableStateOf(0L) }
    LaunchedEffect(quickDayOffRequest) { if (quickDayOffRequest > 0) { manageScope = initialScope; quickDayOff = true; onQuickDayOffConsumed() } }
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
    val hasDateRecords = todayEvents.isNotEmpty() || state.resolutions.any { it.date == date } ||
        state.legacySessions.any { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == date || it.timeOut?.atZone(AttendanceCalculations.manila)?.toLocalDate() == date }
    val dayStatus = com.lazarus.personalojtdtr.domain.usecase.calendarDayStatus(date, schedule.weekdays, state.calendarOverrides, hasDateRecords)
    val dayOff = !hasDateRecords && isExplicitDayOff(date, schedule.weekdays, state.calendarOverrides)
    val exceptionLabel = state.calendarOverrides.wholeDay().firstOrNull { it.date == date }?.kind?.label ?: "Day Off"
    val unresolvedRecovery = unfinished.isNotEmpty() || earlierOpen || legacy != null
    val eligibleAction = WidgetSnapshot.safeCameraAction(date, WidgetInputs(
        legacy = state.legacySessions, events = state.events, resolutions = state.resolutions,
        workingWeekdays = schedule.weekdays, calendarOverrides = state.calendarOverrides))
    val page = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(page.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Attendance", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$date · Manila time", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = page.onSurfaceVariant)
                AttendanceStatus(when {
                    unresolvedRecovery -> statusLabel(next, todayEvents.size, earlierOpen, legacy != null, unfinished.isNotEmpty())
                    dayOff -> exceptionLabel
                    dayStatus == OjtCalendarDayStatus.SPECIAL_WORKING_DAY -> "Special Workday"
                    else -> statusLabel(next, todayEvents.size, earlierOpen, legacy != null, unfinished.isNotEmpty())
                }, recovery = unresolvedRecovery)
            }
        }

        AttendanceSummary(morning.plus(afternoon).hoursLabel(), morning.hoursLabel(), afternoon.hoursLabel())

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (unresolvedRecovery) Text("Recovery Required · resolve the session below to continue", color = page.error,
                style = MaterialTheme.typography.bodyMedium)
            Text("Today’s events", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            AttendanceEventType.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top) {
                    row.forEach { type ->
                        val event = todayEvents.firstOrNull { it.type == type }
                        val resolution = state.resolutions.firstOrNull { it.date == date && it.session.code == type.code / 2 }
                        val available = event == null && resolution == null && eligibleAction == type && !unresolvedRecovery && !dayOff
                        AttendanceEventCard(type, event, resolution, available, Modifier.weight(1f), photos,
                            onView = { preview = event }, onShare = { event?.let(onShare) },
                            busy = busy, lockedLabel = if (dayOff) exceptionLabel else if (unresolvedRecovery) "Recovery Required" else "Locked",
                            onCapture = { onCapture(date, type) })
                    }
                }
            }
            if (next == null && todayEvents.size == 4) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(AppSymbol.CHECK, modifier = Modifier.size(20.dp))
                        Text("All four events recorded", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        WorkdayManagementCard(busy,
            status = state.calendarOverrides.wholeDay().firstOrNull { it.date == date }?.kind?.label ?: if (dayOff) exceptionLabel else null,
            notes = state.calendarOverrides.wholeDay().firstOrNull { it.date == date }?.reason, dayOff = dayOff,
            onToday = { manageScope = WorkdayScope.WHOLE_DAY; quickDayOff = true },
            onCalendar = { calendarRequest++ })
        SessionExceptionsCard(date, state, busy, dayOff) { scope -> manageScope = scope; quickDayOff = true }
        SmartRemindersEntry(busy, onReminderSettings)

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


    }

    OjtCalendarControls(date, state, schedule, busy, onSetCalendarOverride, onClearCalendarOverride, calendarRequest)
    if (quickDayOff) WorkdayEditor(date, state, schedule, busy, manageScope,
        onSave = { kind, notes, consent, scope -> onSetCalendarOverride(date, kind, notes, consent, scope); quickDayOff = false },
        onRemove = { scope -> onClearCalendarOverride(date, scope); quickDayOff = false },
        onDismiss = { quickDayOff = false }, onCalendar = { quickDayOff = false; calendarRequest++ })
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

@Composable private fun WorkdayEditor(date: LocalDate, state: OjtState, schedule: OjtSchedule, busy: Boolean, initialScope: WorkdayScope = WorkdayScope.WHOLE_DAY,
    onSave: (CalendarOverrideKind, String?, Boolean, WorkdayScope) -> Unit, onRemove: (WorkdayScope) -> Unit,
    onDismiss: () -> Unit, onCalendar: (() -> Unit)? = null) {
    var scope by remember(date, initialScope) { mutableStateOf(initialScope) }
    val existing = state.calendarOverrides.firstOrNull { it.date == date && it.scope == scope }
    var selection by remember(date, existing) { mutableStateOf(existing?.kind) }
    var notes by remember(date, existing) { mutableStateOf(existing?.reason.orEmpty()) }
    var consent by remember(date) { mutableStateOf(false) }
    val conflict = state.events.any { it.date == date } || state.resolutions.any { it.date == date } ||
        state.legacySessions.any { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == date || it.timeOut?.atZone(AttendanceCalculations.manila)?.toLocalDate() == date }
    val regularOff = existing == null && schedule.weekdays.isNotEmpty() && date.dayOfWeek !in schedule.weekdays
    val valid = selection != null && (selection != CalendarOverrideKind.OTHER || notes.isNotBlank()) &&
        !(regularOff && scope == WorkdayScope.WHOLE_DAY && selection == CalendarOverrideKind.DAY_OFF) && (scope == WorkdayScope.WHOLE_DAY || selection != CalendarOverrideKind.WORKING_DAY)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Manage Workday") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$date · Asia/Manila")
            WorkdayScope.entries.forEach { item ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { scope = item }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = scope == item, onClick = { scope = item }, enabled = !busy)
                    Text(item.label)
                }
            }
            Text("A skipped half day uses half the forecast daily capacity. Saved attendance is preserved.", style = MaterialTheme.typography.bodySmall)
            Text(existing?.kind?.label ?: if (conflict) "Attendance Recorded" else if (regularOff) "Regular Day Off" else if (schedule.weekdays.isEmpty()) "Schedule not configured" else if (date < today()) "No Attendance Recorded" else "Scheduled Workday")
            if (regularOff) Text("A regular rest day already applies. Choose another reason or a special working day to override it.", style = MaterialTheme.typography.bodySmall)
            (CalendarOverrideKind.entries.filter { it != CalendarOverrideKind.WORKING_DAY } + CalendarOverrideKind.WORKING_DAY).forEach { kind ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selection = kind }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selection == kind, onClick = { selection = kind }, enabled = !busy)
                    Text(kind.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(notes, { notes = it.take(200) }, label = { Text(if (selection == CalendarOverrideKind.OTHER) "Specify reason · required" else "Notes · optional") }, supportingText = { Text("${notes.length}/200") })
            if (conflict) {
                Text("Attendance exists. Confirm the calendar exception while preserving all attendance, credited hours, and photo evidence.", color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(consent, { consent = it }, enabled = !busy)
                    Text("I confirm this calendar change", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (existing != null) TextButton(onClick = { onRemove(scope) }, enabled = !busy && (!conflict || consent)) { Text("Remove exception") }
            onCalendar?.let { TextButton(onClick = it, enabled = !busy) { Text("Open Calendar") } }
        } },
        confirmButton = { TextButton(enabled = !busy && valid && (!conflict || consent), onClick = {
            selection?.let { onSave(it, notes.trim().takeIf(String::isNotEmpty), conflict, scope) }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable internal fun OjtCalendarControls(date: LocalDate, state: OjtState, schedule: OjtSchedule, busy: Boolean,
    onSet: (LocalDate, CalendarOverrideKind, String?, Boolean, WorkdayScope) -> Unit, onClear: (LocalDate, WorkdayScope) -> Unit, openRequest: Long = 0) {
    var showPicker by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    LaunchedEffect(openRequest) { if (openRequest > 0) showCalendar = true }
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    var editScope by remember { mutableStateOf(WorkdayScope.WHOLE_DAY) }
    val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1800..2400)
    if (showCalendar) AlertDialog(onDismissRequest = { showCalendar = false }, title = { Text("OJT Calendar") },
        confirmButton = { TextButton(onClick = { showCalendar = false }) { Text("Done") } },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (schedule.weekdays.isEmpty()) "Set working weekdays in Profile > Settings > Work Schedule." else
            "Regular days off: " + DayOfWeek.entries.filter { it !in schedule.weekdays }.joinToString { it.name.lowercase().replaceFirstChar(Char::uppercase) }.ifEmpty { "None" },
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { showCalendar = false; showPicker = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Select Date") }
        state.calendarOverrides.sortedByDescending { it.date }.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${entry.date} · ${entry.scope.label} · ${entry.kind.label}", style = MaterialTheme.typography.bodyMedium)
                    entry.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = { showCalendar = false; editScope = entry.scope; editDate = entry.date }, enabled = !busy) { Text("Edit") }
            }
        }
    }
    })
    if (showPicker) DatePickerDialog(onDismissRequest = { showPicker = false; showCalendar = true },
        confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { editDate = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
            showPicker = false; editScope = WorkdayScope.WHOLE_DAY
        }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { showPicker = false; showCalendar = true }) { Text("Cancel") } }) { DatePicker(picker) }
    editDate?.let { day -> WorkdayEditor(day, state, schedule, busy, editScope,
        onSave = { kind, notes, consent, scope -> onSet(day, kind, notes, consent, scope); editDate = null; showCalendar = true },
        onRemove = { scope -> onClear(day, scope); editDate = null; showCalendar = true }, onDismiss = { editDate = null; showCalendar = true }) }
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
    available: Boolean, modifier: Modifier, photos: LocalAttendancePhotos, onView: () -> Unit, onShare: () -> Unit,
    busy: Boolean, lockedLabel: String, onCapture: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme.current
    val recorded = event != null
    val stateColor = when {
        recorded -> if (dark) AttendanceSuccessDark else AttendanceSuccess
        resolution != null -> colors.onSurfaceVariant
        available -> colors.primary
        else -> if (dark) AttendancePendingDark else AttendancePending
    }
    Surface(modifier.clickable(enabled = available && !busy, onClick = onCapture), shape = RoundedCornerShape(16.dp), color = colors.surface,
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
                event != null -> "Completed"
                resolution != null -> resolution.kind.label
                available -> "Next event"
                else -> lockedLabel
            }, style = MaterialTheme.typography.labelMedium, color = stateColor)
            if (available) Button(onClick = onCapture, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                ActionLabel(AppSymbol.CAMERA, "Capture", compact = true)
            }
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
