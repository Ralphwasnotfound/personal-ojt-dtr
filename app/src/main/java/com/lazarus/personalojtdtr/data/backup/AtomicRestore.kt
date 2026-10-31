package com.lazarus.personalojtdtr.data.backup

import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID

interface RestoreTarget {
    /** One database transaction, including its commit marker. Must not modify existing photo files. */
    suspend fun replace(records: BackupRecords, paths: Map<String, String>, generation: String)
    suspend fun applied(generation: String): Boolean
}
class RestoreOutcomeUnknown : IllegalStateException("Restore outcome could not be read. Restart to check your records; existing photo files were retained.")

/** New immutable photo generation + atomic DB pointer switch. Old files are never moved/overwritten. */
class AtomicRestore(private val filesDir: File, private val target: RestoreTarget,
    private val syncDirectory: (File) -> Unit = { FileChannel.open(it.toPath(), StandardOpenOption.READ).use { channel -> channel.force(true) } }) {
    suspend fun apply(stage: StagedBackup, validatePhoto: (File) -> Unit): String {
        BackupValidation.validate(stage.records)
        val generation = UUID.randomUUID().toString()
        val root = File(filesDir, "restore-generations")
        val directory = File(root, generation)
        check(File(directory, "assets").mkdirs()) { "Could not prepare restored photo storage." }
        val paths = mutableMapOf<String, String>()
        try {
            for (asset in stage.records.assets) {
                val source = stage.photos.getValue(asset.id)
                require(source.isFile && source.length() == asset.bytes && BackupArchive.sha256(source) == asset.sha256) { "Staged photos changed. Select the backup again." }
                validatePhoto(source)
                val relative = "restore-generations/$generation/assets/${asset.id}.${BackupAsset.extension(asset.mediaType)}"
                FileOutputStream(File(filesDir, relative)).use { output ->
                    source.inputStream().use { BackupArchive.copy(it, output, BackupArchive.MAX_PHOTO_BYTES) }; output.fd.sync()
                }
                require(BackupArchive.sha256(File(filesDir, relative)) == asset.sha256) { "Restored photo copy failed integrity verification." }
                paths[asset.id] = relative
            }
            // Prepared before the Room generation marker commits, including an explicit
            // empty avatar for older backups. Never contains an archive-controlled path.
            val avatarName = stage.records.avatar?.let { id ->
                val asset = stage.records.assets.single { it.id == id }
                "$id.${BackupAsset.extension(asset.mediaType)}"
            }.orEmpty()
            FileOutputStream(File(directory, ".avatar")).use { it.write(avatarName.toByteArray()); it.fd.sync() }
            FileOutputStream(File(directory, ".ready")).use { it.write("1".toByteArray()); it.fd.sync() }
            syncDirectory(File(directory, "assets")); syncDirectory(directory); syncDirectory(root); syncDirectory(filesDir)
        } catch (error: Throwable) { directory.deleteRecursively(); throw error }
        try {
            target.replace(stage.records, paths, generation)
        } catch (error: Throwable) {
            val committed = try { target.applied(generation) } catch (_: Throwable) { throw RestoreOutcomeUnknown() }
            if (!committed) { directory.deleteRecursively(); throw error }
            // Commit succeeded but delivery failed: retain the committed files and report actual success.
        }
        return generation
    }

    companion object {
        fun recoverGenerations(filesDir: File, referencedPaths: Set<String>, activeGeneration: String?) {
            File(filesDir, "restore-generations").listFiles()?.filter { it.isDirectory }?.forEach { directory ->
                if (runCatching { UUID.fromString(directory.name).toString() == directory.name }.getOrDefault(false)) {
                    val prefix = "restore-generations/${directory.name}/"
                    if (directory.name != activeGeneration && referencedPaths.none { it.startsWith(prefix) || runCatching { File(it).canonicalPath.startsWith(directory.canonicalPath + File.separator) }.getOrDefault(false) }) directory.deleteRecursively()
                }
            }
        }
    }
}
