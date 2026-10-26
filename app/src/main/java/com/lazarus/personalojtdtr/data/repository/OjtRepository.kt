package com.lazarus.personalojtdtr.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.lazarus.personalojtdtr.domain.usecase.AttendanceRecovery
import com.lazarus.personalojtdtr.data.local.OjtDatabase
import com.lazarus.personalojtdtr.data.local.entity.*
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.AttendanceCalculations
import com.lazarus.personalojtdtr.domain.usecase.DailyAttendance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class OjtRepository(private val db: OjtDatabase, private val clock: Clock = Clock.systemUTC()) {
    internal val database get() = db
    private val operationMutex = Mutex()
    suspend fun <T> exclusive(block: suspend () -> T): T = operationMutex.withLock { block() }
    private val dao = db.dao()
    // A single transaction prevents mixed pre/post-restore inputs. Invalidation covers every
    // writer, including confirmed photos and REPLACE restore, without UI-specific hooks.
    suspend fun widgetInputs(): WidgetInputs = db.withTransaction {
        WidgetInputs(dao.profile()?.requiredHours ?: 0, legacySessions(), recordedEvents(), recordedResolutions())
    }
    val widgetChanges get() = db.invalidationTracker.createFlow(
        "personal_profile", "attendance_sessions", "attendance_events", "session_resolutions", "restore_marker"
    ).map { widgetInputs() }.distinctUntilChanged()
    val resolutions = dao.observeResolutions().map { rows -> rows.map { it.toModel() } }
    suspend fun recordedResolutions() = dao.resolutions().map { it.toModel() }
    suspend fun resolve(date: LocalDate, session: OjtSession, kind: ResolutionKind) = exclusive { db.withTransaction {
        val action = SessionResolution(UUID.randomUUID().toString(), date, session, kind, now())
        AttendanceRecovery.validate(action, recordedEvents(), recordedResolutions())
        dao.insertResolution(SessionResolutionEntity(action.id, date, session.code, kind.code, action.actedAt))
    } }

    val events = dao.observeEvents().map { rows -> rows.map { it.toModel() } }
    val profile = dao.observeProfile().map { it?.toModel() ?: PersonalProfile() }
    val sessions = dao.observeSessions().map { rows -> rows.map { it.toModel() } }
    val activities = dao.observeActivities().map { rows -> rows.map { DailyActivity(it.id, it.date, it.description, it.photoPaths, it.title, it.timeMinutes?.let { minute -> java.time.LocalTime.ofSecondOfDay(minute * 60L) }, it.createdAt, it.updatedAt, it.revision) } }
    private fun now(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    suspend fun saveProfile(profile: PersonalProfile) = exclusive {
        require(profile.displayName.trim().length <= 100) { "Name must be at most 100 characters." }
        require(profile.requiredHours in 0..100_000) { "Required hours must be between 0 and 100000." }
        require(profile.defaultBreak.seconds in 0..86400) { "Break must be between 0 and 1440 minutes." }
        dao.saveProfile(PersonalProfileEntity(displayName = profile.displayName.trim(), requiredHours = profile.requiredHours,
            startDate = profile.startDate, defaultBreakSeconds = profile.defaultBreak.seconds))
    }

    suspend fun recordedEvents() = dao.events().map { it.toModel() }
    suspend fun legacySessions() = dao.sessions().map { it.toModel() }

    suspend fun confirmEvent(event: AttendanceEvent) = db.withTransaction {
        DailyAttendance.validate(event, recordedEvents(), legacySessions(), recordedResolutions())
        dao.insertEvent(AttendanceEventEntity(event.id, event.date, event.type.code, event.occurredAt,
            event.capturedAt, event.originalPhotoPath, event.timestampedPhotoPath, event.occurredAt))
    }

    suspend fun timeOut(breakMinutes: Long) = exclusive { db.withTransaction {
        require(breakMinutes in 0..1440) { "Break must be between 0 and 1440 minutes." }
        val existing = dao.sessions()
        val active = existing.singleOrNull { it.timeOut == null } ?: error("No single active attendance session was found.")
        val now = now()
        require(now > active.timeIn) { "Time Out must be after Time In. Check the device clock." }
        val deduction = Duration.ofMinutes(breakMinutes)
        require(deduction <= Duration.between(active.timeIn, now)) { "Break exceeds elapsed attendance. Enter the actual break taken." }
        val closed = active.copy(timeOut = now, breakSeconds = deduction.seconds, updatedAt = now)
        AttendanceCalculations.requireNoOverlap(closed.toModel(), existing.map { it.toModel() })
        check(dao.updateSession(closed) == 1) { "Attendance was not saved." }
    } }


}

private fun PersonalProfileEntity.toModel() = PersonalProfile(displayName, requiredHours, startDate, Duration.ofSeconds(defaultBreakSeconds))
private fun AttendanceSessionEntity.toModel() = AttendanceSession(id, timeIn, timeOut, Duration.ofSeconds(breakSeconds), timeInPhotoPath, timeOutPhotoPath)

private fun AttendanceEventEntity.toModel() = AttendanceEvent(id, date, AttendanceEventType.fromCode(type), occurredAt, capturedAt, originalPhotoPath, timestampedPhotoPath)

fun SessionResolutionEntity.toModel() = SessionResolution(id, date, OjtSession.entries.single { it.code == session }, ResolutionKind.entries.single { it.code == kind }, actedAt)
