package com.lazarus.personalojtdtr.data.local.dao

import androidx.room.*
import com.lazarus.personalojtdtr.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OjtDao {
    @Query("SELECT * FROM daily_activities WHERE date >= :start AND date <= :end ORDER BY date ASC, id ASC")
    suspend fun journalActivities(start: java.time.LocalDate, end: java.time.LocalDate): List<DailyActivityEntity>

    @Query("SELECT * FROM session_resolutions ORDER BY actedAt ASC") fun observeResolutions(): Flow<List<SessionResolutionEntity>>
    @Query("SELECT * FROM session_resolutions ORDER BY actedAt ASC") suspend fun resolutions(): List<SessionResolutionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertResolution(value: SessionResolutionEntity)
    @Query("SELECT * FROM restore_marker WHERE id=1") suspend fun restoreMarker(): RestoreMarkerEntity?
    @Upsert suspend fun saveRestoreMarker(value: RestoreMarkerEntity)
    @Query("SELECT * FROM daily_activities ORDER BY date DESC, coalesce(timeMinutes, -1) DESC, createdAt DESC, id ASC") suspend fun activities(): List<DailyActivityEntity>
    // Called only inside explicit, validated REPLACE restore transactions.
    @Query("DELETE FROM attendance_events") suspend fun clearEvents()
    @Query("DELETE FROM session_resolutions") suspend fun clearResolutions()
    @Query("DELETE FROM attendance_sessions") suspend fun clearSessions()
    @Query("DELETE FROM daily_activities") suspend fun clearActivities()
    @Query("DELETE FROM personal_profile") suspend fun clearProfile()

    @Query("SELECT * FROM attendance_events ORDER BY occurredAt ASC")
    fun observeEvents(): Flow<List<AttendanceEventEntity>>
    @Query("SELECT * FROM attendance_events ORDER BY occurredAt ASC")
    suspend fun events(): List<AttendanceEventEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: AttendanceEventEntity)

    @Query("SELECT * FROM personal_profile WHERE id = 1")
    fun observeProfile(): Flow<PersonalProfileEntity?>
    @Query("SELECT * FROM personal_profile WHERE id = 1")
    suspend fun profile(): PersonalProfileEntity?
    @Upsert suspend fun saveProfile(profile: PersonalProfileEntity)
    @Query("SELECT * FROM attendance_sessions ORDER BY timeIn DESC")
    fun observeSessions(): Flow<List<AttendanceSessionEntity>>
    @Query("SELECT * FROM attendance_sessions ORDER BY timeIn DESC")
    suspend fun sessions(): List<AttendanceSessionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: AttendanceSessionEntity)
    @Update suspend fun updateSession(session: AttendanceSessionEntity): Int
    @Query("SELECT * FROM daily_activities ORDER BY date DESC, coalesce(timeMinutes, -1) DESC, createdAt DESC, id ASC")
    fun observeActivities(): Flow<List<DailyActivityEntity>>
    @Query("SELECT * FROM daily_activities WHERE id = :id")
    suspend fun activity(id: String): DailyActivityEntity?
    @Query("DELETE FROM daily_activities WHERE id = :id AND revision = :revision")
    suspend fun deleteActivity(id: String, revision: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActivity(activity: DailyActivityEntity)
    @Update suspend fun updateActivity(activity: DailyActivityEntity): Int
}
