package com.lazarus.personalojtdtr.domain.usecase

import com.lazarus.personalojtdtr.domain.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DtrRow(val date: LocalDate, val times: List<String>, val completed: Duration,
    val recorded: Boolean, val missing: Boolean, val note: String)
data class DtrNote(val title: String, val detail: String)
data class DtrReport(val period: YearMonth, val student: String, val rows: List<DtrRow>, val notes: List<DtrNote>) {
    val total: Duration get() = rows.fold(Duration.ZERO) { sum, row -> sum.plus(row.completed) }
    val days: Int get() = rows.count { it.recorded }
    val incompleteDays: Int get() = rows.count { it.missing }
    val periodLabel: String get() = period.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    // Fixed row heights keep each row intact. Layout reserves space for header/footer on every page.
    fun tablePages(): List<List<DtrRow>> = rows.chunked(24)
    fun notePages(): List<List<DtrNote>> = notes.chunked(10)
    val pageCount: Int get() = tablePages().size + notePages().size
}

object DtrReports {
    fun create(period: YearMonth, name: String, events: List<AttendanceEvent>,
        legacy: List<AttendanceSession>, resolutions: List<SessionResolution>): DtrReport {
        require(period.year in 1800..2400) { "Choose a year between 1800 and 2400." }
        // Calculate across the entire snapshot before selecting a month. This preserves overlap
        // exclusions and the existing proportional legacy break allocation across midnight.
        val daily = AttendanceCalculations.dailyHours(legacy + DailyAttendance.sessions(events, resolutions))
        val zone = AttendanceCalculations.manila
        val time = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH).withZone(zone)
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).withZone(zone)
        val notes = mutableListOf<DtrNote>()
        val rows = (1..period.lengthOfMonth()).map { day ->
            val date = period.atDay(day)
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val dayEvents = events.filter { it.date == date }
            // Unclosed legacy sessions count only on their recorded start day, not forever.
            val dayLegacy = legacy.filter { if (it.timeOut == null) it.timeIn.atZone(zone).toLocalDate() == date
                else it.timeIn < end && it.timeOut > start }
            val actions = resolutions.filter { it.date == date }
            val recorded = dayEvents.isNotEmpty() || dayLegacy.isNotEmpty()
            val missing = recorded && (dayEvents.isNotEmpty() && dayEvents.size < 4 || dayLegacy.any { it.timeOut == null })
            val note = buildList {
                if (dayEvents.isNotEmpty() && dayEvents.size < 4) add("${4 - dayEvents.size} four-event timestamp(s) missing")
                actions.forEach { add("${it.session.label}: ${it.kind.label}") }
                if (dayLegacy.isNotEmpty()) add("Legacy session(s); see notes")
            }.joinToString(". ")
            if (note.isNotBlank()) notes += DtrNote(date.toString(), note)
            DtrRow(date, AttendanceEventType.entries.map { type -> dayEvents.firstOrNull { it.type == type }?.let { time.format(it.occurredAt) }.orEmpty() },
                daily[date] ?: Duration.ZERO, recorded, missing, note)
        }
        val from = period.atDay(1).atStartOfDay(zone).toInstant()
        val until = period.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
        legacy.filter { if (it.timeOut == null) it.timeIn >= from && it.timeIn < until else it.timeIn < until && it.timeOut > from }
            .sortedBy { it.timeIn }.forEach { session ->
                notes += DtrNote("Legacy P1 session", "In ${stamp.format(session.timeIn)}; Out ${session.timeOut?.let(stamp::format) ?: "not recorded"}; break ${session.breakDuration.toMinutes()}m. Not converted into four events.")
            }
        return DtrReport(period, name.trim().replace(Regex("\\s+"), " ").ifBlank { "Name not configured" }, rows, notes)
    }
}
