package com.lazarus.personalojtdtr.domain.model

import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.domain.usecase.AttendanceRecovery
import com.lazarus.personalojtdtr.domain.usecase.DailyAttendance
import com.lazarus.personalojtdtr.domain.usecase.isExplicitDayOff
import com.lazarus.personalojtdtr.domain.usecase.CompletionForecast
import com.lazarus.personalojtdtr.domain.usecase.CompletionForecasts
import com.lazarus.personalojtdtr.domain.usecase.ForecastMethod
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
    val expectedHoursPerDay: Int? = null,
    val workingWeekdays: Set<java.time.DayOfWeek> = emptySet(),
    val calendarOverrides: List<CalendarDayOverride> = emptyList(),
)

enum class WidgetAttendanceStatus(val label: String) {
    NOT_STARTED("Not Timed In"), MORNING_IN("Timed In"), MORNING_COMPLETED("Morning Timed Out"),
    MORNING_SKIPPED("Morning Skipped"), AFTERNOON_IN("Timed In"), COMPLETED("Day Completed"),
    FINISHED_WITH_SKIPS("Day Completed · skips"), DAY_OFF("Day Off"), RECOVERY_NEEDED("Recovery needed"),
    ABSENT("Absent"), SICK_LEAVE("Sick Leave"), COMPANY_CLOSED("Company Closed"), OTHER_EXCEPTION("Other exception"),
    LEGACY_IN("Timed In (legacy)"), LEGACY_OPEN("Recovery needed"), LEGACY_COMPLETED("Day Completed (legacy)"),
}

enum class WidgetDestination(val route: String) {
    ATTENDANCE("attendance"), ACTIVITIES("activities"), DAY_OFF("attendance");
    // Different intent actions also give each shortcut a distinct PendingIntent identity.
    val action: String get() = "com.lazarus.personalojtdtr.widget.OPEN_${name}"
    companion object {
        fun fromAction(action: String?): WidgetDestination? = entries.firstOrNull { it.action == action }
    }
}

