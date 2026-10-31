package com.lazarus.personalojtdtr.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lazarus.personalojtdtr.data.photos.AttendanceImageProcessor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazarus.personalojtdtr.R
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import com.lazarus.personalojtdtr.ui.OjtState
import com.lazarus.personalojtdtr.util.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val DashboardRecorded = Color(0xFF2E7D32)
private val DashboardRecordedDark = Color(0xFF81C784)
private val DashboardPending = Color(0xFF9A6B12)
private val DashboardPendingDark = Color(0xFFE7C36A)

/** The reference's information order, with theme-aware surfaces and uncluttered data hierarchy. */
@Composable fun DashboardScreen(state: OjtState, photos: ActivityPhotoStore, onProfile: () -> Unit,
    onAttendance: () -> Unit, onActivities: () -> Unit, onActivity: (String) -> Unit, avatar: java.io.File? = null) {
    val summary = AttendanceCalculations.summary(state.sessions, state.profile.requiredHours)
    val date = today()
    val (morning, afternoon) = DailyAttendance.daily(date, state.events, state.resolutions)
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        DashboardHeader(state.profile.displayName, onProfile, avatar)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = colors.surface,
            border = BorderStroke(0.5.dp, colors.outlineVariant), shadowElevation = 1.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).background(colors.tertiary, CircleShape))
                        Text("OJT PROGRESS", Modifier.semantics { heading() },
                            style = MaterialTheme.typography.labelLarge, color = dashboardAccent())
                    }
                    Surface(shape = CircleShape, color = colors.tertiaryContainer) {
                        Text("${(summary.progress * 100).toInt()}%", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = colors.onTertiaryContainer, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(summary.completed.hoursLabel(), style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface, modifier = Modifier.semantics { contentDescription = "Completed ${summary.completed.hoursLabel()}" })
                    Text("Completed OJT hours", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.outlineVariant)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(summary.progress, 0f..1f) }) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(summary.progress).background(dashboardAccent()))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Metric("Required", summary.required.hoursLabel(), Modifier.weight(1f), onProfile)
                    Metric("Remaining", summary.remaining.hoursLabel(), Modifier.weight(1f), onProfile)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelHeading("Today’s Attendance", onAttendance, "View attendance")
            AttendanceEventType.entries.chunked(2).forEach { types ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    types.forEach { eventType ->
                        AttendanceTile(eventType, state.events.firstOrNull { it.date == date && it.type == eventType },
                            photos, Modifier.weight(1f), onAttendance)
                    }
                }
            }
            Text("Today ${morning.plus(afternoon).hoursLabel()} · Morning ${morning.hoursLabel()} · Afternoon ${afternoon.hoursLabel()}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text("Asia/Manila · Device clock", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            state.resolutions.filter { it.date == date }.forEach {
                Text("${it.session.label}: ${it.kind.label}", style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelHeading("Recent Activities", onActivities, "View all activities")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = colors.surface,
                border = BorderStroke(0.5.dp, colors.outlineVariant)) {
                Column {
                    if (state.activities.isEmpty()) {
                        Column(Modifier.fillMaxWidth().clickable(onClick = onActivities).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Your journal starts here", style = MaterialTheme.typography.titleSmall)
                            Text("Add an activity to see your saved work and photos here.",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    state.activities.take(2).forEachIndexed { index, activity ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant, thickness = 0.5.dp)
                        RecentActivity(activity, photos) { onActivity(activity.id) }
                    }
                }
            }
        }
    }
}

@Composable private fun DashboardHeader(name: String, onProfile: () -> Unit, avatar: java.io.File?) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.dashboard_brand_label), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                    color = colors.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Profile", onClick = onProfile)
                    .semantics { contentDescription = "Profile" }.heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.lazarus.personalojtdtr.ui.profile.ProfileAvatar(avatar, Modifier.size(44.dp)) {
                        DashboardIcon(DashboardGlyph.PROFILE, Modifier.size(22.dp), dashboardAccent())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Welcome back", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text(name.trim().ifEmpty { "Welcome!" }, style = MaterialTheme.typography.headlineSmall,
                            color = colors.onBackground, modifier = Modifier.semantics { heading() })
                    }
                }
            }
            Image(painterResource(R.drawable.bsit_logo), stringResource(R.string.bsit_logo_description),
                Modifier.size(68.dp), contentScale = ContentScale.Fit)
        }
        Text("Your OJT progress at a glance", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Profile", onClick = onClick)
        .heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable private fun PanelHeading(title: String, onClick: () -> Unit, action: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
        TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = action },
            colors = ButtonDefaults.textButtonColors(contentColor = dashboardAccent()), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("View", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable private fun AttendanceTile(eventType: AttendanceEventType, event: AttendanceEvent?, photos: ActivityPhotoStore,
    modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Open ${eventType.label}", onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = eventType.label },
        color = colors.surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(0.5.dp, colors.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((if (eventType.code < 2) "Morning" else "Afternoon") + if (eventType.code % 2 == 0) " in" else " out",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                DashboardIcon(if (event == null) DashboardGlyph.CAMERA else DashboardGlyph.COMPLETED,
                    Modifier.size(16.dp), if (event == null) colors.onSurfaceVariant else dashboardAccent())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(event?.occurredAt?.atZone(AttendanceCalculations.manila)?.format(clockFormat) ?: "—",
                        style = MaterialTheme.typography.titleMedium, color = if (event == null) colors.onSurface else dashboardAccent())
                    val statusColor = if (event == null) {
                        if (com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme.current) DashboardPendingDark else DashboardPending
                    } else if (com.lazarus.personalojtdtr.ui.theme.LocalOjtDarkTheme.current) DashboardRecordedDark else DashboardRecorded
                    Text(if (event == null) "Not recorded" else "Recorded", style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
                if (event != null) PhotoPreview(event.timestampedPhotoPath, photos, "Attendance photo", Modifier.size(32.dp))
            }
        }
    }
}

@Composable private fun RecentActivity(activity: DailyActivity, photos: ActivityPhotoStore, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (activity.photoPaths.isNotEmpty()) PhotoPreview(activity.photoPaths.first(), photos, "Activity photo", Modifier.size(44.dp))
        else DashboardIcon(DashboardGlyph.ACTIVITIES, Modifier.size(24.dp), dashboardAccent())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(activity.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(activity.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ActivityRules.dateLabel(activity.date) + activity.time?.let { " · ${ActivityRules.timeLabel(it)}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DashboardIcon(DashboardGlyph.NEXT, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun PhotoPreview(path: String, photos: ActivityPhotoStore, description: String, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { AttendanceImageProcessor.decode(photos.thumbnailFor(path), 240).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .semantics { contentDescription = if (image == null) "$description unavailable" else description },
        contentAlignment = Alignment.Center) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: DashboardIcon(DashboardGlyph.CAMERA, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
