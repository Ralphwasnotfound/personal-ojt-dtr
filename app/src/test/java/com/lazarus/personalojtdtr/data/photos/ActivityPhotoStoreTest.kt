package com.lazarus.personalojtdtr.data.photos

import com.lazarus.personalojtdtr.data.backup.*
import java.io.File
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActivityPhotoStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAC0lEQVR4XmNgQAYAAA4AAdXbrS0AAAAASUVORK5CYII=")
    private fun store(fail: Boolean = false) = ActivityPhotoStore(temp.root, { require(it.isFile); require(BackupArchive.mediaType(it) == "image/png") }, { _, output -> if (fail) error("fixture thumbnail failure") else output.writeBytes(png) })
    private fun id() = UUID.randomUUID().toString()
    @Test fun `picker image is copied into private storage and original bytes preserved`() {
        val s = store(); val draft = id(); val attachment = s.import(draft, ByteArrayInputStream(png))
        assertTrue(attachment.pending); assertArrayEquals(png, s.resolve(attachment.path).readBytes())
        val prepared = s.prepare("legacy-non-uuid-id", draft, listOf(attachment))
        assertArrayEquals(png, s.resolve(prepared.paths.single()).readBytes())
        assertTrue(s.thumbnailFor(prepared.paths.single()).isFile)
    }
    @Test fun `five photos preserve user order including retained attachments`() {
        val s = store(); val draft = id(); val photos = List(5) { s.import(draft, ByteArrayInputStream(png)) }
        val reversed = photos.reversed(); val result = s.prepare(id(), draft, reversed)
        assertEquals(5, result.paths.size); assertEquals(5, result.paths.distinct().size)
        val reordered = s.prepare("legacy-id", draft, result.paths.reversed().map { ActivityAttachment(it, false) })
        assertEquals(result.paths.reversed(), reordered.paths)
        val mixed = s.prepare(id(), draft, listOf(ActivityAttachment(result.paths[3], false), photos[0]))
        assertEquals(result.paths[3], mixed.paths[0]); assertNotEquals(photos[0].path, mixed.paths[1])
    }
    @Test(expected = IllegalArgumentException::class) fun `storage rejects more than five attachments`() {
        val s = store(); val draft = id(); s.prepare(id(), draft, List(6) { s.import(draft, ByteArrayInputStream(png)) })
    }
    @Test fun `removing draft attachment cannot delete retained photo`() {
        val s = store(); val draft = id(); val photo = s.import(draft, ByteArrayInputStream(png)); val published = s.prepare(id(), draft, listOf(photo))
        s.removePending(ActivityAttachment(published.paths.single(), false), draft)
        assertTrue(s.resolve(published.paths.single()).isFile)
        s.removePending(photo, draft); assertFalse(s.resolve(photo.path).exists()); assertTrue(s.resolve(published.paths.single()).isFile)
    }
    @Test(expected = IllegalArgumentException::class) fun `cannot claim another editor's draft photo`() {
        val s = store(); val photo = s.import(id(), ByteArrayInputStream(png)); s.prepare(id(), id(), listOf(photo))
    }
    @Test fun `collection retains referenced photos of other activity records`() {
        val s = store(); val firstDraft = id(); val secondDraft = id()
        val first = s.prepare(id(), firstDraft, listOf(s.import(firstDraft, ByteArrayInputStream(png))))
        val second = s.prepare(id(), secondDraft, listOf(s.import(secondDraft, ByteArrayInputStream(png))))
        s.collect(second.paths.toSet())
        assertFalse(s.resolve(first.paths.single()).exists()); assertTrue(s.resolve(second.paths.single()).isFile)
    }
    @Test fun `failed database apply removes only newly prepared files`() {
        val s = store(); val draft = id(); val old = s.prepare(id(), draft, listOf(s.import(draft, ByteArrayInputStream(png))))
        val prepared = s.prepare(id(), draft, listOf(s.import(draft, ByteArrayInputStream(png))))
        s.rollback(prepared)
        assertFalse(s.resolve(prepared.paths.single()).exists()); assertTrue(s.resolve(old.paths.single()).isFile)
    }
    @Test fun `thumbnail failure cleans partial import`() {
        val s = store(true); val draft = id()
        try { s.import(draft, ByteArrayInputStream(png)); fail("Expected error") } catch (_: IllegalStateException) { }
        assertTrue(s.draftDirectory(draft).listFiles()!!.isEmpty())
    }
    @Test fun `missing and corrupted photos fail before publication`() {
        val s = store(); val draft = id()
        try { s.import(draft, ByteArrayInputStream("not-image".toByteArray())); fail("Expected error") } catch (_: IllegalStateException) { }
        val photo = s.import(draft, ByteArrayInputStream(png)); s.resolve(photo.path).delete()
        try { s.prepare(id(), draft, listOf(photo)); fail("Expected missing") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `thumbnail retained for missing referenced original and collected after reference removal`() {
        val s = store(); val draft = id(); val prepared = s.prepare(id(), draft, listOf(s.import(draft, ByteArrayInputStream(png))))
        val path = prepared.paths.single(); s.resolve(path).delete()
        s.collect(setOf(path)); assertTrue(s.thumbnailFor(path).isFile)
        s.collect(emptySet()); assertFalse(s.thumbnailFor(path).exists())
    }
    @Test fun `cancelling editor removes only its draft and preserves other editor`() {
        val s = store(); val a = id(); val b = id(); val p = s.import(b, ByteArrayInputStream(png))
        s.import(a, ByteArrayInputStream(png)); s.discardDraft(a)
        assertTrue(s.resolve(p.path).exists())
    }
    @Test fun `age cleanup never removes a draft directory referenced by a saved record`() {
        val s = store(); val draft = id(); val photo = s.import(draft, ByteArrayInputStream(png))
        s.draftDirectory(draft).setLastModified(1)
        s.collect(setOf(photo.path), 10L * 24 * 60 * 60 * 1000)
        assertTrue(s.resolve(photo.path).isFile)
        s.collect(emptySet(), 10L * 24 * 60 * 60 * 1000)
        assertFalse(s.resolve(photo.path).exists())
    }
    @Test(expected = IllegalArgumentException::class) fun `private path traversal is refused`() { store().resolve("../../outside.jpg") }
}