data class WidgetSnapshot(val summary: HourSummary, val date: LocalDate, val status: WidgetAttendanceStatus,
    val completedDays: Int = 0, val estimatedCompletionDate: LocalDate? = null,
    val nextActionLabel: String = "", val forecast: CompletionForecast = CompletionForecast(ForecastMethod.INSUFFICIENT_HISTORY)) {
    val isOjtCompleted: Boolean get() = !summary.required.isZero && summary.remaining.isZero
    val completedLabel: String get() = summary.completed.hoursLabel()
    val remainingLabel: String get() = summary.remaining.hoursLabel()
    val requiredLabel: String get() = summary.required.hoursLabel()
    val percentageLabel: String get() = "${(summary.progress * 100).toInt()}%"
    val dateLabel: String get() = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    val goalLabel: String get() = if (summary.required.isZero) "Set required hours in Profile" else "Goal: $requiredLabel"

    companion object {
        /** Return a camera event only when today's saved state has one clear, recoverable next slot. */
        fun safeCameraAction(date: LocalDate, raw: WidgetInputs): AttendanceEventType? {
            val inputs = raw.copy(resolutions = effectiveResolutions(raw.events, raw.resolutions, raw.calendarOverrides))
            if (isExplicitDayOff(date, inputs.workingWeekdays, inputs.calendarOverrides)) return null
            if (AttendanceRecovery.unfinishedBefore(date, inputs.events, inputs.resolutions).isNotEmpty()) return null
            if (inputs.legacy.any { it.timeOut == null }) return null
            val todayEvents = inputs.events.filter { it.date == date }
            if (todayEvents.map { it.type }.distinct().size != todayEvents.size) return null
            val ordered = todayEvents.sortedBy { it.type.code }
            if (ordered.zipWithNext().any { (a, b) -> b.occurredAt <= a.occurredAt }) return null
            val todayResolutions = inputs.resolutions.filter { it.date == date }
            if (todayResolutions.map { it.session }.distinct().size != todayResolutions.size) return null
            if (todayResolutions.any { resolution -> todayEvents.any { it.type.code / 2 == resolution.session.code } }) return null
            if (todayEvents.any { event -> event.type.code % 2 == 1 && todayEvents.none { it.type.code == event.type.code - 1 } }) return null
            return DailyAttendance.next(date, inputs.events, inputs.resolutions)
        }

        fun from(raw: WidgetInputs, now: Instant): WidgetSnapshot {
            val inputs = raw.copy(resolutions = effectiveResolutions(raw.events, raw.resolutions, raw.calendarOverrides))
            val day = now.atZone(AttendanceCalculations.manila).toLocalDate()
            val sessions = inputs.legacy + DailyAttendance.sessions(inputs.events, inputs.resolutions)
            // This is the same aggregation and calculator used by OjtViewModel/Dashboard.
            val summary = AttendanceCalculations.summary(sessions, inputs.requiredHours)
            val days = AttendanceCalculations.dailyHours(sessions).count { it.value > java.time.Duration.ZERO }
            val forecast = CompletionForecasts.calculate(day, sessions, inputs.requiredHours,
                inputs.workingWeekdays, inputs.calendarOverrides, inputs.expectedHoursPerDay)
            val estimate = forecast.estimatedCompletionDate
            val hasTodayRecords = inputs.events.any { it.date == day } || inputs.resolutions.any { it.date == day } ||
                inputs.legacy.any { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == day || it.timeOut?.atZone(AttendanceCalculations.manila)?.toLocalDate() == day }
            val dayOff = isExplicitDayOff(day, inputs.workingWeekdays, inputs.calendarOverrides) && !hasTodayRecords
            val status = when {
                dayOff -> when (inputs.calendarOverrides.wholeDay().firstOrNull { it.date == day }?.kind) {
                    CalendarOverrideKind.ABSENT -> WidgetAttendanceStatus.ABSENT
                    CalendarOverrideKind.SICK_LEAVE -> WidgetAttendanceStatus.SICK_LEAVE
                    CalendarOverrideKind.COMPANY_CLOSED -> WidgetAttendanceStatus.COMPANY_CLOSED
                    CalendarOverrideKind.OTHER -> WidgetAttendanceStatus.OTHER_EXCEPTION
                    else -> WidgetAttendanceStatus.DAY_OFF
                }
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
            val next = DailyAttendance.next(day, inputs.events, inputs.resolutions)
            val action = when {
                status == WidgetAttendanceStatus.RECOVERY_NEEDED || status == WidgetAttendanceStatus.LEGACY_OPEN -> "Resolve attendance"
                dayOff -> if (status == WidgetAttendanceStatus.DAY_OFF) "No attendance required today" else "No attendance scheduled today"
                !summary.required.isZero && summary.remaining.isZero -> "Requirement reached"
                next == null -> "No action available"
                else -> "Next: ${next.label}"
            }
            return WidgetSnapshot(summary, day, status, days, estimate, action, forecast)
        }

        fun estimate(from: LocalDate, remaining: java.time.Duration, hoursPerDay: Int?, weekdays: Set<java.time.DayOfWeek>): LocalDate? {
            if (remaining.isZero || remaining.isNegative) return from
            if (hoursPerDay !in 1..24 || weekdays.isEmpty()) return null
            var leftMinutes = remaining.toMinutes()
            var date = from
            repeat(36600) {
                date = date.plusDays(1)
                if (date.dayOfWeek in weekdays) {
                    leftMinutes -= hoursPerDay!!.toLong() * 60L
                    if (leftMinutes <= 0) return date
                }
            }
            return null
        }

        fun nextMidnight(now: Instant): Instant = now.atZone(AttendanceCalculations.manila).toLocalDate()
            .plusDays(1).atStartOfDay(AttendanceCalculations.manila).toInstant()
    }
}
