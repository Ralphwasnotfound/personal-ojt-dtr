package com.lazarus.personalojtdtr.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lazarus.personalojtdtr.data.local.dao.OjtDao
import com.lazarus.personalojtdtr.data.local.entity.*

@Database(entities = [PersonalProfileEntity::class, AttendanceSessionEntity::class, DailyActivityEntity::class, AttendanceEventEntity::class, SessionResolutionEntity::class, RestoreMarkerEntity::class, OjtScheduleSettingsEntity::class, CalendarDayOverrideEntity::class], version = 6, exportSchema = true)
@TypeConverters(Converters::class)
abstract class OjtDatabase : RoomDatabase() {
    abstract fun dao(): OjtDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendar_day_overrides RENAME TO calendar_day_overrides_v5")
                db.execSQL("CREATE TABLE calendar_day_overrides (`date` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `reason` TEXT, `updatedAt` INTEGER NOT NULL, `scope` INTEGER NOT NULL, PRIMARY KEY(`date`, `scope`))")
                db.execSQL("INSERT INTO calendar_day_overrides SELECT date, kind, reason, updatedAt, 2 FROM calendar_day_overrides_v5")
                db.execSQL("DROP TABLE calendar_day_overrides_v5")
                installScopedGuards(db)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ojt_schedule_settings` (`id` INTEGER NOT NULL, `expectedHoursPerDay` INTEGER, `workingWeekdaysMask` INTEGER NOT NULL, `legacyPrefsMigrated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("INSERT OR IGNORE INTO `ojt_schedule_settings` (`id`, `expectedHoursPerDay`, `workingWeekdaysMask`, `legacyPrefsMigrated`) VALUES (1, NULL, 0, 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_day_overrides` (`date` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `reason` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            }
        }
        fun installScopedGuards(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TRIGGER IF EXISTS event_insert_guard")
            db.execSQL(RecoverySchema.EVENT_GUARD.replace(
                "AND NOT EXISTS (SELECT 1 FROM session_resolutions WHERE date = NEW.date AND session = 0)",
                "AND NOT EXISTS (SELECT 1 FROM session_resolutions WHERE date = NEW.date AND session = 0) AND NOT EXISTS (SELECT 1 FROM calendar_day_overrides WHERE date = NEW.date AND scope = 0 AND kind != 1)"))
        }
        fun installLegacyGuards(db: SupportSQLiteDatabase) {
                // Triggers run atomically inside SQLite, including for future import writers.
                for (operation in listOf("INSERT", "UPDATE")) {
                    db.execSQL("""
                        CREATE TRIGGER attendance_guard_${operation.lowercase()}
                        BEFORE $operation ON attendance_sessions
                        BEGIN
                            SELECT RAISE(ABORT, 'Invalid attendance range') WHERE
                                NEW.breakSeconds < 0 OR (NEW.timeOut IS NOT NULL AND
                                (NEW.timeOut <= NEW.timeIn OR NEW.breakSeconds * 1000 > NEW.timeOut - NEW.timeIn));
                            SELECT RAISE(ABORT, 'Overlapping attendance') WHERE EXISTS (
                                SELECT 1 FROM attendance_sessions old WHERE old.id != NEW.id
                                AND (old.timeOut IS NULL OR NEW.timeIn < old.timeOut)
                                AND (NEW.timeOut IS NULL OR old.timeIn < NEW.timeOut)
                            );
                        END
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TRIGGER profile_guard_${operation.lowercase()}
                        BEFORE $operation ON personal_profile
                        BEGIN
                            SELECT RAISE(ABORT, 'Invalid personal profile') WHERE NEW.id != 1
                                OR NEW.requiredHours < 0 OR NEW.requiredHours > 100000
                                OR NEW.defaultBreakSeconds < 0 OR NEW.defaultBreakSeconds > 86400;
                        END
                    """.trimIndent())
                }
        }
        fun installAllGuards(db: SupportSQLiteDatabase) {
            installLegacyGuards(db)
            EventSchema.installGuards(db)
            RecoverySchema.install(db)
            ActivitySchema.install(db)
            installScopedGuards(db)
        }

        fun create(context: Context): OjtDatabase = Room.databaseBuilder(
            context.applicationContext, OjtDatabase::class.java, "my-ojt-companion.db"
        ).addMigrations(EventSchema.MIGRATION_1_2, RecoverySchema.MIGRATION_2_3, ActivitySchema.MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).addCallback(object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                EventSchema.installGuards(db)
                installLegacyGuards(db)
                RecoverySchema.install(db)
                ActivitySchema.install(db)
                installScopedGuards(db)
            }
        }).build() // No destructive migration fallback. Add explicit migrations when schema changes.
    }
}
