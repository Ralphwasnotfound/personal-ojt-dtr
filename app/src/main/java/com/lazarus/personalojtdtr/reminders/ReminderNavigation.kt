package com.lazarus.personalojtdtr.reminders

import com.lazarus.personalojtdtr.domain.model.OjtSession
import java.time.LocalDate

data class ReminderLink(val date: LocalDate, val session: OjtSession, val skip: Boolean)
object ReminderNavigation {
    fun parse(action: String?, date: String?, sessionCode: Int): ReminderLink? {
        val session = OjtSession.entries.firstOrNull { it.code == sessionCode } ?: return null
        val day = runCatching { LocalDate.parse(date) }.getOrNull()?.takeIf { it.year in 1800..2400 } ?: return null
        return when (action) {
            "com.lazarus.personalojtdtr.reminder.RECORD.${session.name}" -> ReminderLink(day, session, false)
            "com.lazarus.personalojtdtr.reminder.SKIP.${session.name}" -> ReminderLink(day, session, true)
            else -> null
        }
    }
}
