package com.lazarus.personalojtdtr.data.report

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lazarus.personalojtdtr.domain.usecase.DtrReport
import com.lazarus.personalojtdtr.util.hoursLabel
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A4 points, fixed intact rows, no bitmap/photo decoding. Always printer-friendly. */
object DtrPdf {
    fun write(report: DtrReport, output: OutputStream, check: () -> Unit = {}) {
        val document = PdfDocument()
        try {
            val total = report.pageCount
            var pageNumber = 0
            fun page(notes: Boolean, draw: (Canvas) -> Unit) {
                check()
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                val c = page.canvas
                try {
                    c.drawColor(Color.WHITE)
                    text(c, "MY OJT COMPANION", 32f, 42f, 9f, true, MAROON)
                    text(c, "DAILY TIME RECORD", 32f, 70f, 23f, true)
                    wrapped(c, report.student, 32f, 82f, 531, 12f, true)
                    text(c, report.periodLabel + "  |  Asia/Manila (UTC+08:00)", 32f, 132f, 11f)
                    text(c, "Completed: ${report.total.hoursLabel()}     Recorded attendance days: ${report.days}", 32f, 153f, 11f, true)
                    text(c, if (notes) "RECORD NOTES / LEGACY SESSIONS" else "ATTENDANCE  |  24-hour time", 32f, 178f, 9f, true, MAROON)
                    draw(c)
                    text(c, "Blank = no saved event. * = see record notes. Unclosed sessions add zero hours.", 32f, 752f, 8f)
                    text(c, "Legacy hours use the app's daily allocation. Totals display completed hours/minutes.", 32f, 766f, 8f)
                    text(c, "Device-clock records. Generated offline; evidence photos are stored separately.", 32f, 780f, 8f)
                    line(c, 32f, 795f, 563f, 795f)
                    text(c, "${report.periodLabel}  |  Page $pageNumber of $total", 32f, 812f, 9f)
                } finally { document.finishPage(page) }
            }
            report.tablePages().forEach { rows -> page(false) { c ->
                val x = floatArrayOf(32f, 111f, 201f, 291f, 381f, 471f, 563f)
                val headers = listOf("Date", "Morning In", "Morning Out", "Afternoon In", "Afternoon Out", "Daily Total")
                c.drawRect(32f, 190f, 563f, 218f, Paint().apply { color = Color.rgb(247, 242, 242) })
                headers.forEachIndexed { i, label -> text(c, label, x[i] + 5f, 208f, 9f, true) }
                var y = 218f
                rows.forEach { row ->
                    check()
                    val values = listOf(row.date.format(DateTimeFormatter.ofPattern("dd EEE", Locale.ENGLISH)) + if (row.note.isNotBlank()) " *" else "") + row.times + row.completed.hoursLabel()
                    values.forEachIndexed { i, value -> text(c, value, x[i] + 5f, y + 15f, 9f, i == 5) }
                    y += 21f
                    line(c, 32f, y, 563f, y)
                }
                x.forEach { line(c, it, 190f, it, y) }
            } }
            report.notePages().forEach { notes -> page(true) { c ->
                text(c, "Date / source", 37f, 208f, 9f, true)
                text(c, "Recorded exceptions and legacy details", 152f, 208f, 9f, true)
                var y = 220f
                notes.forEach { note ->
                    check()
                    text(c, note.title, 37f, y + 16f, 9f, true)
                    wrapped(c, note.detail, 152f, y + 4f, 405, 10f)
                    y += 50f
                    line(c, 32f, y, 563f, y)
                }
            } }
            check()
            document.writeTo(output)
            check()
        } finally { document.close() }
    }
    private val MAROON = Color.rgb(139, 23, 23)
    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, bold: Boolean = false, ink: Int = Color.rgb(32, 32, 32)) {
        c.drawText(value, x, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = size; typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL) })
    }
    private fun wrapped(c: Canvas, value: String, x: Float, y: Float, width: Int, size: Float, bold: Boolean = false) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 32, 32); textSize = size; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL) }
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
        c.save(); c.translate(x, y); layout.draw(c); c.restore()
    }
    private fun line(c: Canvas, x: Float, y: Float, endX: Float, endY: Float) {
        c.drawLine(x, y, endX, endY, Paint().apply { color = Color.LTGRAY; strokeWidth = 0.4f })
    }
}
