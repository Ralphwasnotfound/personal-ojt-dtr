package com.lazarus.personalojtdtr.data.photos

import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.util.UUID

// Originals are immutable. Photo order is the record's photoPaths order; thumbnails are derived caches.
data class ActivityAttachment(val path: String, val pending: Boolean)
data class PreparedActivityPhotos(val paths: List<String>, val directory: File?)
class ActivityPhotoStore(private val filesDir: File, private val validate: (File) -> Unit,
    private val thumbnail: (File, File) -> Unit, private val syncDirectory: (File) -> Unit = {}) {
    private val root = File(filesDir, "activity-photos")
    private val drafts = File(filesDir, "activity-drafts")
    private fun uuid(value: String) = UUID.fromString(value).toString().also { require(it == value) { "Invalid draft ID." } }
    fun draftDirectory(id: String): File = File(drafts, uuid(id)).also { check(it.exists() || it.mkdirs()) }
    fun newCapture(id: String): File = File(draftDirectory(id), "${UUID.randomUUID()}.jpg")
    fun relative(file: File): String = file.canonicalFile.relativeTo(filesDir.canonicalFile).invariantSeparatorsPath
    fun resolve(path: String): File {
        val file = File(path).let { if (it.isAbsolute) it else File(filesDir, path) }.canonicalFile
        require(file.path.startsWith(filesDir.canonicalPath + File.separator)) { "Photo is outside private storage." }
        return file
    }
    private fun pending(path: String, draftId: String): File = resolve(path).also {
        require(it.path.startsWith(draftDirectory(draftId).canonicalPath + File.separator)) { "Photo does not belong to this editor." }
    }
    fun acceptCapture(draftId: String, file: File): ActivityAttachment {
        val source = pending(relative(file), draftId)
        validate(source)
        thumbnail(source, thumb(source))
        return ActivityAttachment(relative(source), true)
    }
    fun import(draftId: String, input: java.io.InputStream, check: () -> Unit = {}): ActivityAttachment {
        val file = File(draftDirectory(draftId), "${UUID.randomUUID()}.image")
        var typed: File? = null
        try {
            FileOutputStream(file).use { output -> com.lazarus.personalojtdtr.data.backup.BackupArchive.copy(input, output, com.lazarus.personalojtdtr.data.backup.BackupArchive.MAX_PHOTO_BYTES, check); output.fd.sync() }
            validate(file)
            val type = com.lazarus.personalojtdtr.data.backup.BackupArchive.mediaType(file)
            val destination = File(file.parentFile, file.nameWithoutExtension + "." + com.lazarus.personalojtdtr.data.backup.BackupAsset.extension(type))
            typed = destination
            check(file.renameTo(destination)) { "Could not retain selected photo." }
            return acceptCapture(draftId, destination)
        } catch (error: Throwable) { file.delete(); typed?.let { it.delete(); thumb(it).delete() }; throw error }
    }
    private fun thumb(file: File) = File(file.parentFile, file.nameWithoutExtension + ".thumb.jpg")
    fun thumbnailFor(path: String): File = resolve(path).let { if (it.path.startsWith(root.canonicalPath + File.separator) || it.path.startsWith(drafts.canonicalPath + File.separator)) thumb(it).takeIf(File::isFile) ?: it else it }
    fun removePending(attachment: ActivityAttachment, draftId: String) {
        if (attachment.pending) { val file = pending(attachment.path, draftId); file.delete(); thumb(file).delete() }
    }
    fun discardDraft(id: String) { File(drafts, uuid(id)).deleteRecursively() }
    fun prepare(activityId: String, draftId: String, photos: List<ActivityAttachment>): PreparedActivityPhotos {
        require(activityId.isNotBlank() && activityId.length <= 100); uuid(draftId)
        val owner = java.security.MessageDigest.getInstance("SHA-256").digest(activityId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        require(photos.size <= 5 && photos.map { it.path }.distinct().size == photos.size) { "Attach at most five distinct photos." }
        val generation = if (photos.any { it.pending }) File(root, "$owner/${UUID.randomUUID()}") else null
        if (generation != null) check(generation.mkdirs()) { "Could not prepare photo storage." }
        try {
            val paths = photos.map { photo ->
                if (!photo.pending) photo.path else {
                    val source = pending(photo.path, draftId); validate(source)
                    val extension = com.lazarus.personalojtdtr.data.backup.BackupAsset.extension(com.lazarus.personalojtdtr.data.backup.BackupArchive.mediaType(source))
                    val destination = File(generation, "${UUID.randomUUID()}.$extension")
                    FileOutputStream(destination).use { output -> source.inputStream().use { it.copyTo(output) }; output.fd.sync() }
                    thumbnail(destination, thumb(destination))
                    relative(destination)
                }
            }
            generation?.let { syncDirectory(it); syncDirectory(it.parentFile!!); syncDirectory(root); syncDirectory(filesDir) }
            return PreparedActivityPhotos(paths, generation)
        } catch (error: Throwable) { generation?.deleteRecursively(); throw error }
    }
    fun rollback(prepared: PreparedActivityPhotos) { prepared.directory?.deleteRecursively() }
    fun collect(referenced: Set<String>, nowMillis: Long = System.currentTimeMillis()) {
        val canonical = referenced.mapNotNull { runCatching { resolve(it).canonicalPath }.getOrNull() }.toSet()
        root.listFiles()?.filter(File::isDirectory)?.forEach { activity ->
            activity.listFiles()?.filter(File::isDirectory)?.forEach { generation ->
                // A generation may retain several immutable originals; collect individual unreferenced pairs.
                generation.listFiles()?.filter { it.isFile && !it.name.endsWith(".thumb.jpg") }?.forEach { file ->
                    if (file.canonicalPath !in canonical) { file.delete(); thumb(file).delete() }
                }
                generation.listFiles()?.filter { it.isFile && it.name.endsWith(".thumb.jpg") }?.forEach { thumbnail ->
                    val prefix = File(generation, thumbnail.name.removeSuffix(".thumb.jpg") + ".").canonicalPath
                    if (canonical.none { it.startsWith(prefix) }) thumbnail.delete()
                }
                if (generation.listFiles()?.isEmpty() == true) generation.delete()
            }
            if (activity.listFiles()?.isEmpty() == true) activity.delete()
        }
        // Keep recent private drafts across process recreation/Photo Picker. Explicit Cancel removes its draft.
        drafts.listFiles()?.filter { nowMillis - it.lastModified() > Duration.ofDays(7).toMillis() }?.forEach { draft ->
            if (canonical.none { it.startsWith(draft.canonicalPath + File.separator) }) draft.deleteRecursively()
        }
    }
}
