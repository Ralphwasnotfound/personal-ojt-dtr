package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.AttendanceSession
import com.lazarus.personalojtdtr.domain.model.HourSummary
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

object AttendanceCalculations {
    val manila: ZoneId = ZoneId.of("Asia/Manila")

    fun duration(session: AttendanceSession): Duration {
        val end = session.timeOut ?: return Duration.ZERO
        if (!end.isAfter(session.timeIn) || session.breakDuration.isNegative) return Duration.ZERO
        val elapsed = Duration.between(session.timeIn, end)
        return if (session.breakDuration > elapsed) Duration.ZERO else elapsed.minus(session.breakDuration)
    }

    // Half-open intervals permit back-to-back sessions. Open intervals extend indefinitely.
    fun overlaps(a: AttendanceSession, b: AttendanceSession): Boolean =
        (a.timeOut == null || b.timeIn < a.timeOut) &&
            (b.timeOut == null || a.timeIn < b.timeOut)

    fun requireNoOverlap(candidate: AttendanceSession, existing: List<AttendanceSession>) {
        require(candidate.timeOut == null || candidate.timeOut.isAfter(candidate.timeIn)) { "Time Out must be after Time In." }
        require(!candidate.breakDuration.isNegative) { "Break cannot be negative." }
        candidate.timeOut?.let { end ->
            require(candidate.breakDuration <= Duration.between(candidate.timeIn, end)) { "Break exceeds elapsed attendance." }
        }
        require(existing.none { it.id != candidate.id && overlaps(candidate, it) }) { "Attendance overlaps an existing session." }
    }

    private fun credited(sessions: List<AttendanceSession>): List<AttendanceSession> = sessions.filter { s ->
        duration(s) > Duration.ZERO && sessions.none { other -> other.id != s.id && overlaps(s, other) }
    }

    fun summary(sessions: List<AttendanceSession>, requiredHours: Long): HourSummary {
        require(requiredHours in 0..100_000) { "Required hours must be between 0 and 100000." }
        val required = Duration.ofHours(requiredHours)
        val completed = credited(sessions).fold(Duration.ZERO) { total, s -> total.plus(duration(s)) }
        val remaining = if (completed >= required) Duration.ZERO else required.minus(completed)
        val progress = if (required.isZero) 0f else
            (completed.toMillis().toDouble() / required.toMillis()).coerceIn(0.0, 1.0).toFloat()
        return HourSummary(completed, remaining, required, progress)
    }

    // Breaks have no timestamp yet: allocate the net duration proportionally across midnight.
    // Integer nanoseconds keep daily totals exact; the final day receives the rounding remainder.
    fun dailyHours(sessions: List<AttendanceSession>): Map<LocalDate, Duration> {
        val result = sortedMapOf<LocalDate, Duration>()
        for (session in credited(sessions)) {
            val end = requireNotNull(session.timeOut)
            val totalNanos = Duration.between(session.timeIn, end).toNanos()
            val netNanos = duration(session).toNanos()
            var cursor = session.timeIn
            var allocated = 0L
            while (cursor < end) {
                val day = cursor.atZone(manila).toLocalDate()
                val boundary = minOf(day.plusDays(1).atStartOfDay(manila).toInstant(), end)
                val nanos = if (boundary == end) netNanos - allocated else
                    java.math.BigInteger.valueOf(netNanos).multiply(java.math.BigInteger.valueOf(Duration.between(cursor, boundary).toNanos()))
                        .divide(java.math.BigInteger.valueOf(totalNanos)).toLong()
                result[day] = (result[day] ?: Duration.ZERO).plusNanos(nanos)
                allocated += nanos
                cursor = boundary
            }
        }
        return result
    }
}
