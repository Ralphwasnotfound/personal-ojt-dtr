package com.lazarus.personalojtdtr.data.photos

import android.content.Context
import android.net.Uri
import android.graphics.*
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.data.backup.BackupArchive
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class AvatarDraft(val id: String, val file: File)
object AvatarImages {
    const val MAX_BYTES = 2L * 1024 * 1024
    fun source(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth in 1..16000 && bounds.outHeight in 1..16000 && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000) { "Choose a supported image smaller than 80 megapixels." }
    }
    fun validate(file: File) {
        require(file.isFile && file.length() in 1..MAX_BYTES) { "Profile photo is missing or too large." }
        require(BackupArchive.mediaType(file) == "image/jpeg")
        source(file); AttendanceImageProcessor.decode(file, 256).recycle()
    }
    fun crop(file: File, zoom: Float, x: Float, y: Float): Bitmap {
        require(zoom.isFinite() && zoom in 1f..3f && x.isFinite() && x in -1f..1f && y.isFinite() && y in -1f..1f)
        val source = AttendanceImageProcessor.decode(file, 1024)
        try {
            val edge = (minOf(source.width, source.height) / zoom).toInt().coerceAtLeast(1)
            val left = ((source.width - edge) * (x + 1) / 2).toInt().coerceIn(0, source.width - edge)
            val top = ((source.height - edge) * (y + 1) / 2).toInt().coerceIn(0, source.height - edge)
            val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            Canvas(output).apply {
                drawColor(Color.rgb(248, 246, 244))
                drawBitmap(source, Rect(left, top, left + edge, top + edge), Rect(0, 0, 512, 512), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            return output
        } finally { source.recycle() }
    }
    fun encode(bitmap: Bitmap, destination: File) {
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(flattened).apply { drawColor(Color.rgb(248, 246, 244)); drawBitmap(bitmap, 0f, 0f, null) }
            FileOutputStream(destination).use { check(flattened.compress(Bitmap.CompressFormat.JPEG, 88, it)); it.fd.sync() }
        } finally { flattened.recycle() }
    }
}
class ProfileAvatarManager(private val context: Context, private val repository: OjtRepository) {
    private val drafts = File(context.cacheDir, "profile-avatar-drafts")
    private val initialization = Mutex()
    private var initialized = false
    suspend fun startup() = withContext(Dispatchers.IO) { initialization.withLock {
        if (!initialized) { drafts.deleteRecursively(); check(drafts.mkdirs()); initialized = true }
    } }
    suspend fun prepare(uri: Uri): AvatarDraft {
        var prepared: AvatarDraft? = null
        try { return withContext(Dispatchers.IO) {
        startup(); require(uri.scheme == "content") { "Choose an image with Android Photo Picker." }
        val id = UUID.randomUUID().toString()
        val raw = File(drafts, "$id.source")
        val normalized = File(drafts, "$id.jpg")
        try {
            FileOutputStream(raw).use { output -> context.contentResolver.openInputStream(uri)?.use { input -> BackupArchive.copy(input, output, BackupArchive.MAX_PHOTO_BYTES) } ?: error("The selected image cannot be read.") }
            AvatarImages.source(raw)
            val bitmap = AttendanceImageProcessor.decode(raw, 1024)
            try { AvatarImages.encode(bitmap, normalized) } finally { bitmap.recycle() }
            AvatarDraft(id, normalized).also { prepared = it }
        } catch (error: Throwable) { normalized.delete(); throw error } finally { raw.delete() }
    }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { prepared?.let(::discard) }
            throw error
        }
    }
    suspend fun confirm(draft: AvatarDraft, zoom: Float, x: Float, y: Float) = withContext(NonCancellable + Dispatchers.IO) {
        require(draft.file.canonicalFile.parentFile == drafts.canonicalFile)
        val rendered = File(drafts, "${draft.id}.ready.jpg")
        try {
            val bitmap = AvatarImages.crop(draft.file, zoom, x, y)
            try { AvatarImages.encode(bitmap, rendered) } finally { bitmap.recycle() }
            repository.confirmAvatar(rendered)
            draft.file.delete()
        } finally { rendered.delete() }
    }
    fun discard(draft: AvatarDraft) { if (draft.file.canonicalFile.parentFile == drafts.canonicalFile) draft.file.delete() }
}
