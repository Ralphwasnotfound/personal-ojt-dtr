package com.lazarus.personalojtdtr.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import com.lazarus.personalojtdtr.R
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import com.lazarus.personalojtdtr.domain.model.WidgetInputs
import com.lazarus.personalojtdtr.domain.model.WidgetSnapshot
import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.domain.usecase.DailyAttendance
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.ui.theme.AppSpacing
import com.lazarus.personalojtdtr.ui.theme.AppRefinement
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.ui.activity.ActivityListRow
import com.lazarus.personalojtdtr.util.*

@Composable fun DashboardScreen(state: OjtState, photos: ActivityPhotoStore, onProfile: () -> Unit,
    onAttendance: () -> Unit, onActivities: () -> Unit, onActivity: (String) -> Unit) {
    val summary = AttendanceCalculations.summary(state.sessions, state.profile.requiredHours)
    val active = state.sessions.firstOrNull { it.timeOut == null }
    val attendanceStatus = WidgetSnapshot.from(WidgetInputs(state.profile.requiredHours, state.legacySessions, state.events, state.resolutions), Instant.now()).status.label
    val date = today()
    val (morning, afternoon) = DailyAttendance.daily(date, state.events, state.resolutions)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.Page),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Section)) {
        DashboardHeader(state.profile.displayName, onProfile)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(AppRefinement.PanelRadius),
            color = AppRefinement.HeroContainer, contentColor = AppRefinement.HeroContent, shadowElevation = 2.dp) {
            Column(Modifier.padding(AppSpacing.Section), verticalArrangement = Arrangement.spacedBy(AppSpacing.Page)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                            AppIcon(AppSymbol.PROGRESS, modifier = Modifier.size(20.dp))
                            Text("OJT PROGRESS", style = MaterialTheme.typography.labelLarge, color = AppRefinement.HeroSecondary)
                        }
                        Box(Modifier.size(24.dp, 3.dp).background(AppRefinement.GoldHighlight, RoundedCornerShape(2.dp)))
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = AppRefinement.HeroContent.copy(alpha = 0.12f)) {
                        Text("${(summary.progress * 100).toInt()}%", Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium, color = AppRefinement.HeroContent)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Micro)) {
                    Text(summary.completed.hoursLabel(), style = MaterialTheme.typography.headlineMedium, color = AppRefinement.HeroContent)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                        AppIcon(AppSymbol.COMPLETED, modifier = Modifier.size(18.dp))
                        Text("Completed OJT hours", style = MaterialTheme.typography.bodyMedium, color = AppRefinement.HeroSecondary)
                    }
                }
                RoundedProgress(summary.progress, color = AppRefinement.HeroContent, track = AppRefinement.HeroTrack)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Section), verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                    Column { Text("Required", style = MaterialTheme.typography.bodySmall, color = AppRefinement.HeroSecondary); Text(summary.required.hoursLabel(), style = MaterialTheme.typography.titleMedium, color = AppRefinement.HeroContent) }
                    Column { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppIcon(AppSymbol.REMAINING, modifier = Modifier.size(18.dp))
                        Text("Remaining", style = MaterialTheme.typography.bodySmall, color = AppRefinement.HeroSecondary)
                    }; Text(summary.remaining.hoursLabel(), style = MaterialTheme.typography.titleMedium, color = AppRefinement.HeroContent) }
                }
                if (state.profile.requiredHours == 0L) TextButton(onClick = onProfile,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppRefinement.HeroContent)) { ActionLabel(AppSymbol.EDIT, "Set required hours", compact = true) }
                Text("Closed sessions only · actual recorded hours", style = MaterialTheme.typography.bodySmall, color = AppRefinement.HeroSecondary)
            }
        }
        RefinedSurface {
            SectionHeading("Today's attendance", AppSymbol.ATTENDANCE) { QuietButton(onClick = onAttendance) { ActionLabel(AppSymbol.ATTENDANCE, "Open", compact = true) } }
            StatusPill(attendanceStatus, AppSymbol.ATTENDANCE, emphasized = true)
            active?.let { Text("Since ${it.timeIn.manilaLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val events = state.events.filter { it.date == date }
            if (events.isEmpty()) Text("No events recorded today. Open Attendance to continue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            events.forEach { event ->
                Row(Modifier.fillMaxWidth().padding(vertical = AppSpacing.Micro), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
                    AppIcon(AppSymbol.CHECK, modifier = Modifier.size(20.dp))
                    Column { Text(event.type.label, style = MaterialTheme.typography.bodyMedium)
                        Text(event.occurredAt.manilaLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            state.resolutions.filter { it.date == date }.forEach { Text("${it.session.label}: ${it.kind.label}", style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            Text("Today: ${morning.plus(afternoon).hoursLabel()} · Morning ${morning.hoursLabel()} · Afternoon ${afternoon.hoursLabel()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Asia/Manila · device clock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            SectionHeading("Recent activities", AppSymbol.ACTIVITIES) { QuietButton(onClick = onActivities) { ActionLabel(AppSymbol.ACTIVITIES, "View all", compact = true) } }
            if (state.activities.isEmpty()) EmptyState("Your journal starts here", "Capture what you worked on and learned.", "Add an activity", onActivities, actionSymbol = AppSymbol.ADD)
            state.activities.take(3).forEach { activity -> ActivityListRow(activity, photos, { onActivity(activity.id) }, compact = true) }
        }
    }
}

/** Logo and name occupy separate rows so neither competes for text width at large font scales. */
@Composable private fun DashboardHeader(displayName: String, onProfile: () -> Unit) {
    val name = displayName.trim()
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Related)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.Page), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.Micro)) {
                Box(Modifier.size(28.dp, 3.dp).background(AppRefinement.GoldHighlight, RoundedCornerShape(2.dp)))
                Text(stringResource(R.string.dashboard_brand_label), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuietButton(onClick = onProfile) {
                    AppIcon(AppSymbol.PROFILE, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.Related))
                    Text(stringResource(if (name.isEmpty()) R.string.dashboard_profile_setup else R.string.dashboard_profile_shortcut))
                }
            }
            Image(painterResource(R.drawable.bsit_logo), stringResource(R.string.bsit_logo_description),
                Modifier.size(72.dp), contentScale = ContentScale.Fit)
        }
        Text(name.ifEmpty { stringResource(R.string.dashboard_welcome) }, Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.displaySmall.copy(lineBreak = LineBreak.Heading))
        Text(stringResource(R.string.dashboard_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
