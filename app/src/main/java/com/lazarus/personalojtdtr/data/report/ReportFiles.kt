package com.lazarus.personalojtdtr.data.report

import java.io.*
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

/** Only owns newly-created report cache files, never records or evidence. */
object ReportFiles {
    fun generate(directory: File, write: (OutputStream) -> Unit): File {
        check(directory.isDirectory || directory.mkdirs()) { "Report storage is unavailable." }
        val id = UUID.randomUUID().toString()
        val partial = File(directory, "$id.part")
        val complete = File(directory, "$id.pdf")
        try {
            FileOutputStream(partial).use { output -> write(output); output.fd.sync() }
            require(partial.length() > 0) { "PDF generation returned an empty file." }
            check(partial.renameTo(complete)) { "PDF could not be finalized." }
            return complete
        } catch (error: Throwable) { partial.delete(); complete.delete(); throw error }
    }

    fun requireDestination(destination: String) {
        val uri = URI(destination)
        require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) { "Choose a document destination with the Android file picker." }
    }

    /** A provider success is verified by reading back bytes, without deleting user documents. */
    fun save(file: File, destination: String, output: () -> OutputStream?, input: () -> InputStream?, check: () -> Unit = {}) {
        requireDestination(destination)
        require(file.isFile && file.length() > 0) { "Generate the PDF again; its temporary file is unavailable." }
        check()
        val expected = MessageDigest.getInstance("SHA-256")
        output()?.use { target -> file.inputStream().use { source ->
            val buffer = ByteArray(16384)
            while (true) { check(); val n = source.read(buffer); if (n < 0) break; target.write(buffer, 0, n); expected.update(buffer, 0, n) }
            target.flush()
        } } ?: error("The selected document cannot be written.")
        val actual = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        input()?.use { source ->
            val buffer = ByteArray(16384)
            while (true) {
                check(); val n = source.read(buffer); if (n < 0) break
                bytes += n; require(bytes <= file.length()) { "The saved PDF failed verification." }; actual.update(buffer, 0, n)
            }
        } ?: error("The saved document cannot be verified.")
        require(bytes == file.length() && expected.digest().contentEquals(actual.digest())) { "The saved PDF failed verification." }
    }
}
