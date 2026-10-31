package com.lazarus.personalojtdtr.data.photos

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.test.InstrumentationTestCase
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import com.lazarus.personalojtdtr.data.backup.*
import com.lazarus.personalojtdtr.data.local.OjtDatabase
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/** Uses only generated photos, a separate directory, and an in-memory database. */
@Suppress("DEPRECATION")
class ProfileAvatarTest : InstrumentationTestCase() {
    fun testPickerPreparationCancelConfirmReplaceRemoveAndRestart() = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.cacheDir, "avatar-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        }
        val source = File(target.cacheDir, "exports/avatar-${UUID.randomUUID()}.jpg").apply { parentFile!!.mkdirs() }
        val db = Room.inMemoryDatabaseBuilder(target, OjtDatabase::class.java).build()
        try {
            val store = LocalAvatarStore(context.filesDir, AvatarImages::validate, {})
            val repo = OjtRepository(db, avatarFiles = store)
            val manager = ProfileAvatarManager(context, repo)
            val image = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(139, 23, 23)) }
            try { AvatarImages.encode(image, source) } finally { image.recycle() }
            ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
            val uri = FileProvider.getUriForFile(target, "${target.packageName}.files", source)
            val canceled = manager.prepare(uri)
            val normalized = AttendanceImageProcessor.decode(canceled.file)
            assertEquals(400, normalized.width); assertEquals(800, normalized.height); normalized.recycle()
            assertNull(store.reference(null)); manager.discard(canceled); assertFalse(canceled.file.exists())
            val draft = manager.prepare(uri); manager.confirm(draft, 1.5f, .3f, -.2f)
            val first = store.validReference(null)!!; assertFalse(draft.file.exists())
            val shared = withTimeout(5000) { repo.avatar.first { it != null } }!!
            assertEquals(store.resolve(first), shared)
            val bitmap = AttendanceImageProcessor.decode(shared)
            assertEquals(512, bitmap.width); assertEquals(512, bitmap.height); bitmap.recycle()
            assertTrue(shared.length() < AvatarImages.MAX_BYTES)
            assertEquals(first, LocalAvatarStore(context.filesDir, AvatarImages::validate, {}).validReference(null))
            val secondDraft = manager.prepare(uri); manager.confirm(secondDraft, 1f, 0f, 0f)
            assertFalse(shared.exists()); assertNotNull(store.validReference(null))
            repo.removeAvatar(); assertNull(withTimeout(5000) { repo.avatar.first { it == null } })
            assertTrue(File(context.filesDir, "profile-avatar/photos").listFiles()!!.isEmpty())
            assertTrue(db.dao().events().isEmpty()); assertTrue(db.dao().sessions().isEmpty())
        } finally { db.close(); root.deleteRecursively(); source.delete() }
    }
    fun testAvatarZipRoundTripAndOlderBackupFallback() = runBlocking {
        val target = instrumentation.targetContext
        val root = File(target.cacheDir, "avatar-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val db = Room.inMemoryDatabaseBuilder(target, OjtDatabase::class.java).build()
        try {
            val files = File(root, "files").apply { mkdirs() }
            val store = LocalAvatarStore(files, AvatarImages::validate, {})
            val repo = OjtRepository(db, avatarFiles = store)
            val source = File(root, "source.jpg")
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            try { AvatarImages.encode(bitmap, source) } finally { bitmap.recycle() }
            repo.confirmAvatar(source)
            val backup = RoomBackupStore(db, store)
            val (records, paths) = backup.snapshot(); assertNotNull(records.avatar)
            val archive = File(root, "backup.zip")
            val written = BackupArchive.create(archive, records, paths.mapValues { File(files, it.value) }, AvatarImages::validate)
            val stage = BackupArchive.inspect(archive, root, AvatarImages::validate)
            assertEquals(written, stage.records)
            repo.removeAvatar()
            AtomicRestore(files, backup, {}).apply(stage, AvatarImages::validate)
            val restored = withTimeout(5000) { repo.avatar.first { it != null } }!!
            assertEquals(BackupArchive.sha256(source), BackupArchive.sha256(restored))
            backup.recover(files); assertTrue(restored.exists())
            val (again, refs) = backup.snapshot(); assertNotNull(again.avatar); assertEquals(1, refs.size)
            val older = BackupRecords(records.createdAt, records.profile, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            AtomicRestore(files, backup, {}).apply(StagedBackup(root, older, emptyMap()), AvatarImages::validate)
            assertNull(withTimeout(5000) { repo.avatar.first { it == null } })
            backup.recover(files)
            assertTrue(db.dao().events().isEmpty()); assertTrue(db.dao().activities().isEmpty())
        } finally { db.close(); root.deleteRecursively() }
    }
    fun testCorruptPhotoAndTransparentNormalization() {
        val file = File(instrumentation.targetContext.cacheDir, "avatar-corrupt-${UUID.randomUUID()}.jpg")
        try {
            file.writeText("broken")
            try { AvatarImages.validate(file); fail("Corrupt photo accepted") } catch (_: Exception) { }
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            try { AvatarImages.encode(bitmap, file) } finally { bitmap.recycle() }
            val normalized = AttendanceImageProcessor.decode(file)
            assertTrue(Color.red(normalized.getPixel(0, 0)) > 230); normalized.recycle()
        } finally { file.delete() }
    }
}
