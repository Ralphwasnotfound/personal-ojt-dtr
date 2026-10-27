package com.lazarus.personalojtdtr.data.photos

import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.domain.usecase.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoConfirmationTest {
    @get:Rule val folder = TemporaryFolder()
    private val original = "test-original-bytes".toByteArray()
    private fun store(render: (File, File, AttendanceEvent) -> Unit = { _, output, event -> output.writeText("stamp:${event.id}:${event.occurredAt}") }) =
        LocalAttendancePhotos(folder.root, render)
    private fun draft(store: LocalAttendancePhotos): AttendanceEvent {
        val id = UUID.randomUUID().toString()
        store.createDraft(id).writeBytes(original)
        val date = LocalDate.of(2026, 10, 24)
        val paths = store.paths(id, date, AttendanceEventType.MORNING_TIME_IN)
        return AttendanceEvent(id, date, AttendanceEventType.MORNING_TIME_IN, Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-23T23:59:50Z"), paths.first, paths.second)
    }
    @Test fun `success preserves original bytes and saves separate stamped photo`() = runBlocking {
        val files = store()
        val e = draft(files)
        var saved: AttendanceEvent? = null
        assertEquals(e, PhotoConfirmation(files).save(e, { false }) { saved = it })
        assertEquals(e, saved)
        assertArrayEquals(original, files.resolve(e.originalPhotoPath).readBytes())
        assertEquals("stamp:${e.id}:${e.occurredAt}", files.resolve(e.timestampedPhotoPath).readText())
        assertFalse(files.draft(e.id).exists())
    }
    @Test fun `retaking draft replaces only temporary bytes and confirmation inserts once`() = runBlocking {
        val files = store()
        val e = draft(files)
        val replacement = "retaken-photo".toByteArray()
        // Recorder retake removes this draft JPEG, then CameraX writes the next capture to it.
        assertTrue(files.draft(e.id).delete())
        files.draft(e.id).writeBytes(replacement)
        assertFalse(files.resolve(e.originalPhotoPath).exists())
        assertFalse(files.resolve(e.timestampedPhotoPath).exists())
        var inserts = 0
        PhotoConfirmation(files).save(e, { false }) { inserts++ }
        assertEquals(1, inserts)
        assertArrayEquals(replacement, files.resolve(e.originalPhotoPath).readBytes())
    }
    @Test fun `failed confirmation can retry without an extra committed event`() = runBlocking {
        val files = store()
        val e = draft(files)
        var committed = 0
        try { PhotoConfirmation(files).save(e, { false }) { error("storage unavailable") }; fail("Expected rejection") }
        catch (_: IllegalStateException) { }
        assertEquals(0, committed)
        assertTrue(files.draft(e.id).isFile)
        PhotoConfirmation(files).save(e, { false }) { committed++ }
        assertEquals(1, committed)
        assertArrayEquals(original, files.resolve(e.originalPhotoPath).readBytes())
    }
    @Test fun `failed commit delivery retains evidence and returns committed event`() = runBlocking {
        val files = store()
        val e = draft(files)
        var persisted: AttendanceEvent? = null
        val result = PhotoConfirmation(files).save(e, { persisted == it }) {
            persisted = it
            error("commit result delivery failed")
        }
        assertEquals(e, result)
        assertArrayEquals(original, files.resolve(e.originalPhotoPath).readBytes())
        assertTrue(files.resolve(e.timestampedPhotoPath).isFile)
        assertFalse(files.draft(e.id).exists())
    }
    @Test fun `unreadable commit outcome retains all evidence for recovery`() = runBlocking {
        val files = store()
        val e = draft(files)
        try {
            PhotoConfirmation(files).save(e, { error("database unavailable") }) { error("unknown write outcome") }
            fail("Expected unknown outcome")
        } catch (error: IllegalStateException) { assertTrue(error.message!!.contains("outcome is unknown")) }
        assertArrayEquals(original, files.resolve(e.originalPhotoPath).readBytes())
        assertTrue(files.resolve(e.timestampedPhotoPath).isFile)
        assertTrue(files.draft(e.id).isFile)
        // A later readable database snapshot protects the committed evidence during recovery.
        files.recover(setOf(e.originalPhotoPath, e.timestampedPhotoPath))
        assertTrue(files.resolve(e.originalPhotoPath).isFile)
        assertTrue(files.resolve(e.timestampedPhotoPath).isFile)
    }
    @Test fun `directory sync failure prevents insert and retains draft`() = runBlocking {
        val files = LocalAttendancePhotos(folder.root, { _, output, _ -> output.writeText("stamp") }, { error("sync failed") })
        val e = draft(files)
        var inserted = false
        try {
            PhotoConfirmation(files).save(e, { false }) { inserted = true }
            fail("Expected durability failure")
        } catch (_: IllegalStateException) { }
        assertFalse(inserted)
        assertTrue(files.draft(e.id).isFile)
        assertFalse(files.resolve(e.originalPhotoPath).exists())
        assertFalse(files.resolve(e.timestampedPhotoPath).exists())
    }
    @Test fun `all containing directories are flushed before database insert`() = runBlocking<Unit> {
        val synced = mutableListOf<File>()
        val files = LocalAttendancePhotos(folder.root, { _, output, _ -> output.writeText("stamp") }, { synced.add(it) })
        val e = draft(files)
        PhotoConfirmation(files).save(e, { false }) {
            val expected = generateSequence(files.resolve(e.originalPhotoPath).parentFile) { parent ->
                if (parent == folder.root) null else parent.parentFile
            }.toList()
            assertEquals(expected, synced)
            assertTrue(files.resolve(e.timestampedPhotoPath).isFile)
        }
    }
    @Test fun `render failure cleans partial final files and retains draft for retake`() = runBlocking {
        val files = store { _, output, _ -> output.writeText("partial"); error("encoder failed") }
        val e = draft(files)
        var inserts = 0
        try { PhotoConfirmation(files).save(e, { false }) { inserts++ }; fail("Expected failure") } catch (_: IllegalStateException) { }
        assertEquals(0, inserts)
        assertFalse(files.resolve(e.originalPhotoPath).exists())
        assertFalse(files.resolve(e.timestampedPhotoPath).exists())
        assertTrue(files.draft(e.id).isFile)
    }
    @Test fun `database rejection cleans both final files without recording attendance`() = runBlocking {
        val files = store()
        val e = draft(files)
        try { PhotoConfirmation(files).save(e, { false }) { error("constraint rejected") }; fail("Expected failure") } catch (_: IllegalStateException) { }
        assertFalse(files.resolve(e.originalPhotoPath).exists())
        assertFalse(files.resolve(e.timestampedPhotoPath).exists())
        assertTrue(files.draft(e.id).isFile)
    }
    @Test fun `repeated confirmation never deletes already committed photos`() = runBlocking {
        val files = store()
        val e = draft(files)
        var inserts = 0
        PhotoConfirmation(files).save(e, { false }) { inserts++ }
        try { PhotoConfirmation(files).save(e, { false }) { inserts++ }; fail("Expected replay rejection") } catch (_: IllegalArgumentException) { }
        assertEquals(1, inserts)
        assertTrue(files.resolve(e.originalPhotoPath).isFile)
        assertTrue(files.resolve(e.timestampedPhotoPath).isFile)
    }
    @Test fun `cancelled capture removes draft without a record`() {
        val files = store()
        val e = draft(files)
        files.discardDraft(e.id)
        assertFalse(files.draft(e.id).exists())
        assertFalse(files.resolve(e.originalPhotoPath).exists())
    }
    @Test fun `startup recovery removes orphans and abandoned drafts but preserves referenced photos`() = runBlocking {
        val files = store()
        val committed = draft(files)
        PhotoConfirmation(files).save(committed, { false }) { }
        val orphan = draft(files)
        files.prepare(orphan) // Simulated crash before DB INSERT.
        val abandoned = draft(files)
        files.recover(setOf(committed.originalPhotoPath, committed.timestampedPhotoPath))
        assertTrue(files.resolve(committed.originalPhotoPath).isFile)
        assertTrue(files.resolve(committed.timestampedPhotoPath).isFile)
        assertFalse(files.resolve(orphan.originalPhotoPath).exists())
        assertFalse(files.draft(abandoned.id).exists())
    }
    @Test fun `cleanup failure after commit does not report save failure`() = runBlocking {
        val real = store()
        val e = draft(real)
        val failing = object : ConfirmationFiles by real { override fun discardDraft(id: String) { error("cleanup failed") } }
        assertEquals(e, PhotoConfirmation(failing).save(e, { false }) { })
        assertTrue(real.resolve(e.timestampedPhotoPath).isFile)
    }
    @Test fun `replay after failed draft cleanup cannot remove committed files`() = runBlocking {
        val real = store()
        val e = draft(real)
        val failing = object : ConfirmationFiles by real { override fun discardDraft(id: String) { error("cleanup failed") } }
        var inserts = 0
        PhotoConfirmation(failing).save(e, { false }) { inserts++ }
        assertTrue(real.draft(e.id).isFile)
        try { PhotoConfirmation(real).save(e, { false }) { inserts++ }; fail("Expected existing destination rejection") } catch (_: IllegalStateException) { }
        assertEquals(1, inserts)
        assertArrayEquals(original, real.resolve(e.originalPhotoPath).readBytes())
        assertTrue(real.resolve(e.timestampedPhotoPath).isFile)
    }
    @Test fun `mismatched event photo association is rejected before files are changed`() {
        val files = store()
        val e = draft(files)
        try { files.prepare(e.copy(type = AttendanceEventType.AFTERNOON_TIME_IN)); fail("Expected mismatch") } catch (_: IllegalArgumentException) { }
        assertTrue(files.draft(e.id).isFile)
        assertFalse(files.resolve(e.originalPhotoPath).exists())
    }
    @Test(expected = IllegalArgumentException::class) fun `private path traversal is refused`() { store().resolve("../../outside.jpg") }
    @Test(expected = IllegalArgumentException::class) fun `unsafe identifiers are refused`() { store().createDraft("../../unsafe") }
    @Test fun `missing capture is not confirmed`() = runBlocking {
        val files = store()
        val e = draft(files)
        files.discardDraft(e.id)
        var inserted = false
        try { PhotoConfirmation(files).save(e, { false }) { inserted = true }; fail("Expected missing capture") } catch (_: IllegalArgumentException) { }
        assertFalse(inserted)
    }
}
