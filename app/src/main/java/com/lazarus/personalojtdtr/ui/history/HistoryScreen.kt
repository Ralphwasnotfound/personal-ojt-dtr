package com.lazarus.personalojtdtr.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazarus.personalojtdtr.OjtApplication
import com.lazarus.personalojtdtr.data.report.DtrExportService
import com.lazarus.personalojtdtr.ui.report.*
import com.lazarus.personalojtdtr.data.photos.LocalAttendancePhotos
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.attendance.*
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import com.lazarus.personalojtdtr.ui.theme.AppStatusColors
import com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme
import com.lazarus.personalojtdtr.util.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun HistoryScreen(state: OjtState, photos: LocalAttendancePhotos, onAttendance: () -> Unit,
    onShare: (AttendanceEvent) -> Unit) {
    var exporting by rememberSaveable { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as OjtApplication
    val exportModel: DtrExportViewModel = viewModel(factory =
        DtrExportViewModel.factory(app.repository, DtrExportService(app)))
    val legacyDaily = AttendanceCalculations.dailyHours(state.legacySessions)
    val dates = (state.events.map { it.date } + state.legacySessions.map {
        it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate()
    } + legacyDaily.keys + state.resolutions.map { it.date }).distinct().sortedDescending()
    val summary = AttendanceCalculations.summary(state.sessions, state.profile.requiredHours)
    var preview by remember { mutableStateOf<AttendanceEvent?>(null) }
    var expandedDate by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("History", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                    Text("${dates.size} ${if (dates.size == 1) "day" else "days"} recorded · Manila time",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { exporting = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    ActionLabel(AppSymbol.EXPORT, "Export DTR", compact = true)
                }
            }
        }
        if (dates.isNotEmpty()) item { HistorySummary(summary.completed.hoursLabel(),
            summary.required.hoursLabel(), summary.remaining.hoursLabel()) }
        if (dates.isEmpty()) item {
            EmptyState("No attendance yet", "Recorded events and completed hours will appear here.",
                "Open attendance", onAttendance)
        }
        dates.forEach { date ->
            val events = state.events.filter { it.date == date }
            val resolutions = state.resolutions.filter { it.date == date }
            val legacy = state.legacySessions.filter {
                it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == date
            }
            val (morning, afternoon) = DailyAttendance.daily(date, events, resolutions)
            val total = morning.plus(afternoon)
            item(key = "day-$date") {
                HistoryDayCard(date, total.hoursLabel(), morning.hoursLabel(), afternoon.hoursLabel(),
                    events, resolutions, legacy, legacyDaily[date]?.hoursLabel(), photos,
                    expanded = expandedDate == date.toString(),
                    onToggle = { expandedDate = if (expandedDate == date.toString()) null else date.toString() },
                    onView = { preview = it }, onShare = onShare)
            }
        }
    }
    if (exporting) DtrExportDialog(exportModel) { exporting = false }
    preview?.let { event -> EventPhotosDialog(event, photos, { onShare(event) }, { preview = null }) }
}

