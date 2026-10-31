package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate

enum class ForecastMethod { AUTOMATIC, MANUAL_OVERRIDE, INSUFFICIENT_HISTORY, SCHEDULE_REQUIRED, COMPLETED, NO_REQUIREMENT, PROJECTION_LIMIT }
data class CompletionForecast(
    val method: ForecastMethod,
    val earliestCreditedDate: LocalDate? = null,
    val observedWorkdays: Int = 0,
    val averageDailyCredit: Duration? = null,
    val estimatedRemainingWorkdays: Long? = null,
    val estimatedCompletionDate: LocalDate? = null,
    val projectedWeekdays: Set<DayOfWeek> = emptySet(),
)

object CompletionForecasts {
    const val MINIMUM_AUTOMATIC_DAYS = 3
    private const val MAX_PROJECTION_DAYS = 36_600L

    fun calculate(
        today: LocalDate,
        sessions: List<AttendanceSession>,
        requiredHours: Long,
        configuredWeekdays: Set<DayOfWeek>,
        overrides: List<CalendarDayOverride>,
        manualHoursPerDay: Int?,
    ): CompletionForecast {
        require(requiredHours in 0..100_000)
        val summary = AttendanceCalculations.summary(sessions, requiredHours)
        if (requiredHours > 0 && summary.remaining.isZero) return CompletionForecast(ForecastMethod.COMPLETED)

        val daily = AttendanceCalculations.dailyHours(sessions).filterValues { it > Duration.ZERO }
        val weekdays = when {
            configuredWeekdays.isNotEmpty() -> configuredWeekdays
            daily.size >= 5 -> daily.keys.groupingBy { it.dayOfWeek }.eachCount()
                .filterValues { it >= 2 }.keys
            else -> emptySet()
        }
        val base = CompletionForecast( ForecastMethod.INSUFFICIENT_HISTORY,
            earliestCreditedDate = daily.keys.minOrNull(), observedWorkdays = daily.size,
            averageDailyCredit = if (daily.isEmpty()) null else daily.values.fold(Duration.ZERO, Duration::plus).dividedBy(daily.size.toLong()),
            projectedWeekdays = weekdays)
        if (requiredHours == 0L) return base.copy(method = ForecastMethod.NO_REQUIREMENT)
        if (weekdays.isEmpty()) return base.copy(method = ForecastMethod.SCHEDULE_REQUIRED)

        val autoPace = base.averageDailyCredit?.takeIf { daily.size >= MINIMUM_AUTOMATIC_DAYS && !it.isZero }
        val manualPace = manualHoursPerDay?.takeIf { it in 1..24 }?.let { Duration.ofHours(it.toLong()) }
        val pace = manualPace ?: autoPace
            ?: return base.copy(method = ForecastMethod.INSUFFICIENT_HISTORY)
        val remainingMillis = summary.remaining.toMillis()
        val paceMillis = pace.toMillis().coerceAtLeast(1L)
        val workdays = if (remainingMillis <= 0) 0L else 1L + (remainingMillis - 1L) / paceMillis
        val overrideByDate = overrides.wholeDay().associateBy { it.date }
        var candidate = today
        var eligible = 0L
        var leftMillis = remainingMillis
        var resultDate: LocalDate? = null
        for (dayOffset in 0 until MAX_PROJECTION_DAYS) {
            candidate = candidate.plusDays(1)
            val override = overrideByDate[candidate]
            val isWorking = override?.kind?.let { !it.nonWorking } ?: (candidate.dayOfWeek in weekdays)
            if (isWorking) {
                val skipped = overrides.filter { it.date == candidate && it.scope != WorkdayScope.WHOLE_DAY && it.kind.nonWorking }.map { it.scope }.distinct().size
                if (skipped >= 2) continue
                eligible++
                leftMillis -= paceMillis * (2 - skipped) / 2
                if (leftMillis <= 0) { resultDate = candidate; break }
            }
        }
        val method = if (manualPace != null) ForecastMethod.MANUAL_OVERRIDE else ForecastMethod.AUTOMATIC
        if (resultDate == null) return CompletionForecast(ForecastMethod.PROJECTION_LIMIT,
            base.earliestCreditedDate, base.observedWorkdays, base.averageDailyCredit, workdays, null, weekdays)
        return CompletionForecast(method, base.earliestCreditedDate, base.observedWorkdays,
            base.averageDailyCredit, eligible, resultDate, weekdays)
    }
}

fun isWorkingDate(date: LocalDate, weekdays: Set<DayOfWeek>, overrides: List<CalendarDayOverride>): Boolean =
    overrides.wholeDay().firstOrNull { it.date == date }?.kind?.let { !it.nonWorking } ?: (date.dayOfWeek in weekdays)

// Includes all explicit non-attendance exceptions as well as regular rest days.
fun isExplicitDayOff(date: LocalDate, weekdays: Set<DayOfWeek>, overrides: List<CalendarDayOverride>): Boolean =
    overrides.wholeDay().firstOrNull { it.date == date }?.kind?.nonWorking ?: (weekdays.isNotEmpty() && date.dayOfWeek !in weekdays)

fun calendarDayStatus(date: LocalDate, weekdays: Set<DayOfWeek>, overrides: List<CalendarDayOverride>, hasAttendance: Boolean,
    today: LocalDate = LocalDate.now(AttendanceCalculations.manila)): OjtCalendarDayStatus {
    if (hasAttendance) return OjtCalendarDayStatus.ATTENDANCE_RECORDED
    return when (overrides.wholeDay().firstOrNull { it.date == date }?.kind) {
        CalendarOverrideKind.DAY_OFF -> OjtCalendarDayStatus.SPECIAL_DAY_OFF
        CalendarOverrideKind.WORKING_DAY -> OjtCalendarDayStatus.SPECIAL_WORKING_DAY
        CalendarOverrideKind.ABSENT -> OjtCalendarDayStatus.ABSENT
        CalendarOverrideKind.SICK_LEAVE -> OjtCalendarDayStatus.SICK_LEAVE
        CalendarOverrideKind.COMPANY_CLOSED -> OjtCalendarDayStatus.COMPANY_CLOSED
        CalendarOverrideKind.OTHER -> OjtCalendarDayStatus.OTHER_EXCEPTION
        null -> when {
            weekdays.isEmpty() -> OjtCalendarDayStatus.NO_ATTENDANCE_RECORDED
            date.dayOfWeek !in weekdays -> OjtCalendarDayStatus.REGULAR_DAY_OFF
            date < today -> OjtCalendarDayStatus.NO_ATTENDANCE_RECORDED
            else -> OjtCalendarDayStatus.SCHEDULED_WORKDAY
        }
    }
}

/** Future scheduler must recheck this immediately before notification delivery. */
fun attendanceReminderEligible(date: LocalDate, inputs: WidgetInputs): Boolean =
    isWorkingDate(date, inputs.workingWeekdays, inputs.calendarOverrides) &&
        DailyAttendance.next(date, inputs.events, inputs.resolutions) != null
