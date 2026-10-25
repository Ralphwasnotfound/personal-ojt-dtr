package com.lazarus.personalojtdtr.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.domain.usecase.DailyAttendance
import com.lazarus.personalojtdtr.ui.*
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.ui.activity.ActivityCard
import com.lazarus.personalojtdtr.util.*

@Composable fun DashboardScreen(state: OjtState, photos: ActivityPhotoStore, onProfile: () -> Unit, onActivities: () -> Unit, onActivity: (String) -> Unit) {
    val summary = AttendanceCalculations.summary(state.sessions, state.profile.requiredHours)
    val active = state.sessions.firstOrNull { it.timeOut == null }
    Page("My OJT Companion", state.profile.displayName.takeIf { it.isNotBlank() }?.let { "Welcome, $it" } ?: "Your personal OJT journal") {
        InfoCard("Your progress") {
            Text(summary.completed.hoursLabel(), style = MaterialTheme.typography.displaySmall)
            Text("Completed • closed sessions only")
            LinearProgressIndicator(progress = { summary.progress }, modifier = Modifier.fillMaxWidth())
            Text("${(summary.progress * 100).toInt()}% of your goal")
            Text("Remaining: ${summary.remaining.hoursLabel()}")
            Text("Required: ${summary.required.hoursLabel()}")
            if (state.profile.requiredHours == 0L) TextButton(onClick = onProfile) { Text("Set your required OJT hours") }
        }
        val (morning, afternoon) = DailyAttendance.daily(today(), state.events, state.resolutions)
        InfoCard("Today's completed hours") {
            Text("Morning: ${morning.hoursLabel()} • Afternoon: ${afternoon.hoursLabel()}")
            Text("Daily total: ${morning.plus(afternoon).hoursLabel()}")
        }
        InfoCard("Attendance status") {
            Text(if (active == null) "No active session" else "Currently timed in")
            active?.let { Text("Since ${it.timeIn.manilaLabel()}") }
            Text("All dates and times use Asia/Manila.")
        }
        InfoCard("Recent activities") {
            if (state.activities.isEmpty()) Text("Your recent journal entries will appear here. Record your first activity when you are ready.")
            state.activities.take(3).forEach { activity -> ActivityCard(activity, photos, { onActivity(activity.id) }, compact = true) }
            TextButton(onClick = onActivities) { Text("View all activities") }
        }
    }

}
