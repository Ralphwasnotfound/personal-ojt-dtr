package com.lazarus.personalojtdtr.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import androidx.room.withTransaction
import com.lazarus.personalojtdtr.data.backup.*
import com.lazarus.personalojtdtr.data.local.entity.DailyActivityEntity
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.domain.usecase.ActivityRules
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.time.*

class ActivityJournal(private val context: Context, private val repository: OjtRepository) {
    val photos = ActivityPhotoStore(context.filesDir, ::validatePhoto, ::thumbnail, ::syncDirectory)
    private val initialization = Mutex()
    private var initialized = false
    private val dao get() = repository.database.dao()
    private fun syncDirectory(file: File) {
        val fd = Os.open(file.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }
    private fun validatePhoto(file: File) {
        require(file.isFile && file.length() in 1..BackupArchive.MAX_PHOTO_BYTES) { "Photo is missing or larger than 20 MiB." }
        BackupArchive.mediaType(file) // JPEG, PNG or WebP; never trust a picker extension/MIME hint.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..16000 && bounds.outHeight in 1..16000 && bounds.outWidth.toLong() * bounds.outHeight <= 80000000) { "Unsupported or oversized photo." }
        AttendanceImageProcessor.decode(file, 512).recycle()
    }
    private fun thumbnail(source: File, output: File) {
        val bitmap = AttendanceImageProcessor.decode(source, 320)
        try { FileOutputStream(output).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)); it.fd.sync() } }
        finally { bitmap.recycle() }
    }
    suspend fun startup() = withContext(Dispatchers.IO) { initialization.withLock {
        if (!initialized) repository.exclusive { collect(); initialized = true }
    } }
    private suspend fun collect() { photos.collect(RoomBackupStore(repository.database).referencedPaths()) }
    suspend fun import(draftId: String, uris: List<Uri>): List<ActivityAttachment> = withContext(Dispatchers.IO) {
        startup(); val task = currentCoroutineContext(); val imported = mutableListOf<ActivityAttachment>()
        try {
            for (uri in uris) { task.ensureActive(); require(uri.scheme == "content") { "Use the system Photo Picker." }
                context.contentResolver.openInputStream(uri)?.use { imported += photos.import(draftId, it) { task.ensureActive() } }
                    ?: error("Selected photo cannot be read.")
            }
            imported
        } catch (error: Throwable) { imported.forEach { photos.removePending(it, draftId) }; throw error }
    }
    suspend fun acceptCapture(draftId: String, file: File) = withContext(Dispatchers.IO) { photos.acceptCapture(draftId, file) }
    suspend fun removePending(draftId: String, photo: ActivityAttachment) = withContext(NonCancellable + Dispatchers.IO) { photos.removePending(photo, draftId) }
    suspend fun discard(draftId: String) = withContext(NonCancellable + Dispatchers.IO) { photos.discardDraft(draftId) }
    suspend fun save(id: String, expectedRevision: Long?, draftId: String, title: String, description: String,
        date: LocalDate, time: LocalTime?, attachments: List<ActivityAttachment>) = withContext(NonCancellable + Dispatchers.IO) {
        startup()
        repository.exclusive {
            ActivityRules.validate(title, description, date, attachments.map { it.path })
            require(time == null || (time.second == 0 && time.nano == 0)) { "Activity time uses hours and minutes only." }
            val old = dao.activity(id)
            ActivityRules.requireRevision(expectedRevision, old?.revision)
            ActivityRules.requireOwnership(attachments.filterNot { it.pending }.map { it.path }, old?.photoPaths.orEmpty())
            val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
            require(old == null || now >= old.updatedAt) { "The device clock is earlier than this activity's last update." }
            val prepared = photos.prepare(id, draftId, attachments)
            val record = DailyActivityEntity(id, date, description.trim(), prepared.paths, old?.createdAt ?: now, now, title.trim(), time?.let { it.hour * 60 + it.minute }, (old?.revision ?: -1) + 1)
            try {
                repository.database.withTransaction {
                    ActivityRules.requireRevision(expectedRevision, dao.activity(id)?.revision)
                    if (old == null) dao.insertActivity(record) else check(dao.updateActivity(record) == 1) { "Activity was not saved." }
                }
            } catch (error: Throwable) {
                // Do not delete published files if commit delivery failed or the outcome cannot be read.
                val delivered = runCatching { dao.activity(id) }
                if (delivered.isFailure) throw IllegalStateException("Save outcome is unknown. Reopen the activity before retrying.")
                if (delivered.getOrNull() != record) { photos.rollback(prepared); throw error }
            }
            runCatching { photos.discardDraft(draftId); collect() } // cleanup failure after commit must not report a failed save
        }
    }
    suspend fun delete(id: String, revision: Long) = withContext(NonCancellable + Dispatchers.IO) { repository.exclusive {
        repository.database.withTransaction {
            val old = dao.activity(id) ?: error("Activity no longer exists.")
            ActivityRules.requireRevision(revision, old.revision)
            check(dao.deleteActivity(id, revision) == 1) { "Activity was not deleted." }
        }
        runCatching { collect() } // shared/restored/attendance files are never deleted by this collector
    } }
}
