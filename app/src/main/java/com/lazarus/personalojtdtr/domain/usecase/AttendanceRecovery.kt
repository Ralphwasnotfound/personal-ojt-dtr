package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.LocalDate

object AttendanceRecovery {
    fun unfinishedBefore(today: LocalDate, events: List<AttendanceEvent>, actions: List<SessionResolution>): List<AttendanceEvent> =
        events.filter { start -> start.type.code % 2 == 0 && start.date < today &&
            events.none { it.date == start.date && it.type.code == start.type.code + 1 } &&
            actions.none { it.date == start.date && it.session.startCode == start.type.code } }

    fun validate(action: SessionResolution, events: List<AttendanceEvent>, actions: List<SessionResolution>) {
        require(actions.none { it.id == action.id || (it.date == action.date && it.session == action.session) }) { "This session has already been resolved." }
        require(events.all { it.occurredAt < action.actedAt } && actions.all { it.actedAt < action.actedAt }) { "The action must be later than recorded attendance. Check the device clock." }
        val day = action.actedAt.atZone(AttendanceCalculations.manila).toLocalDate()
        val start = events.singleOrNull { it.date == action.date && it.type.code == action.session.startCode }
        val end = events.singleOrNull { it.date == action.date && it.type.code == action.session.startCode + 1 }
        when (action.kind) {
            ResolutionKind.INCOMPLETE -> require(action.date < day && start != null && end == null) { "Only an unfinished previous-day session can be marked incomplete." }
            ResolutionKind.SKIPPED -> {
                require(action.date == day && start == null && end == null) { "Skip only an unstarted session on the current Manila day." }
                if (action.session == OjtSession.AFTERNOON) require(
                    events.any { it.date == action.date && it.type == AttendanceEventType.MORNING_TIME_OUT } ||
                        actions.any { it.date == action.date && it.session == OjtSession.MORNING }
                ) { "Finish or explicitly skip the morning session first." }
            }
        }
    }
}
