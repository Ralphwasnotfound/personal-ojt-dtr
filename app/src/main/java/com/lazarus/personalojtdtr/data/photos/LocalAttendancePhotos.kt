package com.lazarus.personalojtdtr.data.photos

import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.ConfirmationFiles
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.util.UUID

/** No Android dependency: file ownership and crash recovery can be host-tested. */
class LocalAttendancePhotos(
    private val filesDir: File,
    private val render: (File, File, AttendanceEvent) -> Unit,
    private val syncDirectory: (File) -> Unit = { FileChannel.open(it.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) } },
) : ConfirmationFiles {
    private val drafts = File(filesDir, "attendance-drafts")
    private val photos = File(filesDir, "photos/attendance")

    private fun safeId(id: String): String = UUID.fromString(id).toString().also { require(it == id) }
    fun draft(id: String): File = File(drafts, "${safeId(id)}/original.jpg")
    fun createDraft(id: String): File = draft(id).also { check(it.parentFile!!.mkdirs()) { "Could not create photo storage." } }

    fun paths(id: String, date: LocalDate, type: AttendanceEventType): Pair<String, String> {
        val prefix = "photos/attendance/$date/${type.name.lowercase(java.util.Locale.ROOT)}/${safeId(id)}"
        return "$prefix/original.jpg" to "$prefix/timestamped.jpg"
    }
    fun resolve(path: String): File {
        val file = File(filesDir, path).canonicalFile
        require(file.path.startsWith(photos.canonicalPath + File.separator) || file.path.startsWith(File(filesDir, "restore-generations").canonicalPath + File.separator)) { "Invalid attendance photo location." }
        return file
    }
    override fun prepare(event: AttendanceEvent) {
        val expected = paths(event.id, event.date, event.type)
        require(event.originalPhotoPath == expected.first && event.timestampedPhotoPath == expected.second) { "Photo metadata does not match its event." }
        val source = draft(event.id)
        require(source.isFile && source.length() > 0) { "Capture a new photo before confirming." }
        val original = resolve(expected.first)
        val stamped = resolve(expected.second)
        val directory = original.parentFile!!
        // Never claim ownership of an existing (possibly committed) event directory.
        check(!directory.exists()) { "Photo destination already exists. Reopen attendance to check whether it was saved." }
        check(directory.mkdirs()) { "Could not prepare attendance photo storage." }
        try {
            val originalTemp = File(directory, "original.tmp")
            FileOutputStream(originalTemp).use { output -> source.inputStream().use { it.copyTo(output) }; output.fd.sync() }
            check(originalTemp.renameTo(original)) { "Could not preserve original photo." }
            val stampTemp = File(directory, "timestamped.tmp")
            render(original, stampTemp, event)
            require(stampTemp.isFile && stampTemp.length() > 0) { "Timestamped image generation failed." }
            check(stampTemp.renameTo(stamped)) { "Could not save timestamped image." }
            // File fsync does not persist rename/mkdir entries. Flush every containing
            // directory before Room can commit references to this new evidence pair.
            var parent = directory
            while (true) {
                syncDirectory(parent)
                if (parent == filesDir.canonicalFile) break
                parent = requireNotNull(parent.parentFile)
            }
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }
    override fun discardPrepared(event: AttendanceEvent) {
        val expected = paths(event.id, event.date, event.type)
        require(expected.first == event.originalPhotoPath && expected.second == event.timestampedPhotoPath)
        resolve(expected.first).parentFile!!.deleteRecursively()
    }
    override fun discardDraft(id: String) { draft(id).parentFile!!.deleteRecursively() }

    /** Called once before accepting captures, never while a capture/confirmation is running. */
    fun recover(referencedPaths: Set<String>) {
        drafts.deleteRecursively()
        photos.listFiles()?.filter { it.isDirectory }?.forEach { date ->
            date.listFiles()?.filter { it.isDirectory }?.forEach { type ->
                type.listFiles()?.filter { it.isDirectory }?.forEach { event ->
                    val prefix = event.relativeTo(filesDir).invariantSeparatorsPath
                    if (referencedPaths.none { it.startsWith("$prefix/") || runCatching { File(it).canonicalPath.startsWith(event.canonicalPath + File.separator) }.getOrDefault(false) }) event.deleteRecursively()
                }
            }
        }
    }
}
