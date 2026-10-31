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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.time.DayOfWeek
import com.lazarus.personalojtdtr.data.preferences.OjtSchedule
import com.lazarus.personalojtdtr.domain.model.CalendarOverrideKind
import com.lazarus.personalojtdtr.domain.model.CalendarDayOverride

class OjtRepository(private val db: OjtDatabase, private val clock: Clock = Clock.systemUTC(),
    val avatarFiles: com.lazarus.personalojtdtr.data.photos.LocalAvatarStore? = null) {
    private val avatarRevision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val avatar = combine(db.invalidationTracker.createFlow("restore_marker"), avatarRevision) { _, _ ->
        db.withTransaction { avatarFiles?.reference(db.dao().restoreMarker()?.generation)?.let { avatarFiles.resolve(it) } }
    }.distinctUntilChanged()
    suspend fun confirmAvatar(source: java.io.File) = exclusive { db.withTransaction {
        requireNotNull(avatarFiles).confirm(source, dao.restoreMarker()?.generation); avatarRevision.value++
    } }
    suspend fun removeAvatar() = exclusive { db.withTransaction {
        requireNotNull(avatarFiles).remove(dao.restoreMarker()?.generation); avatarRevision.value++
    } }
    suspend fun collectAvatar() = exclusive { avatarFiles?.collect(dao.restoreMarker()?.generation); avatarRevision.value++ }
    internal val database get() = db
    private val operationMutex = Mutex()
    suspend fun <T> exclusive(block: suspend () -> T): T = operationMutex.withLock { block() }
    private val dao = db.dao()
    // A single transaction prevents mixed pre/post-restore inputs. Invalidation covers every
    // writer, including confirmed photos and REPLACE restore, without UI-specific hooks.
    suspend fun widgetInputs(): WidgetInputs = db.withTransaction {
        val schedule = dao.scheduleSettings()?.toSchedule() ?: OjtSchedule()
        WidgetInputs(dao.profile()?.requiredHours ?: 0, legacySessions(), recordedEvents(), recordedResolutions(),
            schedule.hoursPerDay, schedule.weekdays, dao.calendarOverrides().map { it.toModel() })
    }
    suspend fun dtrReport(period: java.time.YearMonth) = db.withTransaction {
        com.lazarus.personalojtdtr.domain.usecase.DtrReports.create(period, dao.profile()?.displayName.orEmpty(),
            recordedEvents(), legacySessions(), recordedResolutions())
    }
    suspend fun journalReport(period: com.lazarus.personalojtdtr.domain.usecase.JournalPeriod) = db.withTransaction {
        com.lazarus.personalojtdtr.domain.usecase.JournalReport.create(period, dao.profile()?.displayName.orEmpty(),
            dao.journalActivities(period.start, period.end).map {
                DailyActivity(it.id, it.date, it.description, it.photoPaths, it.title,
                    it.timeMinutes?.let { minute -> java.time.LocalTime.ofSecondOfDay(minute * 60L) }, it.createdAt, it.updatedAt, it.revision)
            })
    }
    val widgetChanges get() = db.invalidationTracker.createFlow(
        "personal_profile", "attendance_sessions", "attendance_events", "session_resolutions", "restore_marker",
        "ojt_schedule_settings", "calendar_day_overrides"
    ).map { widgetInputs() }.distinctUntilChanged()
    val schedule = dao.observeScheduleSettings().map { it?.toSchedule() ?: OjtSchedule() }.distinctUntilChanged()
    val calendarOverrides = dao.observeCalendarOverrides().map { rows -> rows.map { it.toModel() } }.distinctUntilChanged()
    suspend fun saveSchedule(hoursPerDay: Int?, weekdays: Set<DayOfWeek>) = exclusive { db.withTransaction {
        require(hoursPerDay == null || hoursPerDay in 1..24) { "Expected hours must be between 1 and 24." }
        dao.saveScheduleSettings(OjtScheduleSettingsEntity(expectedHoursPerDay = hoursPerDay,
            workingWeekdaysMask = weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }, legacyPrefsMigrated = true))
    } }
    suspend fun migrateLegacySchedule(schedule: OjtSchedule) = db.withTransaction {
        val existing = dao.scheduleSettings()
        if (existing == null || !existing.legacyPrefsMigrated) {
            dao.saveScheduleSettings(OjtScheduleSettingsEntity(expectedHoursPerDay = schedule.hoursPerDay,
                workingWeekdaysMask = schedule.weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }, legacyPrefsMigrated = true))
        }
    }
    suspend fun setCalendarOverride(date: LocalDate, kind: CalendarOverrideKind, reason: String?, allowAttendanceConflict: Boolean = false, scope: WorkdayScope = WorkdayScope.WHOLE_DAY) = exclusive { db.withTransaction {
        require(date.year in 1800..2400) { "Date is outside the supported range." }
        validateWorkdayException(kind, reason)
        require(scope == WorkdayScope.WHOLE_DAY || kind.nonWorking) { "Special working days apply to the whole day." }
        val settings = dao.scheduleSettings()?.toSchedule() ?: OjtSchedule()
        if (scope == WorkdayScope.WHOLE_DAY && kind == CalendarOverrideKind.DAY_OFF && settings.weekdays.isNotEmpty() && date.dayOfWeek !in settings.weekdays && dao.calendarOverrides().none { it.date == date && it.scope == 2 }) return@withTransaction
        if (scope != WorkdayScope.WHOLE_DAY) require(recordedEvents().none { it.date == date && it.type.code / 2 == scope.code }) {
            "Attendance exists for this session. Keep its evidence and use attendance recovery instead of adding a skip."
        }
        val hasRecords = recordedEvents().any { it.date == date } || recordedResolutions().any { it.date == date } ||
            legacySessions().any { it.timeIn.atZone(AttendanceCalculations.manila).toLocalDate() == date || it.timeOut?.atZone(AttendanceCalculations.manila)?.toLocalDate() == date }
        require(!hasRecords || allowAttendanceConflict) { "Attendance already exists on this date. Confirm the schedule override to preserve those records." }
        // Keep the original skip decision timestamp so edits cannot invalidate event replay.
        val decidedAt = if (scope == WorkdayScope.WHOLE_DAY) now() else dao.calendarOverrides().firstOrNull { it.date == date && it.scope == scope.code }?.updatedAt ?: now()
        dao.saveCalendarOverride(CalendarDayOverrideEntity(date, kind.code, reason?.trim()?.takeIf { it.isNotEmpty() }, decidedAt, scope.code))
    } }
    suspend fun clearCalendarOverride(date: LocalDate, scope: WorkdayScope = WorkdayScope.WHOLE_DAY) = exclusive { db.withTransaction {
        // Cancelling a morning exception must not invalidate a saved afternoon-only sequence.
        if (scope == WorkdayScope.MORNING) require(recordedEvents().none { it.date == date && it.type.code >= 2 } ||
            recordedEvents().any { it.date == date && it.type == AttendanceEventType.MORNING_TIME_OUT } ||
            dao.resolutions().any { it.date == date && it.session == 0 }) { "This morning exception supports saved afternoon attendance and must be retained." }
        dao.deleteCalendarOverride(date, scope.code)
    } }
    val resolutions = combine(dao.observeResolutions(), dao.observeEvents(), calendarOverrides) { rows, events, overrides ->
        effectiveResolutions(events.map { it.toModel() }, rows.map { it.toModel() }, overrides)
    }
    suspend fun recordedResolutions() = effectiveResolutions(recordedEvents(), dao.resolutions().map { it.toModel() }, dao.calendarOverrides().map { it.toModel() })
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

private fun OjtScheduleSettingsEntity.toSchedule() = OjtSchedule(expectedHoursPerDay,
    DayOfWeek.entries.filter { workingWeekdaysMask and (1 shl (it.value - 1)) != 0 }.toSet())
private fun CalendarDayOverrideEntity.toModel() = CalendarDayOverride(date,
    CalendarOverrideKind.entries.single { it.code == kind }, reason, updatedAt, WorkdayScope.entries.single { it.code == scope })

private fun PersonalProfileEntity.toModel() = PersonalProfile(displayName, requiredHours, startDate, Duration.ofSeconds(defaultBreakSeconds))
private fun AttendanceSessionEntity.toModel() = AttendanceSession(id, timeIn, timeOut, Duration.ofSeconds(breakSeconds), timeInPhotoPath, timeOutPhotoPath)

private fun AttendanceEventEntity.toModel() = AttendanceEvent(id, date, AttendanceEventType.fromCode(type), occurredAt, capturedAt, originalPhotoPath, timestampedPhotoPath)

fun SessionResolutionEntity.toModel() = SessionResolution(id, date, OjtSession.entries.single { it.code == session }, ResolutionKind.entries.single { it.code == kind }, actedAt)
