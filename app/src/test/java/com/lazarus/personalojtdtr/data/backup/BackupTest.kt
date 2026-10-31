package com.lazarus.personalojtdtr.data.backup

import java.util.Base64
import java.io.*
import java.time.Instant
import java.util.UUID
import java.util.zip.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupTest {
    @get:Rule val temp = TemporaryFolder()
    private val created = Instant.parse("2026-10-27T09:00:00Z").toEpochMilli()
    private fun empty() = BackupRecords(created, BackupProfile(1, "Fixture", 500, null, 3600), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    // Genuine 2x2 image fixtures; no desktop graphics APIs are required by Android's JVM test compilation.
    private fun image(format: String = "png") = temp.newFile().also {
        val encoded = if (format == "jpg") "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooA//2Q==" else "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAC0lEQVR4XmNgQAYAAA4AAdXbrS0AAAAASUVORK5CYII="
        it.writeBytes(Base64.getDecoder().decode(encoded))
    }
    private fun validate(file: File) { require(BackupArchive.mediaType(file) in listOf("image/png", "image/jpeg")) }
    private fun completeArchive(): File {
        val id = UUID.randomUUID().toString(); val file = image()
        val records = empty().copy(activities = listOf(BackupActivity("activity", 20752, "Fixture activity", listOf(id), created - 1000, created - 1000)))
        return temp.newFile().also { BackupArchive.create(it, records, mapOf(id to file), ::validate) }
    }
    private fun zip(entries: Map<String, ByteArray>): File = temp.newFile().also { file -> ZipOutputStream(file.outputStream()).use { out -> entries.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() } } }
    private fun stage(archive: File) = BackupArchive.inspect(archive, temp.root, ::validate)
    @Test fun `JSON round trip preserves all supported settings`() { assertEquals(empty(), BackupJson.decode(BackupJson.encode(empty()))) }
    @Test(expected = IllegalArgumentException::class) fun `unsupported backup version rejected`() { BackupJson.decode(BackupJson.encode(empty()).replace("\"version\":5", "\"version\":999")) }
    @Test(expected = IllegalArgumentException::class) fun `duplicate JSON keys rejected`() { BackupJson.decode(BackupJson.encode(empty()).replace("\"version\":5", "\"version\":5,\"version\":5")) }
    @Test(expected = IllegalArgumentException::class) fun `unknown manifest fields rejected`() { BackupJson.decode(BackupJson.encode(empty()).replace("\"version\":5", "\"version\":5,\"surprise\":0")) }
    @Test fun `complete attendance half day and all photo metadata round trip`() {
        val date = java.time.LocalDate.of(2026, 10, 26)
        fun at(hour: Int) = date.atTime(hour, 0).atZone(java.time.ZoneId.of("Asia/Manila")).toInstant().toEpochMilli()
        val ids = List(4) { UUID.randomUUID().toString() }
        val events = listOf(BackupEvent("in", date.toEpochDay(), 0, at(8), at(8) - 1000, ids[0], ids[1], at(8)),
            BackupEvent("out", date.toEpochDay(), 1, at(12), at(12) - 1000, ids[2], ids[3], at(12)))
        val data = empty().copy(events = events, resolutions = listOf(BackupResolution("half-day", date.toEpochDay(), 1, 1, at(13))))
        val archive = temp.newFile()
        val written = BackupArchive.create(archive, data, ids.associateWith { image("jpg") }, ::validate)
        val read = stage(archive)
        assertEquals(written, read.records)
        assertEquals(2, read.records.preview().attendanceCount)
        assertEquals(4, read.records.preview().photoCount)
        assertEquals(1, read.records.preview().resolutionCount)
    }
    @Test fun `archive contains valid asset bytes and accurate preview`() {
        val archive = completeArchive(); val staged = stage(archive)
        val preview = staged.records.preview()
        assertEquals("Fixture", preview.displayName); assertEquals(1, preview.activityCount); assertEquals(1, preview.photoCount)
        assertEquals(created, preview.createdAt); assertTrue(staged.photos.values.all { BackupArchive.mediaType(it) == "image/png" })
        staged.discard(); assertTrue(archive.isFile)
    }
    @Test fun `cancel preview deletes staging and preserves source archive`() {
        val archive = completeArchive(); val staged = stage(archive); staged.discard()
        assertFalse(staged.directory.exists()); assertTrue(archive.exists())
    }
    @Test fun `path traversal absolute backslash and unexpected entries rejected`() {
        for (name in listOf("../secret", "/tmp/photo.jpg", "assets/../secret", "assets\\photo.jpg", "payload.exe")) {
            val archive = zip(mapOf("manifest.json" to BackupJson.encode(empty()).toByteArray(), name to byteArrayOf(1)))
            try { stage(archive); fail("Accepted unsafe entry") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `truncated and trailing ZIP content rejected`() {
        val archive = completeArchive(); val bytes = archive.readBytes()
        archive.writeBytes(bytes.copyOf(bytes.size - 5))
        try { stage(archive); fail("Accepted truncation") } catch (_: IllegalStateException) { }
        archive.writeBytes(bytes + byteArrayOf(1, 2, 3))
        try { stage(archive); fail("Accepted trailing payload") } catch (_: IllegalStateException) { }
    }
    @Test fun `too many archive entries rejected before extraction`() {
        val archive = temp.newFile()
        ZipOutputStream(archive.outputStream()).use { out -> repeat(BackupArchive.MAX_ENTRIES + 1) { out.putNextEntry(ZipEntry("entry$it")); out.closeEntry() } }
        try { stage(archive); fail("Accepted excessive entries") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `copy limit stops oversized expanded streams`() {
        try { BackupArchive.copy(ByteArrayInputStream(ByteArray(20)), ByteArrayOutputStream(), 10); fail("Accepted size excess") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `missing photo referenced by activity rejected`() {
        val records = empty().copy(activities = listOf(BackupActivity("a", 20000, "Fixture", listOf(UUID.randomUUID().toString()), created, created)))
        try { BackupValidation.validate(records); fail("Accepted missing reference") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `asset listed in manifest but absent from archive rejected`() {
        val valid = stage(completeArchive()); val json = BackupJson.encode(valid.records)
        try { stage(zip(mapOf("manifest.json" to json.toByteArray()))); fail("Accepted missing asset") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `invalid attendance ordering cannot be restored`() {
        val a = UUID.randomUUID().toString(); val b = UUID.randomUUID().toString(); val image = image("jpg")
        val eventDate = Instant.ofEpochMilli(created - 1000).atZone(java.time.ZoneId.of("Asia/Manila")).toLocalDate().toEpochDay()
        val event = BackupEvent("out-without-in", eventDate, 1, created - 1000, created - 2000, a, b, created - 1000)
        try { BackupArchive.create(temp.newFile(), empty().copy(events = listOf(event)), mapOf(a to image, b to image), ::validate); fail("Accepted invalid attendance") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `photo checksum mismatch rejected`() {
        val valid = stage(completeArchive()); val asset = valid.records.assets.single()
        val altered = image().readBytes().copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        val archive = zip(mapOf("manifest.json" to BackupJson.encode(valid.records).toByteArray(), asset.entry to altered))
        try { stage(archive); fail("Accepted corrupt image") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `inspection cancellation removes staging without touching archive`() {
        val archive = completeArchive(); val before = temp.root.listFiles()!!.map { it.name }.toSet()
        try { BackupArchive.inspect(archive, temp.root, ::validate) { throw CancellationException("fixture cancel") }; fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(before, temp.root.listFiles()!!.map { it.name }.toSet()); assertTrue(archive.isFile)
    }
    @Test fun `oversized declared photo is rejected before expansion`() {
        val archive = completeArchive(); val bytes = archive.readBytes()
        // Increase the asset's central-directory uncompressed length without allocating a huge image.
        val data = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var index = 0; var entries = 0
        while (index <= bytes.size - 46) {
            if (data.getInt(index) == 0x02014b50) {
                entries++
                if (entries == 2) { data.putInt(index + 24, (BackupArchive.MAX_PHOTO_BYTES + 1).toInt()); break }
            }
            index++
        }
        archive.writeBytes(bytes)
        try { stage(archive); fail("Accepted oversized photo declaration") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `recovery and exception metadata survives serialization`() {
        val data = empty().copy(resolutions = listOf(BackupResolution("skip", 20751, 0, 1, created - 1000)))
        assertEquals(data, BackupJson.decode(BackupJson.encode(data)))
    }
    @Test fun `photo validation failure does not publish an archive`() {
        val output = temp.newFile(); val id = UUID.randomUUID().toString()
        val records = empty().copy(activities = listOf(BackupActivity("a", 20000, "Fixture", listOf(id), created, created)))
        try { BackupArchive.create(output, records, mapOf(id to image()), { error("decode fixture failure") }); fail("Expected validation failure") } catch (_: IllegalStateException) { }
        assertEquals(0, output.length())
    }
    private class Target(var data: BackupRecords, var marker: String? = null, var fail: Boolean = false, var failAfterCommit: Boolean = false) : RestoreTarget {
        override suspend fun replace(records: BackupRecords, paths: Map<String, String>, generation: String) {
            if (fail) error("fixture transaction rollback")
            data = records; marker = generation
            if (failAfterCommit) error("fixture failed delivery after commit")
        }
        override suspend fun applied(generation: String) = marker == generation
    }
    @Test fun `restore publishes portable paths only after preparing photos`() = runBlocking {
        val staged = stage(completeArchive()); val root = temp.newFolder(); val target = Target(empty())
        val generation = AtomicRestore(root, target).apply(staged, ::validate)
        assertEquals(generation, target.marker)
        assertTrue(File(root, "restore-generations/$generation/.ready").isFile)
        assertEquals(1, File(root, "restore-generations/$generation/assets").listFiles()!!.size)
    }
    @Test fun `failed final transaction keeps old data and files`() = runBlocking {
        val staged = stage(completeArchive()); val root = temp.newFolder(); val oldFile = File(root, "old-photo").apply { writeText("old") }
        val old = empty(); val target = Target(old, fail = true)
        try { AtomicRestore(root, target).apply(staged, ::validate); fail("Expected rollback") } catch (_: IllegalStateException) { }
        assertEquals(old, target.data); assertEquals("old", oldFile.readText())
        assertTrue(File(root, "restore-generations").listFiles()!!.isEmpty())
    }
    @Test fun `failed commit delivery retains actual committed generation`() = runBlocking {
        val staged = stage(completeArchive()); val root = temp.newFolder(); val target = Target(empty(), failAfterCommit = true)
        val generation = AtomicRestore(root, target).apply(staged, ::validate)
        assertEquals(generation, target.marker); assertTrue(File(root, "restore-generations/$generation/.ready").exists())
    }
    @Test fun `staging corruption preserves current data`() = runBlocking {
        val staged = stage(completeArchive()); val root = temp.newFolder(); val target = Target(empty()); staged.photos.values.single().writeText("damaged")
        try { AtomicRestore(root, target).apply(staged, ::validate); fail("Expected staging rejection") } catch (_: IllegalArgumentException) { }
        assertNull(target.marker); assertEquals(empty(), target.data)
    }
    @Test fun `directory sync failure prevents database replacement`() = runBlocking {
        val staged = stage(completeArchive()); val target = Target(empty()); val root = temp.newFolder()
        try { AtomicRestore(root, target) { error("fixture sync failed") }.apply(staged, ::validate); fail("Expected staging failure") } catch (_: IllegalStateException) { }
        assertNull(target.marker); assertTrue(File(root, "restore-generations").listFiles()!!.isEmpty())
    }
    @Test fun `unknown commit outcome never deletes old or prepared photos`() = runBlocking {
        val staged = stage(completeArchive()); val root = temp.newFolder(); val old = File(root, "old-photo").apply { writeText("original") }
        val target = object : RestoreTarget {
            override suspend fun replace(records: BackupRecords, paths: Map<String, String>, generation: String) { error("fixture connection failure") }
            override suspend fun applied(generation: String): Boolean { error("fixture read failure") }
        }
        try { AtomicRestore(root, target).apply(staged, ::validate); fail("Expected uncertain outcome") } catch (_: RestoreOutcomeUnknown) { }
        assertEquals("original", old.readText())
        assertEquals(1, File(root, "restore-generations").listFiles()!!.size)
    }
    @Test fun `valid archive with forged missing morning recovery is rejected`() {
        val data = empty().copy(resolutions = listOf(BackupResolution("invented", 20751, 0, 0, created - 1000)))
        try { BackupValidation.validate(data); fail("Accepted missing Time In recovery") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `startup recovery removes uncommitted generation and retains active or referenced generation`() {
        val root = temp.newFolder(); val active = UUID.randomUUID().toString(); val orphan = UUID.randomUUID().toString(); val referenced = UUID.randomUUID().toString()
        listOf(active, orphan, referenced).forEach { File(root, "restore-generations/$it").mkdirs() }
        AtomicRestore.recoverGenerations(root, setOf("restore-generations/$referenced/assets/photo.jpg"), active)
        assertTrue(File(root, "restore-generations/$active").exists()); assertTrue(File(root, "restore-generations/$referenced").exists()); assertFalse(File(root, "restore-generations/$orphan").exists())
    }
}
