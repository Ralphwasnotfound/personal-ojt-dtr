package com.lazarus.personalojtdtr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ActivitySchema {
    const val ADD_TITLE = "ALTER TABLE daily_activities ADD COLUMN title TEXT NOT NULL DEFAULT 'Activity'"
    const val ADD_TIME = "ALTER TABLE daily_activities ADD COLUMN timeMinutes INTEGER"
    const val ADD_REVISION = "ALTER TABLE daily_activities ADD COLUMN revision INTEGER NOT NULL DEFAULT 0"
    const val LEGACY_TITLES = "UPDATE daily_activities SET title = CASE WHEN length(trim(replace(description, char(10), ' '))) = 0 THEN 'Activity' ELSE substr(trim(replace(description, char(10), ' ')), 1, 80) END"
    const val INSERT_GUARD = """CREATE TRIGGER activity_insert_guard BEFORE INSERT ON daily_activities BEGIN
        SELECT RAISE(ABORT, 'Invalid activity') WHERE length(trim(NEW.title)) NOT BETWEEN 1 AND 120
            OR length(trim(NEW.description)) NOT BETWEEN 1 AND 10000 OR NEW.revision < 0
            OR (NEW.timeMinutes IS NOT NULL AND NEW.timeMinutes NOT BETWEEN 0 AND 1439)
            OR NEW.updatedAt < NEW.createdAt;
    END"""
    const val UPDATE_GUARD = """CREATE TRIGGER activity_update_guard BEFORE UPDATE ON daily_activities BEGIN
        SELECT RAISE(ABORT, 'Invalid activity edit') WHERE length(trim(NEW.title)) NOT BETWEEN 1 AND 120
            OR length(trim(NEW.description)) NOT BETWEEN 1 AND 10000 OR NEW.revision <= OLD.revision
            OR NEW.createdAt != OLD.createdAt OR NEW.updatedAt < OLD.updatedAt
            OR (NEW.timeMinutes IS NOT NULL AND NEW.timeMinutes NOT BETWEEN 0 AND 1439);
    END"""
    val triggerNames = listOf("activity_insert_guard", "activity_update_guard")
    fun install(db: SupportSQLiteDatabase) { db.execSQL(INSERT_GUARD); db.execSQL(UPDATE_GUARD) }
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(ADD_TITLE); db.execSQL(ADD_TIME); db.execSQL(ADD_REVISION); db.execSQL(LEGACY_TITLES)
            install(db)
        }
    }
}
