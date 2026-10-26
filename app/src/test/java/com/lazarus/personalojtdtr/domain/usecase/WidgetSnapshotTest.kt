package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class WidgetSnapshotTest {
    private val day = LocalDate.parse("2026-10-25")
    private val morning = Instant.parse("2026-10-25T00:00:00Z") // 08:00 Manila
    private val now = morning.plus(Duration.ofHours(10))
    private fun events(count: Int, date: LocalDate = day) = AttendanceEventType.entries.take(count).map { type ->
        val time = date.atTime(8 + type.code * 2, 0).atZone(AttendanceCalculations.manila).toInstant()
        AttendanceEvent("e${type.code}-$date", date, type, time, time, "original-${type.code}", "stamped-${type.code}")
    }
    private fun skip(session: OjtSession) = SessionResolution("skip-$session", day, session, ResolutionKind.SKIPPED, morning)
    private fun snapshot(input: WidgetInputs = WidgetInputs()) = WidgetSnapshot.from(input, now)

    @Test fun `summary exactly matches Dashboard aggregation including legacy breaks`() {
        val legacy = AttendanceSession("legacy", morning.minus(Duration.ofDays(1)), morning.minus(Duration.ofDays(1)).plusSeconds(3600), Duration.ofMinutes(15))
        val input = WidgetInputs(40, listOf(legacy), events(4))
        val expected = AttendanceCalculations.summary(input.legacy + DailyAttendance.sessions(input.events, input.resolutions), 40)
        assertEquals(expected, snapshot(input).summary)
        assertEquals("4h 45m", snapshot(input).completedLabel)
        assertEquals("35h 15m", snapshot(input).remainingLabel)
    }
    @Test fun `unset profile safely displays zero goal and zero progress`() {
        val state = snapshot()
        assertEquals("0h 0m", state.completedLabel)
        assertEquals("0h 0m", state.remainingLabel)
        assertEquals("0%", state.percentageLabel)
        assertEquals("Set required hours in Profile", state.goalLabel)
    }
    @Test fun `zero required hours retains actual completed hours`() {
        val state = snapshot(WidgetInputs(events = events(4)))
        assertEquals("4h 0m", state.completedLabel)
        assertEquals("0h 0m", state.remainingLabel)
        assertEquals("0%", state.percentageLabel)
    }
    @Test fun `over goal caps percentage and remaining without truncating completed`() {
        val state = snapshot(WidgetInputs(requiredHours = 1, events = events(4)))
        assertEquals("4h 0m", state.completedLabel)
        assertEquals("0h 0m", state.remainingLabel)
        assertEquals("100%", state.percentageLabel)
        assertEquals(1f, state.summary.progress, 0f)
    }
    @Test fun `percentage and duration labels follow Dashboard formatting`() {
        val state = snapshot(WidgetInputs(requiredHours = 12, events = events(4)))
        assertEquals("33%", state.percentageLabel)
        assertEquals("12h 0m", state.requiredLabel)
        assertEquals("Goal: 12h 0m", state.goalLabel)
        assertEquals("Oct 25", state.dateLabel)
    }
    @Test fun `new day is not started`() { assertEquals(WidgetAttendanceStatus.NOT_STARTED, snapshot().status) }
    @Test fun `morning time in does not credit an open interval`() {
        val state = snapshot(WidgetInputs(40, events = events(1)))
        assertEquals(WidgetAttendanceStatus.MORNING_IN, state.status)
        assertEquals(Duration.ZERO, state.summary.completed)
    }
    @Test fun `morning time out maps to morning completed`() {
        assertEquals(WidgetAttendanceStatus.MORNING_COMPLETED, snapshot(WidgetInputs(events = events(2))).status)
    }
    @Test fun `afternoon time in credits only closed morning`() {
        val state = snapshot(WidgetInputs(events = events(3)))
        assertEquals(WidgetAttendanceStatus.AFTERNOON_IN, state.status)
        assertEquals(Duration.ofHours(2), state.summary.completed)
    }
    @Test fun `four events map to completed`() {
        assertEquals(WidgetAttendanceStatus.COMPLETED, snapshot(WidgetInputs(events = events(4))).status)
    }
    @Test fun `skipped morning is not reported as worked`() {
        assertEquals(WidgetAttendanceStatus.MORNING_SKIPPED, snapshot(WidgetInputs(resolutions = listOf(skip(OjtSession.MORNING)))).status)
    }
    @Test fun `afternoon only day follows domain next event`() {
        val state = snapshot(WidgetInputs(events = events(3).drop(2), resolutions = listOf(skip(OjtSession.MORNING))))
        assertEquals(WidgetAttendanceStatus.AFTERNOON_IN, state.status)
    }
    @Test fun `completed morning and skipped afternoon are explicitly distinguished`() {
        val state = snapshot(WidgetInputs(events = events(2), resolutions = listOf(skip(OjtSession.AFTERNOON))))
        assertEquals(WidgetAttendanceStatus.FINISHED_WITH_SKIPS, state.status)
        assertEquals(Duration.ofHours(2), state.summary.completed)
    }
    @Test fun `fully skipped day credits zero hours`() {
        val state = snapshot(WidgetInputs(resolutions = OjtSession.entries.map(::skip)))
        assertEquals(WidgetAttendanceStatus.FINISHED_WITH_SKIPS, state.status)
        assertEquals(Duration.ZERO, state.summary.completed)
    }
    @Test fun `unresolved earlier event asks for recovery rather than new attendance`() {
        val state = snapshot(WidgetInputs(events = events(1, day.minusDays(1))))
        assertEquals(WidgetAttendanceStatus.RECOVERY_NEEDED, state.status)
        assertEquals(Duration.ZERO, state.summary.completed)
    }
    @Test fun `resolving unfinished previous day removes recovery status without credit`() {
        val resolution = SessionResolution("resolved", day.minusDays(1), OjtSession.MORNING, ResolutionKind.INCOMPLETE, now)
        val state = snapshot(WidgetInputs(events = events(1, day.minusDays(1)), resolutions = listOf(resolution)))
        assertEquals(WidgetAttendanceStatus.NOT_STARTED, state.status)
        assertEquals(Duration.ZERO, state.summary.completed)
    }
    @Test fun `legacy active and previous day sessions are distinguished`() {
        val active = AttendanceSession("old", morning)
        assertEquals(WidgetAttendanceStatus.LEGACY_IN, snapshot(WidgetInputs(legacy = listOf(active))).status)
        assertEquals(WidgetAttendanceStatus.LEGACY_OPEN, snapshot(WidgetInputs(legacy = listOf(active.copy(timeIn = morning.minusSeconds(86400))))).status)
    }
    @Test fun `closed legacy session today maps to completed legacy`() {
        val closed = AttendanceSession("old", morning, morning.plusSeconds(3600))
        assertEquals(WidgetAttendanceStatus.LEGACY_COMPLETED, snapshot(WidgetInputs(legacy = listOf(closed))).status)
    }
    @Test fun `overlaps still use shared calculator rejection`() {
        val a = AttendanceSession("a", morning, morning.plusSeconds(3600))
        assertEquals(Duration.ZERO, snapshot(WidgetInputs(legacy = listOf(a, a.copy(id = "b")))).summary.completed)
    }
    @Test fun `status rolls over at Manila midnight while total stays constant`() {
        val input = WidgetInputs(40, events = events(4))
        val before = WidgetSnapshot.from(input, Instant.parse("2026-10-25T15:59:59Z"))
        val after = WidgetSnapshot.from(input, Instant.parse("2026-10-25T16:00:00Z"))
        assertEquals(day, before.date)
        assertEquals(day.plusDays(1), after.date)
        assertEquals(WidgetAttendanceStatus.COMPLETED, before.status)
        assertEquals(WidgetAttendanceStatus.NOT_STARTED, after.status)
        assertEquals(before.summary, after.summary)
    }
    @Test fun `open interval becomes recovery needed on day rollover`() {
        val input = WidgetInputs(events = events(1))
        val boundary = WidgetSnapshot.nextMidnight(now)
        assertEquals(WidgetAttendanceStatus.MORNING_IN, WidgetSnapshot.from(input, boundary.minusMillis(1)).status)
        assertEquals(WidgetAttendanceStatus.RECOVERY_NEEDED, WidgetSnapshot.from(input, boundary).status)
    }
    @Test fun `midnight scheduling always uses the next Manila calendar day`() {
        assertEquals(Instant.parse("2026-10-25T16:00:00Z"), WidgetSnapshot.nextMidnight(now))
        assertEquals(Instant.parse("2026-10-26T16:00:00Z"), WidgetSnapshot.nextMidnight(Instant.parse("2026-10-25T16:00:00Z")))
        assertEquals(Instant.parse("2026-12-31T16:00:00Z"), WidgetSnapshot.nextMidnight(Instant.parse("2026-12-31T15:59:59Z")))
    }
    @Test fun `shortcut actions are distinct and accept only known destinations`() {
        assertNotEquals(WidgetDestination.ATTENDANCE.action, WidgetDestination.ACTIVITIES.action)
        WidgetDestination.entries.forEach { assertEquals(it, WidgetDestination.fromAction(it.action)) }
        assertEquals("attendance", WidgetDestination.ATTENDANCE.route)
        assertEquals("activities", WidgetDestination.ACTIVITIES.route)
        assertNull(WidgetDestination.fromAction(null))
        assertNull(WidgetDestination.fromAction("profile"))
    }
    @Test fun `new snapshot reflects required hours changes and restored inputs`() {
        val before = snapshot(WidgetInputs(40, events = events(4)))
        val changed = snapshot(WidgetInputs(4, events = events(4)))
        assertNotEquals(before.percentageLabel, changed.percentageLabel)
        assertEquals("100%", changed.percentageLabel)
        val restored = snapshot(WidgetInputs(10))
        assertEquals("0h 0m", restored.completedLabel)
        assertEquals("10h 0m", restored.remainingLabel)
        assertEquals(WidgetAttendanceStatus.NOT_STARTED, restored.status)
    }
}
