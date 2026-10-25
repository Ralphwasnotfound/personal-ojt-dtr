package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.Duration
import java.time.LocalDate

object DailyAttendance {
    fun next(date: LocalDate, events: List<AttendanceEvent>, actions: List<SessionResolution> = emptyList()): AttendanceEventType? {
        val day = events.filter { it.date == date }
        for (session in OjtSession.entries) {
            if (actions.any { it.date == date && it.session == session }) continue
            if (day.none { it.type.code == session.startCode }) return AttendanceEventType.fromCode(session.startCode)
            if (day.none { it.type.code == session.startCode + 1 }) return AttendanceEventType.fromCode(session.startCode + 1)
        }
        return null
    }

    fun validate(candidate: AttendanceEvent, events: List<AttendanceEvent>, legacy: List<AttendanceSession>, actions: List<SessionResolution> = emptyList()) {
        require(events.none { it.id == candidate.id }) { "This photo has already been confirmed." }
        require(next(candidate.date, events, actions) == candidate.type) { "Attendance changed. Only the next unrecorded event can be confirmed." }
        require(candidate.occurredAt.atZone(AttendanceCalculations.manila).toLocalDate() == candidate.date) { "The Manila date changed. Cancel and capture for the new day." }
        require(candidate.capturedAt.atZone(AttendanceCalculations.manila).toLocalDate() == candidate.date && candidate.capturedAt <= candidate.occurredAt) { "The capture time is invalid. Check the device clock and retake." }
        require(events.all { it.occurredAt < candidate.occurredAt }) { "The event must be later than previous attendance. Check the device clock." }
        require(candidate.originalPhotoPath.isNotBlank() && candidate.timestampedPhotoPath.isNotBlank() && candidate.originalPhotoPath != candidate.timestampedPhotoPath) { "Both original and timestamped photos are required." }
        require(events.none { old ->
            listOf(candidate.originalPhotoPath, candidate.timestampedPhotoPath).any {
                it == old.originalPhotoPath || it == old.timestampedPhotoPath
            }
        }) { "Each event requires its own newly captured photo." }
        require(actions.all { it.actedAt < candidate.occurredAt }) { "Attendance must be later than recovery/exception actions. Check the device clock." }
        val sessions = sessions(events, actions)
        val opening = candidate.type.code % 2 == 0
        if (opening) {
            require(sessions.none { it.timeOut == null }) { "An earlier attendance session is still open. It has not been silently closed." }
            require(legacy.none { it.timeOut == null || it.timeOut > candidate.occurredAt }) { "A legacy P1 session is open or conflicts with the current clock." }
        } else {
            val startType = AttendanceEventType.fromCode(candidate.type.code - 1)
            val start = events.singleOrNull { it.date == candidate.date && it.type == startType }
                ?: error("The corresponding Time In is missing.")
            val closed = AttendanceSession("event:${start.id}", start.occurredAt, candidate.occurredAt)
            AttendanceCalculations.requireNoOverlap(closed, legacy + sessions.filter { it.id != closed.id })
        }
    }

    // No lunch deduction: the two pairs represent actual working intervals.
    fun sessions(events: List<AttendanceEvent>, actions: List<SessionResolution> = emptyList()): List<AttendanceSession> = events.groupBy { it.date }.values.flatMap { day ->
        val ordered = day.sortedBy { it.type.code }
        val date = day.first().date
        val resolved = actions.filter { it.date == date }
        if (day.map { it.type }.distinct().size != day.size || ordered.zipWithNext().any { (a, b) -> b.occurredAt <= a.occurredAt }) return@flatMap emptyList()
        if (day.any { it.type.code >= 2 } && day.none { it.type.code == 1 } && resolved.none { it.session == OjtSession.MORNING }) return@flatMap emptyList()
        if (OjtSession.entries.any { session -> day.any { it.type.code == session.startCode + 1 } && day.none { it.type.code == session.startCode } }) return@flatMap emptyList()
        OjtSession.entries.mapNotNull { session ->
            if (resolved.any { it.session == session }) return@mapNotNull null
            val start = day.singleOrNull { it.type.code == session.startCode } ?: return@mapNotNull null
            val end = day.singleOrNull { it.type.code == session.startCode + 1 }
            AttendanceSession("event:${start.id}", start.occurredAt, end?.occurredAt, Duration.ZERO, start.originalPhotoPath, end?.originalPhotoPath)
        }
    }

    fun daily(date: LocalDate, events: List<AttendanceEvent>, actions: List<SessionResolution> = emptyList()): Pair<Duration, Duration> {
        val pairs = sessions(events.filter { it.date == date }, actions)
        val morningId = events.firstOrNull { it.date == date && it.type.code == 0 }?.id
        val morning = pairs.firstOrNull { it.id == "event:$morningId" }?.let(AttendanceCalculations::duration) ?: Duration.ZERO
        val afternoon = pairs.firstOrNull { it.id != "event:$morningId" }?.let(AttendanceCalculations::duration) ?: Duration.ZERO
        return morning to afternoon
    }
}