@Composable private fun HistorySummary(completed: String, required: String, remaining: String) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = colors.surface,
        border = BorderStroke(0.5.dp, colors.outlineVariant), shadowElevation = 0.5.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hours completed", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Text(completed, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = RoundedCornerShape(50), color = colors.tertiaryContainer) {
                    Text("OJT", Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = colors.onTertiaryContainer)
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryMetric("Required", required, Modifier.weight(1f))
                HistoryMetric("Remaining", remaining, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun HistoryMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable private fun HistoryDayCard(date: java.time.LocalDate, total: String, morning: String, afternoon: String,
    events: List<AttendanceEvent>, resolutions: List<SessionResolution>, legacy: List<AttendanceSession>,
    legacyHours: String?, photos: LocalAttendancePhotos, expanded: Boolean, onToggle: () -> Unit,
    onView: (AttendanceEvent) -> Unit,
    onShare: (AttendanceEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val status = dayStatus(events, resolutions, legacy)
    val rotation by animateFloatAsState(if (expanded) 90f else 0f,
        animationSpec = tween(180), label = "History date expansion")
    Surface(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(180)),
        shape = RoundedCornerShape(18.dp), color = colors.surface,
        border = BorderStroke(0.5.dp, colors.outlineVariant)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button,
                    onClickLabel = if (expanded) "Collapse ${ActivityRules.dateLabel(date)} details"
                    else "Expand ${ActivityRules.dateLabel(date)} details",
                    onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(ActivityRules.dateLabel(date), style = MaterialTheme.typography.titleMedium,
                        color = colors.primary, modifier = Modifier.semantics { heading() })
                    HistoryDayStatus(status)
                }
                Text(total, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface)
                AppIcon(AppSymbol.RIGHT, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
            }
            if (expanded) {
                HorizontalDivider(color = colors.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OjtSession.entries.forEachIndexed { index, session ->
                        if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.7f))
                        val sessionEvents = events.filter { it.type.code / 2 == session.code }
                        val action = resolutions.firstOrNull { it.session == session }
                        val sessionHours = if (session == OjtSession.MORNING) morning else afternoon
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HistorySessionGlyph(session)
                                Text(session.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurfaceVariant)
                                Text(sessionHours, style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant)
                            }
                            AttendanceEventType.entries.filter { it.code / 2 == session.code }.forEach { type ->
                                val event = sessionEvents.firstOrNull { it.type == type }
                                if (event != null || action == null) {
                                    HistoryEventRow(type, event, action, photos, onView, onShare)
                                }
                            }
                            if (action != null) HistoryResolution(action)
                        }
                    }
                    legacy.forEach { session -> LegacyHistory(session) }
                    if (legacy.isNotEmpty() && legacyHours != null) {
                        Text("Legacy P1 total · $legacyHours", style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private enum class DayStatus { COMPLETE, RECOVERY, RESOLVED, PARTIAL, LEGACY }

private fun dayStatus(events: List<AttendanceEvent>, resolutions: List<SessionResolution>,
    legacy: List<AttendanceSession>): DayStatus {
    val openEventSession = OjtSession.entries.any { session ->
        events.any { it.type.code == session.startCode } &&
            events.none { it.type.code == session.startCode + 1 } &&
            resolutions.none { it.session == session }
    }
    return when {
        legacy.any { it.timeOut == null } || openEventSession ||
            resolutions.any { it.kind == ResolutionKind.INCOMPLETE } -> DayStatus.RECOVERY
        AttendanceEventType.entries.all { type -> events.any { it.type == type } } -> DayStatus.COMPLETE
        resolutions.isNotEmpty() -> DayStatus.RESOLVED
        events.isNotEmpty() -> DayStatus.PARTIAL
        legacy.isNotEmpty() -> DayStatus.LEGACY
        else -> DayStatus.PARTIAL
    }
}

@Composable private fun HistoryDayStatus(status: DayStatus) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalOjtDarkTheme.current
    val label: String
    val ink: Color
    val container: Color
    when (status) {
        DayStatus.COMPLETE -> {
            label = "Complete"
            ink = if (dark) AppStatusColors.SuccessDark else AppStatusColors.Success
            container = ink.copy(alpha = if (dark) 0.14f else 0.10f)
        }
        DayStatus.RECOVERY -> {
            label = "Recovery"
            ink = colors.onErrorContainer
            container = colors.errorContainer
        }
        DayStatus.RESOLVED -> {
            label = "Resolved"
            ink = colors.onTertiaryContainer
            container = colors.tertiaryContainer
        }
        DayStatus.PARTIAL -> {
            label = "Partial"
            ink = colors.onSurfaceVariant
            container = colors.surfaceVariant
        }
        DayStatus.LEGACY -> {
            label = "Legacy"
            ink = colors.onSurfaceVariant
            container = colors.surfaceVariant
        }
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

@Composable private fun HistoryEventRow(type: AttendanceEventType, event: AttendanceEvent?,
    resolution: SessionResolution?, photos: LocalAttendancePhotos, onView: (AttendanceEvent) -> Unit,
    onShare: (AttendanceEvent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalOjtDarkTheme.current
    val status = when {
        event != null -> "Recorded" to if (dark) AppStatusColors.SuccessDark else AppStatusColors.Success
        resolution != null -> resolution.kind.label to colors.onTertiaryContainer
        else -> "Not recorded" to colors.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryEventGlyph(isTimeIn = type.code % 2 == 0)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(type.shortLabel(), style = MaterialTheme.typography.labelLarge)
            Text(event?.occurredAt?.historyTimeLabel() ?: status.first,
                style = MaterialTheme.typography.bodySmall,
                color = if (event != null) colors.onSurfaceVariant else status.second)
        }
        if (event != null) {
            val thumbnail = remember(event.timestampedPhotoPath) {
                runCatching { photos.resolve(event.timestampedPhotoPath) }.getOrNull()
            }
            Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Open ${type.label} photo",
                    onClick = { onView(event) })
                .semantics(mergeDescendants = true) {
                    contentDescription = "Open ${type.label} photo"
                }, contentAlignment = Alignment.Center) {
                LocalPhoto(thumbnail, "${type.label} photo",
                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), maxEdge = 240)
            }
            ActionIconButton(AppSymbol.SHARE, "Share ${type.label} photo", { onShare(event) })
        }
    }
}

