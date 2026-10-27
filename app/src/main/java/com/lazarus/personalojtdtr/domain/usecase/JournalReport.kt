package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.DailyActivity
import java.time.LocalDate
import java.time.YearMonth

data class JournalPeriod(val start: LocalDate, val end: LocalDate, val monthly: Boolean = false) {
    init {
        require(start.year in 1800..2400 && end.year in 1800..2400) { "Use dates from 1800 to 2400." }
        require(!end.isBefore(start)) { "End date must be on or after start date." }
        require(!monthly || (start.dayOfMonth == 1 && end == YearMonth.from(start).atEndOfMonth()))
    }
    val label get() = "$start to $end"
    val filename get() = "OJT_Journal_${if (monthly) YearMonth.from(start).toString() else "${start}_to_${end}"}.pdf"
    companion object { fun month(month: YearMonth) = JournalPeriod(month.atDay(1), month.atEndOfMonth(), true) }
}
data class JournalReport(val period: JournalPeriod, val student: String, val activities: List<DailyActivity>, val warnings: List<String> = emptyList()) {
    val photoCount get() = activities.sumOf { it.photoPaths.size }
    companion object {
        fun create(period: JournalPeriod, name: String, entries: List<DailyActivity>) = JournalReport(period,
            name.trim().ifEmpty { "Not provided" }, entries.filter { it.date >= period.start && it.date <= period.end }
                .sortedWith(compareBy({ it.date }, { it.time }, { it.createdAt }, { it.id })))
    }
}

/** Shared page cursor used by text fragments and intact photo rows. Dimensions are A4 points. */
class JournalPageCursor(val top: Float = 76f, val bottom: Float = 778f) {
    var y = top; private set
    var page = 1; private set
    fun reserve(height: Float): Boolean {
        require(height > 0 && height <= bottom - top)
        val changed = y + height > bottom
        if (changed) { page++; y = top }
        return changed
    }
    fun advance(height: Float) { require(height >= 0 && y + height <= bottom + 0.01f); y += height }
}
