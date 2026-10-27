package com.lazarus.personalojtdtr.data.report

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.lazarus.personalojtdtr.data.photos.AttendanceImageProcessor
import com.lazarus.personalojtdtr.domain.usecase.*
import java.io.File
import java.io.OutputStream

/** Streaming layout: full text is split only at measured line boundaries; images fit without cropping. */
object JournalPdf {
    fun write(report: JournalReport, output: OutputStream, resolve: (String) -> File,
        warnings: MutableList<String>, check: () -> Unit = {}) {
        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        val cursor = JournalPageCursor()
        val maroon = Color.rgb(112, 30, 46)
        fun ink(size: Float, bold: Boolean = false, color: Int = Color.rgb(35, 35, 35)) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun newPage() {
            page?.let { document.finishPage(it) }; page = null
            check()
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, cursor.page).create())
            page!!.canvas.apply {
                drawColor(Color.WHITE)
                drawText("MY OJT COMPANION", 36f, 35f, ink(9f, true, maroon))
                drawText("OJT ACTIVITY JOURNAL", 36f, 55f, ink(14f, true))
                drawLine(36f, 64f, 559f, 64f, ink(1f, color = Color.rgb(180, 151, 82)))
                drawText("${report.period.label}  |  Page ${cursor.page}", 36f, 812f, ink(9f))
            }
        }
        fun room(height: Float) { check(); if (cursor.reserve(height)) newPage() }
        fun paragraph(value: String, size: Float = 11f, bold: Boolean = false) {
            if (value.isEmpty()) return
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, ink(size, bold), 523)
                .setIncludePad(false).setLineSpacing(2f, 1f).build()
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line)
                val height = (layout.getLineBottom(line) - top).toFloat()
                room(height)
                page!!.canvas.apply {
                    save(); clipRect(36f, cursor.y, 559f, cursor.y + height)
                    translate(36f, cursor.y - top); layout.draw(this); restore()
                }
                cursor.advance(height)
            }
        }
        fun gap(height: Float) { cursor.advance(minOf(height, cursor.bottom - cursor.y)) }
        try {
            newPage()
            paragraph("Student Name: ${report.student}", 12f, true)
            paragraph("Reporting Period: ${report.period.label}")
            paragraph("Total Activities: ${report.activities.size}    Attached photographs: ${report.photoCount}")
            gap(18f)
            if (report.activities.isEmpty()) paragraph("No saved activities for this reporting period.")
            report.activities.forEach { activity ->
                room(100f) // Reserve heading plus first description lines where possible.
                paragraph(activity.date.toString() + activity.time?.let { "  |  $it (Asia/Manila)" }.orEmpty(), 10f, true)
                paragraph(activity.title, 14f, true)
                gap(6f)
                paragraph(activity.description)
                if (activity.photoPaths.isNotEmpty()) {
                    gap(10f)
                    val single = activity.photoPaths.size == 1
                    activity.photoPaths.take(5).withIndex().toList().chunked(if (single) 1 else 2).forEach { row ->
                        val height = if (single) 240f else 160f
                        room(height + 20f)
                        val y = cursor.y
                        row.forEachIndexed { column, indexed ->
                            check()
                            val width = if (single) 440f else 253f
                            val x = if (row.size == 1) (595f - width) / 2 else 36f + column * 270f
                            val bitmap = try { AttendanceImageProcessor.decode(resolve(indexed.value), if (single) 1200 else 800) }
                                catch (_: Exception) { null }
                            if (bitmap == null) {
                                warnings += "${activity.date} · ${activity.title}: photo ${indexed.index + 1} unavailable or unreadable."
                                page!!.canvas.drawText("Photo ${indexed.index + 1} unavailable", x + 8f, y + 30f, ink(10f))
                            } else try {
                                val scale = minOf(width / bitmap.width, height / bitmap.height)
                                val w = bitmap.width * scale; val h = bitmap.height * scale
                                page!!.canvas.drawBitmap(bitmap, null, RectF(x + (width-w)/2, y + (height-h)/2,
                                    x + (width+w)/2, y + (height+h)/2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                            } finally { bitmap.recycle() }
                            page!!.canvas.drawText("Photo ${indexed.index + 1} · ${activity.date}", x, y + height + 13f, ink(9f))
                        }
                        cursor.advance(height + 20f)
                    }
                }
                gap(18f)
            }
            page?.let { document.finishPage(it) }; page = null
            check(); document.writeTo(output); check()
        } finally { page?.let { document.finishPage(it) }; document.close() }
    }
}
