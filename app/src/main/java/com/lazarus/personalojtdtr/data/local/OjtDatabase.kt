package com.lazarus.personalojtdtr.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lazarus.personalojtdtr.data.local.dao.OjtDao
import com.lazarus.personalojtdtr.data.local.entity.*

@Database(entities = [PersonalProfileEntity::class, AttendanceSessionEntity::class, DailyActivityEntity::class, AttendanceEventEntity::class, SessionResolutionEntity::class, RestoreMarkerEntity::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class OjtDatabase : RoomDatabase() {
    abstract fun dao(): OjtDao

    companion object {
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
        }

        fun create(context: Context): OjtDatabase = Room.databaseBuilder(
            context.applicationContext, OjtDatabase::class.java, "my-ojt-companion.db"
        ).addMigrations(EventSchema.MIGRATION_1_2, RecoverySchema.MIGRATION_2_3, ActivitySchema.MIGRATION_3_4).addCallback(object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                EventSchema.installGuards(db)
                installLegacyGuards(db)
                RecoverySchema.install(db)
                ActivitySchema.install(db)
            }
        }).build() // No destructive migration fallback. Add explicit migrations when schema changes.
    }
}
