package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WorkdayProjectionTest {
    private val monday = LocalDate.parse("2026-11-23")
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private fun session(id: String, date: LocalDate, hour: Int, length: Long) = AttendanceSession(id,
        date.atTime(hour, 0).atZone(AttendanceCalculations.manila).toInstant(),
        date.atTime(hour, 0).atZone(AttendanceCalculations.manila).toInstant().plusSeconds(length * 3600))
    private fun snapshot(input: WidgetInputs, date: LocalDate) = WidgetSnapshot.from(input, date.atStartOfDay(AttendanceCalculations.manila).toInstant())

    @Test fun `all nonworking reasons stay distinct and suppress future reminder eligibility`() {
        val expected = mapOf(CalendarOverrideKind.DAY_OFF to WidgetAttendanceStatus.DAY_OFF,
            CalendarOverrideKind.ABSENT to WidgetAttendanceStatus.ABSENT, CalendarOverrideKind.SICK_LEAVE to WidgetAttendanceStatus.SICK_LEAVE,
            CalendarOverrideKind.COMPANY_CLOSED to WidgetAttendanceStatus.COMPANY_CLOSED, CalendarOverrideKind.OTHER to WidgetAttendanceStatus.OTHER_EXCEPTION)
        expected.forEach { (reason, status) ->
            val input = WidgetInputs(requiredHours = 40, workingWeekdays = weekdays,
                calendarOverrides = listOf(CalendarDayOverride(monday, reason, "Test reason")))
            assertEquals(status, snapshot(input, monday).status)
            assertEquals(0, snapshot(input, monday).completedDays)
            assertFalse(attendanceReminderEligible(monday, input))
            assertNull(WidgetSnapshot.safeCameraAction(monday, input))
        }
    }
    @Test fun `missing past scheduled workday is never automatically absent`() {
        assertEquals(OjtCalendarDayStatus.NO_ATTENDANCE_RECORDED, calendarDayStatus(monday, weekdays, emptyList(), false, monday.plusDays(1)))
        assertEquals(OjtCalendarDayStatus.SCHEDULED_WORKDAY, calendarDayStatus(monday, weekdays, emptyList(), false, monday))
    }
    @Test fun `delayed refresh after Manila midnight projects from new date without crediting missed day`() {
        val input = WidgetInputs(requiredHours = 16, expectedHoursPerDay = 8, workingWeekdays = weekdays)
        val before = WidgetSnapshot.from(input, Instant.parse("2026-11-23T15:59:59Z"))
        val delayed = WidgetSnapshot.from(input, Instant.parse("2026-11-23T21:30:00Z"))
        assertEquals(monday.plusDays(2), before.estimatedCompletionDate)
        assertEquals(monday.plusDays(3), delayed.estimatedCompletionDate)
        assertEquals(before.summary, delayed.summary)
        assertEquals(monday.plusDays(1), delayed.date)
    }
    @Test fun `weekend and consecutive closures are skipped and editing restores projection`() {
        val friday = monday.plusDays(4)
        val input = WidgetInputs(requiredHours = 16, expectedHoursPerDay = 8, workingWeekdays = weekdays,
            calendarOverrides = listOf(CalendarDayOverride(monday.plusDays(7), CalendarOverrideKind.DAY_OFF),
                CalendarDayOverride(monday.plusDays(8), CalendarOverrideKind.COMPANY_CLOSED)))
        assertEquals(monday.plusDays(10), snapshot(input, friday).estimatedCompletionDate)
        assertEquals(monday.plusDays(8), snapshot(input.copy(calendarOverrides = emptyList()), friday).estimatedCompletionDate)
    }
    @Test fun `partial and new attendance change pace without double counting a date`() {
        val first = monday.minusDays(7)
        val history = listOf(session("a", first, 8, 4), session("b", first, 13, 4),
            session("c", first.plusDays(1), 8, 4), session("d", first.plusDays(2), 8, 6))
        val input = WidgetInputs(requiredHours = 48, legacy = history, workingWeekdays = weekdays)
        val before = snapshot(input, monday)
        assertEquals(3, before.completedDays)
        assertEquals(Duration.ofHours(6), before.forecast.averageDailyCredit)
        assertEquals(5L, before.forecast.estimatedRemainingWorkdays)
        val after = snapshot(input.copy(legacy = history + session("e", monday, 8, 8)), monday)
        assertEquals(4, after.completedDays)
        assertEquals(Duration.ofMinutes(390), after.forecast.averageDailyCredit)
        assertEquals(4L, after.forecast.estimatedRemainingWorkdays)
        assertEquals(Duration.ofHours(26), after.summary.completed)
    }
    @Test fun `exception does not erase historical credit and Other requires notes`() {
        val input = WidgetInputs(legacy = listOf(session("saved", monday, 8, 4)),
            calendarOverrides = listOf(CalendarDayOverride(monday, CalendarOverrideKind.COMPANY_CLOSED)))
        assertEquals(Duration.ofHours(4), snapshot(input, monday).summary.completed)
        assertEquals(1, snapshot(input, monday).completedDays)
        try { validateWorkdayException(CalendarOverrideKind.OTHER, " "); fail("Other must be specified") }
        catch (_: IllegalArgumentException) { }
    }
}
