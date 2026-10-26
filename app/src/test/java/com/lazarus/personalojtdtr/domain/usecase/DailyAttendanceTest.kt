package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.util.AttendanceTimestamp
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DailyAttendanceTest {
    private val date = LocalDate.of(2026, 10, 24)
    private fun event(code: Int, time: String, day: LocalDate = date): AttendanceEvent {
        val instant = day.atTime(LocalTime.parse(time)).atZone(AttendanceCalculations.manila).toInstant()
        return AttendanceEvent("$day-$code", day, AttendanceEventType.fromCode(code), instant, instant.minusSeconds(1), "$day/$code/original.jpg", "$day/$code/timestamped.jpg")
    }
    private val complete get() = listOf(event(0, "07:45"), event(1, "12:00"), event(2, "12:45"), event(3, "17:10"))

    @Test fun `four events must be confirmed in order`() {
        val recorded = mutableListOf<AttendanceEvent>()
        complete.forEach { e ->
            assertEquals(e.type, DailyAttendance.next(date, recorded))
            DailyAttendance.validate(e, recorded, emptyList())
            recorded += e
        }
        assertNull(DailyAttendance.next(date, recorded))
    }
    @Test(expected = IllegalArgumentException::class) fun `afternoon cannot precede morning completion`() {
        DailyAttendance.validate(event(2, "11:00"), listOf(event(0, "07:00")), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `morning time out requires time in`() {
        DailyAttendance.validate(event(1, "12:00"), emptyList(), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate event is rejected even with another photo id`() {
        DailyAttendance.validate(event(0, "07:46").copy(id = "different-photo"), listOf(event(0, "07:45")), emptyList())
    }
    @Test fun `morning time out may cross noon`() {
        DailyAttendance.validate(event(1, "12:30"), listOf(event(0, "07:00")), emptyList())
        assertEquals(Duration.ofMinutes(330), DailyAttendance.daily(date, listOf(event(0, "07:00"), event(1, "12:30"))).first)
    }
    @Test fun `afternoon time in may be before noon`() {
        DailyAttendance.validate(event(2, "11:00"), listOf(event(0, "07:00"), event(1, "10:30")), emptyList())
    }
    @Test fun `daily total is two sessions without lunch deduction`() {
        val (morning, afternoon) = DailyAttendance.daily(date, complete)
        assertEquals(Duration.ofMinutes(255), morning)
        assertEquals(Duration.ofMinutes(265), afternoon)
        assertEquals(Duration.ofMinutes(520), morning + afternoon)
        assertEquals(Duration.ofMinutes(520), AttendanceCalculations.summary(DailyAttendance.sessions(complete), 500).completed)
    }
    @Test fun `incomplete morning contributes zero`() {
        assertEquals(Duration.ZERO to Duration.ZERO, DailyAttendance.daily(date, complete.take(1)))
    }
    @Test fun `incomplete afternoon retains only closed morning hours`() {
        val (morning, afternoon) = DailyAttendance.daily(date, complete.take(3))
        assertEquals(Duration.ofMinutes(255), morning)
        assertEquals(Duration.ZERO, afternoon)
    }
    @Test(expected = IllegalArgumentException::class) fun `time out before time in is rejected`() {
        DailyAttendance.validate(event(1, "07:44"), listOf(event(0, "07:45")), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `equal event timestamps are rejected`() {
        DailyAttendance.validate(event(1, "07:45"), listOf(event(0, "07:45")), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `lunch intervals cannot overlap`() {
        DailyAttendance.validate(event(2, "11:59"), complete.take(2), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `previous open day prevents a new time in`() {
        DailyAttendance.validate(event(0, "07:00", date.plusDays(1)), complete.take(1), emptyList())
    }
    @Test fun `prior day with closed morning permits new day without inventing afternoon`() {
        DailyAttendance.validate(event(0, "07:00", date.plusDays(1)), complete.take(2), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `open legacy record blocks new workflow`() {
        val legacy = AttendanceSession("legacy", date.minusDays(1).atStartOfDay(AttendanceCalculations.manila).toInstant())
        DailyAttendance.validate(complete.first(), emptyList(), listOf(legacy))
    }
    @Test(expected = IllegalArgumentException::class) fun `wrong local date is rejected`() {
        DailyAttendance.validate(complete.first().copy(date = date.minusDays(1)), emptyList(), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `clock moved behind capture time is rejected`() {
        val first = complete.first()
        DailyAttendance.validate(first.copy(capturedAt = first.occurredAt.plusSeconds(1)), emptyList(), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `replayed confirmation id cannot become another event`() {
        DailyAttendance.validate(complete[1].copy(id = complete[0].id), complete.take(1), emptyList())
    }
    @Test(expected = IllegalArgumentException::class) fun `photo reuse is rejected`() {
        DailyAttendance.validate(complete[1].copy(originalPhotoPath = complete[0].originalPhotoPath), complete.take(1), emptyList())
    }
    @Test fun `metadata stays associated with the correct session and event`() {
        val sessions = DailyAttendance.sessions(complete)
        assertEquals(complete[0].originalPhotoPath, sessions[0].timeInPhotoPath)
        assertEquals(complete[1].originalPhotoPath, sessions[0].timeOutPhotoPath)
        assertEquals(complete[2].originalPhotoPath, sessions[1].timeInPhotoPath)
        assertEquals(complete[3].originalPhotoPath, sessions[1].timeOutPhotoPath)
        assertEquals(Duration.ZERO, sessions[1].breakDuration)
    }
    @Test fun `overlay uses actual event instant and Philippine timezone`() {
        val e = event(0, "07:45:09")
        assertEquals(listOf("MORNING TIME IN", "October 24, 2026", "7:45:09 AM | PH Time (UTC+08:00)"), AttendanceTimestamp.lines(e))
        assertEquals(Instant.parse("2026-10-23T23:45:09Z"), e.occurredAt)
    }
    @Test fun `corrupted overlapping or out of order events never award hours`() {
        val invalid = complete.toMutableList().apply { this[2] = event(2, "11:30") }
        assertEquals(Duration.ZERO to Duration.ZERO, DailyAttendance.daily(date, invalid))
        assertEquals(Duration.ZERO, AttendanceCalculations.summary(DailyAttendance.sessions(invalid), 500).completed)
    }
    @Test fun `orphan afternoon events never award hours`() {
        assertEquals(Duration.ZERO to Duration.ZERO, DailyAttendance.daily(date, complete.drop(2)))
    }
    @Test fun `review delay preserves separate capture time and confirmation evidence time`() {
        val confirmed = event(0, "08:02:30").copy(capturedAt = date.atTime(8, 0).atZone(AttendanceCalculations.manila).toInstant())
        DailyAttendance.validate(confirmed, emptyList(), emptyList())
        assertEquals("8:02:30 AM | PH Time (UTC+08:00)", AttendanceTimestamp.lines(confirmed)[2])
        assertEquals(120 + 30, Duration.between(confirmed.capturedAt, confirmed.occurredAt).seconds.toInt())
    }
    @Test fun `overlay local day changes independently of UTC day`() {
        assertEquals("October 24, 2026", AttendanceTimestamp.lines(event(0, "00:01"))[1])
    }
}
