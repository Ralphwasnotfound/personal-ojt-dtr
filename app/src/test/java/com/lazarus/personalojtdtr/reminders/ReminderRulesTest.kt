package com.lazarus.personalojtdtr.reminders

import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRulesTest {
    private val day = LocalDate.parse("2026-11-23")
    private val settings = ReminderSettings(enabled = true)
    private val input = WidgetInputs(requiredHours = 80, workingWeekdays = DayOfWeek.entries.toSet())
    private fun at(hour: Int, minute: Int) = day.atTime(hour, minute).atZone(ReminderRules.zone).toInstant()
    private fun skipped(session: WorkdayScope) = input.copy(calendarOverrides = listOf(CalendarDayOverride(day, CalendarOverrideKind.SICK_LEAVE,
        updatedAt = at(7, 0), scope = session)))
    private fun event(type: AttendanceEventType) = AttendanceEvent("e${type.code}", day, type, at(8 + type.code, 0), at(8 + type.code, 0), "o${type.code}", "s${type.code}")
    @Test fun `default targets match both sessions including Get Ready and followups`() {
        assertEquals(at(7, 50), ReminderRules.target(day, OjtSession.MORNING, ReminderOccurrence.READY, settings))
        assertEquals(at(8, 0), ReminderRules.target(day, OjtSession.MORNING, ReminderOccurrence.FIRST, settings))
        assertEquals(at(8, 30), ReminderRules.target(day, OjtSession.MORNING, ReminderOccurrence.SECOND, settings))
        assertEquals(at(12, 50), ReminderRules.target(day, OjtSession.AFTERNOON, ReminderOccurrence.READY, settings))
        assertEquals(at(13, 0), ReminderRules.target(day, OjtSession.AFTERNOON, ReminderOccurrence.FIRST, settings))
        assertEquals(at(13, 30), ReminderRules.target(day, OjtSession.AFTERNOON, ReminderOccurrence.SECOND, settings))
    }
    @Test fun `morning timing and Get Ready controls are independent of afternoon`() {
        val custom = settings.copy(morningStart = 540, morningLead = 30, afternoonLead = 5, morningReady = false)
        assertEquals(at(8, 30), ReminderRules.target(day, OjtSession.MORNING, ReminderOccurrence.READY, custom))
        assertEquals(at(12, 55), ReminderRules.target(day, OjtSession.AFTERNOON, ReminderOccurrence.READY, custom))
        assertFalse(ReminderRules.deliverable(custom, input, day, OjtSession.MORNING, ReminderOccurrence.READY, at(8, 30), true))
    }
    @Test fun `session skips suppress only that session and unlock valid afternoon`() {
        assertFalse(ReminderRules.eligible(skipped(WorkdayScope.MORNING), day, OjtSession.MORNING))
        assertTrue(ReminderRules.eligible(skipped(WorkdayScope.MORNING), day, OjtSession.AFTERNOON))
        assertTrue(ReminderRules.eligible(skipped(WorkdayScope.AFTERNOON), day, OjtSession.MORNING))
        assertFalse(ReminderRules.eligible(skipped(WorkdayScope.AFTERNOON), day, OjtSession.AFTERNOON))
        assertEquals(AttendanceEventType.AFTERNOON_TIME_IN, WidgetSnapshot.safeCameraAction(day, skipped(WorkdayScope.MORNING)))
    }
    @Test fun `all whole day nonworking reasons suppress both sessions`() {
        CalendarOverrideKind.entries.filter { it.nonWorking }.forEach { kind ->
            val off = input.copy(calendarOverrides = listOf(CalendarDayOverride(day, kind, "Reason")))
            OjtSession.entries.forEach { assertFalse(ReminderRules.eligible(off, day, it)) }
        }
        val special = input.copy(workingWeekdays = emptySet(), calendarOverrides = listOf(CalendarDayOverride(day, CalendarOverrideKind.WORKING_DAY)))
        assertTrue(ReminderRules.eligible(special, day, OjtSession.MORNING))
    }
    @Test fun `recorded Time In and recovery suppress reminders without changing records`() {
        val morning = input.copy(events = listOf(event(AttendanceEventType.MORNING_TIME_IN)))
        assertFalse(ReminderRules.eligible(morning, day, OjtSession.MORNING))
        assertFalse(ReminderRules.eligible(morning, day, OjtSession.AFTERNOON))
        val nextDay = day.plusDays(1)
        assertFalse(ReminderRules.eligible(morning, nextDay, OjtSession.MORNING))
        val afternoon = skipped(WorkdayScope.MORNING).copy(events = listOf(event(AttendanceEventType.AFTERNOON_TIME_IN)))
        assertFalse(ReminderRules.eligible(afternoon, day, OjtSession.AFTERNOON))
        assertEquals(1, afternoon.events.size)
    }
    @Test fun `permissions disabled switches and skipped followups never deliver`() {
        assertFalse(ReminderRules.deliverable(settings, input, day, OjtSession.MORNING, ReminderOccurrence.FIRST, at(8, 0), false))
        assertFalse(ReminderRules.deliverable(settings.copy(enabled = false), input, day, OjtSession.MORNING, ReminderOccurrence.FIRST, at(8, 0), true))
        assertFalse(ReminderRules.deliverable(settings.copy(morningEnabled = false), input, day, OjtSession.MORNING, ReminderOccurrence.FIRST, at(8, 0), true))
        assertFalse(ReminderRules.deliverable(settings.copy(secondEnabled = false), input, day, OjtSession.MORNING, ReminderOccurrence.SECOND, at(8, 30), true))
    }
    @Test fun `late execution cannot deliver outdated Get Ready or burst first and second`() {
        val afternoon = skipped(WorkdayScope.MORNING)
        assertTrue(ReminderRules.deliverable(settings, afternoon, day, OjtSession.AFTERNOON, ReminderOccurrence.READY, at(12, 50), true))
        assertFalse(ReminderRules.deliverable(settings, afternoon, day, OjtSession.AFTERNOON, ReminderOccurrence.READY, at(13, 0), true))
        assertFalse(ReminderRules.deliverable(settings, afternoon, day, OjtSession.AFTERNOON, ReminderOccurrence.FIRST, at(13, 30), true))
        assertTrue(ReminderRules.deliverable(settings, afternoon, day, OjtSession.AFTERNOON, ReminderOccurrence.SECOND, at(13, 30), true))
        assertFalse(ReminderRules.deliverable(settings, afternoon, day, OjtSession.AFTERNOON, ReminderOccurrence.SECOND, at(15, 0), true))
    }
    @Test fun `attendance or skip between reminders and midnight invalidates pending delivery`() {
        assertTrue(ReminderRules.deliverable(settings, input, day, OjtSession.MORNING, ReminderOccurrence.FIRST, at(8, 0), true))
        val recorded = input.copy(events = listOf(event(AttendanceEventType.MORNING_TIME_IN)))
        assertFalse(ReminderRules.deliverable(settings, recorded, day, OjtSession.MORNING, ReminderOccurrence.SECOND, at(8, 30), true))
        assertFalse(ReminderRules.deliverable(settings, skipped(WorkdayScope.MORNING), day, OjtSession.MORNING, ReminderOccurrence.SECOND, at(8, 30), true))
        assertFalse(ReminderRules.deliverable(settings, input, day, OjtSession.MORNING, ReminderOccurrence.FIRST, day.plusDays(1).atStartOfDay(ReminderRules.zone).toInstant(), true))
    }
    @Test fun `stable occurrence keys survive restarts and distinguish session date and occurrence`() {
        val key = ReminderRules.key(day, OjtSession.MORNING, ReminderOccurrence.FIRST)
        assertEquals(key, ReminderRules.key(LocalDate.parse(day.toString()), OjtSession.MORNING, ReminderOccurrence.FIRST))
        assertNotEquals(key, ReminderRules.key(day, OjtSession.AFTERNOON, ReminderOccurrence.FIRST))
        assertNotEquals(key, ReminderRules.key(day.plusDays(1), OjtSession.MORNING, ReminderOccurrence.FIRST))
        assertNotEquals(key, ReminderRules.key(day, OjtSession.MORNING, ReminderOccurrence.SECOND))
    }
    @Test fun `notification actions describe navigation only and reject malformed requests`() {
        val skip = ReminderNavigation.parse("com.lazarus.personalojtdtr.reminder.SKIP.AFTERNOON", day.toString(), 1)!!
        assertTrue(skip.skip); assertEquals(OjtSession.AFTERNOON, skip.session); assertEquals(day, skip.date)
        assertFalse(ReminderNavigation.parse("com.lazarus.personalojtdtr.reminder.RECORD.MORNING", day.toString(), 0)!!.skip)
        assertNull(ReminderNavigation.parse("com.lazarus.personalojtdtr.reminder.SKIP.AFTERNOON", day.toString(), 0))
        assertNull(ReminderNavigation.parse("unknown", day.toString(), 0))
        assertNull(ReminderNavigation.parse("com.lazarus.personalojtdtr.reminder.RECORD.MORNING", "invalid", 0))
    }
    @Test fun `half day exceptions reduce forecast capacity without inventing credit`() {
        val date = day.plusDays(1)
        val schedule = input.copy(expectedHoursPerDay = 8, requiredHours = 16,
            calendarOverrides = listOf(CalendarDayOverride(date, CalendarOverrideKind.ABSENT, scope = WorkdayScope.MORNING)))
        val forecast = WidgetSnapshot.from(schedule, at(16, 0))
        assertEquals(day.plusDays(3), forecast.estimatedCompletionDate)
        assertEquals(3L, forecast.forecast.estimatedRemainingWorkdays)
        assertEquals(Duration.ZERO, forecast.summary.completed)
    }

    @Test fun `legacy attendance is not guessed into morning or afternoon reminder actions`() {
        val legacy = input.copy(legacy = listOf(AttendanceSession("legacy", at(8, 0), at(9, 0))))
        assertFalse(ReminderRules.eligible(legacy, day, OjtSession.MORNING))
        assertFalse(ReminderRules.eligible(legacy, day, OjtSession.AFTERNOON))
        assertEquals(Duration.ofHours(1), WidgetSnapshot.from(legacy, at(10, 0)).summary.completed)
    }

    @Test fun `unstarted morning permits afternoon guidance but never an invalid camera action`() {
        assertTrue(ReminderRules.eligible(input, day, OjtSession.AFTERNOON))
        assertTrue(ReminderRules.deliverable(settings, input, day, OjtSession.AFTERNOON, ReminderOccurrence.FIRST, at(13, 0), true))
        assertEquals(AttendanceEventType.MORNING_TIME_IN, WidgetSnapshot.safeCameraAction(day, input))
        assertNotEquals(AttendanceEventType.AFTERNOON_TIME_IN, WidgetSnapshot.safeCameraAction(day, input))
        assertFalse(ReminderRules.eligible(input.copy(events = listOf(event(AttendanceEventType.MORNING_TIME_IN))), day, OjtSession.AFTERNOON))
    }
}
