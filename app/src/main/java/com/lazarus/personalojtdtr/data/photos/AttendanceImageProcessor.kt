package com.lazarus.personalojtdtr.data.photos

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import com.lazarus.personalojtdtr.domain.model.AttendanceEvent
import com.lazarus.personalojtdtr.util.AttendanceTimestamp
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object AttendanceImageProcessor {
    /** Read-only EXIF; the original JPEG is never rewritten. Decode before painting for all eight orientations. */
    fun decode(file: File, maxEdge: Int = 1600): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The photo is missing or cannot be decoded. Please retake." }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("The photo cannot be decoded.")
        val exif = try { ExifInterface(file) } catch (error: Exception) { decoded.recycle(); throw error }
        val matrix = Matrix().apply {
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        var oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        val edge = max(oriented.width, oriented.height)
        if (edge > maxEdge) {
            val scaled = Bitmap.createScaledBitmap(oriented, max(1, oriented.width * maxEdge / edge), max(1, oriented.height * maxEdge / edge), true)
            if (scaled !== oriented) oriented.recycle()
            oriented = scaled
        }
        return oriented
    }

    fun stamp(original: File, output: File, event: AttendanceEvent) {
        val image = decode(original)
        val canvasImage = image.copy(Bitmap.Config.ARGB_8888, true) ?: error("Unable to create timestamp overlay.")
        image.recycle()
        try {
            val canvas = Canvas(canvasImage)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            val padding = minOf(canvas.width, canvas.height) * 0.035f
            val lines = AttendanceTimestamp.lines(event)
            paint.textSize = minOf(canvas.width * 0.032f, canvas.height * 0.04f)
            val widest = lines.maxOf { paint.measureText(it) }
            if (widest > canvas.width - 4 * padding) paint.textSize *= (canvas.width - 4 * padding) / widest
            val lineHeight = paint.fontSpacing * 1.2f
            val height = lines.size * lineHeight + 2 * padding
            val top = canvas.height - height - padding
            val width = lines.maxOf { paint.measureText(it) } + 2 * padding
            canvas.drawRoundRect(padding, top, padding + width, canvas.height - padding, padding * 0.4f, padding * 0.4f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(185, 0, 0, 0) })
            lines.forEachIndexed { index, line -> canvas.drawText(line, 2 * padding, top + padding - paint.fontMetrics.ascent + index * lineHeight, paint) }
            FileOutputStream(output).use {
                check(canvasImage.compress(Bitmap.CompressFormat.JPEG, 88, it)) { "JPEG encoding failed." }
                it.fd.sync()
            }
        } finally { canvasImage.recycle() }
    }
}
