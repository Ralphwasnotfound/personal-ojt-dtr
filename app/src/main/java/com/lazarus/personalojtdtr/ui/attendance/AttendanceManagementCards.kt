package com.lazarus.personalojtdtr.ui.attendance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.ui.*
import java.time.LocalDate

@Composable internal fun WorkdayManagementCard(busy: Boolean, status: String?, notes: String?, dayOff: Boolean,
    onToday: () -> Unit, onCalendar: () -> Unit) {
    ManagementCard(AppSymbol.CALENDAR, "Workday Management",
        "Manage today's workday or plan dates in your OJT calendar.") {
        status?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (dayOff) Text("No attendance required today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onToday, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("Manage Today", textAlign = TextAlign.Center)
            }
            OutlinedButton(onClick = onCalendar, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("OJT Calendar", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable internal fun SessionExceptionsCard(date: LocalDate, state: OjtState, busy: Boolean, dayOff: Boolean,
    onManage: (WorkdayScope) -> Unit) {
    ManagementCard(AppSymbol.ATTENDANCE, "Session Exceptions",
        "Skip only part of your workday without affecting the other session.") {
        val exceptions = state.calendarOverrides.filter { it.date == date && it.scope != WorkdayScope.WHOLE_DAY }
        OjtSession.entries.forEach { session ->
            val entry = exceptions.firstOrNull { it.scope.code == session.code }
            val resolution = state.resolutions.firstOrNull { it.date == date && it.session == session }
            when {
                entry != null -> {
                    Text("${session.label}: ${entry.kind.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    entry.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                resolution != null -> Text("${session.label}: ${resolution.kind.label} · saved audit action",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (dayOff) Text("Today's non-working status already covers both sessions. Use Manage Today to change it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OjtSession.entries.forEach { session ->
                val entry = exceptions.firstOrNull { it.scope.code == session.code }
                val recorded = state.events.any { it.date == date && it.type.code / 2 == session.code }
                val resolved = state.resolutions.any { it.date == date && it.session == session }
                val label = when {
                    entry != null -> "Edit ${session.label}"
                    recorded -> "${session.label} Recorded"
                    resolved -> "${session.label} Resolved"
                    dayOff -> "${session.label} Covered"
                    else -> "${session.label} Skip"
                }
                OutlinedButton(onClick = { onManage(WorkdayScope.entries.first { it.code == session.code }) },
                    enabled = !busy && (entry != null || (!recorded && !resolved && !dayOff)),
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
                    Text(label, textAlign = TextAlign.Center)
                }
            }
        }
        if (exceptions.isNotEmpty()) Text("Edit a session to change its reason or remove the exception. Saved attendance and recovery rules still apply.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun SmartRemindersEntry(busy: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(enabled = !busy, onClick = onClick),
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(.7.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(AppSymbol.BELL, modifier = Modifier.size(20.dp))
            Text("Smart Reminders", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            AppIcon(AppSymbol.RIGHT, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun ManagementCard(icon: AppSymbol, title: String, description: String,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), color = colors.surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(.7.dp, colors.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(icon, modifier = Modifier.size(20.dp))
                Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            content()
        }
    }
}
