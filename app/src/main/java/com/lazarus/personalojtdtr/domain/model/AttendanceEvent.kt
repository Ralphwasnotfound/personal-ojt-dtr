package com.lazarus.personalojtdtr.domain.model

import java.time.Instant
import java.time.LocalDate

enum class AttendanceEventType(val code: Int, val label: String) {
    MORNING_TIME_IN(0, "Morning Time In"),
    MORNING_TIME_OUT(1, "Morning Time Out"),
    AFTERNOON_TIME_IN(2, "Afternoon Time In"),
    AFTERNOON_TIME_OUT(3, "Afternoon Time Out");
    companion object { fun fromCode(code: Int) = entries.single { it.code == code } }
}

data class AttendanceEvent(
    val id: String,
    val date: LocalDate,
    val type: AttendanceEventType,
    val occurredAt: Instant,
    val capturedAt: Instant,
    val originalPhotoPath: String,
    val timestampedPhotoPath: String,
)
