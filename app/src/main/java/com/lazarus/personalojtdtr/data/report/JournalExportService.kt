package com.lazarus.personalojtdtr.data.report

import android.content.Context
import android.net.Uri
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore
import com.lazarus.personalojtdtr.data.photos.AttendanceImageProcessor
import com.lazarus.personalojtdtr.domain.usecase.JournalReport
import kotlinx.coroutines.*
import java.io.File

class JournalExportService(private val context: Context, private val photos: ActivityPhotoStore) {
    private val transport = DtrExportService(context)
    suspend fun inspect(report: JournalReport): JournalReport = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        report.activities.forEach { activity -> activity.photoPaths.forEachIndexed { index, path ->
            currentCoroutineContext().ensureActive()
            try { AttendanceImageProcessor.decode(photos.resolve(path), 128).recycle() }
            catch (_: Exception) { warnings += "${activity.date} · ${activity.title}: photo ${index + 1} unavailable or unreadable." }
        } }
        report.copy(warnings = warnings)
    }
    suspend fun generate(report: JournalReport): Pair<File, List<String>> {
        var result: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val task = currentCoroutineContext()
                val warnings = mutableListOf<String>()
                val file = ReportFiles.generate(File(context.cacheDir, "exports/journal")) { output ->
                    JournalPdf.write(report, output, { photos.resolve(it) }, warnings) { task.ensureActive() }
                }.also { result = it }
                file to warnings
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { result?.delete() }; throw error
        }
    }
    suspend fun save(file: File, destination: Uri) = transport.save(file, destination)
    fun share(file: File, filename: String) = transport.share(file, "Journal", filename)
}
