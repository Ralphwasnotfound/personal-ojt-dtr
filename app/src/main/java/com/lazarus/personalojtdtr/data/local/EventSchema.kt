package com.lazarus.personalojtdtr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration: P1 sessions, profile, activities, photo paths and triggers remain intact. */
object EventSchema {
    const val CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS attendance_events (
            id TEXT NOT NULL PRIMARY KEY, date INTEGER NOT NULL, type INTEGER NOT NULL,
            occurredAt INTEGER NOT NULL, capturedAt INTEGER NOT NULL,
            originalPhotoPath TEXT NOT NULL, timestampedPhotoPath TEXT NOT NULL, createdAt INTEGER NOT NULL
        )
    """
    val indexes = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_events_date_type ON attendance_events(date, type)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_events_originalPhotoPath ON attendance_events(originalPhotoPath)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_events_timestampedPhotoPath ON attendance_events(timestampedPhotoPath)",
        "CREATE INDEX IF NOT EXISTS index_attendance_events_occurredAt ON attendance_events(occurredAt)",
    )
    const val INSERT_GUARD = """
        CREATE TRIGGER IF NOT EXISTS event_insert_guard BEFORE INSERT ON attendance_events BEGIN
            SELECT RAISE(ABORT, 'Invalid event metadata') WHERE NEW.type NOT BETWEEN 0 AND 3
                OR NEW.capturedAt > NEW.occurredAt
                OR date(NEW.occurredAt / 1000.0, 'unixepoch', '+8 hours') != date(NEW.date * 86400, 'unixepoch')
                OR date(NEW.capturedAt / 1000.0, 'unixepoch', '+8 hours') != date(NEW.date * 86400, 'unixepoch')
                OR length(NEW.originalPhotoPath) = 0 OR length(NEW.timestampedPhotoPath) = 0
                OR NEW.originalPhotoPath = NEW.timestampedPhotoPath;
            SELECT RAISE(ABORT, 'Photo already associated with another event') WHERE EXISTS (
                SELECT 1 FROM attendance_events WHERE originalPhotoPath IN (NEW.originalPhotoPath, NEW.timestampedPhotoPath)
                    OR timestampedPhotoPath IN (NEW.originalPhotoPath, NEW.timestampedPhotoPath)
            );
            SELECT RAISE(ABORT, 'Event out of order or already recorded') WHERE
                (SELECT count(*) FROM attendance_events WHERE date = NEW.date) != NEW.type
                OR EXISTS (SELECT 1 FROM attendance_events WHERE occurredAt >= NEW.occurredAt);
            SELECT RAISE(ABORT, 'Earlier session is still open') WHERE NEW.type IN (0, 2) AND EXISTS (
                SELECT 1 FROM attendance_events start WHERE start.type IN (0, 2) AND NOT EXISTS (
                    SELECT 1 FROM attendance_events finish WHERE finish.date = start.date AND finish.type = start.type + 1
                )
            );
            SELECT RAISE(ABORT, 'Legacy attendance overlap') WHERE EXISTS (
                SELECT 1 FROM attendance_sessions old WHERE
                    (NEW.type IN (0, 2) AND (old.timeOut IS NULL OR old.timeOut > NEW.occurredAt)) OR
                    (NEW.type IN (1, 3) AND old.timeIn < NEW.occurredAt AND
                        (old.timeOut IS NULL OR old.timeOut > (SELECT occurredAt FROM attendance_events WHERE date = NEW.date AND type = NEW.type - 1)))
            );
        END
    """
    const val UPDATE_GUARD = """
        CREATE TRIGGER IF NOT EXISTS event_update_guard BEFORE UPDATE ON attendance_events BEGIN
            SELECT RAISE(ABORT, 'Confirmed events are immutable');
        END
    """
    const val DELETE_GUARD = """
        CREATE TRIGGER IF NOT EXISTS event_delete_guard BEFORE DELETE ON attendance_events BEGIN
            SELECT RAISE(ABORT, 'Confirmed events cannot be silently deleted');
        END
    """
    const val LEGACY_INSERT_GUARD = """
        CREATE TRIGGER IF NOT EXISTS legacy_event_insert_guard BEFORE INSERT ON attendance_sessions BEGIN
            SELECT RAISE(ABORT, 'Legacy session overlaps photographed attendance') WHERE EXISTS (
                SELECT 1 FROM attendance_events start WHERE start.type IN (0, 2)
                AND (NEW.timeOut IS NULL OR start.occurredAt < NEW.timeOut)
                AND NEW.timeIn < coalesce((SELECT occurredAt FROM attendance_events finish WHERE finish.date = start.date AND finish.type = start.type + 1), 9223372036854775807)
            );
        END
    """
    const val LEGACY_UPDATE_GUARD = """
        CREATE TRIGGER IF NOT EXISTS legacy_event_update_guard BEFORE UPDATE ON attendance_sessions BEGIN
            SELECT RAISE(ABORT, 'Legacy session overlaps photographed attendance') WHERE EXISTS (
                SELECT 1 FROM attendance_events start WHERE start.type IN (0, 2)
                AND (NEW.timeOut IS NULL OR start.occurredAt < NEW.timeOut)
                AND NEW.timeIn < coalesce((SELECT occurredAt FROM attendance_events finish WHERE finish.date = start.date AND finish.type = start.type + 1), 9223372036854775807)
            );
        END
    """
    fun installGuards(db: SupportSQLiteDatabase) {
        db.execSQL(INSERT_GUARD)
        db.execSQL(UPDATE_GUARD)
        db.execSQL(DELETE_GUARD)
        db.execSQL(LEGACY_INSERT_GUARD)
        db.execSQL(LEGACY_UPDATE_GUARD)
    }
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_TABLE)
            indexes.forEach(db::execSQL)
            installGuards(db)
        }
    }
}
