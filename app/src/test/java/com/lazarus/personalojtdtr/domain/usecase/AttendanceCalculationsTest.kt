package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.AttendanceSession
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AttendanceCalculationsTest {
    private val start = Instant.parse("2026-10-24T00:00:00Z")
    private fun session(id: String = "a", hours: Long = 8, breakMinutes: Long = 60) =
        AttendanceSession(id, start, start.plus(Duration.ofHours(hours)), Duration.ofMinutes(breakMinutes))
    private val calc = AttendanceCalculations

    @Test fun `closed session deducts actual break`() { assertEquals(Duration.ofHours(7), calc.duration(session())) }
    @Test fun `zero break retains elapsed duration`() { assertEquals(Duration.ofHours(8), calc.duration(session(breakMinutes = 0))) }
    @Test fun `open session contributes zero`() {
        val open = session().copy(timeOut = null)
        assertEquals(Duration.ZERO, calc.duration(open))
        assertEquals(Duration.ZERO, calc.summary(listOf(open), 100).completed)
    }
    @Test fun `reversed time range contributes zero`() { assertEquals(Duration.ZERO, calc.duration(session().copy(timeOut = start.minusSeconds(1)))) }
    @Test fun `equal time range contributes zero`() { assertEquals(Duration.ZERO, calc.duration(session().copy(timeOut = start))) }
    @Test fun `negative break contributes zero`() { assertEquals(Duration.ZERO, calc.duration(session(breakMinutes = -1))) }
    @Test fun `excessive break does not award hours`() { assertEquals(Duration.ZERO, calc.duration(session(breakMinutes = 481))) }
    @Test fun `break equal to elapsed duration contributes zero`() { assertEquals(Duration.ZERO, calc.duration(session(breakMinutes = 480))) }
    @Test fun `remaining hours deduct completed only`() {
        val summary = calc.summary(listOf(session()), 100)
        assertEquals(Duration.ofHours(7), summary.completed)
        assertEquals(Duration.ofHours(93), summary.remaining)
        assertEquals(0.07f, summary.progress, 0.00001f)
    }
    @Test fun `remaining hours clamp at zero and progress at one`() {
        val summary = calc.summary(listOf(session()), 5)
        assertEquals(Duration.ZERO, summary.remaining)
        assertEquals(1f, summary.progress, 0f)
    }
    @Test fun `unset goal has zero progress`() {
        val summary = calc.summary(listOf(session()), 0)
        assertEquals(Duration.ZERO, summary.remaining)
        assertEquals(0f, summary.progress, 0f)
    }
    @Test fun `empty records have no completed hours`() { assertEquals(Duration.ZERO, calc.summary(emptyList(), 100).completed) }
    @Test fun `millisecond precision is retained`() {
        assertEquals(Duration.ofMillis(1500), calc.duration(session(breakMinutes = 0).copy(timeOut = start.plusMillis(1500))))
    }
    @Test fun `closed intervals overlap`() {
        val a = session()
        val b = session("b").copy(timeIn = start.plusSeconds(1))
        assertTrue(calc.overlaps(a, b))
        assertTrue(calc.overlaps(b, a))
    }
    @Test fun `adjacent sessions are permitted`() {
        val a = session()
        val b = session("b").copy(timeIn = requireNotNull(a.timeOut), timeOut = start.plus(Duration.ofHours(16)))
        assertFalse(calc.overlaps(a, b))
        calc.requireNoOverlap(b, listOf(a))
        assertEquals(Duration.ofHours(14), calc.summary(listOf(a, b), 100).completed)
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate open time in is rejected`() {
        calc.requireNoOverlap(session("b").copy(timeOut = null, timeIn = start.plusSeconds(30)), listOf(session().copy(timeOut = null)))
    }
    @Test(expected = IllegalArgumentException::class) fun `closed overlap is rejected`() { calc.requireNoOverlap(session("b"), listOf(session())) }
    @Test(expected = IllegalArgumentException::class) fun `reversed range is rejected on write`() { calc.requireNoOverlap(session().copy(timeOut = start.minusSeconds(1)), emptyList()) }
    @Test fun `existing record can be closed without conflicting with itself`() { calc.requireNoOverlap(session(), listOf(session().copy(timeOut = null))) }
    @Test fun `conflicting imported sessions do not double award hours`() {
        assertEquals(Duration.ZERO, calc.summary(listOf(session(), session("b")), 100).completed)
    }
    @Test fun `daily totals use Manila calendar date`() {
        val day = LocalDate.parse("2026-10-24")
        assertEquals(mapOf(day to Duration.ofHours(7)), calc.dailyHours(listOf(session())))
    }
    @Test fun `overnight sessions split duration and breaks at midnight`() {
        // 22:00 through 02:00 Manila, one hour break: 90 minutes credited per day.
        val s = session().copy(timeIn = Instant.parse("2026-10-24T14:00:00Z"), timeOut = Instant.parse("2026-10-24T18:00:00Z"))
        val daily = calc.dailyHours(listOf(s))
        assertEquals(Duration.ofMinutes(90), daily[LocalDate.parse("2026-10-24")])
        assertEquals(Duration.ofMinutes(90), daily[LocalDate.parse("2026-10-25")])
        assertEquals(calc.duration(s), daily.values.fold(Duration.ZERO, Duration::plus))
    }
    @Test fun `open and invalid sessions never enter daily totals`() {
        assertTrue(calc.dailyHours(listOf(session().copy(timeOut = null))).isEmpty())
        assertTrue(calc.dailyHours(listOf(session(breakMinutes = 900))).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun `negative goal is rejected`() { calc.summary(emptyList(), -1) }
    @Test(expected = IllegalArgumentException::class) fun `excessive break is rejected on write`() { calc.requireNoOverlap(session(breakMinutes = 481), emptyList()) }
    @Test fun `midnight endpoint does not create an extra daily entry`() {
        val s = session(breakMinutes = 0).copy(timeIn = Instant.parse("2026-10-24T14:00:00Z"), timeOut = Instant.parse("2026-10-24T16:00:00Z"))
        assertEquals(mapOf(LocalDate.parse("2026-10-24") to Duration.ofHours(2)), calc.dailyHours(listOf(s)))
    }
    @Test fun `fractional overnight split conserves credited time`() {
        val s = session(breakMinutes = 1).copy(timeIn = Instant.parse("2026-10-24T15:59:30.123Z"), timeOut = Instant.parse("2026-10-24T16:02:00Z"))
        assertEquals(calc.duration(s), calc.dailyHours(listOf(s)).values.fold(Duration.ZERO, Duration::plus))
    }
}
