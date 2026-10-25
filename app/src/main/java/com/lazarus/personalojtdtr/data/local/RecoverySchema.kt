package com.lazarus.personalojtdtr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object RecoverySchema {
    const val CREATE_RESOLUTIONS = """CREATE TABLE IF NOT EXISTS session_resolutions (
        id TEXT NOT NULL PRIMARY KEY, date INTEGER NOT NULL, session INTEGER NOT NULL, kind INTEGER NOT NULL, actedAt INTEGER NOT NULL)"""
    const val CREATE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS index_session_resolutions_date_session ON session_resolutions(date, session)"
    const val CREATE_MARKER = """CREATE TABLE IF NOT EXISTS restore_marker (
        id INTEGER NOT NULL PRIMARY KEY, generation TEXT NOT NULL, appliedAt INTEGER NOT NULL)"""
    const val EVENT_GUARD = """
        CREATE TRIGGER event_insert_guard BEFORE INSERT ON attendance_events BEGIN
            SELECT RAISE(ABORT, 'Invalid event metadata') WHERE NEW.type NOT BETWEEN 0 AND 3 OR NEW.capturedAt > NEW.occurredAt
                OR date(NEW.occurredAt / 1000.0, 'unixepoch', '+8 hours') != date(NEW.date * 86400, 'unixepoch')
                OR date(NEW.capturedAt / 1000.0, 'unixepoch', '+8 hours') != date(NEW.date * 86400, 'unixepoch')
                OR length(NEW.originalPhotoPath) = 0 OR length(NEW.timestampedPhotoPath) = 0 OR NEW.originalPhotoPath = NEW.timestampedPhotoPath;
            SELECT RAISE(ABORT, 'Photo already recorded') WHERE EXISTS (SELECT 1 FROM attendance_events
                WHERE originalPhotoPath IN (NEW.originalPhotoPath, NEW.timestampedPhotoPath) OR timestampedPhotoPath IN (NEW.originalPhotoPath, NEW.timestampedPhotoPath));
            SELECT RAISE(ABORT, 'Invalid sequence or resolved session') WHERE
                EXISTS (SELECT 1 FROM session_resolutions WHERE date = NEW.date AND session = NEW.type / 2)
                OR EXISTS (SELECT 1 FROM attendance_events WHERE occurredAt >= NEW.occurredAt)
                OR EXISTS (SELECT 1 FROM session_resolutions WHERE actedAt >= NEW.occurredAt)
                OR (NEW.type % 2 = 1 AND NOT EXISTS (SELECT 1 FROM attendance_events WHERE date = NEW.date AND type = NEW.type - 1))
                OR (NEW.type = 2 AND NOT EXISTS (SELECT 1 FROM attendance_events WHERE date = NEW.date AND type = 1)
                    AND NOT EXISTS (SELECT 1 FROM session_resolutions WHERE date = NEW.date AND session = 0));
            SELECT RAISE(ABORT, 'Earlier session remains open') WHERE NEW.type IN (0, 2) AND EXISTS (
                SELECT 1 FROM attendance_events start WHERE start.type IN (0, 2)
                AND NOT EXISTS (SELECT 1 FROM attendance_events finish WHERE finish.date = start.date AND finish.type = start.type + 1)
                AND NOT EXISTS (SELECT 1 FROM session_resolutions r WHERE r.date = start.date AND r.session = start.type / 2));
            SELECT RAISE(ABORT, 'Legacy attendance overlap') WHERE EXISTS (SELECT 1 FROM attendance_sessions old WHERE
                (NEW.type IN (0, 2) AND (old.timeOut IS NULL OR old.timeOut > NEW.occurredAt)) OR
                (NEW.type IN (1, 3) AND old.timeIn < NEW.occurredAt AND (old.timeOut IS NULL OR old.timeOut >
                    (SELECT occurredAt FROM attendance_events WHERE date = NEW.date AND type = NEW.type - 1))));
        END
    """
    const val RESOLUTION_GUARD = """
        CREATE TRIGGER resolution_insert_guard BEFORE INSERT ON session_resolutions BEGIN
            SELECT RAISE(ABORT, 'Invalid resolution') WHERE NEW.session NOT IN (0, 1) OR NEW.kind NOT IN (0, 1)
                OR EXISTS (SELECT 1 FROM attendance_events WHERE occurredAt >= NEW.actedAt)
                OR EXISTS (SELECT 1 FROM session_resolutions WHERE actedAt >= NEW.actedAt);
            SELECT RAISE(ABORT, 'Cannot change a completed session') WHERE EXISTS (
                SELECT 1 FROM attendance_events WHERE date = NEW.date AND type = NEW.session * 2 + 1);
            SELECT RAISE(ABORT, 'Incomplete requires a prior-day Time In') WHERE NEW.kind = 0 AND (
                date(NEW.actedAt / 1000.0, 'unixepoch', '+8 hours') <= date(NEW.date * 86400, 'unixepoch')
                OR NOT EXISTS (SELECT 1 FROM attendance_events WHERE date = NEW.date AND type = NEW.session * 2));
            SELECT RAISE(ABORT, 'Skip only an unstarted current-day session') WHERE NEW.kind = 1 AND (
                date(NEW.actedAt / 1000.0, 'unixepoch', '+8 hours') != date(NEW.date * 86400, 'unixepoch')
                OR EXISTS (SELECT 1 FROM attendance_events WHERE date = NEW.date AND type IN (NEW.session * 2, NEW.session * 2 + 1))
                OR (NEW.session = 1 AND NOT EXISTS (SELECT 1 FROM attendance_events WHERE date = NEW.date AND type = 1)
                    AND NOT EXISTS (SELECT 1 FROM session_resolutions WHERE date = NEW.date AND session = 0)));
        END
    """
    const val RESOLUTION_UPDATE_GUARD = """CREATE TRIGGER resolution_update_guard BEFORE UPDATE ON session_resolutions BEGIN SELECT RAISE(ABORT, 'Resolution audit is immutable'); END"""
    const val RESOLUTION_DELETE_GUARD = """CREATE TRIGGER resolution_delete_guard BEFORE DELETE ON session_resolutions BEGIN SELECT RAISE(ABORT, 'Resolution audit cannot be silently deleted'); END"""

    val triggerNames = listOf("event_insert_guard", "event_update_guard", "event_delete_guard", "legacy_event_insert_guard", "legacy_event_update_guard",
        "resolution_insert_guard", "resolution_update_guard", "resolution_delete_guard", "attendance_guard_insert", "attendance_guard_update", "profile_guard_insert", "profile_guard_update")

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS event_insert_guard")
        db.execSQL(EVENT_GUARD)
        db.execSQL(RESOLUTION_GUARD)
        db.execSQL(RESOLUTION_UPDATE_GUARD)
        db.execSQL(RESOLUTION_DELETE_GUARD)
        for (operation in listOf("insert", "update")) {
            db.execSQL("DROP TRIGGER IF EXISTS legacy_event_${operation}_guard")
            val original = if (operation == "insert") EventSchema.LEGACY_INSERT_GUARD else EventSchema.LEGACY_UPDATE_GUARD
            db.execSQL(original.replace("AND (NEW.timeOut IS NULL", "AND NOT EXISTS (SELECT 1 FROM session_resolutions r WHERE r.date = start.date AND r.session = start.type / 2) AND (NEW.timeOut IS NULL"))
        }
    }
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_RESOLUTIONS); db.execSQL(CREATE_INDEX); db.execSQL(CREATE_MARKER)
            install(db)
        }
    }
}
