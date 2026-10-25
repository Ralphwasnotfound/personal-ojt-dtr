package com.lazarus.personalojtdtr.data.backup

import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

class StagedBackup(val directory: File, val records: BackupRecords, val photos: Map<String, File>) {
    fun discard() { directory.deleteRecursively() }
}

/** Portable ZIP+JSON only; no raw database files, caller paths, classes or executable payloads. */
object BackupArchive {
    const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 8L * 1024 * 1024
    const val MAX_ENTRIES = 10001
    fun sha256(file: File, check: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { check(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun mediaType(file: File): String {
        val header = ByteArray(12); val n = file.inputStream().use { it.read(header) }
        return when {
            n >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() -> "image/jpeg"
            n >= 8 && header.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)) -> "image/png"
            n >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> error("A referenced photo is not a supported image.")
        }
    }
    fun safeEntry(name: String): Boolean = name == "manifest.json" || name.matches(Regex("assets/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)"))

    fun create(destination: File, records: BackupRecords, sources: Map<String, File>, validatePhoto: (File) -> Unit, check: () -> Unit = {}): BackupRecords {
        var total = 0L
        val assets = sources.map { (id, file) ->
            check(); require(UUID.fromString(id).toString() == id && file.isFile && file.length() in 1..MAX_PHOTO_BYTES) { "A referenced photo is missing or too large." }
            total += file.length(); require(total <= MAX_TOTAL_BYTES) { "Backup photos exceed the size limit." }
            validatePhoto(file)
            BackupAsset(id, mediaType(file), file.length(), sha256(file, check))
        }
        val data = records.copy(assets = assets)
        BackupValidation.validate(data)
        val manifest = BackupJson.encode(data).toByteArray(Charsets.UTF_8)
        require(total + manifest.size <= MAX_TOTAL_BYTES) { "Expanded backup exceeds the size limit." }
        require(manifest.size <= MAX_MANIFEST_BYTES) { "Backup manifest exceeds the size limit." }
        try {
            FileOutputStream(destination).use { file ->
                ZipOutputStream(file).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                for (asset in assets) {
                    check(); zip.putNextEntry(ZipEntry(asset.entry))
                    sources.getValue(asset.id).inputStream().use { input -> copy(input, zip, MAX_PHOTO_BYTES, check) }
                    zip.closeEntry()
                }
                zip.finish(); zip.flush(); file.fd.sync()
                }
            }
            require(destination.length() <= MAX_TOTAL_BYTES) { "Compressed backup exceeds the size limit." }
            return data
        } catch (error: Throwable) { destination.delete(); throw error }
    }

