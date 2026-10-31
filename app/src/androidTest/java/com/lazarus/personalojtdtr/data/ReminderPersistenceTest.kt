package com.lazarus.personalojtdtr.data

import android.test.InstrumentationTestCase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.lazarus.personalojtdtr.data.local.*
import com.lazarus.personalojtdtr.data.repository.OjtRepository
import com.lazarus.personalojtdtr.domain.model.*
import com.lazarus.personalojtdtr.reminders.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.io.File
import java.util.UUID

@Suppress("DEPRECATION")
class ReminderPersistenceTest : InstrumentationTestCase() {
    fun testAttendanceChannelExists() {
        val channel = instrumentation.targetContext.getSystemService(android.app.NotificationManager::class.java)
            .getNotificationChannel(AttendanceReminders.CHANNEL)
        assertNotNull(channel)
        assertEquals("Attendance reminders", channel.name.toString())
    }
    fun testMigrationPreservesRecordsAndAllowsScopedAfternoonSequence() = runBlocking {
        val context = instrumentation.targetContext
        val name = "isolated-reminder-migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `personal_profile` (`id` INTEGER NOT NULL, `displayName` TEXT NOT NULL, `requiredHours` INTEGER NOT NULL, `startDate` INTEGER, `defaultBreakSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `attendance_sessions` (`id` TEXT NOT NULL, `timeIn` INTEGER NOT NULL, `timeOut` INTEGER, `breakSeconds` INTEGER NOT NULL, `timeInPhotoPath` TEXT, `timeOutPhotoPath` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_sessions_timeIn` ON `attendance_sessions` (`timeIn`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_sessions_timeOut` ON `attendance_sessions` (`timeOut`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_activities` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `description` TEXT NOT NULL, `photoPaths` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `title` TEXT NOT NULL DEFAULT 'Activity', `timeMinutes` INTEGER, `revision` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_activities_date` ON `daily_activities` (`date`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `attendance_events` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `type` INTEGER NOT NULL, `occurredAt` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL, `originalPhotoPath` TEXT NOT NULL, `timestampedPhotoPath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_events_date_type` ON `attendance_events` (`date`, `type`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_events_originalPhotoPath` ON `attendance_events` (`originalPhotoPath`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_events_timestampedPhotoPath` ON `attendance_events` (`timestampedPhotoPath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_events_occurredAt` ON `attendance_events` (`occurredAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `session_resolutions` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `session` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `actedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_session_resolutions_date_session` ON `session_resolutions` (`date`, `session`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `restore_marker` (`id` INTEGER NOT NULL, `generation` TEXT NOT NULL, `appliedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ojt_schedule_settings` (`id` INTEGER NOT NULL, `expectedHoursPerDay` INTEGER, `workingWeekdaysMask` INTEGER NOT NULL, `legacyPrefsMigrated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_day_overrides` (`date` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `reason` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))")
                    db.execSQL("INSERT INTO personal_profile VALUES (1, 'Isolated', 486, NULL, 0)")
                    db.execSQL("INSERT INTO calendar_day_overrides VALUES (20686, 4, 'Preserved closure', 1)")
                    db.execSQL("INSERT INTO attendance_sessions VALUES ('preserved', 1000, 3601000, 0, 'original-photo', 'out-photo', 1000, 3601000)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            }).build())
        helper.writableDatabase; helper.close()
        val db = Room.databaseBuilder(context, OjtDatabase::class.java, name).addMigrations(OjtDatabase.MIGRATION_5_6).build()
        try {
            val repo = OjtRepository(db)
            val old = repo.widgetInputs()
            assertEquals(486L, old.requiredHours)
            assertEquals("original-photo", old.legacy.single().timeInPhotoPath)
            assertEquals(WorkdayScope.WHOLE_DAY, old.calendarOverrides.single().scope)
            val day = LocalDate.parse("2026-11-23")
            repo.setCalendarOverride(day, CalendarOverrideKind.SICK_LEAVE, "Isolated", false, WorkdayScope.MORNING)
            val time = day.atTime(13, 0).atZone(ReminderRules.zone).toInstant()
            val event = AttendanceEvent("isolated-in", day, AttendanceEventType.AFTERNOON_TIME_IN, time, time, "o", "s")
            repo.confirmEvent(event)
            assertEquals(1, repo.widgetInputs().events.size)
            var rejected = false
            try { repo.clearCalendarOverride(day, WorkdayScope.MORNING) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            repo.setCalendarOverride(day, CalendarOverrideKind.OTHER, "Edited reason", true, WorkdayScope.MORNING)
            repo.confirmEvent(event.copy(id = "isolated-out", type = AttendanceEventType.AFTERNOON_TIME_OUT,
                occurredAt = time.plusSeconds(3600), capturedAt = time.plusSeconds(3600), originalPhotoPath = "o2", timestampedPhotoPath = "s2"))
            assertEquals(2, repo.widgetInputs().events.size)
            assertEquals(1, repo.widgetInputs().legacy.size)
            repo.setCalendarOverride(day.plusDays(1), CalendarOverrideKind.ABSENT, null, false, WorkdayScope.AFTERNOON)
            repo.clearCalendarOverride(day.plusDays(1), WorkdayScope.AFTERNOON)
            assertTrue(repo.widgetInputs().calendarOverrides.none { it.date == day.plusDays(1) })
        } finally { db.close(); context.deleteDatabase(name) }
    }
    fun testDeliveryLedgerSurvivesStoreRestartAndBoundsSnooze() = runBlocking {
        val file = File(instrumentation.targetContext.cacheDir, "isolated-reminders-${UUID.randomUUID()}.preferences_pb")
        val day = LocalDate.parse("2026-11-23")
        val now = day.atTime(8, 0).atZone(ReminderRules.zone).toInstant()
        var job = SupervisorJob()
        var store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        try {
            val prefs = ReminderPreferences(store)
            prefs.save(ReminderSettings(enabled = true, morningStart = 510))
            assertTrue(prefs.claim(day, OjtSession.MORNING, ReminderOccurrence.READY, now.minusSeconds(600)))
            assertTrue(prefs.claim(day, OjtSession.MORNING, ReminderOccurrence.FIRST, now))
            assertFalse(prefs.claim(day, OjtSession.MORNING, ReminderOccurrence.FIRST, now))
            assertFalse(prefs.claim(day, OjtSession.MORNING, ReminderOccurrence.SECOND, now.plusSeconds(60)))
            assertFalse(prefs.claim(day, OjtSession.MORNING, ReminderOccurrence.SNOOZE, now.plusSeconds(60)))
            assertTrue(prefs.claim(day, OjtSession.MORNING, ReminderOccurrence.SNOOZE, now.plusSeconds(600)))
            assertTrue(prefs.claimSnooze(day, OjtSession.MORNING))
            assertFalse(prefs.claimSnooze(day, OjtSession.MORNING))
            job.cancelAndJoin()
            job = SupervisorJob()
            store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            val restored = ReminderPreferences(store)
            assertEquals(510, restored.settings.first().morningStart)
            assertTrue(restored.fired(day, OjtSession.MORNING, ReminderOccurrence.FIRST))
            assertFalse(restored.claim(day, OjtSession.MORNING, ReminderOccurrence.FIRST, now.plusSeconds(3600)))
            assertFalse(restored.claimSnooze(day, OjtSession.MORNING))
            assertTrue(restored.claim(day, OjtSession.MORNING, ReminderOccurrence.SECOND, now.plusSeconds(1800)))
        } finally { job.cancelAndJoin(); file.delete() }
    }
}
