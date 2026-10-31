package com.lazarus.personalojtdtr.domain.model

import java.time.Instant
import java.time.LocalDate

enum class CalendarOverrideKind(val code: Int, val label: String) {
    DAY_OFF(0, "Day Off / Rest Day"), WORKING_DAY(1, "Special Working Day"),
    ABSENT(2, "Absent / Personal Reason"), SICK_LEAVE(3, "Sick Leave"),
    COMPANY_CLOSED(4, "Company Closed / Holiday"), OTHER(5, "Other (specify)");
    val nonWorking: Boolean get() = this != WORKING_DAY
}
enum class WorkdayScope(val code: Int, val label: String) { MORNING(0, "Morning only"), AFTERNOON(1, "Afternoon only"), WHOLE_DAY(2, "Whole day") }

data class CalendarDayOverride(val date: LocalDate, val kind: CalendarOverrideKind, val reason: String? = null, val updatedAt: Instant = Instant.EPOCH, val scope: WorkdayScope = WorkdayScope.WHOLE_DAY)

enum class OjtCalendarDayStatus { SCHEDULED_WORKDAY, REGULAR_DAY_OFF, SPECIAL_DAY_OFF, SPECIAL_WORKING_DAY, ATTENDANCE_RECORDED, NO_ATTENDANCE_RECORDED, ABSENT, SICK_LEAVE, COMPANY_CLOSED, OTHER_EXCEPTION }

fun validateWorkdayException(kind: CalendarOverrideKind, notes: String?) {
    require(notes == null || notes.length <= 200) { "Notes must be 200 characters or fewer." }
    require(kind != CalendarOverrideKind.OTHER || !notes.isNullOrBlank()) { "Please specify a reason for Other." }
}

/** Calendar skips are reversible and never replace saved events or permanent recovery actions. */
fun effectiveResolutions(events: List<AttendanceEvent>, actions: List<SessionResolution>, overrides: List<CalendarDayOverride>): List<SessionResolution> {
    val derived = overrides.filter { it.scope != WorkdayScope.WHOLE_DAY && it.kind.nonWorking }.mapNotNull { entry ->
        val session = OjtSession.entries[entry.scope.code]
        if (events.any { it.date == entry.date && it.type.code / 2 == session.code } || actions.any { it.date == entry.date && it.session == session }) null
        else SessionResolution("calendar:${entry.date}:${entry.scope.code}", entry.date, session, ResolutionKind.SKIPPED, entry.updatedAt)
    }
    return actions + derived
}
fun List<CalendarDayOverride>.wholeDay() = filter { it.scope == WorkdayScope.WHOLE_DAY }
