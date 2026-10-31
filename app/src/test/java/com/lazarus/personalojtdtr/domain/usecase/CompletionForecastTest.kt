package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class CompletionForecastTest {
    private val today = LocalDate.parse("2026-11-23")
    private fun session(date: LocalDate, hours: Long) = AttendanceSession(
        "s-$date", date.atTime(8, 0).atZone(AttendanceCalculations.manila).toInstant(),
        date.atTime(8, 0).atZone(AttendanceCalculations.manila).toInstant().plus(Duration.ofHours(hours)))

    @Test fun `no history and one or two credited dates do not invent automatic forecasts`() {
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        assertEquals(ForecastMethod.INSUFFICIENT_HISTORY, CompletionForecasts.calculate(today, emptyList(), 486, weekdays, emptyList(), null).method)
        assertEquals(ForecastMethod.INSUFFICIENT_HISTORY, CompletionForecasts.calculate(today,
            listOf(session(today.minusDays(7), 8), session(today.minusDays(6), 8)), 486, weekdays, emptyList(), null).method)
    }

    @Test fun `three credited days provide average pace and earliest credited date`() {
        val days = listOf(session(today.minusDays(7), 8), session(today.minusDays(6), 4), session(today.minusDays(5), 6))
        val forecast = CompletionForecasts.calculate(today, days, 120, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), emptyList(), null)
        assertEquals(ForecastMethod.AUTOMATIC, forecast.method)
        assertEquals(today.minusDays(7), forecast.earliestCreditedDate)
        assertEquals(3, forecast.observedWorkdays)
        assertEquals(Duration.ofHours(6), forecast.averageDailyCredit)
        assertEquals(17L, forecast.estimatedRemainingWorkdays)
        assertNotNull(forecast.estimatedCompletionDate)
    }

    @Test fun `manual pace overrides observed pace and projection skips days off and includes special workdays`() {
        val schedule = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val overrides = listOf(
            CalendarDayOverride(today.plusDays(1), CalendarOverrideKind.DAY_OFF),
            CalendarDayOverride(today.plusDays(2), CalendarOverrideKind.DAY_OFF),
            CalendarDayOverride(today.plusDays(3), CalendarOverrideKind.DAY_OFF),
            CalendarDayOverride(today.plusDays(4), CalendarOverrideKind.DAY_OFF),
            CalendarDayOverride(today.plusDays(6), CalendarOverrideKind.WORKING_DAY),
        )
        val forecast = CompletionForecasts.calculate(today, listOf(session(today.minusDays(7), 4),
            session(today.minusDays(6), 4), session(today.minusDays(5), 4)), 20, schedule, overrides, 8)
        assertEquals(ForecastMethod.MANUAL_OVERRIDE, forecast.method)
        assertEquals(1L, forecast.estimatedRemainingWorkdays)
        assertEquals(today.plusDays(6), forecast.estimatedCompletionDate)
    }

    @Test fun `no weekday schedule needs enough repeated history before tentative inference`() {
        val insufficient = listOf(session(today.minusDays(3), 8), session(today.minusDays(2), 8), session(today.minusDays(1), 8))
        assertEquals(ForecastMethod.SCHEDULE_REQUIRED,
            CompletionForecasts.calculate(today, insufficient, 40, emptySet(), emptyList(), null).method)
        val enough = listOf(14L, 13L, 12L, 7L, 6L).map { session(today.minusDays(it), 8) }
        assertEquals(ForecastMethod.AUTOMATIC,
            CompletionForecasts.calculate(today, enough, 50, emptySet(), emptyList(), null).method)
    }

    @Test fun `regular and date overrides distinguish work and days off`() {
        val date = LocalDate.parse("2026-11-24")
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        assertFalse(isWorkingDate(date, weekdays, listOf(CalendarDayOverride(date, CalendarOverrideKind.DAY_OFF))))
        assertTrue(isWorkingDate(date.plusDays(5), weekdays, listOf(CalendarDayOverride(date.plusDays(5), CalendarOverrideKind.WORKING_DAY))))
        assertEquals(OjtCalendarDayStatus.SPECIAL_DAY_OFF, calendarDayStatus(date, weekdays,
            listOf(CalendarDayOverride(date, CalendarOverrideKind.DAY_OFF)), false))
        assertEquals(OjtCalendarDayStatus.ATTENDANCE_RECORDED, calendarDayStatus(date, weekdays,
            listOf(CalendarDayOverride(date, CalendarOverrideKind.DAY_OFF)), true))
    }

    @Test fun `completed requirement never fabricates a future forecast`() {
        val sessions = listOf(session(today.minusDays(1), 8))
        val result = CompletionForecasts.calculate(today, sessions, 8, setOf(today.dayOfWeek), emptyList(), null)
        assertEquals(ForecastMethod.COMPLETED, result.method)
        assertNull(result.estimatedCompletionDate)
    }

    @Test fun `missing schedule takes priority while manual schedule forecasts without history`() {
        assertEquals(ForecastMethod.SCHEDULE_REQUIRED,
            CompletionForecasts.calculate(today, emptyList(), 40, emptySet(), emptyList(), null).method)
        val manual = CompletionForecasts.calculate(today, emptyList(), 16, setOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), emptyList(), 8)
        assertEquals(ForecastMethod.MANUAL_OVERRIDE, manual.method)
        assertEquals(today.plusDays(2), manual.estimatedCompletionDate)
    }
    @Test fun `cancelling day off restores reminder eligibility and forecast date`() {
        val input = WidgetInputs(requiredHours = 16, expectedHoursPerDay = 8,
            workingWeekdays = DayOfWeek.entries.toSet())
        val off = input.copy(calendarOverrides = listOf(CalendarDayOverride(today, CalendarOverrideKind.DAY_OFF),
            CalendarDayOverride(today.plusDays(1), CalendarOverrideKind.DAY_OFF)))
        assertFalse(attendanceReminderEligible(today, off))
        assertTrue(attendanceReminderEligible(today, input))
        val before = CompletionForecasts.calculate(today, emptyList(), 16, input.workingWeekdays, off.calendarOverrides, 8)
        val after = CompletionForecasts.calculate(today, emptyList(), 16, input.workingWeekdays, emptyList(), 8)
        assertEquals(today.plusDays(3), before.estimatedCompletionDate)
        assertEquals(today.plusDays(2), after.estimatedCompletionDate)
        assertFalse(attendanceReminderEligible(today, input.copy(workingWeekdays = setOf(DayOfWeek.TUESDAY))))
    }
}
