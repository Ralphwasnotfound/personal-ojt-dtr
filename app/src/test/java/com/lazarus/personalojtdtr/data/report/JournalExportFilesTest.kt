package com.lazarus.personalojtdtr.data.report

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.concurrent.CancellationException

class JournalExportFilesTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun repeatedExportsUseIndependentFiles() {
        val files = (1..12).map { index -> ReportFiles.generate(temp.root) { it.write("journal-$index".toByteArray()) } }
        assertEquals(12, files.toSet().size)
        files.forEachIndexed { i, file -> assertEquals("journal-${i+1}", file.readText()) }
    }
    @Test fun cancelledExportPreservesEarlierExportAndEvidence() {
        val evidence = File(temp.root, "original.jpg").apply { writeText("original evidence") }
        val previous = ReportFiles.generate(temp.root) { it.write("previous report".toByteArray()) }
        try { ReportFiles.generate(temp.root) { it.write(42); throw CancellationException() }; fail() }
        catch (_: CancellationException) { }
        assertEquals("original evidence", evidence.readText()); assertEquals("previous report", previous.readText())
        assertEquals(2, temp.root.listFiles()!!.size)
    }
    @Test fun failedSaveDestinationAllowsRetry() {
        val source = ReportFiles.generate(temp.root) { it.write("journal bytes".toByteArray()) }
        try { ReportFiles.save(source, "content://documents/failed", { null }, { null }); fail() }
        catch (_: IllegalStateException) { }
        val output = ByteArrayOutputStream()
        ReportFiles.save(source, "content://documents/retry", { output }, { ByteArrayInputStream(output.toByteArray()) })
        assertEquals("journal bytes", output.toString("UTF-8")); assertTrue(source.exists())
    }
}
