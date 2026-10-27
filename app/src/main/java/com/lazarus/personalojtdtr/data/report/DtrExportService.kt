package com.lazarus.personalojtdtr.data.report

import android.content.*
import android.net.Uri
import androidx.core.content.FileProvider
import com.lazarus.personalojtdtr.domain.usecase.DtrReport
import kotlinx.coroutines.*
import java.io.File

class DtrExportService(private val context: Context) {
    suspend fun generate(report: DtrReport): File {
        var result: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val task = currentCoroutineContext()
                ReportFiles.generate(File(context.cacheDir, "exports/dtr")) { DtrPdf.write(report, it) { task.ensureActive() } }.also { result = it }
            }
        } catch (error: Throwable) { withContext(NonCancellable + Dispatchers.IO) { result?.delete() }; throw error }
    }
    suspend fun save(file: File, destination: Uri) = withContext(Dispatchers.IO) {
        val task = currentCoroutineContext()
        ReportFiles.save(file, destination.toString(),
            { context.contentResolver.openOutputStream(destination, "wt") },
            { context.contentResolver.openInputStream(destination) }) { task.ensureActive() }
    }
    fun share(file: File, label: String = "DTR", filename: String? = null) {
        require(file.isFile && file.length() > 0) { "Generate the PDF again; its temporary file is unavailable." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file, filename ?: file.name)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "$label report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share $label PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
