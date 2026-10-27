package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.DailyActivity
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class JournalReportTest {
    private val month = JournalPeriod.month(YearMonth.of(2026, 10))
    private fun entry(day: String, text: String = "Saved description", photos: List<String> = emptyList()) = DailyActivity(day, LocalDate.parse(day), text, photos)
    @Test fun emptyJournal() { assertTrue(JournalReport.create(month, "Student", emptyList()).activities.isEmpty()) }
    @Test fun singleActivityKeepsSavedContent() {
        val entry = entry("2026-10-02")
        assertEquals(listOf(entry), JournalReport.create(month, "Student", listOf(entry)).activities)
    }
    @Test fun multipleActivitiesSortedAndMonthBoundariesInclusive() {
        val rows = listOf("2026-11-01", "2026-10-31", "2026-09-30", "2026-10-01").map { entry(it) }
        assertEquals(listOf("2026-10-01", "2026-10-31"), JournalReport.create(month, "S", rows).activities.map { it.id })
        assertEquals("OJT_Journal_2026-10.pdf", month.filename)
    }
    @Test fun customRangeIncludesBothDates() {
        val range = JournalPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-15"))
        assertEquals(2, JournalReport.create(range, "S", listOf(entry("2026-10-01"), entry("2026-10-15"), entry("2026-10-16"))).activities.size)
        assertEquals("OJT_Journal_2026-10-01_to_2026-10-15.pdf", range.filename)
    }
    @Test(expected = IllegalArgumentException::class) fun reversedRangeRejected() { JournalPeriod(month.end, month.start) }
    @Test fun leapMonth() { assertEquals(29, JournalPeriod.month(YearMonth.of(2024, 2)).end.dayOfMonth) }
    @Test fun missingNameIsExplicit() { assertEquals("Not provided", JournalReport.create(month, "  ", emptyList()).student) }
    @Test fun fivePhotosAndLongDescriptionArePreserved() {
        val text = "Full saved text\n".repeat(600)
        val row = entry("2026-10-05", text, (1..5).map { "$it.jpg" })
        val report = JournalReport.create(month, "S", listOf(row))
        assertEquals(5, report.photoCount); assertEquals(text, report.activities.single().description)
    }
    @Test fun multiPageRowsNeverCrossFooter() {
        val cursor = JournalPageCursor()
        repeat(1000) { cursor.reserve(204f); assertTrue(cursor.y + 204 <= cursor.bottom); cursor.advance(204f) }
        assertTrue(cursor.page > 300)
    }
    @Test fun exactBoundaryDoesNotCreateExtraPage() {
        val cursor = JournalPageCursor(); cursor.reserve(702f); cursor.advance(702f)
        assertEquals(1, cursor.page); assertTrue(cursor.reserve(14f)); assertEquals(76f, cursor.y)
    }
}
