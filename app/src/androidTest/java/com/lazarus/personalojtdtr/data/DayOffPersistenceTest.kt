package com.lazarus.personalojtdtr.data

import android.test.InstrumentationTestCase
import androidx.room.Room
import com.lazarus.personalojtdtr.data.local.OjtDatabase
import com.lazarus.personalojtdtr.data.local.entity.AttendanceSessionEntity
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.domain.model.*
import kotlinx.coroutines.runBlocking
import java.time.*

@Suppress("DEPRECATION")
class DayOffPersistenceTest : InstrumentationTestCase() {
    fun testConfirmCancelConflictAndPreserveAttendance() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, OjtDatabase::class.java).build()
        try {
            val repo = OjtRepository(db)
            val date = LocalDate.parse("2026-11-23")
            repo.saveSchedule(8, setOf(DayOfWeek.MONDAY))
            repo.setCalendarOverride(date, CalendarOverrideKind.DAY_OFF, "Holiday")
            repo.setCalendarOverride(date, CalendarOverrideKind.DAY_OFF, "Updated reason")
            assertEquals(1, repo.widgetInputs().calendarOverrides.size)
            assertEquals("Updated reason", repo.widgetInputs().calendarOverrides.single().reason)
            repo.clearCalendarOverride(date)
            assertTrue(repo.widgetInputs().calendarOverrides.isEmpty())
            val start = date.atStartOfDay(ZoneId.of("Asia/Manila")).toInstant()
            db.dao().insertSession(AttendanceSessionEntity("isolated", start, start.plusSeconds(3600), createdAt = start, updatedAt = start))
            var rejected = false
            try { repo.setCalendarOverride(date, CalendarOverrideKind.DAY_OFF, null) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            repo.setCalendarOverride(date, CalendarOverrideKind.DAY_OFF, null, true)
            assertEquals(1, repo.widgetInputs().legacy.size)
            repo.clearCalendarOverride(date)
            assertEquals(1, repo.widgetInputs().legacy.size)
            CalendarOverrideKind.entries.forEach { kind ->
                repo.setCalendarOverride(date, kind, "Specified reason", true)
                assertEquals(kind, repo.widgetInputs().calendarOverrides.single().kind)
                assertEquals(1, repo.widgetInputs().legacy.size)
            }
        } finally { db.close() }
    }
}
