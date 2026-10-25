package com.lazarus.personalojtdtr.util

import com.lazarus.personalojtdtr.domain.model.AttendanceEvent
import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import java.time.format.DateTimeFormatter
import java.util.Locale

object AttendanceTimestamp {
    private val date = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH).withZone(AttendanceCalculations.manila)
    private val time = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH).withZone(AttendanceCalculations.manila)
    fun lines(event: AttendanceEvent): List<String> = listOf(event.type.label.uppercase(Locale.ENGLISH),
        date.format(event.occurredAt), "${time.format(event.occurredAt)} | PH Time (UTC+08:00)")
}
