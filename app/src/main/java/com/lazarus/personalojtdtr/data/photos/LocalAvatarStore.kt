package com.lazarus.personalojtdtr.data.photos

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Private immutable images + one atomic reference. No profile fields or attendance files are changed. */
class LocalAvatarStore(val filesDir: File, private val validate: (File) -> Unit,
    private val syncDirectory: (File) -> Unit = { FileChannel.open(it.toPath(), StandardOpenOption.READ).use { c -> c.force(true) } }) {
    private val root = File(filesDir, "profile-avatar")
    private val photos = File(root, "photos")
    private val pointer = File(root, "reference")
    private data class Reference(val generation: String, val path: String?)
    private fun generation(value: String?) = value?.also { require(UUID.fromString(it).toString() == it) } ?: "-"
    private fun read(): Reference? {
        if (!pointer.exists()) return null
        val parts = pointer.readText().split('\n')
        require(parts.size == 2)
        if (parts[0] != "-") generation(parts[0])
        val path = parts[1].takeIf { it.isNotEmpty() }
        path?.let(::resolve)
        return Reference(parts[0], path)
    }
    fun resolve(path: String): File {
        require(path.matches(Regex("profile-avatar/photos/[0-9a-f-]{36}\\.jpg|restore-generations/[0-9a-f-]{36}/assets/[0-9a-f-]{36}\\.jpg"))) { "Invalid profile photo reference." }
        return File(filesDir, path).canonicalFile.also { require(it.path.startsWith(filesDir.canonicalPath + File.separator)) }
    }
    fun reference(activeGeneration: String?): String? = runCatching {
        val ref = read()
        if (ref != null && ref.generation == generation(activeGeneration)) ref.path
        else activeGeneration?.let { gen ->
            generation(gen)
            val name = File(filesDir, "restore-generations/$gen/.avatar").takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
            name?.let { require(it.matches(Regex("[0-9a-f-]{36}\\.jpg"))); "restore-generations/$gen/assets/$it" }
        }
    }.getOrNull()
    fun validReference(activeGeneration: String?): String? = reference(activeGeneration)?.takeIf { path -> runCatching { validate(resolve(path)); true }.getOrDefault(false) }
    private fun write(ref: Reference) {
        check(root.exists() || root.mkdirs())
        val temp = File(root, "reference.tmp")
        FileOutputStream(temp).use { it.write("${ref.generation}\n${ref.path.orEmpty()}".toByteArray()); it.fd.sync() }
        Files.move(temp.toPath(), pointer.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(root)
    }
    fun confirm(source: File, activeGeneration: String?): String {
        validate(source)
        check(photos.exists() || photos.mkdirs())
        val path = "profile-avatar/photos/${UUID.randomUUID()}.jpg"
        val file = resolve(path)
        FileOutputStream(file).use { output -> source.inputStream().use { it.copyTo(output) }; output.fd.sync() }
        syncDirectory(photos); syncDirectory(root); syncDirectory(filesDir)
        val ref = Reference(generation(activeGeneration), path)
        try { write(ref) } catch (error: Exception) { if (runCatching { read() }.getOrNull() != ref) throw error }
        runCatching { collect(activeGeneration) } // Cleanup may retry at startup; the reference already committed.
        return path
    }
    fun remove(activeGeneration: String?) {
        val ref = Reference(generation(activeGeneration), null)
        try { write(ref) } catch (error: Exception) { if (runCatching { read() }.getOrNull() != ref) throw error }
        runCatching { collect(activeGeneration) } // Cleanup may retry at startup; the reference already committed.
    }
    fun collect(activeGeneration: String?) {
        // An unreadable reference is not permission to delete a possibly active image.
        if (pointer.exists() && runCatching { read() }.isFailure) return
        val active = reference(activeGeneration)
        photos.listFiles()?.filter { it.name.matches(Regex("[0-9a-f-]{36}\\.jpg")) && "profile-avatar/photos/${it.name}" != active }?.forEach { it.delete() }
        File(filesDir, "restore-generations").listFiles()?.filter { it.isDirectory && runCatching { generation(it.name) }.isSuccess }?.forEach { directory ->
            val name = File(directory, ".avatar").takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }
            if (name != null && name.matches(Regex("[0-9a-f-]{36}\\.jpg"))) {
                val relative = "restore-generations/${directory.name}/assets/$name"
                if (relative != active) resolve(relative).delete()
            }
        }
        File(root, "reference.tmp").delete()
    }
}
