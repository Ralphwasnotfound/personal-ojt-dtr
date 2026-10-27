package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DtrReportTest {
    private val month = YearMonth.of(2026, 10)
    private fun event(day: Int, type: Int, hour: Int, period: YearMonth = month): AttendanceEvent {
        val date = period.atDay(day)
        val at = date.atTime(hour, 0).atZone(AttendanceCalculations.manila).toInstant()
        return AttendanceEvent("$date-$type", date, AttendanceEventType.fromCode(type), at, at, "$date-$type-original", "$date-$type-stamp")
    }
    private fun full(day: Int) = listOf(event(day, 0, 8), event(day, 1, 12), event(day, 2, 13), event(day, 3, 17))
    private fun report(events: List<AttendanceEvent> = emptyList(), legacy: List<AttendanceSession> = emptyList(), period: YearMonth = month,
        name: String = "Student Name", actions: List<SessionResolution> = emptyList()) = DtrReports.create(period, name, events, legacy, actions)
    @Test fun `empty period contains real calendar days with blank times and zero credit`() {
        val r = report()
        assertEquals(31, r.rows.size); assertEquals(0, r.days); assertEquals(Duration.ZERO, r.total)
        assertTrue(r.rows.all { it.times == List(4) { "" } })
    }
    @Test fun `single unclosed event is shown without fabricated time out or hours`() {
        val r = report(listOf(event(2, 0, 8)))
        assertEquals(1, r.days); assertEquals(1, r.incompleteDays)
        assertEquals(listOf("08:00:00", "", "", ""), r.rows[1].times)
        assertEquals(Duration.ZERO, r.total)
    }
    @Test fun `closed morning and unclosed afternoon only credit morning`() {
        val r = report(full(2).dropLast(1))
        assertEquals(Duration.ofHours(4), r.total); assertEquals("", r.rows[1].times[3])
        assertEquals(1, r.incompleteDays)
    }
    @Test fun `full day excludes lunch gap and matches app calculations`() {
        val events = full(2)
        val r = report(events)
        assertEquals(Duration.ofHours(8), r.total)
        assertEquals(AttendanceCalculations.summary(DailyAttendance.sessions(events), 0).completed, r.total)
        assertEquals(0, r.incompleteDays)
    }
    @Test fun `daily and monthly totals preserve exact durations`() {
        val events = full(2) + full(3)
        val r = report(events)
        assertEquals(Duration.ofHours(8), r.rows[1].completed)
        assertEquals(Duration.ofHours(8), r.rows[2].completed)
        assertEquals(Duration.ofHours(16), r.total); assertEquals(2, r.days)
    }
    @Test fun `month filtering uses Manila day rather than UTC day`() {
        val first = event(1, 0, 0)
        assertEquals(LocalDate.of(2026, 9, 30), first.occurredAt.atZone(ZoneOffset.UTC).toLocalDate())
        val events = listOf(first, event(1, 1, 4), event(1, 0, 8, month.plusMonths(1)), event(30, 0, 8, month.minusMonths(1)), event(30, 1, 12, month.minusMonths(1)))
        val r = report(events)
        assertEquals(1, r.days); assertEquals(Duration.ofHours(4), r.total)
    }
    @Test fun `leap February has 29 days and ordinary February has 28`() {
        assertEquals(29, report(period = YearMonth.of(2024, 2)).rows.size)
        assertEquals(28, report(period = YearMonth.of(2025, 2)).rows.size)
    }
    @Test fun `December does not absorb January records`() {
        val r = report(listOf(event(31, 0, 8, YearMonth.of(2026, 12)), event(1, 0, 8, YearMonth.of(2027, 1))), period = YearMonth.of(2026, 12))
        assertEquals(1, r.days); assertEquals("08:00:00", r.rows.last().times.first())
    }
    @Test fun `legacy midnight breaks use existing proportional daily allocation`() {
        val start = LocalDate.of(2026, 9, 30).atTime(22, 0).atZone(AttendanceCalculations.manila).toInstant()
        val session = AttendanceSession("legacy", start, start.plusSeconds(4 * 3600), Duration.ofHours(1))
        val r = report(legacy = listOf(session))
        assertEquals(Duration.ofMinutes(90), r.total)
        assertEquals(AttendanceCalculations.dailyHours(listOf(session))[month.atDay(1)], r.rows.first().completed)
        assertTrue(r.rows.first().times.all { it.isEmpty() })
        assertEquals(1, r.days); assertTrue(r.notes.any { it.detail.contains("2026-09-30 22:00:00") })
    }
    @Test fun `open legacy record does not fabricate attendance on subsequent days`() {
        val r = report(legacy = listOf(AttendanceSession("open", event(3, 0, 8).occurredAt)))
        assertEquals(1, r.days); assertEquals(Duration.ZERO, r.total); assertEquals(1, r.incompleteDays)
    }
    @Test fun `skipped morning retains afternoon only hours and exception note`() {
        val action = SessionResolution("skip", month.atDay(2), OjtSession.MORNING, ResolutionKind.SKIPPED, event(2, 0, 8).occurredAt)
        val r = report(listOf(event(2, 2, 13), event(2, 3, 17)), actions = listOf(action))
        assertEquals(Duration.ofHours(4), r.total); assertEquals("", r.rows[1].times[0])
        assertTrue(r.rows[1].note.contains(ResolutionKind.SKIPPED.label))
    }
    @Test fun `overlap exclusions use full snapshot before filtering reporting month`() {
        val at = event(1, 0, 8).occurredAt
        val legacy = listOf(AttendanceSession("a", at, at.plusSeconds(3600)), AttendanceSession("b", at.minusSeconds(3600), at.plusSeconds(1)))
        assertEquals(Duration.ZERO, report(legacy = legacy).total)
    }
    @Test fun `missing student name uses explicit nonpersonal fallback`() { assertEquals("Name not configured", report(name = " \n ").student) }
    @Test fun `long student name preserves text and normalizes line breaks`() {
        val name = "Very Long Student Name ".repeat(4).trim()
        assertEquals(name, report(name = name).student)
        assertEquals("Student Name", report(name = "Student\nName").student)
    }
    @Test fun `pagination keeps dates in order and includes every row once`() {
        val r = report((1..31).map { event(it, 0, 8) })
        assertEquals(listOf(24, 7), r.tablePages().map { it.size })
        assertEquals(r.rows, r.tablePages().flatten())
        assertEquals(listOf(10, 10, 10, 1), r.notePages().map { it.size })
        assertEquals(6, r.pageCount)
    }
    @Test(expected = IllegalArgumentException::class) fun `unsupported reporting year is rejected`() { report(period = YearMonth.of(2500, 1)) }
}