@Composable private fun HistoryResolution(action: SessionResolution) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = colors.tertiaryContainer) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(AppSymbol.RESTORE, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("${action.session.label} · ${action.kind.label}", style = MaterialTheme.typography.labelLarge,
                    color = colors.onTertiaryContainer)
                Text("Audit action · ${action.actedAt.historyTimeLabel()} · 0 hours",
                    style = MaterialTheme.typography.bodySmall, color = colors.onTertiaryContainer)
            }
        }
    }
}

@Composable private fun LegacyHistory(session: AttendanceSession) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Legacy P1 · preserved", style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface)
            Text("In ${session.timeIn.historyTimeLabel()} · ${session.timeOut?.let { "Out ${it.historyTimeLabel()}" } ?: "Time Out incomplete"}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text("Break ${session.breakDuration.toMinutes()}m · Net ${AttendanceCalculations.duration(session).hoursLabel()}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

private fun AttendanceEventType.shortLabel(): String = when (this) {
    AttendanceEventType.MORNING_TIME_IN, AttendanceEventType.AFTERNOON_TIME_IN -> "Time In"
    AttendanceEventType.MORNING_TIME_OUT, AttendanceEventType.AFTERNOON_TIME_OUT -> "Time Out"
}

private val historyTimeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)

private fun Instant.historyTimeLabel(): String = atZone(AttendanceCalculations.manila).format(historyTimeFormatter)

@Composable private fun HistoryEventGlyph(isTimeIn: Boolean) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(20.dp)) {
        val unit = size.minDimension / 24f
        val stroke = 2f * unit
        val clockCenter = androidx.compose.ui.geometry.Offset(9.5f * unit, 14.5f * unit)
        drawCircle(ink, radius = 7f * unit, center = clockCenter, style = Stroke(stroke))
        drawLine(ink, androidx.compose.ui.geometry.Offset(clockCenter.x, clockCenter.y),
            androidx.compose.ui.geometry.Offset(clockCenter.x, 10f * unit), stroke)
        drawLine(ink, androidx.compose.ui.geometry.Offset(clockCenter.x, clockCenter.y),
            androidx.compose.ui.geometry.Offset(12.5f * unit, 16f * unit), stroke)
        if (isTimeIn) {
            drawLine(ink, androidx.compose.ui.geometry.Offset(17.5f * unit, 2.5f * unit),
                androidx.compose.ui.geometry.Offset(17.5f * unit, 8.5f * unit), stroke)
            drawLine(ink, androidx.compose.ui.geometry.Offset(14.5f * unit, 5.5f * unit),
                androidx.compose.ui.geometry.Offset(20.5f * unit, 5.5f * unit), stroke)
        } else {
            drawLine(ink, androidx.compose.ui.geometry.Offset(14.5f * unit, 5.5f * unit),
                androidx.compose.ui.geometry.Offset(16.7f * unit, 7.5f * unit), stroke)
            drawLine(ink, androidx.compose.ui.geometry.Offset(16.7f * unit, 7.5f * unit),
                androidx.compose.ui.geometry.Offset(20.5f * unit, 3.7f * unit), stroke)
        }
    }
}

@Composable private fun HistorySessionGlyph(session: OjtSession) {
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val sunRadius = 3.dp.toPx()
        if (session == OjtSession.MORNING) {
            drawCircle(accent, sunRadius, androidx.compose.ui.geometry.Offset(centerX, centerY))
            val inner = 6.dp.toPx()
            val outer = 8.dp.toPx()
            for (angle in 0 until 360 step 45) {
                val radians = Math.toRadians(angle.toDouble())
                drawLine(accent,
                    androidx.compose.ui.geometry.Offset(centerX + kotlin.math.cos(radians).toFloat() * inner,
                        centerY + kotlin.math.sin(radians).toFloat() * inner),
                    androidx.compose.ui.geometry.Offset(centerX + kotlin.math.cos(radians).toFloat() * outer,
                        centerY + kotlin.math.sin(radians).toFloat() * outer), stroke)
            }
        } else {
            val horizon = size.height * 0.62f
            val sun = Path().apply {
                moveTo(size.width * 0.22f, horizon)
                cubicTo(size.width * 0.22f, size.height * 0.14f, size.width * 0.78f,
                    size.height * 0.14f, size.width * 0.78f, horizon)
            }
            drawPath(sun, accent, style = Stroke(stroke))
            drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * 0.12f, horizon),
                androidx.compose.ui.geometry.Offset(size.width * 0.88f, horizon), stroke)
            drawLine(accent, androidx.compose.ui.geometry.Offset(centerX, size.height * 0.05f),
                androidx.compose.ui.geometry.Offset(centerX, size.height * 0.17f), stroke)
            drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.12f),
                androidx.compose.ui.geometry.Offset(size.width * 0.31f, size.height * 0.22f), stroke)
            drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.12f),
                androidx.compose.ui.geometry.Offset(size.width * 0.69f, size.height * 0.22f), stroke)
        }
    }
}
