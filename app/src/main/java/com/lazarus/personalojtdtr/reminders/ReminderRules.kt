package com.lazarus.personalojtdtr.reminders

import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import java.time.*

data class ReminderSettings(
    val enabled: Boolean = false, val morningEnabled: Boolean = true, val afternoonEnabled: Boolean = true,
    val morningStart: Int = 480, val afternoonStart: Int = 780,
    val secondEnabled: Boolean = true, val secondDelay: Int = 30,
    val morningReady: Boolean = true, val afternoonReady: Boolean = true,
    val morningLead: Int = 10, val afternoonLead: Int = 10,
) {
    fun valid() = morningStart in 30..1409 && afternoonStart in 30..1409 && secondDelay in 5..120 &&
        morningLead in listOf(5, 10, 15, 30) && afternoonLead in listOf(5, 10, 15, 30) &&
        morningStart + secondDelay < 1440 && afternoonStart + secondDelay < 1440
    fun start(session: OjtSession) = if (session == OjtSession.MORNING) morningStart else afternoonStart
}
enum class ReminderOccurrence { READY, FIRST, SECOND, SNOOZE }
object ReminderRules {
    val zone: ZoneId = ZoneId.of("Asia/Manila")
    fun target(date: LocalDate, session: OjtSession, occurrence: ReminderOccurrence, settings: ReminderSettings): Instant {
        val minutes = settings.start(session) + when (occurrence) {
            ReminderOccurrence.READY -> -(if (session == OjtSession.MORNING) settings.morningLead else settings.afternoonLead)
            ReminderOccurrence.SECOND -> settings.secondDelay
            else -> 0
        }
        return date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant()
    }
    fun eligible(inputs: WidgetInputs, date: LocalDate, session: OjtSession): Boolean {
        if (!isWorkingDate(date, inputs.workingWeekdays, inputs.calendarOverrides)) return false
        // Legacy intervals cannot safely be assigned to a particular four-event session.
        if (inputs.legacy.any { it.timeIn.atZone(zone).toLocalDate() == date || it.timeOut?.atZone(zone)?.toLocalDate() == date }) return false
        val actions = effectiveResolutions(inputs.events, inputs.resolutions, inputs.calendarOverrides)
        if (actions.any { it.date == date && it.session == session }) return false
        if (inputs.events.any { it.date == date && it.type.code == session.startCode }) return false
        val next = WidgetSnapshot.safeCameraAction(date, inputs) ?: return false
        // An unstarted morning needs explicit selection, not recovery of an open record.
        // Afternoon reminders may guide that choice; their Record action still checks the exact event.
        return next == AttendanceEventType.fromCode(session.startCode) ||
            (session == OjtSession.AFTERNOON && next == AttendanceEventType.MORNING_TIME_IN)
    }
    fun deliverable(settings: ReminderSettings, inputs: WidgetInputs, date: LocalDate, session: OjtSession,
        occurrence: ReminderOccurrence, now: Instant, permission: Boolean): Boolean {
        if (!permission || !settings.enabled || !settings.valid() || now.atZone(zone).toLocalDate() != date) return false
        if (!(if (session == OjtSession.MORNING) settings.morningEnabled else settings.afternoonEnabled)) return false
        if (occurrence == ReminderOccurrence.READY && !(if (session == OjtSession.MORNING) settings.morningReady else settings.afternoonReady)) return false
        if (occurrence == ReminderOccurrence.SECOND && !settings.secondEnabled) return false
        val start = target(date, session, ReminderOccurrence.FIRST, settings)
        if (occurrence == ReminderOccurrence.FIRST && settings.secondEnabled && now >= target(date, session, ReminderOccurrence.SECOND, settings)) return false
        val target = target(date, session, occurrence, settings)
        if (occurrence == ReminderOccurrence.READY && (now < target || now >= start)) return false
        if (occurrence != ReminderOccurrence.READY && (now < target || now > start.plusSeconds(90 * 60))) return false
        return eligible(inputs, date, session)
    }
    fun key(date: LocalDate, session: OjtSession, occurrence: ReminderOccurrence) = "$date:${session.code}:${occurrence.name}"
}
