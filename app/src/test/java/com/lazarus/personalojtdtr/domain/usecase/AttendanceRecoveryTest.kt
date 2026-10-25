package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AttendanceRecoveryTest {
    private val day = LocalDate.of(2026, 10, 26)
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(AttendanceCalculations.manila).toInstant()
    private fun event(code: Int, hour: Int, date: LocalDate = day) = AttendanceEvent("$date-$code", date, AttendanceEventType.fromCode(code), at(date, hour), at(date, hour).minusSeconds(1), "$date-$code-original", "$date-$code-stamped")
    private fun action(session: OjtSession, kind: ResolutionKind, date: LocalDate = day, actedDate: LocalDate = day.plusDays(1), hour: Int = 6) = SessionResolution("action-${session.code}", date, session, kind, at(actedDate, hour))
    @Test fun `previous-day open morning is detected but current day is not`() {
        assertEquals(1, AttendanceRecovery.unfinishedBefore(day.plusDays(1), listOf(event(0, 8)), emptyList()).size)
        assertTrue(AttendanceRecovery.unfinishedBefore(day, listOf(event(0, 8)), emptyList()).isEmpty())
    }
    @Test fun `morning recovery retains original event and allows next day without hours`() {
        val events = listOf(event(0, 8)); val recovery = action(OjtSession.MORNING, ResolutionKind.INCOMPLETE)
        AttendanceRecovery.validate(recovery, events, emptyList())
        assertTrue(AttendanceRecovery.unfinishedBefore(day.plusDays(1), events, listOf(recovery)).isEmpty())
        assertTrue(DailyAttendance.sessions(events, listOf(recovery)).isEmpty())
        DailyAttendance.validate(event(0, 7, day.plusDays(1)), events, emptyList(), listOf(recovery))
        assertEquals(at(day, 8), events.single().occurredAt)
        assertNull(events.firstOrNull { it.type.code == 1 })
    }
    @Test fun `afternoon recovery preserves completed morning hours`() {
        val events = listOf(event(0, 8), event(1, 12), event(2, 13))
        val recovery = action(OjtSession.AFTERNOON, ResolutionKind.INCOMPLETE)
        AttendanceRecovery.validate(recovery, events, emptyList())
        assertEquals(Duration.ofHours(4) to Duration.ZERO, DailyAttendance.daily(day, events, listOf(recovery)))
        DailyAttendance.validate(event(0, 7, day.plusDays(1)), events, emptyList(), listOf(recovery))
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate recovery rejected with another action id`() {
        val recovery = action(OjtSession.MORNING, ResolutionKind.INCOMPLETE)
        AttendanceRecovery.validate(recovery.copy(id = "different"), listOf(event(0, 8)), listOf(recovery))
    }
    @Test(expected = IllegalArgumentException::class) fun `completed session cannot be recovered`() {
        AttendanceRecovery.validate(action(OjtSession.MORNING, ResolutionKind.INCOMPLETE), listOf(event(0, 8), event(1, 12)), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `unstarted session cannot be recovered as missing time out`() {
        AttendanceRecovery.validate(action(OjtSession.MORNING, ResolutionKind.INCOMPLETE), emptyList(), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `same-day in-progress session cannot be skipped`() {
        AttendanceRecovery.validate(action(OjtSession.MORNING, ResolutionKind.SKIPPED, actedDate = day, hour = 9), listOf(event(0, 8)), emptyList())
    }
    @Test fun `afternoon-only workflow explicitly skips morning and credits actual afternoon`() {
        val skip = action(OjtSession.MORNING, ResolutionKind.SKIPPED, actedDate = day, hour = 8)
        AttendanceRecovery.validate(skip, emptyList(), emptyList())
        assertEquals(AttendanceEventType.AFTERNOON_TIME_IN, DailyAttendance.next(day, emptyList(), listOf(skip)))
        val afternoon = listOf(event(2, 10), event(3, 14))
        DailyAttendance.validate(afternoon[0], emptyList(), emptyList(), listOf(skip))
        DailyAttendance.validate(afternoon[1], afternoon.take(1), emptyList(), listOf(skip))
        assertEquals(Duration.ZERO to Duration.ofHours(4), DailyAttendance.daily(day, afternoon, listOf(skip)))
    }
    @Test fun `morning-only half day credits closed morning and skips afternoon`() {
        val events = listOf(event(0, 8), event(1, 11))
        val skip = action(OjtSession.AFTERNOON, ResolutionKind.SKIPPED, actedDate = day, hour = 12)
        AttendanceRecovery.validate(skip, events, emptyList())
        assertNull(DailyAttendance.next(day, events, listOf(skip)))
        assertEquals(Duration.ofHours(3) to Duration.ZERO, DailyAttendance.daily(day, events, listOf(skip)))
    }
    @Test(expected = IllegalArgumentException::class) fun `afternoon cannot be skipped before resolving morning`() {
        AttendanceRecovery.validate(action(OjtSession.AFTERNOON, ResolutionKind.SKIPPED, actedDate = day, hour = 8), emptyList(), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `exception cannot be backdated before captured attendance`() {
        AttendanceRecovery.validate(action(OjtSession.AFTERNOON, ResolutionKind.SKIPPED, actedDate = day, hour = 10), listOf(event(0, 8), event(1, 12)), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `events cannot fill a previously skipped pair`() {
        DailyAttendance.validate(event(0, 9), emptyList(), emptyList(), listOf(action(OjtSession.MORNING, ResolutionKind.SKIPPED, actedDate = day, hour = 8)))
    }
    @Test(expected = IllegalArgumentException::class) fun `cannot silently skip an earlier day`() {
        AttendanceRecovery.validate(action(OjtSession.MORNING, ResolutionKind.SKIPPED), emptyList(), emptyList())
    }
}