    fun inspect(archive: File, privateStageRoot: File, validatePhoto: (File) -> Unit, check: () -> Unit = {}): StagedBackup {
        require(archive.isFile && archive.length() in 1..MAX_TOTAL_BYTES) { "Backup archive is empty or too large." }
        val declaredEntries = preflight(archive)
        val stage = File(privateStageRoot, UUID.randomUUID().toString())
        kotlin.check(stage.mkdirs()) { "Could not create private restore staging." }
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().take(MAX_ENTRIES + 1).toList()
                require(entries.size == declaredEntries && entries.size in 1..MAX_ENTRIES && entries.map { it.name }.distinct().size == entries.size) { "Too many or duplicate ZIP entries." }
                entries.forEach {
                    require(!it.isDirectory && safeEntry(it.name) && it.method in listOf(ZipEntry.STORED, ZipEntry.DEFLATED)) { "Unsafe or unsupported ZIP entry." }
                }
                val manifestEntry = zip.getEntry("manifest.json") ?: error("Missing backup manifest.")
                val manifestFile = File(stage, "manifest.json")
                copyEntry(zip, manifestEntry, manifestFile, MAX_MANIFEST_BYTES, check)
                val text = manifestFile.readBytes().toString(Charsets.UTF_8)
                // Round-trip UTF-8 rejects malformed input rather than silently replacing bytes.
                require(text.toByteArray(Charsets.UTF_8).contentEquals(manifestFile.readBytes())) { "Invalid manifest encoding." }
                val records = BackupJson.decode(text)
                BackupValidation.validate(records)
                require(entries.map { it.name }.toSet() == records.assets.map { it.entry }.toSet() + "manifest.json") { "Archive assets do not match the manifest." }
                var total = manifestFile.length()
                val files = records.assets.associate { asset ->
                    check()
                    val target = File(stage, asset.id + "." + BackupAsset.extension(asset.mediaType)) // generated ID, never an entry path
                    copyEntry(zip, zip.getEntry(asset.entry), target, minOf(MAX_PHOTO_BYTES, MAX_TOTAL_BYTES - total), check)
                    total += target.length(); require(total <= MAX_TOTAL_BYTES) { "Expanded backup exceeds the size limit." }
                    require(target.length() == asset.bytes && sha256(target, check) == asset.sha256 && mediaType(target) == asset.mediaType) { "Photo integrity verification failed." }
                    validatePhoto(target)
                    asset.id to target
                }
                return StagedBackup(stage, records, files)
            }
        } catch (error: Throwable) { stage.deleteRecursively(); throw error }
    }
    // Bound central-directory allocation BEFORE ZipFile opens it. Our format needs no ZIP64/multi-disk/SFX.
    private fun preflight(archive: File): Int {
        RandomAccessFile(archive, "r").use { input ->
            require(input.length() >= 22 && input.readInt() == 0x504b0304) { "Not a standard backup ZIP." }
            val tailSize = minOf(input.length(), 65557).toInt()
            val tail = ByteArray(tailSize); input.seek(input.length() - tailSize); input.readFully(tail)
            for (offset in tail.size - 22 downTo 0) {
                if (tail[offset] != 0x50.toByte() || tail[offset + 1] != 0x4b.toByte() || tail[offset + 2] != 0x05.toByte() || tail[offset + 3] != 0x06.toByte()) continue
                val data = java.nio.ByteBuffer.wrap(tail, offset, tail.size - offset).slice().order(java.nio.ByteOrder.LITTLE_ENDIAN)
                fun short(at: Int) = data.getShort(at).toInt() and 0xffff
                fun uint(at: Int) = data.getInt(at).toLong() and 0xffffffffL
                if (offset + 22 + short(20) != tail.size) continue
                val count = short(10)
                require(short(4) == 0 && short(6) == 0 && count == short(8) && count in 1..MAX_ENTRIES) { "Unsupported or excessive ZIP entries/disks." }
                val endPosition = input.length() - tailSize + offset
                require(uint(12) in 1..MAX_MANIFEST_BYTES && uint(16) + uint(12) == endPosition) { "ZIP central directory is unsafe, oversized or incomplete." }
                return count
            }
            error("ZIP is truncated or has unexpected trailing content.")
        }
    }

    private fun copyEntry(zip: ZipFile, entry: ZipEntry, target: File, limit: Long, check: () -> Unit) {
        require(entry.size in 0..limit) { "ZIP entry exceeds the size limit." }
        val crc = CRC32()
        FileOutputStream(target).use { output -> zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(65536); var total = 0L
            while (true) { check(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= limit) { "Expanded ZIP entry exceeds limit." }; crc.update(buffer, 0, n); output.write(buffer, 0, n) }
            require(total == entry.size && crc.value == entry.crc) { "Incomplete or corrupted ZIP entry." }
        }; output.fd.sync() }
    }
    fun copy(input: InputStream, output: OutputStream, limit: Long, check: () -> Unit = {}): Long {
        val buffer = ByteArray(65536); var count = 0L
        while (true) { check(); val n = input.read(buffer); if (n < 0) break; count += n; require(count <= limit) { "File exceeds the backup size limit." }; output.write(buffer, 0, n) }
        return count
    }
}
