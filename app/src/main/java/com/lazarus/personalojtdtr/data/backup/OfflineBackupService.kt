package com.lazarus.personalojtdtr.data.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import com.lazarus.personalojtdtr.data.photos.AttendanceImageProcessor
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class OfflineBackupService(private val context: Context, private val repository: OjtRepository) {
    private val filesDir get() = context.filesDir
    private val work get() = File(filesDir, "backup-work").also { check(it.exists() || it.mkdirs()) }
    private val initialization = Mutex()
    private var initialized = false
    private val store = RoomBackupStore(repository.database)
    private fun validatePhoto(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..16000 && bounds.outHeight in 1..16000 && bounds.outWidth.toLong() * bounds.outHeight <= 80000000) { "Photo dimensions are invalid or too large." }
        AttendanceImageProcessor.decode(file, 512).recycle()
    }
    private fun source(path: String): File {
        val candidate = File(path).let { if (it.isAbsolute) it else File(filesDir, path) }.canonicalFile
        require(candidate.path.startsWith(filesDir.canonicalPath + File.separator)) { "A referenced photo is outside app-private storage." }
        return candidate
    }
    suspend fun startup() = withContext(Dispatchers.IO) { initialization.withLock {
        if (!initialized) repository.exclusive {
            work.deleteRecursively() // no pending picker/preview survives process death
            store.recover(filesDir)
            initialized = true
        }
    } }
    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        startup()
        require(destination.scheme == "content") { "Select a document destination using Android's file picker." }
        val task = currentCoroutineContext()
        val directory = File(work, UUID.randomUUID().toString()); check(directory.mkdirs())
        try {
            val archive = File(directory, "backup.zip")
            repository.exclusive {
                val (records, paths) = store.snapshot()
                BackupArchive.create(archive, records, paths.mapValues { source(it.value) }, ::validatePhoto) { task.ensureActive() }
                BackupArchive.inspect(archive, directory, ::validatePhoto) { task.ensureActive() }.discard()
            }
            task.ensureActive()
            context.contentResolver.openOutputStream(destination, "wt")?.use { output -> archive.inputStream().use { BackupArchive.copy(it, output, BackupArchive.MAX_TOTAL_BYTES) { task.ensureActive() } } }
                ?: error("The chosen document cannot be written.")
            // Read back the chosen document; success means the completed archive reached its destination.
            val copy = File(directory, "readback.zip")
            FileOutputStream(copy).use { output -> context.contentResolver.openInputStream(destination)?.use { BackupArchive.copy(it, output, BackupArchive.MAX_TOTAL_BYTES) { task.ensureActive() } } ?: error("The exported document could not be verified.") }
            require(copy.length() == archive.length() && BackupArchive.sha256(copy) { task.ensureActive() } == BackupArchive.sha256(archive) { task.ensureActive() }) { "The exported document failed verification." }
        } catch (error: Throwable) {
            withContext(NonCancellable) { runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) } }
            throw error
        } finally { directory.deleteRecursively() }
    }
    suspend fun inspect(source: Uri): StagedBackup {
        var prepared: StagedBackup? = null
        try {
            return withContext(Dispatchers.IO) {
                startup()
                require(source.scheme == "content") { "Select a backup using Android's file picker." }
                val task = currentCoroutineContext()
                val archive = File(work, "${UUID.randomUUID()}.zip")
                try {
                    FileOutputStream(archive).use { output -> context.contentResolver.openInputStream(source)?.use { BackupArchive.copy(it, output, BackupArchive.MAX_TOTAL_BYTES) { task.ensureActive() } } ?: error("The selected document cannot be read.") }
                    BackupArchive.inspect(archive, work, ::validatePhoto) { task.ensureActive() }.also { prepared = it }
                } finally { archive.delete() }
            }
        } catch (error: Throwable) {
            // Covers cancellation between successful staging and delivery back to the UI dispatcher.
            withContext(NonCancellable + Dispatchers.IO) { prepared?.discard() }
            throw error
        }
    }
    suspend fun cancel(stage: StagedBackup) = withContext(NonCancellable + Dispatchers.IO) { stage.discard() }
    suspend fun restore(stage: StagedBackup) = withContext(NonCancellable + Dispatchers.IO) {
        repository.exclusive { AtomicRestore(filesDir, store) { directory ->
            require(directory.isDirectory) { "Restore staging directory is unavailable." }
            val fd = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        }.apply(stage, ::validatePhoto) }
        runCatching { stage.discard() } // cleanup failure after commit is not a failed restore
    }
}
