package com.lazarus.personalojtdtr.data.backup

import androidx.room.withTransaction
import com.lazarus.personalojtdtr.data.local.*
import com.lazarus.personalojtdtr.data.local.entity.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RoomBackupStore(private val database: OjtDatabase, private val avatars: com.lazarus.personalojtdtr.data.photos.LocalAvatarStore? = null) : RestoreTarget {
    private val dao get() = database.dao()
    suspend fun snapshot(): Pair<BackupRecords, Map<String, String>> = database.withTransaction {
        val sources = linkedMapOf<String, String>()
        val pathIds = mutableMapOf<String, String>()
        fun photo(path: String?): String? = path?.let { pathIds.getOrPut(it) { UUID.randomUUID().toString().also { id -> sources[id] = it } } }
        val profile = dao.profile()?.let { BackupProfile(it.id, it.displayName, it.requiredHours, it.startDate?.toEpochDay(), it.defaultBreakSeconds) }
        val events = dao.events().map { BackupEvent(it.id, it.date.toEpochDay(), it.type, it.occurredAt.toEpochMilli(), it.capturedAt.toEpochMilli(), requireNotNull(photo(it.originalPhotoPath)), requireNotNull(photo(it.timestampedPhotoPath)), it.createdAt.toEpochMilli()) }
        val legacy = dao.sessions().map { BackupLegacy(it.id, it.timeIn.toEpochMilli(), it.timeOut?.toEpochMilli(), it.breakSeconds, photo(it.timeInPhotoPath), photo(it.timeOutPhotoPath), it.createdAt.toEpochMilli(), it.updatedAt.toEpochMilli()) }
        val activities = dao.activities().map { BackupActivity(it.id, it.date.toEpochDay(), it.description, it.photoPaths.map { path -> requireNotNull(photo(path)) }, it.createdAt.toEpochMilli(), it.updatedAt.toEpochMilli(), it.title, it.timeMinutes) }
        val resolutions = dao.resolutions().map { BackupResolution(it.id, it.date.toEpochDay(), it.session, it.kind, it.actedAt.toEpochMilli()) }
        val settings = dao.scheduleSettings()
        val schedule = settings?.let { BackupSchedule(it.expectedHoursPerDay, it.workingWeekdaysMask) }
            ?: BackupSchedule(null, 0)
        val calendar = dao.calendarOverrides().map { BackupCalendarOverride(it.date.toEpochDay(), it.kind, it.reason, it.updatedAt.toEpochMilli(), it.scope) }
        BackupRecords(Instant.now().toEpochMilli(), profile, events, legacy, activities, resolutions, emptyList(), schedule, calendar, avatars?.validReference(dao.restoreMarker()?.generation)?.let(::photo)) to sources
    }
    override suspend fun applied(generation: String): Boolean = dao.restoreMarker()?.generation == generation
    override suspend fun replace(records: BackupRecords, paths: Map<String, String>, generation: String) = database.withTransaction {
        val revisions = dao.activities().associate { it.id to
            com.lazarus.personalojtdtr.domain.usecase.ActivityRules.revisionAfterRestore(it.revision) }
        val sqlite = database.openHelper.writableDatabase
        // Fixed application SQL only. DDL and data replacement roll back together on any failure.
        (RecoverySchema.triggerNames + ActivitySchema.triggerNames).forEach { sqlite.execSQL("DROP TRIGGER IF EXISTS $it") }
        dao.clearEvents(); dao.clearResolutions(); dao.clearSessions(); dao.clearActivities(); dao.clearProfile(); dao.clearCalendarOverrides()
        val schedule = records.schedule ?: BackupSchedule(null, 0)
        dao.saveScheduleSettings(OjtScheduleSettingsEntity(expectedHoursPerDay = schedule.expectedHoursPerDay,
            workingWeekdaysMask = schedule.workingWeekdaysMask, legacyPrefsMigrated = true))
        records.calendarOverrides.forEach { dao.saveCalendarOverride(CalendarDayOverrideEntity(LocalDate.ofEpochDay(it.date), it.kind, it.reason, Instant.ofEpochMilli(it.updatedAt), it.scope)) }
        records.profile?.let { dao.saveProfile(PersonalProfileEntity(it.id, it.displayName, it.requiredHours, it.startDate?.let(LocalDate::ofEpochDay), it.defaultBreakSeconds)) }
        records.legacy.forEach { dao.insertSession(AttendanceSessionEntity(it.id, Instant.ofEpochMilli(it.timeIn), it.timeOut?.let(Instant::ofEpochMilli), it.breakSeconds, it.original?.let(paths::getValue), it.outPhoto?.let(paths::getValue), Instant.ofEpochMilli(it.createdAt), Instant.ofEpochMilli(it.updatedAt))) }
        records.activities.forEach { dao.insertActivity(DailyActivityEntity(it.id, LocalDate.ofEpochDay(it.date), it.description, it.photos.map(paths::getValue), Instant.ofEpochMilli(it.createdAt), Instant.ofEpochMilli(it.updatedAt), it.title, it.timeMinutes, revisions[it.id] ?: 0)) }
        records.events.forEach { dao.insertEvent(AttendanceEventEntity(it.id, LocalDate.ofEpochDay(it.date), it.type, Instant.ofEpochMilli(it.at), Instant.ofEpochMilli(it.capturedAt), paths.getValue(it.original), paths.getValue(it.stamped), Instant.ofEpochMilli(it.createdAt))) }
        records.resolutions.forEach { dao.insertResolution(SessionResolutionEntity(it.id, LocalDate.ofEpochDay(it.date), it.session, it.kind, Instant.ofEpochMilli(it.actedAt))) }
        dao.saveRestoreMarker(RestoreMarkerEntity(generation = generation, appliedAt = Instant.now()))
        OjtDatabase.installAllGuards(sqlite)
    }
    suspend fun referencedPaths(): Set<String> = database.withTransaction {
        (dao.events().flatMap { listOf(it.originalPhotoPath, it.timestampedPhotoPath) } + dao.sessions().flatMap { listOfNotNull(it.timeInPhotoPath, it.timeOutPhotoPath) } + dao.activities().flatMap { it.photoPaths } + listOfNotNull(avatars?.reference(dao.restoreMarker()?.generation))).toSet()
    }
    suspend fun recover(filesDir: java.io.File) {
        avatars?.collect(dao.restoreMarker()?.generation)
        AtomicRestore.recoverGenerations(filesDir, referencedPaths(), dao.restoreMarker()?.generation)
    }
}
