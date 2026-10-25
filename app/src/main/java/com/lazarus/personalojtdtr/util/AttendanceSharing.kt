package com.lazarus.personalojtdtr.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lazarus.personalojtdtr.data.photos.*
import com.lazarus.personalojtdtr.domain.model.AttendanceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AttendanceSharing {
    suspend fun share(context: Context, photos: LocalAttendancePhotos, event: AttendanceEvent) {
        val file = withContext(Dispatchers.IO) {
            photos.resolve(event.timestampedPhotoPath).also {
                require(it.isFile) { "The timestamped photo is missing. Attendance is still recorded." }
                AttendanceImageProcessor.decode(it, 500).recycle()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, AttendanceTimestamp.lines(event).joinToString("\n") + "\nTimestamp from device clock.")
            clipData = ClipData.newUri(context.contentResolver, "Attendance photo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share attendance photo").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(chooser) }
        catch (e: ActivityNotFoundException) { error("No compatible sharing application is available. Attendance remains saved.") }
    }
}
