package com.lazarus.personalojtdtr.data.report

import android.test.AndroidTestCase
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.lazarus.personalojtdtr.domain.model.DailyActivity
import com.lazarus.personalojtdtr.domain.usecase.*
import java.io.File
import java.time.*
import kotlinx.coroutines.runBlocking
import com.lazarus.personalojtdtr.data.photos.ActivityPhotoStore

/** Runs only against isolated cache fixtures; never reads or changes the user's journal. */
@Suppress("DEPRECATION")
class JournalPdfDeviceTest : AndroidTestCase() {
    private lateinit var root: File
    override fun setUp() { super.setUp(); root = File(context.cacheDir, "journal-test-${System.nanoTime()}").apply { mkdirs() } }
    override fun tearDown() { root.deleteRecursively(); super.tearDown() }
    private val period = JournalPeriod.month(YearMonth.of(2026, 10))
    private fun row(text: String = "Actual saved description", paths: List<String> = emptyList()) = DailyActivity("fixture", period.start, text, paths, "Saved title")
    private fun render(rows: List<DailyActivity>, warnings: MutableList<String> = mutableListOf()): Int {
        val file = ReportFiles.generate(root) { JournalPdf.write(JournalReport.create(period, "Student", rows), it, { path -> File(root, path) }, warnings) }
        if (rows.singleOrNull()?.photoPaths?.size == 5) file.copyTo(File(context.cacheDir, "journal-layout-validation.pdf"), overwrite = true)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            for (i in 0 until pdf.pageCount) pdf.openPage(i).use { page ->
                val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                try { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); assertEquals(595, page.width) }
                finally { bitmap.recycle() }
            }
            return pdf.pageCount
        }
    }
    fun testEmptyAndSingleActivity() { assertEquals(1, render(emptyList())); assertEquals(1, render(listOf(row()))) }
    fun testLongDescriptionAndManyActivities() { assertTrue(render(List(25) { row("Saved long description with wrapping. ".repeat(250)) }) > 25) }
    fun testFiveRealPhotosAndOriginalPreservation() {
        val image = File(root, "photo.png")
        val bitmap = Bitmap.createBitmap(900, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try { image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        val before = image.readBytes(); val warnings = mutableListOf<String>()
        assertEquals(1, render(listOf(row(paths = List(5) { image.name })), warnings))
        assertTrue(warnings.isEmpty()); assertTrue(before.contentEquals(image.readBytes()))
    }
    fun testMissingAndCorruptedPhotos() = runBlocking {
        File(root, "corrupt.jpg").writeText("not an image")
        val rows = listOf(row(paths = listOf("missing.jpg", "corrupt.jpg")))
        val warnings = mutableListOf<String>(); render(rows, warnings); assertEquals(2, warnings.size)
        val service = JournalExportService(context, ActivityPhotoStore(root, {}, { _, _ -> }))
        assertEquals(2, service.inspect(JournalReport.create(period, "", rows)).warnings.size)
    }
    private fun <T> onMain(block: () -> T): T {
        val task = java.util.concurrent.FutureTask<T> { block() }
        android.os.Handler(android.os.Looper.getMainLooper()).post(task)
        return task.get(10, java.util.concurrent.TimeUnit.SECONDS)
    }
    fun testSimultaneousRequestsAreIgnoredAndPickerCancellationRetainsPdf() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.lazarus.personalojtdtr.data.local.OjtDatabase::class.java).build()
        val store = androidx.lifecycle.ViewModelStore()
        var generated: File? = null
        try {
            val now = Instant.now()
            db.dao().insertActivity(com.lazarus.personalojtdtr.data.local.entity.DailyActivityEntity(
                "fixture", period.start, "Only saved content", emptyList(), now, now, "Title", null, 0))
            val model = onMain {
                com.lazarus.personalojtdtr.ui.report.JournalExportViewModel(
                    com.lazarus.personalojtdtr.data.repository.OjtRepository(db),
                    JournalExportService(context, ActivityPhotoStore(root, {}, { _, _ -> }))).also {
                    store.put("test", it); it.period(month = 10, year = "2026"); it.preview()
                }
            }
            suspend fun idle() {
                kotlinx.coroutines.withTimeout(10000) { while (model.state.value.busy != null) kotlinx.coroutines.delay(10) }
                assertNull(model.state.value.error)
            }
            idle()
            onMain {
                model.generate(); assertNotNull(model.state.value.busy)
                model.generate(); model.preview(); model.period(year = "2025")
                assertEquals("2026", model.state.value.year)
            }
            idle(); generated = model.state.value.file; assertNotNull(generated)
            onMain {
                assertEquals(period.filename, model.beginSave()); assertNull(model.beginSave())
                model.generate(); assertEquals("Choose a save location…", model.state.value.busy)
                model.saveResult(null); assertNull(model.state.value.busy)
                assertEquals(generated, model.state.value.file)
                assertTrue(generated!!.isFile)
            }
        } finally { onMain { store.clear() }; generated?.delete(); db.close() }
    }
    fun testCancellationCleansPartialAndAllowsRetry() {
        try {
            ReportFiles.generate(root) { JournalPdf.write(JournalReport.create(period, "S", listOf(row())), it, { File(root, it) }, mutableListOf()) { throw java.util.concurrent.CancellationException() } }
            fail("Expected cancellation")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertTrue(root.listFiles()!!.isEmpty()); assertEquals(1, render(listOf(row())))
    }
}
