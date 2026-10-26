package com.lazarus.personalojtdtr.data.photos

import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CaptureDraft(val id: String, val date: LocalDate, val type: AttendanceEventType, val file: File, val capturedAt: Instant? = null)

class AttendanceRecorder(
    private val repository: OjtRepository,
    val photos: LocalAttendancePhotos,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()
    private var initialized = false
    private fun now() = clock.instant().truncatedTo(ChronoUnit.MILLIS)
    private suspend fun initialize() {
        if (!initialized) {
            val paths = com.lazarus.personalojtdtr.data.backup.RoomBackupStore(repository.database).referencedPaths()
            photos.recover(paths)
            initialized = true
        }
    }
    suspend fun recover() = withContext(Dispatchers.IO) { mutex.withLock { repository.exclusive { initialize() } } }

    suspend fun newDraft(date: LocalDate, type: AttendanceEventType): CaptureDraft = withContext(Dispatchers.IO) {
        mutex.withLock {
            repository.exclusive {
                initialize()
                val time = now()
                require(date == time.atZone(AttendanceCalculations.manila).toLocalDate()) { "The date changed. Reopen attendance." }
                val id = UUID.randomUUID().toString()
                val paths = photos.paths(id, date, type)
                // Reuse the authoritative validator before opening the camera; confirmation validates again.
                DailyAttendance.validate(AttendanceEvent(id, date, type, time, time, paths.first, paths.second),
                    repository.recordedEvents(), repository.legacySessions(), repository.recordedResolutions())
                CaptureDraft(id, date, type, photos.createDraft(id))
            }
        }
    }
    suspend fun captured(draft: CaptureDraft): CaptureDraft = withContext(Dispatchers.IO) {
        require(draft.file.isFile && draft.file.length() > 0) { "The camera did not save a photo." }
        // Validate the actual captured JPEG now; stamping validates it again before committing.
        AttendanceImageProcessor.decode(draft.file, 1000).recycle()
        draft.copy(capturedAt = now())
    }
    suspend fun retake(draft: CaptureDraft): CaptureDraft = withContext(Dispatchers.IO) {
        if (draft.file.exists()) check(draft.file.delete()) { "Could not remove the previous capture." }
        draft.copy(capturedAt = null)
    }
    suspend fun cancel(draft: CaptureDraft) = withContext(NonCancellable + Dispatchers.IO) { photos.discardDraft(draft.id) }

    // Once confirmed, finish commit/rollback even if the UI coroutine is cancelled on destruction.
    suspend fun confirm(draft: CaptureDraft): AttendanceEvent = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock { repository.exclusive {
            val time = now()
            val captured = requireNotNull(draft.capturedAt) { "Capture a new photo first." }
            val paths = photos.paths(draft.id, draft.date, draft.type)
            val event = AttendanceEvent(draft.id, draft.date, draft.type, time, captured, paths.first, paths.second)
            // Reject replays before touching any existing committed files.
            DailyAttendance.validate(event, repository.recordedEvents(), repository.legacySessions(), repository.recordedResolutions())
            PhotoConfirmation(photos).save(event) { repository.confirmEvent(it) }
        } }
    }
}
