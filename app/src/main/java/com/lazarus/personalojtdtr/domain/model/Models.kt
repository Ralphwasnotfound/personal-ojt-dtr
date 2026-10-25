package com.lazarus.personalojtdtr.domain.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class PersonalProfile(
    val displayName: String = "",
    val requiredHours: Long = 0,
    val startDate: LocalDate? = null,
    val defaultBreak: Duration = Duration.ZERO,
)
data class AttendanceSession(
    val id: String,
    val timeIn: Instant,
    val timeOut: Instant? = null,
    val breakDuration: Duration = Duration.ZERO,
    val timeInPhotoPath: String? = null,
    val timeOutPhotoPath: String? = null,
)
data class DailyActivity(val id: String, val date: LocalDate, val description: String, val photoPaths: List<String> = emptyList(),
    val title: String = "Activity", val time: LocalTime? = null, val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH, val revision: Long = 0)
data class HourSummary(val completed: Duration, val remaining: Duration, val required: Duration, val progress: Float)
