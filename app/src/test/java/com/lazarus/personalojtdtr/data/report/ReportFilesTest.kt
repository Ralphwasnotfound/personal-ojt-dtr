package com.lazarus.personalojtdtr.data.report

import java.io.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReportFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private val bytes = "%PDF-1.4 fixture".toByteArray()
    private fun file() = ReportFiles.generate(temp.root) { it.write(bytes) }
    @Test fun `generation publishes complete bytes with no partial file`() {
        val f = file(); assertArrayEquals(bytes, f.readBytes())
        assertEquals(listOf(f), temp.root.listFiles()!!.toList())
    }
    @Test fun `generation failure removes only its own partial file`() {
        val old = file()
        try { ReportFiles.generate(temp.root) { it.write(bytes); error("disk full") }; fail() } catch (_: IllegalStateException) { }
        assertEquals(listOf(old), temp.root.listFiles()!!.toList()); assertArrayEquals(bytes, old.readBytes())
    }
    @Test fun `cancelled generation never publishes a PDF`() {
        try { ReportFiles.generate(temp.root) { it.write(bytes); throw CancellationException() }; fail() } catch (_: CancellationException) { }
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }
    @Test fun `empty PDF output is refused`() {
        try { ReportFiles.generate(temp.root) { }; fail() } catch (_: IllegalArgumentException) { }
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }
    @Test fun `invalid destination cannot open output or change generated report`() {
        val f = file()
        for (uri in listOf("file:///tmp/report.pdf", "https://example.com/report.pdf", "content:/missing-authority")) {
            var opened = false
            try { ReportFiles.save(f, uri, { opened = true; ByteArrayOutputStream() }, { null }); fail() } catch (_: IllegalArgumentException) { }
            assertFalse(opened); assertArrayEquals(bytes, f.readBytes())
        }
    }
    @Test fun `save verifies provider bytes before success`() {
        val f = file(); val output = ByteArrayOutputStream()
        ReportFiles.save(f, "content://documents/report", { output }, { ByteArrayInputStream(output.toByteArray()) })
        assertArrayEquals(bytes, output.toByteArray()); assertArrayEquals(bytes, f.readBytes())
    }
    @Test fun `unwritable destination retains PDF for retry`() {
        val f = file()
        try { ReportFiles.save(f, "content://documents/report", { null }, { null }); fail() } catch (_: IllegalStateException) { }
        assertArrayEquals(bytes, f.readBytes())
    }
    @Test fun `partial provider write failure is reported and source retained`() {
        val f = file()
        try { ReportFiles.save(f, "content://documents/report", { object : OutputStream() { override fun write(b: Int) { throw IOException("disk full") } } }, { null }); fail() }
        catch (_: IOException) { }
        assertArrayEquals(bytes, f.readBytes())
    }
    @Test fun `truncated provider readback is rejected`() {
        val f = file()
        try { ReportFiles.save(f, "content://documents/report", { ByteArrayOutputStream() }, { ByteArrayInputStream(bytes.dropLast(1).toByteArray()) }); fail() }
        catch (_: IllegalArgumentException) { }
        assertArrayEquals(bytes, f.readBytes())
    }
    @Test fun `same length corrupt provider readback is rejected`() {
        val f = file()
        try { ReportFiles.save(f, "content://documents/report", { ByteArrayOutputStream() }, { ByteArrayInputStream(ByteArray(bytes.size)) }); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `cancelled save retains generated PDF`() {
        val f = file()
        try { ReportFiles.save(f, "content://documents/report", { ByteArrayOutputStream() }, { null }) { throw CancellationException() }; fail() }
        catch (_: CancellationException) { }
        assertArrayEquals(bytes, f.readBytes())
    }
    @Test fun `evicted cache file fails before opening document`() {
        val f = file(); assertTrue(f.delete()); var opened = false
        try { ReportFiles.save(f, "content://documents/report", { opened = true; ByteArrayOutputStream() }, { null }); fail() }
        catch (_: IllegalArgumentException) { }
        assertFalse(opened)
    }
}
