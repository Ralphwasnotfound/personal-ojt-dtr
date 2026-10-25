package com.lazarus.personalojtdtr.util

import android.app.Activity
import android.content.*
import androidx.core.content.FileProvider
import com.lazarus.personalojtdtr.data.backup.BackupArchive
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.domain.model.DailyActivity
import com.lazarus.personalojtdtr.domain.usecase.ActivityRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ActivitySharing {
    suspend fun share(context: Context, activity: DailyActivity, photos: ActivityPhotoStore, paths: List<String>, includeText: Boolean = true) {
        val files = withContext(Dispatchers.IO) { paths.map { path -> photos.resolve(path).also { require(it.isFile) { "Photo is missing." }; com.lazarus.personalojtdtr.data.photos.AttendanceImageProcessor.decode(it, 320).recycle() } } }
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        val send = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = if (uris.isEmpty()) "text/plain" else if (uris.size == 1) BackupArchive.mediaType(files.single()) else "image/*"
            if (includeText || uris.isEmpty()) putExtra(Intent.EXTRA_TEXT, ActivityRules.documentation(activity))
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
            if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (uris.isNotEmpty()) {
                clipData = ClipData.newUri(context.contentResolver, "Activity photo", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(send, "Share activity documentation").apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        try { context.startActivity(chooser) } catch (_: ActivityNotFoundException) { error("No compatible sharing application is available.") }
    }
}
