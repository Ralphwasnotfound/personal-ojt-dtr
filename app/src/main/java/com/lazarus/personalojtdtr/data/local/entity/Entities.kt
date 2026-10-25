package com.lazarus.personalojtdtr.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

// This database represents one person. Records do not need a profile foreign key:
// creating/editing the singleton profile must never orphan or cascade-delete attendance.
@Entity(tableName = "personal_profile")
data class PersonalProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "",
    val requiredHours: Long = 0,
    val startDate: LocalDate? = null,
    val defaultBreakSeconds: Long = 0,
)
@Entity(tableName = "attendance_sessions", indices = [Index("timeIn"), Index("timeOut")])
data class AttendanceSessionEntity(
    @PrimaryKey val id: String,
    val timeIn: Instant,
    val timeOut: Instant? = null,
    val breakSeconds: Long = 0,
    val timeInPhotoPath: String? = null,
    val timeOutPhotoPath: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
@Entity(tableName = "daily_activities", indices = [Index("date")])
data class DailyActivityEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val description: String,
    val photoPaths: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    @ColumnInfo(defaultValue = "'Activity'") val title: String = "Activity",
    val timeMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val revision: Long = 0,
)

@Entity(tableName = "attendance_events", indices = [
    Index(value = ["date", "type"], unique = true),
    Index(value = ["originalPhotoPath"], unique = true),
    Index(value = ["timestampedPhotoPath"], unique = true),
    Index("occurredAt"),
])
data class AttendanceEventEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val type: Int,
    val occurredAt: Instant,
    val capturedAt: Instant,
    val originalPhotoPath: String,
    val timestampedPhotoPath: String,
    val createdAt: Instant,
)

@Entity(tableName = "session_resolutions", indices = [Index(value = ["date", "session"], unique = true)])
data class SessionResolutionEntity(@PrimaryKey val id: String, val date: LocalDate, val session: Int, val kind: Int, val actedAt: Instant)

@Entity(tableName = "restore_marker")
data class RestoreMarkerEntity(@PrimaryKey val id: Int = 1, val generation: String, val appliedAt: Instant)
