package com.lazarus.personalojtdtr.data.photos

import com.lazarus.personalojtdtr.data.backup.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class LocalAvatarStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store() = LocalAvatarStore(temp.root, { require(it.isFile && it.readText() == "valid") }, {})
    private fun draft() = temp.newFile().apply { writeText("valid") }
    private fun empty() = BackupRecords(1793068800000, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    @Test fun `selection and cancellation do not commit`() {
        val selected = draft(); assertNull(store().reference(null)); selected.delete(); assertNull(store().reference(null))
    }
    @Test fun `confirmed replacement persists across restart and removes previous photo`() {
        val store = store(); val first = store.confirm(draft(), null); val second = store.confirm(draft(), null)
        assertFalse(store.resolve(first).exists()); assertEquals(second, store().validReference(null)); assertEquals(1, File(temp.root, "profile-avatar/photos").listFiles()!!.size)
    }
    @Test fun `remove restores placeholder persistently`() {
        val path = store().confirm(draft(), null); store().remove(null)
        assertNull(store().reference(null)); assertFalse(store().resolve(path).exists())
    }
    @Test fun `missing and corrupt files are omitted from backup without discarding reference`() {
        val path = store().confirm(draft(), null); store().resolve(path).writeText("corrupt")
        assertNull(store().validReference(null)); assertEquals(path, store().reference(null))
        store().resolve(path).delete(); assertNull(store().validReference(null)); store().remove(null); assertNull(store().reference(null))
    }
    @Test fun `failed validation preserves old photo`() {
        val path = store().confirm(draft(), null); val bad = draft().apply { writeText("bad") }
        try { store().confirm(bad, null); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(path, store().validReference(null))
    }
    @Test(expected = IllegalArgumentException::class) fun `reference traversal rejected`() { store().resolve("../photos/private.jpg") }
    @Test fun `restore marker switches avatar atomically and older restore clears it`() = runBlocking {
        val previous = store().confirm(draft(), null)
        val id = UUID.randomUUID().toString(); val file = draft()
        val asset = BackupAsset(id, "image/jpeg", file.length(), BackupArchive.sha256(file))
        val records = empty().copy(assets = listOf(asset), avatar = id)
        var marker: String? = null
        val target = object : RestoreTarget {
            override suspend fun replace(records: BackupRecords, paths: Map<String, String>, generation: String) {
                if (marker == null) assertEquals(previous, store().reference(marker)); marker = generation
            }
            override suspend fun applied(generation: String) = marker == generation
        }
        AtomicRestore(temp.root, target, {}).apply(StagedBackup(temp.root, records, mapOf(id to file))) { require(it.readText() == "valid") }
        assertTrue(store().validReference(marker)!!.startsWith("restore-generations/"))
        store().remove(marker); assertNull(store().reference(marker))
        AtomicRestore(temp.root, target, {}).apply(StagedBackup(temp.root, empty(), emptyMap())) {}
        assertNull(store().reference(marker))
    }
    @Test fun `version four backup without avatar remains readable`() {
        val root = JsonParser.parseString(BackupJson.encode(empty())).asJsonObject
        root.addProperty("version", 4); root["records"].asJsonObject.remove("avatar")
        assertNull(BackupJson.decode(root.toString()).avatar)
    }
    @Test fun `avatar metadata round trip and size bounds`() {
        val id = UUID.randomUUID().toString()
        val records = empty().copy(avatar = id, assets = listOf(BackupAsset(id, "image/jpeg", 42, "0".repeat(64))))
        assertEquals(records, BackupJson.decode(BackupJson.encode(records))); BackupValidation.validate(records)
        try { BackupValidation.validate(records.copy(assets = listOf(records.assets.single().copy(bytes = 2097153)))); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun `avatar cannot share attendance or activity asset`() {
        val id = UUID.randomUUID().toString(); val records = empty().copy(avatar = id, assets = listOf(BackupAsset(id, "image/jpeg", 42, "0".repeat(64))),
            activities = listOf(BackupActivity("test", 20686, "Test", listOf(id), 1793000000000, 1793000000000)))
        try { BackupValidation.validate(records); fail() } catch (_: IllegalArgumentException) { }
    }
}
