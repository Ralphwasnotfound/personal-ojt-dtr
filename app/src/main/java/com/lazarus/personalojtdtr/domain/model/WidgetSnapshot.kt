package com.lazarus.personalojtdtr.domain.model

import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.domain.usecase.AttendanceRecovery
import com.lazarus.personalojtdtr.domain.usecase.DailyAttendance
import com.lazarus.personalojtdtr.util.hoursLabel
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Read-only inputs. No photos, names, or journal descriptions are published to the launcher.
data class WidgetInputs(
    val requiredHours: Long = 0,
    val legacy: List<AttendanceSession> = emptyList(),
    val events: List<AttendanceEvent> = emptyList(),
    val resolutions: List<SessionResolution> = emptyList(),
)

enum class WidgetAttendanceStatus(val label: String) {
    NOT_STARTED("Not Started"), MORNING_IN("Morning In"), MORNING_COMPLETED("Morning Completed"),
    MORNING_SKIPPED("Morning Skipped"), AFTERNOON_IN("Afternoon In"), COMPLETED("Completed"),
    FINISHED_WITH_SKIPS("Finished (with skips)"), RECOVERY_NEEDED("Recovery needed"),
    LEGACY_IN("Timed In (legacy)"), LEGACY_OPEN("Legacy session open"), LEGACY_COMPLETED("Completed (legacy)"),
}

enum class WidgetDestination(val route: String) {
    ATTENDANCE("attendance"), ACTIVITIES("activities");
    // Different intent actions also give each shortcut a distinct PendingIntent identity.
    val action: String get() = "com.lazarus.personalojtdtr.widget.OPEN_${name}"
    companion object {
        fun fromAction(action: String?): WidgetDestination? = entries.firstOrNull { it.action == action }
    }
}

data class WidgetSnapshot(val summary: HourSummary, val date: LocalDate, val status: WidgetAttendanceStatus) {
    val completedLabel: String get() = summary.completed.hoursLabel()
    val remainingLabel: String get() = summary.remaining.hoursLabel()
    val requiredLabel: String get() = summary.required.hoursLabel()
    val percentageLabel: String get() = "${(summary.progress * 100).toInt()}%"
    val dateLabel: String get() = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    val goalLabel: String get() = if (summary.required.isZero) "Set required hours in Profile" else "Goal: $requiredLabel"

    companion object {
        fun from(inputs: WidgetInputs, now: Instant): WidgetSnapshot {
            val day = now.atZone(AttendanceCalculations.manila).toLocalDate()
            val sessions = inputs.legacy + DailyAttendance.sessions(inputs.events, inputs.resolutions)
            // This is the same aggregation and calculator used by OjtViewModel/Dashboard.
            val summary = AttendanceCalculations.summary(sessions, inputs.requiredHours)
            val status = when {
                AttendanceRecovery.unfinishedBefore(day, inputs.events, inputs.resolutions).isNotEmpty() -> WidgetAttendanceStatus.RECOVERY_NEEDED
                inputs.legacy.any { it.timeOut == null && it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() < day } -> WidgetAttendanceStatus.LEGACY_OPEN
                inputs.legacy.any { it.timeOut == null } -> WidgetAttendanceStatus.LEGACY_IN
                inputs.events.none { it.date == day } && inputs.resolutions.none { it.date == day } &&
                    inputs.legacy.any { it.timeOut?.atZone(AttendanceCalculations.manila)?.toLocalDate() == day } -> WidgetAttendanceStatus.LEGACY_COMPLETED
                else -> when (DailyAttendance.next(day, inputs.events, inputs.resolutions)) {
                    AttendanceEventType.MORNING_TIME_IN -> WidgetAttendanceStatus.NOT_STARTED
                    AttendanceEventType.MORNING_TIME_OUT -> WidgetAttendanceStatus.MORNING_IN
                    AttendanceEventType.AFTERNOON_TIME_IN -> if (inputs.resolutions.any { it.date == day && it.session == OjtSession.MORNING })
                        WidgetAttendanceStatus.MORNING_SKIPPED else WidgetAttendanceStatus.MORNING_COMPLETED
                    AttendanceEventType.AFTERNOON_TIME_OUT -> WidgetAttendanceStatus.AFTERNOON_IN
                    null -> if (inputs.resolutions.any { it.date == day }) WidgetAttendanceStatus.FINISHED_WITH_SKIPS else WidgetAttendanceStatus.COMPLETED
                }
            }
            return WidgetSnapshot(summary, day, status)
        }

        fun nextMidnight(now: Instant): Instant = now.atZone(AttendanceCalculations.manila).toLocalDate()
            .plusDays(1).atStartOfDay(AttendanceCalculations.manila).toInstant()
    }
}
